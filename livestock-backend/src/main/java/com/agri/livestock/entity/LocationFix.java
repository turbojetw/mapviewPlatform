package com.agri.livestock.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "location_fixes", indexes = {
        @jakarta.persistence.Index(name = "idx_fix_animal_ts", columnList = "animal_id, timestamp DESC")
})
@Data
@NoArgsConstructor
public class LocationFix {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "animal_id", nullable = false)
    private Animal animal;

    @Column(nullable = false)
    private Instant timestamp;

    private Double lat;
    private Double lng;
    private Double altitude;
    private Float hdop;

    @Column(name = "battery_mv")
    private Integer batteryMv;
}
