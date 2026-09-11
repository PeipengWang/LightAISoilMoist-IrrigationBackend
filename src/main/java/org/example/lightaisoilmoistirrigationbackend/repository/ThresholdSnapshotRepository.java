package org.example.lightaisoilmoistirrigationbackend.repository;

import org.example.lightaisoilmoistirrigationbackend.model.entity.ThresholdSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThresholdSnapshotRepository extends JpaRepository<ThresholdSnapshot, Long> {
    List<ThresholdSnapshot> findByStageThresholdIdOrderByChangedAtDesc(Long stageThresholdId);
}
