package org.example.lightaisoilmoistirrigationbackend.image.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.lightaisoilmoistirrigationbackend.image.dto.BatchImageRequest;
import org.example.lightaisoilmoistirrigationbackend.image.dto.ImageUploadRequest;
import org.example.lightaisoilmoistirrigationbackend.image.entity.ImageRecord;
import org.example.lightaisoilmoistirrigationbackend.image.service.ImageService;
import org.example.lightaisoilmoistirrigationbackend.model.Result;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图片管理接口：上传、列表、详情、预览、删除、分类归档。
 */
@RestController
@RequestMapping("/api/images")
@Tag(name = "图片管理", description = "图片上传、列表、详情、预览/下载、删除、分类归档")
@Slf4j
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @Operation(summary = "上传图片", description = "上传图片到本地目录，可指定目录/备注/田块领域上下文")
    @PostMapping("/upload")
    public Result<ImageRecord> upload(
            @Parameter(description = "图片上传请求（文件 + 可选田块领域字段）") @ModelAttribute ImageUploadRequest req) {
        try {
            ImageRecord record = imageService.upload(req);
            return Result.success("上传成功", record);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            log.error("图片上传失败", e);
            return Result.error(500, e.getMessage());
        }
    }

    @Operation(summary = "图片列表", description = "分页查询，可按目录(folderId)过滤")
    @GetMapping
    public Result<Map<String, Object>> list(
            @Parameter(description = "目录ID，不传则返回全部") @RequestParam(required = false) Long folderId,
            @Parameter(description = "页码(0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数(1-100)") @RequestParam(defaultValue = "20") int size) {
        Page<ImageRecord> result = imageService.list(folderId, page, size);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", result.getContent());
        resp.put("page", result.getNumber());
        resp.put("size", result.getSize());
        resp.put("totalElements", result.getTotalElements());
        resp.put("totalPages", result.getTotalPages());
        return Result.success(resp);
    }

    @Operation(summary = "图片详情", description = "根据ID查询图片记录")
    @GetMapping("/{id}")
    public Result<ImageRecord> detail(@Parameter(description = "图片ID") @PathVariable Long id) {
        try {
            return Result.success(imageService.getById(id));
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }

    @Operation(summary = "预览/下载图片", description = "返回图片二进制，浏览器可直接预览(inline)")
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@Parameter(description = "图片ID") @PathVariable Long id) {
        try {
            ImageRecord record = imageService.getById(id);
            Resource resource = imageService.serveFile(id);
            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(record.getContentType());
            } catch (Exception e) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + record.getStoredName() + "\"")
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
    }

    @Operation(summary = "删除图片", description = "彻底删除图片：删除数据库记录、关联分析结论与磁盘文件")
    @DeleteMapping("/{id}")
    public Result<String> delete(@Parameter(description = "图片ID") @PathVariable Long id) {
        try {
            imageService.deleteImage(id);
            return Result.success("删除成功", null);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }

    @Operation(summary = "批量删除图片", description = "批量彻底删除图片，body 传 {\"ids\":[...]}")
    @PostMapping("/batch-delete")
    public Result<Integer> batchDelete(
            @Parameter(description = "图片ID集合与可选目标目录") @RequestBody BatchImageRequest req) {
        try {
            int n = imageService.deleteImages(req.getIds());
            return Result.success("已删除 " + n + " 张图片", n);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "图片分类归档(单张)", description = "修改图片所属目录；folderId 为空表示移出到未分类")
    @PutMapping("/{id}/folder")
    public Result<ImageRecord> archive(
            @Parameter(description = "图片ID") @PathVariable Long id,
            @Parameter(description = "目标目录ID，为空表示移出到未分类") @RequestParam(required = false) Long folderId) {
        try {
            return Result.success("归档成功", imageService.archiveImage(id, folderId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "图片批量归档", description = "将一批图片归入指定目录，body 传 {\"ids\":[...],\"folderId\":目录ID}")
    @PostMapping("/batch-move")
    public Result<Integer> batchMove(
            @Parameter(description = "图片ID集合与目标目录") @RequestBody BatchImageRequest req) {
        try {
            int n = imageService.archiveImages(req.getIds(), req.getFolderId());
            return Result.success("已归档 " + n + " 张图片", n);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
