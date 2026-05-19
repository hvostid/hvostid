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

// api/passports.js
export async function getAllMyPassports() {
    // TODO: Заменить на реальный API эндпоинт, когда будет готов
    // const response = await api.get('/passports/my');
    // return response.data;

    // Заглушка для тестирования
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve({
                content: [
                    {
                        id: 1,
                        name: 'Барсик',
                        species: 'cat',
                        breed: 'Британская короткошерстная',
                        birthDate: '2022-03-15',
                        gender: 'MALE',
                        color: 'Серый',
                        temperament: 'Спокойный',
                        specialNeeds: '',
                        neutered: true,
                        microchipped: true,
                        vaccinations: [],
                    },
                    {
                        id: 2,
                        name: 'Рекс',
                        species: 'dog',
                        breed: 'Немецкая овчарка',
                        birthDate: '2021-07-22',
                        gender: 'MALE',
                        color: 'Черно-подпалый',
                        temperament: 'Активный',
                        specialNeeds: '',
                        neutered: false,
                        microchipped: true,
                        vaccinations: [],
                    },
                    {
                        id: 3,
                        name: 'Марта',
                        species: 'cat',
                        breed: 'Сиамская',
                        birthDate: '2023-01-10',
                        gender: 'FEMALE',
                        color: 'Светлая',
                        temperament: 'Ласковая',
                        specialNeeds: 'Требует специального корма',
                        neutered: true,
                        microchipped: false,
                        vaccinations: [],
                    },
                    {
                        id: 4,
                        name: 'Кеша',
                        species: 'bird',
                        breed: 'Волнистый попугайчик',
                        birthDate: '2023-05-01',
                        gender: 'MALE',
                        color: 'Желто-зеленый',
                        temperament: 'Разговорчивый',
                        specialNeeds: '',
                        neutered: false,
                        microchipped: false,
                        vaccinations: [],
                    },
                ],
            });
        }, 500); // Имитируем задержку сети
    });
}
