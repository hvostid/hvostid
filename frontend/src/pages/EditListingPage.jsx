// pages/EditListingPage.jsx
import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getListingById, updateListing } from '../api/listings';
import ListingForm from '../components/ListingForm';
import LoadingSpinner from '../components/LoadingSpinner';
import StatusBadge from '../components/StatusBadge';

export default function EditListingPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const [listing, setListing] = useState(null);
    const [loading, setLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [saveError, setSaveError] = useState('');
    const [error, setError] = useState('');

    useEffect(() => {
        const loadListing = async () => {
            try {
                const data = await getListingById(id);
                setListing(data);
            } catch (error) {
                console.error('Failed to load listing:', error);
                setError('The listing could not be loaded.');
            } finally {
                setLoading(false);
            }
        };
        loadListing();
    }, [id]);

    const handleSubmit = async (formData) => {
        setIsSubmitting(true);
        setSaveError('');
        try {
            const dataToSend = {
                ...formData,
                age: formData.age === '' ? null : Number(formData.age),
                price: formData.price === '' ? null : Number(formData.price),
            };
            await updateListing(id, dataToSend);
            navigate('/my-listings');
        } catch (error) {
            console.error('Failed to update listing:', error);
            setSaveError('Unable to save the listing. Your changes are still here; please retry.');
        } finally {
            setIsSubmitting(false);
        }
    };

    if (loading) {
        return (
            <div className="flex justify-center py-12">
                <LoadingSpinner size="lg" />
            </div>
        );
    }

    if (error || !listing) {
        return (
            <div className="text-center py-12">
                <p className="text-red-600">{error || 'Listing not found'}</p>
                <button
                    onClick={() => navigate('/my-listings')}
                    className="mt-4 text-indigo-600 hover:text-indigo-700"
                >
                    Return to my listings
                </button>
            </div>
        );
    }

    const canEdit = ['DRAFT', 'PUBLISHED', 'REJECTED'].includes(listing.status);

    return (
        <div className="max-w-2xl mx-auto">
            {saveError && (
                <p role="alert" className="mb-4 text-red-700">
                    {saveError}
                </p>
            )}
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold text-gray-900">Edit listing</h1>
                <StatusBadge status={listing.status} />
            </div>

            {!canEdit && (
                <div className="mb-6 bg-yellow-50 border border-yellow-200 text-yellow-800 px-4 py-3 rounded-md">
                    A listing with status {listing.status} cannot be edited.
                    {listing.status === 'MODERATION' && ' It is awaiting moderator review.'}
                </div>
            )}

            <div className="bg-white rounded-lg shadow p-6">
                <ListingForm
                    initialData={listing}
                    onSubmit={handleSubmit}
                    isSubmitting={isSubmitting || !canEdit}
                    submitLabel="Save changes"
                />
            </div>
        </div>
    );
}
