// api/listings.js
import api from './client';
import { collectPages } from './pages';

export const getListingDraft = async (formId, signal) => {
    const response = await api.get(`/listings/drafts/${formId}`, { signal });
    return response.status === 204 ? null : response.data;
};
export const saveListingDraft = async (formId, draft) =>
    (await api.put(`/listings/drafts/${formId}`, draft)).data;
export const deleteListingDraft = async (formId, version) =>
    api.delete(`/listings/drafts/${formId}?version=${version}`);

// Get my listings (with optional status filter)
export const getMyListings = async (status = null, page = 0, size = 100, signal) => {
    let url = `/listings/my?page=${page}&size=${size}`;
    if (status && status !== 'ALL') {
        url += `&status=${status}`;
    }
    const response = await api.get(url, { signal });
    return response.data;
};

// Get listing by ID
export const getListingById = async (id) => {
    const response = await api.get(`/listings/${id}`);
    return response.data;
};

// Create new listing
export const createListing = async (listingData) => {
    const response = await api.post('/listings', listingData);
    return response.data;
};

// Update listing
export const updateListing = async (id, listingData) => {
    const response = await api.put(`/listings/${id}`, listingData);
    return response.data;
};

// Change listing status
export const changeListingStatus = async (id, status, comment = null) => {
    const response = await api.patch(`/listings/${id}/status`, { status, comment });
    return response.data;
};

// Delete listing (hard delete, owner only).
// Backend rejects deletion of listings under moderation with 409.
export const deleteListing = async (id) => {
    await api.delete(`/listings/${id}`);
};

export const getOwnedListings = (status, signal) =>
    collectPages((page) => getMyListings(status, page, 100, signal), signal);
