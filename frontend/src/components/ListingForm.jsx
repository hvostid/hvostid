import { PET_SPECIES } from '../constants/petSpecies';
// components/ListingForm.jsx
import { useState } from 'react';
import Input from './Input';

const SPECIES_OPTIONS = PET_SPECIES;

export default function ListingForm({
    initialData,
    onSubmit,
    isSubmitting,
    submitLabel,
    fieldErrors = {},
    onChange,
}) {
    const [formData, setFormData] = useState({
        title: initialData?.title || '',
        description: initialData?.description || '',
        species: initialData?.species || 'CAT',
        breed: initialData?.breed || '',
        age: initialData?.age ?? '',
        price: initialData?.price ?? '',
        city: initialData?.city || '',
    });
    const updateField = (field, value) => {
        const next = { ...formData, [field]: value };
        setFormData(next);
        onChange?.(next);
    };
    const [errors, setErrors] = useState({});

    const validate = () => {
        const newErrors = {};

        // Title validation
        if (!formData.title.trim()) {
            newErrors.title = 'Title is required.';
        } else if (formData.title.trim().length < 5) {
            newErrors.title = 'Title must contain at least 5 characters.';
        }

        // Description validation
        if (!formData.description.trim()) {
            newErrors.description = 'Description is required.';
        } else if (formData.description.trim().length < 10) {
            newErrors.description = 'Description must contain at least 10 characters.';
        }

        // City validation
        if (!formData.city.trim()) {
            newErrors.city = 'City is required.';
        }

        if (formData.age && String(formData.age).trim()) {
            const ageNum = Number(formData.age);
            if (isNaN(ageNum)) {
                newErrors.age = 'Age must be a number.';
            } else if (ageNum < 0 || ageNum > 5000) {
                newErrors.age = 'Age must be between 0 and 5000 months.';
            }
        }

        if (formData.price && String(formData.price).trim()) {
            const priceNum = Number(formData.price);
            if (isNaN(priceNum)) {
                newErrors.price = 'Price must be a number.';
            } else if (priceNum < 0) {
                newErrors.price = 'Price cannot be negative.';
            } else if (priceNum > 10000000) {
                newErrors.price = 'Price cannot exceed RUB 10,000,000.';
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

    const getFieldError = (fieldName) => {
        return errors[fieldName] || fieldErrors[fieldName];
    };

    return (
        <form onSubmit={handleSubmit} className="space-y-6">
            <Input
                id="title"
                maxLength={255}
                label="Listing title"
                value={formData.title}
                onChange={(e) => updateField('title', e.target.value)}
                error={getFieldError('title')}
                required
                placeholder="For example: Friendly kitten looking for a home"
            />

            <div>
                <label
                    htmlFor="description"
                    className="block text-sm font-medium text-gray-700 mb-1"
                >
                    Description *
                </label>
                <textarea
                    id="description"
                    aria-invalid={Boolean(getFieldError('description'))}
                    aria-describedby={
                        getFieldError('description') ? 'description-error' : undefined
                    }
                    rows="4"
                    maxLength={2000}
                    value={formData.description}
                    onChange={(e) => updateField('description', e.target.value)}
                    className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500
            ${getFieldError('description') ? 'border-red-500' : 'border-gray-300'}`}
                />
                {getFieldError('description') && (
                    <p id="description-error" role="alert" className="mt-1 text-sm text-red-600">
                        {getFieldError('description')}
                    </p>
                )}
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label
                        htmlFor="species"
                        className="block text-sm font-medium text-gray-700 mb-1"
                    >
                        Species *
                    </label>
                    <select
                        id="species"
                        value={formData.species}
                        onChange={(e) => updateField('species', e.target.value)}
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
                    maxLength={255}
                    label="Breed"
                    value={formData.breed}
                    onChange={(e) => updateField('breed', e.target.value)}
                    error={getFieldError('breed')}
                    placeholder="For example: Maine Coon"
                />
            </div>

            <div className="grid grid-cols-2 gap-4">
                <Input
                    id="age"
                    type="number"
                    label="Age (months)"
                    value={formData.age}
                    onChange={(e) => updateField('age', e.target.value)}
                    error={getFieldError('age')}
                    placeholder="For example: 4"
                    min={0}
                    max={5000}
                />

                <Input
                    id="price"
                    type="number"
                    label="Price (RUB)"
                    value={formData.price}
                    onChange={(e) => updateField('price', e.target.value)}
                    error={getFieldError('price')}
                    placeholder="For example: 5000"
                />
            </div>

            <Input
                id="city"
                maxLength={255}
                label="City"
                value={formData.city}
                onChange={(e) => updateField('city', e.target.value)}
                error={getFieldError('city')}
                required
                placeholder="For example: Moscow"
            />

            <div className="flex justify-end gap-3 pt-4">
                <button
                    type="button"
                    onClick={() => window.history.back()}
                    className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200"
                >
                    Cancel
                </button>
                <button
                    type="submit"
                    disabled={isSubmitting}
                    className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
                >
                    {isSubmitting ? 'Saving...' : submitLabel || 'Save'}
                </button>
            </div>
        </form>
    );
}
