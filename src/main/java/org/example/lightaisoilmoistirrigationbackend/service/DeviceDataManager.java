package org.example.lightaisoilmoistirrigationbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.example.lightaisoilmoistirrigationbackend.config.DeviceConfig;
import org.example.lightaisoilmoistirrigationbackend.config.DeviceConfig.DeviceInfo;
import org.example.lightaisoilmoistirrigationbackend.config.DeviceConfig.PropertyMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 设备数据管理服务 — 整个项目的核心枢纽
 *
 * 职责：
 * 1. 通过 MQTT 协议连接 OneNet 平台，订阅每个设备的实时数据上报
 * 2. 将收到的数据解析后存入内存缓存（ConcurrentHashMap），供 Controller 实时查询
 * 3. 维护 MQTT 长连接，断线自动重连
 * 4. 处理平台下行的属性设置指令和属性查询指令，自动回复
 *
 * 线程安全设计：
 * - 缓存用 ConcurrentHashMap 保证多线程读写不冲突
 * - 每个设备有独立的 ReentrantReadWriteLock，读多写少场景用读写锁优化并发性能
 * - SSE 推送线程读缓存（加读锁），MQTT 回调线程写缓存（加写锁）
 */
@Service
public class DeviceDataManager implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataManager.class);

    /**
     * 设备数据内存缓存
     * 结构: { deviceName → { identifier → {value, time, name, unit, data_type, mode, enum_desc} } }
     * 最外层 key 是设备名，内层 key 是属性标识符（如 soil_moisture），value 是属性详情 Map
     * 使用 ConcurrentHashMap 保证并发安全，内层用 LinkedHashMap 保持属性插入顺序
     */
    private final ConcurrentHashMap<String, Map<String, Map<String, Object>>> cache = new ConcurrentHashMap<>();

    /**
     * 每个设备的读写锁，key 为设备名
     * 读锁: 查询数据时持有（允许多个读线程并发）
     * 写锁: MQTT 消息更新数据时持有（排他）
     */
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    /**
     * 所有已创建的 MQTT 客户端列表，用于关闭时批量断开连接
     */
    private final List<MqttClient> clients = new ArrayList<>();

    /**
     * MQTT 客户端 → 设备信息 的映射，便于根据连接查到对应的设备配置
     */
    private final Map<MqttClient, DeviceInfo> clientDevices = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OneNetApiClient apiClient;

    /**
     * API 刷新间隔（毫秒），默认 2 秒
     */
    private static final long REFRESH_INTERVAL_MS = 2000;

    /**
     * 记录每个设备最后一次通过 API 刷新的时间戳（毫秒）
     */
    private final ConcurrentHashMap<String, Long> lastApiRefreshTime = new ConcurrentHashMap<>();

    /**
     * 构造函数：根据 DeviceConfig 中配置的所有设备，预初始化缓存结构
     * 每个设备的每个属性都预先创建好 Map 条目，value 初始为 null，time 初始为 0
     * 这样前端查询时总是能拿到完整的属性列表，即使设备还没上报过数据
     */
    public DeviceDataManager(OneNetApiClient apiClient) {
        this.apiClient = apiClient;
        for (DeviceInfo dev : DeviceConfig.ALL_DEVICES) {
            // 内层使用 LinkedHashMap 保持属性定义的插入顺序，并允许 null 值
            Map<String, Map<String, Object>> devCache = new LinkedHashMap<>();
            for (Map.Entry<String, PropertyMeta> entry : dev.getProperties().entrySet()) {
                String ident = entry.getKey();      // 属性标识符，如 "soil_moisture"
                PropertyMeta prop = entry.getValue(); // 属性元数据（名称、单位、数据类型等）
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("value", null);            // 当前值，初始为 null
                item.put("time", 0L);               // 最新数据上报的时间戳（毫秒）
                item.put("name", prop.getName());   // 属性中文名，如 "土壤湿度"
                item.put("unit", prop.getUnit());   // 单位，如 "%"
                item.put("data_type", prop.getDataType()); // 数据类型，如 "float"
                item.put("mode", prop.getMode());   // 读写模式: "r"(只读) / "rw"(读写)
                item.put("enum_desc", prop.getEnumDesc()); // 枚举值说明（如有）
                devCache.put(ident, item);
            }
            cache.put(dev.getDeviceName(), devCache);
            locks.put(dev.getDeviceName(), new ReentrantReadWriteLock());
        }
    }

    // ==================== MQTT Token 生成 ====================

    /**
     * 生成 OneNet 平台 MQTT 接入所需的 Token（即 password）
     *
     * OneNet MQTT 认证采用 HMAC-SHA1 签名机制：
     * 1. 拼接签名字符串: {过期时间}\n{签名方法}\n{资源路径}\n{API版本}
     * 2. 使用设备 accessKey 对签名字符串做 HMAC-SHA1 签名
     * 3. Base64 编码后 URLEncode
     * 4. 将 version/res/et/method/sign 组装成 query-string 格式
     *
     * @param productId OneNet 产品 ID
     * @param accessKey 设备 AccessKey（Base64 格式）
     * @return MQTT 连接用的 password（即 token 串）
     */
    public static String generateMqttToken(String productId, String accessKey) {
        String version = "2018-10-31";                                 // OneNet API 版本号
        String res = "products/" + productId;                          // 资源路径
        String et = String.valueOf(System.currentTimeMillis() / 1000 + 3600); // 过期时间: 当前时间 + 1小时（秒）
        String method = "sha1";                                         // 签名方法
        String org = et + "\n" + method + "\n" + res + "\n" + version; // 待签名的原始字符串

        try {
            // 解码 AccessKey（Base64 → 字节数组）
            byte[] key = Base64.getDecoder().decode(accessKey);
            // HMAC-SHA1 签名
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] signBytes = mac.doFinal(org.getBytes(StandardCharsets.UTF_8));
            // Base64 编码 → URL 编码
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signBytes), StandardCharsets.UTF_8);
            String resEnc = URLEncoder.encode(res, StandardCharsets.UTF_8);
            // 组装成 query-string 格式的 token
            return String.format("version=%s&res=%s&et=%s&method=%s&sign=%s", version, resEnc, et, method, sign);
        } catch (Exception e) {
            throw new RuntimeException("MQTT token 生成失败", e);
        }
    }

    // ==================== Spring 生命周期回调 ====================

    /**
     * Spring Bean 初始化完成后自动调用
     * 遍历所有配置的设备，启动各自的 MQTT 连接
     */
    @Override
    public void afterPropertiesSet() {
        log.info("[MQTT] 正在启动所有设备连接...");
        for (DeviceInfo dev : DeviceConfig.ALL_DEVICES) {
//            startDevice(dev);
        }
        log.info("[MQTT] 所有设备 MQTT 已就绪");
    }

    /**
     * Spring Bean 销毁前自动调用（应用关闭时）
     * 逐个断开所有 MQTT 客户端连接，释放资源
     */
    @Override
    public void destroy() {
        log.info("[MQTT] 正在关闭所有设备连接...");
        for (MqttClient client : clients) {
            try {
                client.disconnect();
                client.close();
            } catch (Exception e) {
                log.warn("[MQTT] 关闭客户端失败: {}", e.getMessage());
            }
        }
        clients.clear();
        log.info("[MQTT] 所有设备已断开");
    }

    // ==================== 数据访问接口 ====================

    /**
     * 获取所有设备的最新数据快照
     * 遍历所有设备，委托 getDeviceLatest（含 API 回退逻辑）获取每个设备数据
     * @return 结构: { deviceName → { identifier → {value, time, name, unit, ...} } }
     */
    public Map<String, Map<String, Map<String, Object>>> getAllLatest() {
        Map<String, Map<String, Map<String, Object>>> snapshot = new LinkedHashMap<>();
        for (DeviceInfo dev : DeviceConfig.ALL_DEVICES) {
            Map<String, Map<String, Object>> devData = getDeviceLatest(dev.getDeviceName());
            if (devData != null && !devData.isEmpty()) {
                snapshot.put(dev.getDeviceName(), devData);
            }
        }
        return snapshot;
    }

    /**
     * 获取单个设备的最新数据快照
     *
     * 优先从 MQTT 实时缓存读取，若缓存中所有属性值均为 null（即从未收到过 MQTT 数据），
     * 则自动通过 OneNet REST API 查询平台最新值并回填缓存后返回。
     *
     * @param deviceName 设备名
     * @return 结构: { identifier → {value, time, name, unit, ...} }
     *         设备不存在时返回空 Map
     */
    public Map<String, Map<String, Object>> getDeviceLatest(String deviceName) {
        Map<String, Map<String, Object>> devCache = cache.get(deviceName);
        if (devCache == null) {
            return Collections.emptyMap();
        }
        ReentrantReadWriteLock lock = locks.get(deviceName);
        if (lock == null) {
            return new LinkedHashMap<>(devCache);
        }
        lock.readLock().lock();
        try {
            if (needsRefresh(deviceName)) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    if (needsRefresh(deviceName)) {
                        refreshFromApi(deviceName, devCache);
                        lastApiRefreshTime.put(deviceName, System.currentTimeMillis());
                    }
                } finally {
                    lock.readLock().lock();
                    lock.writeLock().unlock();
                }
            }
            Map<String, Map<String, Object>> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> e : devCache.entrySet()) {
                snapshot.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
            return snapshot;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 判断是否需要通过 API 刷新 — 距上次刷新超过 REFRESH_INTERVAL_MS 则需要刷新
     */
    private boolean needsRefresh(String deviceName) {
        Long lastRefresh = lastApiRefreshTime.get(deviceName);
        if (lastRefresh == null) {
            return true;
        }
        return (System.currentTimeMillis() - lastRefresh) > REFRESH_INTERVAL_MS;
    }

    /**
     * 通过 OneNet REST API 查询设备最新值，将结果解析后更新到缓存
     */
    @SuppressWarnings("unchecked")
    private void refreshFromApi(String deviceName, Map<String, Map<String, Object>> devCache) {
        try {
            Map<String, Object> apiResult = apiClient.queryDeviceLatestApi(deviceName);
            if (apiResult.containsKey("error")) {
                log.warn("[API] 设备 {} API 查询失败: {}", deviceName, apiResult.get("error"));
                return;
            }
            Object dataObj = apiResult.get("data");
            if (!(dataObj instanceof java.util.List)) {
                log.warn("[API] 设备 {} API 返回数据格式异常: {}", deviceName, dataObj);
                return;
            }
            java.util.List<Map<String, Object>> dataList = (java.util.List<Map<String, Object>>) dataObj;
            for (Map<String, Object> item : dataList) {
                String identifier = (String) item.get("identifier");
                if (identifier != null && devCache.containsKey(identifier)) {
                    Map<String, Object> cacheItem = devCache.get(identifier);
                    Object value = item.get("value");
                    Object time = item.get("time");
                    cacheItem.put("value", value);
                    if (time instanceof Number) {
                        cacheItem.put("time", ((Number) time).longValue());
                    } else if (time instanceof String) {
                        try {
                            cacheItem.put("time", Long.parseLong((String) time));
                        } catch (NumberFormatException ignored) {
                            cacheItem.put("time", System.currentTimeMillis());
                        }
                    }
                }
            }
            log.info("[API] 设备 {} 缓存已刷新，共 {} 个属性", deviceName, dataList.size());
        } catch (Exception e) {
            log.error("[API] 设备 {} 刷新缓存异常: {}", deviceName, e.getMessage());
        }
    }
}