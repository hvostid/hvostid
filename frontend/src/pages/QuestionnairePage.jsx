import { PET_SPECIES } from '../constants/petSpecies';
// pages/QuestionnairePage.jsx
import { useState, useEffect } from 'react';
import { returnPath } from '../utils/returnPath';
import { useNavigate, useLocation } from 'react-router-dom';
import { getQuestionnaire, saveQuestionnaire } from '../api/matching';
import LoadingSpinner from '../components/LoadingSpinner';

const LIVING_SPACE_OPTIONS = [
    { value: 'APARTMENT', label: 'Apartment' },
    { value: 'HOUSE', label: 'House' },
    { value: 'FARM', label: 'Farm or country home' },
];

const PET_EXPERIENCE_OPTIONS = [
    { value: 'NONE', label: 'No experience' },
    { value: 'BEGINNER', label: 'Beginner' },
    { value: 'EXPERIENCED', label: 'Experienced owner' },
    { value: 'PROFESSIONAL', label: 'Professional (breeder, groomer, etc.)' },
];

const ACTIVITY_LEVEL_OPTIONS = [
    { value: 'LOW', label: 'Low' },
    { value: 'MEDIUM', label: 'Medium' },
    { value: 'HIGH', label: 'High' },
    { value: 'VERY_HIGH', label: 'Very high' },
];

const WORK_SCHEDULE_OPTIONS = [
    { value: 'HOME', label: 'Work from home' },
    { value: 'HYBRID', label: 'Hybrid (office and home)' },
    { value: 'OFFICE', label: 'Office (five days a week)' },
];

const SPECIES_OPTIONS = [{ value: '', label: 'Any species' }, ...PET_SPECIES];

export default function QuestionnairePage() {
    const navigate = useNavigate();
    const location = useLocation();
    const [loading, setLoading] = useState(true);
    const [loadFailed, setLoadFailed] = useState(false);
    const [loadAttempt, setLoadAttempt] = useState(0);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [validationErrors, setValidationErrors] = useState({});

    const params = new URLSearchParams(location.search);
    const requestedReturn = params.get('returnUrl');
    const returnUrl = returnPath(requestedReturn, '/profile');

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
        let cancelled = false;
        const loadQuestionnaire = async () => {
            setLoading(true);
            setLoadFailed(false);
            try {
                const data = await getQuestionnaire();
                if (cancelled) return;
                if (data) {
                    setFormData({
                        livingSpace: data.livingSpace || 'APARTMENT',
                        livingArea: data.livingArea ?? '',
                        hasYard: data.hasYard || false,
                        hasChildren: data.hasChildren || false,
                        childrenAgeMin: data.childrenAgeMin ?? '',
                        hasAllergies: data.hasAllergies || false,
                        allergyDetails: data.allergyDetails || '',
                        petExperience: data.petExperience || 'BEGINNER',
                        activityLevel: data.activityLevel || 'MEDIUM',
                        monthlyBudget: data.monthlyBudget ?? '',
                        workSchedule: data.workSchedule || 'OFFICE',
                        readyForAdaptation: data.readyForAdaptation ?? true,
                        preferredSpecies: data.preferredSpecies || '',
                        preferredBreed: data.preferredBreed || '',
                    });
                }
            } catch {
                if (!cancelled) {
                    setLoadFailed(true);
                    setError('Unable to load your questionnaire. Please retry before editing.');
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        };
        loadQuestionnaire();
        return () => {
            cancelled = true;
        };
    }, [loadAttempt]);

    const validateForm = () => {
        const errors = {};

        if (!formData.livingArea || formData.livingArea === '') {
            errors.livingArea = 'Living area is required.';
        } else {
            const area = Number(formData.livingArea);
            if (!Number.isFinite(area) || area <= 0) {
                errors.livingArea = 'Living area must exceed 0 square meters.';
            } else if (area > 1000) {
                errors.livingArea = 'Living area cannot exceed 1000 square meters.';
            }
        }

        if (formData.monthlyBudget === '' || formData.monthlyBudget == null) {
            errors.monthlyBudget = 'Monthly budget is required.';
        } else {
            const budget = Number(formData.monthlyBudget);
            if (!Number.isFinite(budget) || budget < 0) {
                errors.monthlyBudget = 'Budget cannot be negative.';
            } else if (budget > 1000000) {
                errors.monthlyBudget = 'Budget cannot exceed RUB 1,000,000.';
            }
        }

        if (
            formData.hasChildren &&
            (formData.childrenAgeMin === '' || formData.childrenAgeMin == null)
        ) {
            errors.childrenAgeMin = 'Enter the age of the youngest child.';
        } else if (formData.hasChildren && parseInt(formData.childrenAgeMin, 10) < 0) {
            errors.childrenAgeMin = 'Child age cannot be negative.';
        }

        setValidationErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!validateForm()) {
            setError('Complete all required fields.');
            return;
        }

        setSaving(true);
        setError('');
        setSuccess('');

        try {
            const dataToSend = {
                ...formData,
                livingArea: formData.livingArea ? parseInt(formData.livingArea, 10) : null,
                childrenAgeMin:
                    formData.childrenAgeMin === '' ? null : Number(formData.childrenAgeMin),
                monthlyBudget:
                    formData.monthlyBudget === '' ? null : Number(formData.monthlyBudget),
            };

            await saveQuestionnaire(dataToSend);
            setSuccess('Questionnaire saved.');

            setTimeout(() => {
                if (returnUrl) {
                    navigate(returnUrl);
                } else {
                    navigate('/profile');
                }
            }, 1500);
        } catch (err) {
            console.error('Failed to save questionnaire:', err);
            setError('The questionnaire could not be saved.');
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

    if (loadFailed)
        return (
            <div role="alert" className="p-8 text-center">
                <p>{error}</p>
                <button onClick={() => setLoadAttempt((n) => n + 1)}>Retry</button>
            </div>
        );

    return (
        <div className="max-w-2xl mx-auto">
            <h1 className="text-2xl font-bold text-gray-900 mb-6">Compatibility questionnaire</h1>
            <p className="text-gray-600 mb-6">
                Complete this questionnaire to receive pet recommendations and compatibility scores.
                Fields marked <span className="text-red-500">*</span> are required.
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
                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Living conditions</h2>
                    <div className="space-y-4">
                        <div>
                            <label
                                htmlFor="questionnaire-livingSpace"
                                className="block text-sm font-medium text-gray-700 mb-1"
                            >
                                Housing type <span className="text-red-500">*</span>
                            </label>
                            <select
                                id="questionnaire-livingSpace"
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
                            <label
                                htmlFor="questionnaire-livingArea"
                                className="block text-sm font-medium text-gray-700 mb-1"
                            >
                                Living area (square meters) <span className="text-red-500">*</span>
                            </label>
                            <input
                                id="questionnaire-livingArea"
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
                            <span className="text-sm text-gray-700">I have a private yard</span>
                        </label>
                    </div>
                </section>

                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Family and health</h2>
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
                            <span className="text-sm text-gray-700">
                                There are children in my household
                            </span>
                        </label>

                        {formData.hasChildren && (
                            <div>
                                <label
                                    htmlFor="questionnaire-childrenAgeMin"
                                    className="block text-sm font-medium text-gray-700 mb-1"
                                >
                                    Age of the youngest child
                                </label>
                                <input
                                    id="questionnaire-childrenAgeMin"
                                    type="number"
                                    value={formData.childrenAgeMin}
                                    onChange={(e) =>
                                        setFormData({ ...formData, childrenAgeMin: e.target.value })
                                    }
                                    className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500
                                        ${validationErrors.childrenAgeMin ? 'border-red-500' : 'border-gray-300'}`}
                                    placeholder="For example: 3"
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
                            <span className="text-sm text-gray-700">I have animal allergies</span>
                        </label>

                        {formData.hasAllergies && (
                            <div>
                                <label
                                    htmlFor="questionnaire-allergyDetails"
                                    className="block text-sm font-medium text-gray-700 mb-1"
                                >
                                    Allergy details
                                </label>
                                <textarea
                                    id="questionnaire-allergyDetails"
                                    rows="2"
                                    value={formData.allergyDetails}
                                    onChange={(e) =>
                                        setFormData({ ...formData, allergyDetails: e.target.value })
                                    }
                                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                                    placeholder="Describe the allergen and severity of the reaction."
                                />
                            </div>
                        )}
                    </div>
                </section>

                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">
                        Experience and budget
                    </h2>
                    <div className="space-y-4">
                        <div>
                            <label
                                htmlFor="questionnaire-petExperience"
                                className="block text-sm font-medium text-gray-700 mb-1"
                            >
                                Pet ownership experience
                            </label>
                            <select
                                id="questionnaire-petExperience"
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
                            <label
                                htmlFor="questionnaire-activityLevel"
                                className="block text-sm font-medium text-gray-700 mb-1"
                            >
                                Activity level
                            </label>
                            <select
                                id="questionnaire-activityLevel"
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
                            <label
                                htmlFor="questionnaire-monthlyBudget"
                                className="block text-sm font-medium text-gray-700 mb-1"
                            >
                                Monthly pet budget (RUB) <span className="text-red-500">*</span>
                            </label>
                            <input
                                id="questionnaire-monthlyBudget"
                                type="number"
                                value={formData.monthlyBudget}
                                onChange={(e) =>
                                    setFormData({ ...formData, monthlyBudget: e.target.value })
                                }
                                className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500
                                    ${validationErrors.monthlyBudget ? 'border-red-500' : 'border-gray-300'}`}
                                placeholder="For example: 5000"
                            />
                            {validationErrors.monthlyBudget && (
                                <p className="mt-1 text-sm text-red-600">
                                    {validationErrors.monthlyBudget}
                                </p>
                            )}
                        </div>

                        <div>
                            <label
                                htmlFor="questionnaire-workSchedule"
                                className="block text-sm font-medium text-gray-700 mb-1"
                            >
                                Work schedule
                            </label>
                            <select
                                id="questionnaire-workSchedule"
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

                <section>
                    <h2 className="text-lg font-semibold text-gray-900 mb-4">Preferences</h2>
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
                                Ready for the adaptation period (first 14 days with support)
                            </span>
                        </label>

                        <div>
                            <label
                                htmlFor="questionnaire-preferredSpecies"
                                className="block text-sm font-medium text-gray-700 mb-1"
                            >
                                Preferred species
                            </label>
                            <select
                                id="questionnaire-preferredSpecies"
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
                            <label
                                htmlFor="questionnaire-preferredBreed"
                                className="block text-sm font-medium text-gray-700 mb-1"
                            >
                                Preferred breed (optional)
                            </label>
                            <input
                                id="questionnaire-preferredBreed"
                                type="text"
                                value={formData.preferredBreed}
                                onChange={(e) =>
                                    setFormData({ ...formData, preferredBreed: e.target.value })
                                }
                                className="w-full px-3 py-2 border border-gray-300 rounded-md"
                                placeholder="For example: Maine Coon, Labrador"
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
                        Cancel
                    </button>
                    <button
                        type="submit"
                        disabled={saving}
                        className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
                    >
                        {saving ? 'Saving...' : 'Save questionnaire'}
                    </button>
                </div>
            </form>
        </div>
    );
}
