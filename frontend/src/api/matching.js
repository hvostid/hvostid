import api from './client';

export const getQuestionnaire = async () => {
    try {
        const response = await api.get('/match/questionnaire');
        return response.data;
    } catch (error) {
        if (error.response?.status === 404) {
            return null; // Анкета не заполнена
        }
        throw error;
    }
};

export const saveQuestionnaire = async (questionnaireData) => {
    const response = await api.post('/match/questionnaire', questionnaireData);
    return response.data;
};

export const getMatchScore = async (listingId) => {
    const response = await api.post('/match/score', { listingId });
    return response.data;
};

export const getRecommendations = async (page = 0, size = 10, minScore = 40) => {
    const response = await api.get(
        `/match/recommendations?page=${page}&size=${size}&minScore=${minScore}`
    );
    return response.data;
};
