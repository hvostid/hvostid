import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import EditListingPage from './EditListingPage';
import { getListingById, updateListing } from '../api/listings';

vi.mock('../api/listings', () => ({ getListingById: vi.fn(), updateListing: vi.fn() }));
it('preserves entered fields after a failed update and allows a retry', async () => {
    getListingById.mockResolvedValue({
        id: 1,
        status: 'REJECTED',
        title: 'Initial title',
        description: 'A sufficiently detailed description',
        city: 'Omsk',
        species: 'CAT',
        age: 0,
        price: 0,
    });
    updateListing.mockRejectedValueOnce({ response: { status: 500 } }).mockResolvedValueOnce({});
    const { container } = render(
        <MemoryRouter initialEntries={['/my-listings/1/edit']}>
            <Routes>
                <Route path="/my-listings/:id/edit" element={<EditListingPage />} />
                <Route path="/my-listings" element={<p>Saved</p>} />
            </Routes>
        </MemoryRouter>
    );
    fireEvent.change(await screen.findByDisplayValue('Initial title'), {
        target: { value: 'Edited title' },
    });
    fireEvent.submit(container.querySelector('form'));
    await screen.findByRole('alert');
    expect(screen.getByDisplayValue('Edited title')).toBeInTheDocument();
    fireEvent.submit(container.querySelector('form'));
    await waitFor(() => expect(updateListing).toHaveBeenCalledTimes(2));
    expect(updateListing.mock.calls[1][1]).toMatchObject({
        age: 0,
        price: 0,
        title: 'Edited title',
    });
});
