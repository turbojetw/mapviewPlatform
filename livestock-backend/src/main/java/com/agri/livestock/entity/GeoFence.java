package com.agri.livestock.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "geo_fences")
@Data
@NoArgsConstructor
public class GeoFence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // JSON array of [lng, lat] pairs e.g. [[105.1,28.1],[105.2,28.1],...]
    @Column(name = "coordinates_json", columnDefinition = "TEXT", nullable = false)
    private String coordinatesJson;

    private String color = "#FF6B6B";

    @Column(name = "fill_opacity")
    private double fillOpacity = 0.15;

    @Column(name = "stroke_width")
    private double strokeWidth = 2.0;

    @Column(name = "alert_on_exit")
    private boolean alertOnExit = true;

    private boolean active = true;
}
