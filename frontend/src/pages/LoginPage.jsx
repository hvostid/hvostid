import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Input from '../components/Input';

export default function LoginPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const { login } = useAuth();

    // Form state
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');

    // Error state
    const [emailError, setEmailError] = useState('');
    const [passwordError, setPasswordError] = useState('');
    const [generalError, setGeneralError] = useState('');

    // Loading state
    const [loading, setLoading] = useState(false);

    // Get redirect URL and registration success flag from query params
    const params = new URLSearchParams(location.search);
    const from = params.get('redirect') || '/';
    const isRegistered = params.get('registered') === 'true';

    // Form validation before submission
    const validateForm = () => {
        let isValid = true;

        // Email validation with strict regex
        if (!email.trim()) {
            setEmailError('Email is required');
            isValid = false;
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            setEmailError('Please enter a valid email (example: name@domain.com)');
            isValid = false;
        } else {
            setEmailError('');
        }

        // Password validation
        if (!password) {
            setPasswordError('Password is required');
            isValid = false;
        } else {
            setPasswordError('');
        }

        return isValid;
    };

    // Form submission handler
    const handleSubmit = async (e) => {
        e.preventDefault();
        setGeneralError('');

        if (!validateForm()) {
            return;
        }

        setLoading(true);

        try {
            await login(email, password);
            navigate(from, { replace: true });
        } catch (err) {
            console.error('Login error:', err);

            // Security: Do not distinguish between 401 and 404 to prevent user enumeration
            if (err.response?.status === 401 || err.response?.status === 404) {
                setGeneralError('Invalid email or password');
            } else {
                setGeneralError('Server error. Please try again later.');
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="flex-1 flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-md w-full space-y-8">
                {/* Header */}
                <div>
                    <h2 className="mt-6 text-center text-3xl font-extrabold text-gray-900">
                        Sign in to your account
                    </h2>
                    <p className="mt-2 text-center text-sm text-gray-600">
                        Or{' '}
                        <Link
                            to="/register"
                            className="font-medium text-indigo-600 hover:text-indigo-500"
                        >
                            create a new account
                        </Link>
                    </p>
                </div>

                {/* Form */}
                <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
                    {/* Registration success message */}
                    {isRegistered && (
                        <div className="rounded-md bg-green-50 p-4">
                            <div className="text-sm text-green-700">
                                Registration successful! You can now log in.
                            </div>
                        </div>
                    )}

                    {/* General error (wrong password, etc.) */}
                    {generalError && (
                        <div className="rounded-md bg-red-50 p-4">
                            <div className="text-sm text-red-700">{generalError}</div>
                        </div>
                    )}

                    {/* Form fields */}
                    <div className="space-y-4">
                        <Input
                            id="email"
                            name="email"
                            type="email"
                            label="Email"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            error={emailError}
                            required
                            autoComplete="email"
                            placeholder="ivan@example.com"
                        />

                        <Input
                            id="password"
                            name="password"
                            type="password"
                            label="Password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            error={passwordError}
                            required
                            autoComplete="current-password"
                            placeholder="••••••••"
                        />
                    </div>

                    {/* Submit button */}
                    <div>
                        <button
                            type="submit"
                            disabled={loading}
                            className={`
                w-full flex justify-center py-2 px-4 border border-transparent 
                rounded-md shadow-sm text-sm font-medium text-white 
                bg-indigo-600 hover:bg-indigo-700 focus:outline-none 
                focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500
                ${loading ? 'opacity-50 cursor-not-allowed' : ''}
              `}
                        >
                            {loading ? 'Signing in...' : 'Sign in'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
