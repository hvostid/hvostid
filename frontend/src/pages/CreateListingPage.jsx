import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import {
    createListing,
    getOwnedListings,
    getListingDraft,
    saveListingDraft,
    deleteListingDraft,
} from '../api/listings';
import { getOwnedPassports } from '../api/passports';
import { useAuth } from '../context/useAuth';
import ListingForm from '../components/ListingForm';
import { canonicalPassportId } from '../utils/passportId';
import { extractDetail } from '../utils/format';

const empty = {
    title: '',
    description: '',
    species: 'CAT',
    breed: '',
    age: '',
    price: '',
    city: '',
    passportId: '',
};
const toRequest = (data) => ({
    title: data.title,
    description: data.description,
    species: data.species,
    breed: data.breed,
    city: data.city,
    age: data.age === '' || data.age == null ? null : Number(data.age),
    price: data.price === '' || data.price == null ? null : Number(data.price),
    passportId: canonicalPassportId(data.passportId),
});

export default function CreateListingPage() {
    const { user } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [warning, setWarning] = useState(location.state?.warning || '');
    const [params] = useSearchParams();
    const activeKey = `listing-form:${user.id}`;
    const [formId] = useState(() => {
        const supplied = params.get('formId');
        const existing = sessionStorage.getItem(activeKey);
        const id = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
            supplied || existing || ''
        )
            ? supplied || existing
            : crypto.randomUUID();
        sessionStorage.setItem(activeKey, id);
        return id;
    });
    const storageKey = `${activeKey}:${formId}`;
    const [data, setData] = useState(null);
    const [passports, setPassports] = useState([]);
    const [inUse, setInUse] = useState(() => new Set());
    const [step, setStep] = useState('form');
    const [error, setError] = useState('');
    const [saveState, setSaveState] = useState('Loading saved draft...');
    const [busy, setBusy] = useState(false);
    const [conflict, setConflict] = useState(false);
    const [formRevision, setFormRevision] = useState(0);
    const [loadAttempt, setLoadAttempt] = useState(0);
    const versionRef = useRef(0);
    const latestRef = useRef(null);
    const queueRef = useRef(Promise.resolve());
    const loadedRef = useRef(false);
    const publishedRef = useRef(false);
    const conflictRef = useRef(false);

    const remember = useCallback(
        (value) => {
            latestRef.current = value;
            sessionStorage.setItem(
                storageKey,
                JSON.stringify({ data: value, version: versionRef.current })
            );
            setData(value);
            setSaveState('Unsaved changes');
        },
        [storageKey]
    );

    useEffect(() => {
        const controller = new AbortController();
        const load = async () => {
            try {
                const [draft, owned, listings] = await Promise.all([
                    getListingDraft(formId, controller.signal),
                    getOwnedPassports(controller.signal),
                    getOwnedListings(null, controller.signal),
                ]);
                if (controller.signal.aborted) return;
                let local;
                try {
                    local = JSON.parse(sessionStorage.getItem(storageKey));
                } catch {
                    local = null;
                }
                const savedVersion = draft?.version || 0;
                const isConflict = local && local.version !== savedVersion;
                versionRef.current = isConflict ? local.version : savedVersion;
                conflictRef.current = Boolean(isConflict);
                setConflict(Boolean(isConflict));
                setError(
                    isConflict
                        ? 'This form changed in another tab. Your unsaved fields are preserved; reload the saved version before continuing.'
                        : ''
                );
                const restored = {
                    ...empty,
                    ...(local?.data || draft),
                    passportId:
                        params.get('passportId') ||
                        local?.data?.passportId ||
                        draft?.passportId ||
                        '',
                };
                latestRef.current = restored;
                setData(restored);
                setPassports(owned);
                setInUse(
                    new Set(
                        listings
                            .filter((item) => !['ARCHIVED', 'SOLD'].includes(item.status))
                            .map((item) => canonicalPassportId(item.passportId))
                    )
                );
                loadedRef.current = true;
                setSaveState(local ? 'Unsaved changes' : 'Saved');
            } catch (failure) {
                if (!controller.signal.aborted)
                    setError(
                        extractDetail(failure, 'Unable to load the draft. Retry before editing.')
                    );
            }
        };
        void load();
        return () => controller.abort();
    }, [formId, storageKey, params, loadAttempt]);

    const persist = useCallback(
        (value) => {
            queueRef.current = queueRef.current
                .catch(() => {})
                .then(async () => {
                    if (conflictRef.current)
                        throw new Error('Reload the saved version before continuing.');
                    if (publishedRef.current) return;
                    setSaveState('Saving...');
                    try {
                        const saved = await saveListingDraft(formId, {
                            ...toRequest(value),
                            version: versionRef.current,
                        });
                        versionRef.current = saved.version;
                        sessionStorage.setItem(
                            storageKey,
                            JSON.stringify({ data: latestRef.current, version: saved.version })
                        );
                        setSaveState(latestRef.current === value ? 'Saved' : 'Unsaved changes');
                    } catch (failure) {
                        if (failure.response?.status === 409) {
                            conflictRef.current = true;
                            setConflict(true);
                        }
                        setSaveState('Not saved');
                        setError(
                            extractDetail(
                                failure,
                                'Draft could not be saved. Your changes remain in this tab.'
                            )
                        );
                        throw failure;
                    }
                });
            return queueRef.current;
        },
        [formId, storageKey]
    );

    useEffect(() => {
        if (!data || !loadedRef.current || conflict) return undefined;
        const timer = setTimeout(() => {
            void persist(data).catch(() => {});
        }, 500);
        return () => clearTimeout(timer);
    }, [data, persist, conflict]);

    const continueToPassport = async (value) => {
        remember({ ...latestRef.current, ...value });
        setBusy(true);
        try {
            await persist(latestRef.current);
            setStep('passport');
            setError('');
        } catch {
            /* The form remains editable and the save error is shown. */
        } finally {
            setBusy(false);
        }
    };
    const create = async () => {
        if (!data.passportId || conflict) return;
        setBusy(true);
        setError('');
        try {
            await persist(latestRef.current);
            publishedRef.current = true;
            await createListing(toRequest(latestRef.current));
            await queueRef.current.catch(() => {});
            sessionStorage.removeItem(storageKey);
            sessionStorage.removeItem(activeKey);
            // Creating the listing succeeded even if cleanup needs a retry later.
            await deleteListingDraft(formId, versionRef.current).catch(() => {});
            navigate('/my-listings');
        } catch (failure) {
            publishedRef.current = false;
            setError(extractDetail(failure, 'Unable to create the listing.'));
        } finally {
            setBusy(false);
        }
    };
    const newPassport = async () => {
        setBusy(true);
        try {
            await persist(latestRef.current);
            navigate(`/passports/new?from=listing&formId=${formId}`);
        } catch {
            /* Save failure is already visible. */
        } finally {
            setBusy(false);
        }
    };
    const reloadSaved = () => {
        sessionStorage.removeItem(storageKey);
        conflictRef.current = false;
        setConflict(false);
        setData(null);
        setFormRevision((n) => n + 1);
        setLoadAttempt((n) => n + 1);
    };

    return (
        <div className="max-w-2xl mx-auto">
            <h1 className="text-2xl font-bold mb-4">Create listing</h1>
            {warning && (
                <div
                    role="alert"
                    className="mb-4 rounded border border-amber-200 bg-amber-50 p-4 text-amber-900"
                >
                    {warning}
                    <button
                        className="ml-4 underline"
                        onClick={() => {
                            setWarning('');
                            navigate(location.pathname + location.search, {
                                replace: true,
                                state: null,
                            });
                        }}
                    >
                        Dismiss
                    </button>
                </div>
            )}
            <p role="status" className="text-sm text-gray-600 mb-4">
                {saveState}
            </p>
            {error && (
                <div role="alert" className="text-red-700 mb-4">
                    {error}
                    {!data && <button onClick={() => setLoadAttempt((n) => n + 1)}>Retry</button>}
                    {conflict && (
                        <button onClick={reloadSaved}>
                            Use saved version (replace these fields)
                        </button>
                    )}
                </div>
            )}
            {data && step === 'form' && (
                <ListingForm
                    key={formRevision}
                    initialData={data}
                    onChange={(value) => remember({ ...latestRef.current, ...value })}
                    onSubmit={continueToPassport}
                    isSubmitting={busy || conflict}
                    submitLabel="Choose passport"
                />
            )}
            {data && step === 'passport' && (
                <section className="bg-white p-6 rounded-lg space-y-4">
                    <button onClick={() => setStep('form')}>Back to listing</button>
                    <label className="block" htmlFor="selected-passport">
                        Pet passport
                    </label>
                    <select
                        id="selected-passport"
                        className="w-full border p-2 rounded"
                        value={data.passportId}
                        onChange={(event) => remember({ ...data, passportId: event.target.value })}
                    >
                        <option value="">Choose a passport</option>
                        {passports.map((passport) => (
                            <option
                                key={passport.id}
                                value={passport.id}
                                disabled={inUse.has(canonicalPassportId(passport.id))}
                            >
                                {passport.name} ({passport.species})
                                {inUse.has(canonicalPassportId(passport.id))
                                    ? ' - already in use'
                                    : ''}
                            </option>
                        ))}
                    </select>
                    <button
                        className="bg-indigo-600 text-white px-4 py-2 rounded disabled:opacity-50"
                        onClick={create}
                        disabled={
                            busy ||
                            conflict ||
                            !data.passportId ||
                            inUse.has(canonicalPassportId(data.passportId))
                        }
                    >
                        {busy ? 'Saving...' : 'Create listing'}
                    </button>
                    <button
                        className="block text-indigo-600"
                        onClick={newPassport}
                        disabled={busy || conflict}
                    >
                        Create new passport
                    </button>
                </section>
            )}
            <Link to="/my-listings" className="block mt-4 text-indigo-600">
                My listings
            </Link>
        </div>
    );
}
