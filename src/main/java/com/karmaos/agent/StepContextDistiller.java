package com.karmaos.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Distills a step's raw output into a compact, relevant context digest for the next step.
 * Prevents forwarding entire JSON blobs (token waste + payload overflow) downstream.
 */
public final class StepContextDistiller {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TEXT_CHARS = 4000;

    private static final String[] STUB_PHRASES = {
        "no source material", "no research data", "zero source document",
        "no source documents", "status\": \"blocked", "no context was provided",
        "no previous step output", "nothing to process"
    };

    private StepContextDistiller() {}

    public static String distill(String rawOutput) {
        if (rawOutput == null) return "";
        String trimmed = rawOutput.trim();
        if (trimmed.isEmpty()) return "";

        try {
            String jsonCandidate = extractJson(trimmed);
            if (jsonCandidate != null) {
                JsonNode node = MAPPER.readTree(jsonCandidate);
                if (node != null && node.isObject()) {
                    JsonNode body = node.get("script_body");
                    if (body != null && body.isArray()) {
                        return scriptBodyToNarration(body);
                    }
                    JsonNode findings = node.get("findings");
                    if (findings != null && findings.isArray()) {
                        return findingsToSummary(findings);
                    }
                    JsonNode content = node.get("content");
                    if (content != null && content.isTextual() && !content.asText().isBlank()) {
                        return truncate(content.asText());
                    }
                }
            }
        } catch (Exception ignored) {
            // not JSON; fall through to plain text
        }
        return truncate(trimmed);
    }

    /**
     * Extracts JSON from a markdown-fenced code block if present; otherwise returns
     * the input when it already starts with a JSON object/array. Prefers the last
     * strictly-parseable balanced object so reasoning prose before the real JSON is
     * skipped and schema examples embedded in reasoning are ignored.
     */
    private static String extractJson(String input) {
        if (input == null) return null;
        String strict = com.karmaos.common.StrictJson.extractLastObjectJson(input);
        if (strict != null) {
            return strict;
        }
        int fence = input.indexOf("```");
        if (fence >= 0) {
            int start = input.indexOf('{', fence);
            int end = input.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return input.substring(start, end + 1);
            }
            int arrStart = input.indexOf('[', fence);
            int arrEnd = input.lastIndexOf(']');
            if (arrStart >= 0 && arrEnd > arrStart) {
                return input.substring(arrStart, arrEnd + 1);
            }
            return null;
        }
        if (input.startsWith("{") || input.startsWith("[")) {
            return input;
        }
        return null;
    }

    private static String scriptBodyToNarration(JsonNode body) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode section : body) {
            if (section.hasNonNull("speaker_text")) {
                sb.append(section.get("speaker_text").asText()).append("\n\n");
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "" : truncate(result);
    }

    private static String findingsToSummary(JsonNode findings) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode f : findings) {
            String title = f.hasNonNull("title") ? f.get("title").asText() : "";
            String summary = f.hasNonNull("summary") ? f.get("summary").asText()
                    : (f.hasNonNull("content") ? f.get("content").asText() : "");
            if (!title.isBlank()) sb.append("- ").append(title);
            if (!summary.isBlank()) sb.append(": ").append(summary);
            if (!title.isBlank() || !summary.isBlank()) sb.append("\n");
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "" : truncate(result);
    }

    public static boolean isStub(String output) {
        if (output == null) return true;
        String lower = output.toLowerCase();
        for (String p : STUB_PHRASES) {
            if (lower.contains(p)) return true;
        }
        return false;
    }

    public static boolean isValidOutput(String output) {
        return output != null
            && !output.isBlank()
            && output.trim().length() >= 30
            && !isStub(output);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= MAX_TEXT_CHARS) return s;
        return s.substring(0, MAX_TEXT_CHARS) + "\n...[context truncated]";
    }
}
