// pages/CreateListingPage.jsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createListing } from '../api/listings';
import ListingForm from '../components/ListingForm';

export default function CreateListingPage() {
    const navigate = useNavigate();
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [error, setError] = useState('');

    const handleSubmit = async (formData) => {
        setIsSubmitting(true);
        setError('');
        try {
            const dataToSend = {
                ...formData,
                age: formData.age ? parseInt(formData.age, 10) : null,
                price: formData.price ? parseInt(formData.price, 10) : null,
            };
            const newListing = await createListing(dataToSend);
            navigate(`/my-listings/${newListing.id}/passport`);
        } catch (err) {
            setError(
                err.response?.status === 403
                    ? 'У вас нет прав продавца'
                    : 'Не удалось создать объявление'
            );
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="max-w-2xl mx-auto">
            <h1 className="text-2xl font-bold text-gray-900 mb-6">Создание объявления</h1>
            {error && (
                <div className="mb-6 bg-red-50 text-red-700 px-4 py-3 rounded-md">{error}</div>
            )}
            <div className="bg-white rounded-lg shadow p-6">
                <ListingForm
                    onSubmit={handleSubmit}
                    isSubmitting={isSubmitting}
                    submitLabel="Создать и продолжить"
                />
            </div>
        </div>
    );
}
