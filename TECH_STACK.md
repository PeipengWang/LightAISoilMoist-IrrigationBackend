# 技术栈文档

## LightAI Soil Moist Irrigation Backend

> 智能土壤湿度灌溉系统后端服务 — OneNET 物联网平台数据采集与展示

---

## 核心框架

| 技术 | 版本 | 说明 |
|------|------|------|
| **Spring Boot** | 3.5.16 | 主框架，提供依赖注入、自动配置、内嵌服务器 |
| **Java** | 17 (LTS) | 运行环境，长期支持版本 |

## 主要依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| **spring-boot-starter-web** | 3.5.16 | RESTful Web 支持，内嵌 Tomcat |
| **spring-boot-starter-validation** | 3.5.16 | Jakarta Bean Validation 参数校验 |
| **knife4j-openapi3-jakarta-spring-boot-starter** | 4.5.0 | Knife4j API 文档生成，基于 OpenAPI 3.0 / Swagger |
| **lombok** | 1.18.x | 简化 Java Bean 代码 |
| **org.eclipse.paho.client.mqttv3** | 1.2.5 | Eclipse Paho MQTT 客户端，连接 OneNET 平台 |
| **spring-boot-starter-data-jpa** | 3.5.16 | Spring Data JPA，ORM 框架 |
| **h2** | 2.3.232 | H2 嵌入式数据库（文件模式持久化），无需安装 |

## IoT 平台对接

| 协议 | 说明 |
|------|------|
| **MQTT** | 实时订阅 OneNET 设备属性上报，缓存最新数据 |
| **OneNET REST API** | HMAC-SHA256 鉴权，查询历史数据和最新值（兜底） |
| **SSE** | Server-Sent Events 向浏览器推送实时数据变化 |

## 数据库

| 配置 | 说明 |
|------|------|
| **H2DB 文件模式** | jdbc:h2:file:./data/threshold_db，数据持久化到本地文件，重启不丢失 |
| **H2 Console** | http://localhost:8086/h2-console（用户名 sa，无密码） |
| **JPA ddl-auto** | update — 启动自动建表/加列，已存在数据不丢失 |
| **方言兼容** | MODE=MySQL，便于日后迁移到 MySQL |

## 开发工具

| 工具 | 说明 |
|------|------|
| **Maven** | 项目构建与依赖管理 |
| **Knife4j UI** | 可视化 API 文档与在线调试 |
| **spring-boot-maven-plugin** | Spring Boot 打包插件 |

## 项目结构

```
src/main/java/org/example/lightaisoilmoistirrigationbackend/
├── LightAiSoilMoistIrrigationBackendApplication.java  # 启动类
├── config/
│   ├── DeviceConfig.java                              # OneNET 设备配置（产品ID/密钥/属性元数据）
│   └── Knife4jConfig.java                             # Knife4j / OpenAPI 配置
├── controller/
│   ├── DeviceController.java                          # REST API：设备列表/实时数据/历史查询/SSE推送
│   └── ThresholdController.java                       # 阈值管理：CRUD / 物候期覆盖 / 变更历史
├── service/
│   ├── DeviceDataManager.java                         # MQTT 连接管理 + 线程安全数据缓存
│   ├── OneNetApiClient.java                           # OneNET REST API 鉴权与历史查询
│   ├── ThresholdService.java                          # 阈值查询（含土壤修正）+ CRUD + 快照记录
│   ├── PhenologyService.java                          # 物候期自动判断（月份）+ 手动覆盖
│   └── DataInitializer.java                           # 预置数据（CommandLineRunner 启动执行）
├── model/
│   ├── Result.java                                    # 统一响应封装
│   └── entity/
│       ├── CropConfig.java                            # 作物配置实体
│       ├── SoilConfig.java                            # 土壤类型实体
│       ├── PhenologyStage.java                        # 物候期定义实体
│       ├── StageThreshold.java                        # 物候期阈值实体
│       ├── ThresholdSnapshot.java                     # 阈值变更快照实体
│       ├── AlertRule.java                             # 告警规则实体
│       └── DecisionLog.java                           # 决策日志实体（指令下发记录）
└── repository/
    ├── CropConfigRepository.java
    ├── SoilConfigRepository.java
    ├── PhenologyStageRepository.java
    ├── StageThresholdRepository.java
    ├── ThresholdSnapshotRepository.java
    ├── AlertRuleRepository.java
    └── DecisionLogRepository.java                     # 决策日志查询（分页+多条件筛选）

src/main/resources/
├── application.yml                                    # 应用配置
└── static/
    └── index.html                                     # 前端实时监控面板
```

## API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/` | GET | 前端监控面板 |
| `/api/devices` | GET | 设备列表与属性元数据 |
| `/api/latest` | GET | MQTT 实时缓存数据（支持 device_name 参数） |
| `/api/latest/api` | GET | OneNET REST API 最新值（兜底） |
| `/api/history` | GET | 单属性历史数据查询 |
| `/api/history/all` | GET | 设备所有属性历史数据查询 |
| `/api/stream` | GET | SSE 实时数据推送 |
| `/api/command` | POST | 指令下发（水泵开关、阈值设置）。请求体: `{device_name, identifier, value}` |
| `/api/decision-logs` | GET | 决策日志分页查询。支持筛选: device_name, identifier, decision_type, start, end, page, size |
| `/api/thresholds/current` | GET | 查询当前物候期生效的全部阈值（支持 soilType 参数） |
| `/api/thresholds/stages` | GET | 查询所有物候期列表 |
| `/api/thresholds/stages/{stageId}` | GET | 查询某物候期的所有阈值配置 |
| `/api/thresholds/{id}` | PUT | 更新单个阈值（自动记录变更快照） |
| `/api/thresholds/{id}/history` | GET | 查看阈值变更历史 |
| `/api/thresholds/phenology/override` | POST | 手动指定当前物候期 |
| `/api/thresholds/phenology/override` | DELETE | 取消手动指定，恢复自动判断 |

## Knife4j 访问

| 页面 | 地址 |
|------|------|
| Knife4j 文档 UI | http://localhost:8086/doc.html |
| Swagger UI | http://localhost:8086/swagger-ui.html |
| OpenAPI JSON | http://localhost:8086/v3/api-docs |
| H2 Console | http://localhost:8086/h2-console |

## 产品凭证（OneNet 控制台获取）

| 产品 ID | Master-APIKey | 设备名 | 设备 AccessKey |
|---------|--------------|--------|---------------|
| `09B8L0Ji9W` | `h3+7jfcX601ZDfEd5zEdi4XBwUvLIYXRR31rcc09g0k=` | DHT11 | `TEVIazZuZlNaNU1jUzJyd2ZzOG5YTFk4eU8xV2hjeDE=` |
| `FeGVC46Lne` | `+R0R0UFbF6e/sWWTmdNZG1GdI9VhNaEB0Z9xBycDXQE=` | device | `WktwTXB5RGNVOTNrdUwxaFVEbEtvQWpGSEJHMWtrT3Q=` |

## 设备属性清单

### DHT11（产品 `09B8L0Ji9W`）

| 标识符 | 名称 | 数据类型 | 单位 | 模式 |
|--------|------|---------|------|------|
| temp | 温度 | float | °C | 只读 |

### device 农业环境监测终端（产品 `FeGVC46Lne`）

| 标识符 | 名称 | 数据类型 | 单位 | 模式 |
|--------|------|---------|------|------|
| A | PH值 | float | — | 只读 |
| B | 土壤湿度 | int32 | % | 只读 |
| C | 环境温度 | int32 | °C | 只读 |
| D | 环境湿度 | int32 | % | 只读 |
| E | 光照 | int32 | lux | 只读 |
| F | 水泵状态 | enum | — | 读写 |
| G | PH状态 | enum | — | 只读 |
| H | 土壤湿度状态 | enum | — | 只读 |
| I | PH阈值低 | string | — | 读写 |
| J | PH阈值高 | string | — | 读写 |
| K | 土壤湿度阈值低 | string | % | 读写 |
| L | 土壤湿度阈值高 | string | % | 读写 |
| M | 氮含量 | int32 | mg/kg | 只读 |
| N | 磷含量 | int32 | mg/kg | 只读 |
| O | 钾含量 | int32 | mg/kg | 只读 |

## MQTT 连接方式

| 身份类型 | Protocol | 端口 | 用途 | 状态 |
|---------|----------|------|------|------|
| 设备身份 | TCP | 1883 | STM32 物理设备连接 | ✅ 正常 |
| 设备身份 | TCP | 1883 | 后端设备身份连接 | ❌ 与物理设备冲突 |
| 应用身份 | TCP/SSL | 1883/8883 | 后端应用级订阅 | ❌ 需 MQ 服务 |
| MQ 服务 | SSL+TLSv1.2 | 8883 | 消息队列订阅（官方推荐） | ⚠️ 待配置 |

## TLS 证书

- 文件：`src/main/resources/MQ-certificate-release-0711.pem`
- 颁发者：CN=OneNET MQ, O=CMIOT, C=CN
- 有效期：2019-06-13 至 2049-06-05
- 用途：OneNet MQ 服务 SSL/TLS 认证

## 测试用例

| 文件 | 说明 |
|------|------|
| `src/test/java/Test/MiotMqttTest.java` | 应用身份 MQTT 实时接收 + API 历史查询 + 指令下发测试 |
| `src/test/java/Test/WaterPumpControlTest.java` | 水泵开关控制测试（on/off/status），通过 REST API set-device-desired-property 下发 |
| `src/test/java/.../OneNetApiClientTest.java` | OneNetApiClient 所有属性历史查询 + 设备不存在边界测试 |
| `src/test/java/.../DeviceDataManagerTest.java` | DeviceDataManager 缓存刷新策略 + API 容错测试 |
| `src/test/java/.../LightAiSoilMoistIrrigationBackendApplicationTests.java` | Spring Boot 启动测试 |
| `src/test/java/.../ThresholdServiceTest.java` | 阈值查询（含5种土壤修正）+ CRUD + 快照 + 历史 8条测试 |
| `src/test/java/.../PhenologyServiceTest.java` | 物候期按月判断 + 跨年 + 手动覆盖/取消 6条测试 |
| `src/test/java/.../DecisionLogRepositoryTest.java` | 决策日志 JPA 操作 + 分页查询 + Specification 组合筛选 9条测试 |

运行 MiotMqttTest：
```bash
JAVA_HOME=D:/jdk/jdk17 ./mvnw compile test-compile -DskipTests
# 获取依赖 classpath 后直接运行
java -cp "target/test-classes;target/classes;{deps}" Test.MiotMqttTest
```

运行 WaterPumpControlTest（JUnit 5，每条用例独立运行）：
```bash
# 开启水泵
mvn test -Dtest=Test.WaterPumpControlTest#turnPumpOn
# 关闭水泵
mvn test -Dtest=Test.WaterPumpControlTest#turnPumpOff
# 查询水泵状态
mvn test -Dtest=Test.WaterPumpControlTest#queryPumpStatus
# 查询所有属性
mvn test -Dtest=Test.WaterPumpControlTest#queryAllProperties
# 开启后验证
mvn test -Dtest=Test.WaterPumpControlTest#turnOnThenVerify
# 关闭后验证
mvn test -Dtest=Test.WaterPumpControlTest#turnOffThenVerify
# 切换水泵（读 → 翻 → 验）
mvn test -Dtest=Test.WaterPumpControlTest#togglePump
```

## 预置数据

应用首次启动时，`DataInitializer` 自动写入以下数据（若已存在则跳过）：

| 数据类别 | 数量 | 内容 |
|----------|------|------|
| 作物配置 | 1 | 红富士苹果（滴灌） |
| 土壤类型 | 5 | SAND / SANDY_LOAM / LOAM / CLAY_LOAM / CLAY（含持水量、萎蔫系数、修正系数） |
| 物候期 | 7 | 休眠期 → 萌芽期 → 花期 → 幼果期 → 膨大期 → 成熟期 → 落叶期 |
| 阈值配置 | 47 | 每阶段 × 7 类属性（湿度上下限/pH上下限/温度上下限/光照下限） |
| 告警规则 | 7 | 数据中断、pH异常、温度极端、湿度骤降、灌溉异常、病害风险、霜冻风险 |

## 数据流说明

```
DeviceDataManager.getDeviceLatest(deviceName)
  ├── 距上次 API 刷新 < 2s → 直接返回缓存
  └── 距上次 API 刷新 ≥ 2s → OneNET REST API 查询 → 更新缓存 → 返回

阈值查询 (ThresholdService.getCurrentThresholds):
  1. PhenologyService 判断当前物候期（手动覆盖 > 按月判断）
  2. 查询该物候期的所有阈值（优先该土壤特定值，否则通用基准兜底）
  3. 湿度类阈值（B、D）× soil_config.moisture_correction_factor
  4. 返回最终有效阈值
```

## 方案文档

详细方案说明见 `OneNet平台对接方案.md`。

## 启动方式

```bash
# 开发环境启动
mvn spring-boot:run

# 或编译后启动
mvn clean package -DskipTests
java -jar target/LightAISoilMoist-IrrigationBackend-0.0.1-SNAPSHOT.jar
```
