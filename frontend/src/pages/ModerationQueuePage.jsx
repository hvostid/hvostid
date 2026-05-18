import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api from '../api/client';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 20;

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

export default function ModerationQueuePage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [page, setPage] = useState(() => {
        const parsed = parseInt(searchParams.get('page') ?? '0', 10);
        return Number.isFinite(parsed) ? Math.max(0, parsed) : 0;
    });

    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

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

        api.get(`/moderation/listings?page=${page}&size=${PAGE_SIZE}`, {
            signal: controller.signal,
        })
            .then((res) => setData(res.data))
            .catch((err) => {
                if (err.name === 'CanceledError') return;
                setError(extractDetail(err, 'Failed to load moderation queue'));
                setData(null);
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });

        return () => controller.abort();
    }, [page]);

    const listings = data?.content ?? [];
    const totalPages = data?.totalPages ?? 0;
    const totalElements = data?.totalElements ?? 0;

    return (
        <div className="space-y-4">
            <header className="flex items-center justify-between flex-wrap gap-3">
                <div className="flex items-center gap-3">
                    <h1 className="text-2xl font-bold text-gray-900">Moderation queue</h1>
                    {totalElements > 0 && (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800 ring-1 ring-inset ring-amber-200">
                            {totalElements}
                        </span>
                    )}
                </div>
                <nav className="text-sm">
                    <Link to="/moderation/flags" className="text-indigo-600 hover:underline">
                        View pending flags →
                    </Link>
                </nav>
            </header>

            {error && (
                <div className="bg-red-50 border border-red-200 text-red-700 rounded-md px-3 py-2 text-sm">
                    {error}
                </div>
            )}

            {loading ? (
                <QueueSkeleton />
            ) : listings.length === 0 ? (
                <div className="rounded-md border border-dashed border-gray-300 bg-white p-10 text-center text-gray-500">
                    No listings awaiting moderation.
                </div>
            ) : (
                <>
                    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
                        <table className="min-w-full text-sm">
                            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
                                <tr>
                                    <th className="px-3 py-2 font-medium">Title</th>
                                    <th className="px-3 py-2 font-medium">Seller</th>
                                    <th className="px-3 py-2 font-medium">Submitted</th>
                                    <th className="px-3 py-2 font-medium text-right">Action</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {listings.map((listing) => (
                                    <tr key={listing.id} className="hover:bg-gray-50">
                                        <td className="px-3 py-2">
                                            <p className="font-medium text-gray-900">
                                                {listing.title}
                                            </p>
                                            <p className="text-xs text-gray-500">
                                                {[listing.species, listing.breed]
                                                    .filter(Boolean)
                                                    .join(' / ')}
                                            </p>
                                        </td>
                                        <td className="px-3 py-2 text-gray-700">
                                            #{listing.sellerId}
                                        </td>
                                        <td className="px-3 py-2 text-gray-600">
                                            {formatDate(listing.updatedAt ?? listing.createdAt)}
                                        </td>
                                        <td className="px-3 py-2 text-right">
                                            <Link
                                                to={`/moderation/${listing.id}`}
                                                className="text-indigo-600 hover:underline"
                                            >
                                                Review
                                            </Link>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <Pagination page={page} totalPages={totalPages} onChange={setPage} />
                </>
            )}
        </div>
    );
}

function QueueSkeleton() {
    const rows = useMemo(() => ['r1', 'r2', 'r3', 'r4', 'r5'], []);
    return (
        <div className="bg-white border border-gray-200 rounded-lg overflow-hidden animate-pulse">
            <div className="h-9 bg-gray-100" />
            {rows.map((key) => (
                <div key={key} className="h-12 border-t border-gray-100 bg-gray-50/30" />
            ))}
        </div>
    );
}
