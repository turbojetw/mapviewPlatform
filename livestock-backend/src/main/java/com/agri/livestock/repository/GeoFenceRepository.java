package com.agri.livestock.repository;

import com.agri.livestock.entity.GeoFence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeoFenceRepository extends JpaRepository<GeoFence, Long> {
    List<GeoFence> findByActiveTrue();
}
