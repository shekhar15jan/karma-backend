package com.karmaos.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

/**
 * Strict, reasoning-tolerant JSON extraction helpers.
 *
 * LLMs that emit chain-of-thought before their answer leave prose before and after
 * the real JSON payload. Jackson's default readTree() silently ignores trailing
 * garbage, which lets schema *examples* embedded in reasoning pass validation.
 * These helpers find the LAST balanced JSON object (the real deliverable normally
 * comes after the reasoning) and parse it with FAIL_ON_TRAILING_TOKENS so a parsed
 * object must consume the entire candidate.
 */
public final class StrictJson {

    private static final ObjectMapper STRICT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private StrictJson() {}

    /**
     * Returns the last top-level balanced JSON object found in {@code input},
     * or null when none exists. The candidate must parse as a single JSON object
     * with no trailing content (strict).
     */
    public static JsonNode extractLastObject(String input) {
        String candidate = extractLastObjectJson(input);
        if (candidate == null) return null;
        try {
            JsonNode node = STRICT_MAPPER.readTree(candidate);
            return (node != null && node.isObject()) ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the raw JSON text of the last strictly-parseable balanced object,
     * or null. Callers that build their own mapper can safely re-parse this text.
     */
    public static String extractLastObjectJson(String input) {
        if (input == null) return null;
        String candidate = findLastBalancedObject(input);
        if (candidate == null) return null;
        try {
            JsonNode node = STRICT_MAPPER.readTree(candidate);
            return (node != null && node.isObject()) ? candidate : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Scans {@code input} for a balanced {...} region, returning the substring of
     * the LAST one that starts at a top-level depth of zero. Reasoning prose can
     * contain unbalanced braces in prose, so this walks the whole string and keeps
     * the final region that is properly balanced.
     */
    private static String findLastBalancedObject(String input) {
        int lastStart = -1;
        int lastEnd = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        int pendingStart = -1;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                if (depth == 0) pendingStart = i;
                depth++;
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && pendingStart >= 0) {
                        lastStart = pendingStart;
                        lastEnd = i;
                        pendingStart = -1;
                    }
                }
            }
        }
        if (lastStart < 0 || lastEnd <= lastStart) {
            return null;
        }
        return input.substring(lastStart, lastEnd + 1);
    }
}
