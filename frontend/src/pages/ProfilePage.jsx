// pages/ProfilePage.jsx
import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import LoadingSpinner from '../components/LoadingSpinner';

export default function ProfilePage() {
    const { user, hasRole, addRole } = useAuth();
    const [addingRole, setAddingRole] = useState(false);
    const [roleError, setRoleError] = useState('');
    const [roleSuccess, setRoleSuccess] = useState('');

    const handleAddSellerRole = async () => {
        setAddingRole(true);
        setRoleError('');
        setRoleSuccess('');

        try {
            await addRole('SELLER');
            setRoleSuccess('Поздравляем! Теперь вы продавец.');
        } catch (err) {
            console.error('Failed to add SELLER role:', err);
            setRoleError('Не удалось получить роль продавца. Попробуйте позже.');
        } finally {
            setAddingRole(false);
        }
    };

    if (!user) {
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

            <div className="bg-white rounded-lg shadow p-6 space-y-6">
                {/* Информация о пользователе */}
                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-500">Имя</label>
                        <p className="text-lg text-gray-900">{user.name || '—'}</p>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-500">Email</label>
                        <p className="text-lg text-gray-900">{user.email}</p>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-500">Роли</label>
                        <div className="flex flex-wrap gap-2 mt-1">
                            {user.roles?.map((role) => (
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
                </div>

                {/* Кнопка получения роли SELLER */}
                {!isSeller && (
                    <div className="border-t pt-6">
                        <h2 className="text-lg font-semibold text-gray-900 mb-3">
                            Стать продавцом
                        </h2>
                        <p className="text-sm text-gray-600 mb-4">
                            Получите роль продавца, чтобы создавать объявления о продаже питомцев.
                        </p>

                        {/* Сообщение об успехе */}
                        {roleSuccess && (
                            <div className="mb-4 bg-green-50 border border-green-200 text-green-700 px-4 py-3 rounded-md">
                                {roleSuccess}
                            </div>
                        )}

                        {/* Сообщение об ошибке */}
                        {roleError && (
                            <div className="mb-4 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md">
                                {roleError}
                            </div>
                        )}

                        <button
                            onClick={handleAddSellerRole}
                            disabled={addingRole}
                            className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 disabled:opacity-50"
                        >
                            {addingRole ? 'Отправка...' : 'Стать продавцом'}
                        </button>
                    </div>
                )}

                {/* Информация для продавца */}
                {isSeller && (
                    <div className="border-t pt-6 bg-green-50 p-4 rounded-md">
                        <p className="text-green-800">
                            Вы уже являетесь продавцом. Перейдите в раздел «My listings» для
                            управления объявлениями.
                        </p>
                    </div>
                )}
            </div>
        </div>
    );
}
