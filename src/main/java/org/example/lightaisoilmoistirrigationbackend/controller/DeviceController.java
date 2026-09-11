package org.example.lightaisoilmoistirrigationbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.example.lightaisoilmoistirrigationbackend.config.DeviceConfig;
import org.example.lightaisoilmoistirrigationbackend.config.DeviceConfig.DeviceInfo;
import org.example.lightaisoilmoistirrigationbackend.config.DeviceConfig.PropertyMeta;
import org.example.lightaisoilmoistirrigationbackend.model.entity.DecisionLog;
import org.example.lightaisoilmoistirrigationbackend.repository.DecisionLogRepository;
import org.example.lightaisoilmoistirrigationbackend.service.DeviceDataManager;
import org.example.lightaisoilmoistirrigationbackend.service.OneNetApiClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Tag(name = "设备接口", description = "OneNET 设备数据采集与展示")
@RestController
@Slf4j
public class DeviceController {

    private final DeviceDataManager dataManager;
    private final OneNetApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final DecisionLogRepository decisionLogRepository;

    private final CopyOnWriteArrayList<SseEmitter> sseEmitters = new CopyOnWriteArrayList<>();

    public DeviceController(DeviceDataManager dataManager, OneNetApiClient apiClient,
                            ObjectMapper objectMapper, DecisionLogRepository decisionLogRepository) {
        this.dataManager = dataManager;
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        this.decisionLogRepository = decisionLogRepository;
    }

    @Operation(summary = "获取设备列表", description = "返回所有设备及其属性元数据")
    @GetMapping("/api/devices")
    public Map<String, Object> apiDevices() {
        var result = new java.util.ArrayList<Map<String, Object>>();
        for (DeviceInfo dev : DeviceConfig.ALL_DEVICES) {
            Map<String, Object> devMap = new LinkedHashMap<>();
            devMap.put("device_name", dev.getDeviceName());
            devMap.put("product_id", dev.getProductId());
            Map<String, Object> props = new LinkedHashMap<>();
            for (Map.Entry<String, PropertyMeta> entry : dev.getProperties().entrySet()) {
                PropertyMeta prop = entry.getValue();
                Map<String, Object> propMap = new LinkedHashMap<>();
                propMap.put("name", prop.getName());
                propMap.put("unit", prop.getUnit());
                propMap.put("data_type", prop.getDataType());
                propMap.put("mode", prop.getMode());
                propMap.put("enum_desc", prop.getEnumDesc());
                props.put(entry.getKey(), propMap);
            }
            devMap.put("properties", props);
            result.add(devMap);
        }
        return Map.of("devices", result);
    }

    @Operation(summary = "获取设备最新数据", description = "从 MQTT 实时缓存读取设备最新属性值，不传 device_name 则返回所有设备")
    @GetMapping("/api/latest")
    public Map<String, Object> apiLatest(
            @Parameter(description = "设备名，不传则返回所有设备") @RequestParam(required = false) String device_name) {
        if (device_name != null && !device_name.isEmpty()) {
            Map<String, Map<String, Object>> data = dataManager.getDeviceLatest(device_name);
            if (data.isEmpty()) {
                return Map.of("error", "设备 " + device_name + " 不存在", "device_name", device_name);
            }
            return Map.of("device_name", device_name, "data", data);
        }
        return Map.of("devices", dataManager.getAllLatest());
    }

    @Operation(summary = "通过 API 获取最新值", description = "通过 OneNET REST API 查询设备最新值（兜底方案，直接查平台）")
    @GetMapping("/api/latest/api")
    public Map<String, Object> apiLatestFromApi(
            @Parameter(description = "设备名", required = true) @RequestParam String device_name) {
        DeviceInfo dev = DeviceConfig.findDevice(device_name);
        if (dev == null) {
            return Map.of("error", "设备 " + device_name + " 不存在");
        }
        return apiClient.queryDeviceLatestApi(device_name);
    }

    @Operation(summary = "查询历史数据", description = "查询指定设备指定属性的历史数据")
    @GetMapping("/api/history")
    public Map<String, Object> apiHistory(
            @Parameter(description = "设备名", required = true) @RequestParam String device_name,
            @Parameter(description = "属性标识符", required = true) @RequestParam String identifier,
            @Parameter(description = "起始毫秒时间戳，默认24小时前") @RequestParam(required = false) Long start,
            @Parameter(description = "结束毫秒时间戳，默认当前时间") @RequestParam(required = false) Long end,
            @Parameter(description = "最大返回条数(1-100)") @RequestParam(defaultValue = "100") int limit) {

        if (end == null) {
            end = System.currentTimeMillis();
        }
        if (start == null) {
            start = end - 72 * 3600 * 1000L;
        }

        DeviceInfo dev = DeviceConfig.findDevice(device_name);
        if (dev == null) {
            return Map.of("error", "设备 " + device_name + " 不存在");
        }

        if (!dev.getProperties().containsKey(identifier)) {
            return Map.of("error", "属性 " + identifier + " 不存在",
                    "available", dev.getProperties().keySet());
        }

        Map<String, Object> result = apiClient.queryDeviceHistory(device_name, identifier, start, end, limit);
        PropertyMeta prop = dev.getProperties().get(identifier);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("device_name", device_name);
        response.put("identifier", identifier);
        response.put("property_name", prop != null ? prop.getName() : identifier);
        response.put("unit", prop != null ? prop.getUnit() : "");
        response.put("data", result);
        return response;
    }

    @Operation(summary = "查询设备所有属性历史数据", description = "查询指定设备所有属性的历史数据，返回该设备下每个属性的历史数据")
    @GetMapping("/api/history/all")
    public Map<String, Object> apiHistoryAll(
            @Parameter(description = "设备名", required = true) @RequestParam String device_name,
            @Parameter(description = "起始毫秒时间戳，默认24小时前") @RequestParam(required = false) Long start,
            @Parameter(description = "结束毫秒时间戳，默认当前时间") @RequestParam(required = false) Long end,
            @Parameter(description = "每个属性最大返回条数(1-100)") @RequestParam(defaultValue = "100") int limit) {

        if (end == null) {
            end = System.currentTimeMillis();
        }
        if (start == null) {
            start = end - 72 * 3600 * 1000L;
        }

        DeviceInfo dev = DeviceConfig.findDevice(device_name);
        if (dev == null) {
            return Map.of("error", "设备 " + device_name + " 不存在");
        }

        Map<String, Map<String, Object>> historyData = apiClient.queryDeviceAllHistory(device_name, start, end, limit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("device_name", device_name);
        response.put("start", start);
        response.put("end", end);
        response.put("properties", historyData);
        return response;
    }

    @Operation(summary = "SSE 实时推送", description = "Server-Sent Events 实时数据推送，每2秒检查数据变化，仅在数据有变化时推送")
    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter apiStream(
            @Parameter(description = "设备名，不传则推送所有设备") @RequestParam(required = false) String device_name) {

        SseEmitter emitter = new SseEmitter(0L);
        sseEmitters.add(emitter);

        // 连接销毁回调
        Runnable clearTask = () -> {
            sseEmitters.remove(emitter);
            emitter.complete();
        };
        emitter.onCompletion(clearTask);
        emitter.onTimeout(clearTask);
        emitter.onError((e) -> clearTask.run());

        Thread sseThread = new Thread(() -> {
            String lastHash = "";
            // 新增标记：线程运行开关，前端断开后立刻置false
            boolean running = true;
            while (running) {
                try {
                    // 提前判断emitter是否失效，提前终止循环
                    if (!sseEmitters.contains(emitter)) {
                        running = false;
                        break;
                    }
                    Object current;
                    if (device_name != null && !device_name.isEmpty()) {
                        current = dataManager.getDeviceLatest(device_name);
                    } else {
                        current = dataManager.getAllLatest();
                    }
                    String currentStr = objectMapper.writeValueAsString(current);
                    String currentHash = String.valueOf(currentStr.hashCode());
                    if (!currentHash.equals(lastHash)) {
                        emitter.send(SseEmitter.event().data(currentStr));
                        lastHash = currentHash;
                    }
                    Thread.sleep(2000);
                } catch (IOException e) {
                    // IO异常=客户端连接已断开，终止循环
                    running = false;
                    clearTask.run();
                    break;
                } catch (InterruptedException e) {
                    running = false;
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        running = false;
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("SSE推送线程已终止");
        });
        sseThread.setDaemon(true);
        sseThread.setName("sse-" + (device_name != null ? device_name : "all"));
        sseThread.start();

        return emitter;
    }

    @Operation(summary = "指令下发", description = "向设备下发期望属性值。可写属性: F(水泵开关:0关/1开)、I(pH阈值低)、J(pH阈值高)、K(土壤湿度阈值低)、L(土壤湿度阈值高)")
    @PostMapping("/api/command")
    public Map<String, Object> apiCommand(
            @Parameter(description = "请求体") @RequestBody Map<String, Object> requestBody) {

        String deviceName = (String) requestBody.get("device_name");
        String identifier = (String) requestBody.get("identifier");
        Object value = requestBody.get("value");

        // 参数校验
        if (deviceName == null || deviceName.isEmpty()) {
            return Map.of("error", "device_name 不能为空");
        }
        if (identifier == null || identifier.isEmpty()) {
            return Map.of("error", "identifier 不能为空");
        }
        if (value == null) {
            return Map.of("error", "value 不能为空");
        }

        DeviceInfo dev = DeviceConfig.findDevice(deviceName);
        if (dev == null) {
            return Map.of("error", "设备 " + deviceName + " 不存在",
                    "available_devices", DeviceConfig.ALL_DEVICES.stream()
                            .map(DeviceInfo::getDeviceName).toList());
        }

        PropertyMeta prop = dev.getProperties().get(identifier);
        if (prop == null) {
            return Map.of("error", "属性 " + identifier + " 不存在于设备 " + deviceName,
                    "available_identifiers", dev.getProperties().keySet());
        }

        if (!"读写".equals(prop.getMode())) {
            return Map.of("error", "属性 " + identifier + "(" + prop.getName() + ") 为只读属性，不可下发",
                    "writable_identifiers", dev.getProperties().entrySet().stream()
                            .filter(e -> "读写".equals(e.getValue().getMode()))
                            .map(e -> e.getKey() + "(" + e.getValue().getName() + ")").toList());
        }

        // 类型自动转换：enum 类型属性支持传入数字或字符串
        Object convertedValue = value;
        if (!prop.getEnumDesc().isEmpty()) {
            String valStr = String.valueOf(value);
            if (prop.getEnumDesc().containsKey(valStr)) {
                try {
                    convertedValue = Integer.parseInt(valStr);
                } catch (NumberFormatException ignored) {
                    // 保持字符串
                }
            }
        } else if ("int32".equals(prop.getDataType()) && value instanceof String) {
            try {
                convertedValue = Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return Map.of("error", "属性 " + identifier + " 需要整数类型，收到: " + value);
            }
        }

        Map<String, Object> result = apiClient.setDesiredProperty(deviceName, identifier, convertedValue);

        // 记录决策日志
        saveDecisionLog(deviceName, identifier, prop, convertedValue, result);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("device_name", deviceName);
        response.put("identifier", identifier);
        response.put("property_name", prop.getName());
        response.put("value", convertedValue);
        if (!prop.getEnumDesc().isEmpty()) {
            response.put("value_desc", prop.getEnumDesc().getOrDefault(String.valueOf(convertedValue), ""));
        }
        response.put("result", result);
        return response;
    }

    private void saveDecisionLog(String deviceName, String identifier, PropertyMeta prop,
                                  Object value, Map<String, Object> apiResult) {
        try {
            DecisionLog log = new DecisionLog();
            log.setDeviceName(deviceName);
            log.setIdentifier(identifier);
            log.setPropertyName(prop.getName());
            log.setValue(String.valueOf(value));
            if (!prop.getEnumDesc().isEmpty()) {
                log.setValueDesc(prop.getEnumDesc().getOrDefault(String.valueOf(value), ""));
            }
            // 决策类型推断
            log.setDecisionType("F".equals(identifier) ? "PUMP_CONTROL" : "THRESHOLD_SET");
            // 成功判定
            Object code = apiResult.get("code");
            boolean success = code instanceof Number && ((Number) code).intValue() == 0;
            log.setSuccess(success);
            Object msg = apiResult.get("msg");
            log.setResultMsg(msg != null ? String.valueOf(msg) : (success ? "SUCCESS" : "FAILED"));
            log.setCreatedAt(LocalDateTime.now());

            decisionLogRepository.save(log);
        } catch (Exception e) {
            log.warn("决策日志记录失败: {}", e.getMessage());
        }
    }

    @Operation(summary = "查询决策日志", description = "分页查询指令下发决策日志，支持按设备名、属性、决策类型、时间范围筛选")
    @GetMapping("/api/decision-logs")
    public Map<String, Object> apiDecisionLogs(
            @Parameter(description = "设备名") @RequestParam(required = false) String device_name,
            @Parameter(description = "属性标识符") @RequestParam(required = false) String identifier,
            @Parameter(description = "决策类型: PUMP_CONTROL / THRESHOLD_SET") @RequestParam(required = false) String decision_type,
            @Parameter(description = "起始毫秒时间戳，默认24小时前") @RequestParam(required = false) Long start,
            @Parameter(description = "结束毫秒时间戳，默认当前时间") @RequestParam(required = false) Long end,
            @Parameter(description = "页码(0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数(1-100)") @RequestParam(defaultValue = "20") int size) {

        if (end == null) {
            end = System.currentTimeMillis();
        }
        if (start == null) {
            start = end - 24 * 3600 * 1000L;
        }
        int pageSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        final LocalDateTime startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(start), ZoneId.systemDefault());
        final LocalDateTime endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(end), ZoneId.systemDefault());

        Specification<DecisionLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.between(root.get("createdAt"), startTime, endTime));
            if (device_name != null && !device_name.isEmpty()) {
                predicates.add(cb.equal(root.get("deviceName"), device_name));
            }
            if (identifier != null && !identifier.isEmpty()) {
                predicates.add(cb.equal(root.get("identifier"), identifier));
            }
            if (decision_type != null && !decision_type.isEmpty()) {
                predicates.add(cb.equal(root.get("decisionType"), decision_type));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DecisionLog> result = decisionLogRepository.findAll(spec, pageable);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", result.getContent());
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        return response;
    }
}
