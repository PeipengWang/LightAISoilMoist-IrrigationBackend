package org.example.lightaisoilmoistirrigationbackend.repository;

import org.example.lightaisoilmoistirrigationbackend.model.entity.CropConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CropConfigRepository extends JpaRepository<CropConfig, Long> {
    Optional<CropConfig> findByIsActiveTrue();
}
