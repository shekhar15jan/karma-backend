package com.karmaos.voice;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class VoiceService {

    public Map<String, Object> processCommand(String text) {
        String normalized = text != null ? text.toLowerCase().trim() : "";
        String intent = resolveIntent(normalized);
        String target = resolveTarget(intent, normalized);
        double confidence = computeConfidence(normalized, intent);

        return Map.of(
                "text", text != null ? text : "",
                "intent", intent,
                "action", intent,
                "target", target,
                "confidence", confidence,
                "displayText", "Processing command...",
                "params", Map.of()
        );
    }

    private String resolveIntent(String text) {
        if (text.contains("go to") || text.contains("navigate") || text.contains("open") || text.contains("show")) {
            return "navigate";
        }
        if (text.contains("create") || text.contains("new") || text.contains("make")) {
            return "create";
        }
        if (text.contains("search") || text.contains("find") || text.contains("look for")) {
            return "search";
        }
        if (text.contains("delete") || text.contains("remove") || text.contains("destroy")) {
            return "delete";
        }
        if (text.contains("help") || text.contains("what can") || text.contains("commands")) {
            return "help";
        }
        return "navigate";
    }

    private String resolveTarget(String intent, String text) {
        if (text.contains("dashboard")) return "/dashboard";
        if (text.contains("project")) return "/projects";
        if (text.contains("mission")) return "/missions";
        if (text.contains("flow")) return "/flows";
        if (text.contains("agent")) return "/agents";
        if (text.contains("provider")) return "/providers";
        if (text.contains("artifact")) return "/artifacts";
        if (text.contains("review")) return "/reviews";
        if (text.contains("publish")) return "/publications";
        if (text.contains("knowledge")) return "/knowledge";
        if (text.contains("config") || text.contains("setting")) return "/config";
        return "/dashboard";
    }

    private double computeConfidence(String text, String intent) {
        if (text.isEmpty()) return 0.0;
        if (intent.equals("navigate") && !text.equals("navigate")) return 0.85;
        if (intent.equals("help")) return 0.95;
        return 0.75;
    }
}
