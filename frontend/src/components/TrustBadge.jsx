/**
 * Color-coded trust badge for a 0..100 score.
 *
 *   0..40   red    "Limited evidence"
 *   41..70  yellow "Some evidence"
 *   71..100 green  "Detailed evidence"
 *
 * Renders nothing when `score` is null/undefined so a listing without
 * a passport-derived score does not show a misleading zero.
 */
export default function TrustBadge({ score, className = '' }) {
    if (score === null || score === undefined || Number.isNaN(Number(score))) {
        return null;
    }

    const value = Math.round(Number(score));
    let palette;
    let label;
    if (value <= 40) {
        palette = 'bg-red-100 text-red-800 ring-red-200';
        label = 'Limited evidence';
    } else if (value <= 70) {
        palette = 'bg-yellow-100 text-yellow-800 ring-yellow-200';
        label = 'Some evidence';
    } else {
        palette = 'bg-green-100 text-green-800 ring-green-200';
        label = 'Detailed evidence';
    }

    return (
        <span
            title={`${label}: passport completeness and uploaded evidence; not a guarantee of accuracy`}
            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ring-1 ring-inset ${palette} ${className}`}
        >
            <span className="font-semibold">{value}</span>
            <span aria-hidden="true">/ 100</span>
            <span className="sr-only">{label}</span>
        </span>
    );
}
