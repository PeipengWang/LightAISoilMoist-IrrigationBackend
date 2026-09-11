package Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;

/**
 * DHT11 设备实时数据接收测试 — OneNet 产品级（应用身份）MQTT 接入
 *
 * 参照官方 demo (OneNET-MQ-Reconn-demo) 关键配置:
 *   1. ssl://域名:8883 + TLSv1.2 + OneNet CA 证书
 *   2. 自定义 HostnameVerifier（因服务器证书 SAN 不含 IP）
 *   3. MQTT 协议版本 3.1.1，CleanSession=true，QoS=1
 *   4. HMAC-SHA1 Token 鉴权（产品 Master-APIKey）
 *
 * 测试功能:
 *   1. 应用身份 MQTT 实时接收设备上报
 *   2. REST API 历史数据查询
 *   3. REST API 指令下发
 */
public class MiotMqttTest {

    // ==================== 产品 09B8L0Ji9W（DHT11） ====================
    private static final String PRODUCT_ID = "09B8L0Ji9W";
    private static final String MASTER_API_KEY = "h3+7jfcX601ZDfEd5zEdi4XBwUvLIYXRR31rcc09g0k=";
    private static final String DEVICE_NAME = "DHT11";

    // ==================== OneNet MQTT 连接参数 ====================
    /** OneNet MQTT 域名（官方 demo 也使用此域名对应的 IP: 183.230.40.96） */
    private static final String MQTT_HOST_DOMAIN = "mqtts.heclouds.com";
    private static final String MQTT_HOST_IP = "183.230.40.96";
    private static final int MQTT_PORT_SSL = 8883;
    private static final int MQTT_PORT_TCP = 1883;
    private static final String APP_CLIENT_ID = "soil_test_backend_001";
    private static final String API_BASE = "https://iot-api.heclouds.com";

    // PEM 证书路径
    private static final String CERT_FILE = "src/main/resources/MQ-certificate-release-0711.pem";

    // 订阅主题: 通配符 + 匹配产品下所有设备的属性上报
    private static final String SUB_TOPIC = "$sys/" + PRODUCT_ID + "/+/thing/property/post";

    // 测试超时
    private static final int WAIT_SECONDS = 60;

    private static MqttClient mqttClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ==================== 入口 ====================

    public static void main(String[] args) throws Exception {
        System.out.println("==============================================");
        System.out.println("  DHT11 Real-time Data Test (App Identity MQTT)");
        System.out.println("==============================================");
        System.out.println("Product ID : " + PRODUCT_ID);
        System.out.println("Device     : " + DEVICE_NAME);
        System.out.println("Subscribe  : " + SUB_TOPIC);
        System.out.println("Timeout    : " + WAIT_SECONDS + "s");
        System.out.println("==============================================\n");

        // ====== 1. MQTT 实时接收 ======
        boolean connected = startMqttAppClient();
        if (!connected) {
            System.err.println("\n[FAIL] MQTT connection failed.");
            System.err.println("Troubleshooting:");
            System.err.println("  1. DNS: Can you resolve mqtts.heclouds.com?");
            System.err.println("  2. AccessKey: Is Master-APIKey correct?");
            System.err.println("  3. OneNet may require MQ service for app-level MQTT.");
            System.err.println("     Create MQ instance in OneNet console, then update config.");
            System.exit(1);
        }

        // ====== 2. 历史查询 ======
        Thread.sleep(3000);
        testHistoryQuery();

        // ====== 3. 指令演示 ======
        testCommandSend();

        // ====== 4. 等待实时数据 ======
        System.out.println("\n[INFO] Listening for device data (" + WAIT_SECONDS + "s)...\n");
        for (int i = 0; i < WAIT_SECONDS; i++) {
            Thread.sleep(1000);
        }

        // ====== 5. 最终查询 ======
        testHistoryQuery();

        System.out.println("\n[OK] Test completed.");
        cleanup();
        System.exit(0);
    }

    // ==================== TLS (参照 demo SslUtil.java) ====================

    /**
     * 创建 TLSv1.2 SSL Socket Factory
     *
     * 与官方 demo 的区别:
     *   - JDK 17 原生读取 PEM（demo 使用 BouncyCastle PEMReader）
     *   - 添加自定义 HostnameVerifier，兼容 IP 直连场景
     */
    private static SSLSocketFactory createSslSocketFactory() {
        try {
            // 1. 加载 OneNet CA 证书
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate caCert;
            try (InputStream is = new FileInputStream(CERT_FILE)) {
                caCert = cf.generateCertificate(is);
            }
            System.out.println("[TLS] CA cert loaded: CN="
                    + ((X509Certificate) caCert).getSubjectX500Principal().getName());

            // 2. 导入 KeyStore
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("onenet-ca", caCert);

            // 3. 创建 TrustManager
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            // 4. 创建 TLSv1.2 SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, tmf.getTrustManagers(), null);

            System.out.println("[TLS] TLSv1.2 SSLContext created OK");
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            System.err.println("[TLS] ERROR: " + e.getMessage());
            throw new RuntimeException("SSL factory creation failed", e);
        }
    }

    /**
     * 全通配 HostnameVerifier — 信任所有主机名
     * 原因: 直接连接 IP 时，服务器证书 SAN 不含 IP 地址，默认校验会失败
     */
    private static final HostnameVerifier TRUST_ALL = (hostname, session) -> true;

    // ==================== Token (参照 demo Token.java) ====================

    /**
     * 应用身份 MQTT Token (HMAC-SHA1)
     * 格式: version=2018-10-31&res={url_encoded_res}&et={expire}&method=sha1&sign={url_encoded_sig}
     */
    private static String generateAppMqttToken() {
        String version = "2018-10-31";
        String res = "products/" + PRODUCT_ID;
        String et = String.valueOf(System.currentTimeMillis() / 1000 + 3600);
        String method = "sha1";
        String org = et + "\n" + method + "\n" + res + "\n" + version;

        try {
            byte[] key = Base64.getDecoder().decode(MASTER_API_KEY);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] signBytes = mac.doFinal(org.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(
                    Base64.getEncoder().encodeToString(signBytes), StandardCharsets.UTF_8);
            String resEnc = URLEncoder.encode(res, StandardCharsets.UTF_8);
            return String.format("version=%s&res=%s&et=%s&method=%s&sign=%s",
                    version, resEnc, et, method, sign);
        } catch (Exception e) {
            throw new RuntimeException("MQTT token generation failed", e);
        }
    }

    // ==================== MQTT ====================

    private static boolean startMqttAppClient() {
        // 尝试顺序: 域名 SSL > IP SSL > IP TCP
        String[][] targets = {
                { "ssl", MQTT_HOST_DOMAIN, String.valueOf(MQTT_PORT_SSL) },
                { "ssl", MQTT_HOST_IP,   String.valueOf(MQTT_PORT_SSL) },
                { "tcp", MQTT_HOST_IP,   String.valueOf(MQTT_PORT_TCP) },
        };

        for (String[] t : targets) {
            String proto = t[0], host = t[1], port = t[2];
            String url = proto + "://" + host + ":" + port;
            boolean ssl = "ssl".equals(proto);
            System.out.println("[MQTT] Trying: " + url);
            try {
                boolean ok = doConnect(url, ssl);
                if (ok) return true;
            } catch (MqttException e) {
                System.err.println("[MQTT] Failed: " + e.getMessage()
                        + " (rc=" + e.getReasonCode() + ")");
                if (e.getCause() != null && e.getCause().getMessage() != null) {
                    System.err.println("[MQTT]   cause: " + e.getCause().getMessage());
                }
            }
        }
        return false;
    }

    private static boolean doConnect(String brokerUrl, boolean useSsl) throws MqttException {
        mqttClient = new MqttClient(brokerUrl, APP_CLIENT_ID, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(PRODUCT_ID);
        options.setPassword(generateAppMqttToken().toCharArray());
        options.setCleanSession(true);
        options.setConnectionTimeout(20);
        options.setKeepAliveInterval(30);
        options.setAutomaticReconnect(false);
        // MQTT 3.1.1 (参照官方 demo)
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

        if (useSsl) {
            SSLSocketFactory ssf = createSslSocketFactory();
            options.setSocketFactory(ssf);
            // 禁用主机名验证器（兼容 IP 直连）
            options.setHttpsHostnameVerificationEnabled(false);
        }

        System.out.println("[MQTT] Connecting (MQTT 3.1.1, SSL=" + useSsl + ")...");

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                String msg = cause != null ? cause.getMessage() : "unknown";
                if (msg == null) msg = "unknown";
                System.err.println("[MQTT] Lost: " + msg);
                reconnect(brokerUrl, useSsl);
            }

            private void reconnect(String url, boolean ssl) {
                new Thread(() -> {
                    int retry = 0;
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            Thread.sleep(retry < 20 ? 1000 : 10000);
                        } catch (InterruptedException ie) {
                            return;
                        }
                        try {
                            try { mqttClient.close(); } catch (Exception ignored) {}

                            MqttConnectOptions opts = new MqttConnectOptions();
                            opts.setUserName(PRODUCT_ID);
                            opts.setPassword(generateAppMqttToken().toCharArray());
                            opts.setCleanSession(true);
                            opts.setConnectionTimeout(20);
                            opts.setKeepAliveInterval(30);
                            opts.setAutomaticReconnect(false);
                            opts.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
                            if (ssl) {
                                opts.setSocketFactory(createSslSocketFactory());
                                opts.setHttpsHostnameVerificationEnabled(false);
                            }

                            mqttClient = new MqttClient(url, APP_CLIENT_ID, new MemoryPersistence());
                            mqttClient.setCallback(cb());
                            mqttClient.connect(opts);
                            mqttClient.subscribe(SUB_TOPIC, 1);
                            System.out.println("[MQTT] Reconnect OK (#" + retry + ")");
                            return;
                        } catch (Exception e) {
                            retry++;
                            if (retry <= 3 || retry % 10 == 0) {
                                System.err.println("[MQTT] Reconnect #" + retry + " failed: "
                                        + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                            }
                        }
                    }
                }, "mqtt-reconnect").start();
            }

            private MqttCallback cb() {
                return this;
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                System.out.println("\n======== DEVICE DATA RECEIVED ========");
                System.out.println("Topic: " + topic);

                String[] parts = topic.split("/");
                String devName = parts.length > 2 ? parts[2] : "?";
                System.out.println("Device: " + devName);

                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> root = objectMapper.readValue(payload, Map.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) root.get("params");
                    if (params != null && !params.isEmpty()) {
                        System.out.println("Data: " + params);
                    } else {
                        System.out.println("Raw: " + payload);
                    }
                } catch (Exception e) {
                    System.out.println("Raw: " + payload);
                }
                System.out.println("======================================\n");
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        mqttClient.connect(options);
        mqttClient.subscribe(SUB_TOPIC, 1);
        System.out.println("[MQTT] Connected! Subscribed: " + SUB_TOPIC);
        return true;
    }

    // ==================== REST API ====================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> apiGet(String path, String query) {
        try {
            String token = generateApiToken();
            String url = API_BASE + path + "?" + query;
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
                return Map.of("error", "empty response", "status_code", response.statusCode());
            }
            return objectMapper.readValue(body, Map.class);
        } catch (Exception e) {
            return Map.of("error", "request failed",
                    "detail", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private static String generateApiToken() {
        String version = "2022-05-01";
        String res = "products/" + PRODUCT_ID + "/devices/" + DEVICE_NAME;
        String et = String.valueOf(System.currentTimeMillis() / 1000 + 3600);
        String method = "sha256";
        String org = et + "\n" + method + "\n" + res + "\n" + version;

        try {
            byte[] key = Base64.getDecoder().decode(MASTER_API_KEY);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] signBytes = mac.doFinal(org.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(
                    Base64.getEncoder().encodeToString(signBytes), StandardCharsets.UTF_8);
            String resEnc = URLEncoder.encode(res, StandardCharsets.UTF_8);
            return String.format("version=%s&res=%s&et=%s&method=%s&sign=%s",
                    version, resEnc, et, method, sign);
        } catch (Exception e) {
            throw new RuntimeException("API token generation failed", e);
        }
    }

    // ==================== Tests ====================

    private static void testHistoryQuery() {
        System.out.println("\n>>> [API] Query DHT11 'temp' history (last 24h, top 3) <<<");
        long endMs = System.currentTimeMillis();
        long startMs = endMs - 24 * 3600 * 1000L;

        String query = String.format(
                "product_id=%s&device_name=%s&identifier=%s&start_time=%d&end_time=%d&limit=%d&sort=2",
                PRODUCT_ID, DEVICE_NAME, "temp", startMs, endMs, 3);

        Map<String, Object> result = apiGet("/thingmodel/query-device-property-history", query);
        System.out.println("Response: " + result);
    }

    private static void testCommandSend() {
        System.out.println("\n>>> [API] Command Send (demo mode) <<<");
        System.out.println("  F = water pump (0/1)");
        System.out.println("  I/J = pH thresholds");
        System.out.println("  K/L = moisture thresholds");
    }

    private static void cleanup() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
            }
        } catch (Exception ignored) {}
    }
}
