// pages/PassportFormPage.jsx
import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getListingById, getMyListings } from '../api/listings';
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
import Input from '../components/Input';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';
import { DOCUMENT_ACCEPT, DOCUMENT_MIME_TYPES } from '../constants/passportDocuments';
import { extractDetail, todayIso } from '../utils/format';

const UNSUPPORTED_DOCUMENT_MESSAGE =
    'Формат файла не поддерживается. Допустимы JPEG, PNG или PDF, до 10 МБ.';

// Fetch a passport's documents and resolve a short-TTL <img src> ticket for
// each PHOTO so the grid can render them. Non-photo docs are returned as-is.
const loadDocumentsWithPhotos = async (passportId) => {
    const docs = await getPassportDocuments(passportId);
    return Promise.all(
        docs.map(async (doc) => {
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
        })
    );
};

const GENDER_OPTIONS = [
    { value: 'MALE', label: 'Мальчик' },
    { value: 'FEMALE', label: 'Девочка' },
];

const STATUS_LABELS = {
    DRAFT: 'Черновик',
    MODERATION: 'На модерации',
    PUBLISHED: 'Опубликовано',
    REJECTED: 'Отклонено',
    ARCHIVED: 'В архиве',
    SOLD: 'Продано',
};

export default function PassportFormPage() {
    const { id: listingId, passportId: directPassportId } = useParams();
    const navigate = useNavigate();

    // Определяем, как мы попали на страницу
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
    const [canEdit, setCanEdit] = useState(true);
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
    const [documents, setDocuments] = useState([]);
    const [uploading, setUploading] = useState(false);
    const [dragActive, setDragActive] = useState(false);
    const [deleteDialog, setDeleteDialog] = useState({ isOpen: false, docId: null, docName: null });
    const [imageErrors, setImageErrors] = useState({});

    // Валидация обязательных полей
    const validateForm = () => {
        const errors = {};

        if (!formData.name || formData.name.trim() === '') {
            errors.name = 'Кличка обязательна для заполнения';
        }
        if (!formData.species || formData.species.trim() === '') {
            errors.species = 'Вид животного обязателен для заполнения';
        }
        if (!formData.birthDate || formData.birthDate.trim() === '') {
            errors.birthDate = 'Дата рождения обязательна для заполнения';
        } else if (formData.birthDate > todayIso()) {
            errors.birthDate = 'Дата рождения не может быть в будущем';
        }

        setValidationErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const getAllListings = async () => {
        let allListings = [];
        let page = 0;
        let hasMore = true;

        while (hasMore) {
            const data = await getMyListings(null, page, 100);
            allListings = [...allListings, ...(data.content || [])];
            hasMore = page + 1 < data.totalPages;
            page++;
        }

        return allListings;
    };

    const checkIfCanEdit = async (passportId) => {
        try {
            const allListings = await getAllListings();
            const linkedListing = allListings.find(
                (l) => String(l.passportId) === String(passportId)
            );

            if (linkedListing) {
                setLinkedListingStatus(linkedListing.status);
                setCanEdit(linkedListing.status === 'DRAFT');
                return linkedListing.status === 'DRAFT';
            } else {
                setCanEdit(true);
                setLinkedListingStatus(null);
                return true;
            }
        } catch (err) {
            console.error('Failed to check listing status:', err);
            setCanEdit(true);
            return true;
        }
    };

    useEffect(() => {
        const load = async () => {
            try {
                // Если прямой доступ к паспорту (через /passports/:passportId/edit)
                if (isDirectPassportAccess && directPassportId) {
                    const passport = await getPassport(directPassportId);
                    setPassportId(passport.id);

                    // Проверяем, можно ли редактировать
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
                        console.log('Trust score not available for unlinked passport');
                    }

                    try {
                        setDocuments(await loadDocumentsWithPhotos(passport.id));
                    } catch (err) {
                        console.warn('Failed to load passport documents:', err);
                    }
                    setLoading(false);
                    return;
                }

                // Старая логика: через listingId
                const listing = await getListingById(listingId);
                if (listing.passportId) {
                    const passport = await getPassport(listing.passportId);
                    setPassportId(passport.id);

                    // Проверяем, можно ли редактировать
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
                setError('Не удалось загрузить данные паспорта. Пожалуйста, попробуйте позже.');
            } finally {
                setLoading(false);
            }
        };
        load();
    }, [listingId, directPassportId]);

    const handleSave = async () => {
        // Валидация обязательных полей
        if (!validateForm()) {
            setError('Пожалуйста, заполните все обязательные поля');
            setTimeout(() => setError(null), 3000);
            return;
        }

        if (!canEdit) {
            setError('Нельзя редактировать паспорт, привязанный к опубликованному объявлению');
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
                // Trust score не обновляется для паспорта без опубликованного объявления
            }

            setSuccessMessage('Паспорт успешно сохранён! Возврат к списку объявлений...');

            setTimeout(() => {
                navigate('/my-listings');
            }, 1500);
        } catch (err) {
            console.error('Failed to save passport:', err);
            setError('Ошибка при сохранении паспорта. Проверьте все поля и попробуйте снова.');
        } finally {
            setSaving(false);
        }
    };

    const addVaccination = () => {
        if (!newVaccination.name || !newVaccination.date) {
            setError('Заполните название и дату прививки');
            setTimeout(() => setError(null), 3000);
            return;
        }

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
        setSuccessMessage('Прививка добавлена');
        setTimeout(() => setSuccessMessage(null), 2000);
    };

    const removeVaccination = (index) => {
        setVaccinations(vaccinations.filter((_, i) => i !== index));
        setSuccessMessage('Прививка удалена');
        setTimeout(() => setSuccessMessage(null), 2000);
    };

    const flashUnsupportedFormat = () => {
        setError(UNSUPPORTED_DOCUMENT_MESSAGE);
        setTimeout(() => setError(null), 3000);
    };

    const handleFileUpload = async (file) => {
        if (!passportId) {
            setError('Сначала сохраните паспорт');
            setTimeout(() => setError(null), 3000);
            return;
        }
        if (!canEdit) {
            setError(
                'Нельзя изменять документы паспорта, привязанного к опубликованному объявлению'
            );
            return;
        }
        if (!DOCUMENT_MIME_TYPES.includes(file.type)) {
            flashUnsupportedFormat();
            return;
        }
        setUploading(true);
        setError(null);

        try {
            const type = file.type === 'application/pdf' ? 'OTHER' : 'PHOTO';
            const doc = await uploadDocument(passportId, file, type);
            let downloadUrl = null;
            try {
                const { url } = await issueDocumentTicket(passportId, doc.id);
                downloadUrl = url;
            } catch (err) {
                console.warn('Failed to get download URL for new document:', err);
            }
            setDocuments([...documents, { ...doc, downloadUrl }]);
            setSuccessMessage('Файл успешно загружен');
            setTimeout(() => setSuccessMessage(null), 2000);
        } catch (err) {
            console.error('Failed to upload file:', err);
            setError(extractDetail(err, 'Ошибка загрузки файла. Попробуйте ещё раз.'));
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

    // Разделяем документы на фото и другие
    const photos = documents.filter((doc) => doc.type === 'PHOTO');
    const otherDocs = documents.filter((doc) => doc.type !== 'PHOTO');

    // Статус блокировки редактирования
    const isReadOnly = !canEdit;

    return (
        <div className="max-w-3xl mx-auto">
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold text-gray-900">Паспорт питомца</h1>
                {trustScore && (
                    <div className="text-right">
                        <div className="text-sm text-gray-500">Trust score</div>
                        <div className="text-2xl font-bold text-indigo-600">
                            {trustScore.score}%
                        </div>
                    </div>
                )}
            </div>

            {!canEdit && (
                <div className="mb-6 bg-amber-50 border border-amber-200 text-amber-800 px-4 py-3 rounded-md text-sm">
                    Этот паспорт привязан к объявлению со статусом «
                    {STATUS_LABELS[linkedListingStatus] || linkedListingStatus}». Редактирование
                    паспорта недоступно. Вы можете отредактировать паспорт только для объявлений в
                    статусе «Черновик».
                </div>
            )}

            {!trustScore && passportId && (
                <div className="mb-6 bg-yellow-50 border border-yellow-200 text-yellow-800 px-4 py-3 rounded-md text-sm">
                    Trust score недоступен для паспорта, не привязанного к опубликованному
                    объявлению.
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
                {/* Основная информация */}
                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">
                        Основная информация
                    </h2>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <Input
                            id="name"
                            name="name"
                            type="text"
                            label="Кличка"
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
                            label="Вид"
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
                            label="Порода"
                            value={formData.breed}
                            onChange={(e) => setFormData({ ...formData, breed: e.target.value })}
                            disabled={isReadOnly}
                        />
                        <Input
                            id="birthDate"
                            name="birthDate"
                            type="date"
                            label="Дата рождения"
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
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Пол
                            </label>
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
                            label="Окрас"
                            value={formData.color}
                            onChange={(e) => setFormData({ ...formData, color: e.target.value })}
                            disabled={isReadOnly}
                        />
                        <Input
                            id="temperament"
                            name="temperament"
                            type="text"
                            label="Характер"
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
                            label="Особые потребности"
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
                            <span className="text-sm text-gray-700">Стерилизован/кастрирован</span>
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
                            <span className="text-sm text-gray-700">Чипирован</span>
                        </label>
                    </div>
                </section>

                {/* Прививки */}
                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Прививки</h2>
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
                                            {vac.date}{' '}
                                            {vac.nextDate && `→ следующая: ${vac.nextDate}`}
                                        </p>
                                    </div>
                                    {!isReadOnly && (
                                        <button
                                            onClick={() => removeVaccination(index)}
                                            className="text-red-600 hover:text-red-800"
                                        >
                                            Удалить
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
                                    label="Название"
                                    value={newVaccination.name}
                                    onChange={(e) =>
                                        setNewVaccination({
                                            ...newVaccination,
                                            name: e.target.value,
                                        })
                                    }
                                    placeholder="Например: Бешенство"
                                />
                                <Input
                                    id="vacDate"
                                    type="date"
                                    label="Дата"
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
                                    label="Следующая"
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
                                + Добавить прививку
                            </button>
                        </>
                    )}
                    {isReadOnly && vaccinations.length === 0 && (
                        <p className="text-sm text-gray-500">Нет добавленных прививок</p>
                    )}
                </section>

                {/* Фото и документы */}
                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Фото и документы</h2>

                    {/* Сетка с превью существующих фото */}
                    {photos.length > 0 && (
                        <div className="mb-4">
                            <p className="text-sm text-gray-600 mb-2">Загруженные фото:</p>
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
                                                <span className="text-xs">Нет фото</span>
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

                    {/* Drag-and-drop зона для загрузки новых фото */}
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
                                        Перетащите файл сюда или{' '}
                                        <label className="text-indigo-600 hover:text-indigo-800 cursor-pointer">
                                            выберите из папки
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
                                        Поддерживаются JPEG, PNG и PDF, до 10 МБ
                                    </p>
                                </>
                            )}
                        </div>
                    )}

                    {/* Другие документы (не фото) */}
                    {otherDocs.length > 0 && (
                        <div className="mt-4 space-y-2">
                            <p className="text-sm text-gray-600">Документы:</p>
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
                                            Удалить
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
                        Отмена
                    </button>
                    {!isReadOnly && (
                        <button
                            onClick={handleSave}
                            disabled={saving}
                            className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
                        >
                            {saving ? 'Сохранение...' : 'Сохранить паспорт'}
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
                        setSuccessMessage('Файл удалён');
                        setTimeout(() => setSuccessMessage(null), 2000);
                    } catch (err) {
                        console.error('Failed to delete document:', err);
                        setError('Ошибка при удалении файла');
                        setTimeout(() => setError(null), 3000);
                    }
                    setDeleteDialog({ isOpen: false, docId: null, docName: null });
                }}
                title="Удалить файл"
                message={`Вы уверены, что хотите удалить файл "${deleteDialog.docName}"?`}
            />
        </div>
    );
}
