package com.karmaos.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.voice.VoiceChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KarmaMemoryService {

    private static final Logger log = LoggerFactory.getLogger(KarmaMemoryService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MMM d HH:mm");
    private static final int AUTO_CONFIDENCE_STEP = 15;

    private final KarmaInteractionEventRepository eventRepository;
    private final KarmaUserMemoryRepository memoryRepository;
    private final ObjectMapper objectMapper;

    public KarmaMemoryService(KarmaInteractionEventRepository eventRepository,
                              KarmaUserMemoryRepository memoryRepository,
                              ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.memoryRepository = memoryRepository;
        this.objectMapper = objectMapper;
    }

    // ========== RECORDING ==========

    /**
     * Records one chat turn + any emitted action with status PENDING, so the frontend
     * can later report whether the action actually succeeded. Returns the event id.
     */
    public UUID recordInteraction(UUID userId, String userMessage, VoiceChatResponse response, String page) {
        try {
            KarmaInteractionEvent event = new KarmaInteractionEvent(userId, userMessage);
            event.setKarmaReply(response != null ? response.getReply() : null);
            event.setPage(page);
            if (response != null && response.getAction() != null) {
                event.setActionType(String.valueOf(response.getAction().get("type")));
                event.setActionParams(safeJson(response.getAction()));
            }
            return eventRepository.save(event).getId();
        } catch (Exception e) {
            log.warn("Failed to record interaction for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Reports whether a previously emitted action succeeded. On success, reinforces
     * learned preferences derived from the action params.
     */
    public void recordActionFeedback(UUID userId, UUID eventId, boolean success, String note) {
        if (eventId == null) return;
        try {
            KarmaInteractionEvent event = eventRepository.findByIdAndUserId(eventId, userId).orElse(null);
            if (event == null) return;
            event.setActionStatus(success ? "SUCCESS" : "FAILED");
            event.setOutcomeNote(note);
            eventRepository.save(event);

            if (success && event.getActionType() != null) {
                learnFromAction(event.getActionType(), event.getActionParams(), userId);
            }
        } catch (Exception e) {
            log.warn("Failed to record action feedback for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Explicit memory — the user told KARMA to remember something ("remember that I prefer X").
     */
    public void remember(UUID userId, String key, String value, String source) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) return;
        try {
            KarmaUserMemory memory = memoryRepository.findByUserIdAndMemoryKey(userId, key.trim()).orElse(null);
            if (memory == null) {
                memory = new KarmaUserMemory(userId, key.trim(), value.trim());
                memory.setSource(source != null && !source.isBlank() ? source : "EXPLICIT");
                memory.setConfidence(100);
            } else {
                memory.setMemoryValue(value.trim());
                memory.setSource(source != null && !source.isBlank() ? source : "EXPLICIT");
                memory.setConfidence(100);
            }
            memoryRepository.save(memory);
        } catch (Exception e) {
            log.warn("Failed to save memory for user {}: {}", userId, e.getMessage());
        }
    }

    // ========== IMPLICIT LEARNING ==========

    private void learnFromAction(String actionType, String actionParamsJson, UUID userId) {
        try {
            if (actionParamsJson == null || actionParamsJson.isBlank()) return;
            Map<String, Object> root = objectMapper.readValue(actionParamsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (root == null) return;
            Object rawParams = root.get("params");
            Map<String, Object> params;
            if (rawParams instanceof Map<?, ?>) {
                params = (Map<String, Object>) rawParams;
            } else if (root.containsKey("runMode") || root.containsKey("priority")
                    || root.containsKey("missionType") || root.containsKey("providerId")) {
                params = root;
            } else {
                return;
            }

            String runMode = str(params.get("runMode"));
            String priority = str(params.get("priority"));
            String missionType = str(params.get("missionType"));
            String providerId = str(params.get("providerId"));

            if (runMode != null && ("AUTO".equalsIgnoreCase(runMode) || "REVIEW".equalsIgnoreCase(runMode))) {
                reinforce(userId, "preferred.run_mode", runMode.toUpperCase());
            }
            if (priority != null && !priority.isBlank()) {
                reinforce(userId, "preferred.priority", priority);
            }
            if (missionType != null && !missionType.isBlank()) {
                reinforce(userId, "preferred.mission_type", missionType);
            }
            if (providerId != null && !providerId.isBlank() && actionType.contains("mission")) {
                reinforce(userId, "preferred.provider_id", providerId);
            }
        } catch (Exception e) {
            log.warn("Failed to learn from action {}: {}", actionType, e.getMessage());
        }
    }

    private void reinforce(UUID userId, String key, String value) {
        try {
            KarmaUserMemory memory = memoryRepository.findByUserIdAndMemoryKey(userId, key).orElse(null);
            if (memory == null) {
                memory = new KarmaUserMemory(userId, key, value);
                memory.setSource("AUTO");
                memory.setConfidence(AUTO_CONFIDENCE_STEP);
            } else {
                memory.setMemoryValue(value);
                memory.setConfidence(Math.min(100, memory.getConfidence() + AUTO_CONFIDENCE_STEP));
            }
            memoryRepository.save(memory);
        } catch (Exception e) {
            log.warn("Failed to reinforce memory {} for user {}: {}", key, userId, e.getMessage());
        }
    }

    // ========== PROMPT INJECTION ==========

    /**
     * Builds a compact memory block for the system prompt: the user's learned preferences
     * (confidence >= 60 or explicit) and the most recent action outcomes so KARMA adapts
     * (reuses what worked, avoids repeating failures).
     */
    public String buildMemoryPromptBlock(UUID userId) {
        return buildMemoryPromptBlock(userId, true);
    }

    /**
     * Same as {@link #buildMemoryPromptBlock(UUID)} but with a toggle for the auto-learned
     * (implicit) memories and action outcomes. USER mode only includes explicit user-told
     * memories; AUTO mode adds learned preferences and recent action outcomes.
     */
    public String buildMemoryPromptBlock(UUID userId, boolean includeAuto) {
        StringBuilder sb = new StringBuilder();
        try {
            List<KarmaUserMemory> memories = memoryRepository.findByUserIdOrderByConfidenceDesc(userId);
            List<KarmaUserMemory> shown = new ArrayList<>();
            for (KarmaUserMemory m : memories) {
                boolean explicitOnly = !includeAuto && !"EXPLICIT".equalsIgnoreCase(m.getSource());
                if (explicitOnly) continue;
                if ("EXPLICIT".equalsIgnoreCase(m.getSource()) || m.getConfidence() >= 60) {
                    shown.add(m);
                    if (shown.size() >= 6) break;
                }
            }
            if (!shown.isEmpty()) {
                sb.append("LEARNED USER MEMORY (from past conversations and actions — personalize to this user, but follow any NEW instruction that contradicts them):\n");
                for (KarmaUserMemory m : shown) {
                    String source = "EXPLICIT".equalsIgnoreCase(m.getSource()) ? "user-told" : "learned";
                    sb.append("  - ").append(m.getMemoryKey()).append(": ").append(m.getMemoryValue())
                      .append(" (").append(source).append(")\n");
                }
                sb.append("\n");
            }

            if (includeAuto) {
                List<KarmaInteractionEvent> recent = eventRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
                List<KarmaInteractionEvent> withAction = new ArrayList<>();
                for (KarmaInteractionEvent e : recent) {
                    if (e.getActionType() != null && !e.getActionType().isBlank()) {
                        withAction.add(e);
                        if (withAction.size() >= 8) break;
                    }
                }
                if (!withAction.isEmpty()) {
                    sb.append("RECENT ACTION OUTCOMES (what happened on this account recently — avoid repeating failures, reuse what succeeded):\n");
                    for (KarmaInteractionEvent e : withAction) {
                        String when = e.getCreatedAt() != null ? e.getCreatedAt().format(TIME_FMT) : "";
                        String outcome = "SUCCESS".equals(e.getActionStatus()) ? "OK"
                                : "FAILED".equals(e.getActionStatus()) ? "FAILED"
                                : "sent-to-UI";
                        sb.append("  - [").append(when).append("] ").append(e.getActionType())
                          .append(" → ").append(outcome);
                        if (e.getOutcomeNote() != null && !e.getOutcomeNote().isBlank()) {
                            sb.append(" (").append(e.getOutcomeNote()).append(")");
                        }
                        sb.append("\n");
                    }
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to build memory block for user {}: {}", userId, e.getMessage());
        }
        return sb.toString();
    }

    public boolean hasMemory(UUID userId) {
        return memoryRepository.findByUserIdOrderByConfidenceDesc(userId).stream()
                .anyMatch(m -> "EXPLICIT".equalsIgnoreCase(m.getSource()) || m.getConfidence() >= 60);
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
