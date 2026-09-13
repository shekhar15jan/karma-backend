package com.karmaos.workspace;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Read allowed origins from env (comma-separated), fall back to localhost for dev
        String envOrigins = System.getenv("KARMA_WS_ORIGINS");
        String[] origins;
        if (envOrigins != null && !envOrigins.isBlank()) {
            origins = envOrigins.split(",");
            for (int i = 0; i < origins.length; i++) origins[i] = origins[i].trim();
        } else {
            origins = new String[]{"http://localhost*", "http://127.0.0.1*"};
        }
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .withSockJS();
    }
}
