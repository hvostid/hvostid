// api/passports.js
import api from './client';

// Create passport
export const createPassport = async (passportData) => {
    const response = await api.post('/passports', passportData);
    return response.data;
};

// Get passport by pet ID
export const getPassport = async (petId) => {
    const response = await api.get(`/passports/${petId}`);
    return response.data;
};

// Update passport
export const updatePassport = async (petId, passportData) => {
    const response = await api.put(`/passports/${petId}`, passportData);
    return response.data;
};

// Get trust score
export const getTrustScore = async (petId) => {
    const response = await api.get(`/passports/${petId}/trust`);
    return response.data;
};

// Upload document
export const uploadDocument = async (passportId, file, type) => {
    const formData = new FormData();
    formData.append('file', file);

    const response = await api.post(`/passports/${passportId}/docs?type=${type}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
};

// Delete document
export const deleteDocument = async (passportId, docId) => {
    await api.delete(`/passports/${passportId}/docs/${docId}`);
};

// Get document download URL (the API returns redirect, so we just open it)
export const getDocumentUrl = (passportId, docId) => {
    return `/api/v1/passports/${passportId}/docs/${docId}`;
};
