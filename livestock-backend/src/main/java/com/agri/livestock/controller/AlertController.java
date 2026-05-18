package com.agri.livestock.controller;

import com.agri.livestock.entity.GeofenceAlert;
import com.agri.livestock.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public List<GeofenceAlert> getRecent(@RequestParam(defaultValue = "50") int limit) {
        return alertService.getRecent(limit);
    }
}
