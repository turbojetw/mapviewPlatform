package com.agri.livestock.dto;

import com.agri.livestock.entity.Species;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnimalDto(
        Long id,
        @NotBlank String name,
        @NotNull Species species,
        String deviceEui,
        String collarColor,
        String notes,
        boolean active
) {}
