package org.example.lightaisoilmoistirrigationbackend.service;

import org.example.lightaisoilmoistirrigationbackend.model.entity.CropConfig;
import org.example.lightaisoilmoistirrigationbackend.model.entity.PhenologyStage;
import org.example.lightaisoilmoistirrigationbackend.repository.CropConfigRepository;
import org.example.lightaisoilmoistirrigationbackend.repository.PhenologyStageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PhenologyService {

    private static final Logger log = LoggerFactory.getLogger(PhenologyService.class);

    private final PhenologyStageRepository phenologyStageRepo;
    private final CropConfigRepository cropConfigRepo;

    /** 手动覆盖的物候期: cropId → stageId */
    private final Map<Long, Long> manualOverride = new ConcurrentHashMap<>();

    public PhenologyService(PhenologyStageRepository phenologyStageRepo,
                             CropConfigRepository cropConfigRepo) {
        this.phenologyStageRepo = phenologyStageRepo;
        this.cropConfigRepo = cropConfigRepo;
    }

    /**
     * 判断当前物候期。
     * 优先级：手动指定 > 积温计算 > 按月份判断
     */
    public PhenologyStage determineCurrentStage() {
        Optional<CropConfig> cropOpt = cropConfigRepo.findByIsActiveTrue();
        if (cropOpt.isEmpty()) {
            log.warn("未找到激活的作物配置");
            return null;
        }
        CropConfig crop = cropOpt.get();

        // 1. 手动覆盖
        Long overrideStageId = manualOverride.get(crop.getId());
        if (overrideStageId != null) {
            Optional<PhenologyStage> stageOpt = phenologyStageRepo.findById(overrideStageId);
            if (stageOpt.isPresent()) {
                log.debug("物候期由手动指定: {}", stageOpt.get().getStageName());
                return stageOpt.get();
            }
        }

        // 2. 按月份判断（一期实现，二期替换为积温计算）
        int month = LocalDate.now().getMonthValue();
        Optional<PhenologyStage> stageOpt = phenologyStageRepo.findByCropIdAndMonth(crop.getId(), month);
        if (stageOpt.isPresent()) {
            return stageOpt.get();
        }

        log.warn("无法判断当前物候期，月份={}", month);
        return null;
    }

    /**
     * 手动指定当前物候期。
     * @return 被覆盖的物候期
     */
    public PhenologyStage overrideStage(Long cropId, Long stageId) {
        manualOverride.put(cropId, stageId);
        log.info("物候期已手动覆盖: cropId={}, stageId={}", cropId, stageId);
        return phenologyStageRepo.findById(stageId).orElse(null);
    }

    /**
     * 取消手动指定，恢复自动判断。
     * @return true 表示成功取消，false 表示之前没有手动覆盖
     */
    public boolean clearOverride(Long cropId) {
        Long removed = manualOverride.remove(cropId);
        if (removed != null) {
            log.info("物候期手动覆盖已取消: cropId={}", cropId);
        }
        return removed != null;
    }

    /**
     * 检查当前是否有手动覆盖。
     */
    public boolean isOverridden(Long cropId) {
        return manualOverride.containsKey(cropId);
    }
}
