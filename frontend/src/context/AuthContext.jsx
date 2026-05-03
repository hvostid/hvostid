import { createContext, useContext, useState, useEffect } from 'react';
import api from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    // On mount, check if we have a token and fetch profile
    useEffect(() => {
        const token = localStorage.getItem('accessToken');
        if (token) {
            api.get('/profile/me')
                .then((res) => setUser(res.data))
                .catch(() => {
                    localStorage.removeItem('accessToken');
                    localStorage.removeItem('refreshToken');
                })
                .finally(() => setLoading(false));
        } else {
            setLoading(false);
        }
    }, []);

    const login = async (email, password) => {
        const res = await api.post('/auth/login', { email, password });
        const { accessToken, refreshToken } = res.data;
        localStorage.setItem('accessToken', accessToken);
        localStorage.setItem('refreshToken', refreshToken);

        const profile = await api.get('/profile/me');
        setUser(profile.data);
        return profile.data;
    };

    const register = async (email, password, name) => {
        const res = await api.post('/auth/register', { email, password, name });
        return res.data;
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

    const isAuthenticated = !!user;

    const hasRole = (role) => {
        if (!user || !user.roles) return false;
        return user.roles.includes(role);
    };

    return (
        <AuthContext.Provider
            value={{ user, loading, login, register, logout, isAuthenticated, hasRole }}
        >
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within AuthProvider');
    }
    return context;
}
