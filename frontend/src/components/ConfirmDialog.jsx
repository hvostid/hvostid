// components/ConfirmDialog.jsx
export default function ConfirmDialog({
    isOpen,
    onClose,
    onConfirm,
    title,
    message,
    confirmLabel = 'Подтвердить',
    cancelLabel = 'Отмена',
    confirmVariant = 'danger', // 'danger' (красный) или 'primary' (синий)
}) {
    if (!isOpen) return null;

    const confirmButtonClass =
        confirmVariant === 'danger'
            ? 'px-4 py-2 text-sm font-medium text-white bg-red-600 hover:bg-red-700 rounded-md'
            : 'px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-md';

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
            <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
                <div className="p-6">
                    <h3 className="text-lg font-medium text-gray-900 mb-2">{title}</h3>
                    <p className="text-gray-500">{message}</p>
                </div>
                <div className="flex justify-end gap-3 px-6 py-4 bg-gray-50 rounded-b-lg">
                    <button
                        onClick={onClose}
                        className="px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 rounded-md"
                    >
                        {cancelLabel}
                    </button>
                    <button onClick={onConfirm} className={confirmButtonClass}>
                        {confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}
