package com.agri.livestock.service;

import com.agri.livestock.dto.AnimalDto;
import com.agri.livestock.dto.AnimalStatusDto;
import com.agri.livestock.entity.Animal;
import com.agri.livestock.entity.LocationFix;
import com.agri.livestock.repository.AnimalRepository;
import com.agri.livestock.repository.LocationFixRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnimalService {

    private final AnimalRepository animalRepository;
    private final LocationFixRepository locationFixRepository;

    public List<Animal> getAllActive() {
        return animalRepository.findByActiveTrue();
    }

    public Animal getById(Long id) {
        return animalRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Animal not found: " + id));
    }

    public Animal create(AnimalDto dto) {
        Animal animal = new Animal();
        animal.setName(dto.name());
        animal.setSpecies(dto.species());
        animal.setDeviceEui(dto.deviceEui());
        animal.setCollarColor(dto.collarColor() != null ? dto.collarColor() : "#4CAF50");
        animal.setNotes(dto.notes());
        animal.setActive(true);
        return animalRepository.save(animal);
    }

    public Animal update(Long id, AnimalDto dto) {
        Animal animal = getById(id);
        animal.setName(dto.name());
        animal.setSpecies(dto.species());
        animal.setDeviceEui(dto.deviceEui());
        if (dto.collarColor() != null) animal.setCollarColor(dto.collarColor());
        animal.setNotes(dto.notes());
        return animalRepository.save(animal);
    }

    public void softDelete(Long id) {
        Animal animal = getById(id);
        animal.setActive(false);
        animalRepository.save(animal);
    }

    public Animal setHomeFence(Long animalId, Long fenceId) {
        Animal animal = getById(animalId);
        animal.setHomeGeofenceId(fenceId);   // null = unassign
        return animalRepository.save(animal);
    }

    public Optional<AnimalStatusDto> getLatestStatus(Animal animal) {
        List<LocationFix> fixes = locationFixRepository.findTop1ByAnimalOrderByTimestampDesc(animal);
        if (fixes.isEmpty()) return Optional.empty();
        return Optional.of(toStatusDto(animal, fixes.get(0), null));
    }

    public List<AnimalStatusDto> getAllLatestStatuses() {
        return getAllActive().stream()
                .map(a -> getLatestStatus(a).orElse(null))
                .filter(s -> s != null)
                .toList();
    }

    public AnimalStatusDto toStatusDto(Animal animal, LocationFix fix, String geofenceAlert) {
        int batteryPct = fix.getBatteryMv() != null
                ? Math.max(0, Math.min(100, (int) ((fix.getBatteryMv() - 3200.0) / (4200.0 - 3200.0) * 100)))
                : 0;
        return new AnimalStatusDto(
                animal.getId(),
                animal.getName(),
                animal.getSpecies().name(),
                animal.getSpecies().getEmoji(),
                animal.getCollarColor(),
                fix.getLat(),
                fix.getLng(),
                fix.getAltitude(),
                fix.getHdop(),
                fix.getBatteryMv(),
                batteryPct,
                fix.getTimestamp(),
                geofenceAlert
        );
    }
}
