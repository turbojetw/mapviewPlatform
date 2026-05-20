package com.agri.livestock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AnimalEventDto(
        @NotBlank String eventType,
        @NotBlank String description,
        @NotNull LocalDate eventDate
) {}
