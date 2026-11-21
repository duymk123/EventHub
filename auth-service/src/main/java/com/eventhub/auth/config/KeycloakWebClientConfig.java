package com.eventhub.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient configuration for Keycloak token endpoint calls (login, refresh, logout).
 * Non-blocking HTTP client for Authorization Code + PKCE flow.
 */
@Configuration
public class KeycloakWebClientConfig {

    @Value("${keycloak.admin.server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.token.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${keycloak.token.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Bean
    public WebClient keycloakTokenWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(readTimeoutMs))
                .keepAlive(true);

        return WebClient.builder()
                .baseUrl(keycloakServerUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
    }
}