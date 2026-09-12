package org.example.lightaisoilmoistirrigationbackend.image.service;

import lombok.extern.slf4j.Slf4j;
import org.example.lightaisoilmoistirrigationbackend.image.dto.ImageUploadRequest;
import org.example.lightaisoilmoistirrigationbackend.image.entity.ImageFolder;
import org.example.lightaisoilmoistirrigationbackend.image.entity.ImageRecord;
import org.example.lightaisoilmoistirrigationbackend.image.repository.ImageAnalysisRepository;
import org.example.lightaisoilmoistirrigationbackend.image.repository.ImageFolderRepository;
import org.example.lightaisoilmoistirrigationbackend.image.repository.ImageRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 图片管理核心服务：上传落盘、目录管理、列表/预览。
 * 阶段一：图片存储到本地磁盘（app.upload.root-dir），基础信息写入 image_record。
 * 阶段二：上传后提取 EXIF 元数据并填充田块领域上下文，统一持久化到 image_record（含 A/B/C/D 全类字段）。
 */
@Service
@Slf4j
public class ImageService {

    private final ImageRecordRepository recordRepository;
    private final ImageFolderRepository folderRepository;
    private final ImageAnalysisRepository analysisRepository;
    private final MetadataExtractorService metadataExtractor;
    private final Path rootDir;

    public ImageService(ImageRecordRepository recordRepository,
                        ImageFolderRepository folderRepository,
                        ImageAnalysisRepository analysisRepository,
                        MetadataExtractorService metadataExtractor,
                        @Value("${app.upload.root-dir:./data/images}") String rootDir) {
        this.recordRepository = recordRepository;
        this.folderRepository = folderRepository;
        this.analysisRepository = analysisRepository;
        this.metadataExtractor = metadataExtractor;
        this.rootDir = Paths.get(rootDir);
        try {
            Files.createDirectories(this.rootDir);
            log.info("图片上传根目录已就绪: {}", this.rootDir.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传根目录: " + this.rootDir, e);
        }
    }

    // ===================== 上传 =====================

    public ImageRecord upload(ImageUploadRequest req) {
        MultipartFile file = req.getFile();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("仅支持图片文件，收到类型: " + contentType);
        }
        Long folderId = req.getFolderId();
        if (folderId != null && !folderRepository.existsById(folderId)) {
            throw new IllegalArgumentException("目录不存在: " + folderId);
        }

        String originalName = file.getOriginalFilename();
        String ext = extractExt(originalName);
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        Path targetDir = rootDir.resolve(yearMonth);
        Path targetFile = targetDir.resolve(storedName);

        byte[] bytes;
        try {
            bytes = file.getBytes();
            Files.createDirectories(targetDir);
            Files.write(targetFile, bytes,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("文件写入失败: " + targetFile, e);
        }

        ImageRecord imageRecord = new ImageRecord();
        // A 类基础信息
        imageRecord.setOriginalName(originalName);
        imageRecord.setStoredName(storedName);
        imageRecord.setRelativePath(yearMonth + "/" + storedName);
        imageRecord.setFolderId(folderId);
        imageRecord.setContentType(contentType);
        imageRecord.setSize(file.getSize());
        imageRecord.setStorageType("LOCAL");
        imageRecord.setRemark(req.getRemark());
        imageRecord.setCreatedAt(LocalDateTime.now());

        // C 类田块领域上下文（可选）
        imageRecord.setPlotCode(req.getPlotCode());
        imageRecord.setCropType(req.getCropType());
        imageRecord.setSoilType(req.getSoilType());
        imageRecord.setGrowthStage(req.getGrowthStage());
        imageRecord.setIrrigationStatus(req.getIrrigationStatus());
        imageRecord.setSamplingDepth(req.getSamplingDepth());
        imageRecord.setCaptureCondition(req.getCaptureCondition());
        imageRecord.setOperator(req.getOperator());
        imageRecord.setDeviceId(req.getDeviceId());

        // D 类分析就绪
        imageRecord.setAnalyzed(false);
        imageRecord.setAnalysisStatus(null);

        // B 类采集元数据（EXIF 提取，内部已容错，部分字段可能为 null）
        ImageMetadataVO meta = metadataExtractor.extract(bytes, contentType);
        imageRecord.setWidth(meta.getWidth());
        imageRecord.setHeight(meta.getHeight());
        imageRecord.setFormat(meta.getFormat());
        imageRecord.setCameraMake(meta.getCameraMake());
        imageRecord.setCameraModel(meta.getCameraModel());
        imageRecord.setDateTaken(meta.getDateTaken());
        imageRecord.setGpsLatitude(meta.getGpsLatitude());
        imageRecord.setGpsLongitude(meta.getGpsLongitude());
        imageRecord.setGpsAltitude(meta.getGpsAltitude());

        imageRecord = recordRepository.save(imageRecord);
        log.info("【图片元数据】id={} originalName={} -> {}", imageRecord.getId(), originalName, meta);
        return imageRecord;
    }

    // ===================== 查询 =====================

    public ImageRecord getById(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("图片不存在: " + id));
    }

    public Page<ImageRecord> list(Long folderId, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (folderId != null) {
            return recordRepository.findByFolderId(folderId, pageable);
        }
        return recordRepository.findAll(pageable);
    }

    public Resource serveFile(Long id) {
        ImageRecord imageRecord = getById(id);
        Path file = rootDir.resolve(imageRecord.getRelativePath());
        if (!Files.exists(file)) {
            throw new IllegalStateException("文件已丢失: " + file);
        }
        return new FileSystemResource(file);
    }

    /**
     * 读取图片文件二进制（供阶段四调用 AI 智能体使用）。
     * 文件本体在本地磁盘（阶段三若切换到 MinIO，此处需改为从 MinIO 拉取）。
     */
    public byte[] readBytes(Long id) {
        ImageRecord imageRecord = getById(id);
        Path file = rootDir.resolve(imageRecord.getRelativePath());
        if (!Files.exists(file)) {
            throw new IllegalStateException("图片文件已丢失: " + file);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("读取图片文件失败: " + file, e);
        }
    }

    // ===================== 目录管理 =====================

    public List<ImageFolder> listFolders(Long parentId) {
        if (parentId != null) {
            if (!folderRepository.existsById(parentId)) {
                throw new IllegalArgumentException("父目录不存在: " + parentId);
            }
            return folderRepository.findByParentId(parentId);
        }
        return folderRepository.findAll();
    }

    public ImageFolder createFolder(String name, Long parentId) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("目录名不能为空");
        }
        if (parentId != null && !folderRepository.existsById(parentId)) {
            throw new IllegalArgumentException("父目录不存在: " + parentId);
        }
        ImageFolder folder = new ImageFolder();
        folder.setName(name.trim());
        folder.setParentId(parentId);
        folder.setCreatedAt(LocalDateTime.now());
        return folderRepository.save(folder);
    }

    public void deleteFolder(Long id) {
        if (!folderRepository.existsById(id)) {
            throw new IllegalArgumentException("目录不存在: " + id);
        }
        // 其下图片 folderId 置空（变为未分类），不删除图片文件
        List<ImageRecord> records = recordRepository.findByFolderId(id);
        for (ImageRecord r : records) {
            r.setFolderId(null);
        }
        recordRepository.saveAll(records);
        folderRepository.deleteById(id);
        log.info("删除目录 {}，其下 {} 张图片已转为未分类", id, records.size());
    }

    // ===================== 删除与归档 =====================

    /**
     * 删除单张图片：级联删除磁盘文件与关联分析结论。
     * 返回实际删除数量（1）。
     */
    public int deleteImage(Long id) {
        ImageRecord record = getById(id);
        deleteRecord(record);
        return 1;
    }

    /**
     * 批量删除图片，返回实际删除数量；不存在的 id 自动忽略。
     */
    public int deleteImages(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        int count = 0;
        for (Long id : ids) {
            ImageRecord record = recordRepository.findById(id).orElse(null);
            if (record == null) {
                log.warn("批量删除跳过不存在的图片: {}", id);
                continue;
            }
            deleteRecord(record);
            count++;
        }
        log.info("批量删除图片 {} 张", count);
        return count;
    }

    /**
     * 单张分类归档：设置/清除所属目录。
     * folderId 为 null 表示移出目录回到未分类。
     */
    public ImageRecord archiveImage(Long id, Long folderId) {
        ImageRecord record = getById(id);
        applyFolder(record, folderId);
        return recordRepository.save(record);
    }

    /**
     * 批量归档到指定目录，返回实际处理数量；不存在的 id 自动忽略。
     */
    public int archiveImages(List<Long> ids, Long folderId) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        if (folderId != null && !folderRepository.existsById(folderId)) {
            throw new IllegalArgumentException("目录不存在: " + folderId);
        }
        int count = 0;
        for (Long id : ids) {
            ImageRecord record = recordRepository.findById(id).orElse(null);
            if (record == null) {
                log.warn("批量归档跳过不存在的图片: {}", id);
                continue;
            }
            record.setFolderId(folderId);
            recordRepository.save(record);
            count++;
        }
        log.info("批量归档图片 {} 张 -> folderId={}", count, folderId);
        return count;
    }

    // ---- 内部工具 ----

    private void applyFolder(ImageRecord record, Long folderId) {
        if (folderId != null && !folderRepository.existsById(folderId)) {
            throw new IllegalArgumentException("目录不存在: " + folderId);
        }
        record.setFolderId(folderId);
    }

    /** 彻底删除一张图片：先删关联分析、再删磁盘文件、最后删数据库记录 */
    private void deleteRecord(ImageRecord record) {
        analysisRepository.deleteByImageId(record.getId());
        Path file = rootDir.resolve(record.getRelativePath());
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("删除图片文件失败(已忽略): {} -> {}", record.getId(), file, e);
        }
        recordRepository.delete(record);
        log.info("已删除图片 id={} path={}", record.getId(), record.getRelativePath());
    }

    // ===================== 工具 =====================

    private String extractExt(String originalName) {
        if (originalName != null && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
            // 限制扩展名长度，防止异常文件名
            if (ext.length() <= 10) {
                return ext;
            }
        }
        return "";
    }
}
