import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import api from '../api/client';
import { FLAG_REASON_LABEL, FLAG_STATUS_CLASS } from '../constants/moderation';
import { extractDetail, formatDateTime } from '../utils/format';

export default function ModerationDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();

    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [notFound, setNotFound] = useState(false);

    const [rejectOpen, setRejectOpen] = useState(false);
    const [actionBusy, setActionBusy] = useState(false);
    const [actionError, setActionError] = useState(null);

    useEffect(() => {
        const controller = new AbortController();
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setLoading(true);
        setError(null);
        setNotFound(false);
        setData(null);

        api.get(`/moderation/listings/${id}`, { signal: controller.signal })
            .then((res) => setData(res.data))
            .catch((err) => {
                if (err.name === 'CanceledError') return;
                if (err.response?.status === 404) {
                    setNotFound(true);
                } else {
                    setError(extractDetail(err, 'Failed to load listing'));
                }
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });

        return () => controller.abort();
    }, [id]);

    const handleApprove = async () => {
        if (actionBusy) return;
        setActionBusy(true);
        setActionError(null);
        try {
            await api.post(`/moderation/listings/${id}/approve`);
            navigate('/moderation');
        } catch (err) {
            setActionError(extractDetail(err, 'Failed to approve listing'));
            setActionBusy(false);
        }
    };

    if (loading) {
        return <DetailSkeleton />;
    }

    if (notFound) {
        return (
            <div className="text-center py-16">
                <h1 className="text-2xl font-bold text-gray-900">Listing not found</h1>
                <Link
                    to="/moderation"
                    className="inline-block mt-4 text-indigo-600 hover:underline"
                >
                    Back to queue
                </Link>
            </div>
        );
    }

    if (error) {
        return (
            <div className="bg-red-50 border border-red-200 text-red-700 rounded-md px-4 py-3 text-sm">
                {error}
            </div>
        );
    }

    if (!data) return null;

    const { listing, flags } = data;

    return (
        <div className="space-y-4">
            <nav className="text-sm text-gray-500">
                <Link to="/moderation" className="hover:text-indigo-600">
                    Moderation queue
                </Link>
                <span className="mx-1">/</span>
                <span className="text-gray-700">#{listing.id}</span>
            </nav>

            <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-6">
                <article className="space-y-4">
                    <header className="flex items-start justify-between gap-3 flex-wrap">
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900">{listing.title}</h1>
                            <p className="text-sm text-gray-500">
                                {[listing.species, listing.breed].filter(Boolean).join(' / ')}
                                {listing.city ? ` · ${listing.city}` : ''}
                            </p>
                        </div>
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800 ring-1 ring-inset ring-amber-200">
                            {listing.status}
                        </span>
                    </header>

                    <section className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
                        <h2 className="text-sm font-semibold text-gray-900">Listing</h2>
                        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2 text-sm">
                            <InfoRow label="Seller" value={`#${listing.sellerId}`} />
                            <InfoRow
                                label="Price"
                                value={
                                    listing.price !== null && listing.price !== undefined
                                        ? `${Number(listing.price).toLocaleString('ru-RU')} ₽`
                                        : null
                                }
                            />
                            <InfoRow
                                label="Age"
                                value={
                                    listing.age !== null && listing.age !== undefined
                                        ? `${listing.age} months`
                                        : null
                                }
                            />
                            <InfoRow label="Submitted" value={formatDateTime(listing.createdAt)} />
                            <InfoRow
                                label="Last update"
                                value={formatDateTime(listing.updatedAt ?? listing.createdAt)}
                            />
                            <InfoRow label="Passport" value={listing.passportId || '—'} />
                        </dl>
                        {listing.description && (
                            <div>
                                <h3 className="text-sm font-medium text-gray-700 mb-1">
                                    Description
                                </h3>
                                <p className="text-sm text-gray-800 whitespace-pre-wrap">
                                    {listing.description}
                                </p>
                            </div>
                        )}
                    </section>

                    <FlagsSection flags={flags} />

                    {listing.passportId && (
                        <section className="bg-white rounded-lg border border-gray-200 p-4">
                            <h2 className="text-sm font-semibold text-gray-900 mb-2">
                                Pet passport
                            </h2>
                            <Link
                                to={`/listings/${listing.id}`}
                                className="text-sm text-indigo-600 hover:underline"
                                target="_blank"
                                rel="noopener noreferrer"
                            >
                                Open full listing view (with passport and photos) ↗
                            </Link>
                        </section>
                    )}
                </article>

                <aside className="space-y-3 lg:sticky lg:top-4 lg:self-start">
                    <section className="bg-white rounded-lg border border-gray-200 p-4 space-y-2">
                        <h2 className="text-sm font-semibold text-gray-900">Decision</h2>
                        <button
                            type="button"
                            onClick={handleApprove}
                            disabled={actionBusy}
                            className="w-full px-3 py-2 rounded-md bg-green-600 text-white text-sm hover:bg-green-700 disabled:opacity-60"
                        >
                            {actionBusy ? 'Working…' : 'Approve & publish'}
                        </button>
                        <button
                            type="button"
                            onClick={() => setRejectOpen(true)}
                            disabled={actionBusy}
                            className="w-full px-3 py-2 rounded-md border border-gray-200 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-60"
                        >
                            Return to draft…
                        </button>
                        {actionError && <p className="text-xs text-red-600">{actionError}</p>}
                    </section>
                </aside>
            </div>

            {rejectOpen && (
                <RejectDialog
                    listingId={listing.id}
                    onClose={() => setRejectOpen(false)}
                    onDone={() => navigate('/moderation')}
                />
            )}
        </div>
    );
}

function FlagsSection({ flags }) {
    if (!flags || flags.length === 0) {
        return (
            <section className="bg-white rounded-lg border border-gray-200 p-4">
                <h2 className="text-sm font-semibold text-gray-900 mb-1">Flags</h2>
                <p className="text-sm text-gray-500">No flags reported against this listing.</p>
            </section>
        );
    }

    return (
        <section className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
            <h2 className="text-sm font-semibold text-gray-900">Flags ({flags.length})</h2>
            <ul className="divide-y divide-gray-100 border border-gray-100 rounded-md">
                {flags.map((flag) => (
                    <li key={flag.id} className="px-3 py-2 text-sm">
                        <div className="flex items-center justify-between gap-3 flex-wrap">
                            <div>
                                <p className="font-medium text-gray-900">
                                    {FLAG_REASON_LABEL[flag.reason] || flag.reason}
                                </p>
                                <p className="text-xs text-gray-500">
                                    Reporter #{flag.reporterId} · {formatDateTime(flag.createdAt)}
                                </p>
                            </div>
                            <span
                                className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ring-1 ring-inset ${
                                    FLAG_STATUS_CLASS[flag.status] ||
                                    'bg-gray-100 text-gray-700 ring-gray-200'
                                }`}
                            >
                                {flag.status}
                            </span>
                        </div>
                        {flag.description && (
                            <p className="mt-1 text-sm text-gray-700 whitespace-pre-wrap">
                                {flag.description}
                            </p>
                        )}
                    </li>
                ))}
            </ul>
        </section>
    );
}

function RejectDialog({ listingId, onClose, onDone }) {
    const [comment, setComment] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!comment.trim()) {
            setError('Comment is required');
            return;
        }
        setSubmitting(true);
        setError(null);
        try {
            await api.post(`/moderation/listings/${listingId}/reject`, { comment });
            onDone();
        } catch (err) {
            setError(extractDetail(err, 'Failed to reject listing'));
            setSubmitting(false);
        }
    };

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
            role="dialog"
            aria-modal="true"
        >
            <div className="bg-white rounded-lg shadow-lg w-full max-w-md p-5 space-y-4">
                <div className="flex items-center justify-between">
                    <h3 className="text-lg font-semibold text-gray-900">Return to draft</h3>
                    <button
                        type="button"
                        onClick={onClose}
                        className="text-gray-400 hover:text-gray-600 text-xl leading-none"
                        aria-label="Close"
                    >
                        ×
                    </button>
                </div>
                <form onSubmit={handleSubmit} className="space-y-3">
                    <label className="block">
                        <span className="block text-xs font-medium text-gray-700 mb-1">
                            Comment for the seller
                        </span>
                        <textarea
                            value={comment}
                            onChange={(e) => setComment(e.target.value)}
                            rows={4}
                            maxLength={500}
                            required
                            placeholder="What needs to be fixed before this listing can be published?"
                            className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                        />
                    </label>
                    {error && <p className="text-sm text-red-600">{error}</p>}
                    <div className="flex justify-end gap-2">
                        <button
                            type="button"
                            onClick={onClose}
                            className="px-3 py-2 rounded-md border border-gray-200 text-sm text-gray-700 hover:bg-gray-50"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={submitting}
                            className="px-3 py-2 rounded-md bg-amber-600 text-white text-sm hover:bg-amber-700 disabled:opacity-60"
                        >
                            {submitting ? 'Submitting…' : 'Return to draft'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

function InfoRow({ label, value }) {
    return (
        <div className="flex gap-2">
            <dt className="text-gray-500 w-32 shrink-0">{label}</dt>
            <dd className="text-gray-900 break-words">{value || '—'}</dd>
        </div>
    );
}

function DetailSkeleton() {
    return (
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-6 animate-pulse">
            <div className="space-y-3">
                <div className="h-8 bg-gray-200 rounded w-2/3" />
                <div className="h-48 bg-gray-200 rounded-lg" />
                <div className="h-32 bg-gray-200 rounded-lg" />
            </div>
            <div className="space-y-3">
                <div className="h-32 bg-gray-200 rounded-lg" />
            </div>
        </div>
    );
}
