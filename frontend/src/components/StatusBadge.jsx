// components/StatusBadge.jsx
import { LISTING_STATUS_LABELS } from '../constants/listingStatus';
export default function StatusBadge({ status }) {
    const styles = {
        DRAFT: 'bg-gray-200 text-gray-800',
        MODERATION: 'bg-gray-200 text-emerald-800',
        PUBLISHED: 'bg-indigo-100 text-indigo-800',
        REJECTED: 'bg-red-100 text-red-800',
        ARCHIVED: 'bg-gray-300 text-gray-600',
        SOLD: 'bg-gray-100 text-gray-800',
    };

    return (
        <span
            className={`px-2 py-1 rounded-full text-xs font-medium ${styles[status] || 'bg-gray-100'}`}
        >
            {LISTING_STATUS_LABELS[status] || status}
        </span>
    );
}
