import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import api from '../api/client';

export default function SellerContact({ sellerId, authenticated }) {
    const location = useLocation();
    const [contact, setContact] = useState(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    if (!authenticated)
        return (
            <Link
                className="text-indigo-700 underline"
                to={`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`}
            >
                Sign in to contact the seller
            </Link>
        );
    async function loadContact() {
        setLoading(true);
        setError('');
        try {
            const { data } = await api.get(`/users/${sellerId}/contact`);
            setContact(data);
        } catch (err) {
            setError(
                err.response?.status === 404
                    ? 'This seller has not shared a contact number.'
                    : 'The contact could not be loaded. Please retry.'
            );
        } finally {
            setLoading(false);
        }
    }
    return (
        <div className="mt-4 space-y-2">
            {contact ? (
                <p>
                    {contact.name}:{' '}
                    <a
                        className="text-indigo-700 underline"
                        href={`tel:${contact.phone.replace(/[^+0-9]/g, '')}`}
                    >
                        {contact.phone}
                    </a>
                </p>
            ) : (
                <button
                    disabled={loading}
                    onClick={loadContact}
                    className="rounded bg-indigo-600 px-4 py-2 text-white"
                >
                    {loading ? 'Loading contact...' : 'Contact seller'}
                </button>
            )}
            {error && (
                <p role="status" className="text-sm text-gray-700">
                    {error}
                </p>
            )}
        </div>
    );
}
