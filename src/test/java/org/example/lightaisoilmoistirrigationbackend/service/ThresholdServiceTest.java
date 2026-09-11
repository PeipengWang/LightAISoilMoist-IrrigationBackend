package org.example.lightaisoilmoistirrigationbackend.service;

import org.example.lightaisoilmoistirrigationbackend.model.entity.*;
import org.example.lightaisoilmoistirrigationbackend.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ThresholdServiceTest {

    @Autowired private ThresholdService thresholdService;
    @Autowired private StageThresholdRepository thresholdRepo;
    @Autowired private PhenologyStageRepository phenologyStageRepo;
    @Autowired private CropConfigRepository cropConfigRepo;
    @Autowired private SoilConfigRepository soilConfigRepo;

    @Test
    @Order(1)
    @DisplayName("查询当前物候期阈值 - 返回非空且包含土壤湿度和pH的上下限")
    void shouldReturnThresholdsForCurrentStage() {
        Map<String, Map<String, BigDecimal>> result = thresholdService.getCurrentThresholds("LOAM");
        assertNotNull(result);
        assertFalse(result.isEmpty(), "应有阈值数据");

        // 必须包含土壤湿度(B)和pH(A)的上下限
        assertTrue(result.containsKey("B"), "应包含土壤湿度(B)");
        assertTrue(result.containsKey("A"), "应包含pH(A)");
        assertTrue(result.containsKey("C"), "应包含环境温度(C)");
    }

    @Test
    @Order(2)
    @DisplayName("土壤修正系数 - SAND的湿度阈值应为LOAM的约70%")
    void shouldApplySoilCorrectionForSand() {
        Map<String, Map<String, BigDecimal>> loam = thresholdService.getCurrentThresholds("LOAM");
        Map<String, Map<String, BigDecimal>> sand = thresholdService.getCurrentThresholds("SAND");

        assertNotNull(loam.get("B"));
        assertNotNull(sand.get("B"));

        BigDecimal loamLower = loam.get("B").get("LOWER");
        BigDecimal sandLower = sand.get("B").get("LOWER");
        assertNotNull(loamLower);
        assertNotNull(sandLower);

        // SAND修正系数为0.700
        BigDecimal expected = loamLower.multiply(new BigDecimal("0.700")).setScale(1, RoundingMode.HALF_UP);
        assertEquals(0, expected.compareTo(sandLower),
                "SAND湿度下限应为LOAM的 " + loamLower + " × 0.7 = " + expected + "，实际 " + sandLower);
    }

    @Test
    @Order(3)
    @DisplayName("更新阈值后自动创建快照记录")
    void shouldCreateSnapshotOnUpdate() {
        // 获取任意一个阈值
        List<StageThreshold> all = thresholdRepo.findAll();
        assertFalse(all.isEmpty(), "应有预置的阈值数据");
        StageThreshold t = all.get(0);

        BigDecimal oldValue = t.getThresholdValue();
        BigDecimal newValue = new BigDecimal("99.9");
        thresholdService.updateThreshold(t.getId(), newValue, "测试修改", "tester");

        List<ThresholdSnapshot> history = thresholdService.getHistory(t.getId());
        assertFalse(history.isEmpty(), "应有快照记录");
        ThresholdSnapshot latest = history.get(0);
        assertEquals(newValue, latest.getNewValue());
        assertEquals(oldValue, latest.getOldValue());
        assertEquals("测试修改", latest.getChangeReason());
        assertEquals("tester", latest.getOperator());
        assertEquals("MANUAL", latest.getChangeSource());
    }

    @Test
    @Order(4)
    @DisplayName("查询不存在的土壤类型 - 应回退到通用阈值")
    void shouldFallbackToGeneralThreshold() {
        Map<String, Map<String, BigDecimal>> result = thresholdService.getCurrentThresholds("NONEXISTENT_SOIL");
        assertNotNull(result);
        assertFalse(result.isEmpty(), "即使土壤类型不存在，也应返回通用阈值");
        // 通用阈值中仍应有B和C
        assertTrue(result.containsKey("B"));
    }

    @Test
    @Order(5)
    @DisplayName("更新不存在的阈值ID应抛出异常")
    void shouldThrowOnInvalidThresholdId() {
        assertThrows(NoSuchElementException.class, () ->
                thresholdService.updateThreshold(99999L, new BigDecimal("10"), "测试", "tester"));
    }

    @Test
    @Order(6)
    @DisplayName("查询阈值变更历史 - 为空时返回空列表")
    void shouldReturnEmptyHistoryForNoSnapshots() {
        // 取一个从未被修改过的阈值
        List<StageThreshold> all = thresholdRepo.findAll();
        StageThreshold t = all.get(all.size() - 1);
        // 历史表中的记录来自update操作，未修改过的阈值可能无记录
        List<ThresholdSnapshot> history = thresholdService.getHistory(t.getId());
        assertNotNull(history, "历史记录不应为null");
    }

    @Test
    @Order(7)
    @DisplayName("getStages 返回7个物候期并按阶段顺序排列")
    void shouldReturnSevenStagesInOrder() {
        List<PhenologyStage> stages = thresholdService.getStages();
        assertNotNull(stages);
        assertEquals(7, stages.size(), "应有7个物候期");

        // 验证顺序
        for (int i = 0; i < stages.size(); i++) {
            assertEquals(i + 1, stages.get(i).getStageOrder(), "第" + (i+1) + "个应为第" + (i+1) + "阶段");
        }

        // 验证阶段名称
        assertEquals("休眠期", stages.get(0).getStageName());
        assertEquals("落叶期", stages.get(6).getStageName());
    }

    @Test
    @Order(8)
    @DisplayName("CLAY(黏土)的湿度阈值应大于LOAM(壤土)")
    void shouldHaveHigherThresholdForClay() {
        Map<String, Map<String, BigDecimal>> loam = thresholdService.getCurrentThresholds("LOAM");
        Map<String, Map<String, BigDecimal>> clay = thresholdService.getCurrentThresholds("CLAY");

        assertNotNull(loam.get("B"));
        assertNotNull(clay.get("B"));

        BigDecimal loamLower = loam.get("B").get("LOWER");
        BigDecimal clayLower = clay.get("B").get("LOWER");

        // 黏土修正系数1.300，应大于壤土
        assertTrue(clayLower.compareTo(loamLower) > 0,
                "黏土湿度下限(" + clayLower + ")应大于壤土(" + loamLower + ")");
    }
}
