package com.agri.livestock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mqtt")
public record MqttProperties(
        String brokerUrl,
        String clientId,
        String topicPrefix,
        String username,
        String password,
        boolean enabled
) {}
