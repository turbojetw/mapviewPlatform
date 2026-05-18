package com.agri.livestock.repository;

import com.agri.livestock.entity.GeofenceAlert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GeofenceAlertRepository extends JpaRepository<GeofenceAlert, Long> {
    List<GeofenceAlert> findAllByOrderByTimestampDesc(Pageable pageable);
}
