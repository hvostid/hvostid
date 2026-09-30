import { beforeEach, describe, expect, it, vi } from 'vitest';
import axios, { AxiosError } from 'axios';
import api, { startSession } from './client';

function denied(config, status = 401) {
    return new AxiosError('Request failed', 'ERR_BAD_RESPONSE', config, null, {
        status,
        data: {},
        headers: {},
        config,
    });
}
beforeEach(() => {
    localStorage.setItem('accessToken', 'old-access');
    localStorage.setItem('refreshToken', 'old-refresh');
    localStorage.setItem('authSessionId', 'session-a');
});
describe('session refresh', () => {
    it('refreshes once for simultaneous 401 responses and retries both requests', async () => {
        const refresh = vi.spyOn(axios, 'post').mockResolvedValue({
            data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
        });
        api.defaults.adapter = async (config) => {
            if (config.headers.Authorization === 'Bearer old-access') throw denied(config);
            return { data: config.url, status: 200, headers: {}, config };
        };
        const result = await Promise.all([api.get('/profile/me'), api.get('/listings/my')]);
        expect(result.map((response) => response.status)).toEqual([200, 200]);
        expect(refresh).toHaveBeenCalledTimes(1);
        expect(localStorage.getItem('refreshToken')).toBe('new-refresh');
    });
    it('retains the session when refresh is temporarily unavailable', async () => {
        vi.spyOn(axios, 'post').mockRejectedValue({ response: { status: 503 } });
        api.defaults.adapter = async (config) => {
            throw denied(config);
        };
        await expect(api.get('/profile/me')).rejects.toMatchObject({ response: { status: 503 } });
        expect(localStorage.getItem('refreshToken')).toBe('old-refresh');
    });
    it('clears a rejected refresh and notifies auth context', async () => {
        vi.spyOn(axios, 'post').mockRejectedValue({ response: { status: 401 } });
        const expired = vi.fn();
        window.addEventListener('auth:expired', expired);
        api.defaults.adapter = async (config) => {
            throw denied(config);
        };
        await expect(api.get('/profile/me')).rejects.toBeDefined();
        expect(expired).toHaveBeenCalledOnce();
        expect(localStorage.getItem('accessToken')).toBeNull();
        window.removeEventListener('auth:expired', expired);
    });
    it('does not refresh failed login attempts or guest public requests', async () => {
        const refresh = vi.spyOn(axios, 'post');
        api.defaults.adapter = async (config) => {
            throw denied(config);
        };
        await expect(api.post('/auth/login', {})).rejects.toBeDefined();
        localStorage.clear();
        await expect(api.get('/listings/1')).rejects.toBeDefined();
        expect(refresh).not.toHaveBeenCalled();
    });
    it('does not revive a session that was logged out during refresh', async () => {
        let resolveRefresh;
        vi.spyOn(axios, 'post').mockImplementation(
            () =>
                new Promise((resolve) => {
                    resolveRefresh = resolve;
                })
        );
        api.defaults.adapter = async (config) => {
            throw denied(config);
        };
        const request = api.get('/profile/me');
        const rejection = expect(request).rejects.toThrow('Session ended');
        await vi.waitFor(() => expect(resolveRefresh).toBeDefined());
        localStorage.clear();
        resolveRefresh({ data: { accessToken: 'new', refreshToken: 'new-refresh' } });
        await rejection;
        expect(localStorage.getItem('accessToken')).toBeNull();
    });
    it('does not replay a mutation under an account that logged in during refresh', async () => {
        let resolveRefresh;
        vi.spyOn(axios, 'post').mockImplementation(
            () =>
                new Promise((resolve) => {
                    resolveRefresh = resolve;
                })
        );
        const requests = [];
        api.defaults.adapter = async (config) => {
            requests.push(config.headers.Authorization);
            if (config.headers.Authorization === 'Bearer old-access') throw denied(config);
            return { data: {}, status: 200, headers: {}, config };
        };
        const mutation = api.post('/listings', { title: 'Account A listing' });
        const rejected = expect(mutation).rejects.toThrow('Session ended or changed');
        await vi.waitFor(() => expect(resolveRefresh).toBeDefined());
        startSession({ accessToken: 'account-b', refreshToken: 'refresh-b' });
        resolveRefresh({ data: { accessToken: 'rotated-a', refreshToken: 'rotated-refresh-a' } });
        await rejected;
        expect(requests).toEqual(['Bearer old-access']);
        expect(localStorage.getItem('accessToken')).toBe('account-b');
    });
    it('keeps the original marker even if the account changes immediately after dispatch', async () => {
        const refresh = vi.spyOn(axios, 'post');
        let rejectRequest;
        api.defaults.adapter = (config) =>
            new Promise((_, reject) => {
                rejectRequest = () => reject(denied(config));
            });
        const mutation = api.post('/listings', { title: 'Account A listing' });
        const rejected = expect(mutation).rejects.toThrow('Session ended or changed');
        startSession({ accessToken: 'account-b', refreshToken: 'refresh-b' });
        rejectRequest();
        await rejected;
        expect(refresh).not.toHaveBeenCalled();
    });
    it('does not reuse an outstanding refresh from a different login session', async () => {
        let resolveA;
        const refresh = vi
            .spyOn(axios, 'post')
            .mockImplementationOnce(
                () =>
                    new Promise((resolve) => {
                        resolveA = resolve;
                    })
            )
            .mockResolvedValueOnce({
                data: { accessToken: 'fresh-b', refreshToken: 'fresh-refresh-b' },
            });
        api.defaults.adapter = async (config) => {
            if (config.headers.Authorization !== 'Bearer fresh-b') throw denied(config);
            return { data: {}, status: 200, headers: {}, config };
        };
        const requestA = api.get('/profile/me');
        const rejectedA = expect(requestA).rejects.toThrow('Session ended or changed');
        await vi.waitFor(() => expect(resolveA).toBeDefined());
        startSession({ accessToken: 'account-b', refreshToken: 'refresh-b' });
        await expect(api.get('/profile/me')).resolves.toMatchObject({ status: 200 });
        expect(refresh).toHaveBeenCalledTimes(2);
        resolveA({ data: { accessToken: 'fresh-a', refreshToken: 'fresh-refresh-a' } });
        await rejectedA;
        expect(localStorage.getItem('accessToken')).toBe('fresh-b');
    });
});
