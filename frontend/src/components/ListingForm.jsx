// components/ListingForm.jsx
import { useState } from 'react';
import Input from './Input';

const SPECIES_OPTIONS = [
    { value: 'CAT', label: 'Кошка' },
    { value: 'DOG', label: 'Собака' },
    { value: 'BIRD', label: 'Птица' },
    { value: 'RODENT', label: 'Грызун' },
    { value: 'RABBIT', label: 'Кролик' },
    { value: 'OTHER', label: 'Другое' },
];

export default function ListingForm({
    initialData,
    onSubmit,
    isSubmitting,
    submitLabel,
    fieldErrors = {},
}) {
    const [formData, setFormData] = useState({
        title: initialData?.title || '',
        description: initialData?.description || '',
        species: initialData?.species || 'CAT',
        breed: initialData?.breed || '',
        age: initialData?.age || '',
        price: initialData?.price || '',
        city: initialData?.city || '',
    });
    const [errors, setErrors] = useState({});

    const validate = () => {
        const newErrors = {};

        // Title validation
        if (!formData.title.trim()) {
            newErrors.title = 'Название обязательно';
        } else if (formData.title.trim().length < 5) {
            newErrors.title = 'Название должно быть не менее 5 символов';
        }

        // Description validation
        if (!formData.description.trim()) {
            newErrors.description = 'Описание обязательно';
        } else if (formData.description.trim().length < 10) {
            newErrors.description = 'Описание должно быть не менее 10 символов';
        }

        // City validation
        if (!formData.city.trim()) {
            newErrors.city = 'Город обязателен';
        }

        // Age validation - преобразуем строку в число
        if (formData.age && String(formData.age).trim()) {
            const ageNum = Number(formData.age);
            if (isNaN(ageNum)) {
                newErrors.age = 'Возраст должен быть числом';
            } else if (ageNum < 0 || ageNum > 50) {
                newErrors.age = 'Возраст должен быть от 0 до 50 месяцев';
            }
        }

        // Price validation - преобразуем строку в число
        if (formData.price && String(formData.price).trim()) {
            const priceNum = Number(formData.price);
            if (isNaN(priceNum)) {
                newErrors.price = 'Цена должна быть числом';
            } else if (priceNum < 0) {
                newErrors.price = 'Цена не может быть отрицательной';
            } else if (priceNum > 10000000) {
                newErrors.price = 'Цена не может превышать 10 000 000 ₽';
            }
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (validate()) {
            onSubmit(formData);
        }
    };

    // Объединяем клиентские ошибки и ошибки от сервера
    const getFieldError = (fieldName) => {
        return errors[fieldName] || fieldErrors[fieldName];
    };

    return (
        <form onSubmit={handleSubmit} className="space-y-6">
            <Input
                id="title"
                label="Название объявления"
                value={formData.title}
                onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                error={getFieldError('title')}
                required
                placeholder="Например: Пушистый котёнок ищет дом"
            />

            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Описание *</label>
                <textarea
                    rows="4"
                    value={formData.description}
                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                    className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500
            ${getFieldError('description') ? 'border-red-500' : 'border-gray-300'}`}
                />
                {getFieldError('description') && (
                    <p className="mt-1 text-sm text-red-600">{getFieldError('description')}</p>
                )}
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Вид *</label>
                    <select
                        value={formData.species}
                        onChange={(e) => setFormData({ ...formData, species: e.target.value })}
                        className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500
              ${getFieldError('species') ? 'border-red-500' : 'border-gray-300'}`}
                    >
                        {SPECIES_OPTIONS.map((opt) => (
                            <option key={opt.value} value={opt.value}>
                                {opt.label}
                            </option>
                        ))}
                    </select>
                    {getFieldError('species') && (
                        <p className="mt-1 text-sm text-red-600">{getFieldError('species')}</p>
                    )}
                </div>

                <Input
                    id="breed"
                    label="Порода"
                    value={formData.breed}
                    onChange={(e) => setFormData({ ...formData, breed: e.target.value })}
                    error={getFieldError('breed')}
                    placeholder="Например: Мейн-кун"
                />
            </div>

            <div className="grid grid-cols-2 gap-4">
                <Input
                    id="age"
                    type="number"
                    label="Возраст (месяцев)"
                    value={formData.age}
                    onChange={(e) => setFormData({ ...formData, age: e.target.value })}
                    error={getFieldError('age')}
                    placeholder="Например: 4"
                />

                <Input
                    id="price"
                    type="number"
                    label="Цена (₽)"
                    value={formData.price}
                    onChange={(e) => setFormData({ ...formData, price: e.target.value })}
                    error={getFieldError('price')}
                    placeholder="Например: 5000"
                />
            </div>

            <Input
                id="city"
                label="Город"
                value={formData.city}
                onChange={(e) => setFormData({ ...formData, city: e.target.value })}
                error={getFieldError('city')}
                required
                placeholder="Например: Москва"
            />

            <div className="flex justify-end gap-3 pt-4">
                <button
                    type="button"
                    onClick={() => window.history.back()}
                    className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200"
                >
                    Отмена
                </button>
                <button
                    type="submit"
                    disabled={isSubmitting}
                    className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
                >
                    {isSubmitting ? 'Сохранение...' : submitLabel || 'Сохранить'}
                </button>
            </div>
        </form>
    );
}
