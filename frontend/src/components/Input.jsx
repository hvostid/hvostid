// это переиспользуемый компонент для всех полей ввода

export default function Input({
    id, // уникальный идентификатор (для label)
    type, // email, password, text
    label, // текст подписи
    value, // текущее значение (из useState)
    onChange, // функция для изменения значения
    error, // текст ошибки (если есть)
    required, // обязательно ли поле
    autoComplete, // автозаполнение браузера
    placeholder, // подсказка внутри поля
}) {
    return (
        <div className="mb-4">
            {/* Подпись над полем */}
            <label htmlFor={id} className="block text-sm font-medium text-gray-700 mb-1">
                {label}
                {required && <span className="text-red-500 ml-1">*</span>}
            </label>

            {/* Поле ввода */}
            <input
                id={id}
                type={type}
                value={value}
                onChange={onChange}
                required={required}
                autoComplete={autoComplete}
                placeholder={placeholder}
                className={`
          w-full px-3 py-2 border rounded-md shadow-sm
          focus:outline-none focus:ring-indigo-500 focus:border-indigo-500
          ${error ? 'border-red-500' : 'border-gray-300'}
        `}
            />

            {/* Сообщение об ошибке */}
            {error && <p className="mt-1 text-sm text-red-600">{error}</p>}
        </div>
    );
}
