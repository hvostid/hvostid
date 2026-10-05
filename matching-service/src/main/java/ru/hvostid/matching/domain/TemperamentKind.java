package ru.hvostid.matching.domain;

import java.util.Arrays;
import java.util.Locale;

/** Shared vocabulary for structured codes and legacy English/Russian descriptions. */
public enum TemperamentKind {
    CHALLENGING,
    NERVOUS,
    FRIENDLY,
    ACTIVE,
    UNKNOWN;

    public static TemperamentKind classify(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String[] words = value.toLowerCase(Locale.ROOT).split("[^\\p{L}]+");
        if (matches(
                words,
                "challenging",
                "aggress",
                "bite",
                "dominant",
                "guarding",
                "\u0430\u0433\u0440\u0435\u0441\u0441",
                "\u043a\u0443\u0441\u0430",
                "\u0434\u043e\u043c\u0438\u043d\u0430\u043d\u0442")) {
            return CHALLENGING;
        }
        if (matches(
                words,
                "nervous",
                "shy",
                "fearful",
                "anxious",
                "\u043d\u0435\u0440\u0432\u043d",
                "\u043f\u0443\u0433\u043b\u0438\u0432",
                "\u0440\u043e\u0431\u043a",
                "\u0442\u0440\u0435\u0432\u043e\u0436")) {
            return NERVOUS;
        }
        if (matches(
                words,
                "gentle",
                "patient",
                "calm",
                "friendly",
                "tolerant",
                "\u0441\u043f\u043e\u043a\u043e\u0439\u043d",
                "\u0434\u0440\u0443\u0436\u0435\u043b\u044e\u0431",
                "\u043b\u0430\u0441\u043a\u043e\u0432",
                "\u0442\u0435\u0440\u043f\u0435\u043b\u0438\u0432")) {
            return FRIENDLY;
        }
        if (matches(
                words,
                "active",
                "energetic",
                "hyper",
                "\u0430\u043a\u0442\u0438\u0432\u043d",
                "\u044d\u043d\u0435\u0440\u0433\u0438\u0447\u043d",
                "\u0438\u0433\u0440\u0438\u0432")) {
            return ACTIVE;
        }
        return UNKNOWN;
    }

    private static boolean matches(String[] words, String... stems) {
        return Arrays.stream(words).anyMatch(word -> Arrays.stream(stems).anyMatch(word::startsWith));
    }
}
