package com.agri.livestock.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Species {
    GOOSE("Goose", "🦢"),
    CHICKEN("Chicken", "🐔"),
    SHEEP("Sheep", "🐑"),
    PIG("Pig", "🐷");

    private final String displayName;
    private final String emoji;
}
