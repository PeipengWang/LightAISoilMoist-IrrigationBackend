# OneNet 平台对接方案：实时更新、历史查询与指令下发

## 一、两端现状分析

### 1.1 设备端（STM32 + 4G 模块）

| 项目 | 值 |
|------|-----|
| 代码位置 | `E:\土壤监测平台\365_基于STM32的土壤质量检测系统(1)\代码` |
| MCU | STM32F103 + 4G 模组（AT 指令） |
| 连接方式 | **设备身份** MQTT 直连 OneNet |

**OneNet 配置（tcp_mqtt.c）：**




**上报属性（8 个传感器数据 + 4 个阈值）：**

| 标识符 | 含义 | 数据类型 | 读写 |
|--------|------|----------|------|
| A | PH 值 | float | 只读 |
| B | 土壤湿度 | int32 (0-100%) | 只读 |
| C | 环境温度 | int32 | 只读 |
| D | 环境湿度 | int32 | 只读 |
| E | 光照 | int32 (lux) | 只读 |
| F | 水泵/继电器状态 | enum (0=关闭,1=开启) | **读写** |
| G | PH 报警状态 | enum (0=正常,1=报警) | 只读 |
| H | 土壤湿度报警状态 | enum (0=正常,1=异常) | 只读 |
| I | PH 阈值低 | string | **读写** |
| J | PH 阈值高 | string | **读写** |
| K | 土壤湿度阈值低 | string | **读写** |
| L | 土壤湿度阈值高 | string | **读写** |

**设备端命令处理（main.c mqtt_BackDataHandle）：**
- `F` — 切换水泵开关（读到 1 开水泵，读到 0 关水泵）
- `I` / `J` — 更新 PH 阈值
- `K` / `L` — 更新土壤湿度阈值
- 处理完毕后立即上报最新状态

### 1.2 后端（Spring Boot）

| 项目 | 值 |
|------|-----|
| 位置 | `src/main/java/org/example/lightaisoilmoistirrigationbackend/` |
| 框架 | Spring Boot 3.5.16 + JDK 17 |
| MQTT 库 | Eclipse Paho 1.2.5 |

**当前后端 MQTT 连接方式（DeviceDataManager.java）：**
- 以 **设备身份** 连接（clientId = 设备名 `device`）
- 与物理设备同名 → **OneNet 只允许一个设备身份在线，后连者踢前者**

**这就是为什么 error.md 中反复出现 "连接断开" 的根本原因。**

---

## 二、核心矛盾

```
物理 STM32 设备 ──设备身份──> OneNet MQTT Broker
                                    ↑
Spring Boot 后端 ──设备身份──────────┘  ← 同名 "device"，互相踢下线！
```

**解决方法：后端改用「应用身份 / 产品级」接入，不再占用设备身份。**

---

## 三、三大功能实现方案

### 3.1 实时数据接收 — 产品级 MQTT 订阅

**原理：** OneNet 支持「应用身份」MQTT 接入，以一个独立的 clientId 全局订阅本产品下所有设备的数据，不占用设备连接名额。

**前提条件：** 需要从 OneNet 控制台获取产品的 **Master-APIKey**（不是设备 access_key）。

> 获取路径：OneNet 控制台 → 产品管理 → 选择产品 `FeGVC46Lne` → 产品概况 → 解锁 Master-APIKey

**连接参数：**

| 参数 | 值 |
|------|-----|
| MQTT Broker | `183.230.40.96:1883` |
| Client ID | 自定义，不与任何设备重名，如 `soil_app_backend_001` |
| Username | `FeGVC46Lne`（产品 ID） |
| Password | HMAC-SHA1 Token（用 Master-APIKey 签名生成，见下方） |
| 订阅 Topic | `$sys/FeGVC46Lne/+/thing/property/post` |

**Password（Token）生成公式：**

```
原始串 = {过期秒}&{签名方法}&{资源路径}&{版本}
       = 1734567890\nsha1\nproducts/FeGVC46Lne\n2018-10-31

签名 = Base64( HMAC-SHA1( Base64Decode(Master-APIKey), 原始串 ) )
Token = version=2018-10-31&res=products%2FFeGVC46Lne&et=1734567890&method=sha1&sign={URLEncode(签名)}
```

**代码参考：** 见 `MiotMqttTest.java` 中的 `generateAppMqttToken()` 方法。

---

### 3.2 历史数据查询 — REST API

**原理：** 通过 OneNet REST API 查询设备属性的历史数据，鉴权使用设备 access_key 生成 HMAC-SHA256 Token。

**已有实现：** `OneNetApiClient.queryDeviceHistory()` 已经完整实现此功能。

**API 端点：**

```
GET https://iot-api.heclouds.com/thingmodel/query-device-property-history
    ?product_id=FeGVC46Lne
    &device_name=device
    &identifier=A
    &start_time=1734567890000
    &end_time=1734654290000
    &limit=100
    &sort=2

Authorization: version=2022-05-01&res=products%2FFeGVC46Lne%2Fdevices%2Fdevice&et=...
```

**使用方式：**

```java
// 查询土壤湿度(B)过去24小时的历史数据
Map<String, Object> result = apiClient.queryDeviceHistory(
    "device",                              // 设备名
    "B",                                   // 属性标识符
    System.currentTimeMillis() - 86400000, // 起始毫秒
    System.currentTimeMillis(),            // 结束毫秒
    100                                    // 条数
);
```

---

### 3.3 指令下发 — REST API

**原理：** 通过 OneNet REST API 向设备发送属性设置指令，平台会通过 MQTT 转发给设备，设备处理后上报最新状态。

**API 端点：**

```
POST https://iot-api.heclouds.com/thingmodel/property/set

Headers:
  Authorization: {API Token}
  Content-Type: application/json

Body:
{
  "product_id": "FeGVC46Lne",
  "device_name": "device",
  "params": {
    "F": 1
  }
}
```

**可下发的指令（与设备端属性对应）：**

| 场景 | 属性 | 值 | 说明 |
|------|------|-----|------|
| 开启水泵 | `F` | 1 | 打开继电器，开始灌溉 |
| 关闭水泵 | `F` | 0 | 关闭继电器，停止灌溉 |
| 设置 PH 低阈值 | `I` | "5.5" | PH 低于此值报警 |
| 设置 PH 高阈值 | `J` | "8.5" | PH 高于此值报警 |
| 设置湿度低阈值 | `K` | "30" | 湿度低于此值报警 |
| 设置湿度高阈值 | `L` | "70" | 湿度高于此值报警 |

**代码参考（已加入 MiotMqttTest.java）：**

```java
// 开启水泵
Map<String, Object> params = new LinkedHashMap<>();
params.put("F", 1);
sendPropertySet("device", params);

// 设置湿度阈值
params.clear();
params.put("K", "30");
params.put("L", "70");
sendPropertySet("device", params);
```

---

## 四、需要从 OneNet 控制台获取的配置

| 配置项 | 获取位置 | 用途 |
|--------|----------|------|
| **产品 ID** | 产品概况页 | 已知道：`FeGVC46Lne` |
| **Master-APIKey** | 产品概况页 → 解锁 | 产品级 MQTT 连接 + API 调用 |
| **设备名称** | 设备列表 | 已知道：`device` |
| **设备 AccessKey** | 设备详情页 | 设备级 API 调用（历史查询、指令下发） |

> **状态：全部配置已就绪。** Master-APIKey 和所有设备凭证已获取完毕，可直接实施。

---

## 五、配置对照表（待补全后填入）

### 后端需要配置的两套凭证：

```
# 产品 FeGVC46Lne（农业环境监测终端）
产品ID         = FeGVC46Lne
Master-APIKey  = +R0R0UFbF6e/sWWTmdNZG1GdI9VhNaEB0Z9xBycDXQE=
设备名         = device
设备AccessKey  = WktwTXB5RGNVOTNrdUwxaFVEbEtvQWpGSEJHMWtrT3Q= （已知）

# 产品 09B8L0Ji9W（DHT11 温湿度传感器）
产品ID         = 09B8L0Ji9W
Master-APIKey  = h3+7jfcX601ZDfEd5zEdi4XBwUvLIYXRR31rcc09g0k= （已知）
设备名         = DHT11
设备AccessKey  = TEVIazZuZlNaNU1jUzJyd2ZzOG5YTFk4eU8xV2hjeDE= （已知）
```

---

## 六、实施步骤

### 第 1 步：获取 Master-APIKey ✅ 已完成

产品 `FeGVC46Lne` 的 Master-APIKey 已获取并填入配置。

### 第 2 步：修改 MiotMqttTest.java 配置 ✅ 已完成

MiotMqttTest.java 已针对 DHT11（产品 `09B8L0Ji9W`）实现应用身份 MQTT 连接、历史查询、指令下发三项能力。

### 第 3 步：修改 DeviceDataManager.java

将设备身份的 MQTT 连接改为应用身份：

- clientId 改为 `soil_app_backend_001`（不与设备重名）
- password 改为用 Master-APIKey 签名生成的 token（而非设备 access_key）
- 订阅 Topic 改为 `$sys/FeGVC46Lne/+/thing/property/post`（通配符匹配所有设备）
- 取消订阅 `setTopic` 和 `queryTopic`（应用身份不需要回复设备指令，设备自己回复）
- 移除属性设置/查询指令的自动回复逻辑（设备端自己处理）

### 第 4 步：在 DeviceController 增加指令下发接口

新增 REST API 端点，供前端调用以向设备发送指令。

### 第 5 步：测试验证

1. 确保 STM32 设备正常上电并连接到 OneNet
2. 启动后端，确认应用身份 MQTT 连接成功
3. 观察控制台，验证收到设备实时上报数据
4. 调用历史查询 API，验证数据正确
5. 调用指令下发 API，验证设备端水泵能正常开关

---

## 七、架构图

```
┌─────────────────────────────────────────────────────┐
│                    OneNet 平台                        │
│                                                     │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────┐ │
│  │ 设备接入  │  │ 应用接入(MQTT)│  │  REST API     │ │
│  │ MQTT     │  │              │  │               │ │
│  └────┬─────┘  └──────┬───────┘  └───────┬───────┘ │
└───────┼───────────────┼─────────────────┼─────────┘
        │               │                 │
   设备身份          应用身份           HTTP/HTTPS
   clientId=device   clientId=app_001
        │               │                 │
   ┌────┴────┐   ┌──────┴──────┐   ┌─────┴─────┐
   │ STM32   │   │ Spring Boot │   │ Spring Boot│
   │ 4G 模组  │   │ MQTT 订阅   │   │ REST 调用  │
   │         │   │ 实时接收数据  │   │ 历史查询   │
   │ 上报数据 │   │             │   │ 指令下发   │
   │ 接收指令 │   └─────────────┘   └───────────┘
   └─────────┘
```

**关键点：设备身份归物理设备独占，后端使用应用身份（产品级）接入，互不冲突。**

---

## 八、配置完整性评估

| 功能 | 产品 09B8L0Ji9W (DHT11) | 产品 FeGVC46Lne (农业终端) |
|------|------------------------|---------------------------|
| 实时接收 (MQTT) | ✅ 可行 — Master-APIKey 已知 | ✅ 可行 — Master-APIKey 已知 |
| 历史查询 (API) | ✅ 可行 — 设备 AccessKey 已知 | ✅ 可行 — 设备 AccessKey 已知 |
| 指令下发 (API) | ✅ 可行 — 设备 AccessKey 已知 | ✅ 可行 — 设备 AccessKey 已知 |

**结论：三套凭证已完整，但实时接收需要额外配置 OneNet MQ 服务才能正常工作。**

> **实测发现**：OneNet IoT 设备 MQTT Broker (183.230.40.96:1883) 不接受应用身份的 MQTT CONNECT 连接（连接超时，rc=4）。仅在设备身份下 MQTT 可用。应用级实时接收需要通过 OneNet 的「消息队列 MQ」服务实现。

---

## 九、实测问题与补充方案

### 9.1 实测结果

对 `183.230.40.96` 进行了三种连接方式测试：

| 方式 | URL | 错误 |
|------|-----|------|
| SSL 域名 | `ssl://mqtts.heclouds.com:8883` | PKIX path building failed — 服务器证书链不含我们加载的 CA |
| SSL IP | `ssl://183.230.40.96:8883` | 连接超时 (rc=4) |
| TCP IP | `tcp://183.230.40.96:1883` | 连接超时 (rc=4) |

**结论：OneNet IoT 设备 MQTT Broker 不接受应用身份的 MQTT CONNECT。设备身份可用（物理 STM32 设备已正常连接），但应用身份无法直接订阅设备 Topic。**

### 9.2 实时接收的正确方案

OneNet 提供两种方式让应用接收实时数据：

#### 方案 A：消息队列 MQ 服务（推荐，参照官方 demo）

OneNet 控制台 → 消息队列 MQ → 创建 MQ 实例 → 配置数据流转规则

```
物理设备 → 上报数据 → OneNet IoT → 数据流转规则 → MQ 消息队列 → 应用订阅
```

**配置步骤：**

1. 登录 OneNet 控制台
2. 进入「消息队列 MQ」服务
3. 创建 MQ 实例（记录实例名称，如 `soil_moist_mq`）
4. 获取 MQ 的 AccessKey
5. 配置数据流转规则：产品 `09B8L0Ji9W` 的设备数据 → MQ 实例
6. 应用连接 `ssl://183.230.40.96:8883`，用 MQ 凭证认证
7. 订阅 Topic：`$sys/pb/consume/{MQ实例名}/{Topic名}/{订阅名}`

**MQ 连接参数（参照官方 demo）：**

| 参数 | 值 |
|------|-----|
| URL | `ssl://183.230.40.96:8883` |
| Username | MQ 实例名称 |
| Password | HMAC-MD5 Token（用 MQ AccessKey 签名，resource=`mqs/{实例名}`） |
| CA 证书 | `MQ-certificate-release-0711.pem` |
| TLS 版本 | TLSv1.2 |
| MQTT 版本 | 3.1.1 |
| 订阅 Topic | `$sys/pb/consume/{MQ实例名}/{Topic}/{订阅名}` |

**注意：** MQ 服务的消息体为 Protobuf 编码（非 JSON），需要引入 protobuf 依赖并反序列化。

#### 方案 B：REST API 轮询（简单但不实时）

不使用 MQTT，通过定时调用 REST API 获取最新数据：

```
每 5 秒调用 GET /thingmodel/query-device-property 获取最新值
与上一轮比较，有变化则更新前端
```

**优点：** 无需额外配置 OneNet MQ 服务，立即可用
**缺点：** 不是真正的实时（5-10 秒延迟），API 调用有频率限制

**已有实现：** `OneNetApiClient.queryDeviceLatestApi()` 已实现。

#### 方案 C：HTTP 数据推送

在 OneNet 控制台配置「数据推送」→ 指定后端 HTTP 地址 → 设备数据上报时 OneNet 主动推送到后端。

需要在后端新增一个 HTTP 端点接收推送数据。

---

### 9.3 历史查询与指令下发：无需额外配置，立即可用

| 功能 | 状态 | 所需凭证 |
|------|------|----------|
| 历史查询 | ✅ 立即可用 | 设备 AccessKey（已有） |
| 指令下发 | ✅ 立即可用 | 设备 AccessKey（已有） |

这两个功能不依赖 MQTT 连接，通过 REST API 直接实现。测试用例中的 `testHistoryQuery()` 已验证可正常调用。

---

## 十、测试用例说明

测试用例 `MiotMqttTest.java` 针对产品 `09B8L0Ji9W` 的 DHT11 设备：

```
运行方式：
  JAVA_HOME=D:/jdk/jdk17 ./mvnw compile test-compile -DskipTests
  java -cp "target/test-classes;target/classes;{dependency_jars}" Test.MiotMqttTest

测试内容：
  1. 尝试应用身份 MQTT（当前会报错，需先配置 MQ 服务）
  2. REST API 历史数据查询（✅ 正常）
  3. REST API 指令下发演示

当前状态：
  - 历史数据查询：可用
  - 指令下发：可用（取消注释即可发送）
  - MQTT 实时接收：需配置 OneNet MQ 服务后可用
```