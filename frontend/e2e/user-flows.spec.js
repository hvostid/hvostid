import { test, expect } from '@playwright/test';

const seller = {
    id: 42,
    name: 'Test seller',
    email: 'seller@example.test',
    roles: ['BUYER', 'SELLER'],
    emailVerified: true,
};
const listing = {
    id: 1,
    sellerId: 42,
    title: 'Mila needs a home',
    description: 'A friendly cat looking for a family.',
    species: 'CAT',
    breed: 'Mixed',
    city: 'Omsk',
    age: 12,
    price: 0,
    status: 'PUBLISHED',
    passportId: '42',
};
const pageOf = (content) => ({
    content,
    page: 0,
    size: 20,
    totalPages: 1,
    totalElements: content.length,
});

async function session(page) {
    await page.addInitScript(() => {
        localStorage.setItem('accessToken', 'fixture-access');
        localStorage.setItem('refreshToken', 'fixture-refresh');
    });
}

test('guests can open a public listing without private passport calls', async ({ page }) => {
    const privateRequests = [];
    await page.route('**/api/v1/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/v1/listings/1') return route.fulfill({ json: listing });
        if (path.endsWith('/cover')) return route.fulfill({ status: 404 });
        privateRequests.push(path);
        return route.fulfill({ status: 401, json: { detail: 'Authentication required' } });
    });
    await page.goto('/listings/1');
    await expect(page.getByRole('heading', { name: listing.title })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Sign in to interact' })).toBeVisible();
    await expect(page).toHaveURL(/\/listings\/1$/);
    expect(privateRequests).toEqual([]);
    await page.screenshot({ path: 'test-results/public-listing.png', fullPage: true });
});

test('catalog deep links retain page and search after debounce', async ({ page }) => {
    const pages = [];
    await page.route('**/api/v1/passports/*/cover', (route) => route.fulfill({ status: 404 }));
    await page.route('**/api/v1/listings?**', async (route) => {
        pages.push(new URL(route.request().url()).searchParams.get('page'));
        await route.fulfill({ json: { ...pageOf([listing]), totalPages: 5, totalElements: 81 } });
    });
    await page.goto('/?page=3&q=cat');
    await expect(page.getByText(listing.title)).toBeVisible();
    await expect.poll(() => pages.length).toBeGreaterThan(0);
    await page.getByRole('searchbox').focus();
    await page.waitForTimeout(400);
    await expect(page).toHaveURL(/page=3/);
    expect(pages.every((value) => value === '3')).toBe(true);
    await page.screenshot({ path: 'test-results/catalog.png', fullPage: true });
});

test('failed update retains fields and can be submitted again', async ({ page }) => {
    await session(page);
    let updates = 0;
    await page.route('**/api/v1/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/v1/profile/me') return route.fulfill({ json: seller });
        if (path === '/api/v1/listings/1' && route.request().method() === 'PUT') {
            updates += 1;
            return route.fulfill({
                status: updates === 1 ? 500 : 200,
                json: updates === 1 ? { detail: 'Temporary outage' } : listing,
            });
        }
        if (path === '/api/v1/listings/1')
            return route.fulfill({ json: { ...listing, status: 'REJECTED' } });
        return route.fulfill({ json: pageOf([]) });
    });
    await page.goto('/my-listings/1/edit');
    await page.locator('#title').fill('Edited cat listing');
    await page.locator('form button[type=submit]').click();
    await expect(page.getByRole('alert')).toContainText('Your changes are still here');
    await expect(page.locator('#title')).toHaveValue('Edited cat listing');
    await page.screenshot({ path: 'test-results/edit-retry.png', fullPage: true });
    await page.locator('form button[type=submit]').click();
    await expect(page).toHaveURL(/\/my-listings$/);
    expect(updates).toBe(2);
});

test('draft autosave survives reload and keeps its passport selection', async ({ page }) => {
    await session(page);
    let draft = null;
    await page.route('**/api/v1/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/v1/profile/me') return route.fulfill({ json: seller });
        if (path.startsWith('/api/v1/listings/drafts/')) {
            if (route.request().method() === 'PUT') {
                const value = route.request().postDataJSON();
                if (value.version !== (draft?.version || 0))
                    return route.fulfill({
                        status: 409,
                        json: { detail: 'Draft changed in another tab' },
                    });
                draft = { ...value, version: (draft?.version || 0) + 1 };
                return route.fulfill({ json: draft });
            }
            return draft ? route.fulfill({ json: draft }) : route.fulfill({ status: 204 });
        }
        if (path === '/api/v1/passports/my')
            return route.fulfill({ json: pageOf([{ id: 42, name: 'Mila', species: 'CAT' }]) });
        return route.fulfill({ json: pageOf([]) });
    });
    await page.goto('/my-listings/new');
    await page.locator('#title').fill('My draft title');
    await page.locator('#description').fill('Detailed description of this cat.');
    await page.locator('#city').fill('Omsk');
    await expect.poll(() => draft?.title).toBe('My draft title');
    await page.reload();
    await expect(page.locator('#title')).toHaveValue('My draft title');
    await page.getByRole('button', { name: 'Choose passport' }).click();
    await page.locator('#selected-passport').selectOption('42');
    await expect.poll(() => draft?.passportId).toBe('42');
    await page.reload();
    await page.getByRole('button', { name: 'Choose passport' }).click();
    await expect(page.locator('#selected-passport')).toHaveValue('42');
    await page.screenshot({ path: 'test-results/draft-passport.png', fullPage: true });
});

test('unknown routes render a recoverable 404 page', async ({ page }) => {
    await page.goto('/this-route-does-not-exist');
    await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Return to catalog' })).toHaveAttribute(
        'href',
        '/'
    );
});

test('a concurrent draft update preserves local input until the saved version is chosen', async ({
    page,
}) => {
    await session(page);
    let draft = { ...listing, title: 'Saved draft', version: 1 };
    await page.route('**/api/v1/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/v1/profile/me') return route.fulfill({ json: seller });
        if (path.startsWith('/api/v1/listings/drafts/')) {
            if (route.request().method() === 'PUT') {
                const value = route.request().postDataJSON();
                if (value.version !== draft.version)
                    return route.fulfill({
                        status: 409,
                        json: { detail: 'Draft changed in another tab' },
                    });
                draft = { ...value, version: draft.version + 1 };
            }
            return route.fulfill({ json: draft });
        }
        return route.fulfill({ json: pageOf([]) });
    });
    await page.goto('/my-listings/new');
    await expect(page.locator('#title')).toHaveValue('Saved draft');
    await expect.poll(() => draft.version).toBeGreaterThan(1);
    draft = { ...draft, title: 'Changed elsewhere', version: draft.version + 1 };
    await page.locator('#title').fill('My unsaved title');
    await expect(page.getByRole('alert')).toContainText('Draft changed in another tab');
    await expect(page.locator('#title')).toHaveValue('My unsaved title');
    await page.getByRole('button', { name: 'Use saved version (replace these fields)' }).click();
    await expect(page.locator('#title')).toHaveValue('Changed elsewhere');
});

test('successful creation is retained when versioned draft cleanup conflicts', async ({ page }) => {
    await session(page);
    let draft = { ...listing, version: 1 };
    let creations = 0;
    let cleanupVersion;
    await page.route('**/api/v1/**', async (route) => {
        const url = new URL(route.request().url());
        if (url.pathname === '/api/v1/profile/me') return route.fulfill({ json: seller });
        if (url.pathname.startsWith('/api/v1/listings/drafts/')) {
            if (route.request().method() === 'PUT')
                draft = { ...route.request().postDataJSON(), version: draft.version + 1 };
            if (route.request().method() === 'DELETE') {
                cleanupVersion = url.searchParams.get('version');
                return route.fulfill({
                    status: 409,
                    json: { detail: 'A newer saved draft exists' },
                });
            }
            return route.fulfill({ json: draft });
        }
        if (url.pathname === '/api/v1/listings' && route.request().method() === 'POST') {
            creations++;
            return route.fulfill({ status: 201, json: listing });
        }
        if (url.pathname === '/api/v1/passports/my')
            return route.fulfill({ json: pageOf([{ id: 42, name: 'Mila', species: 'CAT' }]) });
        return route.fulfill({ json: pageOf([]) });
    });
    await page.goto('/my-listings/new');
    await page.getByRole('button', { name: 'Choose passport' }).click();
    await expect(page.locator('#selected-passport')).toHaveValue('42');
    await page.getByRole('button', { name: 'Create listing', exact: true }).click();
    await expect(page).toHaveURL(/\/my-listings$/);
    expect(creations).toBe(1);
    expect(cleanupVersion).toBe(String(draft.version));
});

test('a moderator can retry a failed approval without losing the listing', async ({ page }) => {
    await session(page);
    let attempts = 0;
    await page.route('**/api/v1/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/v1/profile/me')
            return route.fulfill({ json: { ...seller, roles: ['BUYER', 'MODERATOR'] } });
        if (path === '/api/v1/moderation/listings/1/approve') {
            attempts++;
            return route.fulfill({
                status: attempts === 1 ? 503 : 200,
                json: attempts === 1 ? { detail: 'Moderation temporarily unavailable' } : listing,
            });
        }
        if (path === '/api/v1/moderation/listings/1')
            return route.fulfill({
                json: { listing: { ...listing, status: 'MODERATION' }, flags: [] },
            });
        return route.fulfill({ json: pageOf([]) });
    });
    await page.goto('/moderation/1');
    await expect(page.getByRole('heading', { name: listing.title })).toBeVisible();
    await page.getByRole('button', { name: 'Approve & publish', exact: true }).click();
    await expect(page.getByText('Moderation temporarily unavailable')).toBeVisible();
    await expect(page.getByRole('heading', { name: listing.title })).toBeVisible();
    await page.screenshot({ path: 'test-results/moderation-retry.png', fullPage: true });
    await page.getByRole('button', { name: 'Approve & publish', exact: true }).click();
    await expect(page).toHaveURL(/\/moderation$/);
    expect(attempts).toBe(2);
});
