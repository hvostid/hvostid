import api from './client';

export const getProfile = async () => {
    const response = await api.get('/profile/me');
    return response.data;
};

export const updateProfile = async (profileData) => {
    const response = await api.put('/profile/me', profileData);
    return response.data;
};
