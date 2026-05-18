package com.agri.livestock.controller;

import com.agri.livestock.dto.AnimalStatusDto;
import com.agri.livestock.dto.LocationHistoryDto;
import com.agri.livestock.service.AnimalService;
import com.agri.livestock.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/locations")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class LocationController {

    private final AnimalService animalService;
    private final LocationService locationService;

    @GetMapping("/latest")
    public List<AnimalStatusDto> getLatestAll() {
        return animalService.getAllLatestStatuses();
    }

    @GetMapping("/{animalId}/history")
    public List<LocationHistoryDto> getHistory(
            @PathVariable Long animalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return locationService.getHistory(animalId, from, to);
    }
}
