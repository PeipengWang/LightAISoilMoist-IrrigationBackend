package org.example.lightaisoilmoistirrigationbackend.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "soil_config")
public class SoilConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String soilType;

    @Column(precision = 5, scale = 2)
    private BigDecimal fieldCapacity;

    @Column(precision = 5, scale = 2)
    private BigDecimal wiltingPoint;

    @Column(precision = 6, scale = 2)
    private BigDecimal infiltrationRate;

    @Column(precision = 4, scale = 3)
    private BigDecimal moistureCorrectionFactor = new BigDecimal("1.000");

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSoilType() { return soilType; }
    public void setSoilType(String soilType) { this.soilType = soilType; }

    public BigDecimal getFieldCapacity() { return fieldCapacity; }
    public void setFieldCapacity(BigDecimal fieldCapacity) { this.fieldCapacity = fieldCapacity; }

    public BigDecimal getWiltingPoint() { return wiltingPoint; }
    public void setWiltingPoint(BigDecimal wiltingPoint) { this.wiltingPoint = wiltingPoint; }

    public BigDecimal getInfiltrationRate() { return infiltrationRate; }
    public void setInfiltrationRate(BigDecimal infiltrationRate) { this.infiltrationRate = infiltrationRate; }

    public BigDecimal getMoistureCorrectionFactor() { return moistureCorrectionFactor; }
    public void setMoistureCorrectionFactor(BigDecimal moistureCorrectionFactor) { this.moistureCorrectionFactor = moistureCorrectionFactor; }
}