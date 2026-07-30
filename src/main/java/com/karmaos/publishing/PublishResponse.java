package com.karmaos.publishing;

public class PublishResponse {
    private boolean success;
    private String platformUrl;
    private String platformId;
    private String errorMessage;

    public static PublishResponse ok(String platformUrl, String platformId) {
        PublishResponse r = new PublishResponse();
        r.success = true;
        r.platformUrl = platformUrl;
        r.platformId = platformId;
        return r;
    }

    public static PublishResponse error(String errorMessage) {
        PublishResponse r = new PublishResponse();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getPlatformUrl() { return platformUrl; }
    public void setPlatformUrl(String platformUrl) { this.platformUrl = platformUrl; }
    public String getPlatformId() { return platformId; }
    public void setPlatformId(String platformId) { this.platformId = platformId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
