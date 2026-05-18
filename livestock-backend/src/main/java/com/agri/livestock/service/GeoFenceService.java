package com.agri.livestock.service;

import com.agri.livestock.dto.GeoFenceDto;
import com.agri.livestock.entity.GeoFence;
import com.agri.livestock.repository.GeoFenceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoFenceService {

    private final GeoFenceRepository geoFenceRepository;
    private final ObjectMapper objectMapper;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    // animalId → set of fenceIds that animal is currently inside
    private final Map<Long, Set<Long>> animalInsideFences = new ConcurrentHashMap<>();

    public List<GeoFence> getAllActive() {
        return geoFenceRepository.findByActiveTrue();
    }

    public List<GeoFence> getAll() {
        return geoFenceRepository.findAll();
    }

    public GeoFence create(GeoFenceDto dto) {
        GeoFence fence = new GeoFence();
        fence.setName(dto.name());
        fence.setCoordinatesJson(dto.coordinatesJson());
        fence.setColor(dto.color() != null ? dto.color() : "#FF6B6B");
        fence.setAlertOnExit(dto.alertOnExit());
        fence.setActive(true);
        return geoFenceRepository.save(fence);
    }

    public GeoFence update(Long id, GeoFenceDto dto) {
        GeoFence fence = geoFenceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("GeoFence not found: " + id));
        fence.setName(dto.name());
        fence.setCoordinatesJson(dto.coordinatesJson());
        fence.setColor(dto.color() != null ? dto.color() : fence.getColor());
        fence.setAlertOnExit(dto.alertOnExit());
        fence.setActive(dto.active());
        return geoFenceRepository.save(fence);
    }

    public void delete(Long id) {
        geoFenceRepository.findById(id).ifPresent(f -> {
            f.setActive(false);
            geoFenceRepository.save(f);
        });
    }

    /**
     * Returns the name of the first fence the animal just exited, or empty if no exit detected.
     * Maintains in-memory state of which fences each animal is inside.
     */
    public Optional<String> checkFenceExit(Double lat, Double lng, Long animalId) {
        List<GeoFence> activeFences = getAllActive();
        if (activeFences.isEmpty()) return Optional.empty();

        Point point = geometryFactory.createPoint(new Coordinate(lng, lat));
        Set<Long> currentlyInside = new HashSet<>();

        for (GeoFence fence : activeFences) {
            if (!fence.isAlertOnExit()) continue;
            try {
                Polygon polygon = parsePolygon(fence.getCoordinatesJson());
                if (polygon.contains(point)) {
                    currentlyInside.add(fence.getId());
                }
            } catch (Exception e) {
                log.warn("Failed to parse geofence {} coordinates: {}", fence.getId(), e.getMessage());
            }
        }

        Set<Long> previouslyInside = animalInsideFences.getOrDefault(animalId, Collections.emptySet());
        animalInsideFences.put(animalId, currentlyInside);

        // Detect exits: was inside, now outside
        for (Long fenceId : previouslyInside) {
            if (!currentlyInside.contains(fenceId)) {
                return activeFences.stream()
                        .filter(f -> f.getId().equals(fenceId))
                        .map(GeoFence::getName)
                        .findFirst();
            }
        }
        return Optional.empty();
    }

    private Polygon parsePolygon(String coordinatesJson) throws Exception {
        List<List<Double>> coords = objectMapper.readValue(
                coordinatesJson, new TypeReference<>() {});
        Coordinate[] ring = coords.stream()
                .map(c -> new Coordinate(c.get(0), c.get(1)))
                .toArray(Coordinate[]::new);
        // JTS requires closed ring
        if (!ring[0].equals2D(ring[ring.length - 1])) {
            ring = Arrays.copyOf(ring, ring.length + 1);
            ring[ring.length - 1] = new Coordinate(ring[0].x, ring[0].y);
        }
        return geometryFactory.createPolygon(ring);
    }
}
