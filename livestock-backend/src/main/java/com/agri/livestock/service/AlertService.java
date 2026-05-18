package com.agri.livestock.service;

import com.agri.livestock.entity.GeofenceAlert;
import com.agri.livestock.repository.GeofenceAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final GeofenceAlertRepository alertRepository;

    public void save(Long animalId, String animalName, String fenceName) {
        GeofenceAlert alert = new GeofenceAlert();
        alert.setAnimalId(animalId);
        alert.setAnimalName(animalName);
        alert.setFenceName(fenceName);
        alert.setTimestamp(Instant.now());
        alertRepository.save(alert);
    }

    public List<GeofenceAlert> getRecent(int limit) {
        return alertRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, limit));
    }
}
