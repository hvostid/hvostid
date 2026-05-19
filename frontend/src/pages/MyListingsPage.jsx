// pages/MyListingsPage.jsx
import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getMyListings, changeListingStatus } from '../api/listings';
import StatusBadge from '../components/StatusBadge';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';

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

export default function MyListingsPage() {
    const [listings, setListings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [activeStatus, setActiveStatus] = useState('ALL');
    const [actionLoading, setActionLoading] = useState(null);
    const [error, setError] = useState(null);

    const [confirmDialog, setConfirmDialog] = useState({
        isOpen: false,
        listingId: null,
        newStatus: null,
        title: '',
        message: '',
        isDelete: false, // Добавляем флаг для удаления
    });

    // Загрузка объявлений при изменении activeStatus
    useEffect(() => {
        const loadListings = async () => {
            setLoading(true);
            setError(null);
            try {
                const status = activeStatus === 'ALL' ? null : activeStatus;
                const data = await getMyListings(status);
                setListings(data.content || []);
            } catch (error) {
                console.error('Failed to load listings:', error);
                setError('Не удалось загрузить объявления');
            } finally {
                setLoading(false);
            }
        };

        loadListings();
    }, [activeStatus]);

    const handleStatusChange = async (listingId, newStatus) => {
        setActionLoading(listingId);
        setError(null);
        try {
            await changeListingStatus(listingId, newStatus);
            // Перезагружаем список после изменения статуса
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
        // await deleteListing(listingId);
        //  нет отдельного API — можно изменить статус на ARCHIVED
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

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-xl font-bold text-gray-900">Мои объявления</h1>
                <Link
                    to="/my-listings/new"
                    className="bg-indigo-500 text-white px-2 py-1 rounded-md text-sm hover:bg-indigo-700 transition-colors"
                >
                    + Создать объявление
                </Link>
            </div>

            {/* Сообщение об ошибке */}
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
                                        : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
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
                                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    Название
                                </th>
                                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    Статус
                                </th>
                                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    Цена
                                </th>
                                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    Дата создания
                                </th>
                                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                    Действия
                                </th>
                            </tr>
                        </thead>
                        <tbody className="bg-white divide-y divide-gray-200">
                            {listings.map((listing) => (
                                <tr key={listing.id} className="hover:bg-gray-50">
                                    <td className="px-4 py-2">
                                        <Link
                                            to={`/my-listings/${listing.id}/edit`}
                                            className="text-gray-600 hover:text-gray-900 text-sm font-medium"
                                        >
                                            {listing.title}
                                        </Link>
                                    </td>
                                    <td className="px-4 py-2">
                                        <StatusBadge status={listing.status} />
                                    </td>
                                    <td className="px-4 py-2 text-xs text-gray-900">
                                        {formatPrice(listing.price)}
                                    </td>
                                    <td className="px-4 py-2 text-xs text-gray-500">
                                        {formatDate(listing.createdAt)}
                                    </td>
                                    <td className="px-4 py-2 text-xs space-x-2">
                                        <Link
                                            to={`/my-listings/${listing.id}/edit`}
                                            className="text-indigo-600 hover:text-indigo-900"
                                        >
                                            Ред.
                                        </Link>

                                        {STATUS_ACTIONS[listing.status]?.map((action) => (
                                            <button
                                                key={action.action}
                                                onClick={() =>
                                                    openConfirmDialog(listing.id, action)
                                                }
                                                disabled={actionLoading === listing.id}
                                                className={`
                                                    ${
                                                        action.variant === 'primary'
                                                            ? 'text-blue-600 hover:text-blue-900'
                                                            : ''
                                                    }
                                                    ${
                                                        action.variant === 'warning'
                                                            ? 'text-gray-600 hover:text-gray-900'
                                                            : ''
                                                    }
                                                    ${
                                                        action.variant === 'success'
                                                            ? 'text-gray-600 hover:text-gray-900'
                                                            : ''
                                                    }
                                                    ${
                                                        action.variant === 'secondary'
                                                            ? 'text-gray-600 hover:text-gray-900'
                                                            : ''
                                                    }
                                                    ${
                                                        action.variant === 'danger'
                                                            ? 'text-red-600 hover:text-red-900'
                                                            : ''
                                                    }
                                                    hover:underline disabled:opacity-50
                                                `}
                                            >
                                                {actionLoading === listing.id
                                                    ? '...'
                                                    : action.label}
                                            </button>
                                        ))}

                                        {listing.passportId && (
                                            <Link
                                                to={`/my-listings/${listing.id}/passport`}
                                                className="text-indigo-600 hover:text-indigo-900 hover:underline"
                                            >
                                                Паспорт
                                            </Link>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
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
