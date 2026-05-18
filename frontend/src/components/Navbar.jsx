import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
    const { user, isAuthenticated, hasRole, logout } = useAuth();
    const navigate = useNavigate();

    const handleLogout = async () => {
        await logout();
        navigate('/login');
    };

    return (
        <nav className="bg-white shadow-sm border-b border-gray-200">
            <div className="max-w-7xl mx-auto px-4">
                <div className="flex items-center justify-between h-16">
                    <div className="flex items-center gap-6">
                        <Link to="/" className="flex items-center gap-2">
                            <img src="/icon.svg" alt="HvostID logo" className="h-20 w-20" />
                            <span className="text-lg font-bold text-indigo-600">HvostID</span>
                        </Link>
                        <Link to="/" className="text-sm text-gray-600 hover:text-gray-900">
                            Catalog
                        </Link>
                        {isAuthenticated && (
                            <Link
                                to="/recommendations"
                                className="text-sm text-gray-600 hover:text-gray-900"
                            >
                                Recommendations
                            </Link>
                        )}
                        {hasRole('SELLER') && (
                            <Link
                                to="/my-listings"
                                className="text-sm text-gray-600 hover:text-gray-900"
                            >
                                My listings
                            </Link>
                        )}
                        {hasRole('MODERATOR') && (
                            <Link
                                to="/moderation"
                                className="text-sm text-gray-600 hover:text-gray-900"
                            >
                                Moderation
                            </Link>
                        )}
                    </div>

                    <div className="flex items-center gap-4">
                        {isAuthenticated ? (
                            <>
                                <Link
                                    to="/profile"
                                    className="text-sm text-gray-600 hover:text-gray-900"
                                >
                                    {user?.name || 'Profile'}
                                </Link>
                                <button
                                    onClick={handleLogout}
                                    className="text-sm text-gray-500 hover:text-gray-700"
                                >
                                    Logout
                                </button>
                            </>
                        ) : (
                            <>
                                <Link
                                    to="/login"
                                    className="text-sm text-gray-600 hover:text-gray-900"
                                >
                                    Login
                                </Link>
                                <Link
                                    to="/register"
                                    className="text-sm bg-indigo-600 text-white px-3 py-1.5 rounded-md hover:bg-indigo-700"
                                >
                                    Register
                                </Link>
                            </>
                        )}
                    </div>
                </div>
            </div>
        </nav>
    );
}
