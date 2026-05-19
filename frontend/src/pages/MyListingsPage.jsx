// pages/MyListingsPage.jsx
import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getMyListings, changeListingStatus } from '../api/listings';
import { getPassport, getAllMyPassports } from '../api/passports';
import StatusBadge from '../components/StatusBadge';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';
import PassportCard from '../components/PassportCard';

const STATUS_TABS = [
    { value: 'ALL', label: 'Все' },
    { value: 'DRAFT', label: 'Черновики' },
    { value: 'MODERATION', label: 'На модерации' },
    { value: 'PUBLISHED', label: 'Опубликованные' },
    { value: 'ARCHIVED', label: 'В архиве' },
    { value: 'SOLD', label: 'Проданные' },
];

const STATUS_ACTIONS = {
    DRAFT: [
        {
            action: 'MODERATION',
            label: 'Отправить на модерацию',
            variant: 'primary',
            confirmTitle: 'Отправить на модерацию',
            confirmMessage:
                'После отправки объявление будет проверено модератором. Вы не сможете его редактировать до завершения проверки.',
        },
        {
            action: 'DELETE',
            label: 'Удалить',
            variant: 'danger',
            confirmTitle: 'Удалить объявление',
            confirmMessage: 'Вы уверены? Это действие нельзя отменить.',
        },
    ],
    MODERATION: [],
    PUBLISHED: [
        {
            action: 'ARCHIVED',
            label: 'Архивировать',
            variant: 'warning',
            confirmTitle: 'Архивировать объявление',
            confirmMessage:
                'Объявление будет скрыто из каталога. Вы можете восстановить его позже.',
        },
        {
            action: 'SOLD',
            label: 'Пометить как проданное',
            variant: 'success',
            confirmTitle: 'Пометить как проданное',
            confirmMessage: 'Объявление будет помечено как проданное и скрыто из каталога.',
        },
        {
            action: 'DELETE',
            label: 'Удалить',
            variant: 'danger',
            confirmTitle: 'Удалить объявление',
            confirmMessage: 'Вы уверены? Это действие нельзя отменить.',
        },
    ],
    REJECTED: [
        {
            action: 'MODERATION',
            label: 'Отправить на повторную модерацию',
            variant: 'primary',
            confirmTitle: 'Отправить на модерацию',
            confirmMessage: 'После отправки объявление будет проверено модератором.',
        },
        {
            action: 'DRAFT',
            label: 'Вернуть в черновики',
            variant: 'secondary',
            confirmTitle: 'Вернуть в черновики',
            confirmMessage:
                'Объявление будет перемещено в черновики. Вы сможете отредактировать его позже.',
        },
        {
            action: 'DELETE',
            label: 'Удалить',
            variant: 'danger',
            confirmTitle: 'Удалить объявление',
            confirmMessage: 'Вы уверены? Это действие нельзя отменить.',
        },
    ],
    ARCHIVED: [],
    SOLD: [],
};

// Стили для кнопок в зависимости от варианта
const ACTION_BUTTON_STYLES = {
    primary: 'bg-gray-50 text-gray-700 hover:bg-gray-300 border border-indigo-200',
    secondary: 'bg-gray-50 text-gray-700 hover:bg-gray-300 border border-gray-200',
    warning: 'bg-gray-50 text-gray-700 hover:bg-gray-300 border border-gray-200',
    success: 'bg-indigo-50 text-gray-700 hover:bg-indigo-300 border border-gray-200',
    danger: 'bg-gray-50 text-red-700 hover:bg-red-100 border border-gray-200',
};

export default function MyListingsPage() {
    const [listings, setListings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [activeStatus, setActiveStatus] = useState('ALL');
    const [actionLoading, setActionLoading] = useState(null);
    const [error, setError] = useState(null);

    // Состояния для паспортов
    const [passports, setPassports] = useState([]);
    const [passportsLoading, setPassportsLoading] = useState(true);
    const [sidebarOpen, setSidebarOpen] = useState(false);

    // Кэш паспортов для быстрого доступа по id
    const [passportCache, setPassportCache] = useState({});

    const [confirmDialog, setConfirmDialog] = useState({
        isOpen: false,
        listingId: null,
        newStatus: null,
        title: '',
        message: '',
        isDelete: false,
    });

    // Загрузка объявлений
    useEffect(() => {
        const loadListings = async () => {
            setLoading(true);
            setError(null);
            try {
                const status = activeStatus === 'ALL' ? null : activeStatus;
                const data = await getMyListings(status);
                setListings(data.content || []);

                // Загружаем паспорта для каждого объявления, у которого есть passportId
                const newCache = { ...passportCache };
                for (const listing of data.content || []) {
                    if (listing.passportId && !newCache[listing.passportId]) {
                        try {
                            const passport = await getPassport(listing.passportId);
                            newCache[listing.passportId] = passport;
                        } catch (err) {
                            console.error(`Failed to load passport ${listing.passportId}:`, err);
                        }
                    }
                }
                setPassportCache(newCache);
            } catch (error) {
                console.error('Failed to load listings:', error);
                setError('Не удалось загрузить объявления');
            } finally {
                setLoading(false);
            }
        };

        loadListings();
    }, [activeStatus]);

    // Загрузка всех паспортов пользователя для боковой панели
    useEffect(() => {
        const loadPassports = async () => {
            setPassportsLoading(true);
            try {
                const data = await getAllMyPassports();
                setPassports(data.content || []);
            } catch (error) {
                console.error('Failed to load passports:', error);
            } finally {
                setPassportsLoading(false);
            }
        };
        loadPassports();
    }, []);

    const handleStatusChange = async (listingId, newStatus) => {
        setActionLoading(listingId);
        setError(null);
        try {
            await changeListingStatus(listingId, newStatus);
            const status = activeStatus === 'ALL' ? null : activeStatus;
            const data = await getMyListings(status);
            setListings(data.content || []);
        } catch (error) {
            console.error('Failed to change status:', error);
            setError('Не удалось изменить статус объявления');
        } finally {
            setActionLoading(null);
        }
    };

    const handleDeleteListing = async (listingId) => {
        setActionLoading(listingId);
        setError(null);
        await changeListingStatus(listingId, 'ARCHIVED');

        const status = activeStatus === 'ALL' ? null : activeStatus;
        const data = await getMyListings(status);
        setListings(data.content || []);
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

    // Получить имя питомца по passportId
    const getPetName = (passportId) => {
        if (!passportId) return null;
        const passport = passportCache[passportId];
        return passport?.name || null;
    };

    return (
        <div className="relative">
            {/* Основной контент */}
            <div className={`transition-all duration-300 ${sidebarOpen ? 'mr-80' : ''}`}>
                <div className="flex justify-between items-center mb-6">
                    <h1 className="text-xl font-bold text-gray-900">Мои объявления</h1>
                    <div className="flex gap-3">
                        {/* Кнопка открытия панели паспортов */}
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
                            Мои питомцы
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
                            + Создать объявление
                        </Link>
                    </div>
                </div>

                {error && (
                    <div className="mb-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md text-sm">
                        {error}
                    </div>
                )}

                <div className="border-b border-gray-200 mb-6">
                    <nav className="flex gap-4 overflow-x-auto">
                        {STATUS_TABS.map((tab) => (
                            <button
                                key={tab.value}
                                onClick={() => setActiveStatus(tab.value)}
                                className={`
                                    px-3 py-2 text-sm font-medium border-b-2 transition-colors whitespace-nowrap
                                    ${
                                        activeStatus === tab.value
                                            ? 'border-indigo-500 text-indigo-600'
                                            : 'border-transparent text-gray-700 hover:text-gray-900 hover:border-gray-300'
                                    }
                                `}
                            >
                                {tab.label}
                            </button>
                        ))}
                    </nav>
                </div>

                {loading ? (
                    <div className="flex justify-center py-12">
                        <LoadingSpinner size="lg" />
                    </div>
                ) : listings.length === 0 ? (
                    <div className="text-center py-12 bg-gray-50 rounded-lg">
                        <p className="text-gray-500">У вас пока нет объявлений</p>
                        <Link
                            to="/my-listings/new"
                            className="inline-block mt-4 text-indigo-600 hover:text-indigo-700"
                        >
                            Создать первое объявление →
                        </Link>
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="min-w-full divide-y divide-gray-200">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Название
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Статус
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Цена
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Дата создания
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Питомец
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Действия
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-gray-200">
                                {listings.map((listing) => {
                                    const petName = getPetName(listing.passportId);
                                    return (
                                        <tr key={listing.id} className="hover:bg-gray-50">
                                            <td className="px-4 py-3">
                                                <Link
                                                    to={`/my-listings/${listing.id}/edit`}
                                                    className="text-gray-600 hover:text-gray-900 text-sm font-medium"
                                                >
                                                    {listing.title}
                                                </Link>
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
                                                        {petName || 'Паспорт'}
                                                    </Link>
                                                ) : (
                                                    <span className="inline-flex items-center px-2.5 py-1 rounded-md text-xs font-medium bg-gray-100 text-gray-400">
                                                        Нет паспорта
                                                    </span>
                                                )}
                                            </td>
                                            <td className="px-4 py-3">
                                                <div className="flex flex-wrap gap-1.5">
                                                    <Link
                                                        to={`/my-listings/${listing.id}/edit`}
                                                        className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-gray-50 text-gray-700 hover:bg-gray-200 border border-indigo-200 transition-colors"
                                                    >
                                                        Ред.
                                                    </Link>

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
                                                                className={`
                                                                inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium
                                                                transition-all duration-200
                                                                ${ACTION_BUTTON_STYLES[action.variant]}
                                                                disabled:opacity-50 disabled:cursor-not-allowed
                                                            `}
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

            {/* Правая боковая панель с паспортами */}
            <div
                className={`
                    fixed top-0 right-0 h-full w-80 bg-white shadow-xl z-40
                    transform transition-transform duration-300 ease-in-out
                    ${sidebarOpen ? 'translate-x-0' : 'translate-x-full'}
                `}
            >
                <div className="h-full flex flex-col">
                    {/* Заголовок панели */}
                    <div className="p-4 border-b border-gray-200 flex justify-between items-center bg-white sticky top-0">
                        <h2 className="text-lg font-semibold text-gray-900">
                            Мои питомцы
                            <span className="ml-2 text-sm text-gray-500 font-normal">
                                ({passports.length})
                            </span>
                        </h2>
                        <button
                            onClick={() => setSidebarOpen(false)}
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

                    {/* Список паспортов */}
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
                                <p className="text-gray-500 text-sm">У вас пока нет паспортов</p>
                                <p className="text-gray-400 text-xs mt-1">
                                    Паспорт появится после создания объявления
                                </p>
                            </div>
                        ) : (
                            passports.map((passport) => {
                                const listing = listings.find((l) => l.passportId === passport.id);
                                return (
                                    <PassportCard
                                        key={passport.id}
                                        passport={passport}
                                        listingId={listing?.id}
                                    />
                                );
                            })
                        )}
                    </div>

                    {/* Подвал панели */}
                    <div className="p-4 border-t border-gray-200 bg-white">
                        <Link
                            to="/my-listings/new"
                            onClick={() => setSidebarOpen(false)}
                            className="block w-full text-center px-3 py-2 rounded-md bg-indigo-600 text-white text-sm hover:bg-indigo-700 transition-colors"
                        >
                            + Создать новый паспорт
                        </Link>
                    </div>
                </div>
            </div>

            {/* Оверлей при открытой панели (для мобильных) */}
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
            />
        </div>
    );
}
