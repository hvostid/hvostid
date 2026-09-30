import { CATALOG_SPECIES } from '../constants/petSpecies';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import api from '../api/client';
import ListingCard from '../components/ListingCard';
import ListingCardSkeleton from '../components/ListingCardSkeleton';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;
const SKELETON_KEYS = ['skel-0', 'skel-1', 'skel-2', 'skel-3', 'skel-4', 'skel-5'];

const SORT_OPTIONS = [
    { value: 'created_desc', label: 'Newest first' },
    { value: 'price_asc', label: 'Price: low to high' },
    { value: 'price_desc', label: 'Price: high to low' },
];

const SPECIES_OPTIONS = CATALOG_SPECIES;

// Mirror filter state in the URL so links are shareable. Empty values are
// stripped so the query string stays clean (`?` shows only what the user set).
function paramsFromFilters(filters, page) {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
            params.set(key, String(value));
        }
    });
    if (page > 0) params.set('page', String(page));
    return params;
}

function readInitialFilters(searchParams) {
    return {
        q: searchParams.get('q') ?? '',
        species: searchParams.get('species') ?? '',
        breed: searchParams.get('breed') ?? '',
        ageMin: searchParams.get('ageMin') ?? '',
        ageMax: searchParams.get('ageMax') ?? '',
        priceMin: searchParams.get('priceMin') ?? '',
        priceMax: searchParams.get('priceMax') ?? '',
        city: searchParams.get('city') ?? '',
        sort: searchParams.get('sort') ?? 'created_desc',
    };
}

export default function CatalogPage() {
    const [searchParams, setSearchParams] = useSearchParams();

    // `filters` is the source of truth for what gets sent to the API and
    // mirrored in the URL. `searchInput` is the uncontrolled value of the
    // search box: debouncing pushes it into `filters.q` after 300 ms so
    // every keystroke does not fire a request.
    const filters = useMemo(() => readInitialFilters(searchParams), [searchParams]);
    const parsedPage = Number(searchParams.get('page') || 0);
    const page = Number.isSafeInteger(parsedPage) && parsedPage >= 0 ? parsedPage : 0;
    const [searchInput, setSearchInput] = useState(filters.q);
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        // Browser navigation owns the query; typing remains local until the debounce completes.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setSearchInput(filters.q);
    }, [filters.q]);

    useEffect(() => {
        if (searchInput === filters.q) return undefined;
        const timer = setTimeout(() => {
            setSearchParams((previous) =>
                paramsFromFilters({ ...readInitialFilters(previous), q: searchInput }, 0)
            );
        }, SEARCH_DEBOUNCE_MS);
        return () => clearTimeout(timer);
    }, [searchInput, filters.q, setSearchParams]);

    useEffect(() => {
        const controller = new AbortController();
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setLoading(true);
        setError(null);
        const params = paramsFromFilters(filters, page);
        params.set('size', String(PAGE_SIZE));
        api.get(`/listings?${params}`, { signal: controller.signal })
            .then((res) => {
                if (!controller.signal.aborted) setData(res.data);
            })
            .catch((err) => {
                if (controller.signal.aborted) return;
                const detail = err.response?.data?.detail;
                setError(typeof detail === 'string' ? detail : 'Failed to load listings');
                setData(null);
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [filters, page]);

    const setPage = (next) => setSearchParams(paramsFromFilters(filters, next));
    const updateFilter = useCallback(
        (key, value) => {
            setSearchParams((previous) =>
                paramsFromFilters({ ...readInitialFilters(previous), [key]: value }, 0)
            );
        },
        [setSearchParams]
    );
    const resetFilters = () => {
        setSearchInput('');
        setSearchParams({});
    };

    const listings = data?.content ?? [];
    const totalPages = data?.totalPages ?? 0;
    const totalElements = data?.totalElements ?? 0;

    const hasActiveFilters = useMemo(
        () =>
            Boolean(
                filters.q ||
                filters.species ||
                filters.breed ||
                filters.ageMin ||
                filters.ageMax ||
                filters.priceMin ||
                filters.priceMax ||
                filters.city
            ),
        [filters]
    );

    return (
        <div className="grid grid-cols-1 md:grid-cols-[260px_1fr] gap-6">
            <aside className="space-y-4">
                <h2 className="text-lg font-semibold text-gray-900">Filters</h2>

                <FilterField label="Species">
                    <select
                        value={filters.species}
                        onChange={(e) => updateFilter('species', e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                    >
                        {SPECIES_OPTIONS.map((opt) => (
                            <option key={opt.value} value={opt.value}>
                                {opt.label}
                            </option>
                        ))}
                    </select>
                </FilterField>

                <FilterField label="Breed">
                    <input
                        type="text"
                        value={filters.breed}
                        onChange={(e) => updateFilter('breed', e.target.value)}
                        placeholder="e.g. Husky"
                        className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                    />
                </FilterField>

                <FilterField label="Age (months)">
                    <div className="grid grid-cols-2 gap-2">
                        <NumericInput
                            value={filters.ageMin}
                            placeholder="min"
                            onChange={(v) => updateFilter('ageMin', v)}
                        />
                        <NumericInput
                            value={filters.ageMax}
                            placeholder="max"
                            onChange={(v) => updateFilter('ageMax', v)}
                        />
                    </div>
                </FilterField>

                <FilterField label="Price (₽)">
                    <div className="grid grid-cols-2 gap-2">
                        <NumericInput
                            value={filters.priceMin}
                            placeholder="min"
                            onChange={(v) => updateFilter('priceMin', v)}
                        />
                        <NumericInput
                            value={filters.priceMax}
                            placeholder="max"
                            onChange={(v) => updateFilter('priceMax', v)}
                        />
                    </div>
                </FilterField>

                <FilterField label="City">
                    <input
                        type="text"
                        value={filters.city}
                        onChange={(e) => updateFilter('city', e.target.value)}
                        placeholder="e.g. Moscow"
                        className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
                    />
                </FilterField>

                {hasActiveFilters && (
                    <button
                        type="button"
                        onClick={resetFilters}
                        className="text-sm text-indigo-600 hover:underline"
                    >
                        Reset filters
                    </button>
                )}
            </aside>

            <section>
                <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between mb-4">
                    <div className="flex-1 max-w-xl">
                        <input
                            type="search"
                            value={searchInput}
                            onChange={(e) => setSearchInput(e.target.value)}
                            placeholder="Search listings…"
                            aria-label="Search listings"
                            className="w-full px-4 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-indigo-200"
                        />
                    </div>
                    <select
                        value={filters.sort}
                        onChange={(e) => updateFilter('sort', e.target.value)}
                        className="px-3 py-2 border border-gray-300 rounded-md text-sm"
                        aria-label="Sort listings"
                    >
                        {SORT_OPTIONS.map((opt) => (
                            <option key={opt.value} value={opt.value}>
                                {opt.label}
                            </option>
                        ))}
                    </select>
                </div>

                {error && (
                    <div className="bg-red-50 border border-red-200 text-red-700 rounded-md px-3 py-2 text-sm mb-4">
                        {error}
                    </div>
                )}

                {loading ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        {SKELETON_KEYS.map((key) => (
                            <ListingCardSkeleton key={key} />
                        ))}
                    </div>
                ) : listings.length === 0 ? (
                    <div className="rounded-md border border-dashed border-gray-300 bg-white p-10 text-center text-gray-500">
                        No listings match the current filters.
                    </div>
                ) : (
                    <>
                        <p className="text-xs text-gray-500 mb-3">
                            Showing {listings.length} of {totalElements} listings
                        </p>
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                            {listings.map((listing) => (
                                <div
                                    key={listing.id}
                                    className="relative group transition-transform duration-300 ease-out hover:scale-[1.02]"
                                >
                                    <ListingCard listing={listing} />

                                    <div
                                        className={`
                                            absolute inset-0 bg-black/10 rounded-lg flex items-center justify-center transition-all duration-300 ease-out opacity-0 group-hover:opacity-70 pointer-events-none
                                        `}
                                    />
                                </div>
                            ))}
                        </div>
                        <Pagination page={page} totalPages={totalPages} onChange={setPage} />
                    </>
                )}
            </section>
        </div>
    );
}

function FilterField({ label, children }) {
    return (
        <label className="block">
            <span className="block text-xs font-medium text-gray-700 mb-1">{label}</span>
            {children}
        </label>
    );
}

function NumericInput({ value, onChange, placeholder }) {
    return (
        <input
            type="number"
            min="0"
            value={value}
            placeholder={placeholder}
            onChange={(e) => onChange(e.target.value)}
            className="w-full px-2 py-2 border border-gray-300 rounded-md text-sm"
        />
    );
}
