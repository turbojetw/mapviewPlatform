package com.agri.livestock.controller;

import com.agri.livestock.dto.AnimalEventDto;
import com.agri.livestock.entity.AnimalEvent;
import com.agri.livestock.service.AnimalEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/animals/{animalId}/events")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AnimalEventController {

    private final AnimalEventService service;

    @GetMapping
    public List<AnimalEvent> list(@PathVariable Long animalId) {
        return service.getEvents(animalId);
    }

    @PostMapping
    public AnimalEvent create(@PathVariable Long animalId, @RequestBody @Valid AnimalEventDto dto) {
        return service.addEvent(animalId, dto);
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delete(@PathVariable Long animalId, @PathVariable Long eventId) {
        service.deleteEvent(animalId, eventId);
        return ResponseEntity.noContent().build();
    }
}
