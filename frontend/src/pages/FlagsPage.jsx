import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api from '../api/client';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 20;

const REASON_LABEL = {
    FAKE_INFO: 'Fake information',
    ANIMAL_ABUSE: 'Animal abuse',
    SCAM: 'Scam',
    INAPPROPRIATE: 'Inappropriate content',
    OTHER: 'Other',
};

function extractDetail(err, fallback) {
    const raw = err?.response?.data?.detail ?? err?.message;
    return typeof raw === 'string' && raw ? raw : fallback;
}

function formatDate(iso) {
    if (!iso) return '—';
    try {
        return new Date(iso).toLocaleString();
    } catch {
        return iso;
    }
}

export default function FlagsPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [page, setPage] = useState(() => {
        const parsed = parseInt(searchParams.get('page') ?? '0', 10);
        return Number.isFinite(parsed) ? Math.max(0, parsed) : 0;
    });

    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    // Track in-flight decisions per-flag so two buttons on the same row can't
    // double-submit and so other rows stay interactive.
    const [busyById, setBusyById] = useState({});
    const [reloadTick, setReloadTick] = useState(0);

    useEffect(() => {
        const params = new URLSearchParams();
        if (page > 0) params.set('page', String(page));
        setSearchParams(params, { replace: true });
    }, [page, setSearchParams]);

    useEffect(() => {
        const controller = new AbortController();
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setLoading(true);
        setError(null);

        api.get(`/moderation/flags?page=${page}&size=${PAGE_SIZE}`, {
            signal: controller.signal,
        })
            .then((res) => setData(res.data))
            .catch((err) => {
                if (err.name === 'CanceledError') return;
                setError(extractDetail(err, 'Failed to load flags'));
                setData(null);
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });

        return () => controller.abort();
    }, [page, reloadTick]);

    const handleDecision = async (flagId, decision) => {
        if (busyById[flagId]) return;
        setBusyById((prev) => ({ ...prev, [flagId]: true }));
        try {
            await api.post(`/moderation/flags/${flagId}/review`, { decision });
            // The simplest correct refresh: reload the current page so reviewed
            // flags drop out and pagination totals stay accurate.
            setReloadTick((tick) => tick + 1);
        } catch (err) {
            setError(extractDetail(err, 'Failed to update flag'));
        } finally {
            setBusyById((prev) => {
                const next = { ...prev };
                delete next[flagId];
                return next;
            });
        }
    };

    const flags = data?.content ?? [];
    const totalPages = data?.totalPages ?? 0;
    const totalElements = data?.totalElements ?? 0;

    return (
        <div className="space-y-4">
            <header className="flex items-center justify-between flex-wrap gap-3">
                <div className="flex items-center gap-3">
                    <h1 className="text-2xl font-bold text-gray-900">Pending flags</h1>
                    {totalElements > 0 && (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800 ring-1 ring-inset ring-amber-200">
                            {totalElements}
                        </span>
                    )}
                </div>
                <nav className="text-sm">
                    <Link to="/moderation" className="text-indigo-600 hover:underline">
                        ← Moderation queue
                    </Link>
                </nav>
            </header>

            {error && (
                <div className="bg-red-50 border border-red-200 text-red-700 rounded-md px-3 py-2 text-sm">
                    {error}
                </div>
            )}

            {loading ? (
                <FlagsSkeleton />
            ) : flags.length === 0 ? (
                <div className="rounded-md border border-dashed border-gray-300 bg-white p-10 text-center text-gray-500">
                    No flags awaiting review.
                </div>
            ) : (
                <>
                    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
                        <table className="min-w-full text-sm">
                            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
                                <tr>
                                    <th className="px-3 py-2 font-medium">Listing</th>
                                    <th className="px-3 py-2 font-medium">Reason</th>
                                    <th className="px-3 py-2 font-medium">Reporter</th>
                                    <th className="px-3 py-2 font-medium">Submitted</th>
                                    <th className="px-3 py-2 font-medium text-right">Decision</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {flags.map((flag) => {
                                    const busy = Boolean(busyById[flag.id]);
                                    return (
                                        <tr key={flag.id} className="align-top">
                                            <td className="px-3 py-2">
                                                <Link
                                                    to={`/moderation/${flag.listingId}`}
                                                    className="text-indigo-600 hover:underline font-medium"
                                                >
                                                    Listing #{flag.listingId}
                                                </Link>
                                                {flag.description && (
                                                    <p className="mt-1 text-xs text-gray-600 whitespace-pre-wrap">
                                                        {flag.description}
                                                    </p>
                                                )}
                                            </td>
                                            <td className="px-3 py-2 text-gray-700">
                                                {REASON_LABEL[flag.reason] || flag.reason}
                                            </td>
                                            <td className="px-3 py-2 text-gray-700">
                                                #{flag.reporterId}
                                            </td>
                                            <td className="px-3 py-2 text-gray-600">
                                                {formatDate(flag.createdAt)}
                                            </td>
                                            <td className="px-3 py-2 text-right space-x-2 whitespace-nowrap">
                                                <button
                                                    type="button"
                                                    onClick={() =>
                                                        handleDecision(flag.id, 'REVIEWED')
                                                    }
                                                    disabled={busy}
                                                    className="px-2.5 py-1 rounded-md bg-green-600 text-white text-xs hover:bg-green-700 disabled:opacity-60"
                                                >
                                                    Reviewed
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() =>
                                                        handleDecision(flag.id, 'DISMISSED')
                                                    }
                                                    disabled={busy}
                                                    className="px-2.5 py-1 rounded-md border border-gray-200 text-xs text-gray-700 hover:bg-gray-50 disabled:opacity-60"
                                                >
                                                    Dismiss
                                                </button>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                    <Pagination page={page} totalPages={totalPages} onChange={setPage} />
                </>
            )}
        </div>
    );
}

function FlagsSkeleton() {
    return (
        <div className="bg-white border border-gray-200 rounded-lg overflow-hidden animate-pulse">
            <div className="h-9 bg-gray-100" />
            {['r1', 'r2', 'r3', 'r4', 'r5'].map((key) => (
                <div key={key} className="h-12 border-t border-gray-100 bg-gray-50/30" />
            ))}
        </div>
    );
}
