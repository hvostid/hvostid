package ru.hvostid.matching.domain;

public enum DegradedReason {
    PASSPORT_ID_UNPARSEABLE("PASSPORT_ID_UNPARSEABLE"),
    PASSPORT_UNAVAILABLE("PASSPORT_UNAVAILABLE"),
    SPECIES_UNKNOWN("SPECIES_UNKNOWN");

    private final String code;

    DegradedReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
