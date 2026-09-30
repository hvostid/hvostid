// pages/MatchResultPage.jsx
import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getMatchScore } from '../api/matching';
import LoadingSpinner from '../components/LoadingSpinner';

const LEVEL_COLORS = {
    GREAT: { bg: 'bg-green-100', text: 'text-green-800', border: 'border-green-200' },
    GOOD: { bg: 'bg-blue-100', text: 'text-blue-800', border: 'border-blue-200' },
    RISKY: { bg: 'bg-yellow-100', text: 'text-yellow-800', border: 'border-yellow-200' },
    NOT_RECOMMENDED: { bg: 'bg-red-100', text: 'text-red-800', border: 'border-red-200' },
};

const LEVEL_LABELS = {
    GREAT: 'Excellent match',
    GOOD: 'Good match',
    RISKY: 'Some risks',
    NOT_RECOMMENDED: 'Not recommended',
};

const FACTOR_LABELS = {
    living_space: 'Housing type',
    living_area: 'Living area',
    has_yard: 'Outdoor space',
    has_children: 'Children',
    allergies: 'Allergies',
    pet_experience: 'Pet ownership experience',
    activity_level: 'Activity level',
    monthly_budget: 'Monthly budget',
    work_schedule: 'Work schedule',
    ready_for_adaptation: 'Readiness for adaptation',
    preferred_species: 'Preferred species',
    preferred_breed: 'Preferred breed',
};

export default function MatchResultPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [result, setResult] = useState(null);

    useEffect(() => {
        const loadMatch = async () => {
            try {
                const data = await getMatchScore(id);
                setResult(data);
            } catch (err) {
                console.error('Failed to load match result:', err);
                if (err.response?.status === 404) {
                    setError('Complete your compatibility questionnaire first.');
                } else {
                    setError('The result could not be loaded. Please retry later.');
                }
            } finally {
                setLoading(false);
            }
        };
        loadMatch();
    }, [id]);

    if (loading) {
        return (
            <div className="flex justify-center py-12">
                <LoadingSpinner size="lg" />
            </div>
        );
    }

    if (error) {
        return (
            <div className="max-w-2xl mx-auto text-center py-12">
                <div className="bg-yellow-50 border border-yellow-200 text-yellow-800 px-6 py-4 rounded-lg">
                    <p className="mb-4">{error}</p>
                    <Link
                        to="/profile/questionnaire"
                        className="inline-block px-4 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700"
                    >
                        Complete questionnaire
                    </Link>
                </div>
            </div>
        );
    }

    if (!result) return null;

    const levelStyle = LEVEL_COLORS[result.level] || LEVEL_COLORS.NOT_RECOMMENDED;
    const levelLabel = LEVEL_LABELS[result.level] || result.level;

    return (
        <div className="max-w-4xl mx-auto">
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold text-gray-900">Compatibility result</h1>
                <Link
                    to={`/listings/${id}`}
                    className="px-4 py-1 bg-indigo-600 text-white rounded-md hover:bg-indigo-700"
                >
                    View listing →
                </Link>
            </div>

            <div className="bg-white rounded-lg shadow p-8 mb-6 text-center">
                <div className="inline-block relative">
                    <svg className="w-48 h-48">
                        <circle
                            className="text-gray-200"
                            strokeWidth="12"
                            stroke="currentColor"
                            fill="transparent"
                            r="88"
                            cx="96"
                            cy="96"
                        />
                        <circle
                            className={`${result.score >= 70 ? 'text-green-500' : result.score >= 40 ? 'text-yellow-500' : 'text-red-500'}`}
                            strokeWidth="12"
                            strokeDasharray={2 * Math.PI * 88}
                            strokeDashoffset={2 * Math.PI * 88 * (1 - result.score / 100)}
                            strokeLinecap="round"
                            stroke="currentColor"
                            fill="transparent"
                            r="88"
                            cx="96"
                            cy="96"
                            transform="rotate(-90 96 96)"
                        />
                    </svg>
                    <div className="absolute inset-0 flex flex-col items-center justify-center">
                        <span className="text-5xl font-bold text-gray-900">{result.score}%</span>
                        <span
                            className={`text-sm font-medium px-3 py-1 rounded-full mt-2 ${levelStyle.bg} ${levelStyle.text}`}
                        >
                            {levelLabel}
                        </span>
                    </div>
                </div>
            </div>

            {result.summary && (
                <div className={`border rounded-lg p-6 mb-6 ${levelStyle.border} ${levelStyle.bg}`}>
                    <p className="text-gray-800">{result.summary}</p>
                </div>
            )}

            {result.factors && result.factors.length > 0 && (
                <div className="bg-white rounded-lg shadow p-6 mb-6">
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Detailed analysis</h2>
                    <div className="space-y-4">
                        {result.factors.map((factor) => {
                            const percent = (factor.score / factor.maxScore) * 100;
                            const factorName = FACTOR_LABELS[factor.name] || factor.name;

                            return (
                                <div key={factor.name}>
                                    <div className="flex justify-between text-sm mb-1">
                                        <span className="text-gray-700">{factorName}</span>
                                        <span className="text-gray-500">
                                            {factor.score} / {factor.maxScore}
                                        </span>
                                    </div>
                                    <div className="w-full bg-gray-200 rounded-full h-2">
                                        <div
                                            className={`h-2 rounded-full transition-all ${
                                                percent >= 70
                                                    ? 'bg-green-500'
                                                    : percent >= 40
                                                      ? 'bg-yellow-500'
                                                      : 'bg-red-500'
                                            }`}
                                            style={{ width: `${percent}%` }}
                                        />
                                    </div>
                                    {factor.comment && (
                                        <p className="text-xs text-gray-500 mt-1">
                                            {factor.comment}
                                        </p>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {result.tips && result.tips.length > 0 && (
                <div className="bg-white rounded-lg shadow p-6 mb-6">
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Tips</h2>
                    <ul className="space-y-2">
                        {[...new Set(result.tips)].map((tip) => (
                            <li key={tip} className="flex items-start gap-2">
                                <span className="text-indigo-500 text-lg">•</span>
                                <span className="text-gray-700">{tip}</span>
                            </li>
                        ))}
                    </ul>
                </div>
            )}

            {result.adaptationPlan && result.adaptationPlan.length > 0 && (
                <div className="bg-white rounded-lg shadow p-6">
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Adaptation plan</h2>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        {result.adaptationPlan.map((phase) => (
                            <div key={phase.dayRange} className="border rounded-lg p-4">
                                <h3 className="font-semibold text-indigo-600 mb-2">
                                    {phase.dayRange}
                                </h3>
                                <p className="text-sm text-gray-600 mb-3">{phase.title}</p>
                                <ul className="space-y-1">
                                    {[...new Set(phase.tasks)].map((task) => (
                                        <li
                                            key={task}
                                            className="text-xs text-gray-500 flex items-start gap-1"
                                        >
                                            <span>•</span>
                                            <span>{task}</span>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            <div className="mt-6 text-center">
                <button onClick={() => navigate(-1)} className="text-gray-500 hover:text-gray-700">
                    ← Back to recommendations
                </button>
            </div>
        </div>
    );
}
