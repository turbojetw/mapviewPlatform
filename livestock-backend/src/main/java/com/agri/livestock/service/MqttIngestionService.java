package com.agri.livestock.service;

import com.agri.livestock.config.MqttProperties;
import com.agri.livestock.dto.DevicePayloadDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttIngestionService {

    private final MqttProperties mqttProperties;
    private final LocationService locationService;
    private final ObjectMapper objectMapper;

    private MqttClient mqttClient;

    @PostConstruct
    public void connect() {
        if (!mqttProperties.enabled()) {
            log.info("MQTT ingestion disabled (mqtt.enabled=false). Use POST /api/simulator/push to inject fixes.");
            return;
        }
        try {
            mqttClient = new MqttClient(mqttProperties.brokerUrl(), mqttProperties.clientId(), new MemoryPersistence());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            opts.setKeepAliveInterval(60);
            opts.setAutomaticReconnect(true);
            if (mqttProperties.username() != null && !mqttProperties.username().isBlank()) {
                opts.setUserName(mqttProperties.username());
                opts.setPassword(mqttProperties.password().toCharArray());
            }
            mqttClient.connect(opts);
            String topic = mqttProperties.topicPrefix() + "#";
            mqttClient.subscribe(topic, 1, this::onMessage);
            log.info("MQTT connected to {} and subscribed to {}", mqttProperties.brokerUrl(), topic);
        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker at {}: {}", mqttProperties.brokerUrl(), e.getMessage());
        }
    }

    private void onMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            log.debug("MQTT message on {}: {}", topic, payload);
            DevicePayloadDto dto = objectMapper.readValue(payload, DevicePayloadDto.class);
            // If deviceEui not in payload, derive from topic suffix
            if (dto.deviceEui() == null) {
                String eui = topic.substring(mqttProperties.topicPrefix().length());
                dto = new DevicePayloadDto(eui, dto.ts(), dto.lat(), dto.lng(), dto.alt(), dto.hdop(), dto.bat());
            }
            locationService.ingest(dto);
        } catch (Exception e) {
            log.error("Error processing MQTT message on topic {}: {}", topic, e.getMessage());
        }
    }

    @PreDestroy
    public void disconnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                log.info("MQTT client disconnected");
            } catch (MqttException e) {
                log.warn("Error disconnecting MQTT client: {}", e.getMessage());
            }
        }
    }
}
