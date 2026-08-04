package com.karmaos.provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Orders a fetched model list cheapest-first so that {@code resolveModel} picking
 * the first entry lands on the cheapest tier by default (flash/mini/lite/nano/free
 * before pro/opus/ultra/max).
 */
public final class ModelRanker {

    private static final List<String> CHEAP_TOKENS = List.of(
        "free", "flash", "mini", "lite", "nano", "small", "haiku", "instant",
        "compact", "versatile", "8b", "7b", "1.5", "2.5"
    );

    private static final List<String> EXPENSIVE_TOKENS = List.of(
        "pro", "opus", "ultra", "max", "large", "405b", "70b", "sonnet"
    );

    private ModelRanker() {}

    public static List<String> orderCheapestFirst(List<String> models) {
        if (models == null || models.isEmpty()) {
            return models == null ? List.of() : models;
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> deduped = new ArrayList<>();
        for (String m : models) {
            if (m != null && !m.isBlank() && seen.add(m.trim())) {
                deduped.add(m.trim());
            }
        }
        return deduped.stream()
            .sorted(Comparator.comparingInt(ModelRanker::score).thenComparingInt(deduped::indexOf))
            .toList();
    }

    private static int score(String model) {
        String m = model.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String t : CHEAP_TOKENS) {
            if (m.contains(t)) {
                score -= 10;
            }
        }
        for (String t : EXPENSIVE_TOKENS) {
            if (m.contains(t)) {
                score += 10;
            }
        }
        if (m.contains("free")) {
            score -= 20;
        }
        return score;
    }
}
