package com.agri.livestock;

import com.agri.livestock.config.MqttProperties;
import com.agri.livestock.entity.Animal;
import com.agri.livestock.entity.GeoFence;
import com.agri.livestock.entity.Species;
import com.agri.livestock.repository.AnimalRepository;
import com.agri.livestock.repository.GeoFenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties(MqttProperties.class)
public class LivestockApplication {

    public static void main(String[] args) {
        SpringApplication.run(LivestockApplication.class, args);
    }

    @Bean
    CommandLineRunner seedData(AnimalRepository animalRepo, GeoFenceRepository fenceRepo) {
        return args -> {
            if (animalRepo.count() > 0) return;

            log.info("Seeding sample livestock data...");

            Animal sheep1 = new Animal();
            sheep1.setName("Sheep-001");
            sheep1.setSpecies(Species.SHEEP);
            sheep1.setDeviceEui("DEVICE001");
            sheep1.setCollarColor("#4CAF50");
            animalRepo.save(sheep1);

            Animal sheep2 = new Animal();
            sheep2.setName("Sheep-002");
            sheep2.setSpecies(Species.SHEEP);
            sheep2.setDeviceEui("DEVICE002");
            sheep2.setCollarColor("#2196F3");
            animalRepo.save(sheep2);

            Animal chicken = new Animal();
            chicken.setName("Chicken-001");
            chicken.setSpecies(Species.CHICKEN);
            chicken.setDeviceEui("DEVICE003");
            chicken.setCollarColor("#FF9800");
            animalRepo.save(chicken);

            Animal pig = new Animal();
            pig.setName("Pig-001");
            pig.setSpecies(Species.PIG);
            pig.setDeviceEui("DEVICE004");
            pig.setCollarColor("#E91E63");
            animalRepo.save(pig);

            Animal goose = new Animal();
            goose.setName("Goose-001");
            goose.setSpecies(Species.GOOSE);
            goose.setDeviceEui("DEVICE005");
            goose.setCollarColor("#9C27B0");
            animalRepo.save(goose);

            // Sample geofence: 500m × 500m rectangle around base location
            GeoFence fence = new GeoFence();
            fence.setName("Main Pasture");
            fence.setCoordinatesJson("[[105.496,28.196],[105.504,28.196],[105.504,28.204],[105.496,28.204],[105.496,28.196]]");
            fence.setColor("#FF6B6B");
            fence.setAlertOnExit(true);
            fence.setActive(true);
            fenceRepo.save(fence);

            log.info("Seeded 5 animals and 1 geofence. Use POST /api/simulator/push-random-all to generate initial positions.");
        };
    }
}
