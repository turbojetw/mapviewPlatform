package com.agri.livestock.repository;

import com.agri.livestock.entity.AnimalEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnimalEventRepository extends JpaRepository<AnimalEvent, Long> {
    List<AnimalEvent> findByAnimalIdOrderByEventDateDescCreatedAtDesc(Long animalId);
}
