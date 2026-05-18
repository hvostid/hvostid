import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import api from '../api/client';
import TrustBadge from '../components/TrustBadge';
import { useAuth } from '../context/AuthContext';

const STATUS_BADGE = {
    DRAFT: 'bg-gray-100 text-gray-700 ring-gray-200',
    MODERATION: 'bg-amber-100 text-amber-800 ring-amber-200',
    PUBLISHED: 'bg-green-100 text-green-800 ring-green-200',
    REJECTED: 'bg-red-100 text-red-800 ring-red-200',
    ARCHIVED: 'bg-gray-100 text-gray-600 ring-gray-200',
    SOLD: 'bg-indigo-100 text-indigo-800 ring-indigo-200',
};

const GENDER_LABEL = {
    MALE: 'Male',
    FEMALE: 'Female',
    UNKNOWN: 'Unknown',
};

const DOC_TYPE_LABEL = {
    PHOTO: 'Photo',
    VACCINATION_CERT: 'Vaccination certificate',
    VET_RECORD: 'Vet record',
    OTHER: 'Other',
};

const TRUST_COMPONENTS = [
    { key: 'profileComplete', label: 'Profile complete', max: 20 },
    { key: 'hasPhoto', label: 'Photo uploaded', max: 15 },
    { key: 'hasVaccinationCert', label: 'Vaccination certificate', max: 15 },
    { key: 'hasVetRecord', label: 'Vet record', max: 15 },
    { key: 'vaccinationsDated', label: 'Dated vaccinations', max: 10 },
    { key: 'sellerRating', label: 'Seller rating ≥ 4.0', max: 10 },
    { key: 'sellerSales', label: 'Experienced seller', max: 10 },
    { key: 'moderated', label: 'Passed moderation', max: 5 },
];

const FLAG_REASONS = [
    { value: 'FAKE_INFO', label: 'Fake information' },
    { value: 'ANIMAL_ABUSE', label: 'Animal abuse' },
    { value: 'SCAM', label: 'Scam' },
    { value: 'INAPPROPRIATE', label: 'Inappropriate content' },
    { value: 'OTHER', label: 'Other' },
];

function extractDetail(err, fallback) {
    const raw = err?.response?.data?.detail ?? err?.message;
    return typeof raw === 'string' && raw ? raw : fallback;
}

export default function ListingDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { user, isAuthenticated, hasRole } = useAuth();

    const [listing, setListing] = useState(null);
    const [listingLoading, setListingLoading] = useState(true);
    const [listingError, setListingError] = useState(null);
    const [notFound, setNotFound] = useState(false);

    const [passport, setPassport] = useState(null);
    const [passportRestricted, setPassportRestricted] = useState(false);

    const [trust, setTrust] = useState(null);
    const [trustUnavailable, setTrustUnavailable] = useState(false);

    const [documents, setDocuments] = useState(null);
    const [documentsRestricted, setDocumentsRestricted] = useState(false);

    // Fetch the listing first; other resources fan out from its payload.
    useEffect(() => {
        const controller = new AbortController();
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setListingLoading(true);
        setListingError(null);
        setNotFound(false);
        setListing(null);
        setPassport(null);
        setPassportRestricted(false);
        setTrust(null);
        setTrustUnavailable(false);
        setDocuments(null);
        setDocumentsRestricted(false);

        api.get(`/listings/${id}`, { signal: controller.signal })
            .then((res) => setListing(res.data))
            .catch((err) => {
                if (err.name === 'CanceledError') return;
                if (err.response?.status === 404) {
                    setNotFound(true);
                } else if (err.response?.status === 403) {
                    setListingError('You do not have access to this listing.');
                } else {
                    setListingError(extractDetail(err, 'Failed to load listing'));
                }
            })
            .finally(() => {
                if (!controller.signal.aborted) setListingLoading(false);
            });

        return () => controller.abort();
    }, [id]);

    // Once the listing is loaded, fan out to passport / trust / documents.
    // Each may legitimately 403 (buyer viewing a non-owned passport) — treated as
    // "restricted", not an error.
    useEffect(() => {
        if (!listing?.passportId) return undefined;
        const passportId = listing.passportId;
        const controller = new AbortController();

        api.get(`/passports/${passportId}`, { signal: controller.signal })
            .then((res) => setPassport(res.data))
            .catch((err) => {
                if (err.name === 'CanceledError') return;
                if (err.response?.status === 403 || err.response?.status === 404) {
                    setPassportRestricted(true);
                }
            });

        api.get(`/passports/${passportId}/trust`, { signal: controller.signal })
            .then((res) => setTrust(res.data))
            .catch((err) => {
                if (err.name === 'CanceledError') return;
                setTrustUnavailable(true);
            });

        api.get(`/passports/${passportId}/docs`, { signal: controller.signal })
            .then((res) => setDocuments(res.data))
            .catch((err) => {
                if (err.name === 'CanceledError') return;
                if (err.response?.status === 403 || err.response?.status === 404) {
                    setDocumentsRestricted(true);
                }
            });

        return () => controller.abort();
    }, [listing?.passportId]);

    const isOwner = isAuthenticated && listing && user?.id === listing.sellerId;
    const isModerator = hasRole('MODERATOR') || hasRole('ADMIN');
    // Buyers see photos via the presigned URL embedded in each document
    // payload; only owners and moderators see the private documents section
    // (vaccination certs, vet records, ...).
    const canSeeAllDocs = isOwner || isModerator;

    if (listingLoading) {
        return <ListingDetailSkeleton />;
    }

    if (notFound) {
        return (
            <div className="text-center py-16">
                <h1 className="text-2xl font-bold text-gray-900">Listing not found</h1>
                <p className="text-gray-500 mt-2">
                    The listing you are looking for does not exist or has been removed.
                </p>
                <Link
                    to="/"
                    className="inline-block mt-6 px-4 py-2 rounded-md bg-indigo-600 text-white text-sm hover:bg-indigo-700"
                >
                    Back to catalog
                </Link>
            </div>
        );
    }

    if (listingError) {
        return (
            <div className="bg-red-50 border border-red-200 text-red-700 rounded-md px-4 py-3 text-sm">
                {listingError}
            </div>
        );
    }

    if (!listing) return null;

    return (
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-6">
            <article className="space-y-6">
                <Header listing={listing} isOwner={isOwner} />
                <PhotoGallery
                    documents={documents}
                    documentsRestricted={documentsRestricted}
                    hasPassportId={Boolean(listing.passportId)}
                />
                <MainInfo listing={listing} />
                <PassportBlock
                    passport={passport}
                    restricted={passportRestricted}
                    hasPassportId={Boolean(listing.passportId)}
                />
                {canSeeAllDocs && documents && documents.length > 0 && (
                    <DocumentsBlock documents={documents} />
                )}
            </article>

            <aside className="space-y-4 lg:sticky lg:top-4 lg:self-start">
                <PriceCard listing={listing} />
                <TrustCard
                    trust={trust}
                    unavailable={trustUnavailable}
                    hasPassportId={Boolean(listing.passportId)}
                />
                <SellerCard sellerId={listing.sellerId} />
                <ActionsCard
                    listing={listing}
                    isAuthenticated={isAuthenticated}
                    isOwner={isOwner}
                    navigate={navigate}
                />
            </aside>
        </div>
    );
}

function Header({ listing, isOwner }) {
    const statusClass = STATUS_BADGE[listing.status] ?? 'bg-gray-100 text-gray-700 ring-gray-200';
    return (
        <header className="space-y-2">
            <nav className="text-sm text-gray-500">
                <Link to="/" className="hover:text-indigo-600">
                    Catalog
                </Link>
                <span className="mx-1">/</span>
                <span className="text-gray-700">{listing.title}</span>
            </nav>
            <div className="flex items-start justify-between gap-4 flex-wrap">
                <h1 className="text-2xl md:text-3xl font-bold text-gray-900">{listing.title}</h1>
                {(isOwner || listing.status !== 'PUBLISHED') && (
                    <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ring-1 ring-inset ${statusClass}`}
                    >
                        {listing.status}
                    </span>
                )}
            </div>
            <p className="text-sm text-gray-500">
                {[listing.species, listing.breed].filter(Boolean).join(' / ') ||
                    'Unspecified breed'}
                {listing.city ? ` · ${listing.city}` : ''}
            </p>
        </header>
    );
}

function MainInfo({ listing }) {
    return (
        <section className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
            <h2 className="text-lg font-semibold text-gray-900">About</h2>
            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2 text-sm">
                <InfoRow label="Species" value={listing.species} />
                <InfoRow label="Breed" value={listing.breed} />
                <InfoRow
                    label="Age"
                    value={
                        listing.age !== null && listing.age !== undefined
                            ? `${listing.age} months`
                            : null
                    }
                />
                <InfoRow label="City" value={listing.city} />
            </dl>
            {listing.description && (
                <div>
                    <h3 className="text-sm font-medium text-gray-700 mb-1">Description</h3>
                    <p className="text-sm text-gray-800 whitespace-pre-wrap">
                        {listing.description}
                    </p>
                </div>
            )}
        </section>
    );
}

function PassportBlock({ passport, restricted, hasPassportId }) {
    if (!hasPassportId) {
        return (
            <section className="bg-white rounded-lg border border-gray-200 p-4">
                <h2 className="text-lg font-semibold text-gray-900 mb-2">Pet passport</h2>
                <p className="text-sm text-gray-500">No passport linked to this listing yet.</p>
            </section>
        );
    }

    if (!passport) {
        return (
            <section className="bg-white rounded-lg border border-gray-200 p-4">
                <h2 className="text-lg font-semibold text-gray-900 mb-2">Pet passport</h2>
                <p className="text-sm text-gray-500">
                    {restricted
                        ? 'Detailed passport information is visible to the seller and moderators.'
                        : 'Loading passport…'}
                </p>
            </section>
        );
    }

    return (
        <section className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
            <h2 className="text-lg font-semibold text-gray-900">Pet passport</h2>
            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2 text-sm">
                <InfoRow label="Name" value={passport.name} />
                <InfoRow label="Species" value={passport.species} />
                <InfoRow label="Breed" value={passport.breed} />
                <InfoRow label="Birth date" value={formatDate(passport.birthDate)} />
                <InfoRow label="Gender" value={GENDER_LABEL[passport.gender]} />
                <InfoRow label="Color" value={passport.color} />
                <InfoRow label="Temperament" value={passport.temperament} />
                <InfoRow label="Special needs" value={passport.specialNeeds} />
                <InfoRow label="Neutered" value={passport.neutered ? 'Yes' : 'No'} />
                <InfoRow label="Microchipped" value={passport.microchipped ? 'Yes' : 'No'} />
            </dl>

            {passport.vaccinations && passport.vaccinations.length > 0 && (
                <div>
                    <h3 className="text-sm font-medium text-gray-700 mt-2 mb-2">Vaccinations</h3>
                    <ul className="divide-y divide-gray-100 border border-gray-100 rounded-md">
                        {passport.vaccinations.map((v) => (
                            <li
                                key={v.id}
                                className="px-3 py-2 flex items-center justify-between text-sm"
                            >
                                <div>
                                    <p className="font-medium text-gray-900">{v.name}</p>
                                    <p className="text-xs text-gray-500">
                                        {formatDate(v.date)}
                                        {v.nextDate ? ` · next ${formatDate(v.nextDate)}` : ''}
                                    </p>
                                </div>
                                {v.verified && (
                                    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800 ring-1 ring-inset ring-green-200">
                                        verified
                                    </span>
                                )}
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </section>
    );
}

function PhotoGallery({ documents, documentsRestricted, hasPassportId }) {
    // The backend includes a presigned MinIO downloadUrl on every document
    // the caller is allowed to see (PHOTO documents on PUBLISHED listings
    // are visible to any authenticated viewer; private documents stay
    // restricted to the owner, MODERATOR, and ADMIN). The URL has its
    // credentials in the query string, so we hand it straight to <img src>
    // — no XHR, no blob URLs, browser cache + native lazy-loading apply.
    //
    // Backwards-compat: when the backend has not been updated yet, no
    // document carries a downloadUrl. The filter drops those, so the
    // gallery degrades to the "No photos uploaded" plate.
    const photos = useMemo(
        () => (documents ?? []).filter((d) => d.type === 'PHOTO' && d.downloadUrl),
        [documents]
    );

    if (!hasPassportId) {
        return null;
    }

    if (documentsRestricted || photos.length === 0) {
        return (
            <section className="aspect-[4/3] bg-gray-100 rounded-lg border border-gray-200 flex items-center justify-center text-gray-400 text-sm">
                No photos uploaded
            </section>
        );
    }

    return (
        <section className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            {photos.map((photo) => (
                <PhotoTile key={photo.id} photo={photo} />
            ))}
        </section>
    );
}

function PhotoTile({ photo }) {
    const [failed, setFailed] = useState(false);

    if (failed) {
        return (
            <div className="aspect-square bg-gray-100 rounded-md flex items-center justify-center text-xs text-gray-400">
                Failed to load
            </div>
        );
    }

    return (
        <div className="aspect-square bg-gray-100 rounded-md overflow-hidden">
            <img
                src={photo.downloadUrl}
                alt={photo.originalFilename || 'Pet photo'}
                loading="lazy"
                className="w-full h-full object-cover"
                onError={() => setFailed(true)}
            />
        </div>
    );
}

function DocumentsBlock({ documents }) {
    const otherDocs = documents.filter((d) => d.type !== 'PHOTO');
    if (otherDocs.length === 0) return null;

    return (
        <section className="bg-white rounded-lg border border-gray-200 p-4 space-y-2">
            <h2 className="text-lg font-semibold text-gray-900">Documents</h2>
            <ul className="divide-y divide-gray-100 border border-gray-100 rounded-md">
                {otherDocs.map((doc) => (
                    <li
                        key={doc.id}
                        className="px-3 py-2 flex items-center justify-between text-sm"
                    >
                        <div>
                            <p className="font-medium text-gray-900">
                                {DOC_TYPE_LABEL[doc.type] || doc.type}
                            </p>
                            <p className="text-xs text-gray-500">{doc.originalFilename}</p>
                        </div>
                        <DocumentDownloadLink doc={doc} />
                    </li>
                ))}
            </ul>
        </section>
    );
}

function DocumentDownloadLink({ doc }) {
    // The backend now ships a presigned MinIO URL on the document, so a plain
    // anchor is enough — no blob hop, no race between revokeObjectURL and
    // the synthetic click. The link opens in a new tab so the original page
    // is preserved if the browser chooses to navigate rather than download.
    if (!doc.downloadUrl) {
        return <span className="text-xs text-gray-400">Unavailable</span>;
    }
    return (
        <a
            href={doc.downloadUrl}
            download={doc.originalFilename || `document-${doc.id}`}
            target="_blank"
            rel="noopener noreferrer"
            className="text-xs text-indigo-600 hover:underline"
        >
            Download
        </a>
    );
}

function PriceCard({ listing }) {
    return (
        <section className="bg-white rounded-lg border border-gray-200 p-4">
            <p className="text-xs uppercase text-gray-500 tracking-wide">Price</p>
            <p className="text-2xl font-bold text-indigo-600 mt-1">
                {listing.price !== null && listing.price !== undefined
                    ? `${Number(listing.price).toLocaleString('ru-RU')} ₽`
                    : 'Price not set'}
            </p>
        </section>
    );
}

function TrustCard({ trust, unavailable, hasPassportId }) {
    if (!hasPassportId) return null;

    if (!trust) {
        return (
            <section className="bg-white rounded-lg border border-gray-200 p-4">
                <h2 className="text-sm font-semibold text-gray-900 mb-2">Trust score</h2>
                <p className="text-xs text-gray-500">
                    {unavailable ? 'Trust score is not available.' : 'Loading…'}
                </p>
            </section>
        );
    }

    return (
        <section className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
            <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-gray-900">Trust score</h2>
                <TrustBadge score={trust.score} />
            </div>
            <ul className="space-y-1.5">
                {TRUST_COMPONENTS.map((c) => {
                    const value = trust.breakdown?.[c.key] ?? 0;
                    const pct = Math.min(100, Math.round((value / c.max) * 100));
                    return (
                        <li key={c.key}>
                            <div className="flex items-center justify-between text-xs text-gray-700">
                                <span>{c.label}</span>
                                <span className="text-gray-500">
                                    {value} / {c.max}
                                </span>
                            </div>
                            <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
                                <div
                                    className={`h-full ${
                                        value > 0 ? 'bg-indigo-500' : 'bg-gray-200'
                                    }`}
                                    style={{ width: `${pct}%` }}
                                />
                            </div>
                        </li>
                    );
                })}
            </ul>
        </section>
    );
}

function SellerCard({ sellerId }) {
    return (
        <section className="bg-white rounded-lg border border-gray-200 p-4">
            <h2 className="text-sm font-semibold text-gray-900 mb-1">Seller</h2>
            <p className="text-sm text-gray-700">User #{sellerId}</p>
        </section>
    );
}

function ActionsCard({ listing, isAuthenticated, isOwner, navigate }) {
    const [matchBusy, setMatchBusy] = useState(false);
    const [matchError, setMatchError] = useState(null);
    const [flagOpen, setFlagOpen] = useState(false);

    const handleCheckCompatibility = useCallback(async () => {
        if (matchBusy) return;
        setMatchBusy(true);
        setMatchError(null);
        const returnUrl = `/listings/${listing.id}/match`;
        try {
            await api.get('/match/questionnaire');
            navigate(returnUrl);
        } catch (err) {
            if (err.response?.status === 404) {
                navigate(`/profile/questionnaire?return=${encodeURIComponent(returnUrl)}`);
                return;
            }
            setMatchError(extractDetail(err, 'Failed to check questionnaire'));
        } finally {
            setMatchBusy(false);
        }
    }, [listing.id, matchBusy, navigate]);

    if (isOwner) {
        return (
            <section className="bg-white rounded-lg border border-gray-200 p-4 space-y-2">
                <h2 className="text-sm font-semibold text-gray-900">Manage</h2>
                <Link
                    to="/my-listings"
                    className="block text-center px-3 py-2 rounded-md bg-indigo-600 text-white text-sm hover:bg-indigo-700"
                >
                    Open my listings
                </Link>
            </section>
        );
    }

    return (
        <>
            <section className="bg-white rounded-lg border border-gray-200 p-4 space-y-2">
                {isAuthenticated && listing.status === 'PUBLISHED' && (
                    <button
                        type="button"
                        onClick={handleCheckCompatibility}
                        disabled={matchBusy}
                        className="w-full px-3 py-2 rounded-md bg-indigo-600 text-white text-sm hover:bg-indigo-700 disabled:opacity-60"
                    >
                        {matchBusy ? 'Checking…' : 'Check compatibility'}
                    </button>
                )}
                {matchError && <p className="text-xs text-red-600">{matchError}</p>}
                {isAuthenticated && listing.status === 'PUBLISHED' && (
                    <button
                        type="button"
                        onClick={() => setFlagOpen(true)}
                        className="w-full px-3 py-2 rounded-md border border-gray-200 text-sm text-gray-700 hover:bg-gray-50"
                    >
                        Report listing
                    </button>
                )}
                {!isAuthenticated && (
                    <Link
                        to={`/login?redirect=/listings/${listing.id}`}
                        className="block text-center px-3 py-2 rounded-md bg-indigo-600 text-white text-sm hover:bg-indigo-700"
                    >
                        Sign in to interact
                    </Link>
                )}
            </section>

            {flagOpen && <FlagDialog listingId={listing.id} onClose={() => setFlagOpen(false)} />}
        </>
    );
}

function FlagDialog({ listingId, onClose }) {
    const [reason, setReason] = useState('SCAM');
    const [description, setDescription] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);
    const [done, setDone] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setSubmitting(true);
        setError(null);
        try {
            await api.post(`/listings/${listingId}/flag`, { reason, description });
            setDone(true);
        } catch (err) {
            setError(extractDetail(err, 'Failed to submit report'));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
            role="dialog"
            aria-modal="true"
        >
            <div className="bg-white rounded-lg shadow-lg w-full max-w-md p-5 space-y-4">
                <div className="flex items-center justify-between">
                    <h3 className="text-lg font-semibold text-gray-900">Report listing</h3>
                    <button
                        type="button"
                        onClick={onClose}
                        className="text-gray-400 hover:text-gray-600 text-xl leading-none"
                        aria-label="Close"
                    >
                        ×
                    </button>
                </div>

                {done ? (
                    <div className="space-y-3">
                        <p className="text-sm text-gray-700">
                            Thanks — the report has been submitted to moderation.
                        </p>
                        <button
                            type="button"
                            onClick={onClose}
                            className="w-full px-3 py-2 rounded-md bg-indigo-600 text-white text-sm hover:bg-indigo-700"
                        >
                            Close
                        </button>
                    </div>
                ) : (
                    <form onSubmit={handleSubmit} className="space-y-3">
                        <label className="block">
                            <span className="block text-xs font-medium text-gray-700 mb-1">
                                Reason
                            </span>
                            <select
                                value={reason}
                                onChange={(e) => setReason(e.target.value)}
                                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                            >
                                {FLAG_REASONS.map((r) => (
                                    <option key={r.value} value={r.value}>
                                        {r.label}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="block text-xs font-medium text-gray-700 mb-1">
                                Description (optional)
                            </span>
                            <textarea
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                rows={4}
                                maxLength={1000}
                                placeholder="What looks wrong?"
                                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                            />
                        </label>
                        {error && <p className="text-sm text-red-600">{error}</p>}
                        <div className="flex justify-end gap-2">
                            <button
                                type="button"
                                onClick={onClose}
                                className="px-3 py-2 rounded-md border border-gray-200 text-sm text-gray-700 hover:bg-gray-50"
                            >
                                Cancel
                            </button>
                            <button
                                type="submit"
                                disabled={submitting}
                                className="px-3 py-2 rounded-md bg-red-600 text-white text-sm hover:bg-red-700 disabled:opacity-60"
                            >
                                {submitting ? 'Submitting…' : 'Submit report'}
                            </button>
                        </div>
                    </form>
                )}
            </div>
        </div>
    );
}

function InfoRow({ label, value }) {
    return (
        <div className="flex gap-2">
            <dt className="text-gray-500 w-32 shrink-0">{label}</dt>
            <dd className="text-gray-900 break-words">{value || '—'}</dd>
        </div>
    );
}

function formatDate(iso) {
    if (!iso) return null;
    try {
        return new Date(iso).toLocaleDateString();
    } catch {
        return iso;
    }
}

function ListingDetailSkeleton() {
    return (
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-6 animate-pulse">
            <div className="space-y-4">
                <div className="h-8 bg-gray-200 rounded w-2/3" />
                <div className="aspect-[4/3] bg-gray-200 rounded-lg" />
                <div className="h-32 bg-gray-200 rounded-lg" />
                <div className="h-48 bg-gray-200 rounded-lg" />
            </div>
            <div className="space-y-3">
                <div className="h-24 bg-gray-200 rounded-lg" />
                <div className="h-48 bg-gray-200 rounded-lg" />
                <div className="h-24 bg-gray-200 rounded-lg" />
            </div>
        </div>
    );
}
