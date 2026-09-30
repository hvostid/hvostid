// pages/PassportFormPage.jsx
import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getListingById, getOwnedListings } from '../api/listings';
import {
    getPassport,
    createPassport,
    updatePassport,
    getTrustScore,
    getPassportDocuments,
    uploadDocument,
    deleteDocument,
    issueDocumentTicket,
} from '../api/passports';
import { canonicalPassportId } from '../utils/passportId';
import { mapConcurrent } from '../api/pages';
import Input from '../components/Input';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';
import { DOCUMENT_ACCEPT, DOCUMENT_MIME_TYPES } from '../constants/passportDocuments';
import { extractDetail, todayIso } from '../utils/format';
import { LISTING_STATUS_LABELS as STATUS_LABELS } from '../constants/listingStatus';

const UNSUPPORTED_DOCUMENT_MESSAGE = 'Unsupported file format. Use JPEG, PNG or PDF, up to 10 MB.';

const vaccinationError = (value, birthDate) => {
    if (!value.name.trim() || !value.date) return 'Enter the vaccination name and date.';
    if (value.date > todayIso()) return 'Vaccination date cannot be in the future.';
    if (birthDate && value.date < birthDate) return 'Vaccination date cannot be before birth.';
    if (value.nextDate && value.nextDate < value.date)
        return 'Next due date cannot be before the vaccination date.';
    return null;
};

// Fetch a passport's documents and resolve a short-TTL <img src> ticket for
// each PHOTO so the grid can render them. Non-photo docs are returned as-is.
const loadDocumentsWithPhotos = async (passportId) => {
    const docs = await getPassportDocuments(passportId);
    return mapConcurrent(docs, async (doc) => {
        if (doc.type !== 'PHOTO') {
            return doc;
        }
        try {
            const { url } = await issueDocumentTicket(passportId, doc.id);
            return { ...doc, downloadUrl: url };
        } catch (err) {
            console.warn(`Failed to load URL for document ${doc.id}:`, err);
            return { ...doc, downloadUrl: null };
        }
    });
};

const GENDER_OPTIONS = [
    { value: 'MALE', label: 'Male' },
    { value: 'FEMALE', label: 'Female' },
];

export default function PassportFormPage() {
    const { id: listingId, passportId: directPassportId } = useParams();
    const navigate = useNavigate();

    const isDirectPassportAccess = !!directPassportId;

    const tempIdCounterRef = useRef(0);
    const generateTempId = () => {
        tempIdCounterRef.current += 1;
        return `temp_${Date.now()}_${tempIdCounterRef.current}`;
    };

    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);
    const [successMessage, setSuccessMessage] = useState(null);
    const [passportId, setPassportId] = useState(null);
    const [trustScore, setTrustScore] = useState(null);
    const [canEdit, setCanEdit] = useState(false);
    const [linkedListingStatus, setLinkedListingStatus] = useState(null);
    const [validationErrors, setValidationErrors] = useState({});
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
    const [vaccinations, setVaccinations] = useState([]);
    const [newVaccination, setNewVaccination] = useState({ name: '', date: '', nextDate: '' });
    const [documentType, setDocumentType] = useState('PHOTO');
    const [documents, setDocuments] = useState([]);
    const [uploading, setUploading] = useState(false);
    const [dragActive, setDragActive] = useState(false);
    const [deleteDialog, setDeleteDialog] = useState({ isOpen: false, docId: null, docName: null });
    const [imageErrors, setImageErrors] = useState({});

    const validateForm = () => {
        const errors = {};

        if (!formData.name || formData.name.trim() === '') {
            errors.name = 'Pet name is required.';
        }
        if (!formData.species || formData.species.trim() === '') {
            errors.species = 'Species is required.';
        }
        if (!formData.birthDate || formData.birthDate.trim() === '') {
            errors.birthDate = 'Birth date is required.';
        } else if (formData.birthDate > todayIso()) {
            errors.birthDate = 'Birth date cannot be in the future.';
        }

        setValidationErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const checkIfCanEdit = useCallback(async (passportId) => {
        try {
            const listings = await getOwnedListings();
            const linked = listings.find(
                (item) =>
                    canonicalPassportId(item.passportId) === canonicalPassportId(passportId) &&
                    ['MODERATION', 'PUBLISHED'].includes(item.status)
            );
            setLinkedListingStatus(linked?.status || null);
            setCanEdit(!linked);
            return !linked;
        } catch {
            setCanEdit(false);
            setError('Unable to check whether this passport is in use. Reload before editing.');
            return false;
        }
    }, []);

    useEffect(() => {
        const load = async () => {
            try {
                if (isDirectPassportAccess && directPassportId) {
                    const passport = await getPassport(directPassportId);
                    setPassportId(passport.id);

                    await checkIfCanEdit(directPassportId);

                    setFormData({
                        name: passport.name ?? '',
                        species: passport.species ?? '',
                        breed: passport.breed ?? '',
                        birthDate: passport.birthDate ?? '',
                        gender: passport.gender ?? 'MALE',
                        color: passport.color ?? '',
                        temperament: passport.temperament ?? '',
                        specialNeeds: passport.specialNeeds ?? '',
                        neutered: passport.neutered ?? false,
                        microchipped: passport.microchipped ?? false,
                    });
                    setVaccinations(passport.vaccinations ?? []);

                    try {
                        const trust = await getTrustScore(directPassportId);
                        setTrustScore(trust);
                    } catch {
                        console.log('Evidence score not available for unlinked passport');
                    }

                    try {
                        setDocuments(await loadDocumentsWithPhotos(passport.id));
                    } catch (err) {
                        console.warn('Failed to load passport documents:', err);
                    }
                    setLoading(false);
                    return;
                }

                const listing = await getListingById(listingId);
                if (listing.passportId) {
                    const passport = await getPassport(listing.passportId);
                    setPassportId(passport.id);

                    await checkIfCanEdit(passport.id);

                    setFormData({
                        name: passport.name ?? '',
                        species: passport.species ?? listing.species ?? '',
                        breed: passport.breed ?? listing.breed ?? '',
                        birthDate: passport.birthDate ?? '',
                        gender: passport.gender ?? 'MALE',
                        color: passport.color ?? '',
                        temperament: passport.temperament ?? '',
                        specialNeeds: passport.specialNeeds ?? '',
                        neutered: passport.neutered ?? false,
                        microchipped: passport.microchipped ?? false,
                    });
                    setVaccinations(passport.vaccinations ?? []);
                    const trust = await getTrustScore(listing.passportId);
                    setTrustScore(trust);

                    try {
                        setDocuments(await loadDocumentsWithPhotos(passport.id));
                    } catch (err) {
                        console.warn('Failed to load passport documents:', err);
                    }
                }
            } catch (err) {
                console.error('Failed to load passport:', err);
                setError('The passport could not be loaded. Please retry later.');
            } finally {
                setLoading(false);
            }
        };
        load();
    }, [listingId, directPassportId, isDirectPassportAccess, checkIfCanEdit]);

    const handleSave = async () => {
        if (!validateForm()) {
            setError('Complete all required fields.');
            setTimeout(() => setError(null), 3000);
            return;
        }

        if (!canEdit) {
            setError('A passport linked to a published listing cannot be edited.');
            return;
        }

        const invalidVaccination = vaccinations
            .map((value) => vaccinationError(value, formData.birthDate))
            .find(Boolean);
        if (invalidVaccination) {
            setError(invalidVaccination);
            return;
        }

        setSaving(true);
        setError(null);
        setSuccessMessage(null);

        try {
            const passportData = { ...formData, vaccinations };
            const saved = passportId
                ? await updatePassport(passportId, passportData)
                : await createPassport(passportData);
            setPassportId(saved.id);

            try {
                const trust = await getTrustScore(saved.id);
                setTrustScore(trust);
            } catch {
                // Saving succeeded even if refreshing the evidence score is unavailable.
            }

            setSuccessMessage('Passport saved. Returning to your listings...');

            setTimeout(() => {
                navigate('/my-listings');
            }, 1500);
        } catch (err) {
            console.error('Failed to save passport:', err);
            setError(
                extractDetail(err, 'The passport could not be saved. Check all fields and retry.')
            );
        } finally {
            setSaving(false);
        }
    };

    const addVaccination = () => {
        const invalid = vaccinationError(newVaccination, formData.birthDate);
        if (invalid) {
            setError(invalid);
            return;
        }

        setError(null);

        setVaccinations([
            ...vaccinations,
            {
                id: generateTempId(),
                name: newVaccination.name,
                date: newVaccination.date,
                nextDate: newVaccination.nextDate || null,
                verified: false,
            },
        ]);
        setNewVaccination({ name: '', date: '', nextDate: '' });
        setSuccessMessage('Vaccination added.');
        setTimeout(() => setSuccessMessage(null), 2000);
    };

    const removeVaccination = (index) => {
        setVaccinations(vaccinations.filter((_, i) => i !== index));
        setSuccessMessage('Vaccination removed.');
        setTimeout(() => setSuccessMessage(null), 2000);
    };

    const flashUnsupportedFormat = () => {
        setError(UNSUPPORTED_DOCUMENT_MESSAGE);
        setTimeout(() => setError(null), 3000);
    };

    const handleFileUpload = async (file) => {
        if (!passportId) {
            setError('Save the passport first.');
            setTimeout(() => setError(null), 3000);
            return;
        }
        if (!canEdit) {
            setError('Documents of a passport linked to a published listing cannot be changed.');
            return;
        }
        if (!DOCUMENT_MIME_TYPES.includes(file.type)) {
            flashUnsupportedFormat();
            return;
        }
        setUploading(true);
        setError(null);

        try {
            const type = documentType;
            if (type === 'PHOTO' && file.type === 'application/pdf')
                throw new Error('Choose a document type for PDF files.');
            const doc = await uploadDocument(passportId, file, type);
            let downloadUrl = null;
            try {
                const { url } = await issueDocumentTicket(passportId, doc.id);
                downloadUrl = url;
            } catch (err) {
                console.warn('Failed to get download URL for new document:', err);
            }
            setDocuments([...documents, { ...doc, downloadUrl }]);
            setSuccessMessage('File uploaded.');
            setTimeout(() => setSuccessMessage(null), 2000);
        } catch (err) {
            console.error('Failed to upload file:', err);
            setError(extractDetail(err, 'The file could not be uploaded. Please retry.'));
            setTimeout(() => setError(null), 4000);
        } finally {
            setUploading(false);
        }
    };

    const handleDrop = (e) => {
        e.preventDefault();
        setDragActive(false);
        const file = e.dataTransfer.files?.[0];
        if (file) {
            handleFileUpload(file);
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

    const handleImageError = (docId) => {
        setImageErrors((prev) => ({ ...prev, [docId]: true }));
    };

    if (loading) {
        return (
            <div className="flex justify-center py-12">
                <LoadingSpinner size="lg" />
            </div>
        );
    }

    const photos = documents.filter((doc) => doc.type === 'PHOTO');
    const otherDocs = documents.filter((doc) => doc.type !== 'PHOTO');

    const isReadOnly = !canEdit;

    return (
        <div className="max-w-3xl mx-auto">
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold text-gray-900">Pet passport</h1>
                {trustScore && (
                    <div className="text-right">
                        <div className="text-sm text-gray-500">Evidence score</div>
                        <div className="text-2xl font-bold text-indigo-600">
                            {trustScore.score}%
                        </div>
                    </div>
                )}
            </div>

            {!canEdit && (
                <div className="mb-6 bg-amber-50 border border-amber-200 text-amber-800 px-4 py-3 rounded-md text-sm">
                    This passport is linked to a listing with status
                    {STATUS_LABELS[linkedListingStatus] || linkedListingStatus} . Editing is
                    unavailable while the listing is active. Return it to a draft before editing.
                </div>
            )}

            {!trustScore && passportId && (
                <div className="mb-6 bg-yellow-50 border border-yellow-200 text-yellow-800 px-4 py-3 rounded-md text-sm">
                    Evidence score is unavailable for a passport without a published listing.
                </div>
            )}

            {error && (
                <div className="mb-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md">
                    {error}
                </div>
            )}

            {successMessage && (
                <div className="mb-6 bg-green-50 border border-green-200 text-green-700 px-4 py-3 rounded-md">
                    {successMessage}
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
                            error={validationErrors.name}
                            disabled={isReadOnly}
                            required
                        />
                        <Input
                            id="species"
                            name="species"
                            type="text"
                            label="Species"
                            value={formData.species}
                            onChange={(e) => setFormData({ ...formData, species: e.target.value })}
                            error={validationErrors.species}
                            disabled={isReadOnly}
                            required
                        />
                        <Input
                            id="breed"
                            name="breed"
                            type="text"
                            label="Breed"
                            value={formData.breed}
                            onChange={(e) => setFormData({ ...formData, breed: e.target.value })}
                            disabled={isReadOnly}
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
                            error={validationErrors.birthDate}
                            max={todayIso()}
                            disabled={isReadOnly}
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
                                            disabled={isReadOnly}
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
                            disabled={isReadOnly}
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
                            disabled={isReadOnly}
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
                            disabled={isReadOnly}
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
                                disabled={isReadOnly}
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
                                disabled={isReadOnly}
                            />
                            <span className="text-sm text-gray-700">Microchipped</span>
                        </label>
                    </div>
                </section>

                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Vaccinations</h2>
                    {vaccinations.length > 0 && (
                        <div className="mb-4 space-y-2">
                            {vaccinations.map((vac, index) => (
                                <div
                                    key={vac.id || index}
                                    className="flex justify-between items-center bg-gray-50 p-3 rounded-md"
                                >
                                    <div>
                                        <p className="font-medium">{vac.name}</p>
                                        <p className="text-sm text-gray-500">
                                            {vac.date} {vac.nextDate && `→ next: ${vac.nextDate}`}
                                        </p>
                                    </div>
                                    {!isReadOnly && (
                                        <button
                                            onClick={() => removeVaccination(index)}
                                            className="text-red-600 hover:text-red-800"
                                        >
                                            Delete
                                        </button>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                    {!isReadOnly && (
                        <>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                                <Input
                                    id="vacName"
                                    type="text"
                                    label="Name"
                                    value={newVaccination.name}
                                    onChange={(e) =>
                                        setNewVaccination({
                                            ...newVaccination,
                                            name: e.target.value,
                                        })
                                    }
                                    placeholder="For example: Rabies"
                                />
                                <Input
                                    id="vacDate"
                                    type="date"
                                    label="Date"
                                    min={formData.birthDate || undefined}
                                    max={todayIso()}
                                    value={newVaccination.date}
                                    onChange={(e) =>
                                        setNewVaccination({
                                            ...newVaccination,
                                            date: e.target.value,
                                        })
                                    }
                                />
                                <Input
                                    id="vacNextDate"
                                    type="date"
                                    label="Next due date"
                                    min={newVaccination.date || undefined}
                                    value={newVaccination.nextDate}
                                    onChange={(e) =>
                                        setNewVaccination({
                                            ...newVaccination,
                                            nextDate: e.target.value,
                                        })
                                    }
                                />
                            </div>
                            <button
                                onClick={addVaccination}
                                className="mt-3 text-indigo-600 hover:text-indigo-800 text-sm font-medium"
                            >
                                + Add vaccination
                            </button>
                        </>
                    )}
                    {isReadOnly && vaccinations.length === 0 && (
                        <p className="text-sm text-gray-500">No vaccinations added</p>
                    )}
                </section>

                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">
                        Photos and documents
                    </h2>

                    {!isReadOnly && (
                        <label className="block mb-4" htmlFor="document-type">
                            Document type
                            <select
                                id="document-type"
                                value={documentType}
                                onChange={(event) => setDocumentType(event.target.value)}
                                className="block border rounded p-2"
                            >
                                <option value="PHOTO">Photo (JPEG or PNG)</option>
                                <option value="VACCINATION_CERT">Vaccination certificate</option>
                                <option value="VET_RECORD">Veterinary record</option>
                                <option value="OTHER">Other document</option>
                            </select>
                        </label>
                    )}
                    {/* Existing photos */}
                    {photos.length > 0 && (
                        <div className="mb-4">
                            <p className="text-sm text-gray-600 mb-2">Uploaded photos:</p>
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                                {photos.map((photo) => (
                                    <div
                                        key={photo.id}
                                        className="relative group aspect-square bg-gray-100 rounded-lg overflow-hidden border border-gray-200"
                                    >
                                        {photo.downloadUrl && !imageErrors[photo.id] ? (
                                            <img
                                                src={photo.downloadUrl}
                                                alt={photo.originalFilename}
                                                className="w-full h-full object-cover"
                                                onError={() => handleImageError(photo.id)}
                                            />
                                        ) : (
                                            <div className="w-full h-full flex flex-col items-center justify-center text-gray-400">
                                                <svg
                                                    className="w-8 h-8 mb-1"
                                                    fill="none"
                                                    stroke="currentColor"
                                                    viewBox="0 0 24 24"
                                                >
                                                    <path
                                                        strokeLinecap="round"
                                                        strokeLinejoin="round"
                                                        strokeWidth={1.5}
                                                        d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                                                    />
                                                </svg>
                                                <span className="text-xs">No photo</span>
                                            </div>
                                        )}
                                        {!isReadOnly && (
                                            <button
                                                onClick={() =>
                                                    setDeleteDialog({
                                                        isOpen: true,
                                                        docId: photo.id,
                                                        docName: photo.originalFilename,
                                                    })
                                                }
                                                className="absolute top-1 right-1 bg-red-600 text-white rounded-full w-6 h-6 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                                            >
                                                ×
                                            </button>
                                        )}
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {!isReadOnly && (
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
                                        Drop a file here or{' '}
                                        <label className="text-indigo-600 hover:text-indigo-800 cursor-pointer">
                                            choose a file
                                            <input
                                                type="file"
                                                className="hidden"
                                                onChange={(e) => {
                                                    const file = e.target.files?.[0];
                                                    if (file) handleFileUpload(file);
                                                }}
                                                accept={DOCUMENT_ACCEPT}
                                            />
                                        </label>
                                    </p>
                                    <p className="text-xs text-gray-400 mt-2">
                                        JPEG, PNG and PDF, up to 10 MB
                                    </p>
                                </>
                            )}
                        </div>
                    )}

                    {otherDocs.length > 0 && (
                        <div className="mt-4 space-y-2">
                            <p className="text-sm text-gray-600">Documents:</p>
                            {otherDocs.map((doc) => (
                                <div
                                    key={doc.id}
                                    className="flex justify-between items-center bg-gray-50 p-2 rounded-md"
                                >
                                    <div className="flex items-center gap-2">
                                        <svg
                                            className="w-5 h-5 text-gray-400"
                                            fill="none"
                                            stroke="currentColor"
                                            viewBox="0 0 24 24"
                                        >
                                            <path
                                                strokeLinecap="round"
                                                strokeLinejoin="round"
                                                strokeWidth={2}
                                                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                                            />
                                        </svg>
                                        <span className="text-sm truncate max-w-[200px]">
                                            {doc.originalFilename}
                                        </span>
                                    </div>
                                    {!isReadOnly && (
                                        <button
                                            onClick={() =>
                                                setDeleteDialog({
                                                    isOpen: true,
                                                    docId: doc.id,
                                                    docName: doc.originalFilename,
                                                })
                                            }
                                            className="text-red-600 hover:text-red-800 text-sm"
                                        >
                                            Delete
                                        </button>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                </section>

                <div className="flex justify-end gap-3 pt-4 border-t">
                    <button
                        onClick={() => navigate('/my-listings')}
                        className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200"
                    >
                        Cancel
                    </button>
                    {!isReadOnly && (
                        <button
                            onClick={handleSave}
                            disabled={saving}
                            className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
                        >
                            {saving ? 'Saving...' : 'Save passport'}
                        </button>
                    )}
                </div>
            </div>

            <ConfirmDialog
                isOpen={deleteDialog.isOpen}
                onClose={() => setDeleteDialog({ isOpen: false, docId: null, docName: null })}
                onConfirm={async () => {
                    try {
                        await deleteDocument(passportId, deleteDialog.docId);
                        setDocuments(documents.filter((d) => d.id !== deleteDialog.docId));
                        setSuccessMessage('File deleted.');
                        setTimeout(() => setSuccessMessage(null), 2000);
                    } catch (err) {
                        console.error('Failed to delete document:', err);
                        setError('The file could not be deleted.');
                        setTimeout(() => setError(null), 3000);
                    }
                    setDeleteDialog({ isOpen: false, docId: null, docName: null });
                }}
                title="Delete file"
                message={`Delete file "${deleteDialog.docName}"?`}
            />
        </div>
    );
}
