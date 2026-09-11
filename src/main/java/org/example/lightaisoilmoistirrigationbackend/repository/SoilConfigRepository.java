package org.example.lightaisoilmoistirrigationbackend.repository;

import org.example.lightaisoilmoistirrigationbackend.model.entity.SoilConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SoilConfigRepository extends JpaRepository<SoilConfig, Long> {
    Optional<SoilConfig> findBySoilType(String soilType);
}