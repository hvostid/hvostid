package ru.hvostid.matching.domain;

import java.util.List;

public record CompatibilityResult(
        int score, CompatibilityLevel level, List<FactorScore> factors, boolean allergyCapApplied) {}
