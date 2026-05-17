package ru.hvostid.matching.domain;

public record FactorScore(CompatibilityFactor factor, int score, String comment) {
    public int maxScore() {
        return factor.maxScore();
    }

    public String name() {
        return factor.apiName();
    }
}
