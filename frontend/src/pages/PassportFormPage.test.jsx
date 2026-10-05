import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import PassportFormPage from './PassportFormPage';
import { getOwnedListings } from '../api/listings';
import { getPassport, getPassportDocuments, getTrustScore, updatePassport } from '../api/passports';

vi.mock('../api/listings', () => ({ getListingById: vi.fn(), getOwnedListings: vi.fn() }));
vi.mock('../api/passports', () => ({
    getPassport: vi.fn(),
    createPassport: vi.fn(),
    updatePassport: vi.fn(),
    getTrustScore: vi.fn(),
    getPassportDocuments: vi.fn(),
    uploadDocument: vi.fn(),
    deleteDocument: vi.fn(),
    issueDocumentTicket: vi.fn(),
}));
beforeEach(() => {
    getOwnedListings.mockResolvedValue([]);
    getPassport.mockResolvedValue({
        id: 42,
        name: 'Mila',
        species: 'CAT',
        birthDate: '2020-01-01',
        vaccinations: [],
    });
    getPassportDocuments.mockResolvedValue([]);
    getTrustScore.mockResolvedValue(null);
});
const show = () =>
    render(
        <MemoryRouter initialEntries={['/passports/42']}>
            <Routes>
                <Route path="/passports/:passportId" element={<PassportFormPage />} />
            </Routes>
        </MemoryRouter>
    );

it.each([
    ['2099-01-01', '', 'Vaccination date cannot be in the future.'],
    ['2019-12-31', '', 'Vaccination date cannot be before birth.'],
    ['2025-01-01', '2024-12-31', 'Next due date cannot be before the vaccination date.'],
])(
    'rejects incompatible vaccination dates %s/%s without losing input',
    async (date, nextDate, error) => {
        const { container } = show();
        await screen.findByRole('button', { name: '+ Add vaccination' });
        fireEvent.change(container.querySelector('#vacName'), { target: { value: 'Rabies' } });
        fireEvent.change(container.querySelector('#vacDate'), { target: { value: date } });
        fireEvent.change(container.querySelector('#vacNextDate'), { target: { value: nextDate } });
        fireEvent.click(screen.getByRole('button', { name: '+ Add vaccination' }));
        expect(screen.getByText(error)).toBeInTheDocument();
        expect(container.querySelector('#vacName')).toHaveValue('Rabies');
        expect(updatePassport).not.toHaveBeenCalled();
    }
);

it('retains vaccination fields and shows API validation detail after a failed save', async () => {
    updatePassport.mockRejectedValue({
        response: {
            status: 400,
            data: { detail: 'Vaccination conflicts with updated birth date' },
        },
    });
    const { container } = show();
    await screen.findByRole('button', { name: '+ Add vaccination' });
    fireEvent.change(container.querySelector('#vacName'), { target: { value: 'Rabies' } });
    fireEvent.change(container.querySelector('#vacDate'), { target: { value: '2025-01-01' } });
    fireEvent.click(screen.getByRole('button', { name: '+ Add vaccination' }));
    fireEvent.click(screen.getByRole('button', { name: 'Save passport' }));
    await screen.findByText('Vaccination conflicts with updated birth date');
    expect(screen.getByText('Rabies')).toBeInTheDocument();
    expect(updatePassport).toHaveBeenCalledWith(
        42,
        expect.objectContaining({
            vaccinations: [expect.objectContaining({ name: 'Rabies', date: '2025-01-01' })],
        })
    );
});
