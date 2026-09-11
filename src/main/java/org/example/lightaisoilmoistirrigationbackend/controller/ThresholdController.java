package org.example.lightaisoilmoistirrigationbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.lightaisoilmoistirrigationbackend.model.Result;
import org.example.lightaisoilmoistirrigationbackend.model.entity.PhenologyStage;
import org.example.lightaisoilmoistirrigationbackend.model.entity.StageThreshold;
import org.example.lightaisoilmoistirrigationbackend.model.entity.ThresholdSnapshot;
import org.example.lightaisoilmoistirrigationbackend.service.PhenologyService;
import org.example.lightaisoilmoistirrigationbackend.service.ThresholdService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "阈值管理", description = "物候期阈值配置与查询")
@RestController
@RequestMapping("/api/thresholds")
public class ThresholdController {

    private final ThresholdService thresholdService;
    private final PhenologyService phenologyService;

    public ThresholdController(ThresholdService thresholdService, PhenologyService phenologyService) {
        this.thresholdService = thresholdService;
        this.phenologyService = phenologyService;
    }

    @GetMapping("/current")
    @Operation(summary = "查询当前生效阈值", description = "自动判断当前物候期，返回该阶段所有属性的阈值")
    public Result<Map<String, Object>> getCurrentThresholds(
            @Parameter(description = "土壤类型：LOAM/SAND/SANDY_LOAM/CLAY_LOAM/CLAY")
            @RequestParam(required = false, defaultValue = "LOAM") String soilType) {

        Map<String, Object> data = thresholdService.getCurrentThresholdsWithMeta(soilType);
        if (data.isEmpty()) {
            return Result.error("无法判断当前物候期或未配置阈值");
        }
        return Result.success(data);
    }

    @GetMapping("/stages")
    @Operation(summary = "查询物候期列表", description = "返回当前激活作物的所有物候期")
    public Result<List<Map<String, Object>>> getStages() {
        List<PhenologyStage> stages = thresholdService.getStages();
        List<Map<String, Object>> list = stages.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("stageName", s.getStageName());
            m.put("stageOrder", s.getStageOrder());
            m.put("typicalStartMonth", s.getTypicalStartMonth());
            m.put("typicalEndMonth", s.getTypicalEndMonth());
            m.put("gddThresholdLow", s.getGddThresholdLow());
            m.put("gddThresholdHigh", s.getGddThresholdHigh());
            m.put("description", s.getDescription());
            return m;
        }).collect(Collectors.toList());
        return Result.success(list);
    }

    @GetMapping("/stages/{stageId}")
    @Operation(summary = "查询某物候期的阈值", description = "返回指定物候期的所有阈值配置")
    public Result<List<Map<String, Object>>> getStageThresholds(@PathVariable Long stageId) {
        List<StageThreshold> list = thresholdService.getStageThresholds(stageId);
        List<Map<String, Object>> result = list.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("propertyIdentifier", t.getPropertyIdentifier());
            m.put("propertyName", resolvePropertyName(t.getPropertyIdentifier()));
            m.put("thresholdType", t.getThresholdType());
            m.put("thresholdTypeName", resolveThresholdTypeName(t.getThresholdType()));
            m.put("thresholdValue", t.getThresholdValue());
            m.put("unit", t.getUnit());
            m.put("isActive", t.getIsActive());
            m.put("soilConfigId", t.getSoilConfig() != null ? t.getSoilConfig().getId() : null);
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新阈值", description = "更新阈值数值，自动记录变更快照")
    public Result<Map<String, Object>> updateThreshold(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        BigDecimal newValue = new BigDecimal(body.get("value").toString());
        String reason = (String) body.getOrDefault("reason", "人工调整");
        String operator = (String) body.getOrDefault("operator", "admin");

        try {
            StageThreshold updated = thresholdService.updateThreshold(id, newValue, reason, operator);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", updated.getId());
            result.put("propertyIdentifier", updated.getPropertyIdentifier());
            result.put("thresholdType", updated.getThresholdType());
            result.put("thresholdValue", updated.getThresholdValue());
            result.put("unit", updated.getUnit());
            result.put("updatedAt", updated.getUpdatedAt().toString());
            return Result.success("阈值已更新", result);
        } catch (NoSuchElementException e) {
            return Result.error(404, e.getMessage());
        }
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "阈值变更历史", description = "查看指定阈值的变更历史记录")
    public Result<List<Map<String, Object>>> getHistory(@PathVariable Long id) {
        List<ThresholdSnapshot> history = thresholdService.getHistory(id);
        List<Map<String, Object>> result = history.stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("oldValue", h.getOldValue());
            m.put("newValue", h.getNewValue());
            m.put("changeReason", h.getChangeReason());
            m.put("changeSource", h.getChangeSource());
            m.put("operator", h.getOperator());
            m.put("changedAt", h.getChangedAt().toString());
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    @PostMapping("/phenology/override")
    @Operation(summary = "手动指定物候期", description = "覆盖自动判断的物候期，手动指定当前阶段")
    public Result<Map<String, Object>> overrideStage(@RequestBody Map<String, Object> body) {
        Long cropId = Long.valueOf(body.get("cropId").toString());
        Long stageId = Long.valueOf(body.get("stageId").toString());

        PhenologyStage stage = phenologyService.overrideStage(cropId, stageId);
        if (stage == null) {
            return Result.error(404, "物候期不存在: id=" + stageId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cropId", cropId);
        result.put("stageId", stageId);
        result.put("stageName", stage.getStageName());
        result.put("overridden", true);
        return Result.success("已手动指定物候期", result);
    }

    @DeleteMapping("/phenology/override")
    @Operation(summary = "取消手动指定物候期", description = "取消手动覆盖，恢复按月份自动判断物候期")
    public Result<Map<String, Object>> clearOverride(
            @Parameter(description = "作物配置ID") @RequestParam Long cropId) {

        boolean removed = phenologyService.clearOverride(cropId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cropId", cropId);
        result.put("cleared", removed);
        if (removed) {
            PhenologyStage autoStage = phenologyService.determineCurrentStage();
            result.put("currentStage", autoStage != null ? autoStage.getStageName() : "unknown");
        }
        return Result.success(removed ? "已恢复自动判断" : "之前没有手动覆盖", result);
    }

    private String resolvePropertyName(String identifier) {
        return switch (identifier) {
            case "A" -> "土壤pH值";
            case "B" -> "土壤湿度";
            case "C" -> "环境温度";
            case "D" -> "环境湿度";
            case "E" -> "光照";
            default -> identifier;
        };
    }

    private String resolveThresholdTypeName(String type) {
        return switch (type) {
            case "LOWER" -> "下限";
            case "UPPER" -> "上限";
            case "TARGET" -> "目标值";
            default -> type;
        };
    }
}
