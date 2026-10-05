import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import api, { clearSession, startSession } from '../api/client';
import { AuthProvider } from './AuthContext';
import { useAuth } from './useAuth';

function Probe() {
    const { user, loading, authError, login, logout, reloadProfile } = useAuth();
    return (
        <>
            <p data-testid="identity">{user?.name || 'Guest'}</p>
            <p data-testid="loading">{String(loading)}</p>
            <p data-testid="error">{authError || ''}</p>
            <button onClick={() => void login('b@example.com', 'password123')}>Login B</button>
            <button onClick={() => void logout()}>Logout</button>
            <button onClick={() => void reloadProfile()}>Reload</button>
        </>
    );
}
beforeEach(() => {
    startSession({ accessToken: 'access-a', refreshToken: 'refresh-a' });
    vi.spyOn(api, 'post').mockResolvedValue({
        data: { accessToken: 'access-b', refreshToken: 'refresh-b' },
    });
});
it('ignores a slow profile response after logout', async () => {
    let resolveProfile;
    vi.spyOn(api, 'get').mockImplementation(
        () =>
            new Promise((resolve) => {
                resolveProfile = resolve;
            })
    );
    render(
        <AuthProvider>
            <Probe />
        </AuthProvider>
    );
    await waitFor(() => expect(resolveProfile).toBeDefined());
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    await waitFor(() => expect(localStorage.getItem('accessToken')).toBeNull());
    await act(async () => resolveProfile({ data: { id: 1, name: 'Account A' } }));
    expect(screen.getByTestId('identity')).toHaveTextContent('Guest');
    expect(screen.getByTestId('loading')).toHaveTextContent('false');
});
it('keeps the new account when an older profile response arrives late', async () => {
    let resolveA;
    vi.spyOn(api, 'get')
        .mockImplementationOnce(
            () =>
                new Promise((resolve) => {
                    resolveA = resolve;
                })
        )
        .mockResolvedValueOnce({ data: { id: 2, name: 'Account B' } });
    render(
        <AuthProvider>
            <Probe />
        </AuthProvider>
    );
    await waitFor(() => expect(resolveA).toBeDefined());
    fireEvent.click(screen.getByRole('button', { name: 'Login B' }));
    await screen.findByText('Account B');
    await act(async () => resolveA({ data: { id: 1, name: 'Account A' } }));
    expect(screen.getByTestId('identity')).toHaveTextContent('Account B');
});
it('ignores stale authentication failures instead of clearing the newer session', async () => {
    let rejectA;
    vi.spyOn(api, 'get')
        .mockImplementationOnce(
            () =>
                new Promise((_, reject) => {
                    rejectA = reject;
                })
        )
        .mockResolvedValueOnce({ data: { id: 2, name: 'Account B' } });
    render(
        <AuthProvider>
            <Probe />
        </AuthProvider>
    );
    await waitFor(() => expect(rejectA).toBeDefined());
    fireEvent.click(screen.getByRole('button', { name: 'Login B' }));
    await screen.findByText('Account B');
    await act(async () => rejectA({ response: { status: 401 } }));
    expect(screen.getByTestId('identity')).toHaveTextContent('Account B');
    expect(localStorage.getItem('accessToken')).toBe('access-b');
});
it('does not restore an outstanding login after an explicit logout event', async () => {
    vi.spyOn(api, 'get').mockResolvedValue({ data: { id: 1, name: 'Account A' } });
    let resolveLogin;
    api.post.mockImplementationOnce(
        () =>
            new Promise((resolve) => {
                resolveLogin = resolve;
            })
    );
    render(
        <AuthProvider>
            <Probe />
        </AuthProvider>
    );
    await screen.findByText('Account A');
    fireEvent.click(screen.getByRole('button', { name: 'Login B' }));
    await waitFor(() => expect(resolveLogin).toBeDefined());
    act(() => clearSession());
    await act(async () =>
        resolveLogin({ data: { accessToken: 'access-b', refreshToken: 'refresh-b' } })
    );
    expect(screen.getByTestId('identity')).toHaveTextContent('Guest');
    expect(localStorage.getItem('accessToken')).toBeNull();
});
