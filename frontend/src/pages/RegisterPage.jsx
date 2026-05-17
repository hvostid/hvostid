// pages/RegisterPage.jsx
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Input from '../components/Input';

export default function RegisterPage() {
    const navigate = useNavigate();
    const { register } = useAuth();

    // Состояния для полей формы
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    //   const [role, setRole] = useState('BUYER'); // По умолчанию покупатель

    // Состояния для ошибок
    const [nameError, setNameError] = useState('');
    const [emailError, setEmailError] = useState('');
    const [passwordError, setPasswordError] = useState('');
    const [confirmPasswordError, setConfirmPasswordError] = useState('');
    const [generalError, setGeneralError] = useState('');

    const [loading, setLoading] = useState(false);

    // Валидация формы
    const validateForm = () => {
        let isValid = true;

        // Проверка имени
        if (!name.trim()) {
            setNameError('Имя обязательно');
            isValid = false;
        } else if (name.trim().length < 2) {
            setNameError('Имя должно быть не менее 2 символов');
            isValid = false;
        } else {
            setNameError('');
        }

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

        // Проверка пароля (минимум 8 символов)
        if (!password) {
            setPasswordError('Пароль обязателен');
            isValid = false;
        } else if (password.length < 8) {
            setPasswordError('Пароль должен быть не менее 8 символов');
            isValid = false;
        } else {
            setPasswordError('');
        }

        // Проверка подтверждения пароля
        if (password !== confirmPassword) {
            setConfirmPasswordError('Пароли не совпадают');
            isValid = false;
        } else {
            setConfirmPasswordError('');
        }

        return isValid;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setGeneralError('');

        if (!validateForm()) {
            return;
        }

        setLoading(true);

        try {
            // API пока поддерживает только регистрацию с ролью BUYER
            // Роль SELLER нужно будет запросить отдельно
            await register(email, password, name);

            // Успешная регистрация → редирект на логин с сообщением
            navigate('/login?registered=true');
        } catch (err) {
            console.error('Registration error:', err);

            if (err.response?.status === 409) {
                setGeneralError('Пользователь с таким email уже зарегистрирован');
            } else if (err.response?.status === 400) {
                setGeneralError('Неверные данные. Проверьте email и пароль.');
            } else {
                setGeneralError('Ошибка сервера. Попробуйте позже.');
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-[calc(100vh-56px)] flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-md w-full space-y-8">
                {/* Заголовок */}
                <div>
                    <h2 className="mt-6 text-center text-3xl font-extrabold text-gray-900">
                        Регистрация
                    </h2>
                    <p className="mt-2 text-center text-sm text-gray-600">
                        Уже есть аккаунт?{' '}
                        <Link
                            to="/login"
                            className="font-medium text-indigo-600 hover:text-indigo-500"
                        >
                            Войдите
                        </Link>
                    </p>
                </div>

                {/* Сообщение об успешной регистрации (при редиректе с /login?registered=true) */}
                {new URLSearchParams(window.location.search).get('registered') === 'true' && (
                    <div className="rounded-md bg-green-50 p-4">
                        <div className="text-sm text-green-700">
                            Регистрация прошла успешно! Теперь вы можете войти.
                        </div>
                    </div>
                )}

                {/* Форма */}
                <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
                    {/* Общая ошибка */}
                    {generalError && (
                        <div className="rounded-md bg-red-50 p-4">
                            <div className="text-sm text-red-700">{generalError}</div>
                        </div>
                    )}

                    {/* Поля ввода */}
                    <div className="space-y-4">
                        <Input
                            id="name"
                            type="text"
                            label="Имя"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            error={nameError}
                            required
                            autoComplete="name"
                            placeholder="Иван Петров"
                        />

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
                            autoComplete="new-password"
                            placeholder="Минимум 8 символов"
                        />

                        <Input
                            id="confirmPassword"
                            type="password"
                            label="Подтверждение пароля"
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                            error={confirmPasswordError}
                            required
                            autoComplete="new-password"
                            placeholder="Повторите пароль"
                        />
                    </div>

                    {/* Выбор роли (закомментировано, пока API не поддерживает) */}
                    {/* <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Я хочу
            </label>
            <div className="flex gap-4">
              <label className="flex items-center">
                <input
                  type="radio"
                  value="BUYER"
                  checked={role === 'BUYER'}
                  onChange={(e) => setRole(e.target.value)}
                  className="mr-2"
                />
                Найти питомца
              </label>
              <label className="flex items-center">
                <input
                  type="radio"
                  value="SELLER"
                  checked={role === 'SELLER'}
                  onChange={(e) => setRole(e.target.value)}
                  className="mr-2"
                />
                Продать питомца
              </label>
            </div>
          </div> */}

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
                            {loading ? 'Регистрация...' : 'Зарегистрироваться'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
