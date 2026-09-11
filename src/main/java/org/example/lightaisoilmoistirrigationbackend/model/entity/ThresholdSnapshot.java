package org.example.lightaisoilmoistirrigationbackend.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "threshold_snapshot")
public class ThresholdSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stage_threshold_id", nullable = false)
    private Long stageThresholdId;

    @Column(precision = 10, scale = 3)
    private BigDecimal oldValue;

    @Column(precision = 10, scale = 3)
    private BigDecimal newValue;

    @Column(length = 512)
    private String changeReason;

    @Column(length = 32)
    private String changeSource;

    @Column(length = 64)
    private String operator;

    @Column(nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStageThresholdId() { return stageThresholdId; }
    public void setStageThresholdId(Long stageThresholdId) { this.stageThresholdId = stageThresholdId; }

    public BigDecimal getOldValue() { return oldValue; }
    public void setOldValue(BigDecimal oldValue) { this.oldValue = oldValue; }

    public BigDecimal getNewValue() { return newValue; }
    public void setNewValue(BigDecimal newValue) { this.newValue = newValue; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public String getChangeSource() { return changeSource; }
    public void setChangeSource(String changeSource) { this.changeSource = changeSource; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}