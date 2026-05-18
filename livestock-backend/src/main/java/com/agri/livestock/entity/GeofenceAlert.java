package com.agri.livestock.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "geofence_alerts", indexes = {
    @Index(name = "idx_alert_ts", columnList = "timestamp DESC")
})
@Data
@NoArgsConstructor
public class GeofenceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "animal_id", nullable = false)
    private Long animalId;

    @Column(name = "animal_name", nullable = false)
    private String animalName;

    @Column(name = "fence_name", nullable = false)
    private String fenceName;

    @Column(nullable = false)
    private Instant timestamp;
}
