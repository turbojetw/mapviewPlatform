package com.agri.livestock.dto;

import java.time.Instant;

public record LocationHistoryDto(
        Double lat,
        Double lng,
        Double altitude,
        Instant timestamp,
        Integer batteryMv
) {}
