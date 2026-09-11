package org.example.lightaisoilmoistirrigationbackend.service;

import org.example.lightaisoilmoistirrigationbackend.config.DeviceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceDataManagerTest {

    @Mock
    private OneNetApiClient apiClient;

    private DeviceDataManager dataManager;

    @BeforeEach
    void setUp() {
        dataManager = new DeviceDataManager(apiClient);
    }

    @Test
    void getDeviceLatest_shouldReturnEmptyMapWhenDeviceNotFound() {
        Map<String, Map<String, Object>> result = dataManager.getDeviceLatest("NON_EXISTENT_DEVICE");

        assertTrue(result.isEmpty());
    }

    @Test
    void getDeviceLatest_shouldRefreshFromApiOnFirstCall() {
        String deviceName = DeviceConfig.DEVICE_DHT11.getDeviceName();

        Map<String, Object> apiResponse = Map.of(
                "code", 0,
                "msg", "SUCCESS",
                "data", List.of(
                        Map.of("identifier", "temp", "value", 26.5, "time", 1680000000L)
                )
        );
        when(apiClient.queryDeviceLatestApi(deviceName)).thenReturn(apiResponse);

        Map<String, Map<String, Object>> result = dataManager.getDeviceLatest(deviceName);

        assertNotNull(result);
        verify(apiClient, times(1)).queryDeviceLatestApi(deviceName);

        Map<String, Object> tempData = result.get("temp");
        assertNotNull(tempData);
        assertEquals(26.5, tempData.get("value"));
        assertEquals(1680000000L, tempData.get("time"));
        assertEquals("温度", tempData.get("name"));
        assertEquals("°C", tempData.get("unit"));
    }

    @Test
    void getDeviceLatest_shouldNotRefreshWithinInterval() {
        String deviceName = DeviceConfig.DEVICE_DHT11.getDeviceName();

        Map<String, Object> apiResponse = Map.of(
                "code", 0,
                "msg", "SUCCESS",
                "data", List.of(
                        Map.of("identifier", "temp", "value", 25.0, "time", 1680000000L)
                )
        );
        when(apiClient.queryDeviceLatestApi(deviceName)).thenReturn(apiResponse);

        // 第一次调用触发 API 刷新
        dataManager.getDeviceLatest(deviceName);
        verify(apiClient, times(1)).queryDeviceLatestApi(deviceName);

        // 紧接第二次调用：距上次刷新不足 2 秒，不触发 API
        Map<String, Map<String, Object>> result2 = dataManager.getDeviceLatest(deviceName);
        verify(apiClient, times(1)).queryDeviceLatestApi(deviceName);

        Map<String, Object> tempData = result2.get("temp");
        assertEquals(25.0, tempData.get("value"));
    }

    @Test
    void getDeviceLatest_shouldHandleApiErrorGracefully() {
        String deviceName = DeviceConfig.DEVICE_DHT11.getDeviceName();

        Map<String, Object> errorResponse = Map.of("error", "网络错误", "detail", "timeout");
        when(apiClient.queryDeviceLatestApi(deviceName)).thenReturn(errorResponse);

        Map<String, Map<String, Object>> result = dataManager.getDeviceLatest(deviceName);

        assertNotNull(result);
        assertTrue(result.containsKey("temp"), "即使 API 失败，也应返回属性结构");
        assertNull(result.get("temp").get("value"), "API 失败时 value 仍为 null");
        verify(apiClient, times(1)).queryDeviceLatestApi(deviceName);
    }

    @Test
    void getAllLatest_shouldReturnAllDevices() {
        for (DeviceConfig.DeviceInfo dev : DeviceConfig.ALL_DEVICES) {
            String firstPropId = dev.getProperties().keySet().iterator().next();
            Map<String, Object> apiResponse = Map.of(
                    "code", 0,
                    "msg", "SUCCESS",
                    "data", List.of(
                            Map.of("identifier", firstPropId, "value", 42, "time", 1680000000L)
                    )
            );
            when(apiClient.queryDeviceLatestApi(dev.getDeviceName())).thenReturn(apiResponse);
        }

        Map<String, Map<String, Map<String, Object>>> result = dataManager.getAllLatest();

        assertEquals(DeviceConfig.ALL_DEVICES.size(), result.size());
        for (DeviceConfig.DeviceInfo dev : DeviceConfig.ALL_DEVICES) {
            assertTrue(result.containsKey(dev.getDeviceName()),
                    "结果应包含设备: " + dev.getDeviceName());
        }
    }

    @Test
    void agricultureDeviceCache_shouldContainNpkProperties() {
        String deviceName = DeviceConfig.DEVICE_AGRICULTURE.getDeviceName();
        Map<String, Object> apiResponse = Map.of(
                "code", 0,
                "msg", "SUCCESS",
                "data", List.of()
        );
        when(apiClient.queryDeviceLatestApi(deviceName)).thenReturn(apiResponse);

        Map<String, Map<String, Object>> result = dataManager.getDeviceLatest(deviceName);

        assertNotNull(result);
        assertTrue(result.containsKey("M"), "缓存应包含氮(M)属性");
        assertTrue(result.containsKey("N"), "缓存应包含磷(N)属性");
        assertTrue(result.containsKey("O"), "缓存应包含钾(O)属性");

        Map<String, Object> mData = result.get("M");
        assertEquals("氮含量", mData.get("name"));
        assertEquals("mg/kg", mData.get("unit"));
        assertEquals("int32", mData.get("data_type"));
        assertEquals("只读", mData.get("mode"));
    }
}
