package com.karmaos.publishing;

public interface SocialPublisher {
    String getPlatform();
    PublishResponse publish(PublishRequest request);
    boolean testConnection();
}
