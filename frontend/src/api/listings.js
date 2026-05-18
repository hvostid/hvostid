// api/listings.js
import api from './client';

// Get my listings (with optional status filter)
export const getMyListings = async (status = null, page = 0, size = 20) => {
    let url = `/listings?my=true&page=${page}&size=${size}`;
    if (status && status !== 'ALL') {
        url += `&status=${status}`;
    }
    const response = await api.get(url);
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
