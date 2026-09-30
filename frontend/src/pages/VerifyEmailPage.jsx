import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api from '../api/client';
import { useAuth } from '../context/useAuth';

export default function VerifyEmailPage() {
    const [params] = useSearchParams();
    const token = params.get('token');
    const { isAuthenticated, reloadProfile } = useAuth();
    const [status, setStatus] = useState('');
    const [busy, setBusy] = useState(false);
    const [verified, setVerified] = useState(false);
    async function verify() {
        setBusy(true);
        setStatus('');
        try {
            await api.post('/auth/email-verification/confirm', { token });
            setVerified(true);
            if (isAuthenticated) await reloadProfile();
        } catch (err) {
            setStatus(
                err.response?.data?.detail ||
                    'The link is invalid, expired, or temporarily unavailable.'
            );
        } finally {
            setBusy(false);
        }
    }
    return (
        <div className="mx-auto max-w-md space-y-4 rounded-lg bg-white p-6 shadow">
            <h1 className="text-2xl font-semibold">Verify email</h1>
            {verified ? (
                <p role="status">Your email is verified.</p>
            ) : token ? (
                <button
                    disabled={busy}
                    onClick={verify}
                    className="rounded bg-indigo-600 px-4 py-2 text-white"
                >
                    {busy ? 'Verifying...' : 'Confirm my email'}
                </button>
            ) : (
                <p role="alert">
                    The verification link is missing. Request a new email from your profile.
                </p>
            )}
            {status && (
                <p role="alert" className="text-red-700">
                    {status}
                </p>
            )}
            <Link
                to={isAuthenticated ? '/profile' : '/login'}
                className="text-indigo-700 underline"
            >
                {isAuthenticated ? 'Return to profile' : 'Sign in'}
            </Link>
        </div>
    );
}
