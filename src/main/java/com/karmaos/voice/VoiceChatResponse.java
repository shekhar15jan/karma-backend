package com.karmaos.voice;

import java.util.List;
import java.util.Map;

public class VoiceChatResponse {
    private String reply;
    private String intent;
    private String target;
    private boolean fromLlm;
    private String language;
    private String eventId;
    private Map<String, Object> action;
    /** Multi-step plan: executed sequentially by the UI, max 5 steps. */
    private List<Map<String, Object>> actions;

    public VoiceChatResponse() {}

    public VoiceChatResponse(String reply, String intent, String target, boolean fromLlm) {
        this.reply = reply;
        this.intent = intent;
        this.target = target;
        this.fromLlm = fromLlm;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public boolean isFromLlm() { return fromLlm; }
    public void setFromLlm(boolean fromLlm) { this.fromLlm = fromLlm; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Map<String, Object> getAction() { return action; }
    public void setAction(Map<String, Object> action) { this.action = action; }
    public List<Map<String, Object>> getActions() { return actions; }
    public void setActions(List<Map<String, Object>> actions) { this.actions = actions; }

    public static VoiceChatResponse fromLlm(String reply, String intent, String target) {
        return new VoiceChatResponse(reply, intent, target, true);
    }

    public static VoiceChatResponse staticReply(String reply, String intent, String target) {
        return new VoiceChatResponse(reply, intent, target, false);
    }
}
