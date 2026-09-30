import { useState, useEffect, useCallback, useRef } from 'react';
import api, { clearSession, sessionMarker, startSession } from '../api/client';
import { AuthContext } from './AuthStore';

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [authError, setAuthError] = useState(null);
    const generationRef = useRef(0);
    const actionRef = useRef(0);
    const profileRequestRef = useRef(null);

    const invalidateProfile = useCallback(() => {
        generationRef.current += 1;
        profileRequestRef.current?.abort();
    }, []);

    const reloadProfile = useCallback(async () => {
        invalidateProfile();
        const request = generationRef.current;
        const marker = sessionMarker();
        const controller = new AbortController();
        profileRequestRef.current = controller;
        const isCurrent = () =>
            request === generationRef.current &&
            !controller.signal.aborted &&
            marker === sessionMarker();
        setLoading(true);
        setAuthError(null);
        try {
            if (!localStorage.getItem('accessToken')) {
                setUser(null);
                return null;
            }
            const { data } = await api.get('/profile/me', { signal: controller.signal });
            if (!isCurrent()) return null;
            setUser(data);
            return data;
        } catch (error) {
            if (!isCurrent()) return null;
            if (error.response?.status === 401) {
                clearSession();
            } else {
                setAuthError('Your session could not be loaded. Please retry.');
            }
            return null;
        } finally {
            if (isCurrent()) setLoading(false);
        }
    }, [invalidateProfile]);

    useEffect(() => {
        const expired = () => {
            actionRef.current += 1;
            invalidateProfile();
            setUser(null);
            setLoading(false);
            setAuthError(null);
        };
        const storage = (event) => {
            if (event.key === 'authSessionId' || event.key === null) {
                actionRef.current += 1;
                invalidateProfile();
                setUser(null);
            }
            if (event.key === 'accessToken' || event.key === 'authSessionId' || event.key === null)
                void reloadProfile();
        };
        window.addEventListener('auth:expired', expired);
        window.addEventListener('storage', storage);
        // Start the external profile request when the provider mounts.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        void reloadProfile();
        return () => {
            window.removeEventListener('auth:expired', expired);
            window.removeEventListener('storage', storage);
            actionRef.current += 1;
            invalidateProfile();
        };
    }, [reloadProfile, invalidateProfile]);

    const login = async (email, password) => {
        const attempt = ++actionRef.current;
        invalidateProfile();
        try {
            const { data } = await api.post('/auth/login', { email, password });
            if (attempt !== actionRef.current) return null;
            startSession(data);
            setUser(null);
            return reloadProfile();
        } catch (error) {
            if (attempt === actionRef.current) setLoading(false);
            throw error;
        }
    };
    const register = async (email, password, name) =>
        (await api.post('/auth/register', { email, password, name })).data;
    const logout = async (all = false) => {
        const attempt = ++actionRef.current;
        const marker = sessionMarker();
        invalidateProfile();
        try {
            await api.post(all ? '/auth/logout-all' : '/auth/logout');
        } catch (error) {
            if (all) {
                if (attempt === actionRef.current) setLoading(false);
                throw error;
            }
        }
        if (attempt === actionRef.current && marker === sessionMarker()) clearSession();
    };
    const addRole = async (role) => {
        await api.post('/profile/me/roles', { role });
        return reloadProfile();
    };
    const hasRole = (role) => Boolean(user?.roles?.includes(role));

    return (
        <AuthContext
            value={{
                user,
                loading,
                authError,
                reloadProfile,
                login,
                register,
                logout,
                addRole,
                isAuthenticated: Boolean(user),
                hasRole,
            }}
        >
            {children}
        </AuthContext>
    );
}
