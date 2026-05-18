/**
 * Page-number pagination with prev/next. `page` is zero-based to match
 * Spring's Pageable. Renders nothing when the result fits on one page.
 *
 * Number sequence collapses around the current page so wide result sets do
 * not produce a horizontally-scrolling row of buttons:
 *
 *   1 ... 4 5 [6] 7 8 ... 42
 */
export default function Pagination({ page, totalPages, onChange }) {
    if (totalPages <= 1) return null;

    const visible = pageNumbers(page, totalPages);

    return (
        <nav
            className="flex items-center justify-center gap-1 flex-wrap mt-6"
            aria-label="Pagination"
        >
            <button
                type="button"
                onClick={() => onChange(page - 1)}
                disabled={page <= 0}
                className="px-3 py-1.5 text-sm rounded-md border border-gray-200 bg-white hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
            >
                Prev
            </button>

            {visible.map((p, i) =>
                p === '...' ? (
                    <span
                        key={`gap-after-${visible[i - 1] ?? 'start'}`}
                        className="px-2 text-gray-400 select-none"
                        aria-hidden="true"
                    >
                        …
                    </span>
                ) : (
                    <button
                        key={p}
                        type="button"
                        onClick={() => onChange(p)}
                        aria-current={p === page ? 'page' : undefined}
                        className={`px-3 py-1.5 text-sm rounded-md border ${
                            p === page
                                ? 'bg-indigo-600 text-white border-indigo-600'
                                : 'bg-white border-gray-200 hover:bg-gray-50'
                        }`}
                    >
                        {p + 1}
                    </button>
                )
            )}

            <button
                type="button"
                onClick={() => onChange(page + 1)}
                disabled={page >= totalPages - 1}
                className="px-3 py-1.5 text-sm rounded-md border border-gray-200 bg-white hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
            >
                Next
            </button>
        </nav>
    );
}

function pageNumbers(current, total) {
    const window = 1; // pages on each side of current
    const pages = new Set([0, total - 1, current]);
    for (let i = 1; i <= window; i++) {
        if (current - i >= 0) pages.add(current - i);
        if (current + i <= total - 1) pages.add(current + i);
    }
    const sorted = [...pages].sort((a, b) => a - b);
    const out = [];
    for (let i = 0; i < sorted.length; i++) {
        if (i > 0 && sorted[i] - sorted[i - 1] > 1) {
            out.push('...');
        }
        out.push(sorted[i]);
    }
    return out;
}
