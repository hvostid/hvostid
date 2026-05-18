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
                title: formData.title.trim(),
                description: formData.description.trim(),
                species: formData.species,
                city: formData.city.trim(),
                passportId: '0', // Временное решение, пока паспорт создаётся отдельно
            };

            if (formData.breed?.trim()) {
                dataToSend.breed = formData.breed.trim();
            }

            if (formData.age && String(formData.age).trim()) {
                const ageNum = parseInt(formData.age, 10);
                if (!isNaN(ageNum) && ageNum > 0) {
                    dataToSend.age = ageNum;
                }
            }

            if (formData.price && String(formData.price).trim()) {
                const priceNum = parseInt(formData.price, 10);
                if (!isNaN(priceNum) && priceNum > 0) {
                    dataToSend.price = priceNum;
                }
            }

            const newListing = await createListing(dataToSend);
            navigate(`/my-listings/${newListing.id}/passport`);
        } catch (err) {
            console.error('Error:', err.response?.data);

            if (err.response?.status === 403) {
                setError('У вас нет прав продавца. Получите роль SELLER в профиле.');
            } else if (err.response?.data?.errors) {
                const messages = err.response.data.errors
                    .map((e) => `${e.field}: ${e.message}`)
                    .join(', ');
                setError(`Ошибка: ${messages}`);
            } else {
                setError(err.response?.data?.message || 'Не удалось создать объявление');
            }
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="max-w-2xl mx-auto">
            <h1 className="text-2xl font-bold text-gray-900 mb-6">Создание объявления</h1>

            {error && (
                <div className="mb-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md">
                    {error}
                </div>
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
