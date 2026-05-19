package com.agri.livestock.service;

import com.agri.livestock.dto.DevicePayloadDto;
import com.agri.livestock.dto.AnimalStatusDto;
import com.agri.livestock.dto.LocationHistoryDto;
import com.agri.livestock.dto.GeofenceAlertNotificationDto;
import com.agri.livestock.entity.Animal;
import com.agri.livestock.entity.LocationFix;
import com.agri.livestock.repository.AnimalRepository;
import com.agri.livestock.repository.LocationFixRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final AnimalRepository animalRepository;
    private final LocationFixRepository locationFixRepository;
    private final AnimalService animalService;
    private final GeoFenceService geoFenceService;
    private final AlertService alertService;
    private final SimpMessagingTemplate messagingTemplate;

    public void ingest(DevicePayloadDto payload) {
        Optional<Animal> animalOpt = animalRepository.findByDeviceEui(payload.deviceEui());
        if (animalOpt.isEmpty()) {
            log.warn("Received fix for unknown deviceEui: {}", payload.deviceEui());
            return;
        }
        Animal animal = animalOpt.get();

        LocationFix fix = new LocationFix();
        fix.setAnimal(animal);
        fix.setTimestamp(payload.ts() != null ? Instant.ofEpochMilli(payload.ts()) : Instant.now());
        fix.setLat(payload.lat());
        fix.setLng(payload.lng());
        fix.setAltitude(payload.alt());
        fix.setHdop(payload.hdop());
        fix.setBatteryMv(payload.bat());
        locationFixRepository.save(fix);

        String alert = null;
        if (payload.lat() != null && payload.lng() != null) {
            alert = geoFenceService.checkFenceExit(payload.lat(), payload.lng(), animal.getId())
                    .orElse(null);
            if (alert != null) {
                log.info("Animal '{}' exited geofence '{}'", animal.getName(), alert);
                alertService.save(animal.getId(), animal.getName(), alert);
                messagingTemplate.convertAndSend("/topic/geofence/alert",
                        new GeofenceAlertNotificationDto(animal.getId(), animal.getName(), alert, Instant.now()));
            }
        }

        AnimalStatusDto status = animalService.toStatusDto(animal, fix, alert);
        messagingTemplate.convertAndSend("/topic/animal/location", status);
        log.debug("Published location update for animal {} at {},{}", animal.getName(), payload.lat(), payload.lng());
    }

    public List<LocationHistoryDto> getHistory(Long animalId, Instant from, Instant to) {
        Animal animal = animalService.getById(animalId);
        return locationFixRepository
                .findByAnimalAndTimestampBetweenOrderByTimestampAsc(animal, from, to)
                .stream()
                .map(f -> new LocationHistoryDto(f.getLat(), f.getLng(), f.getAltitude(), f.getTimestamp(), f.getBatteryMv()))
                .toList();
    }
}
