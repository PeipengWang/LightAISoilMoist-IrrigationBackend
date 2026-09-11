package org.example.lightaisoilmoistirrigationbackend.repository;

import org.example.lightaisoilmoistirrigationbackend.model.entity.PhenologyStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PhenologyStageRepository extends JpaRepository<PhenologyStage, Long> {

    List<PhenologyStage> findByCropConfigIdOrderByStageOrderAsc(Long cropConfigId);

    @Query("SELECT p FROM PhenologyStage p WHERE p.cropConfig.id = :cropId " +
           "AND ((p.typicalStartMonth <= p.typicalEndMonth AND :month BETWEEN p.typicalStartMonth AND p.typicalEndMonth) " +
           "OR (p.typicalStartMonth > p.typicalEndMonth AND (:month >= p.typicalStartMonth OR :month <= p.typicalEndMonth)))")
    Optional<PhenologyStage> findByCropIdAndMonth(@Param("cropId") Long cropId, @Param("month") int month);

    Optional<PhenologyStage> findByCropConfigIdAndGddThresholdLowLessThanEqualAndGddThresholdHighGreaterThanEqual(
            Long cropConfigId, BigDecimal gddLow, BigDecimal gddHigh);
}
