import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import MyListingsPage from './MyListingsPage';
import { getMyListings, changeListingStatus, deleteListing } from '../api/listings';
import { getAllMyPassports } from '../api/passports';

vi.mock('../api/listings', () => ({
    getMyListings: vi.fn(),
    changeListingStatus: vi.fn(),
    deleteListing: vi.fn(),
}));
vi.mock('../api/passports', () => ({
    getPassport: vi.fn(),
    getAllMyPassports: vi.fn(),
    issueDocumentTicket: vi.fn(),
    deletePassport: vi.fn(),
}));
const listing = (title, status = 'DRAFT') => ({ id: 1, title, status, species: 'CAT', price: 0 });
const page = (item, totalPages = 1) => ({ content: item ? [item] : [], totalPages });
beforeEach(() => {
    getAllMyPassports.mockResolvedValue(page(null));
});
const show = () =>
    render(
        <MemoryRouter>
            <MyListingsPage />
        </MemoryRouter>
    );

it('refreshes the current filter after a delayed mutation completes', async () => {
    getMyListings.mockImplementation(async (status) =>
        page(
            listing(
                status === 'PUBLISHED' ? 'Current published' : 'Original draft',
                status || 'DRAFT'
            )
        )
    );
    let complete;
    changeListingStatus.mockImplementation(
        () =>
            new Promise((resolve) => {
                complete = resolve;
            })
    );
    show();
    fireEvent.click(await screen.findByRole('button', { name: 'Submit for review' }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(complete).toBeDefined());
    fireEvent.click(screen.getByRole('button', { name: 'Published', exact: true }));
    await screen.findByText('Current published');
    await act(async () => complete());
    await waitFor(() =>
        expect(getMyListings).toHaveBeenLastCalledWith('PUBLISHED', 0, 20, expect.any(AbortSignal))
    );
    expect(screen.getByText('Current published')).toBeInTheDocument();
    expect(screen.queryByText('Original draft')).toBeNull();
});

it('returns to the remaining page after deleting the last item on the last page', async () => {
    let deleted = false;
    getMyListings.mockImplementation(async (_status, number) => {
        if (deleted) return page(number === 0 ? listing('Remaining listing') : null, 1);
        return page(listing(number === 1 ? 'Last listing' : 'First listing'), 2);
    });
    deleteListing.mockImplementation(async () => {
        deleted = true;
    });
    show();
    await screen.findByText('First listing');
    fireEvent.click(screen.getByRole('button', { name: 'Next', exact: true }));
    await screen.findByText('Last listing');
    fireEvent.click(screen.getByRole('button', { name: 'Delete', exact: true }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm' }));
    await screen.findByText('Remaining listing');
    expect(screen.queryByText('Last listing')).toBeNull();
    expect(screen.queryByRole('navigation', { name: 'Pagination' })).toBeNull();
});
