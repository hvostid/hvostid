import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import RecommendationsPage from './RecommendationsPage';
import { getRecommendations } from '../api/matching';

vi.mock('../api/matching', () => ({ getRecommendations: vi.fn() }));
function result(title) {
    return {
        content: [
            {
                listing: { id: 1, title, species: 'CAT', city: 'Omsk', price: 0 },
                score: 90,
                level: 'GREAT',
            },
        ],
        totalPages: 1,
        totalElements: 1,
    };
}
it('ignores an older response when a new filter request completes first', async () => {
    getRecommendations.mockResolvedValueOnce({ ...result('Initial result'), totalPages: 3 });
    let oldResponse;
    getRecommendations.mockImplementationOnce(
        () =>
            new Promise((resolve) => {
                oldResponse = resolve;
            })
    );
    getRecommendations.mockResolvedValueOnce(result('Current result'));
    const { container } = render(
        <MemoryRouter>
            <RecommendationsPage />
        </MemoryRouter>
    );
    await screen.findByText('Initial result');
    fireEvent.click(screen.getByRole('button', { name: /Load more/i }));
    await waitFor(() => expect(oldResponse).toBeDefined());
    fireEvent.change(container.querySelector('select'), { target: { value: '70' } });
    await screen.findByText('Current result');
    await act(async () => {
        oldResponse(result('Old result'));
    });
    expect(screen.getByText('Current result')).toBeInTheDocument();
    expect(screen.queryByText('Old result')).toBeNull();
});
it('clears a prior error on a successful retry', async () => {
    getRecommendations
        .mockRejectedValueOnce({ response: { status: 503 } })
        .mockResolvedValueOnce(result('Recovered result'));
    render(
        <MemoryRouter>
            <RecommendationsPage />
        </MemoryRouter>
    );
    fireEvent.click(await screen.findByRole('button', { name: 'Retry' }));
    await screen.findByText('Recovered result');
    expect(screen.queryByRole('button', { name: 'Retry' })).toBeNull();
});
