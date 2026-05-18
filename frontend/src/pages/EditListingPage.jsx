// pages/EditListingPage.jsx
import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getListingById, updateListing } from '../api/listings';
import ListingForm from '../components/ListingForm';
import LoadingSpinner from '../components/LoadingSpinner';
import StatusBadge from '../components/StatusBadge';

export default function EditListingPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const [listing, setListing] = useState(null);
    const [loading, setLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        const loadListing = async () => {
            try {
                const data = await getListingById(id);
                setListing(data);
            } catch (error) {
                console.error('Failed to load listing:', error);
                setError('Не удалось загрузить объявление');
            } finally {
                setLoading(false);
            }
        };
        loadListing();
    }, [id]);

    const handleSubmit = async (formData) => {
        setIsSubmitting(true);
        try {
            const dataToSend = {
                ...formData,
                age: formData.age ? parseInt(formData.age, 10) : null,
                price: formData.price ? parseInt(formData.price, 10) : null,
            };
            await updateListing(id, dataToSend);
            navigate('/my-listings');
        } catch (error) {
            console.error('Failed to update listing:', error);
            setError('Не удалось обновить объявление');
        } finally {
            setIsSubmitting(false);
        }
    };

    if (loading) {
        return (
            <div className="flex justify-center py-12">
                <LoadingSpinner size="lg" />
            </div>
        );
    }

    if (error || !listing) {
        return (
            <div className="text-center py-12">
                <p className="text-red-600">{error || 'Объявление не найдено'}</p>
                <button
                    onClick={() => navigate('/my-listings')}
                    className="mt-4 text-indigo-600 hover:text-indigo-700"
                >
                    Вернуться к моим объявлениям
                </button>
            </div>
        );
    }

    const canEdit = listing.status === 'DRAFT' || listing.status === 'PUBLISHED';

    return (
        <div className="max-w-2xl mx-auto">
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold text-gray-900">Редактирование объявления</h1>
                <StatusBadge status={listing.status} />
            </div>

            {!canEdit && (
                <div className="mb-6 bg-yellow-50 border border-yellow-200 text-yellow-800 px-4 py-3 rounded-md">
                    Объявление в статусе «{listing.status}» нельзя редактировать.
                    {listing.status === 'MODERATION' && ' Оно находится на проверке модератором.'}
                </div>
            )}

            {error && (
                <div className="mb-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md">
                    {error}
                </div>
            )}

            <div className="bg-white rounded-lg shadow p-6">
                <ListingForm
                    initialData={listing}
                    onSubmit={handleSubmit}
                    isSubmitting={isSubmitting || !canEdit}
                    submitLabel="Сохранить изменения"
                />
            </div>
        </div>
    );
}
