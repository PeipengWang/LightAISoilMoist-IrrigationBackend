package org.example.lightaisoilmoistirrigationbackend.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "crop_config")
public class CropConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String cropName;

    @Column(nullable = false, length = 64)
    private String variety;

    private Integer treeAge;

    @Column(length = 32)
    private String irrigationMethod;

    @Column(nullable = false)
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCropName() { return cropName; }
    public void setCropName(String cropName) { this.cropName = cropName; }

    public String getVariety() { return variety; }
    public void setVariety(String variety) { this.variety = variety; }

    public Integer getTreeAge() { return treeAge; }
    public void setTreeAge(Integer treeAge) { this.treeAge = treeAge; }

    public String getIrrigationMethod() { return irrigationMethod; }
    public void setIrrigationMethod(String irrigationMethod) { this.irrigationMethod = irrigationMethod; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}