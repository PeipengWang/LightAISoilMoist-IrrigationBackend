package org.example.lightaisoilmoistirrigationbackend.repository;

import org.example.lightaisoilmoistirrigationbackend.model.entity.StageThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StageThresholdRepository extends JpaRepository<StageThreshold, Long> {

    List<StageThreshold> findByPhenologyStageIdAndIsActiveTrue(Long stageId);

    List<StageThreshold> findByPhenologyStageIdAndSoilConfigIdAndPropertyIdentifierAndIsActiveTrue(
            Long stageId, Long soilId, String propertyIdentifier);

    List<StageThreshold> findByPhenologyStageIdAndSoilConfigIsNullAndPropertyIdentifierAndIsActiveTrue(
            Long stageId, String propertyIdentifier);

    List<StageThreshold> findByPhenologyStageIdAndThresholdTypeAndIsActiveTrue(Long stageId, String thresholdType);
}
