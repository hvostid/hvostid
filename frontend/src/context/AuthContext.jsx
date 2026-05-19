import { createContext, useContext, useState, useEffect } from 'react';
import api from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    // On mount, check if we have a token and fetch profile
    useEffect(() => {
        let isMounted = true;

        const initAuth = async () => {
            const token = localStorage.getItem('accessToken');
            if (!token) {
                if (isMounted) setLoading(false);
                return;
            }

            try {
                const profile = await api.get('/profile/me');
                if (isMounted) {
                    setUser(profile.data);
                    setLoading(false);
                }
            } catch {
                if (isMounted) {
                    localStorage.removeItem('accessToken');
                    localStorage.removeItem('refreshToken');
                    setLoading(false);
                }
            }
        };

        initAuth();

        return () => {
            isMounted = false;
        };
    }, []);

    const login = async (email, password) => {
        const { data } = await api.post('/auth/login', { email, password });
        const { accessToken, refreshToken } = data;
        localStorage.setItem('accessToken', accessToken);
        localStorage.setItem('refreshToken', refreshToken);

        const profile = await api.get('/profile/me');
        setUser(profile.data);
        return profile.data;
    };

    const register = async (email, password, name) => {
        const { data } = await api.post('/auth/register', { email, password, name });
        return data;
    };

    const logout = async () => {
        try {
            await api.post('/auth/logout');
        } catch {
            // Ignore errors on logout
        }
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        setUser(null);
    };

    const addRole = async (role) => {
        try {
            await api.post('/profile/me/roles', { role });
            const profile = await api.get('/profile/me');
            setUser(profile.data);
            return profile.data;
        } catch (error) {
            console.error('Failed to add role:', error);
            throw error;
        }
    };

    const isAuthenticated = !!user;

    const hasRole = (role) => {
        if (!user?.roles) return false;
        return user.roles.includes(role);
    };

    return (
        <AuthContext
            value={{
                user,
                loading,
                login,
                register,
                logout,
                addRole,
                isAuthenticated,
                hasRole,
            }}
        >
            {children}
        </AuthContext>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within AuthProvider');
    }
    return context;
}
