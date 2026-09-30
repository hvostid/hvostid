import axios from 'axios';

const api = axios.create({
    baseURL: '/api/v1',
    headers: { 'Content-Type': 'application/json' },
});

const refreshes = new Map();
const SESSION_KEY = 'authSessionId';
const publicAuth =
    /\/auth\/(login|register|refresh|password-reset\/.*|email-verification\/confirm)$/;

export const sessionMarker = () => localStorage.getItem(SESSION_KEY) || '';

export function startSession({ accessToken, refreshToken }) {
    localStorage.setItem(SESSION_KEY, crypto.randomUUID());
    localStorage.setItem('refreshToken', refreshToken);
    localStorage.setItem('accessToken', accessToken);
}

export function clearSession() {
    // Keep a new marker even after logout so outstanding requests cannot revive a prior session.
    localStorage.setItem(SESSION_KEY, crypto.randomUUID());
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    window.dispatchEvent(new Event('auth:expired'));
}

function assertSession(expected) {
    if (sessionMarker() !== expected) throw new axios.CanceledError('Session ended or changed');
}

async function refreshSession(failedAccessToken, marker) {
    assertSession(marker);
    const current = localStorage.getItem('accessToken');
    if (current && current !== failedAccessToken) return current;
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) throw new axios.CanceledError('Session ended');
    try {
        const { data } = await axios.post('/api/v1/auth/refresh', { refreshToken });
        assertSession(marker);
        if (localStorage.getItem('refreshToken') !== refreshToken) {
            const replacement = localStorage.getItem('accessToken');
            if (replacement) return replacement;
            throw new axios.CanceledError('Session ended while refreshing');
        }
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        return data.accessToken;
    } catch (error) {
        if (
            [400, 401].includes(error.response?.status) &&
            sessionMarker() === marker &&
            localStorage.getItem('refreshToken') === refreshToken
        ) {
            clearSession();
        }
        throw error;
    }
}

api.interceptors.request.use(
    (config) => {
        if (!publicAuth.test(config.url)) {
            if (config._authSessionId !== undefined) assertSession(config._authSessionId);
            config._authSessionId = sessionMarker();
            const token = localStorage.getItem('accessToken');
            if (token) config.headers.Authorization = `Bearer ${token}`;
            else delete config.headers.Authorization;
        }
        return config;
    },
    (error) => {
        throw error;
    },
    { synchronous: true }
);

api.interceptors.response.use(
    (response) => {
        if (response.config._authSessionId !== undefined)
            assertSession(response.config._authSessionId);
        return response;
    },
    async (error) => {
        const original = error.config;
        if (original?._authSessionId !== undefined) assertSession(original._authSessionId);
        if (
            !original ||
            error.response?.status !== 401 ||
            original._retry ||
            publicAuth.test(original.url) ||
            !localStorage.getItem('refreshToken')
        ) {
            if (
                error.response?.status === 401 &&
                !publicAuth.test(original?.url || '') &&
                localStorage.getItem('accessToken') &&
                !localStorage.getItem('refreshToken')
            ) {
                clearSession();
            }
            throw error;
        }
        original._retry = true;
        const marker = original._authSessionId;
        const failedToken = original.headers.Authorization?.replace(/^Bearer /, '');
        if (!refreshes.has(marker)) {
            const refresh = () => refreshSession(failedToken, marker);
            const pending = (
                navigator.locks
                    ? navigator.locks.request(`hvostid-session-refresh:${marker}`, refresh)
                    : refresh()
            ).finally(() => {
                if (refreshes.get(marker) === pending) refreshes.delete(marker);
            });
            refreshes.set(marker, pending);
        }
        const accessToken = await refreshes.get(marker);
        assertSession(marker);
        original.headers.Authorization = `Bearer ${accessToken}`;
        return api(original);
    }
);

export default api;
