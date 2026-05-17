package ru.hvostid.matching.domain;

public record FactorScore(CompatibilityFactor factor, int score, String comment, boolean criticalConflict) {

    public static FactorScore of(CompatibilityFactor factor, int score, String comment) {
        return new FactorScore(factor, score, comment, false);
    }

    public static FactorScore critical(CompatibilityFactor factor, int score, String comment) {
        return new FactorScore(factor, score, comment, true);
    }

    public int maxScore() {
        return factor.maxScore();
    }

    public String name() {
        return factor.apiName();
    }
}
