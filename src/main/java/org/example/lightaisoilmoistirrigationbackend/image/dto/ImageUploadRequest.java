package org.example.lightaisoilmoistirrigationbackend.image.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传请求（阶段二增强）：在文件基础上携带田块领域上下文（C 类）。
 * 所有领域字段均为可选，由采集端/前端在上传时一并带来；缺失则留空。
 */
public class ImageUploadRequest {

    private MultipartFile file;
    private Long folderId;
    private String remark;

    // C 类 · 田块领域上下文（可选）
    private String plotCode;
    private String cropType;
    private String soilType;
    private String growthStage;
    private String irrigationStatus;
    private String samplingDepth;
    private String captureCondition;
    private String operator;
    private String deviceId;

    public MultipartFile getFile() { return file; }
    public void setFile(MultipartFile file) { this.file = file; }

    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

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
}
