import { useCallback, useEffect, useRef, useState } from 'react';
import { issueDocumentTicket } from '../api/passports';

/**
 * Renders a passport document photo via the single-use-ticket flow.
 *
 * The component fetches a ticketed URL from the backend
 * (`GET /passports/{passportId}/docs/{docId}` -> `{ url, expiresAt }`), then
 * uses that URL as a plain `<img src>`. The frontend nginx swaps the URL out
 * via X-Accel-Redirect so the bytes stream straight from MinIO without ever
 * passing through application threads.
 *
 * Tickets are one-shot and short lived. If the browser drops cached bytes
 * and tries to refetch (e.g. the user reopens the tab after the ticket
 * expired), the `<img onError>` triggers a single retry that issues a fresh
 * ticket. After that we fall back to the `Failed to load` UI.
 */
export default function PassportImage({
    passportId,
    document,
    className,
    placeholderClassName,
    alt,
}) {
    const [ticketedUrl, setTicketedUrl] = useState(null);
    const [failed, setFailed] = useState(false);
    const retryRef = useRef(false);
    const controllerRef = useRef(null);

    const fetchTicket = useCallback(
        async (signal) => {
            try {
                const { url } = await issueDocumentTicket(passportId, document.id, signal);
                if (!signal?.aborted) setTicketedUrl(url);
            } catch (err) {
                if (err.name === 'CanceledError') return;
                setFailed(true);
            }
        },
        [passportId, document.id]
    );

    useEffect(() => {
        const controller = new AbortController();
        controllerRef.current = controller;
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setFailed(false);
        setTicketedUrl(null);
        retryRef.current = false;
        fetchTicket(controller.signal);
        return () => controllerRef.current?.abort();
    }, [fetchTicket]);

    const handleError = useCallback(() => {
        if (retryRef.current) {
            setFailed(true);
            return;
        }
        retryRef.current = true;
        setTicketedUrl(null);
        controllerRef.current?.abort();
        const controller = new AbortController();
        controllerRef.current = controller;
        fetchTicket(controller.signal);
    }, [fetchTicket]);

    if (failed) {
        return (
            <div
                className={
                    placeholderClassName ??
                    'w-full h-full flex items-center justify-center text-xs text-gray-400 bg-gray-100'
                }
            >
                Failed to load
            </div>
        );
    }

    if (!ticketedUrl) {
        return (
            <div className={placeholderClassName ?? 'w-full h-full bg-gray-100 animate-pulse'} />
        );
    }

    return (
        <img
            src={ticketedUrl}
            alt={alt ?? document.originalFilename ?? 'Pet photo'}
            loading="lazy"
            decoding="async"
            className={className}
            onError={handleError}
        />
    );
}
