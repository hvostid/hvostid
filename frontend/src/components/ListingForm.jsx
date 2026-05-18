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

export default function ListingForm({ initialData, onSubmit, isSubmitting, submitLabel }) {
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
        if (!formData.title.trim()) newErrors.title = 'Название обязательно';
        if (!formData.description.trim()) newErrors.description = 'Описание обязательно';
        if (!formData.city.trim()) newErrors.city = 'Город обязателен';
        if (formData.age && (formData.age < 0 || formData.age > 50))
            newErrors.age = 'Возраст от 0 до 50';
        if (formData.price && (formData.price < 0 || formData.price > 10000000))
            newErrors.price = 'Цена от 0 до 10 млн';
        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (validate()) onSubmit(formData);
    };

    return (
        <form onSubmit={handleSubmit} className="space-y-6">
            <Input
                id="title"
                label="Название"
                value={formData.title}
                onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                error={errors.title}
                required
            />

            <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Описание *</label>
                <textarea
                    rows="4"
                    value={formData.description}
                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                    className="w-full px-3 py-2 border rounded-md"
                />
                {errors.description && (
                    <p className="text-sm text-red-600 mt-1">{errors.description}</p>
                )}
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Вид *</label>
                    <select
                        value={formData.species}
                        onChange={(e) => setFormData({ ...formData, species: e.target.value })}
                        className="w-full px-3 py-2 border rounded-md"
                    >
                        {SPECIES_OPTIONS.map((opt) => (
                            <option key={opt.value} value={opt.value}>
                                {opt.label}
                            </option>
                        ))}
                    </select>
                </div>
                <Input
                    id="breed"
                    label="Порода"
                    value={formData.breed}
                    onChange={(e) => setFormData({ ...formData, breed: e.target.value })}
                />
            </div>

            <div className="grid grid-cols-2 gap-4">
                <Input
                    id="age"
                    type="number"
                    label="Возраст (мес)"
                    value={formData.age}
                    onChange={(e) => setFormData({ ...formData, age: e.target.value })}
                    error={errors.age}
                />
                <Input
                    id="price"
                    type="number"
                    label="Цена (₽)"
                    value={formData.price}
                    onChange={(e) => setFormData({ ...formData, price: e.target.value })}
                    error={errors.price}
                />
            </div>

            <Input
                id="city"
                label="Город"
                value={formData.city}
                onChange={(e) => setFormData({ ...formData, city: e.target.value })}
                error={errors.city}
                required
            />

            <div className="flex justify-end gap-3 pt-4">
                <button
                    type="button"
                    onClick={() => window.history.back()}
                    className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md"
                >
                    Отмена
                </button>
                <button
                    type="submit"
                    disabled={isSubmitting}
                    className="px-4 py-2 text-white bg-indigo-600 rounded-md disabled:opacity-50"
                >
                    {isSubmitting ? 'Сохранение...' : submitLabel || 'Сохранить'}
                </button>
            </div>
        </form>
    );
}
