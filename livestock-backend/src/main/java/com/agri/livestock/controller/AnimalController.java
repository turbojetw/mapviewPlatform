package com.agri.livestock.controller;

import com.agri.livestock.dto.AnimalDto;
import com.agri.livestock.entity.Animal;
import com.agri.livestock.service.AnimalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/animals")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AnimalController {

    private final AnimalService animalService;

    @GetMapping
    public List<Animal> listAll() {
        return animalService.getAllActive();
    }

    @GetMapping("/{id}")
    public Animal getById(@PathVariable Long id) {
        return animalService.getById(id);
    }

    @PostMapping
    public Animal create(@RequestBody @Valid AnimalDto dto) {
        return animalService.create(dto);
    }

    @PutMapping("/{id}")
    public Animal update(@PathVariable Long id, @RequestBody @Valid AnimalDto dto) {
        return animalService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        animalService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
