package com.agri.livestock.controller;

import com.agri.livestock.dto.DevicePayloadDto;
import com.agri.livestock.entity.Animal;
import com.agri.livestock.repository.AnimalRepository;
import com.agri.livestock.repository.LocationFixRepository;
import com.agri.livestock.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

@Slf4j
@RestController
@RequestMapping("/api/simulator")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DeviceSimulatorController {

    private final LocationService locationService;
    private final AnimalRepository animalRepository;
    private final LocationFixRepository locationFixRepository;
    private final Random random = new Random();

    @PostMapping("/push")
    public ResponseEntity<String> push(@RequestBody DevicePayloadDto payload) {
        locationService.ingest(payload);
        return ResponseEntity.ok("Fix ingested for device: " + payload.deviceEui());
    }

    @PostMapping("/push-random/{animalId}")
    public ResponseEntity<DevicePayloadDto> pushRandom(@PathVariable Long animalId) {
        Animal animal = animalRepository.findById(animalId)
                .orElseThrow(() -> new NoSuchElementException("Animal not found: " + animalId));

        double baseLat = 28.20;
        double baseLng = 105.50;

        List<?> lastFix = locationFixRepository.findLatestByAnimal(animalId, PageRequest.of(0, 1));
        if (!lastFix.isEmpty()) {
            var fix = (com.agri.livestock.entity.LocationFix) lastFix.get(0);
            if (fix.getLat() != null) baseLat = fix.getLat();
            if (fix.getLng() != null) baseLng = fix.getLng();
        }

        double lat = baseLat + (random.nextDouble() - 0.5) * 0.024;
        double lng = baseLng + (random.nextDouble() - 0.5) * 0.024;
        double alt = 1200 + random.nextDouble() * 200;
        float hdop = 0.8f + random.nextFloat() * 1.5f;
        int bat = 3500 + random.nextInt(600);

        DevicePayloadDto payload = new DevicePayloadDto(animal.getDeviceEui(), null, lat, lng, alt, hdop, bat);
        locationService.ingest(payload);
        log.info("Simulated fix for animal '{}': {},{}", animal.getName(), lat, lng);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/push-random-all")
    public ResponseEntity<String> pushRandomAll() {
        List<Animal> animals = animalRepository.findByActiveTrue();
        animals.forEach(a -> {
            try {
                pushRandom(a.getId());
            } catch (Exception e) {
                log.warn("Simulator failed for animal {}: {}", a.getId(), e.getMessage());
            }
        });
        return ResponseEntity.ok("Pushed random fixes for " + animals.size() + " animals");
    }
}
