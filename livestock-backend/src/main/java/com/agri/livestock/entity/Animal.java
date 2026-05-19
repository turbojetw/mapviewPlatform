package com.agri.livestock.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "animals", indexes = {
        @jakarta.persistence.Index(name = "idx_animal_device_eui", columnList = "device_eui")
})
@Data
@NoArgsConstructor
public class Animal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Species species;

    @Column(name = "device_eui", unique = true)
    private String deviceEui;

    @Column(name = "collar_color")
    private String collarColor = "#4CAF50";

    private String notes;

    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    // Nullable: which geofence this animal is "assigned" to as its home area
    @Column(name = "home_geofence_id")
    private Long homeGeofenceId;
}
