package org.example.lightaisoilmoistirrigationbackend.service;

import org.example.lightaisoilmoistirrigationbackend.model.entity.*;
import org.example.lightaisoilmoistirrigationbackend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ThresholdService {

    private static final Logger log = LoggerFactory.getLogger(ThresholdService.class);

    private final StageThresholdRepository thresholdRepo;
    private final ThresholdSnapshotRepository snapshotRepo;
    private final PhenologyService phenologyService;
    private final PhenologyStageRepository phenologyStageRepo;
    private final SoilConfigRepository soilConfigRepo;
    private final CropConfigRepository cropConfigRepo;

    public ThresholdService(StageThresholdRepository thresholdRepo,
                             ThresholdSnapshotRepository snapshotRepo,
                             PhenologyService phenologyService,
                             PhenologyStageRepository phenologyStageRepo,
                             SoilConfigRepository soilConfigRepo,
                             CropConfigRepository cropConfigRepo) {
        this.thresholdRepo = thresholdRepo;
        this.snapshotRepo = snapshotRepo;
        this.phenologyService = phenologyService;
        this.phenologyStageRepo = phenologyStageRepo;
        this.soilConfigRepo = soilConfigRepo;
        this.cropConfigRepo = cropConfigRepo;
    }

    /**
     * 查询当前物候期下生效的全部阈值。
     * 对每个属性，先查"物候期 + 指定土壤"的阈值，找不到则用"物候期 + 通用土壤(soilConfig=null)"兜底。
     * 湿度类阈值（B、D）会应用土壤修正系数。
     *
     * @param soilType 土壤类型（如 LOAM、SAND），null 则默认 LOAM
     * @return { propertyIdentifier → { thresholdType → effectiveValue } }
     */
    public Map<String, Map<String, BigDecimal>> getCurrentThresholds(String soilType) {
        PhenologyStage stage = phenologyService.determineCurrentStage();
        if (stage == null) {
            return Collections.emptyMap();
        }

        SoilConfig soil = null;
        String type = (soilType != null) ? soilType : "LOAM";
        Optional<SoilConfig> soilOpt = soilConfigRepo.findBySoilType(type);
        if (soilOpt.isPresent()) {
            soil = soilOpt.get();
        }

        List<StageThreshold> all = thresholdRepo.findByPhenologyStageIdAndIsActiveTrue(stage.getId());

        // 按属性分组: prop → {soilSpecifics, generals}
        Map<String, List<StageThreshold>> propMap = new LinkedHashMap<>();
        for (StageThreshold t : all) {
            propMap.computeIfAbsent(t.getPropertyIdentifier(), k -> new ArrayList<>()).add(t);
        }

        Map<String, Map<String, BigDecimal>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<StageThreshold>> entry : propMap.entrySet()) {
            String prop = entry.getKey();
            List<StageThreshold> list = entry.getValue();

            // 优先匹配指定土壤类型，否则用通用（soilConfig=null）
            StageThreshold matched = null;
            StageThreshold general = null;
            for (StageThreshold t : list) {
                if (soil != null && t.getSoilConfig() != null
                        && t.getSoilConfig().getId().equals(soil.getId())) {
                    matched = t;
                }
                if (t.getSoilConfig() == null) {
                    general = t;
                }
            }

            // 对每个 thresholdType 取值
            Map<String, BigDecimal> effective = new LinkedHashMap<>();
            for (StageThreshold t : list) {
                StageThreshold source = (t.getSoilConfig() != null && soil != null
                        && t.getSoilConfig().getId().equals(soil.getId())) ? matched : general;
                if (source == null) continue;

                // 找到对应的 thresholdType
                StageThreshold typeMatch = list.stream()
                        .filter(x -> x.getThresholdType().equals(t.getThresholdType())
                                && (x.getSoilConfig() == source.getSoilConfig()
                                    || (x.getSoilConfig() == null && source.getSoilConfig() == null)))
                        .findFirst().orElse(null);
                if (typeMatch == null) continue;

                BigDecimal value = applySoilCorrection(typeMatch, soil);
                effective.put(t.getThresholdType(), value);
            }

            if (!effective.isEmpty()) {
                result.put(prop, effective);
            }
        }

        return result;
    }

    /**
     * 查询当前完整阈值信息（含元数据，供Controller返回）。
     */
    public Map<String, Object> getCurrentThresholdsWithMeta(String soilType) {
        PhenologyStage stage = phenologyService.determineCurrentStage();
        if (stage == null) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, BigDecimal>> rawThresholds = getCurrentThresholds(soilType);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stageName", stage.getStageName());
        result.put("stageOrder", stage.getStageOrder());
        result.put("soilType", soilType != null ? soilType : "LOAM");
        result.put("description", stage.getDescription());

        List<Map<String, Object>> thresholdList = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> entry : rawThresholds.entrySet()) {
            String prop = entry.getKey();
            Map<String, BigDecimal> types = entry.getValue();
            for (Map.Entry<String, BigDecimal> typeEntry : types.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("propertyIdentifier", prop);
                item.put("propertyName", resolvePropertyName(prop));
                item.put("thresholdType", typeEntry.getKey());
                item.put("thresholdTypeName", resolveThresholdTypeName(typeEntry.getKey()));
                item.put("value", typeEntry.getValue());
                thresholdList.add(item);
            }
        }
        result.put("thresholds", thresholdList);

        return result;
    }

    /**
     * 查某物候期的所有阈值。
     */
    public List<StageThreshold> getStageThresholds(Long stageId) {
        return thresholdRepo.findByPhenologyStageIdAndIsActiveTrue(stageId);
    }

    /**
     * 更新阈值并记录快照。
     */
    @Transactional
    public StageThreshold updateThreshold(Long id, BigDecimal newValue, String reason, String operator) {
        StageThreshold t = thresholdRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("阈值不存在: id=" + id));

        BigDecimal oldValue = t.getThresholdValue();
        t.setThresholdValue(newValue);
        t.setUpdatedAt(LocalDateTime.now());
        thresholdRepo.save(t);

        ThresholdSnapshot snap = new ThresholdSnapshot();
        snap.setStageThresholdId(id);
        snap.setOldValue(oldValue);
        snap.setNewValue(newValue);
        snap.setChangeReason(reason);
        snap.setChangeSource("MANUAL");
        snap.setOperator(operator);
        snapshotRepo.save(snap);

        log.info("阈值已更新: id={}, {}→{}, reason={}, operator={}", id, oldValue, newValue, reason, operator);
        return t;
    }

    /**
     * 查询阈值变更历史。
     */
    public List<ThresholdSnapshot> getHistory(Long thresholdId) {
        return snapshotRepo.findByStageThresholdIdOrderByChangedAtDesc(thresholdId);
    }

    /**
     * 查询所有物候期。
     */
    public List<PhenologyStage> getStages() {
        Optional<CropConfig> cropOpt = cropConfigRepo.findByIsActiveTrue();
        if (cropOpt.isEmpty()) {
            return Collections.emptyList();
        }
        return phenologyStageRepo.findByCropConfigIdOrderByStageOrderAsc(cropOpt.get().getId());
    }

    /**
     * 对湿度类阈值应用土壤修正系数。
     */
    private BigDecimal applySoilCorrection(StageThreshold threshold, SoilConfig soil) {
        if (soil == null || soil.getMoistureCorrectionFactor() == null) {
            return threshold.getThresholdValue();
        }
        if ("B".equals(threshold.getPropertyIdentifier()) || "D".equals(threshold.getPropertyIdentifier())) {
            return threshold.getThresholdValue()
                    .multiply(soil.getMoistureCorrectionFactor())
                    .setScale(1, RoundingMode.HALF_UP);
        }
        return threshold.getThresholdValue();
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
