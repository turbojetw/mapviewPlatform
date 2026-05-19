package com.agri.livestock.dto;

import java.util.Set;

public record FenceStatsDto(
        Long fenceId,
        String fenceName,
        int totalAssigned,
        int currentlyInside,
        int away,
        Set<Long> insideAnimalIds
) {}
