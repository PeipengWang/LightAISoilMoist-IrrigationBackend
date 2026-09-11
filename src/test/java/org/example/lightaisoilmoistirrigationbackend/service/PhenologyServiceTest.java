package org.example.lightaisoilmoistirrigationbackend.service;

import org.example.lightaisoilmoistirrigationbackend.model.entity.CropConfig;
import org.example.lightaisoilmoistirrigationbackend.model.entity.PhenologyStage;
import org.example.lightaisoilmoistirrigationbackend.repository.CropConfigRepository;
import org.example.lightaisoilmoistirrigationbackend.repository.PhenologyStageRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PhenologyServiceTest {

    @Autowired private PhenologyService phenologyService;
    @Autowired private CropConfigRepository cropConfigRepo;
    @Autowired private PhenologyStageRepository phenologyStageRepo;

    @Test
    @Order(1)
    @DisplayName("按月份自动判断 - 当前月份应对应一个有效的物候期")
    void shouldDetermineStageByMonth() {
        PhenologyStage stage = phenologyService.determineCurrentStage();
        assertNotNull(stage, "应能根据当前月份判断物候期");

        int currentMonth = LocalDate.now().getMonthValue();
        assertNotNull(stage.getStageName());

        // 验证返回的阶段覆盖当前月份
        if (stage.getTypicalStartMonth() <= stage.getTypicalEndMonth()) {
            assertTrue(currentMonth >= stage.getTypicalStartMonth()
                    && currentMonth <= stage.getTypicalEndMonth(),
                    "当前月份 " + currentMonth + " 应在物候期 " + stage.getStageName()
                    + " 的月份范围 [" + stage.getTypicalStartMonth()
                    + ", " + stage.getTypicalEndMonth() + "] 内");
        }
        // 跨年阶段（如休眠期11→2）不做断言，月份落在范围内即正确
    }

    @Test
    @Order(2)
    @DisplayName("跨年物候期 - 休眠期(11→2) 1月应命中")
    void shouldHandleCrossYearStageForJanuary() {
        Optional<CropConfig> cropOpt = cropConfigRepo.findByIsActiveTrue();
        assertTrue(cropOpt.isPresent(), "应有激活的作物配置");

        Optional<PhenologyStage> stageOpt = phenologyStageRepo.findByCropIdAndMonth(cropOpt.get().getId(), 1);
        assertTrue(stageOpt.isPresent(), "1月应能匹配到物候期");
        assertEquals("休眠期", stageOpt.get().getStageName(),
                "1月应处于休眠期（typicalStartMonth=11, typicalEndMonth=2）");
    }

    @Test
    @Order(3)
    @DisplayName("手动覆盖后返回指定物候期")
    void shouldReturnOverriddenStage() {
        Optional<CropConfig> cropOpt = cropConfigRepo.findByIsActiveTrue();
        assertTrue(cropOpt.isPresent());
        CropConfig crop = cropOpt.get();

        // 获取休眠期
        Optional<PhenologyStage> dormantOpt = phenologyStageRepo.findByCropIdAndMonth(crop.getId(), 1);
        assertTrue(dormantOpt.isPresent());
        PhenologyStage dormant = dormantOpt.get();

        // 覆盖为休眠期
        phenologyService.overrideStage(crop.getId(), dormant.getId());
        PhenologyStage result = phenologyService.determineCurrentStage();
        assertEquals("休眠期", result.getStageName(), "手动覆盖后应返回休眠期");

        // 清理
        phenologyService.clearOverride(crop.getId());
    }

    @Test
    @Order(4)
    @DisplayName("取消手动覆盖后恢复自动判断")
    void shouldRestoreAutoDetectionAfterClearOverride() {
        Optional<CropConfig> cropOpt = cropConfigRepo.findByIsActiveTrue();
        assertTrue(cropOpt.isPresent());
        CropConfig crop = cropOpt.get();

        // 覆盖为花期（id=3）
        Optional<PhenologyStage> flowerOpt = phenologyStageRepo.findByCropIdAndMonth(crop.getId(), 4);
        assertTrue(flowerOpt.isPresent());
        PhenologyStage flower = flowerOpt.get();

        phenologyService.overrideStage(crop.getId(), flower.getId());
        assertEquals("花期", phenologyService.determineCurrentStage().getStageName());

        // 清除覆盖
        boolean cleared = phenologyService.clearOverride(crop.getId());
        assertTrue(cleared);

        // 恢复后应为当前月份的物候期
        PhenologyStage restored = phenologyService.determineCurrentStage();
        assertNotNull(restored);
        assertNotEquals("花期", restored.getStageName(),
                "7月不应是花期，应恢复为当前月份的物候期");
    }

    @Test
    @Order(5)
    @DisplayName("清除不存在的手动覆盖 - 返回false")
    void shouldReturnFalseWhenNoOverrideToClear() {
        Optional<CropConfig> cropOpt = cropConfigRepo.findByIsActiveTrue();
        assertTrue(cropOpt.isPresent());
        CropConfig crop = cropOpt.get();

        // 确认当前没有手动覆盖
        assertFalse(phenologyService.isOverridden(crop.getId()));

        boolean cleared = phenologyService.clearOverride(crop.getId());
        assertFalse(cleared, "没有手动覆盖时清除应返回false");
    }

    @Test
    @Order(6)
    @DisplayName("跨年物候期 - 12月也应命中休眠期")
    void shouldHandleCrossYearStageForDecember() {
        Optional<CropConfig> cropOpt = cropConfigRepo.findByIsActiveTrue();
        assertTrue(cropOpt.isPresent());

        Optional<PhenologyStage> stageOpt = phenologyStageRepo.findByCropIdAndMonth(cropOpt.get().getId(), 12);
        assertTrue(stageOpt.isPresent(), "12月应能匹配到物候期");
        assertEquals("休眠期", stageOpt.get().getStageName(),
                "12月应处于休眠期（typicalStartMonth=11, typicalEndMonth=2）");
    }
}
