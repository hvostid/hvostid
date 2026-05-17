// pages/RegisterPage.jsx
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Input from '../components/Input';

export default function RegisterPage() {
    const navigate = useNavigate();
    const { register } = useAuth();

    // Form state
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    // Error state
    const [nameError, setNameError] = useState('');
    const [emailError, setEmailError] = useState('');
    const [passwordError, setPasswordError] = useState('');
    const [confirmPasswordError, setConfirmPasswordError] = useState('');
    const [generalError, setGeneralError] = useState('');

    // Loading state
    const [loading, setLoading] = useState(false);

    // Form validation before submission
    const validateForm = () => {
        let isValid = true;

        // Name validation
        if (!name.trim()) {
            setNameError('Name is required');
            isValid = false;
        } else if (name.trim().length < 2) {
            setNameError('Name must be at least 2 characters');
            isValid = false;
        } else {
            setNameError('');
        }

        // Email validation
        if (!email.trim()) {
            setEmailError('Email is required');
            isValid = false;
        } else if (!/^\S+@\S+\.\S+$/.test(email)) {
            setEmailError('Please enter a valid email (example: name@domain.com)');
            isValid = false;
        } else {
            setEmailError('');
        }

        // Password validation (minimum 8 characters)
        if (!password) {
            setPasswordError('Password is required');
            isValid = false;
        } else if (password.length < 8) {
            setPasswordError('Password must be at least 8 characters');
            isValid = false;
        } else {
            setPasswordError('');
        }

        // Confirm password validation
        if (password !== confirmPassword) {
            setConfirmPasswordError('Passwords do not match');
            isValid = false;
        } else {
            setConfirmPasswordError('');
        }

        return isValid;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setGeneralError('');

        if (!validateForm()) {
            return;
        }

        setLoading(true);

        try {
            // API currently only supports BUYER role registration
            await register(email, password, name);

            // Redirect to login page with success flag
            navigate('/login?registered=true');
        } catch (err) {
            console.error('Registration error:', err);

            if (err.response?.status === 409) {
                setGeneralError('User with this email already exists');
            } else if (err.response?.status === 400) {
                setGeneralError('Invalid data. Please check your email and password.');
            } else {
                setGeneralError('Server error. Please try again later.');
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-[calc(100vh-56px)] flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-md w-full space-y-8">
                {/* Header */}
                <div>
                    <h2 className="mt-6 text-center text-3xl font-extrabold text-gray-900">
                        Create an account
                    </h2>
                    <p className="mt-2 text-center text-sm text-gray-600">
                        Already have an account?{' '}
                        <Link
                            to="/login"
                            className="font-medium text-indigo-600 hover:text-indigo-500"
                        >
                            Sign in
                        </Link>
                    </p>
                </div>

                {/* Form */}
                <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
                    {/* General error */}
                    {generalError && (
                        <div className="rounded-md bg-red-50 p-4">
                            <div className="text-sm text-red-700">{generalError}</div>
                        </div>
                    )}

                    {/* Form fields */}
                    <div className="space-y-4">
                        <Input
                            id="name"
                            type="text"
                            label="Full name"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            error={nameError}
                            required
                            autoComplete="name"
                            placeholder="Ivan Petrov"
                        />

                        <Input
                            id="email"
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
                            type="password"
                            label="Password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            error={passwordError}
                            required
                            autoComplete="new-password"
                            placeholder="Minimum 8 characters"
                        />

                        <Input
                            id="confirmPassword"
                            type="password"
                            label="Confirm password"
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                            error={confirmPasswordError}
                            required
                            autoComplete="new-password"
                            placeholder="Repeat your password"
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
                            {loading ? 'Creating account...' : 'Create account'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
