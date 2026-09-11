package org.example.lightaisoilmoistirrigationbackend.service;

import org.example.lightaisoilmoistirrigationbackend.model.entity.*;
import org.example.lightaisoilmoistirrigationbackend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CropConfigRepository cropConfigRepo;
    private final SoilConfigRepository soilConfigRepo;
    private final PhenologyStageRepository phenologyStageRepo;
    private final StageThresholdRepository thresholdRepo;
    private final AlertRuleRepository alertRuleRepo;

    public DataInitializer(CropConfigRepository cropConfigRepo,
                           SoilConfigRepository soilConfigRepo,
                           PhenologyStageRepository phenologyStageRepo,
                           StageThresholdRepository thresholdRepo,
                           AlertRuleRepository alertRuleRepo) {
        this.cropConfigRepo = cropConfigRepo;
        this.soilConfigRepo = soilConfigRepo;
        this.phenologyStageRepo = phenologyStageRepo;
        this.thresholdRepo = thresholdRepo;
        this.alertRuleRepo = alertRuleRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (cropConfigRepo.count() > 0) {
            log.info("阈值数据库已初始化，跳过预置数据");
            return;
        }

        log.info("开始预置阈值数据...");

        CropConfig apple = createCropConfig();
        List<SoilConfig> soils = createSoilConfigs();
        List<PhenologyStage> stages = createPhenologyStages(apple);
        createThresholds(stages);
        createAlertRules();

        log.info("阈值数据预置完成：作物={}，土壤类型={}，物候期={}，阈值={}，告警规则={}",
                cropConfigRepo.count(), soilConfigRepo.count(),
                phenologyStageRepo.count(), thresholdRepo.count(),
                alertRuleRepo.count());
    }

    private CropConfig createCropConfig() {
        CropConfig apple = new CropConfig();
        apple.setCropName("苹果");
        apple.setVariety("红富士");
        apple.setIrrigationMethod("DRIP");
        apple.setIsActive(true);
        apple.setCreatedAt(LocalDateTime.now());
        apple.setUpdatedAt(LocalDateTime.now());
        return cropConfigRepo.save(apple);
    }

    private List<SoilConfig> createSoilConfigs() {
        List<SoilConfig> soils = new ArrayList<>();

        SoilConfig loam = new SoilConfig();
        loam.setSoilType("LOAM");
        loam.setFieldCapacity(new BigDecimal("32"));
        loam.setWiltingPoint(new BigDecimal("12"));
        loam.setInfiltrationRate(new BigDecimal("10"));
        loam.setMoistureCorrectionFactor(new BigDecimal("1.000"));
        soils.add(soilConfigRepo.save(loam));

        SoilConfig sand = new SoilConfig();
        sand.setSoilType("SAND");
        sand.setFieldCapacity(new BigDecimal("15"));
        sand.setWiltingPoint(new BigDecimal("4"));
        sand.setInfiltrationRate(new BigDecimal("25"));
        sand.setMoistureCorrectionFactor(new BigDecimal("0.700"));
        soils.add(soilConfigRepo.save(sand));

        SoilConfig sandyLoam = new SoilConfig();
        sandyLoam.setSoilType("SANDY_LOAM");
        sandyLoam.setFieldCapacity(new BigDecimal("22"));
        sandyLoam.setWiltingPoint(new BigDecimal("8"));
        sandyLoam.setInfiltrationRate(new BigDecimal("15"));
        sandyLoam.setMoistureCorrectionFactor(new BigDecimal("0.850"));
        soils.add(soilConfigRepo.save(sandyLoam));

        SoilConfig clayLoam = new SoilConfig();
        clayLoam.setSoilType("CLAY_LOAM");
        clayLoam.setFieldCapacity(new BigDecimal("38"));
        clayLoam.setWiltingPoint(new BigDecimal("16"));
        clayLoam.setInfiltrationRate(new BigDecimal("6"));
        clayLoam.setMoistureCorrectionFactor(new BigDecimal("1.150"));
        soils.add(soilConfigRepo.save(clayLoam));

        SoilConfig clay = new SoilConfig();
        clay.setSoilType("CLAY");
        clay.setFieldCapacity(new BigDecimal("42"));
        clay.setWiltingPoint(new BigDecimal("22"));
        clay.setInfiltrationRate(new BigDecimal("3"));
        clay.setMoistureCorrectionFactor(new BigDecimal("1.300"));
        soils.add(soilConfigRepo.save(clay));

        return soils;
    }

    private List<PhenologyStage> createPhenologyStages(CropConfig apple) {
        String[][] stageData = {
            {"休眠期", "12", "2",  "0",    "50",   "12月至次年2月，冬剪清园"},
            {"萌芽期", "3",  "3",  "50",   "150",  "春灌追萌芽肥"},
            {"花期",   "4",  "4",  "150",  "300",  "防霜冻疏花"},
            {"幼果期", "5",  "5",  "300",  "600",  "疏果追坐果肥"},
            {"膨大期", "6",  "8",  "600",  "1800", "水肥关键期"},
            {"成熟期", "9",  "10", "1800", "2300", "控水增糖采收"},
            {"落叶期", "11", "11", "2300", "2600", "秋施基肥清园"},
        };

        List<PhenologyStage> stages = new ArrayList<>();
        for (int i = 0; i < stageData.length; i++) {
            PhenologyStage s = new PhenologyStage();
            s.setCropConfig(apple);
            s.setStageOrder(i + 1);
            s.setStageName(stageData[i][0]);
            s.setTypicalStartMonth(Integer.parseInt(stageData[i][1]));
            s.setTypicalEndMonth(Integer.parseInt(stageData[i][2]));
            s.setGddThresholdLow(new BigDecimal(stageData[i][3]));
            s.setGddThresholdHigh(new BigDecimal(stageData[i][4]));
            s.setDescription(stageData[i][5]);
            stages.add(phenologyStageRepo.save(s));
        }
        return stages;
    }

    private void createThresholds(List<PhenologyStage> stages) {
        // [土壤湿度下限, 土壤湿度上限, pH下限, pH上限, 温度下限, 温度上限, 光照下限]
        BigDecimal[][] matrix = {
            {bd("50"), bd("70"), bd("5.5"), bd("7.5"), bd("-15"), bd("10"),  null             },
            {bd("55"), bd("75"), bd("5.8"), bd("7.2"), bd("5"),   bd("25"),  bd("15000")},
            {bd("60"), bd("75"), bd("6.0"), bd("7.0"), bd("8"),   bd("25"),  bd("20000")},
            {bd("60"), bd("80"), bd("6.0"), bd("7.0"), bd("12"),  bd("30"),  bd("25000")},
            {bd("65"), bd("80"), bd("6.0"), bd("7.0"), bd("18"),  bd("32"),  bd("30000")},
            {bd("55"), bd("70"), bd("6.0"), bd("7.0"), bd("15"),  bd("28"),  bd("30000")},
            {bd("50"), bd("70"), bd("5.8"), bd("7.2"), bd("0"),   bd("18"),  null             },
        };

        for (int si = 0; si < stages.size(); si++) {
            PhenologyStage stage = stages.get(si);
            BigDecimal[] row = matrix[si];

            createThreshold(stage, "B", "LOWER", row[0], "%");
            createThreshold(stage, "B", "UPPER", row[1], "%");
            createThreshold(stage, "A", "LOWER", row[2], null);
            createThreshold(stage, "A", "UPPER", row[3], null);
            createThreshold(stage, "C", "LOWER", row[4], "°C");
            createThreshold(stage, "C", "UPPER", row[5], "°C");
            if (row[6] != null) {
                createThreshold(stage, "E", "LOWER", row[6], "lux");
            }
        }
    }

    private void createThreshold(PhenologyStage stage, String prop, String type,
                                  BigDecimal value, String unit) {
        StageThreshold t = new StageThreshold();
        t.setPhenologyStage(stage);
        t.setSoilConfig(null); // 通用基准
        t.setPropertyIdentifier(prop);
        t.setThresholdType(type);
        t.setThresholdValue(value);
        t.setUnit(unit);
        t.setIsActive(true);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        thresholdRepo.save(t);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private void createAlertRules() {
        createAlertRule("数据中断告警", "DATA_LOSS", null, "device",
                "data_age_seconds > 1800",
                "WARNING", "%s 传感器超过30分钟无数据上报", 15);
        createAlertRule("pH极端异常", "EXTREME_VALUE", "A", "device",
                "value < 4.5 OR value > 8.5",
                "CRITICAL", "土壤pH严重异常: %.1f", 60);
        createAlertRule("温度极端", "EXTREME_VALUE", "C", "device",
                "value > 40",
                "CRITICAL", "环境温度过高: %.0f°C，果实日灼风险", 30);
        createAlertRule("湿度骤降", "TREND", "B", "device",
                "delta_2h < -15",
                "WARNING", "土壤湿度2小时内骤降%.0f%%", 60);
        createAlertRule("灌溉异常", "DEVICE_FAULT", "B", "device",
                "pump_on AND delta_30min < 0",
                "CRITICAL", "水泵已开启但土壤湿度未上升，请检查灌溉系统", 30);
        createAlertRule("高湿病害风险", "TREND", "D", "device",
                "humidity_48h > 85 AND temp_48h BETWEEN 20 AND 28",
                "WARNING", "持续高湿，炭疽病/轮纹病风险升高", 360);
        createAlertRule("霜冻风险", "EXTREME_VALUE", "C", "device",
                "stage IN (3,4) AND forecast_temp < 0",
                "CRITICAL", "花期/幼果期霜冻预警：预报最低温%.0f°C", 60);
    }

    private void createAlertRule(String alertName, String alertType, String propId,
                                  String deviceName, String condition, String severity,
                                  String msgTemplate, int cooldownMinutes) {
        AlertRule rule = new AlertRule();
        rule.setAlertName(alertName);
        rule.setAlertType(alertType);
        rule.setPropertyIdentifier(propId);
        rule.setDeviceName(deviceName);
        rule.setTriggerCondition(condition);
        rule.setSeverity(severity);
        rule.setMessageTemplate(msgTemplate);
        rule.setCooldownMinutes(cooldownMinutes);
        rule.setIsActive(true);
        alertRuleRepo.save(rule);
    }
}
