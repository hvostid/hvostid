// components/StatusBadge.jsx
export default function StatusBadge({ status }) {
    const styles = {
        DRAFT: 'bg-gray-100 text-gray-800',
        MODERATION: 'bg-yellow-100 text-yellow-800',
        PUBLISHED: 'bg-green-100 text-green-800',
        REJECTED: 'bg-red-100 text-red-800',
        ARCHIVED: 'bg-gray-200 text-gray-600',
        SOLD: 'bg-blue-100 text-blue-800',
    };

    const labels = {
        DRAFT: 'Черновик',
        MODERATION: 'На модерации',
        PUBLISHED: 'Опубликовано',
        REJECTED: 'Отклонено',
        ARCHIVED: 'В архиве',
        SOLD: 'Продано',
    };

    return (
        <span
            className={`px-2 py-1 rounded-full text-xs font-medium ${styles[status] || 'bg-gray-100'}`}
        >
            {labels[status] || status}
        </span>
    );
}
