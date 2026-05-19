package com.agri.livestock.repository;

import com.agri.livestock.entity.Animal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnimalRepository extends JpaRepository<Animal, Long> {
    Optional<Animal> findByDeviceEui(String deviceEui);
    List<Animal> findByActiveTrue();
    List<Animal> findByHomeGeofenceIdAndActiveTrue(Long homeGeofenceId);
}
