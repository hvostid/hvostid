export default function Input({
    id,
    name,
    type,
    label,
    value,
    onChange,
    error,
    required,
    autoComplete,
    placeholder,
    ...props // Allows passing additional HTML attributes (disabled, onBlur, etc.)
}) {
    return (
        <div className="mb-4">
            <label htmlFor={id} className="block text-sm font-medium text-gray-700 mb-1">
                {label}
                {required && <span className="text-red-500 ml-1">*</span>}
            </label>

            <input
                id={id}
                name={name || id} // Default to id if name is not provided
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
                {...props} // Spread all additional props to the input element
            />

            {error && <p className="mt-1 text-sm text-red-600">{error}</p>}
        </div>
    );
}
