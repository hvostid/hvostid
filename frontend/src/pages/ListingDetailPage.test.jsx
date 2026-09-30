import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import ListingDetailPage from './ListingDetailPage';
import api from '../api/client';

const auth = vi.hoisted(() => ({ user: { id: 42 }, isAuthenticated: true, hasRole: () => false }));
vi.mock('../context/useAuth', () => ({ useAuth: () => auth }));
vi.mock('../api/client', () => ({ default: { get: vi.fn() } }));
const view = () => (
    <MemoryRouter initialEntries={['/listings/1']}>
        <Routes>
            <Route path="/listings/:id" element={<ListingDetailPage />} />
        </Routes>
    </MemoryRouter>
);

it.each([false, true])(
    'clears protected data when identity changes (new account=%s)',
    async (newAccount) => {
        Object.assign(auth, { user: { id: 42 }, isAuthenticated: true });
        api.get.mockImplementation(async (path) => {
            if (path === '/listings/1')
                return {
                    data: {
                        id: 1,
                        title: 'Public cat',
                        sellerId: 42,
                        passportId: '42',
                        status: 'PUBLISHED',
                        species: 'CAT',
                    },
                };
            if (path.endsWith('/docs')) return { data: [] };
            if (path.endsWith('/trust')) return { data: null };
            if (path === '/passports/42' && auth.user?.id === 42)
                return { data: { id: 42, name: 'Private passport content', vaccinations: [] } };
            throw { response: { status: 403 } };
        });
        const { rerender } = render(view());
        await screen.findByText('Private passport content');
        Object.assign(auth, { user: newAccount ? { id: 99 } : null, isAuthenticated: newAccount });
        rerender(view());
        expect(screen.queryByText('Private passport content')).toBeNull();
        await screen.findByRole('heading', { name: 'Public cat' });
        expect(screen.queryByText('Private passport content')).toBeNull();
    }
);
