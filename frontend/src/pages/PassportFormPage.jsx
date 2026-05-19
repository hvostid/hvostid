// pages/PassportFormPage.jsx
import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getListingById } from '../api/listings';
import {
    getPassport,
    createPassport,
    updatePassport,
    getTrustScore,
    uploadDocument,
    deleteDocument,
} from '../api/passports';
import Input from '../components/Input';
import LoadingSpinner from '../components/LoadingSpinner';
import ConfirmDialog from '../components/ConfirmDialog';

const GENDER_OPTIONS = [
    { value: 'MALE', label: 'Мальчик' },
    { value: 'FEMALE', label: 'Девочка' },
];

export default function PassportFormPage() {
    const { id: listingId } = useParams();
    const navigate = useNavigate();

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

    useEffect(() => {
        const load = async () => {
            try {
                const listing = await getListingById(listingId);
                if (listing.passportId) {
                    const passport = await getPassport(listing.passportId);
                    setPassportId(passport.id);
                    setFormData({
                        name: passport.name || '',
                        species: passport.species || listing.species,
                        breed: passport.breed || listing.breed,
                        birthDate: passport.birthDate || '',
                        gender: passport.gender || 'MALE',
                        color: passport.color || '',
                        temperament: passport.temperament || '',
                        specialNeeds: passport.specialNeeds || '',
                        neutered: passport.neutered || false,
                        microchipped: passport.microchipped || false,
                    });
                    setVaccinations(passport.vaccinations || []);
                    const trust = await getTrustScore(listing.passportId);
                    setTrustScore(trust);

                    // Загружаем документы, если есть API
                    if (passport.documents) {
                        setDocuments(passport.documents);
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
    }, [listingId]);

    const handleSave = async () => {
        setSaving(true);
        setError(null);
        setSuccessMessage(null);

        try {
            const passportData = { ...formData, vaccinations };
            const saved = passportId
                ? await updatePassport(passportId, passportData)
                : await createPassport(passportData);
            setPassportId(saved.id);
            const trust = await getTrustScore(saved.id);
            setTrustScore(trust);
            setSuccessMessage('Паспорт успешно сохранён! Возврат к списку объявлений...');

            // ✅ Возвращаемся на страницу моих объявлений через 1.5 секунды
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

    const handleFileUpload = async (file) => {
        if (!passportId) {
            setError('Сначала сохраните паспорт');
            setTimeout(() => setError(null), 3000);
            return;
        }
        setUploading(true);
        setError(null);

        try {
            const doc = await uploadDocument(passportId, file, 'PHOTO');
            setDocuments([...documents, doc]);
            setSuccessMessage('Файл успешно загружен');
            setTimeout(() => setSuccessMessage(null), 2000);
        } catch (err) {
            console.error('Failed to upload file:', err);
            setError('Ошибка загрузки файла. Попробуйте ещё раз.');
            setTimeout(() => setError(null), 3000);
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

    if (loading) {
        return (
            <div className="flex justify-center py-12">
                <LoadingSpinner size="lg" />
            </div>
        );
    }

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
                        />
                        <Input
                            id="species"
                            name="species"
                            type="text"
                            label="Вид"
                            value={formData.species}
                            onChange={(e) => setFormData({ ...formData, species: e.target.value })}
                        />
                        <Input
                            id="breed"
                            name="breed"
                            type="text"
                            label="Порода"
                            value={formData.breed}
                            onChange={(e) => setFormData({ ...formData, breed: e.target.value })}
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
                                    <button
                                        onClick={() => removeVaccination(index)}
                                        className="text-red-600 hover:text-red-800"
                                    >
                                        Удалить
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                        <Input
                            id="vacName"
                            type="text"
                            label="Название"
                            value={newVaccination.name}
                            onChange={(e) =>
                                setNewVaccination({ ...newVaccination, name: e.target.value })
                            }
                            placeholder="Например: Бешенство"
                        />
                        <Input
                            id="vacDate"
                            type="date"
                            label="Дата"
                            value={newVaccination.date}
                            onChange={(e) =>
                                setNewVaccination({ ...newVaccination, date: e.target.value })
                            }
                        />
                        <Input
                            id="vacNextDate"
                            type="date"
                            label="Следующая"
                            value={newVaccination.nextDate}
                            onChange={(e) =>
                                setNewVaccination({ ...newVaccination, nextDate: e.target.value })
                            }
                        />
                    </div>
                    <button
                        onClick={addVaccination}
                        className="mt-3 text-indigo-600 hover:text-indigo-800 text-sm font-medium"
                    >
                        + Добавить прививку
                    </button>
                </section>

                {/* Фото и документы */}
                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Фото и документы</h2>

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
                                            accept="image/*,.pdf"
                                        />
                                    </label>
                                </p>
                                <p className="text-xs text-gray-400 mt-2">
                                    Поддерживаются изображения и PDF (до 10 МБ)
                                </p>
                            </>
                        )}
                    </div>

                    {documents.length > 0 && (
                        <div className="mt-4 space-y-2">
                            <p className="text-sm text-gray-600">Загруженные файлы:</p>
                            {documents.map((doc) => (
                                <div
                                    key={doc.id}
                                    className="flex justify-between items-center bg-gray-50 p-2 rounded-md"
                                >
                                    <span className="text-sm truncate flex-1">
                                        {doc.originalFilename}
                                    </span>
                                    <button
                                        onClick={() =>
                                            setDeleteDialog({
                                                isOpen: true,
                                                docId: doc.id,
                                                docName: doc.originalFilename,
                                            })
                                        }
                                        className="text-red-600 hover:text-red-800 ml-2"
                                    >
                                        Удалить
                                    </button>
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
                    <button
                        onClick={handleSave}
                        disabled={saving}
                        className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
                    >
                        {saving ? 'Сохранение...' : 'Сохранить паспорт'}
                    </button>
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
