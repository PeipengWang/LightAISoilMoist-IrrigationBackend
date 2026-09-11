package org.example.lightaisoilmoistirrigationbackend.image.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 图片元数据提取服务（阶段二 2.1：提取并「打印」到日志，暂不落库）。
 * 依赖 metadata-extractor 解析 EXIF/GPS；尺寸解析兜底使用 ImageIO。
 * 任何单目录解析异常都只记 warn、不抛异常中断上传，保证上传链路健壮性。
 */
@Service
@Slf4j
public class MetadataExtractorService {

    /**
     * 解析图片元数据。
     *
     * @param data        图片二进制（已在内存中，避免重复读取流）
     * @param contentType 上传时的 MIME，用于格式兜底判定
     * @return 元数据值对象（部分字段可能为 null）
     */
    public ImageMetadataVO extract(byte[] data, String contentType) {
        ImageMetadataVO vo = new ImageMetadataVO();
        vo.setFormat(detectFormat(data, contentType));
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(data));

            // ---- 尺寸：EXIF -> JPEG -> ImageIO 兜底 ----
            Integer width = null, height = null;
            ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (sub != null) {
                if (sub.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)) {
                    width = sub.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
                }
                if (sub.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)) {
                    height = sub.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
                }
            }
            if (width == null || height == null) {
                JpegDirectory jpeg = metadata.getFirstDirectoryOfType(JpegDirectory.class);
                if (jpeg != null) {
                    if (width == null) width = jpeg.getImageWidth();
                    if (height == null) height = jpeg.getImageHeight();
                }
            }
            if (width == null || height == null) {
                try (ByteArrayInputStream fb = new ByteArrayInputStream(data)) {
                    BufferedImage img = ImageIO.read(fb);
                    if (img != null) {
                        if (width == null) width = img.getWidth();
                        if (height == null) height = img.getHeight();
                    }
                }
            }
            vo.setWidth(width);
            vo.setHeight(height);

            // ---- 相机品牌/型号 ----
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                if (ifd0.containsTag(ExifIFD0Directory.TAG_MAKE)) {
                    vo.setCameraMake(ifd0.getString(ExifIFD0Directory.TAG_MAKE));
                }
                if (ifd0.containsTag(ExifIFD0Directory.TAG_MODEL)) {
                    vo.setCameraModel(ifd0.getString(ExifIFD0Directory.TAG_MODEL));
                }
            }

            // ---- 拍摄时间 ----
            if (sub != null && sub.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)) {
                Date d = sub.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (d != null) {
                    vo.setDateTaken(d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                }
            }

            // ---- GPS 经纬度 + 海拔 ----
            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gps != null) {
                if (gps.getGeoLocation() != null) {
                    vo.setGpsLatitude(gps.getGeoLocation().getLatitude());
                    vo.setGpsLongitude(gps.getGeoLocation().getLongitude());
                }
                // 海拔：GPS 目录 TAG 0x0006=altitude, 0x0005=altitude ref(0=上/1=下)
                try {
                    if (gps.containsTag(0x0006)) {
                        double alt = gps.getRational(0x0006).doubleValue();
                        if (gps.containsTag(0x0005)) {
                            int ref = gps.getInteger(0x0005);
                            if (ref == 1) alt = -alt;
                        }
                        vo.setGpsAltitude(alt);
                    }
                } catch (Exception ignore) {
                    // 海拔解析失败不影响其他字段
                }
            }
        } catch (Exception e) {
            log.warn("图片元数据提取出现异常，仅返回已解析部分（format/尺寸兜底）。原因: {}", e.getMessage());
        }
        return vo;
    }

    /**
     * 按文件魔数（magic bytes）判定图片格式，失败则按 contentType 兜底。
     */
    private String detectFormat(byte[] data, String contentType) {
        if (data != null && data.length >= 4) {
            int b0 = data[0] & 0xFF, b1 = data[1] & 0xFF, b2 = data[2] & 0xFF, b3 = data[3] & 0xFF;
            if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "JPEG";
            if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return "PNG";
            if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46) return "GIF";
            if (b0 == 0x42 && b1 == 0x4D) return "BMP";
            if ((b0 == 0x49 && b1 == 0x49 && b2 == 0x2A) || (b0 == 0x4D && b1 == 0x4D && b2 == 0x00 && b3 == 0x2A)) return "TIFF";
            if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46) return "WEBP";
        }
        if (contentType != null) {
            if (contentType.contains("png")) return "PNG";
            if (contentType.contains("jpeg") || contentType.contains("jpg")) return "JPEG";
            if (contentType.contains("gif")) return "GIF";
            if (contentType.contains("bmp")) return "BMP";
            if (contentType.contains("webp")) return "WEBP";
        }
        return "UNKNOWN";
    }
}
