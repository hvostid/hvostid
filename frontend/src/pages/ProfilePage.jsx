// pages/ProfilePage.jsx
import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getProfile, updateProfile } from '../api/profile';
import LoadingSpinner from '../components/LoadingSpinner';
import Input from '../components/Input';

export default function ProfilePage() {
    const { user: hasRole, addRole } = useAuth();
    const [profile, setProfile] = useState(null);
    const [loading, setLoading] = useState(true);
    const [isEditing, setIsEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const [formData, setFormData] = useState({
        name: '',
        phone: '',
        city: '',
        bio: '',
    });

    useEffect(() => {
        const loadProfile = async () => {
            try {
                const data = await getProfile();
                setProfile(data);
                setFormData({
                    name: data.name || '',
                    phone: data.phone || '',
                    city: data.city || '',
                    bio: data.bio || '',
                });
            } catch (err) {
                console.error('Failed to load profile:', err);
                setError('Не удалось загрузить профиль');
            } finally {
                setLoading(false);
            }
        };
        loadProfile();
    }, []);

    const handleSave = async () => {
        setSaving(true);
        setError('');
        setSuccess('');

        try {
            const updated = await updateProfile(formData);
            setProfile(updated);
            setIsEditing(false);
            setSuccess('Профиль успешно обновлён');
            setTimeout(() => setSuccess(''), 3000);
        } catch (err) {
            console.error('Failed to update profile:', err);
            setError('Не удалось сохранить изменения');
        } finally {
            setSaving(false);
        }
    };

    const handleCancel = () => {
        setFormData({
            name: profile?.name || '',
            phone: profile?.phone || '',
            city: profile?.city || '',
            bio: profile?.bio || '',
        });
        setIsEditing(false);
        setError('');
    };

    const handleAddSellerRole = async () => {
        setSaving(true);
        setError('');
        try {
            await addRole('SELLER');
            setSuccess('Поздравляем! Теперь вы продавец. Страница обновится.');
            setTimeout(() => window.location.reload(), 2000);
        } catch (err) {
            console.error('Failed to get seller role:', err);
            setError('Не удалось получить роль продавца');
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

    const isSeller = hasRole('SELLER');

    return (
        <div className="max-w-2xl mx-auto">
            <h1 className="text-2xl font-bold text-gray-900 mb-6">Мой профиль</h1>

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

            <div className="bg-white rounded-lg shadow p-6 space-y-6">
                {/* Основная информация */}
                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-500">Email</label>
                        <p className="text-base text-gray-900">{profile?.email}</p>
                    </div>

                    {isEditing ? (
                        <>
                            <Input
                                id="name"
                                label="Имя"
                                value={formData.name}
                                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                            />
                            <Input
                                id="phone"
                                label="Телефон"
                                value={formData.phone}
                                onChange={(e) =>
                                    setFormData({ ...formData, phone: e.target.value })
                                }
                                placeholder="+7 999 000-00-00"
                            />
                            <Input
                                id="city"
                                label="Город"
                                value={formData.city}
                                onChange={(e) => setFormData({ ...formData, city: e.target.value })}
                                placeholder="Москва"
                            />
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    О себе
                                </label>
                                <textarea
                                    rows="3"
                                    value={formData.bio}
                                    onChange={(e) =>
                                        setFormData({ ...formData, bio: e.target.value })
                                    }
                                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                                    placeholder="Расскажите о себе..."
                                />
                            </div>
                        </>
                    ) : (
                        <>
                            <div>
                                <label className="block text-sm font-medium text-gray-500">
                                    Имя
                                </label>
                                <p className="text-base text-gray-900">{profile?.name || '—'}</p>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-500">
                                    Телефон
                                </label>
                                <p className="text-base text-gray-900">{profile?.phone || '—'}</p>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-500">
                                    Город
                                </label>
                                <p className="text-base text-gray-900">{profile?.city || '—'}</p>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-500">
                                    О себе
                                </label>
                                <p className="text-gray-900">{profile?.bio || '—'}</p>
                            </div>
                        </>
                    )}

                    <div>
                        <label className="block text-sm font-medium text-gray-500">Роли</label>
                        <div className="flex flex-wrap gap-2 mt-1">
                            {profile?.roles?.map((role) => (
                                <span
                                    key={role}
                                    className="px-2 py-1 text-xs font-medium rounded-full bg-indigo-100 text-indigo-800"
                                >
                                    {role === 'BUYER' && 'Покупатель'}
                                    {role === 'SELLER' && 'Продавец'}
                                    {role === 'MODERATOR' && 'Модератор'}
                                    {role === 'ADMIN' && 'Администратор'}
                                </span>
                            ))}
                        </div>
                    </div>

                    {profile?.rating !== undefined && (
                        <div>
                            <label className="block text-sm font-medium text-gray-500">
                                Рейтинг продавца
                            </label>
                            <div className="flex items-center gap-2">
                                <span className="text-xl font-bold text-indigo-600">
                                    {profile.rating}
                                </span>
                                <span className="text-sm text-gray-500">/ 5.0</span>
                                <div className="flex text-yellow-400">
                                    {'★'.repeat(Math.floor(profile.rating))}
                                    {'☆'.repeat(5 - Math.floor(profile.rating))}
                                </div>
                            </div>
                        </div>
                    )}
                </div>

                {/* Кнопки действий */}
                <div className="flex justify-between items-center pt-4 border-t">
                    <Link
                        to="/profile/questionnaire"
                        className="text-indigo-600 hover:text-indigo-800 text-sm font-medium"
                    >
                        Заполнить анкету совместимости →
                    </Link>

                    <div className="flex gap-3">
                        {isEditing ? (
                            <>
                                <button
                                    onClick={handleCancel}
                                    className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200"
                                >
                                    Отмена
                                </button>
                                <button
                                    onClick={handleSave}
                                    disabled={saving}
                                    className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
                                >
                                    {saving ? 'Сохранение...' : 'Сохранить'}
                                </button>
                            </>
                        ) : (
                            <button
                                onClick={() => setIsEditing(true)}
                                className="px-4 py-2 text-sm font-medium text-indigo-600 border border-indigo-600 rounded-md hover:bg-indigo-50"
                            >
                                Редактировать профиль
                            </button>
                        )}
                    </div>
                </div>

                {/* Стать продавцом */}
                {!isSeller && (
                    <div className="border-t pt-6">
                        <h2 className="text-lg font-semibold text-gray-900 mb-3">
                            Стать продавцом
                        </h2>
                        <p className="text-sm text-gray-600 mb-4">
                            Получите роль продавца, чтобы создавать объявления о продаже питомцев.
                        </p>
                        <button
                            onClick={handleAddSellerRole}
                            disabled={saving}
                            className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
                        >
                            Стать продавцом
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}
