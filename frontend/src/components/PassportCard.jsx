// components/PassportCard.jsx
import { Link } from 'react-router-dom';
import { useState } from 'react';

// Функция для получения иконки вида животного
const getSpeciesIcon = (species) => {
    if (!species) return '/def.svg';
    const normalized = species.toLowerCase().trim();
    const availableSpecies = ['cat', 'dog', 'rabbit', 'bird', 'fish', 'hamster', 'rat'];
    if (availableSpecies.includes(normalized)) {
        return `/${normalized}.svg`;
    }
    return '/def.png';
};

export default function PassportCard({ passport, listingId, onDelete }) {
    const [failedPhotoUrl, setFailedPhotoUrl] = useState(null);

    // Если нет паспорта, не показываем карточку
    if (!passport) return null;

    // Ссылка для просмотра паспорта
    // Если есть listingId — идём через объявление
    // Если нет — идём напрямую на PassportFormPage через passportId
    const passportViewUrl = listingId
        ? `/my-listings/${listingId}/passport`
        : `/passports/${passport.id}/edit`;
    const showPhoto = Boolean(passport.photoUrl) && failedPhotoUrl !== passport.photoUrl;
    const imageSrc = showPhoto ? passport.photoUrl : getSpeciesIcon(passport.species);
    const imageClassName = showPhoto ? 'w-full h-full object-cover' : 'w-8 h-8 object-contain';

    const handleDeleteClick = (e) => {
        e.preventDefault(); // Предотвращаем переход по ссылке
        e.stopPropagation(); // Останавливаем всплытие события
        if (onDelete) {
            onDelete(passport.id, passport.name);
        }
    };

    return (
        <div className="relative group">
            <Link
                to={passportViewUrl}
                className="block bg-white rounded-lg border border-gray-200 p-3 hover:shadow-md transition-all duration-200 hover:border-indigo-300 group"
            >
                <div className="flex items-center gap-3">
                    {/* Фото паспорта или fallback-иконка вида животного */}
                    <div className="w-10 h-10 bg-gray-100 rounded-lg flex items-center justify-center overflow-hidden">
                        <img
                            src={imageSrc}
                            alt={passport.name || passport.species || 'Питомец'}
                            className={imageClassName}
                            onError={(e) => {
                                if (showPhoto) {
                                    setFailedPhotoUrl(passport.photoUrl);
                                } else {
                                    e.target.src = '/def.svg';
                                }
                            }}
                        />
                    </div>

                    {/* Информация о питомце */}
                    <div className="flex-1 min-w-0">
                        <h3 className="text-sm font-semibold text-gray-900 truncate group-hover:text-indigo-600 transition-colors">
                            {passport.name || 'Без имени'}
                        </h3>
                        <div className="flex items-center gap-2 mt-0.5">
                            <span className="text-xs text-gray-500">{passport.species || '—'}</span>
                            {passport.breed && (
                                <>
                                    <span className="text-xs text-gray-300">•</span>
                                    <span className="text-xs text-gray-500 truncate">
                                        {passport.breed}
                                    </span>
                                </>
                            )}
                        </div>
                        {passport.birthDate && (
                            <p className="text-xs text-gray-400 mt-1">
                                {new Date(passport.birthDate).toLocaleDateString('ru-RU')}
                            </p>
                        )}
                        {!listingId && (
                            <p className="text-xs text-amber-600 mt-1">Нет связанного объявления</p>
                        )}
                    </div>

                    {/* Стрелка-индикатор */}
                    <div className="text-gray-400 group-hover:text-indigo-500 transition-colors">
                        <svg
                            className="w-4 h-4"
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                        >
                            <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={2}
                                d="M9 5l7 7-7 7"
                            />
                        </svg>
                    </div>
                </div>
            </Link>

            {/* Кнопка удаления (появляется при наведении) */}
            <button
                onClick={handleDeleteClick}
                className="absolute bottom-2 left-2 bg-white/90 backdrop-blur-sm text-red-500 rounded-full w-5 h-5 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-200 hover:bg-red-500 hover:text-white shadow-md"
                title="Удалить паспорт"
            >
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                    />
                </svg>
            </button>
        </div>
    );
}
