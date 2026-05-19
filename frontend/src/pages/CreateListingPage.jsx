// pages/CreateListingPage.jsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createListing } from '../api/listings';
import { createPassport } from '../api/passports';
import ListingForm from '../components/ListingForm';

export default function CreateListingPage() {
    const navigate = useNavigate();
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [error, setError] = useState('');

    const handleSubmit = async (formData) => {
        setIsSubmitting(true);
        setError('');

        try {
            const today = new Date().toISOString().split('T')[0];

            // 1. Создаём паспорт
            const passportData = {
                species: formData.species,
                breed: formData.breed || null,
                name: formData.title || 'Временное имя',
                birthDate: today,
                gender: 'MALE',
                color: '',
                temperament: '',
                specialNeeds: '',
                neutered: false,
                microchipped: false,
                vaccinations: [],
            };

            console.log('📤 Creating passport...', passportData);
            const passport = await createPassport(passportData);
            const passportId = passport.id;
            console.log('✅ Passport created with ID:', passportId);

            // 2. Создаём объявление
            const listingData = {
                title: formData.title.trim(),
                description: formData.description.trim(),
                species: formData.species,
                city: formData.city.trim(),
                passportId: String(passportId),
            };

            // Добавляем breed, если есть
            if (formData.breed?.trim()) {
                listingData.breed = formData.breed.trim();
            }

            // Добавляем age, если есть
            if (formData.age && String(formData.age).trim()) {
                const ageNum = parseInt(formData.age, 10);
                if (!isNaN(ageNum) && ageNum >= 0) {
                    listingData.age = ageNum;
                }
            }

            // Добавляем price, если есть
            if (formData.price && String(formData.price).trim()) {
                const priceNum = parseInt(formData.price, 10);
                if (!isNaN(priceNum) && priceNum >= 0) {
                    listingData.price = priceNum;
                }
            }

            console.log('📤 Creating listing with data:', listingData);
            const newListing = await createListing(listingData);
            console.log('✅ Listing created with ID:', newListing.id);

            navigate(`/my-listings/${newListing.id}/passport`);
        } catch (err) {
            console.error('Error:', err);
            console.error('Error response:', err.response?.data);

            if (err.response?.status === 403) {
                setError('У вас нет прав продавца. Получите роль SELLER в профиле.');
            } else if (err.response?.data?.errors) {
                const messages = err.response.data.errors
                    .map((e) => `${e.field}: ${e.message}`)
                    .join(', ');
                setError(`Ошибка: ${messages}`);
            } else {
                setError(
                    err.response?.data?.message || err.message || 'Не удалось создать объявление'
                );
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
                    submitLabel="Создать объявление"
                />
            </div>
        </div>
    );
}
