package com.agri.livestock.repository;

import com.agri.livestock.entity.Animal;
import com.agri.livestock.entity.LocationFix;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LocationFixRepository extends JpaRepository<LocationFix, Long> {

    List<LocationFix> findTop1ByAnimalOrderByTimestampDesc(Animal animal);

    List<LocationFix> findByAnimalAndTimestampBetweenOrderByTimestampAsc(
            Animal animal, Instant from, Instant to);

    @Query("SELECT f FROM LocationFix f WHERE f.animal.id = :animalId ORDER BY f.timestamp DESC")
    List<LocationFix> findLatestByAnimal(@Param("animalId") Long animalId, Pageable pageable);
}
