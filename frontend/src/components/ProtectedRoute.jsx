import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function ProtectedRoute({ children, requiredRole }) {
    const { isAuthenticated, hasRole, loading } = useAuth();
    const location = useLocation();

    if (loading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="text-gray-500">Loading...</div>
            </div>
        );
    }

    if (!isAuthenticated) {
        return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname)}`} replace />;
    }

    if (requiredRole) {
        const allowed = Array.isArray(requiredRole) ? requiredRole : [requiredRole];
        if (!allowed.some((role) => hasRole(role))) {
            return (
                <div className="text-center py-12">
                    <h2 className="text-xl font-semibold text-gray-800">Access denied</h2>
                    <p className="text-gray-500 mt-2">
                        You do not have the required role: {allowed.join(' or ')}
                    </p>
                </div>
            );
        }
    }

    return children;
}
