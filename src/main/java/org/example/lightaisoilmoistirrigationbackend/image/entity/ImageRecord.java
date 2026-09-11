package org.example.lightaisoilmoistirrigationbackend.image.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 图片主记录实体（阶段二已落库完整元数据）。
 * A 类基础文件 + B 类采集 EXIF + C 类田块领域上下文 + D 类分析就绪，均自动建列。
 * 图片二进制不入库，仅存元信息与落盘相对路径。
 */
@Entity
@Table(name = "image_record")
public class ImageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== A 类 · 基础文件元数据（系统生成） =====
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 255)
    private String storedName;

    @Column(name = "relative_path", nullable = false, length = 512)
    private String relativePath;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(nullable = false)
    private Long size = 0L;

    @Column(name = "storage_type", length = 16)
    private String storageType = "LOCAL";

    @Column(length = 512)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ===== B 类 · 图像采集元数据（EXIF 自动提取，可空） =====
    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "format", length = 16)
    private String format;

    @Column(name = "camera_make", length = 128)
    private String cameraMake;

    @Column(name = "camera_model", length = 128)
    private String cameraModel;

    @Column(name = "date_taken")
    private LocalDateTime dateTaken;

    @Column(name = "gps_latitude")
    private Double gpsLatitude;

    @Column(name = "gps_longitude")
    private Double gpsLongitude;

    @Column(name = "gps_altitude")
    private Double gpsAltitude;

    // ===== C 类 · 田块领域上下文（上传时提供，可空） =====
    @Column(name = "plot_code", length = 64)
    private String plotCode;

    @Column(name = "crop_type", length = 64)
    private String cropType;

    @Column(name = "soil_type", length = 64)
    private String soilType;

    @Column(name = "growth_stage", length = 64)
    private String growthStage;

    @Column(name = "irrigation_status", length = 16)
    private String irrigationStatus;

    @Column(name = "sampling_depth", length = 64)
    private String samplingDepth;

    @Column(name = "capture_condition", length = 128)
    private String captureCondition;

    @Column(name = "operator", length = 64)
    private String operator;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    // ===== D 类 · 分析就绪/结果（阶段四填充） =====
    @Column(name = "analyzed")
    private Boolean analyzed = false;

    @Column(name = "analysis_status", length = 16)
    private String analysisStatus;

    // ===== getters / setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getCameraMake() { return cameraMake; }
    public void setCameraMake(String cameraMake) { this.cameraMake = cameraMake; }

    public String getCameraModel() { return cameraModel; }
    public void setCameraModel(String cameraModel) { this.cameraModel = cameraModel; }

    public LocalDateTime getDateTaken() { return dateTaken; }
    public void setDateTaken(LocalDateTime dateTaken) { this.dateTaken = dateTaken; }

    public Double getGpsLatitude() { return gpsLatitude; }
    public void setGpsLatitude(Double gpsLatitude) { this.gpsLatitude = gpsLatitude; }

    public Double getGpsLongitude() { return gpsLongitude; }
    public void setGpsLongitude(Double gpsLongitude) { this.gpsLongitude = gpsLongitude; }

    public Double getGpsAltitude() { return gpsAltitude; }
    public void setGpsAltitude(Double gpsAltitude) { this.gpsAltitude = gpsAltitude; }

    public String getPlotCode() { return plotCode; }
    public void setPlotCode(String plotCode) { this.plotCode = plotCode; }

    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }

    public String getSoilType() { return soilType; }
    public void setSoilType(String soilType) { this.soilType = soilType; }

    public String getGrowthStage() { return growthStage; }
    public void setGrowthStage(String growthStage) { this.growthStage = growthStage; }

    public String getIrrigationStatus() { return irrigationStatus; }
    public void setIrrigationStatus(String irrigationStatus) { this.irrigationStatus = irrigationStatus; }

    public String getSamplingDepth() { return samplingDepth; }
    public void setSamplingDepth(String samplingDepth) { this.samplingDepth = samplingDepth; }

    public String getCaptureCondition() { return captureCondition; }
    public void setCaptureCondition(String captureCondition) { this.captureCondition = captureCondition; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Boolean getAnalyzed() { return analyzed; }
    public void setAnalyzed(Boolean analyzed) { this.analyzed = analyzed; }

    public String getAnalysisStatus() { return analysisStatus; }
    public void setAnalysisStatus(String analysisStatus) { this.analysisStatus = analysisStatus; }
}
