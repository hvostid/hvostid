/**
 * Placeholder card rendered while the catalog is fetching. Matches the
 * dimensions of ListingCard so the grid does not jump when results arrive.
 */
export default function ListingCardSkeleton() {
    return (
        <div className="flex flex-col rounded-lg overflow-hidden border border-gray-200 bg-white animate-pulse">
            <div className="aspect-[4/3] bg-gray-200" />
            <div className="p-3 flex flex-col gap-2">
                <div className="h-4 bg-gray-200 rounded w-3/4" />
                <div className="h-3 bg-gray-200 rounded w-1/2" />
                <div className="flex justify-between mt-2">
                    <div className="h-3 bg-gray-200 rounded w-1/3" />
                    <div className="h-3 bg-gray-200 rounded w-1/4" />
                </div>
            </div>
        </div>
    );
}
