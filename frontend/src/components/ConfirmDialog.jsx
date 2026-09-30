// components/ConfirmDialog.jsx
import { useEffect, useRef, useId, useEffectEvent } from 'react';

export default function ConfirmDialog({
    isOpen,
    onClose,
    onConfirm,
    title,
    message,
    confirmLabel = 'Confirm',
    cancelLabel = 'Cancel',
    confirmVariant = 'danger',
    isLoading = false,
}) {
    const dialogRef = useRef(null);
    const titleId = useId();
    const messageId = useId();
    const dismiss = useEffectEvent(() => {
        if (!isLoading) onClose();
    });
    useEffect(() => {
        if (!isOpen) return undefined;
        const previousFocus = document.activeElement;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        dialogRef.current?.querySelector('button')?.focus();
        const onKey = (event) => {
            if (event.key === 'Escape') {
                event.preventDefault();
                dismiss();
            }
            if (event.key !== 'Tab') return;
            const targets = [
                ...dialogRef.current.querySelectorAll(
                    'button:not([disabled]), a[href], input:not([disabled]), [tabindex="0"]'
                ),
            ];
            if (!targets.length) {
                event.preventDefault();
                dialogRef.current.focus();
                return;
            }
            const first = targets[0],
                last = targets[targets.length - 1];
            if (
                event.shiftKey &&
                (document.activeElement === first ||
                    !dialogRef.current.contains(document.activeElement))
            ) {
                event.preventDefault();
                last.focus();
            } else if (
                !event.shiftKey &&
                (document.activeElement === last ||
                    !dialogRef.current.contains(document.activeElement))
            ) {
                event.preventDefault();
                first.focus();
            }
        };
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('keydown', onKey);
            document.body.style.overflow = previousOverflow;
            if (previousFocus instanceof HTMLElement) previousFocus.focus();
        };
    }, [isOpen]);

    if (!isOpen) return null;

    const confirmButtonClass =
        confirmVariant === 'danger'
            ? 'px-4 py-2 text-sm font-semibold text-white bg-red-600 rounded-lg hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2 transition-all duration-200 disabled:opacity-50'
            : 'px-4 py-2 text-sm font-semibold text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 transition-all duration-200 disabled:opacity-50';

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
            <div
                className="absolute inset-0 bg-black/40 backdrop-blur-sm"
                onClick={() => {
                    if (!isLoading) onClose();
                }}
            />

            <div
                ref={dialogRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby={titleId}
                aria-describedby={messageId}
                tabIndex={-1}
                className="relative bg-white rounded-2xl shadow-2xl max-w-md w-full mx-4 transform transition-all duration-300 animate-in zoom-in-95"
            >
                <div className="flex justify-center pt-6">
                    <div
                        className={`
                            w-12 h-12 rounded-full flex items-center justify-center
                            ${confirmVariant === 'danger' ? 'bg-red-100' : 'bg-indigo-100'}
                        `}
                    >
                        {confirmVariant === 'danger' ? (
                            <svg
                                className="w-6 h-6 text-red-600"
                                fill="none"
                                stroke="currentColor"
                                viewBox="0 0 24 24"
                            >
                                <path
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                    strokeWidth={2}
                                    d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
                                />
                            </svg>
                        ) : (
                            <svg
                                className="w-6 h-6 text-indigo-600"
                                fill="none"
                                stroke="currentColor"
                                viewBox="0 0 24 24"
                            >
                                <path
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                    strokeWidth={2}
                                    d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                                />
                            </svg>
                        )}
                    </div>
                </div>

                <div className="text-center mt-4 px-6">
                    <h3 id={titleId} className="text-xl font-semibold text-gray-900">
                        {title}
                    </h3>
                    <p id={messageId} className="mt-2 text-sm text-gray-500">
                        {message}
                    </p>
                </div>

                <div className="flex gap-3 px-6 py-6">
                    <button
                        onClick={onClose}
                        disabled={isLoading}
                        className="flex-1 px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 focus:outline-none focus:ring-2 focus:ring-gray-400 transition-all duration-200 disabled:opacity-50"
                    >
                        {cancelLabel}
                    </button>
                    <button onClick={onConfirm} disabled={isLoading} className={confirmButtonClass}>
                        {isLoading ? (
                            <span className="flex items-center justify-center gap-2">
                                <svg
                                    className="w-4 h-4 animate-spin"
                                    fill="none"
                                    stroke="currentColor"
                                    viewBox="0 0 24 24"
                                >
                                    <circle
                                        className="opacity-25"
                                        cx="12"
                                        cy="12"
                                        r="10"
                                        stroke="currentColor"
                                        strokeWidth="4"
                                        fill="none"
                                    />
                                    <path
                                        className="opacity-75"
                                        fill="currentColor"
                                        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                                    />
                                </svg>
                                Loading...
                            </span>
                        ) : (
                            confirmLabel
                        )}
                    </button>
                </div>
            </div>
        </div>
    );
}
