package com.karmaos.config;

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

/**
 * Makes every WebClient (LLM, image and voice providers) honour the standard JVM proxy
 * properties (https.proxyHost / http.nonProxyHosts, ...). Reactor Netty ignores them by
 * default, so the app could not reach providers on networks that require an HTTP proxy.
 * Without those properties set this is a no-op.
 */
@Configuration
public class WebClientProxyConfig {

    @Bean
    public WebClientCustomizer systemProxyWebClientCustomizer() {
        return builder -> builder.clientConnector(
                new ReactorClientHttpConnector(HttpClient.create().proxyWithSystemProperties()));
    }
}
