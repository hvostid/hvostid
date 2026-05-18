/**
 * Pull a human-readable message out of an axios error. Falls back to `fallback`
 * when the response shape is unexpected; coerces non-string `detail` payloads
 * (e.g. arrays of field errors) so React never tries to render a raw object.
 */
export function extractDetail(err, fallback) {
    const raw = err?.response?.data?.detail ?? err?.message;
    return typeof raw === 'string' && raw ? raw : fallback;
}

/**
 * Format an ISO-8601 string with the user's locale. Returns "—" for nullish
 * values and falls back to the raw input if `Date` cannot parse it.
 */
export function formatDateTime(iso) {
    if (!iso) return '—';
    try {
        return new Date(iso).toLocaleString();
    } catch {
        return iso;
    }
}
