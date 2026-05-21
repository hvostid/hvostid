// pages/QuestionnairePage.jsx
import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { getQuestionnaire, saveQuestionnaire } from '../api/matching';
import LoadingSpinner from '../components/LoadingSpinner';

// Значения из бэкенда: APARTMENT, HOUSE, FARM
const LIVING_SPACE_OPTIONS = [
    { value: 'APARTMENT', label: 'Квартира' },
    { value: 'HOUSE', label: 'Частный дом' },
    { value: 'FARM', label: 'Ферма / Загородный дом' },
];

// Значения из бэкенда: NONE, BEGINNER, EXPERIENCED, PROFESSIONAL
const PET_EXPERIENCE_OPTIONS = [
    { value: 'NONE', label: 'Нет опыта' },
    { value: 'BEGINNER', label: 'Начинающий' },
    { value: 'EXPERIENCED', label: 'Опытный владелец' },
    { value: 'PROFESSIONAL', label: 'Профессионал (заводчик, грумер и т.д.)' },
];

// Значения из бэкенда: LOW, MEDIUM, HIGH, VERY_HIGH
const ACTIVITY_LEVEL_OPTIONS = [
    { value: 'LOW', label: 'Низкая' },
    { value: 'MEDIUM', label: 'Средняя' },
    { value: 'HIGH', label: 'Высокая' },
    { value: 'VERY_HIGH', label: 'Очень высокая' },
];

// Значения из бэкенда: HOME, HYBRID, OFFICE
const WORK_SCHEDULE_OPTIONS = [
    { value: 'HOME', label: 'Полностью из дома' },
    { value: 'HYBRID', label: 'Гибридный (офис + дом)' },
    { value: 'OFFICE', label: 'Офис (5/2)' },
];

const SPECIES_OPTIONS = [
    { value: '', label: 'Любой' },
    { value: 'CAT', label: 'Кошка' },
    { value: 'DOG', label: 'Собака' },
    { value: 'BIRD', label: 'Птица' },
    { value: 'RABBIT', label: 'Кролик' },
    { value: 'OTHER', label: 'Другое' },
];

export default function QuestionnairePage() {
    const navigate = useNavigate();
    const location = useLocation();
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [validationErrors, setValidationErrors] = useState({});

    const params = new URLSearchParams(location.search);
    const returnUrl = params.get('returnUrl');

    const [formData, setFormData] = useState({
        livingSpace: 'APARTMENT',
        livingArea: '',
        hasYard: false,
        hasChildren: false,
        childrenAgeMin: '',
        hasAllergies: false,
        allergyDetails: '',
        petExperience: 'BEGINNER',
        activityLevel: 'MEDIUM',
        monthlyBudget: '',
        workSchedule: 'OFFICE',
        readyForAdaptation: true,
        preferredSpecies: '',
        preferredBreed: '',
    });

    useEffect(() => {
        const loadQuestionnaire = async () => {
            try {
                const data = await getQuestionnaire();
                if (data) {
                    setFormData({
                        livingSpace: data.livingSpace || 'APARTMENT',
                        livingArea: data.livingArea || '',
                        hasYard: data.hasYard || false,
                        hasChildren: data.hasChildren || false,
                        childrenAgeMin: data.childrenAgeMin || '',
                        hasAllergies: data.hasAllergies || false,
                        allergyDetails: data.allergyDetails || '',
                        petExperience: data.petExperience || 'BEGINNER',
                        activityLevel: data.activityLevel || 'MEDIUM',
                        monthlyBudget: data.monthlyBudget || '',
                        workSchedule: data.workSchedule || 'OFFICE',
                        readyForAdaptation: data.readyForAdaptation ?? true,
                        preferredSpecies: data.preferredSpecies || '',
                        preferredBreed: data.preferredBreed || '',
                    });
                }
            } catch (err) {
                console.error('Failed to load questionnaire:', err);
            } finally {
                setLoading(false);
            }
        };
        loadQuestionnaire();
    }, []);

    // Валидация формы
    const validateForm = () => {
        const errors = {};

        // Валидация площади жилья (обязательное поле, положительное число)
        if (!formData.livingArea || formData.livingArea === '') {
            errors.livingArea = 'Площадь жилья обязательна для заполнения';
        } else if (parseInt(formData.livingArea, 10) <= 0) {
            errors.livingArea = 'Площадь жилья должна быть больше 0 м²';
        } else if (parseInt(formData.livingArea, 10) > 1000) {
            errors.livingArea = 'Площадь жилья не может превышать 1000 м²';
        }

        // Валидация бюджета (обязательное поле, положительное число)
        if (!formData.monthlyBudget || formData.monthlyBudget === '') {
            errors.monthlyBudget = 'Месячный бюджет обязателен для заполнения';
        } else if (parseInt(formData.monthlyBudget, 10) < 0) {
            errors.monthlyBudget = 'Бюджет не может быть отрицательным';
        } else if (parseInt(formData.monthlyBudget, 10) > 1000000) {
            errors.monthlyBudget = 'Бюджет не может превышать 1 000 000 ₽';
        }

        // Валидация возраста детей (если есть дети)
        if (formData.hasChildren && (!formData.childrenAgeMin || formData.childrenAgeMin === '')) {
            errors.childrenAgeMin = 'Укажите минимальный возраст детей';
        } else if (formData.hasChildren && parseInt(formData.childrenAgeMin, 10) < 0) {
            errors.childrenAgeMin = 'Возраст детей не может быть отрицательным';
        }

        setValidationErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!validateForm()) {
            setError('Пожалуйста, заполните все обязательные поля');
            return;
        }

        setSaving(true);
        setError('');
        setSuccess('');

        try {
            const dataToSend = {
                ...formData,
                livingArea: formData.livingArea ? parseInt(formData.livingArea, 10) : null,
                childrenAgeMin: formData.childrenAgeMin
                    ? parseInt(formData.childrenAgeMin, 10)
                    : null,
                monthlyBudget: formData.monthlyBudget ? parseInt(formData.monthlyBudget, 10) : null,
            };

            await saveQuestionnaire(dataToSend);
            setSuccess('Анкета успешно сохранена!');

            setTimeout(() => {
                if (returnUrl) {
                    navigate(returnUrl);
                } else {
                    navigate('/profile');
                }
            }, 1500);
        } catch (err) {
            console.error('Failed to save questionnaire:', err);
            setError('Не удалось сохранить анкету');
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return (
            <div className="flex justify-center py-12">
                <LoadingSpinner size="lg" />
            </div>
        );
    }

    return (
        <div className="max-w-2xl mx-auto">
            <h1 className="text-2xl font-bold text-gray-900 mb-6">Анкета совместимости</h1>
            <p className="text-gray-600 mb-6">
                Заполните эту анкету, чтобы получать рекомендации подходящих питомцев и оценку
                совместимости. Поля отмеченные <span className="text-red-500">*</span> обязательны.
            </p>

            {error && (
                <div className="mb-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md">
                    {error}
                </div>
            )}

            {success && (
                <div className="mb-6 bg-green-50 border border-green-200 text-green-700 px-4 py-3 rounded-md">
                    {success}
                </div>
            )}

            <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow p-6 space-y-6">
                {/* Условия проживания */}
                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Условия проживания</h2>
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Тип жилья <span className="text-red-500">*</span>
                            </label>
                            <select
                                value={formData.livingSpace}
                                onChange={(e) =>
                                    setFormData({ ...formData, livingSpace: e.target.value })
                                }
                                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                            >
                                {LIVING_SPACE_OPTIONS.map((opt) => (
                                    <option key={opt.value} value={opt.value}>
                                        {opt.label}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Площадь жилья (м²) <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="number"
                                value={formData.livingArea}
                                onChange={(e) =>
                                    setFormData({ ...formData, livingArea: e.target.value })
                                }
                                className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500
                                    ${validationErrors.livingArea ? 'border-red-500' : 'border-gray-300'}`}
                            />
                            {validationErrors.livingArea && (
                                <p className="mt-1 text-sm text-red-600">
                                    {validationErrors.livingArea}
                                </p>
                            )}
                        </div>

                        <label className="flex items-center">
                            <input
                                type="checkbox"
                                checked={formData.hasYard}
                                onChange={(e) =>
                                    setFormData({ ...formData, hasYard: e.target.checked })
                                }
                                className="mr-2"
                            />
                            <span className="text-sm text-gray-700">Есть свой двор/участок</span>
                        </label>
                    </div>
                </section>

                {/* Семья и аллергии */}
                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Семья и здоровье</h2>
                    <div className="space-y-4">
                        <label className="flex items-center">
                            <input
                                type="checkbox"
                                checked={formData.hasChildren}
                                onChange={(e) =>
                                    setFormData({ ...formData, hasChildren: e.target.checked })
                                }
                                className="mr-2"
                            />
                            <span className="text-sm text-gray-700">В семье есть дети</span>
                        </label>

                        {formData.hasChildren && (
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Минимальный возраст детей
                                </label>
                                <input
                                    type="number"
                                    value={formData.childrenAgeMin}
                                    onChange={(e) =>
                                        setFormData({ ...formData, childrenAgeMin: e.target.value })
                                    }
                                    className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500
                                        ${validationErrors.childrenAgeMin ? 'border-red-500' : 'border-gray-300'}`}
                                    placeholder="Например: 3"
                                />
                                {validationErrors.childrenAgeMin && (
                                    <p className="mt-1 text-sm text-red-600">
                                        {validationErrors.childrenAgeMin}
                                    </p>
                                )}
                            </div>
                        )}

                        <label className="flex items-center">
                            <input
                                type="checkbox"
                                checked={formData.hasAllergies}
                                onChange={(e) =>
                                    setFormData({ ...formData, hasAllergies: e.target.checked })
                                }
                                className="mr-2"
                            />
                            <span className="text-sm text-gray-700">Есть аллергия на животных</span>
                        </label>

                        {formData.hasAllergies && (
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Подробности об аллергии
                                </label>
                                <textarea
                                    rows="2"
                                    value={formData.allergyDetails}
                                    onChange={(e) =>
                                        setFormData({ ...formData, allergyDetails: e.target.value })
                                    }
                                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                                    placeholder="На что аллергия, насколько сильная реакция..."
                                />
                            </div>
                        )}
                    </div>
                </section>

                {/* Опыт и бюджет */}
                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Опыт и бюджет</h2>
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Опыт содержания животных
                            </label>
                            <select
                                value={formData.petExperience}
                                onChange={(e) =>
                                    setFormData({ ...formData, petExperience: e.target.value })
                                }
                                className="w-full px-3 py-2 border border-gray-300 rounded-md"
                            >
                                {PET_EXPERIENCE_OPTIONS.map((opt) => (
                                    <option key={opt.value} value={opt.value}>
                                        {opt.label}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Уровень активности
                            </label>
                            <select
                                value={formData.activityLevel}
                                onChange={(e) =>
                                    setFormData({ ...formData, activityLevel: e.target.value })
                                }
                                className="w-full px-3 py-2 border border-gray-300 rounded-md"
                            >
                                {ACTIVITY_LEVEL_OPTIONS.map((opt) => (
                                    <option key={opt.value} value={opt.value}>
                                        {opt.label}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Месячный бюджет на питомца (₽){' '}
                                <span className="text-red-500">*</span>
                            </label>
                            <input
                                type="number"
                                value={formData.monthlyBudget}
                                onChange={(e) =>
                                    setFormData({ ...formData, monthlyBudget: e.target.value })
                                }
                                className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500
                                    ${validationErrors.monthlyBudget ? 'border-red-500' : 'border-gray-300'}`}
                                placeholder="Например: 5000"
                            />
                            {validationErrors.monthlyBudget && (
                                <p className="mt-1 text-sm text-red-600">
                                    {validationErrors.monthlyBudget}
                                </p>
                            )}
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                График работы
                            </label>
                            <select
                                value={formData.workSchedule}
                                onChange={(e) =>
                                    setFormData({ ...formData, workSchedule: e.target.value })
                                }
                                className="w-full px-3 py-2 border border-gray-300 rounded-md"
                            >
                                {WORK_SCHEDULE_OPTIONS.map((opt) => (
                                    <option key={opt.value} value={opt.value}>
                                        {opt.label}
                                    </option>
                                ))}
                            </select>
                        </div>
                    </div>
                </section>

                {/* Предпочтения */}
                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Предпочтения</h2>
                    <div className="space-y-4">
                        <label className="flex items-center">
                            <input
                                type="checkbox"
                                checked={formData.readyForAdaptation}
                                onChange={(e) =>
                                    setFormData({
                                        ...formData,
                                        readyForAdaptation: e.target.checked,
                                    })
                                }
                                className="mr-2"
                            />
                            <span className="text-sm text-gray-700">
                                Готов к адаптационному периоду (первые 14 дней с поддержкой)
                            </span>
                        </label>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Предпочитаемый вид животного
                            </label>
                            <select
                                value={formData.preferredSpecies}
                                onChange={(e) =>
                                    setFormData({ ...formData, preferredSpecies: e.target.value })
                                }
                                className="w-full px-3 py-2 border border-gray-300 rounded-md"
                            >
                                {SPECIES_OPTIONS.map((opt) => (
                                    <option key={opt.value} value={opt.value}>
                                        {opt.label}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Предпочитаемая порода (необязательно)
                            </label>
                            <input
                                type="text"
                                value={formData.preferredBreed}
                                onChange={(e) =>
                                    setFormData({ ...formData, preferredBreed: e.target.value })
                                }
                                className="w-full px-3 py-2 border border-gray-300 rounded-md"
                                placeholder="Например: Мейн-кун, Лабрадор..."
                            />
                        </div>
                    </div>
                </section>

                <div className="flex justify-end gap-3 pt-4 border-t">
                    <button
                        type="button"
                        onClick={() => navigate(-1)}
                        className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200"
                    >
                        Отмена
                    </button>
                    <button
                        type="submit"
                        disabled={saving}
                        className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
                    >
                        {saving ? 'Сохранение...' : 'Сохранить анкету'}
                    </button>
                </div>
            </form>
        </div>
    );
}
