package org.example.lightaisoilmoistirrigationbackend.config;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DeviceConfig {

    public static final String MQTT_SERVER = "183.230.40.96";
    public static final int MQTT_PORT = 1883;
    public static final String API_BASE = "https://iot-api.heclouds.com";

    @Getter
    public static class PropertyMeta {
        private final String name;
        private final String unit;
        private final String dataType;
        private final String mode;
        private final Map<String, String> enumDesc;

        public PropertyMeta(String name, String unit, String dataType, String mode, Map<String, String> enumDesc) {
            this.name = name;
            this.unit = unit != null ? unit : "";
            this.dataType = dataType;
            this.mode = mode != null ? mode : "只读";
            this.enumDesc = enumDesc != null ? enumDesc : Collections.emptyMap();
        }

        public PropertyMeta(String name, String unit, String dataType, String mode) {
            this(name, unit, dataType, mode, Collections.emptyMap());
        }

        public PropertyMeta(String name, String unit, String dataType) {
            this(name, unit, dataType, "只读", Collections.emptyMap());
        }
    }

    @Getter
    public static class DeviceInfo {
        private final String deviceName;
        private final String productId;
        private final String accessKey;
        private final Map<String, PropertyMeta> properties;

        public DeviceInfo(String deviceName, String productId, String accessKey, Map<String, PropertyMeta> properties) {
            this.deviceName = deviceName;
            this.productId = productId;
            this.accessKey = accessKey;
            this.properties = properties;
        }
    }

    public static final DeviceInfo DEVICE_DHT11;
    public static final DeviceInfo DEVICE_AGRICULTURE;
    public static final List<DeviceInfo> ALL_DEVICES;

    static {
        // 设备1: DHT11 温湿度传感器
        Map<String, PropertyMeta> dht11Props = new LinkedHashMap<>();
        dht11Props.put("temp", new PropertyMeta("温度", "°C", "float"));
        DEVICE_DHT11 = new DeviceInfo("DHT11", "09B8L0Ji9W",
                "TEVIazZuZlNaNU1jUzJyd2ZzOG5YTFk4eU8xV2hjeDE=", dht11Props);

        // 设备2: 农业环境监测终端
        Map<String, PropertyMeta> agriProps = new LinkedHashMap<>();
        agriProps.put("A", new PropertyMeta("PH值", "", "float"));
        agriProps.put("B", new PropertyMeta("土壤湿度", "%", "int32"));
        agriProps.put("C", new PropertyMeta("环境温度", "°C", "int32"));
        agriProps.put("D", new PropertyMeta("环境湿度", "%", "int32"));
        agriProps.put("E", new PropertyMeta("光照", "lux", "int32"));
        agriProps.put("F", new PropertyMeta("水泵状态", "", "enum", "读写",
                Map.of("0", "关闭", "1", "开启")));
        agriProps.put("G", new PropertyMeta("PH状态", "", "enum", "只读",
                Map.of("0", "正常", "1", "报警")));
        agriProps.put("H", new PropertyMeta("土壤湿度状态", "", "enum", "只读",
                Map.of("0", "正常", "1", "异常")));
        agriProps.put("I", new PropertyMeta("PH阈值低", "", "string", "读写"));
        agriProps.put("J", new PropertyMeta("PH阈值高", "", "string", "读写"));
        agriProps.put("K", new PropertyMeta("土壤湿度阈值低", "%", "string", "读写"));
        agriProps.put("L", new PropertyMeta("土壤湿度阈值高", "%", "string", "读写"));
        agriProps.put("M", new PropertyMeta("氮含量", "mg/kg", "int32"));
        agriProps.put("N", new PropertyMeta("磷含量", "mg/kg", "int32"));
        agriProps.put("O", new PropertyMeta("钾含量", "mg/kg", "int32"));
        DEVICE_AGRICULTURE = new DeviceInfo("device", "FeGVC46Lne",
                "WktwTXB5RGNVOTNrdUwxaFVEbEtvQWpGSEJHMWtrT3Q=", agriProps);

        ALL_DEVICES = List.of(DEVICE_DHT11, DEVICE_AGRICULTURE);
    }

    public static DeviceInfo findDevice(String deviceName) {
        for (DeviceInfo dev : ALL_DEVICES) {
            if (dev.getDeviceName().equals(deviceName)) {
                return dev;
            }
        }
        return null;
    }
}
