package com.agri.livestock.dto;

import java.time.Instant;

public record GeofenceAlertNotificationDto(
        Long animalId,
        String animalName,
        String fenceName,
        Instant timestamp
) {}
