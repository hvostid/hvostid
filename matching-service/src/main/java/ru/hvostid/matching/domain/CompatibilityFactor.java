package ru.hvostid.matching.domain;

public enum CompatibilityFactor {
    LIVING_SPACE("living_space", 20),
    CHILDREN("children", 15),
    ALLERGIES("allergies", 15),
    EXPERIENCE("experience", 15),
    YARD("yard", 10),
    ACTIVITY("activity", 10),
    BUDGET("budget", 10),
    WORK_SCHEDULE("work_schedule", 5);

    private final String apiName;
    private final int maxScore;

    CompatibilityFactor(String apiName, int maxScore) {
        this.apiName = apiName;
        this.maxScore = maxScore;
    }

    public String apiName() {
        return apiName;
    }

    public int maxScore() {
        return maxScore;
    }
}
