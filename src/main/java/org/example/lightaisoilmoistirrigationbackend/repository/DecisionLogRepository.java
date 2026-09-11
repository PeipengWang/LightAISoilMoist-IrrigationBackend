package org.example.lightaisoilmoistirrigationbackend.repository;

import org.example.lightaisoilmoistirrigationbackend.model.entity.DecisionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface DecisionLogRepository extends JpaRepository<DecisionLog, Long>,
        JpaSpecificationExecutor<DecisionLog> {

    Page<DecisionLog> findByDeviceNameOrderByCreatedAtDesc(String deviceName, Pageable pageable);

    Page<DecisionLog> findByDecisionTypeOrderByCreatedAtDesc(String decisionType, Pageable pageable);

    Page<DecisionLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    long countByDecisionType(String decisionType);

    long countBySuccessTrue();

    long countByDeviceName(String deviceName);
}
