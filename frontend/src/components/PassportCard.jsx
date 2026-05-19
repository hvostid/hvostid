// components/PassportCard.jsx
import { Link } from 'react-router-dom';

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

export default function PassportCard({ passport, listingId }) {
    // Если нет паспорта, не показываем карточку
    if (!passport) return null;

    return (
        <Link
            to={`/my-listings/${listingId}/passport`}
            className="block bg-white rounded-lg border border-gray-200 p-3 hover:shadow-md transition-all duration-200 hover:border-indigo-300 group"
        >
            <div className="flex items-center gap-3">
                {/* Иконка вида животного */}
                <div className="w-10 h-10 bg-gray-100 rounded-lg flex items-center justify-center overflow-hidden">
                    <img
                        src={getSpeciesIcon(passport.species)}
                        alt={passport.species}
                        className="w-8 h-8 object-contain"
                        onError={(e) => {
                            e.target.src = '/def.svg';
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
                </div>

                {/* Стрелка-индикатор */}
                <div className="text-gray-400 group-hover:text-indigo-500 transition-colors">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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
    );
}
