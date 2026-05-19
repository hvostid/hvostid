import { Link } from 'react-router-dom';
import TrustBadge from './TrustBadge';
import { useState } from 'react';

export default function ListingCard({ listing }) {
    const { id, title, species, breed, age, price, city, photoUrl, trustScore } = listing;

    const [imgError, setImgError] = useState(false);

    // Финальный источник картинки
    const imageSrc = photoUrl && !imgError ? photoUrl : '/def.png';
    const isPlaceholder = !photoUrl || imgError;

    return (
        <Link
            to={`/listings/${id}`}
            className="flex flex-col rounded-lg overflow-hidden border border-gray-200 bg-white shadow-sm hover:shadow-md transition-shadow"
        >
            <div className="aspect-[4/3] bg-gray-200 relative">
                <img
                    src={imageSrc}
                    alt={title}
                    className="w-full h-full object-cover"
                    loading="lazy"
                    onError={() => {
                        if (!imgError) {
                            setImgError(true);
                        }
                    }}
                />

                {/* Осветляющий слой ТОЛЬКО для картинки-заглушки */}
                {isPlaceholder && (
                    <div className="absolute inset-0 bg-white/80 transition-all duration-300 hover:bg-white/50"></div>
                )}
                {!isPlaceholder && (
                    <div className="absolute inset-0 bg-white/50 transition-all duration-300 hover:bg-white/30"></div>
                )}

                {/* Аккуратная надпись в углу, если нет фото */}
                {isPlaceholder && (
                    <div className="absolute bottom-2 right-2 bg-black/30 backdrop-blur-sm px-2 py-0.5 rounded-md">
                        <span className="text-white text-xs font-medium">No photo</span>
                    </div>
                )}

                {trustScore !== undefined && trustScore !== null && (
                    <div className="absolute top-2 right-2">
                        <TrustBadge score={trustScore} />
                    </div>
                )}
            </div>

            <div className="flex-1 flex flex-col p-3 gap-1">
                <h3 className="font-semibold text-gray-900 line-clamp-1">{title}</h3>
                <p className="text-sm text-gray-600 line-clamp-1">
                    {[species, breed].filter(Boolean).join(' / ')}
                </p>
                <div className="flex items-center justify-between mt-auto pt-2 text-sm">
                    <span className="text-gray-500">
                        {age !== null && age !== undefined ? `${age} mo` : '—'}
                        {city ? ` · ${city}` : ''}
                    </span>
                    <span className="font-semibold text-indigo-600">
                        {price !== null && price !== undefined
                            ? `${Number(price).toLocaleString('ru-RU')} ₽`
                            : '—'}
                    </span>
                </div>
            </div>
        </Link>
    );
}
