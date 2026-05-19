package com.agri.livestock.service;

import com.agri.livestock.dto.FenceStatsDto;
import com.agri.livestock.dto.GeoFenceDto;
import com.agri.livestock.entity.Animal;
import com.agri.livestock.entity.GeoFence;
import com.agri.livestock.repository.AnimalRepository;
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
    private final AnimalRepository animalRepository;
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
        fence.setFillOpacity(dto.fillOpacity() != null ? dto.fillOpacity() : 0.15);
        fence.setStrokeWidth(dto.strokeWidth() != null ? dto.strokeWidth() : 2.0);
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
        if (dto.fillOpacity() != null) fence.setFillOpacity(dto.fillOpacity());
        if (dto.strokeWidth() != null) fence.setStrokeWidth(dto.strokeWidth());
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

    // ── Animal assignment ─────────────────────────────────────────────────────

    public List<Animal> getAssignedAnimals(Long fenceId) {
        return animalRepository.findByHomeGeofenceIdAndActiveTrue(fenceId);
    }

    public void setAssignedAnimals(Long fenceId, List<Long> animalIds) {
        // Clear existing assignments for this fence
        animalRepository.findByHomeGeofenceIdAndActiveTrue(fenceId)
                .forEach(a -> { a.setHomeGeofenceId(null); animalRepository.save(a); });
        // Assign the new list (clears any previous home fence for those animals)
        animalIds.forEach(id -> animalRepository.findById(id).ifPresent(a -> {
            a.setHomeGeofenceId(fenceId);
            animalRepository.save(a);
        }));
    }

    public FenceStatsDto getFenceStats(Long fenceId) {
        GeoFence fence = geoFenceRepository.findById(fenceId)
                .orElseThrow(() -> new NoSuchElementException("GeoFence not found: " + fenceId));
        List<Animal> assigned = animalRepository.findByHomeGeofenceIdAndActiveTrue(fenceId);
        Set<Long> insideIds = new HashSet<>();
        Set<Long> animalFences = null;
        for (Animal a : assigned) {
            animalFences = animalInsideFences.getOrDefault(a.getId(), Collections.emptySet());
            if (animalFences.contains(fenceId)) insideIds.add(a.getId());
        }
        return new FenceStatsDto(fenceId, fence.getName(),
                assigned.size(), insideIds.size(), assigned.size() - insideIds.size(), insideIds);
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
