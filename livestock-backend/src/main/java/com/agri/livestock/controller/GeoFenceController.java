package com.agri.livestock.controller;

import com.agri.livestock.dto.FenceStatsDto;
import com.agri.livestock.dto.GeoFenceDto;
import com.agri.livestock.entity.Animal;
import com.agri.livestock.entity.GeoFence;
import com.agri.livestock.service.GeoFenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/geofences")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class GeoFenceController {

    private final GeoFenceService geoFenceService;

    @GetMapping
    public List<GeoFence> listAll() {
        return geoFenceService.getAll();
    }

    @PostMapping
    public GeoFence create(@RequestBody @Valid GeoFenceDto dto) {
        return geoFenceService.create(dto);
    }

    @PutMapping("/{id}")
    public GeoFence update(@PathVariable Long id, @RequestBody @Valid GeoFenceDto dto) {
        return geoFenceService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        geoFenceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/animals")
    public List<Animal> getAssignedAnimals(@PathVariable Long id) {
        return geoFenceService.getAssignedAnimals(id);
    }

    @PutMapping("/{id}/animals")
    public ResponseEntity<Void> setAssignedAnimals(@PathVariable Long id, @RequestBody List<Long> animalIds) {
        geoFenceService.setAssignedAnimals(id, animalIds);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stats")
    public FenceStatsDto getStats(@PathVariable Long id) {
        return geoFenceService.getFenceStats(id);
    }
}
