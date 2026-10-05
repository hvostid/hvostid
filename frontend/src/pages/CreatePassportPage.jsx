// pages/CreatePassportPage.jsx
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { createPassport, uploadDocument } from '../api/passports';
import Input from '../components/Input';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';
import { PHOTO_ACCEPT, PHOTO_MIME_TYPES } from '../constants/passportDocuments';
import { extractDetail, todayIso } from '../utils/format';
import { PET_SPECIES } from '../constants/petSpecies';

const UNSUPPORTED_PHOTO_MESSAGE = 'Unsupported file format. Use JPEG or PNG, up to 10 MB.';

const GENDER_OPTIONS = [
    { value: 'MALE', label: 'Male' },
    { value: 'FEMALE', label: 'Female' },
];

export default function CreatePassportPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const fromListing = searchParams.get('from') === 'listing';
    const [saving, setSaving] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState(null);
    const [documents, setDocuments] = useState([]);
    const [dragActive, setDragActive] = useState(false);
    const [deleteDialog, setDeleteDialog] = useState({ isOpen: false, docId: null, docName: null });

    const [formData, setFormData] = useState({
        name: '',
        species: '',
        breed: '',
        birthDate: '',
        gender: 'MALE',
        color: '',
        temperament: '',
        specialNeeds: '',
        neutered: false,
        microchipped: false,
    });

    const handleFileUpload = (file) => {
        setUploading(true);
        setError(null);

        const previewUrl = URL.createObjectURL(file);
        const tempDoc = {
            id: `temp_${Date.now()}`,
            file: file,
            originalFilename: file.name,
            previewUrl: previewUrl,
            isNew: true,
        };

        setDocuments((prev) => [...prev, tempDoc]);
        setUploading(false);
    };

    const flashUnsupportedFormat = () => {
        setError(UNSUPPORTED_PHOTO_MESSAGE);
        setTimeout(() => setError(null), 3000);
    };

    // Drag-and-drop handlers
    const handleDrop = (e) => {
        e.preventDefault();
        setDragActive(false);
        const file = e.dataTransfer.files?.[0];
        if (!file) return;
        if (PHOTO_MIME_TYPES.includes(file.type)) {
            handleFileUpload(file);
        } else {
            flashUnsupportedFormat();
        }
    };

    const handleDragOver = (e) => {
        e.preventDefault();
        setDragActive(true);
    };

    const handleDragLeave = (e) => {
        e.preventDefault();
        setDragActive(false);
    };

    const handleFileSelect = (e) => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (PHOTO_MIME_TYPES.includes(file.type)) {
            handleFileUpload(file);
        } else {
            flashUnsupportedFormat();
        }
    };

    const removeTempDocument = (docId) => {
        const docToRemove = documents.find((d) => d.id === docId);
        if (docToRemove?.previewUrl) {
            URL.revokeObjectURL(docToRemove.previewUrl);
        }
        setDocuments(documents.filter((d) => d.id !== docId));
    };

    const handleSave = async () => {
        if (!formData.name.trim()) {
            setError('Pet name is required.');
            setTimeout(() => setError(null), 3000);
            return;
        }
        if (!formData.species.trim()) {
            setError('Species is required.');
            setTimeout(() => setError(null), 3000);
            return;
        }
        if (!formData.birthDate || !formData.birthDate.trim()) {
            setError('Birth date is required.');
            setTimeout(() => setError(null), 3000);
            return;
        }
        if (formData.birthDate > todayIso()) {
            setError('Birth date cannot be in the future.');
            setTimeout(() => setError(null), 3000);
            return;
        }

        setSaving(true);
        setError(null);

        try {
            const passportData = {
                ...formData,
                vaccinations: [],
            };
            const passport = await createPassport(passportData);
            const passportId = passport.id;

            let uploadErrors = [];
            for (const doc of documents) {
                if (doc.isNew && doc.file) {
                    try {
                        await uploadDocument(passportId, doc.file, 'PHOTO');
                    } catch (err) {
                        console.error('Failed to upload photo:', err);
                        const reason = extractDetail(err, 'unknown error');
                        uploadErrors.push(`${doc.originalFilename} (${reason})`);
                    }
                }
            }

            // Return to the saved listing form when passport creation is part of that flow.
            const warning =
                uploadErrors.length > 0
                    ? `Passport created, but these photos could not be uploaded: ${uploadErrors.join('; ')}`
                    : null;
            navigate(
                fromListing
                    ? `/my-listings/new?passportId=${passportId}&formId=${encodeURIComponent(searchParams.get('formId') || '')}`
                    : '/my-listings',
                warning ? { state: { warning } } : undefined
            );
        } catch (err) {
            console.error('Failed to create passport:', err);

            if (err.response?.data?.errors) {
                const messages = err.response.data.errors
                    .map((e) => `${e.field}: ${e.message}`)
                    .join(', ');
                setError(`Validation error: ${messages}`);
            } else if (err.response?.data?.message) {
                setError(err.response.data.message);
            } else {
                setError('The passport could not be created. Please retry later.');
            }
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="max-w-3xl mx-auto">
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold text-gray-900">Create a pet passport</h1>
            </div>

            <p className="text-gray-600 mb-6">
                Enter pet details and add photos. You can create a listing after saving the
                passport. Fields marked <span className="text-red-500">*</span> are required.
            </p>

            {error && (
                <div className="mb-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md">
                    {error}
                </div>
            )}

            <div className="bg-white rounded-lg shadow p-6 space-y-8">
                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Basic information</h2>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <Input
                            id="name"
                            name="name"
                            type="text"
                            label="Pet name"
                            value={formData.name}
                            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                            required
                        />
                        <div>
                            <label
                                htmlFor="species"
                                className="block text-sm font-medium text-gray-700 mb-1"
                            >
                                Species *
                            </label>
                            <select
                                id="species"
                                name="species"
                                value={formData.species}
                                onChange={(e) =>
                                    setFormData({ ...formData, species: e.target.value })
                                }
                                required
                                className="w-full rounded border border-gray-300 px-3 py-2"
                            >
                                <option value="">Choose a species</option>
                                {PET_SPECIES.map((option) => (
                                    <option key={option.value} value={option.value}>
                                        {option.label}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <Input
                            id="breed"
                            name="breed"
                            type="text"
                            label="Breed"
                            value={formData.breed}
                            onChange={(e) => setFormData({ ...formData, breed: e.target.value })}
                        />
                        <Input
                            id="birthDate"
                            name="birthDate"
                            type="date"
                            label="Birth date"
                            value={formData.birthDate}
                            onChange={(e) =>
                                setFormData({ ...formData, birthDate: e.target.value })
                            }
                            max={todayIso()}
                            required
                        />
                        <div>
                            <p className="block text-sm font-medium text-gray-700 mb-1">Sex</p>
                            <div className="flex gap-4">
                                {GENDER_OPTIONS.map((option) => (
                                    <label key={option.value} className="flex items-center">
                                        <input
                                            type="radio"
                                            name="gender"
                                            value={option.value}
                                            checked={formData.gender === option.value}
                                            onChange={(e) =>
                                                setFormData({ ...formData, gender: e.target.value })
                                            }
                                            className="mr-2"
                                        />
                                        {option.label}
                                    </label>
                                ))}
                            </div>
                        </div>
                        <Input
                            id="color"
                            name="color"
                            type="text"
                            label="Color"
                            value={formData.color}
                            onChange={(e) => setFormData({ ...formData, color: e.target.value })}
                        />
                        <Input
                            id="temperament"
                            name="temperament"
                            type="text"
                            label="Temperament"
                            value={formData.temperament}
                            onChange={(e) =>
                                setFormData({ ...formData, temperament: e.target.value })
                            }
                        />
                        <Input
                            id="specialNeeds"
                            name="specialNeeds"
                            type="text"
                            label="Special needs"
                            value={formData.specialNeeds}
                            onChange={(e) =>
                                setFormData({ ...formData, specialNeeds: e.target.value })
                            }
                        />
                        <label className="flex items-center">
                            <input
                                type="checkbox"
                                name="neutered"
                                checked={formData.neutered}
                                onChange={(e) =>
                                    setFormData({ ...formData, neutered: e.target.checked })
                                }
                                className="mr-2"
                            />
                            <span className="text-sm text-gray-700">Spayed or neutered</span>
                        </label>
                        <label className="flex items-center">
                            <input
                                type="checkbox"
                                name="microchipped"
                                checked={formData.microchipped}
                                onChange={(e) =>
                                    setFormData({ ...formData, microchipped: e.target.checked })
                                }
                                className="mr-2"
                            />
                            <span className="text-sm text-gray-700">Microchipped</span>
                        </label>
                    </div>
                </section>

                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Pet photos</h2>

                    <div
                        onDragOver={handleDragOver}
                        onDragLeave={handleDragLeave}
                        onDrop={handleDrop}
                        className={`
                            border-2 border-dashed rounded-lg p-8 text-center transition-colors
                            ${dragActive ? 'border-indigo-500 bg-indigo-50' : 'border-gray-300 bg-gray-50'}
                            ${uploading ? 'opacity-50' : ''}
                        `}
                    >
                        {uploading ? (
                            <LoadingSpinner size="md" />
                        ) : (
                            <>
                                <p className="text-gray-600">
                                    Drop a photo here or{' '}
                                    <label className="text-indigo-600 hover:text-indigo-800 cursor-pointer">
                                        choose a file
                                        <input
                                            type="file"
                                            className="hidden"
                                            onChange={handleFileSelect}
                                            accept={PHOTO_ACCEPT}
                                        />
                                    </label>
                                </p>
                                <p className="text-xs text-gray-400 mt-2">
                                    JPEG and PNG, up to 10 MB
                                </p>
                            </>
                        )}
                    </div>

                    {documents.length > 0 && (
                        <div className="mt-4">
                            <p className="text-sm text-gray-600 mb-2">Selected photos:</p>
                            <div className="grid grid-cols-3 gap-2">
                                {documents.map((doc) => (
                                    <div
                                        key={doc.id}
                                        className="relative group aspect-square bg-gray-100 rounded-lg overflow-hidden"
                                    >
                                        <img
                                            src={doc.previewUrl}
                                            alt={doc.originalFilename}
                                            className="w-full h-full object-cover"
                                        />
                                        <button
                                            onClick={() => removeTempDocument(doc.id)}
                                            className="absolute top-1 right-1 bg-red-600 text-white rounded-full w-6 h-6 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                                        >
                                            ×
                                        </button>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </section>

                <div className="flex justify-end gap-3 pt-4 border-t">
                    <button
                        onClick={() => navigate(fromListing ? '/my-listings/new' : '/my-listings')}
                        className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSave}
                        disabled={saving}
                        className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
                    >
                        {saving ? 'Creating...' : 'Create passport'}
                    </button>
                </div>
            </div>

            <ConfirmDialog
                isOpen={deleteDialog.isOpen}
                onClose={() => setDeleteDialog({ isOpen: false, docId: null, docName: null })}
                onConfirm={() => {
                    removeTempDocument(deleteDialog.docId);
                    setDeleteDialog({ isOpen: false, docId: null, docName: null });
                }}
                title="Delete photo"
                message={`Delete photo "${deleteDialog.docName}"?`}
            />
        </div>
    );
}
