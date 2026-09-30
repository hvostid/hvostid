// api/passports.js
import api from './client';
import { canonicalPassportId } from '../utils/passportId';
import { collectPages } from './pages';

// Create passport
export const createPassport = async (passportData) => {
    const response = await api.post('/passports', passportData);
    return response.data;
};

// Get passport by pet ID
export const getPassport = async (petId, signal) => {
    const response = await api.get(`/passports/${canonicalPassportId(petId)}`, { signal });
    return response.data;
};

// Update passport
export const updatePassport = async (petId, passportData) => {
    const response = await api.put(`/passports/${canonicalPassportId(petId)}`, passportData);
    return response.data;
};

// Get trust score
export const getTrustScore = async (petId) => {
    const response = await api.get(`/passports/${canonicalPassportId(petId)}/trust`);
    return response.data;
};

// Upload document
export const uploadDocument = async (passportId, file, type) => {
    const formData = new FormData();
    formData.append('file', file);

    const response = await api.post(
        `/passports/${canonicalPassportId(passportId)}/docs?type=${type}`,
        formData,
        {
            headers: { 'Content-Type': 'multipart/form-data' },
        }
    );
    return response.data;
};

// Delete document
export const deleteDocument = async (passportId, docId) => {
    await api.delete(`/passports/${canonicalPassportId(passportId)}/docs/${docId}`);
};

export async function getPassportDocuments(passportId, signal) {
    const response = await api.get(`/passports/${canonicalPassportId(passportId)}/docs`, {
        signal,
    });
    return response.data;
}

/**
 * Issues a short-lived ticketed URL for a passport document. The returned
 * `url` works as a plain `<img src>` and is enforced by a single use ticket
 * on the gateway-public content endpoint -- no Bearer header is needed once
 * the URL is in hand.
 *
 * Shape: `{ url: string, expiresAt: string (ISO instant) }`.
 */
export async function issueDocumentTicket(passportId, docId, signal) {
    const response = await api.get(`/passports/${canonicalPassportId(passportId)}/docs/${docId}`, {
        signal,
    });
    return response.data;
}

/**
 * Public catalog cover URL for a passport. Renders directly via the frontend
 * nginx X-Accel-Redirect path -- no auth, no ticket round trip. Returns 404
 * when the passport has no PUBLISHED listing reference or no PHOTO uploaded;
 * call sites should provide an `onError` fallback (e.g. `/def.png`).
 */
export const passportCoverUrl = (passportId) =>
    `/api/v1/passports/${canonicalPassportId(passportId)}/cover`;

/**
 * List passports owned by the authenticated user.
 * Returns Spring Page shape: { content, totalElements, totalPages, ... }
 */
export async function getAllMyPassports(page = 0, size = 20, signal) {
    const response = await api.get(`/passports/my?page=${page}&size=${size}`, { signal });
    return response.data;
}

// Delete a passport. Backend rejects deletion when the passport
// is referenced by a PUBLISHED listing (409).
export const deletePassport = async (petId) => {
    await api.delete(`/passports/${canonicalPassportId(petId)}`);
};

export const getOwnedPassports = (signal) =>
    collectPages((page) => getAllMyPassports(page, 100, signal), signal);
