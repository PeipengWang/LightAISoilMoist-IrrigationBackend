package org.example.lightaisoilmoistirrigationbackend.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "phenology_stage")
public class PhenologyStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_config_id", nullable = false)
    private CropConfig cropConfig;

    @Column(nullable = false)
    private Integer stageOrder;

    @Column(nullable = false, length = 32)
    private String stageName;

    private Integer typicalStartMonth;
    private Integer typicalEndMonth;

    @Column(precision = 8, scale = 1)
    private BigDecimal gddThresholdLow;

    @Column(precision = 8, scale = 1)
    private BigDecimal gddThresholdHigh;

    @Column(length = 255)
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CropConfig getCropConfig() { return cropConfig; }
    public void setCropConfig(CropConfig cropConfig) { this.cropConfig = cropConfig; }

    public Integer getStageOrder() { return stageOrder; }
    public void setStageOrder(Integer stageOrder) { this.stageOrder = stageOrder; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public Integer getTypicalStartMonth() { return typicalStartMonth; }
    public void setTypicalStartMonth(Integer typicalStartMonth) { this.typicalStartMonth = typicalStartMonth; }

    public Integer getTypicalEndMonth() { return typicalEndMonth; }
    public void setTypicalEndMonth(Integer typicalEndMonth) { this.typicalEndMonth = typicalEndMonth; }

    public BigDecimal getGddThresholdLow() { return gddThresholdLow; }
    public void setGddThresholdLow(BigDecimal gddThresholdLow) { this.gddThresholdLow = gddThresholdLow; }

    public BigDecimal getGddThresholdHigh() { return gddThresholdHigh; }
    public void setGddThresholdHigh(BigDecimal gddThresholdHigh) { this.gddThresholdHigh = gddThresholdHigh; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}