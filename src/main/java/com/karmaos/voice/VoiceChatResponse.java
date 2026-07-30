package com.karmaos.voice;

public class VoiceChatResponse {
    private String reply;
    private String intent;
    private String target;
    private boolean fromLlm;

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

    public static VoiceChatResponse fromLlm(String reply, String intent, String target) {
        return new VoiceChatResponse(reply, intent, target, true);
    }

    public static VoiceChatResponse staticReply(String reply, String intent, String target) {
        return new VoiceChatResponse(reply, intent, target, false);
    }
}
