package org.example.lightaisoilmoistirrigationbackend.image.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.lightaisoilmoistirrigationbackend.image.entity.ImageFolder;
import org.example.lightaisoilmoistirrigationbackend.image.service.ImageService;
import org.example.lightaisoilmoistirrigationbackend.model.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 图片目录(相册)管理接口：新建、列表、删除。
 */
@RestController
@RequestMapping("/api/image-folders")
@Tag(name = "图片目录", description = "目录(相册)管理：新建 / 列表 / 删除")
@Slf4j
public class ImageFolderController {

    private final ImageService imageService;

    public ImageFolderController(ImageService imageService) {
        this.imageService = imageService;
    }

    @Operation(summary = "新建目录", description = "创建图片目录(相册)，可指定父目录(parentId)形成树形结构")
    @PostMapping
    public Result<ImageFolder> create(
            @Parameter(description = "目录名", required = true) @RequestParam String name,
            @Parameter(description = "父目录ID，不传则为根目录") @RequestParam(required = false) Long parentId) {
        try {
            return Result.success("创建成功", imageService.createFolder(name, parentId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "目录列表", description = "不传 parentId 返回全部目录；传 parentId 返回其直接子目录")
    @GetMapping
    public Result<List<ImageFolder>> list(
            @Parameter(description = "父目录ID") @RequestParam(required = false) Long parentId) {
        try {
            return Result.success(imageService.listFolders(parentId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "删除目录", description = "删除目录，其下图片自动转为未分类(folderId 置空)，不删除图片文件")
    @DeleteMapping("/{id}")
    public Result<String> delete(@Parameter(description = "目录ID") @PathVariable Long id) {
        try {
            imageService.deleteFolder(id);
            return Result.success("删除成功", null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
