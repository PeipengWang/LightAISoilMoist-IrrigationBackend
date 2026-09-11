package org.example.lightaisoilmoistirrigationbackend.image.service;

import java.time.LocalDateTime;

/**
 * 图片元数据值对象（阶段二提取 + 落库用）。
 * 由 MetadataExtractorService 从图片 EXIF 提取，最终持久化到 image_record。
 */
public class ImageMetadataVO {

    /** 像素宽 */
    private Integer width;
    /** 像素高 */
    private Integer height;
    /** 格式：JPEG / PNG / GIF / BMP / TIFF / WEBP / UNKNOWN */
    private String format;
    /** EXIF 相机品牌（可空） */
    private String cameraMake;
    /** EXIF 相机型号（可空） */
    private String cameraModel;
    /** EXIF 拍摄时间（可空） */
    private LocalDateTime dateTaken;
    /** 十进制纬度（EXIF GPS，可空） */
    private Double gpsLatitude;
    /** 十进制经度（EXIF GPS，可空） */
    private Double gpsLongitude;
    /** 海拔，单位米（EXIF GPS，可空；正数高于海平面） */
    private Double gpsAltitude;

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

    @Override
    public String toString() {
        return "ImageMetadataVO{width=" + width +
                ", height=" + height +
                ", format='" + format + '\'' +
                ", cameraMake='" + cameraMake + '\'' +
                ", cameraModel='" + cameraModel + '\'' +
                ", dateTaken=" + dateTaken +
                ", gpsLatitude=" + gpsLatitude +
                ", gpsLongitude=" + gpsLongitude +
                ", gpsAltitude=" + gpsAltitude + '}';
    }
}
