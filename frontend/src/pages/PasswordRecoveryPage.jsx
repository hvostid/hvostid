import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api from '../api/client';
import Input from '../components/Input';

export default function PasswordRecoveryPage() {
    const [params] = useSearchParams();
    const token = params.get('token');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmation, setConfirmation] = useState('');
    const [error, setError] = useState('');
    const [complete, setComplete] = useState(false);
    const [busy, setBusy] = useState(false);
    async function submit(event) {
        event.preventDefault();
        setError('');
        if (token && (password.length < 8 || new TextEncoder().encode(password).length > 72)) {
            setError('Use at least 8 characters and at most 72 UTF-8 bytes.');
            return;
        }
        if (token && password !== confirmation) {
            setError('Passwords do not match.');
            return;
        }
        setBusy(true);
        try {
            await api.post(
                token ? '/auth/password-reset/confirm' : '/auth/password-reset/request',
                token ? { token, password } : { email: email.trim().toLowerCase() }
            );
            setComplete(true);
            setPassword('');
            setConfirmation('');
        } catch (err) {
            setError(
                err.response?.status === 503
                    ? 'Email delivery is temporarily unavailable. Please try again later.'
                    : err.response?.data?.detail || 'The request failed. Please retry.'
            );
        } finally {
            setBusy(false);
        }
    }
    return (
        <div className="mx-auto max-w-md space-y-6 rounded-lg bg-white p-6 shadow">
            <h1 className="text-2xl font-semibold">
                {token ? 'Choose a new password' : 'Recover your account'}
            </h1>
            {complete ? (
                <p role="status">
                    {token
                        ? 'Password changed. All previous sessions have been revoked.'
                        : 'If this email is registered, a recovery email has been queued. Check your inbox shortly.'}
                </p>
            ) : (
                <form onSubmit={submit} className="space-y-4">
                    {token ? (
                        <>
                            <Input
                                id="new-password"
                                label="New password"
                                type="password"
                                autoComplete="new-password"
                                required
                                minLength={8}
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                            />
                            <Input
                                id="confirm-password"
                                label="Confirm password"
                                type="password"
                                autoComplete="new-password"
                                required
                                value={confirmation}
                                onChange={(e) => setConfirmation(e.target.value)}
                            />
                        </>
                    ) : (
                        <Input
                            id="recovery-email"
                            label="Email"
                            type="email"
                            autoComplete="email"
                            maxLength={255}
                            required
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                        />
                    )}
                    {error && (
                        <p role="alert" className="text-red-700">
                            {error}
                        </p>
                    )}
                    <button disabled={busy} className="rounded bg-indigo-600 px-4 py-2 text-white">
                        {busy ? 'Submitting...' : token ? 'Change password' : 'Send recovery link'}
                    </button>
                </form>
            )}
            <Link to="/login" className="text-indigo-700 underline">
                Back to sign in
            </Link>
        </div>
    );
}
