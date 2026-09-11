package org.example.lightaisoilmoistirrigationbackend.service;

import org.example.lightaisoilmoistirrigationbackend.config.DeviceConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OneNetApiClientTest {

    @Test
    void queryDeviceAllHistory_shouldReturnErrorWhenDeviceNotFound() {
        OneNetApiClient client = new OneNetApiClient();
        long now = System.currentTimeMillis();

        Map<String, Map<String, Object>> result = client.queryDeviceAllHistory(
                "NON_EXISTENT_DEVICE", now - 86400000, now, 100);

        assertTrue(result.containsKey("error"));
    }

    @Test
    void queryDeviceAllHistory_shouldReturnAllPropertiesForValidDevice() {
        OneNetApiClient client = new OneNetApiClient();
        long now = System.currentTimeMillis();
        long start = now - 3600_000;

        String deviceName = DeviceConfig.DEVICE_DHT11.getDeviceName();
        Map<String, Map<String, Object>> result = client.queryDeviceAllHistory(
                deviceName, start, now, 10);

        assertNotNull(result);
        assertFalse(result.containsKey("error"));
        for (String expectedId : DeviceConfig.DEVICE_DHT11.getProperties().keySet()) {
            assertTrue(result.containsKey(expectedId),
                    "结果应包含属性: " + expectedId);
        }
    }

    @Test
    void queryDeviceAllHistory_shouldIncludeNpkPropertiesForAgricultureDevice() {
        OneNetApiClient client = new OneNetApiClient();
        long now = System.currentTimeMillis();
        long start = now - 3600_000;

        String deviceName = DeviceConfig.DEVICE_AGRICULTURE.getDeviceName();
        Map<String, Map<String, Object>> result = client.queryDeviceAllHistory(
                deviceName, start, now, 10);

        assertNotNull(result);
        assertFalse(result.containsKey("error"));
        assertTrue(result.containsKey("M"), "结果应包含氮(M)");
        assertTrue(result.containsKey("N"), "结果应包含磷(N)");
        assertTrue(result.containsKey("O"), "结果应包含钾(O)");
    }

    @Test
    void queryDeviceHistory_shouldSupportNpkIdentifier() {
        OneNetApiClient client = new OneNetApiClient();
        long now = System.currentTimeMillis();
        long start = now - 3600_000;

        Map<String, Object> result = client.queryDeviceHistory(
                DeviceConfig.DEVICE_AGRICULTURE.getDeviceName(), "M", start, now, 10);

        assertNotNull(result);
        assertFalse(result.containsKey("error"),
                "NPK 属性 M 历史查询不应失败: " + result.getOrDefault("error", ""));
    }

    @Test
    void setDesiredProperty_shouldReturnErrorForNonExistentDevice() {
        OneNetApiClient client = new OneNetApiClient();

        Map<String, Object> result = client.setDesiredProperty("NON_EXISTENT_DEVICE", "F", 1);

        assertNotNull(result);
        assertTrue(result.containsKey("error"), "不存在的设备应返回错误");
    }

    @Test
    @SuppressWarnings("unchecked")
    void setDesiredProperty_shouldWorkForPumpControl() {
        OneNetApiClient client = new OneNetApiClient();
        String deviceName = DeviceConfig.DEVICE_AGRICULTURE.getDeviceName();

        // 先读取当前水泵状态，然后下发相同值（无副作用）
        Map<String, Object> queryResult = client.queryDeviceLatestApi(deviceName);
        assertFalse(queryResult.containsKey("error"), "查询失败: " + queryResult);

        List<Map<String, Object>> data = (List<Map<String, Object>>) queryResult.get("data");
        int currentState = 0;
        for (Map<String, Object> item : data) {
            if ("F".equals(item.get("identifier"))) {
                currentState = Integer.parseInt(String.valueOf(item.get("value")));
                break;
            }
        }

        // 下发与当前状态相同的值（保持原状态，无实际变化）
        Map<String, Object> cmdResult = client.setDesiredProperty(deviceName, "F", currentState);
        assertNotNull(cmdResult);
        // OneNet API 返回 code=0 表示成功
        Object code = cmdResult.get("code");
        assertNotNull(code, "API 应返回 code 字段");
        assertEquals(0, ((Number) code).intValue(),
                "指令下发应成功，响应: " + cmdResult);
    }
}