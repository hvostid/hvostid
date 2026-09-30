import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import QuestionnairePage from './QuestionnairePage';
import { getQuestionnaire, saveQuestionnaire } from '../api/matching';

vi.mock('../api/matching', () => ({ getQuestionnaire: vi.fn(), saveQuestionnaire: vi.fn() }));
it('does not expose defaults for editing when a saved questionnaire cannot be loaded', async () => {
    getQuestionnaire
        .mockRejectedValueOnce({ response: { status: 503 } })
        .mockResolvedValueOnce(null);
    const { container } = render(
        <MemoryRouter>
            <QuestionnairePage />
        </MemoryRouter>
    );
    await screen.findByRole('alert');
    expect(container.querySelector('form')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(container.querySelector('form')).not.toBeNull());
    expect(saveQuestionnaire).not.toHaveBeenCalled();
});

it.each([
    ['/listings/42/match', 'Returned to selected pet'],
    ['//outside.example', 'Returned to profile'],
])('returns safely after saving with returnUrl=%s', async (returnUrl, destination) => {
    getQuestionnaire.mockResolvedValue({ livingArea: 60, monthlyBudget: 0 });
    saveQuestionnaire.mockResolvedValue({});
    const { container } = render(
        <MemoryRouter
            initialEntries={[`/profile/questionnaire?returnUrl=${encodeURIComponent(returnUrl)}`]}
        >
            <Routes>
                <Route path="/profile/questionnaire" element={<QuestionnairePage />} />
                <Route path="/listings/42/match" element={<h1>Returned to selected pet</h1>} />
                <Route path="/profile" element={<h1>Returned to profile</h1>} />
            </Routes>
        </MemoryRouter>
    );
    await waitFor(() => expect(container.querySelector('form')).not.toBeNull());
    fireEvent.submit(container.querySelector('form'));
    await screen.findByRole('heading', { name: destination }, { timeout: 3000 });
});
it('preserves valid zero budget and age values during loading and saving', async () => {
    getQuestionnaire.mockResolvedValue({
        livingArea: 60,
        hasChildren: true,
        childrenAgeMin: 0,
        monthlyBudget: 0,
        livingSpace: 'APARTMENT',
        workSchedule: 'HOME',
        petExperience: 'BEGINNER',
        activityLevel: 'LOW',
    });
    saveQuestionnaire.mockResolvedValue({});
    const { container } = render(
        <MemoryRouter>
            <QuestionnairePage />
        </MemoryRouter>
    );
    await waitFor(() => expect(container.querySelector('form')).not.toBeNull());
    fireEvent.submit(container.querySelector('form'));
    await waitFor(() => expect(saveQuestionnaire).toHaveBeenCalled());
    expect(saveQuestionnaire.mock.calls[0][0]).toMatchObject({
        childrenAgeMin: 0,
        monthlyBudget: 0,
    });
});
