// pages/MyListingsPage.jsx
import { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { getMyListings, changeListingStatus, deleteListing } from '../api/listings';
import {
    getPassport,
    getAllMyPassports,
    issueDocumentTicket,
    deletePassport,
} from '../api/passports';
import StatusBadge from '../components/StatusBadge';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';
import { mapConcurrent } from '../api/pages';
import { canonicalPassportId } from '../utils/passportId';
import Pagination from '../components/Pagination';
import PassportCard from '../components/PassportCard';

const STATUS_TABS = [
    { value: 'ALL', label: 'All' },
    { value: 'DRAFT', label: 'Drafts' },
    { value: 'REJECTED', label: 'Rejected' },
    { value: 'MODERATION', label: 'Under review' },
    { value: 'PUBLISHED', label: 'Published' },
    { value: 'ARCHIVED', label: 'Archived' },
    { value: 'SOLD', label: 'Sold' },
];

const STATUS_ACTIONS = {
    DRAFT: [
        {
            action: 'MODERATION',
            label: 'Submit for review',
            variant: 'primary',
            confirmTitle: 'Submit for review',
            confirmMessage:
                'A moderator will review this listing. Editing is unavailable until the review is complete.',
        },
        {
            action: 'DELETE',
            label: 'Delete',
            variant: 'danger',
            confirmTitle: 'Delete listing',
            confirmMessage: 'Are you sure? This action cannot be undone.',
        },
    ],
    MODERATION: [],
    PUBLISHED: [
        {
            action: 'ARCHIVED',
            label: 'Archive',
            variant: 'warning',
            confirmTitle: 'Archive listing',
            confirmMessage:
                'This listing will be hidden from the catalog. You can restore it later.',
        },
        {
            action: 'SOLD',
            label: 'Mark as sold',
            variant: 'success',
            confirmTitle: 'Mark as sold',
            confirmMessage: 'This listing will be marked as sold and hidden from the catalog.',
        },
        {
            action: 'DELETE',
            label: 'Delete',
            variant: 'danger',
            confirmTitle: 'Delete listing',
            confirmMessage: 'Are you sure? This action cannot be undone.',
        },
    ],
    REJECTED: [
        {
            action: 'DRAFT',
            label: 'Return to drafts',
            variant: 'secondary',
            confirmTitle: 'Return to drafts',
            confirmMessage: 'This listing will return to drafts for editing.',
        },
        {
            action: 'DELETE',
            label: 'Delete',
            variant: 'danger',
            confirmTitle: 'Delete listing',
            confirmMessage: 'Are you sure? This action cannot be undone.',
        },
    ],
    ARCHIVED: [
        {
            action: 'DRAFT',
            label: 'Restore draft',
            variant: 'secondary',
            confirmTitle: 'Restore listing',
            confirmMessage: 'Return this listing to your drafts?',
        },
        {
            action: 'DELETE',
            label: 'Delete',
            variant: 'danger',
            confirmTitle: 'Delete listing permanently',
            confirmMessage: 'Delete this listing permanently? This action cannot be undone.',
        },
    ],
    SOLD: [
        {
            action: 'DELETE',
            label: 'Delete',
            variant: 'danger',
            confirmTitle: 'Delete listing',
            confirmMessage: 'Are you sure? This action cannot be undone.',
        },
    ],
};

const ACTION_BUTTON_STYLES = {
    primary: 'bg-gray-50 text-gray-700 hover:bg-gray-300 border border-indigo-200',
    secondary: 'bg-gray-50 text-gray-700 hover:bg-gray-300 border border-gray-200',
    warning: 'bg-gray-50 text-gray-700 hover:bg-gray-300 border border-gray-200',
    success: 'bg-indigo-50 text-gray-700 hover:bg-indigo-300 border border-gray-200',
    danger: 'bg-gray-50 text-red-700 hover:bg-red-100 border border-gray-200',
};

const enrichPassportWithFirstPhoto = async (passport, signal) => {
    try {
        const photoId = passport.firstPhotoDocumentId;
        if (!photoId) {
            return { ...passport, photoUrl: null };
        }
        const { url } = await issueDocumentTicket(passport.id, photoId, signal);
        return { ...passport, photoUrl: url };
    } catch (error) {
        if (error.name !== 'CanceledError') {
            console.warn(`Failed to load photo for passport ${passport.id}:`, error);
        }
        return { ...passport, photoUrl: null };
    }
};

export default function MyListingsPage() {
    const location = useLocation();
    const navigate = useNavigate();
    const [listings, setListings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [revision, setRevision] = useState(0);
    const [listingPage, setListingPage] = useState(0);
    const [listingPages, setListingPages] = useState(0);
    const [passportPage, setPassportPage] = useState(0);
    const [passportPages, setPassportPages] = useState(0);
    const [activeStatus, setActiveStatus] = useState('ALL');
    const [actionLoading, setActionLoading] = useState(null);
    const [error, setError] = useState(null);
    const [successMessage, setSuccessMessage] = useState(null);
    const [warning, setWarning] = useState(location.state?.warning ?? null);
    const [confirmLoading, setConfirmLoading] = useState(false);

    // Drop the one-shot warning out of history so a refresh or back-nav
    // does not resurface it.
    useEffect(() => {
        if (location.state?.warning) {
            navigate(location.pathname, { replace: true, state: null });
        }
    }, [location.pathname, location.state, navigate]);

    const [passports, setPassports] = useState([]);
    const [passportsLoading, setPassportsLoading] = useState(true);
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [passportCache, setPassportCache] = useState({});

    const [confirmDialog, setConfirmDialog] = useState({
        isOpen: false,
        listingId: null,
        newStatus: null,
        title: '',
        message: '',
        isDelete: false,
    });

    const [passportDeleteDialog, setPassportDeleteDialog] = useState({
        isOpen: false,
        passportId: null,
        passportName: '',
    });

    // Fetch only the visible page, and ignore responses from older filters.
    useEffect(() => {
        const controller = new AbortController();
        const loadListings = async () => {
            setLoading(true);
            setError(null);
            try {
                const status = activeStatus === 'ALL' ? null : activeStatus;
                const data = await getMyListings(status, listingPage, 20, controller.signal);
                if (controller.signal.aborted) return;
                setListings(data.content || []);
                setListingPages(data.totalPages || 0);
                if (listingPage > 0 && listingPage >= data.totalPages) {
                    setListingPage(Math.max(0, data.totalPages - 1));
                    return;
                }
                const ids = [
                    ...new Set(
                        (data.content || [])
                            .map((item) => canonicalPassportId(item.passportId))
                            .filter(Boolean)
                    ),
                ];
                const passports = await mapConcurrent(ids, async (id) => {
                    try {
                        return [id, await getPassport(id, controller.signal)];
                    } catch {
                        return [id, null];
                    }
                });
                if (!controller.signal.aborted) setPassportCache(Object.fromEntries(passports));
            } catch (error) {
                if (controller.signal.aborted) return;
                console.error('Failed to load listings:', error);
                setError('Listings could not be loaded.');
            } finally {
                if (!controller.signal.aborted) setLoading(false);
            }
        };

        loadListings();
        return () => controller.abort();
    }, [activeStatus, listingPage, revision]);

    useEffect(() => {
        const controller = new AbortController();

        const loadPassports = async () => {
            setPassportsLoading(true);
            try {
                const data = await getAllMyPassports(passportPage, 20, controller.signal);
                if (controller.signal.aborted) return;
                setPassportPages(data.totalPages || 0);
                if (passportPage > 0 && passportPage >= data.totalPages) {
                    setPassportPage(Math.max(0, data.totalPages - 1));
                    return;
                }
                const passportsWithPhotos = await mapConcurrent(data.content || [], (passport) =>
                    enrichPassportWithFirstPhoto(passport, controller.signal)
                );
                if (!controller.signal.aborted) {
                    setPassports(passportsWithPhotos);
                }
            } catch (error) {
                if (error.name !== 'CanceledError') {
                    console.error('Failed to load passports:', error);
                }
                if (!controller.signal.aborted) {
                    setPassports([]);
                }
            } finally {
                if (!controller.signal.aborted) {
                    setPassportsLoading(false);
                }
            }
        };
        loadPassports();

        return () => controller.abort();
    }, [passportPage, revision]);

    const handleStatusChange = async (listingId, newStatus) => {
        setConfirmLoading(true);
        setActionLoading(listingId);
        setError(null);
        try {
            await changeListingStatus(listingId, newStatus);
            setRevision((value) => value + 1);
        } catch (error) {
            console.error('Failed to change status:', error);
            setError('The listing status could not be changed.');
        } finally {
            setConfirmLoading(false);
            setActionLoading(null);
        }
    };

    const handleDeleteListing = async (listingId) => {
        setConfirmLoading(true);
        setError(null);
        try {
            await deleteListing(listingId);
            setRevision((value) => value + 1);
        } catch (error) {
            console.error('Failed to delete listing:', error);
            if (error.response?.status === 409) {
                setError('A listing under review cannot be deleted.');
            } else if (error.response?.status === 403) {
                setError('You cannot delete this listing.');
            } else if (error.response?.status === 404) {
                setError('Listing not found');
            } else {
                setError('The listing could not be deleted.');
            }
        } finally {
            setConfirmLoading(false);
            setActionLoading(null);
        }
    };

    const handleDeletePassport = async (passportId, passportName) => {
        setConfirmLoading(true);
        setError(null);
        try {
            await deletePassport(passportId);

            setRevision((value) => value + 1);
            setSuccessMessage(`Passport "${passportName}" deleted.`);
            setTimeout(() => setSuccessMessage(null), 3000);
        } catch (error) {
            console.error('Failed to delete passport:', error);
            if (error.response?.status === 409) {
                setError(
                    'A passport linked to a published listing or a listing under review cannot be deleted.'
                );
            } else if (error.response?.status === 403) {
                setError('You cannot delete this passport.');
            } else if (error.response?.status === 404) {
                setError('Passport not found');
            } else {
                setError('The passport could not be deleted.');
            }
        } finally {
            setConfirmLoading(false);
        }
    };

    const openConfirmDialog = (listingId, action) => {
        setConfirmDialog({
            isOpen: true,
            listingId,
            newStatus: action.action,
            title: action.confirmTitle,
            message: action.confirmMessage,
            isDelete: action.action === 'DELETE',
        });
    };

    const handleConfirm = async () => {
        const { listingId, newStatus, isDelete } = confirmDialog;

        if (isDelete) {
            await handleDeleteListing(listingId);
        } else {
            await handleStatusChange(listingId, newStatus);
        }

        setConfirmDialog({ ...confirmDialog, isOpen: false });
    };

    const formatDate = (dateString) => {
        if (!dateString) return '—';
        return new Date(dateString).toLocaleDateString('ru-RU');
    };

    const formatPrice = (price) => {
        if (!price && price !== 0) return '—';
        return `${price.toLocaleString('ru-RU')} ₽`;
    };

    const getPetName = (passportId) => {
        if (!passportId) return null;
        const passport = passportCache[canonicalPassportId(passportId)];
        return passport?.name || null;
    };

    return (
        <div className="relative">
            <div className={`transition-all duration-300 ${sidebarOpen ? 'mr-80' : ''}`}>
                <div className="flex justify-between items-center mb-6">
                    <h1 className="text-xl font-bold text-gray-900">My listings</h1>
                    <div className="flex gap-3">
                        <button
                            onClick={() => setSidebarOpen(!sidebarOpen)}
                            className="bg-gray-100 text-gray-700 px-3 py-1.5 rounded-full text-sm hover:bg-gray-200 transition-colors flex items-center gap-2"
                        >
                            <svg
                                className="w-4 h-4"
                                fill="none"
                                stroke="currentColor"
                                viewBox="0 0 24 24"
                            >
                                <path
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                    strokeWidth={2}
                                    d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                                />
                            </svg>
                            My pets
                            {passports.length > 0 && (
                                <span className="bg-indigo-100 text-indigo-700 text-xs px-1.5 py-0.5 rounded-full">
                                    {passports.length}
                                </span>
                            )}
                        </button>
                        <Link
                            to="/my-listings/new"
                            className="bg-indigo-500 text-white px-3 py-1.5 rounded-full text-sm hover:bg-indigo-700 transition-colors"
                        >
                            + Create listing
                        </Link>
                    </div>
                </div>

                {error && (
                    <div className="mb-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md text-sm">
                        {error}
                    </div>
                )}

                {successMessage && (
                    <div className="mb-6 bg-green-50 border border-green-200 text-green-700 px-4 py-3 rounded-md text-sm">
                        {successMessage}
                    </div>
                )}

                {warning && (
                    <div className="mb-6 flex items-start justify-between gap-4 bg-amber-50 border border-amber-200 text-amber-800 px-4 py-3 rounded-md text-sm">
                        <span>{warning}</span>
                        <button
                            onClick={() => setWarning(null)}
                            className="text-amber-700 hover:text-amber-900 font-medium"
                        >
                            ×
                        </button>
                    </div>
                )}

                <div className="border-b border-gray-200 mb-6">
                    <nav className="flex gap-4 overflow-x-auto">
                        {STATUS_TABS.map((tab) => (
                            <button
                                key={tab.value}
                                onClick={() => {
                                    setActiveStatus(tab.value);
                                    setListingPage(0);
                                }}
                                className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors whitespace-nowrap
                                    ${
                                        activeStatus === tab.value
                                            ? 'border-indigo-500 text-indigo-600'
                                            : 'border-transparent text-gray-700 hover:text-gray-900 hover:border-gray-300'
                                    }`}
                            >
                                {tab.label}
                            </button>
                        ))}
                    </nav>
                </div>

                <Pagination
                    page={listingPage}
                    totalPages={listingPages}
                    onChange={setListingPage}
                />
                {loading ? (
                    <div className="flex justify-center py-12">
                        <LoadingSpinner size="lg" />
                    </div>
                ) : listings.length === 0 ? (
                    <div className="text-center py-12 bg-gray-50 rounded-lg">
                        <p className="text-gray-500">You have no listings yet.</p>
                        <Link
                            to="/my-listings/new"
                            className="inline-block mt-4 text-indigo-600 hover:text-indigo-700"
                        >
                            Create your first listing →
                        </Link>
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="min-w-full divide-y divide-gray-200">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Name
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Status
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Price
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Created
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Pet
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Actions
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-gray-200">
                                {listings.map((listing) => {
                                    const petName = getPetName(listing.passportId);
                                    return (
                                        <tr key={listing.id} className="hover:bg-gray-50">
                                            <td className="px-4 py-3">
                                                {listing.status !== 'MODERATION' &&
                                                listing.status !== 'PUBLISHED' &&
                                                listing.status !== 'SOLD' ? (
                                                    <Link
                                                        to={`/my-listings/${listing.id}/edit`}
                                                        className="text-gray-600 hover:text-gray-900 text-sm font-medium"
                                                    >
                                                        {listing.title}
                                                    </Link>
                                                ) : (
                                                    <span className="text-gray-400 text-sm">
                                                        {listing.title}
                                                    </span>
                                                )}
                                                {listing.moderationComment &&
                                                    (listing.status === 'REJECTED' ||
                                                        listing.status === 'DRAFT') && (
                                                        <p
                                                            className="mt-1 text-xs text-amber-700"
                                                            title="Moderator comment"
                                                        >
                                                            Moderator: {listing.moderationComment}
                                                        </p>
                                                    )}
                                            </td>
                                            <td className="px-4 py-3">
                                                <StatusBadge status={listing.status} />
                                            </td>
                                            <td className="px-4 py-3 text-sm text-gray-900">
                                                {formatPrice(listing.price)}
                                            </td>
                                            <td className="px-4 py-3 text-sm text-gray-500">
                                                {formatDate(listing.createdAt)}
                                            </td>
                                            <td className="px-4 py-3">
                                                {listing.passportId ? (
                                                    <Link
                                                        to={`/my-listings/${listing.id}/passport`}
                                                        className="inline-flex items-center px-2.5 py-1 rounded-md text-xs font-medium bg-gray-100 text-teal-700 hover:bg-yellow-100 border border-gray-200 transition-colors gap-1.5"
                                                    >
                                                        <svg
                                                            className="w-3 h-3"
                                                            fill="none"
                                                            stroke="currentColor"
                                                            viewBox="0 0 24 24"
                                                        >
                                                            <path
                                                                strokeLinecap="round"
                                                                strokeLinejoin="round"
                                                                strokeWidth={2}
                                                                d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                                                            />
                                                        </svg>
                                                        {petName || 'Passport'}
                                                    </Link>
                                                ) : (
                                                    <span className="inline-flex items-center px-2.5 py-1 rounded-md text-xs font-medium bg-gray-100 text-gray-400">
                                                        No passport
                                                    </span>
                                                )}
                                            </td>
                                            <td className="px-4 py-3">
                                                <div className="flex flex-wrap gap-1.5">
                                                    {listing.status !== 'MODERATION' &&
                                                        listing.status !== 'PUBLISHED' &&
                                                        listing.status !== 'SOLD' && (
                                                            <Link
                                                                to={`/my-listings/${listing.id}/edit`}
                                                                className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-gray-50 text-gray-700 hover:bg-gray-200 border border-indigo-200 transition-colors"
                                                            >
                                                                Edit
                                                            </Link>
                                                        )}

                                                    {STATUS_ACTIONS[listing.status]?.map(
                                                        (action) => (
                                                            <button
                                                                key={action.action}
                                                                onClick={() =>
                                                                    openConfirmDialog(
                                                                        listing.id,
                                                                        action
                                                                    )
                                                                }
                                                                disabled={
                                                                    actionLoading === listing.id
                                                                }
                                                                className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium transition-all duration-200 ${ACTION_BUTTON_STYLES[action.variant]} disabled:opacity-50 disabled:cursor-not-allowed`}
                                                            >
                                                                {actionLoading === listing.id
                                                                    ? ''
                                                                    : action.label}
                                                            </button>
                                                        )
                                                    )}
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            <div
                className={`fixed top-0 right-0 h-full w-80 bg-white shadow-xl z-40 transform transition-transform duration-300 ease-in-out ${sidebarOpen ? 'translate-x-0' : 'translate-x-full'}`}
            >
                <div className="h-full flex flex-col">
                    <div className="p-4 border-b border-gray-200 flex justify-between items-center bg-white sticky top-0">
                        <h2 className="text-lg font-semibold text-gray-900">
                            My pets
                            <span className="ml-2 text-sm text-gray-500 font-normal">
                                ({passports.length})
                            </span>
                        </h2>
                        <button
                            onClick={() => setSidebarOpen(false)}
                            aria-label="Close pets panel"
                            className="text-gray-400 hover:text-gray-600 transition-colors"
                        >
                            <svg
                                className="w-5 h-5"
                                fill="none"
                                stroke="currentColor"
                                viewBox="0 0 24 24"
                            >
                                <path
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                    strokeWidth={2}
                                    d="M6 18L18 6M6 6l12 12"
                                />
                            </svg>
                        </button>
                    </div>

                    <div className="flex-1 overflow-y-auto p-4 space-y-3">
                        {passportsLoading ? (
                            <div className="flex justify-center py-8">
                                <LoadingSpinner size="md" />
                            </div>
                        ) : passports.length === 0 ? (
                            <div className="text-center py-8">
                                <svg
                                    className="w-12 h-12 text-gray-300 mx-auto mb-3"
                                    fill="none"
                                    stroke="currentColor"
                                    viewBox="0 0 24 24"
                                >
                                    <path
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        strokeWidth={1.5}
                                        d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                                    />
                                </svg>
                                <p className="text-gray-500 text-sm">You have no passports yet.</p>
                                <p className="text-gray-400 text-xs mt-1">
                                    Create a passport for your pet.
                                </p>
                            </div>
                        ) : (
                            passports.map((passport) => {
                                const listing = listings.find(
                                    (l) =>
                                        canonicalPassportId(l.passportId) ===
                                        canonicalPassportId(passport.id)
                                );
                                return (
                                    <PassportCard
                                        key={passport.id}
                                        passport={passport}
                                        listingId={listing?.id}
                                        onDelete={(id, name) => {
                                            setPassportDeleteDialog({
                                                isOpen: true,
                                                passportId: id,
                                                passportName: name,
                                            });
                                        }}
                                    />
                                );
                            })
                        )}
                    </div>

                    <div className="p-4 border-t border-gray-200 bg-white">
                        <Pagination
                            page={passportPage}
                            totalPages={passportPages}
                            onChange={setPassportPage}
                        />
                        <Link
                            to="/passports/new"
                            onClick={() => setSidebarOpen(false)}
                            className="block w-full text-center px-3 py-2 rounded-md bg-indigo-600 text-white text-sm hover:bg-indigo-700 transition-colors"
                        >
                            + Create a passport
                        </Link>
                    </div>
                </div>
            </div>

            {sidebarOpen && (
                <div
                    className="fixed inset-0 bg-black/20 z-30 lg:hidden"
                    onClick={() => setSidebarOpen(false)}
                />
            )}

            <ConfirmDialog
                isOpen={confirmDialog.isOpen}
                onClose={() => setConfirmDialog({ ...confirmDialog, isOpen: false })}
                onConfirm={handleConfirm}
                title={confirmDialog.title}
                message={confirmDialog.message}
                isLoading={confirmLoading}
            />

            <ConfirmDialog
                isOpen={passportDeleteDialog.isOpen}
                onClose={() =>
                    setPassportDeleteDialog({ isOpen: false, passportId: null, passportName: '' })
                }
                onConfirm={async () => {
                    await handleDeletePassport(
                        passportDeleteDialog.passportId,
                        passportDeleteDialog.passportName
                    );
                    setPassportDeleteDialog({ isOpen: false, passportId: null, passportName: '' });
                }}
                title="Delete passport"
                message={`Delete passport "${passportDeleteDialog.passportName}"? This action cannot be undone.`}
                isLoading={confirmLoading}
            />
        </div>
    );
}
