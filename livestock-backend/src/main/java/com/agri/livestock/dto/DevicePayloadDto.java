package com.agri.livestock.dto;

public record DevicePayloadDto(
        String deviceEui,
        Long ts,        // epoch millis; use Instant.now() if null
        Double lat,
        Double lng,
        Double alt,
        Float hdop,
        Integer bat     // battery millivolts
) {}
