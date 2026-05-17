import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Input from '../components/Input';

export default function LoginPage() {
    // Хуки React
    const navigate = useNavigate();
    const location = useLocation();
    const { login } = useAuth();

    // Состояния для полей формы
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');

    // Состояния для ошибок
    const [emailError, setEmailError] = useState('');
    const [passwordError, setPasswordError] = useState('');
    const [generalError, setGeneralError] = useState('');

    // Состояние загрузки (блокирует кнопку)
    const [loading, setLoading] = useState(false);

    // Получаем URL для редиректа после логина
    // Например: /login?redirect=/profile → from = '/profile'
    const params = new URLSearchParams(location.search);
    const from = params.get('redirect') || '/';

    // Валидация формы перед отправкой
    const validateForm = () => {
        let isValid = true;

        // Проверка email
        if (!email.trim()) {
            setEmailError('Email обязателен');
            isValid = false;
        } else if (!email.includes('@') || !email.includes('.')) {
            setEmailError('Введите корректный email');
            isValid = false;
        } else {
            setEmailError('');
        }

        // Проверка пароля
        if (!password) {
            setPasswordError('Пароль обязателен');
            isValid = false;
        } else {
            setPasswordError('');
        }

        return isValid;
    };

    // Обработчик отправки формы
    const handleSubmit = async (e) => {
        e.preventDefault(); // Не перезагружать страницу

        setGeneralError(''); // Очищаем общую ошибку

        if (!validateForm()) {
            return; // Если валидация не прошла, не отправляем
        }

        setLoading(true); // Блокируем кнопку

        try {
            await login(email, password);
            // Если успешно — AuthContext сам сохранит токены
            navigate(from, { replace: true }); // Редирект на страницу, откуда пришли
        } catch (err) {
            // Обрабатываем ошибки от сервера
            console.error('Login error:', err);

            if (err.response?.status === 401) {
                setGeneralError('Неверный email или пароль');
            } else if (err.response?.status === 404) {
                setGeneralError('Пользователь не найден');
            } else {
                setGeneralError('Ошибка сервера. Попробуйте позже.');
            }
        } finally {
            setLoading(false); // Разблокируем кнопку
        }
    };

    return (
        <div className="min-h-[calc(100vh-56px)] flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-md w-full space-y-8">
                {/* Заголовок */}
                <div>
                    <h2 className="mt-6 text-center text-3xl font-extrabold text-gray-900">
                        Вход в аккаунт
                    </h2>
                    <p className="mt-2 text-center text-sm text-gray-600">
                        Или{' '}
                        <Link
                            to="/register"
                            className="font-medium text-indigo-600 hover:text-indigo-500"
                        >
                            зарегистрируйтесь
                        </Link>
                    </p>
                </div>

                {/* Форма */}
                <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
                    {/* Общая ошибка (неверный пароль и т.д.) */}
                    {generalError && (
                        <div className="rounded-md bg-red-50 p-4">
                            <div className="text-sm text-red-700">{generalError}</div>
                        </div>
                    )}

                    {/* Поля ввода */}
                    <div className="space-y-4">
                        <Input
                            id="email"
                            type="email"
                            label="Email"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            error={emailError}
                            required
                            autoComplete="email"
                            placeholder="ivan@example.com"
                        />

                        <Input
                            id="password"
                            type="password"
                            label="Пароль"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            error={passwordError}
                            required
                            autoComplete="current-password"
                            placeholder="••••••••"
                        />
                    </div>

                    {/* Кнопка отправки */}
                    <div>
                        <button
                            type="submit"
                            disabled={loading}
                            className={`
                w-full flex justify-center py-2 px-4 border border-transparent 
                rounded-md shadow-sm text-sm font-medium text-white 
                bg-indigo-600 hover:bg-indigo-700 focus:outline-none 
                focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500
                ${loading ? 'opacity-50 cursor-not-allowed' : ''}
              `}
                        >
                            {loading ? 'Вход...' : 'Войти'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
