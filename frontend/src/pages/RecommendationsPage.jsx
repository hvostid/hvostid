// pages/RecommendationsPage.jsx
import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { getRecommendations } from '../api/matching';
import LoadingSpinner from '../components/LoadingSpinner';
import ListingCard from '../components/ListingCard';
import ListingCardSkeleton from '../components/ListingCardSkeleton';

const COMPATIBILITY_STYLES = {
    GREAT: 'bg-green-100 text-green-800',
    GOOD: 'bg-blue-100 text-blue-800',
    RISKY: 'bg-yellow-100 text-yellow-800',
    NOT_RECOMMENDED: 'bg-red-100 text-red-800',
};

const COMPATIBILITY_LABELS = {
    GREAT: 'Отлично',
    GOOD: 'Хорошо',
    RISKY: 'Есть риски',
    NOT_RECOMMENDED: 'Не рекомендуется',
};

const MIN_SCORE_OPTIONS = [
    { value: 0, label: 'Все' },
    { value: 30, label: '≥ 30%' },
    { value: 50, label: '≥ 50%' },
    { value: 70, label: '≥ 70%' },
    { value: 85, label: '≥ 85%' },
];

export default function RecommendationsPage() {
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [recommendations, setRecommendations] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [minScore, setMinScore] = useState(0);
    const [loadingMore, setLoadingMore] = useState(false);

    const loadRecommendations = useCallback(async (pageNum, currentMinScore) => {
        setLoadingMore(true);
        try {
            const data = await getRecommendations(pageNum, 12, currentMinScore);
            if (pageNum === 0) {
                setRecommendations(data.content || []);
            } else {
                setRecommendations((prev) => [...prev, ...(data.content || [])]);
            }
            setTotalPages(data.totalPages || 0);
            setTotalElements(data.totalElements || 0);
        } catch (err) {
            console.error('Failed to load recommendations:', err);
            if (err.response?.status === 400) {
                setError('Анкета не заполнена. Пожалуйста, заполните анкету совместимости.');
            } else {
                setError('Не удалось загрузить рекомендации');
            }
        } finally {
            setLoading(false);
            setLoadingMore(false);
        }
    }, []);

    useEffect(() => {
        setLoading(true);
        setPage(0);
        loadRecommendations(0, minScore);
    }, [minScore, loadRecommendations]);

    const loadMore = () => {
        if (page + 1 < totalPages) {
            const nextPage = page + 1;
            setPage(nextPage);
            loadRecommendations(nextPage, minScore);
        }
    };

    const handleMinScoreChange = (e) => {
        setMinScore(parseInt(e.target.value, 10));
    };

    if (loading && page === 0) {
        return (
            <div>
                <div className="flex justify-between items-center mb-6">
                    <h1 className="text-2xl font-bold text-gray-900">Рекомендации для вас</h1>
                    <div className="w-32 h-8 bg-gray-200 rounded animate-pulse" />
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                    {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => (
                        <ListingCardSkeleton key={i} />
                    ))}
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="max-w-2xl mx-auto text-center py-12">
                <div className="bg-teal-50 border border-indigo-200 text-teal-800 px-6 py-4 rounded-lg">
                    <p className="mb-4">{error}</p>
                    <Link
                        to="/profile/questionnaire"
                        className="inline-block px-4 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700 transition-colors"
                    >
                        Заполнить анкету
                    </Link>
                </div>
            </div>
        );
    }

    return (
        <div>
            <div className="flex justify-between items-center mb-6 flex-wrap gap-4">
                <h1 className="text-2xl font-bold text-gray-900">Рекомендации для вас</h1>
                <div className="flex items-center gap-3">
                    <label className="text-sm text-gray-600">Минимальный score:</label>
                    <select
                        value={minScore}
                        onChange={handleMinScoreChange}
                        className="px-3 py-1.5 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                    >
                        {MIN_SCORE_OPTIONS.map((opt) => (
                            <option key={opt.value} value={opt.value}>
                                {opt.label}
                            </option>
                        ))}
                    </select>
                </div>
            </div>

            {totalElements > 0 && (
                <p className="text-sm text-gray-500 mb-4">Найдено {totalElements} рекомендаций</p>
            )}

            {recommendations.length === 0 ? (
                <div className="text-center py-12 bg-gray-50 rounded-lg">
                    <p className="text-gray-500">Нет рекомендаций с выбранным фильтром</p>
                    {minScore > 0 && (
                        <button
                            onClick={() => setMinScore(0)}
                            className="mt-4 text-indigo-600 hover:text-indigo-700 transition-colors"
                        >
                            Сбросить фильтр
                        </button>
                    )}
                </div>
            ) : (
                <>
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                        {recommendations.map((item) => {
                            const listing = item.listing;
                            const compatibilityStyle =
                                COMPATIBILITY_STYLES[item.level] ||
                                COMPATIBILITY_STYLES.NOT_RECOMMENDED;
                            const compatibilityLabel =
                                COMPATIBILITY_LABELS[item.level] || item.level;

                            return (
                                <div key={listing.id} className="relative group">
                                    <div className="transition-transform duration-300 ease-out group-hover:scale-[1.02] group-hover:shadow-xl">
                                        <ListingCard listing={listing} />
                                    </div>

                                    <div className="absolute top-2 left-2 z-10 transition-all duration-300 ease-out group-hover:scale-110">
                                        <div
                                            className={`px-2 py-1 rounded-full text-xs font-medium shadow-sm ${compatibilityStyle}`}
                                        >
                                            {item.score}% • {compatibilityLabel}
                                        </div>
                                    </div>

                                    <Link
                                        to={`/listings/${listing.id}/match`}
                                        className="absolute inset-0 rounded-lg flex items-center justify-center transition-all duration-300 ease-out bg-black/10 opacity-0 group-hover:opacity-70"
                                    />
                                </div>
                            );
                        })}
                    </div>

                    {page + 1 < totalPages && (
                        <div className="text-center mt-8">
                            <button
                                onClick={loadMore}
                                disabled={loadingMore}
                                className="px-6 py-2 bg-gray-100 text-gray-700 rounded-md hover:bg-gray-200 transition-colors disabled:opacity-50"
                            >
                                {loadingMore ? (
                                    <span className="flex items-center gap-2">
                                        <LoadingSpinner size="sm" />
                                        Загрузка...
                                    </span>
                                ) : (
                                    'Загрузить ещё'
                                )}
                            </button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}
