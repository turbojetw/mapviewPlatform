package com.agri.livestock.dto;

import java.time.Instant;

public record AnimalStatusDto(
        Long animalId,
        String name,
        String species,
        String speciesEmoji,
        String collarColor,
        Double lat,
        Double lng,
        Double altitude,
        Float hdop,
        Integer batteryMv,
        Integer batteryPct,
        Instant timestamp,
        String geofenceAlert
) {}
