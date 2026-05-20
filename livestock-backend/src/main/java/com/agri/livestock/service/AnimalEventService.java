package com.agri.livestock.service;

import com.agri.livestock.dto.AnimalEventDto;
import com.agri.livestock.entity.AnimalEvent;
import com.agri.livestock.repository.AnimalEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnimalEventService {

    private final AnimalEventRepository repo;

    public List<AnimalEvent> getEvents(Long animalId) {
        return repo.findByAnimalIdOrderByEventDateDescCreatedAtDesc(animalId);
    }

    public AnimalEvent addEvent(Long animalId, AnimalEventDto dto) {
        AnimalEvent ev = new AnimalEvent();
        ev.setAnimalId(animalId);
        ev.setEventType(dto.eventType());
        ev.setDescription(dto.description());
        ev.setEventDate(dto.eventDate());
        return repo.save(ev);
    }

    public void deleteEvent(Long animalId, Long eventId) {
        AnimalEvent ev = repo.findById(eventId)
                .filter(e -> e.getAnimalId().equals(animalId))
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Event not found"));
        repo.delete(ev);
    }
}
