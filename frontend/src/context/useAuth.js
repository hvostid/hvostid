import { use } from 'react';
import { AuthContext } from './AuthStore';

export function useAuth() {
    const context = use(AuthContext);
    if (!context) throw new Error('useAuth must be used within AuthProvider');
    return context;
}
