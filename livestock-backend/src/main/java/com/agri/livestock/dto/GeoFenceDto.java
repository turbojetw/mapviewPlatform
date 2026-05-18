package com.agri.livestock.dto;

import jakarta.validation.constraints.NotBlank;

public record GeoFenceDto(
        Long id,
        @NotBlank String name,
        @NotBlank String coordinatesJson,
        String color,
        boolean alertOnExit,
        boolean active
) {}
