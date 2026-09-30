import { useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../context/useAuth';

export default function AccountSecurity() {
    const { user, logout } = useAuth();
    const [sessions, setSessions] = useState([]);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [busy, setBusy] = useState(false);
    useEffect(() => {
        const controller = new AbortController();
        api.get('/auth/sessions', { signal: controller.signal })
            .then(({ data }) => setSessions(data))
            .catch((err) => {
                if (!controller.signal.aborted)
                    setError(err.response?.data?.detail || 'Sessions could not be loaded.');
            });
        return () => controller.abort();
    }, []);
    async function perform(action, success) {
        setBusy(true);
        setError('');
        setMessage('');
        try {
            await action();
            setMessage(success);
        } catch (err) {
            setError(
                err.response?.status === 503
                    ? 'Email delivery is temporarily unavailable. Please try again later.'
                    : err.response?.data?.detail || 'The operation failed. Please retry.'
            );
        } finally {
            setBusy(false);
        }
    }
    return (
        <section
            className="mt-6 rounded-lg bg-white p-6 shadow space-y-4"
            aria-labelledby="account-security"
        >
            <h2 id="account-security" className="text-xl font-semibold">
                Account security
            </h2>
            <p>Email: {user?.emailVerified ? 'Verified' : 'Not verified'}</p>
            {!user?.emailVerified && (
                <button
                    disabled={busy}
                    className="text-indigo-700 underline"
                    onClick={() =>
                        perform(
                            () => api.post('/auth/email-verification/request'),
                            'Verification email requested. Check your inbox shortly.'
                        )
                    }
                >
                    Send verification email
                </button>
            )}
            {error && (
                <p role="alert" className="text-red-700">
                    {error}
                </p>
            )}
            {message && (
                <p role="status" className="text-green-700">
                    {message}
                </p>
            )}
            <h3 className="font-semibold">Active sessions</h3>
            <ul className="space-y-2">
                {sessions.map((session) => (
                    <li key={session.id} className="flex justify-between gap-3">
                        <span>Signed in {new Date(session.createdAt).toLocaleString()}</span>
                        <button
                            disabled={busy}
                            className="text-red-700 underline"
                            aria-label={`Revoke session ${session.id}`}
                            onClick={() =>
                                perform(async () => {
                                    await api.delete(`/auth/sessions/${session.id}`);
                                    setSessions((current) =>
                                        current.filter((item) => item.id !== session.id)
                                    );
                                }, 'Session revoked.')
                            }
                        >
                            Revoke
                        </button>
                    </li>
                ))}
            </ul>
            <button
                disabled={busy}
                className="rounded bg-red-700 px-4 py-2 text-white"
                onClick={() => perform(() => logout(true), '')}
            >
                Sign out on all devices
            </button>
        </section>
    );
}
