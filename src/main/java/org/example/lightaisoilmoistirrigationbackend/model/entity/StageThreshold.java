package org.example.lightaisoilmoistirrigationbackend.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stage_threshold", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"phenology_stage_id", "soil_config_id", "property_identifier", "threshold_type"})
})
public class StageThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phenology_stage_id", nullable = false)
    private PhenologyStage phenologyStage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soil_config_id")
    private SoilConfig soilConfig;

    @Column(nullable = false, length = 16)
    private String propertyIdentifier;

    @Column(nullable = false, length = 16)
    private String thresholdType;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal thresholdValue;

    @Column(length = 16)
    private String unit;

    @Column(nullable = false)
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PhenologyStage getPhenologyStage() { return phenologyStage; }
    public void setPhenologyStage(PhenologyStage phenologyStage) { this.phenologyStage = phenologyStage; }

    public SoilConfig getSoilConfig() { return soilConfig; }
    public void setSoilConfig(SoilConfig soilConfig) { this.soilConfig = soilConfig; }

    public String getPropertyIdentifier() { return propertyIdentifier; }
    public void setPropertyIdentifier(String propertyIdentifier) { this.propertyIdentifier = propertyIdentifier; }

    public String getThresholdType() { return thresholdType; }
    public void setThresholdType(String thresholdType) { this.thresholdType = thresholdType; }

    public BigDecimal getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(BigDecimal thresholdValue) { this.thresholdValue = thresholdValue; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}