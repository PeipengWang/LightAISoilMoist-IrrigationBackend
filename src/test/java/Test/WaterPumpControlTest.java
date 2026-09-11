package Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 水泵开关控制测试 — 通过 OneNET REST API 下发期望属性
 *
 * 每个测试方法独立运行，可直接在 IDE 中点击单个方法执行。
 *
 * Maven 运行单个测试：
 *   mvn test -Dtest=Test.WaterPumpControlTest#turnPumpOn
 *   mvn test -Dtest=Test.WaterPumpControlTest#turnPumpOff
 *   mvn test -Dtest=Test.WaterPumpControlTest#queryPumpStatus
 *   mvn test -Dtest=Test.WaterPumpControlTest#queryAllProperties
 */
public class WaterPumpControlTest {

    // ==================== 农业环境监测终端 配置 ====================
    private static final String PRODUCT_ID  = "FeGVC46Lne";
    private static final String DEVICE_NAME = "device";
    private static final String ACCESS_KEY  = "WktwTXB5RGNVOTNrdUwxaFVEbEtvQWpGSEJHMWtrT3Q=";
    private static final String API_BASE    = "https://iot-api.heclouds.com";

    private static final String PUMP_ID = "F";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 测试用例 ====================

    @Test
    @DisplayName("开启水泵 (F=1)")
    void turnPumpOn() {
        printSeparator("开启水泵 (F=1)");

        Map<String, Object> result = setDesiredProperty(PUMP_ID, 1);

        printResult(result, "开启");
        assertSuccess(result, "开启");
    }

    @Test
    @DisplayName("关闭水泵 (F=0)")
    void turnPumpOff() {
        printSeparator("关闭水泵 (F=0)");

        Map<String, Object> result = setDesiredProperty(PUMP_ID, 0);

        printResult(result, "关闭");
        assertSuccess(result, "关闭");
    }

    @Test
    @DisplayName("查询水泵状态")
    void queryPumpStatus() {
        printSeparator("查询水泵状态");

        Map<String, Object> result = queryDeviceProperty();
        assertTrue(result.get("code") instanceof Integer && (Integer) result.get("code") == 0,
                "API 查询失败: " + result);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        assertNotNull(data, "返回数据为空");

        // 找到水泵属性并打印
        for (Map<String, Object> item : data) {
            if (PUMP_ID.equals(String.valueOf(item.get("identifier")))) {
                String val = String.valueOf(item.get("value"));
                System.out.println();
                System.out.println("==============================================");
                System.out.println("  水泵当前状态: " + ("1".equals(val) ? "● 开启" : "○ 关闭"));
                System.out.println("==============================================");

                // 状态断言：值必须是 0 或 1
                assertTrue("0".equals(val) || "1".equals(val),
                        "水泵状态值异常: " + val);
                return;
            }
        }
        fail("未找到水泵状态 (F) 数据");
    }

    @Test
    @DisplayName("查询设备所有属性")
    void queryAllProperties() {
        printSeparator("查询设备所有属性");

        Map<String, Object> result = queryDeviceProperty();
        assertTrue(result.get("code") instanceof Integer && (Integer) result.get("code") == 0,
                "API 查询失败: " + result);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        assertNotNull(data, "返回数据为空");
        assertFalse(data.isEmpty(), "返回数据为空列表");

        System.out.println("设备属性列表:");
        System.out.println("┌────┬──────────────────┬──────────┐");
        System.out.println("│ ID │ 名称             │ 值       │");
        System.out.println("├────┼──────────────────┼──────────┤");
        for (Map<String, Object> item : data) {
            String id = String.valueOf(item.get("identifier"));
            String val = String.valueOf(item.get("value"));
            String name = getPropertyName(id);
            String marker = "";
            if (PUMP_ID.equals(id)) {
                marker = "1".equals(val) ? " [开启]" : " [关闭]";
            }
            System.out.printf("│ %-2s │ %-16s │ %-8s │%s%n",
                    id, name, val, marker);
        }
        System.out.println("└────┴──────────────────┴──────────┘");

        // 验证关键属性存在
        assertTrue(data.stream().anyMatch(it -> PUMP_ID.equals(it.get("identifier"))),
                "水泵属性 F 缺失");
        assertTrue(data.stream().anyMatch(it -> "M".equals(it.get("identifier"))),
                "氮属性 M 缺失");
        assertTrue(data.stream().anyMatch(it -> "N".equals(it.get("identifier"))),
                "磷属性 N 缺失");
        assertTrue(data.stream().anyMatch(it -> "O".equals(it.get("identifier"))),
                "钾属性 O 缺失");
    }

    @Test
    @DisplayName("开启后查询验证 — 开启水泵并确认状态")
    void turnOnThenVerify() {
        printSeparator("开启水泵并验证");

        // Step 1: 开启
        System.out.println("[Step 1] 下发开启命令...");
        Map<String, Object> onResult = setDesiredProperty(PUMP_ID, 1);
        printResult(onResult, "开启");
        assertSuccess(onResult, "开启");

        // Step 2: 等 2 秒让设备响应
        System.out.println("[Step 2] 等待 2 秒后查询状态...");
        sleep(2000);

        // Step 3: 查询确认
        Map<String, Object> queryResult = queryDeviceProperty();
        assertTrue(queryResult.get("code") instanceof Integer && (Integer) queryResult.get("code") == 0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) queryResult.get("data");
        assertNotNull(data);

        String pumpVal = findPumpValue(data);
        assertNotNull(pumpVal, "未找到水泵状态");
        assertEquals("1", pumpVal, "水泵应为开启状态(F=1)，实际为: " + pumpVal);
        System.out.println("[OK] 验证通过: 水泵已开启");
    }

    @Test
    @DisplayName("关闭后查询验证 — 关闭水泵并确认状态")
    void turnOffThenVerify() {
        printSeparator("关闭水泵并验证");

        // Step 1: 关闭
        System.out.println("[Step 1] 下发关闭命令...");
        Map<String, Object> offResult = setDesiredProperty(PUMP_ID, 0);
        printResult(offResult, "关闭");
        assertSuccess(offResult, "关闭");

        // Step 2: 等待
        System.out.println("[Step 2] 等待 2 秒后查询状态...");
        sleep(2000);

        // Step 3: 查询确认
        Map<String, Object> queryResult = queryDeviceProperty();
        assertTrue(queryResult.get("code") instanceof Integer && (Integer) queryResult.get("code") == 0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) queryResult.get("data");
        assertNotNull(data);

        String pumpVal = findPumpValue(data);
        assertNotNull(pumpVal, "未找到水泵状态");
        assertEquals("0", pumpVal, "水泵应为关闭状态(F=0)，实际为: " + pumpVal);
        System.out.println("[OK] 验证通过: 水泵已关闭");
    }

    @Test
    @DisplayName("切换水泵 — 读取当前状态后翻转")
    void togglePump() {
        printSeparator("切换水泵状态");

        // Step 1: 读取当前状态
        System.out.println("[Step 1] 读取当前水泵状态...");
        Map<String, Object> queryResult = queryDeviceProperty();
        assertTrue(queryResult.get("code") instanceof Integer && (Integer) queryResult.get("code") == 0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) queryResult.get("data");
        assertNotNull(data);
        String currentVal = findPumpValue(data);
        assertNotNull(currentVal, "未找到水泵状态");
        System.out.println("当前状态: " + ("1".equals(currentVal) ? "开启" : "关闭") + " (F=" + currentVal + ")");

        // Step 2: 翻转
        int targetVal = "1".equals(currentVal) ? 0 : 1;
        String targetDesc = targetVal == 1 ? "开启" : "关闭";
        System.out.println("[Step 2] 目标状态: " + targetDesc + " (F=" + targetVal + ")");

        Map<String, Object> result = setDesiredProperty(PUMP_ID, targetVal);
        printResult(result, targetDesc);
        assertSuccess(result, targetDesc);

        // Step 3: 验证
        System.out.println("[Step 3] 等待 2 秒后验证...");
        sleep(2000);
        Map<String, Object> verifyResult = queryDeviceProperty();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> verifyData = (List<Map<String, Object>>) verifyResult.get("data");
        String newVal = findPumpValue(verifyData);
        assertEquals(String.valueOf(targetVal), newVal,
                "翻转失败: 期望=" + targetVal + ", 实际=" + newVal);
        System.out.println("[OK] 切换成功: " + ("1".equals(currentVal) ? "开启→关闭" : "关闭→开启"));
    }

    // ==================== REST API 调用 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> setDesiredProperty(String identifier, int value) {
        try {
            String token = generateApiToken();

            Map<String, Object> params = new LinkedHashMap<>();
            params.put(identifier, value);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("product_id", PRODUCT_ID);
            body.put("device_name", DEVICE_NAME);
            body.put("params", params);

            String jsonBody = objectMapper.writeValueAsString(body);

            System.out.println("POST " + API_BASE + "/thingmodel/set-device-desired-property");
            System.out.println("Body: " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/thingmodel/set-device-desired-property"))
                    .header("Authorization", token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("HTTP " + response.statusCode());

            if (response.body() == null || response.body().isEmpty()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("error", "空响应");
                error.put("status_code", response.statusCode());
                return error;
            }
            return objectMapper.readValue(response.body(), Map.class);

        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return error;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> queryDeviceProperty() {
        try {
            String token = generateApiToken();
            String query = String.format("product_id=%s&device_name=%s", PRODUCT_ID, DEVICE_NAME);
            String url = API_BASE + "/thingmodel/query-device-property?" + query;

            System.out.println("GET " + url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("HTTP " + response.statusCode());

            if (response.body() == null || response.body().isEmpty()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("error", "空响应");
                return error;
            }
            return objectMapper.readValue(response.body(), Map.class);

        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return error;
        }
    }

    // ==================== 鉴权 ====================

    private String generateApiToken() {
        String version = "2022-05-01";
        String res = "products/" + PRODUCT_ID + "/devices/" + DEVICE_NAME;
        String et = String.valueOf(System.currentTimeMillis() / 1000 + 3600);
        String org = et + "\n" + "sha256" + "\n" + res + "\n" + version;

        try {
            byte[] key = Base64.getDecoder().decode(ACCESS_KEY);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] signBytes = mac.doFinal(org.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(
                    Base64.getEncoder().encodeToString(signBytes), StandardCharsets.UTF_8);
            String resEnc = URLEncoder.encode(res, StandardCharsets.UTF_8);
            return String.format("version=%s&res=%s&et=%s&method=sha256&sign=%s",
                    version, resEnc, et, sign);
        } catch (Exception e) {
            throw new RuntimeException("Token 生成失败", e);
        }
    }

    // ==================== 辅助方法 ====================

    private String getPropertyName(String id) {
        switch (id) {
            case "A": return "PH值";
            case "B": return "土壤湿度";
            case "C": return "环境温度";
            case "D": return "环境湿度";
            case "E": return "光照强度";
            case "F": return "水泵状态";
            case "G": return "PH状态";
            case "H": return "土壤湿度状态";
            case "I": return "PH阈值低";
            case "J": return "PH阈值高";
            case "K": return "土壤湿度阈值低";
            case "L": return "土壤湿度阈值高";
            case "M": return "氮含量";
            case "N": return "磷含量";
            case "O": return "钾含量";
            default:  return id;
        }
    }

    private String findPumpValue(List<Map<String, Object>> data) {
        for (Map<String, Object> item : data) {
            if (PUMP_ID.equals(String.valueOf(item.get("identifier")))) {
                return String.valueOf(item.get("value"));
            }
        }
        return null;
    }

    private void assertSuccess(Map<String, Object> result, String action) {
        assertTrue(result.get("code") instanceof Integer,
                "响应 code 异常: " + result);
        assertEquals(0, result.get("code"),
                "水泵" + action + "失败: " + result.getOrDefault("msg", ""));
    }

    private void printResult(Map<String, Object> result, String action) {
        System.out.println();
        System.out.println("--- 响应 ---");
        System.out.println("  code: " + result.getOrDefault("code", "N/A"));
        System.out.println("  msg:  " + result.getOrDefault("msg", "N/A"));
        if (result.get("code") instanceof Integer && (Integer) result.get("code") == 0) {
            System.out.println("  >> 水泵已" + action + " <<");
        } else {
            System.out.println("  !! 失败 !!");
            if (result.containsKey("error")) {
                System.out.println("  error: " + result.get("error"));
            }
        }
    }

    private void printSeparator(String title) {
        System.out.println();
        System.out.println("==============================================");
        System.out.println("  " + title);
        System.out.println("  Product: " + PRODUCT_ID + " / " + DEVICE_NAME);
        System.out.println("==============================================");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}