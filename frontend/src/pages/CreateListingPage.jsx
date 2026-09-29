// pages/CreateListingPage.jsx
import { useState, useEffect } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { createListing, getMyListings, getListingDraft, saveListingDraft } from '../api/listings';
import { getAllMyPassports, getPassport } from '../api/passports';
import ListingForm from '../components/ListingForm';
import LoadingSpinner from '../components/LoadingSpinner';
import { extractDetail } from '../utils/format';

const toDraft = (data) => ({
    title: data.title.trim(),
    description: data.description?.trim() || null,
    species: data.species,
    breed: data.breed?.trim() || null,
    age: data.age === '' || data.age == null ? null : Number(data.age),
    price: data.price === '' || data.price == null ? null : Number(data.price),
    city: data.city.trim(),
});

export default function CreateListingPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const [searchParams] = useSearchParams();
    const passportIdFromUrl = searchParams.get('passportId');

    const [step, setStep] = useState('form');
    const [formData, setFormData] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [preloadedPassport, setPreloadedPassport] = useState(null);
    const [loadingPreload, setLoadingPreload] = useState(true);
    const [loadFailed, setLoadFailed] = useState(false);

    const [passports, setPassports] = useState([]);
    const [loadingPassports, setLoadingPassports] = useState(false);
    const [selectedPassportId, setSelectedPassportId] = useState('');
    const [listings, setListings] = useState([]);

    // Загрузка паспортов и объявлений (для проверки используемых паспортов)
    const loadPassportsAndListings = async () => {
        setLoadingPassports(true);
        try {
            const [passportsData, listingsData] = await Promise.all([
                getAllMyPassports(0, 100),
                getMyListings(),
            ]);
            setPassports(passportsData.content || []);
            setListings(listingsData.content || []);
        } catch (err) {
            console.error('Failed to load data:', err);
            setPassports([]);
            setListings([]);
            throw err;
        } finally {
            setLoadingPassports(false);
        }
    };

    // Проверка, используется ли паспорт в каком-либо объявлении
    const isPassportInUse = (passportId) => {
        return listings.some((l) => String(l.passportId) === String(passportId));
    };

    // Restore the server draft before rendering the form or applying passport defaults.
    useEffect(() => {
        let cancelled = false;
        const restore = async () => {
            setLoadingPreload(true);
            setLoadFailed(false);
            try {
                const [draft, passport] = await Promise.all([
                    getListingDraft(),
                    passportIdFromUrl ? getPassport(passportIdFromUrl) : null,
                ]);
                const [passportsData, listingsData] = passport
                    ? await Promise.all([getAllMyPassports(0, 100), getMyListings()])
                    : [null, null];
                if (cancelled) return;
                setFormData(
                    draft ||
                        (passport
                            ? {
                                  title: '',
                                  description: '',
                                  species: passport.species || '',
                                  breed: passport.breed || '',
                                  age: '',
                                  price: '',
                                  city: '',
                              }
                            : null)
                );
                setPreloadedPassport(passport);
                setSelectedPassportId(passportIdFromUrl || '');
                setPassports(passportsData?.content || []);
                setListings(listingsData?.content || []);
                setStep(draft && passport ? 'passportChoice' : 'form');
            } catch (err) {
                if (!cancelled) {
                    setError(extractDetail(err, 'Не удалось восстановить форму объявления'));
                    setLoadFailed(true);
                }
            } finally {
                if (!cancelled) setLoadingPreload(false);
            }
        };
        restore();
        return () => {
            cancelled = true;
        };
    }, [passportIdFromUrl]);

    // Когда пользователь заполнил форму
    const handleFormSubmit = async (data) => {
        setFormData(data);
        setIsSubmitting(true);
        setError('');
        try {
            await saveListingDraft(toDraft(data));
            await loadPassportsAndListings();
            setStep('passportChoice');
        } catch (err) {
            setError(extractDetail(err, 'Не удалось сохранить форму объявления'));
        } finally {
            setIsSubmitting(false);
        }
    };

    // Использовать существующий паспорт
    const handleUseExistingPassport = async () => {
        if (!selectedPassportId) {
            setError('Пожалуйста, выберите паспорт');
            return;
        }

        // Проверка, не используется ли уже паспорт
        if (isPassportInUse(selectedPassportId)) {
            setError('Этот паспорт уже используется в другом объявлении');
            return;
        }

        setIsSubmitting(true);
        setError('');

        try {
            const listingData = {
                ...toDraft(formData),
                passportId: String(selectedPassportId),
            };

            await createListing(listingData);
            navigate('/my-listings');
        } catch (err) {
            console.error('Failed to create listing:', err);
            if (err.response?.status === 409) {
                setError('Этот паспорт уже используется в другом объявлении');
            } else {
                setError(extractDetail(err, 'Не удалось создать объявление'));
            }
        } finally {
            setIsSubmitting(false);
        }
    };

    // Создать новый паспорт — переходим на отдельную страницу
    const handleCreateNewPassport = () => {
        navigate('/passports/new?from=listing');
    };

    const handleBackToForm = () => {
        setStep('form');
        setError('');
    };

    if (loadingPreload) {
        return (
            <div className="flex justify-center py-12">
                <LoadingSpinner size="lg" />
            </div>
        );
    }

    if (loadFailed) {
        return <div className="text-red-600 py-12">{error}</div>;
    }

    if (step === 'form') {
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
                        initialData={formData}
                        onSubmit={handleFormSubmit}
                        isSubmitting={isSubmitting}
                        submitLabel="Далее →"
                    />
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-2xl mx-auto">
            <h1 className="text-2xl font-bold text-gray-900 mb-6">Выбор паспорта</h1>
            <p className="text-gray-600 mb-4">
                У питомца уже есть паспорт? Выберите его из списка или создайте новый.
            </p>

            {location.state?.warning && (
                <div role="alert" className="mb-6 bg-yellow-50 text-yellow-800 p-4 rounded-md">
                    {location.state.warning}
                </div>
            )}

            {preloadedPassport && (
                <div className="mb-6 bg-green-50 border border-green-200 text-green-800 px-4 py-3 rounded-md">
                    <p className="font-medium">✅ Паспорт создан!</p>
                    <p className="text-sm mt-1">
                        {preloadedPassport.name || 'Без имени'} — {preloadedPassport.species}
                        {preloadedPassport.breed ? ` (${preloadedPassport.breed})` : ''}
                    </p>
                </div>
            )}

            {error && (
                <div className="mb-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md">
                    {error}
                </div>
            )}

            <div className="bg-white rounded-lg shadow p-6">
                <button
                    onClick={handleBackToForm}
                    className="mb-6 text-sm text-indigo-600 hover:text-indigo-800 flex items-center gap-1"
                >
                    ← Назад к форме
                </button>

                {loadingPassports ? (
                    <div className="flex justify-center py-8">
                        <LoadingSpinner size="md" />
                    </div>
                ) : (
                    <>
                        {passports.length > 0 && !preloadedPassport && (
                            <div className="mb-6">
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Мои паспорта
                                </label>
                                <select
                                    value={selectedPassportId}
                                    onChange={(e) => setSelectedPassportId(e.target.value)}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                                >
                                    <option value="">-- Выберите паспорт --</option>
                                    {passports.map((passport) => {
                                        const inUse = isPassportInUse(passport.id);
                                        return (
                                            <option
                                                key={passport.id}
                                                value={passport.id}
                                                disabled={inUse}
                                                className={inUse ? 'text-gray-400' : ''}
                                            >
                                                {passport.name || 'Без имени'} — {passport.species}
                                                {passport.breed ? ` (${passport.breed})` : ''}
                                                {inUse ? ' (уже используется)' : ''}
                                            </option>
                                        );
                                    })}
                                </select>
                            </div>
                        )}

                        {preloadedPassport && (
                            <div className="mb-6 p-3 bg-gray-50 rounded-md border border-gray-200">
                                <p className="text-sm text-gray-600 mb-2">Выбран паспорт:</p>
                                <p className="font-medium">
                                    {preloadedPassport.name || 'Без имени'} —{' '}
                                    {preloadedPassport.species}
                                    {preloadedPassport.breed ? ` (${preloadedPassport.breed})` : ''}
                                </p>
                            </div>
                        )}

                        <div className="flex flex-col gap-3">
                            {(selectedPassportId || preloadedPassport) && (
                                <button
                                    onClick={handleUseExistingPassport}
                                    disabled={
                                        isSubmitting ||
                                        (selectedPassportId && isPassportInUse(selectedPassportId))
                                    }
                                    className="w-full px-4 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700 disabled:opacity-50 transition-colors"
                                >
                                    {isSubmitting
                                        ? 'Создание...'
                                        : 'Использовать выбранный паспорт'}
                                </button>
                            )}

                            {!preloadedPassport && (
                                <button
                                    onClick={handleCreateNewPassport}
                                    disabled={isSubmitting}
                                    className="w-full px-4 py-2 bg-gray-100 text-gray-700 rounded-md hover:bg-gray-200 disabled:opacity-50 transition-colors"
                                >
                                    {isSubmitting ? 'Создание...' : '+ Создать новый паспорт'}
                                </button>
                            )}
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}
