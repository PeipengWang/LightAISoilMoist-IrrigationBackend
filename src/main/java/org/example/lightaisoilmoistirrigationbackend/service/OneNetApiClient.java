package org.example.lightaisoilmoistirrigationbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lightaisoilmoistirrigationbackend.config.DeviceConfig;
import org.example.lightaisoilmoistirrigationbackend.config.DeviceConfig.DeviceInfo;
import org.springframework.stereotype.Service;

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
import java.util.Map;
import java.util.Objects;

@Service
public class OneNetApiClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成 OneNET REST API 鉴权 token (SHA256, version=2022-05-01)
     */
    public static String generateApiToken(DeviceInfo dev) {
        String version = "2022-05-01";
        String res = "products/" + dev.getProductId() + "/devices/" + dev.getDeviceName();
        String et = String.valueOf(System.currentTimeMillis() / 1000 + 3600);
        String method = "sha256";
        String org = et + "\n" + method + "\n" + res + "\n" + version;

        try {
            byte[] key = Base64.getDecoder().decode(dev.getAccessKey());
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] signBytes = mac.doFinal(org.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signBytes), StandardCharsets.UTF_8);
            String resEnc = URLEncoder.encode(res, StandardCharsets.UTF_8);
            return String.format("version=%s&res=%s&et=%s&method=%s&sign=%s", version, resEnc, et, method, sign);
        } catch (Exception e) {
            throw new RuntimeException("API token 生成失败", e);
        }
    }

    /**
     * 查询设备属性历史数据
     */
    public Map<String, Object> queryDeviceHistory(String deviceName, String identifier,
                                                   long startMs, long endMs, int limit) {
        DeviceInfo dev = DeviceConfig.findDevice(deviceName);
        if (dev == null) {
            return Map.of("error", "设备 " + deviceName + " 不存在");
        }

        String params = String.format(
                "product_id=%s&device_name=%s&identifier=%s&start_time=%d&end_time=%d&limit=%d&sort=2",
                dev.getProductId(), dev.getDeviceName(), identifier, startMs, endMs, Math.min(limit, 100));

        String token = generateApiToken(dev);
        return apiGet("/thingmodel/query-device-property-history", params, token);
    }

    /**
     * 查询设备所有属性历史数据
     */
    public Map<String, Map<String, Object>> queryDeviceAllHistory(String deviceName,
                                                                   long startMs, long endMs, int limit) {
        DeviceInfo dev = DeviceConfig.findDevice(deviceName);
        if (dev == null) {
            return Map.of("error", Map.of("detail", "设备 " + deviceName + " 不存在"));
        }

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String identifier : dev.getProperties().keySet()) {
            Map<String, Object> history = queryDeviceHistory(deviceName, identifier, startMs, endMs, limit);
            result.put(identifier, history);
        }
        return result;
    }

    /**
     * 通过 API 查询设备所有属性最新值
     */
    public Map<String, Object> queryDeviceLatestApi(String deviceName) {
        DeviceInfo dev = DeviceConfig.findDevice(deviceName);
        if (dev == null) {
            return Map.of("error", "设备 " + deviceName + " 不存在");
        }

        String params = String.format("product_id=%s&device_name=%s",
                dev.getProductId(), dev.getDeviceName());
        String token = generateApiToken(dev);
        return apiGet("/thingmodel/query-device-property", params, token);
    }

    /**
     * 下发设备期望属性（指令下发）
     *
     * @param deviceName 设备名
     * @param identifier 属性标识符（如 F=水泵状态, I/J=PH阈值, K/L=土壤湿度阈值）
     * @param value      属性值（int/string，类型需与设备物模型定义一致）
     * @return OneNet API 响应 Map
     */
    public Map<String, Object> setDesiredProperty(String deviceName, String identifier, Object value) {
        DeviceInfo dev = DeviceConfig.findDevice(deviceName);
        if (dev == null) {
            return Map.of("error", "设备 " + deviceName + " 不存在");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put(identifier, value);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("product_id", dev.getProductId());
        body.put("device_name", dev.getDeviceName());
        body.put("params", params);

        String token = generateApiToken(dev);
        try {
            return apiPost("/thingmodel/set-device-desired-property", body, token);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "指令下发失败");
            error.put("detail", Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
            return error;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> apiGet(String path, String query, String token) {
        try {
            String url = DeviceConfig.API_BASE + path + "?" + query;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (body == null || body.isEmpty()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("error", "API 返回空响应");
                error.put("status_code", response.statusCode());
                return error;
            }
            return objectMapper.readValue(body, Map.class);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "网络错误");
            error.put("detail", Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
            return error;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> apiPost(String path, Map<String, Object> body, String token) {
        try {
            String url = DeviceConfig.API_BASE + path;
            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            if (respBody == null || respBody.isEmpty()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("error", "API 返回空响应");
                error.put("status_code", response.statusCode());
                return error;
            }
            return objectMapper.readValue(respBody, Map.class);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "网络错误");
            error.put("detail", Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
            return error;
        }
    }
}
