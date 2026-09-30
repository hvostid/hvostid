import { act, fireEvent, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CatalogPage from './CatalogPage';
import api from '../api/client';

vi.mock('../api/client', () => ({ default: { get: vi.fn() } }));
beforeEach(() => {
    vi.useFakeTimers();
    api.get.mockResolvedValue({ data: { content: [], totalPages: 5, totalElements: 0 } });
});
afterEach(() => vi.useRealTimers());
describe('catalog URL state', () => {
    it('keeps a linked page after the search debounce and follows browser navigation', async () => {
        const router = createMemoryRouter([{ path: '/', element: <CatalogPage /> }], {
            initialEntries: ['/?page=3&q=cat'],
        });
        render(<RouterProvider router={router} />);
        await act(async () => {
            await vi.advanceTimersByTimeAsync(400);
        });
        expect(router.state.location.search).toBe('?page=3&q=cat');
        expect(api.get.mock.calls.every(([url]) => url.includes('page=3'))).toBe(true);
        await act(async () => {
            await router.navigate('/?page=2&q=dog');
        });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(400);
        });
        expect(screen.getByRole('searchbox')).toHaveValue('dog');
        expect(router.state.location.search).toBe('?page=2&q=dog');
    });
    it('resets the page only after the query actually changes', async () => {
        const router = createMemoryRouter([{ path: '/', element: <CatalogPage /> }], {
            initialEntries: ['/?page=3'],
        });
        render(<RouterProvider router={router} />);
        await act(async () => {});
        fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'kitten' } });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(400);
        });
        expect(router.state.location.search).toContain('q=kitten');
        expect(router.state.location.search).not.toContain('page=');
    });
});
