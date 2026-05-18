import { Link } from 'react-router-dom';
import TrustBadge from './TrustBadge';

/**
 * Single listing card for the catalog grid.
 *
 * The current API does not include a photo URL or a denormalized trust score
 * on the listing payload (those live in passport-service / future enrichment
 * endpoints). The card renders a neutral placeholder when no photo is
 * available; `TrustBadge` renders nothing when `trustScore` is unset, so we
 * stay forward-compatible without showing a misleading "0".
 */
export default function ListingCard({ listing }) {
    const { id, title, species, breed, age, price, city, photoUrl, trustScore } = listing;

    return (
        <Link
            to={`/listings/${id}`}
            className="flex flex-col rounded-lg overflow-hidden border border-gray-200 bg-white shadow-sm hover:shadow-md transition-shadow"
        >
            <div className="aspect-[4/3] bg-gray-100 relative">
                {photoUrl ? (
                    <img
                        src={photoUrl}
                        alt={title}
                        className="w-full h-full object-cover"
                        loading="lazy"
                    />
                ) : (
                    <div className="w-full h-full flex items-center justify-center text-gray-400 text-sm">
                        No photo
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
