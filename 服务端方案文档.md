# LightAI 苹果智慧果园监测系统 —— 服务端技术方案文档

> 本文档以「服务端」视角，对 **LightAI 苹果智慧果园监测系统** 的整体技术架构进行说明。
> 系统由三大层级、四类服务构成：**STM32 采集端**（嵌入式）→ **OneNET 云平台**（中移物联网）→ **服务端**（Spring Boot 数据中枢 + 智能决策 AI 服务）→ **Web 前端**（Vue 3 管理后台）。
>
> 适用读者：项目开发者、技术负责人、评审人员。
> 配套文档：采集端方案《采集端方案.md》（位于 `E:\土壤监测平台\365_基于STM32的土壤质量检测系统(1)\方案文档\`）。

---

## 一、项目概述

### 1.1 背景与目标
本项目面向**苹果种植园区**，构建一套集「环境/土壤数据采集 — 云端汇聚 — 可视化监测 — 智能决策 — 设备反控」于一体的物联网监测系统。核心目标是把分散在田间的传感器数据实时汇聚到服务端，结合**物候期动态阈值模型**与 **AI 农技助理**，为种植户提供科学的灌溉、施肥、环境调控建议，并支持对水泵等执行设备的远程下发控制。

### 1.2 系统角色
| 角色 | 说明 |
| --- | --- |
| 采集端（STM32） | 部署在田间，采集土壤/环境传感器数据，经 4G 上传 OneNET |
| OneNET 云平台 | 设备接入、数据落盘、属性模型管理、指令下行通道 |
| 服务端后端（:8086） | 数据中枢：拉取/缓存设备数据、阈值计算、决策日志、指令代理 |
| 智能决策服务（:8001） | AI 农技助理「小沂」：决策建议、对话、ASR、记忆、TTS |
| Web 前端（:3000） | 管理后台：实时监测、历史分析、决策管理、阈值配置 |

---

## 二、系统总体架构

```mermaid
flowchart TB
    subgraph 采集层["① 采集端 (STM32 嵌入式)"]
        MCU[STM32F103 主控]
        S1[土壤湿度/PH ADC]
        S2[DHT11 温湿度]
        S3[BH1750 光照]
        S4[NPK 氮磷钾传感器 RS485]
        RLY[继电器/水泵]
        MCU --- S1 & S2 & S3 & S4
        MCU -->|4G / MQTT| RLY
    end

    subgraph 云["② OneNET 云平台 (中移物联网)"]
        ON[产品 FeGVC46Lne / 09B8L0Ji9W<br/>设备属性模型 A-O]
    end

    subgraph 服务端["③ 服务端"]
        BE["后端 Spring Boot :8086<br/>设备数据 / 阈值 / 决策 / 指令代理"]
        AI["智能决策服务 :8001<br/>小沂 AI 助理 (chat/decision/asr/memory)"]
        BE -.REST/决策建议.-> AI
    end

    subgraph 前端["④ Web 前端 (Vue 3) :3000"]
        FE[管理后台<br/>实时/历史/决策/阈值]
    end

    MCU -->|MQTT 属性上报 (A-O)| ON
    ON -->|REST 拉取 / 指令下行| BE
    FE -->|HTTP+ SSE| BE
    FE -.代理.-> AI
```

**架构要点**
- 采集端只与 OneNET 对接，不直接连服务端；服务端只与 OneNET 对接，不直接连设备。
- 服务端内部：后端 `:8086` 是「数据中枢 + 控制中枢」，AI 决策服务 `:8001` 是「智能大脑」，二者通过 HTTP 协作。
- 前端 `:3000` 的 Vite 开发代理，将 `/api/decision|chat|asr|memory` 转发到 `:8001`，其余 `/api/*` 转发到 `:8086`，对前端代码屏蔽了多服务差异。

---

## 三、技术栈总览

| 层级 | 技术选型 | 关键说明 |
| --- | --- | --- |
| 采集端 | STM32F103 + HAL/标准库 + FreeRTOS(可选) + 4G 模块 | USART+RS485 读 NPK；ADC 读土壤/PH；DHT11/BH1750；OneNET MQTT AT 指令状态机 |
| 云平台 | 中移物联网 OneNET（旧版 MQTT / REST API） | 产品级/设备级接入；属性标识符 A–O；REST `https://iot-api.heclouds.com` |
| 后端 | Spring Boot 3.5.16 / Java 17 / Maven | Spring Web、Validation、Spring Data JPA |
| 后端-MQTT | Eclipse Paho MQTT 1.2.5 | 实时接入组件（当前默认关闭，见 §6.1） |
| 后端-存储 | H2 嵌入式数据库（文件模式，MySQL 兼容） | 阈值/物候/决策日志；路径 `./data/threshold_db` |
| 后端-文档 | Knife4j / OpenAPI 3 4.5.0 | Swagger UI：`/swagger-ui.html`；H2 控制台：`/h2-console` |
| 后端-工具 | Lombok | 简化实体类 |
| AI 服务 | Python 微服务（推测 Flask/FastAPI） | 端口 `:8001`；另含 TTS `:9000`/`:9001` |
| 前端 | Vue 3 + Vite 8 + TypeScript 6 | `npm run dev` → `:3000` |
| 前端-UI | Element Plus 2.14 + Pinia 3 + vue-router 5 | 中文语言包 zh-cn |
| 前端-图表 | Chart.js 4 | 历史折线、双 Y 轴、数据断点虚线 |
| 前端-实时 | SSE (EventSource) + 10s 轮询兜底 | `/api/stream` 推送 |
| 前端-其他 | marked（Markdown 渲染） | AI 建议富文本展示 |

---

## 四、采集端（STM32）职责与实现

> 详细设计见《采集端方案.md》，此处仅从服务端视角概括其契约。

- **传感器接入**：土壤湿度/PH（ADC）、DHT11（温湿度单总线）、BH1750（I²C 光照）、NPK 氮磷钾（USART+RS485，自动方向模块，USART1 PA9/PA10）。
- **数据模型约定**：采集端向 OneNET 上报的属性标识符为 **A–O**（见 §十一附录），与服务端、前端完全一致，避免了硬编码转换。
- **上行**：通过 4G 模块以 OneNET MQTT 协议上报属性（topic 形如 `$sys/{productId}/{deviceName}/thing/property/post`）。
- **下行**：接收 OneNET 下发的 `set` 指令（如水泵开关 F、阈值 I/J/K/L），驱动继电器/更新本地阈值。
- **本地交互**：OLED 显示、AT 指令状态机控制 4G 模块。

---

## 五、服务端后端架构（Spring Boot :8086）

后端是系统的**数据中枢与控制中枢**，核心目录（`org.example.lightaisoilmoistirrigationbackend`）结构如下：

```
config/      DeviceConfig 设备与属性模型、Knife4jConfig 接口文档
model/       Result 统一返回；entity/ 阈值与决策实体
repository/  JPA Repository（CropConfig / SoilConfig / PhenologyStage /
             StageThreshold / ThresholdSnapshot / AlertRule / DecisionLog）
service/     DeviceDataManager 数据中枢、OneNetApiClient 平台对接、
             ThresholdService 阈值、PhenologyService 物候、DataInitializer 初始化
controller/  DeviceController 设备/数据/指令、ThresholdController 阈值
```

### 5.1 设备与属性模型（DeviceConfig）
- 集中定义两个 OneNET 设备：
  - **DEVICE_DHT11**：产品 `09B8L0Ji9W`，设备名 `DHT11`，属性 `temp`（温度）。
  - **DEVICE_AGRICULTURE**：产品 `FeGVC46Lne`，设备名 `device`，属性 **A–O**（PH、土壤湿度、温湿度、光照、水泵、报警状态、阈值、氮磷钾）。
- 每个属性含 `name / unit / dataType / mode(只读|读写) / enumDesc`，`mode` 决定前端是否可下发、后端 `command` 是否放行。
- 平台地址常量：`API_BASE=https://iot-api.heclouds.com`，`MQTT_SERVER=183.230.40.96:1883`。

### 5.2 数据中枢（DeviceDataManager）
- 维护 `ConcurrentHashMap` 内存缓存：`device → identifier → {value, time, name, unit, data_type, mode, enum_desc}`。
- 每个设备独立 `ReentrantReadWriteLock`：SSE 推送线程读、API 刷新线程写，读多写少场景并发安全。
- 构造时按 `DeviceConfig` 预初始化所有设备所有属性条目（value 初始 null），保证前端总能拿到完整属性列表。
- **数据获取策略（当前生效）**：因实时 MQTT 在 `afterPropertiesSet()` 中 `startDevice(dev)` 被注释，**当前走 OneNET REST 拉取兜底**——`getDeviceLatest()` 发现距上次刷新超过 `REFRESH_INTERVAL_MS=2000` 即调用 `refreshFromApi()` 经 `OneNetApiClient` 拉取最新值回填缓存。
- **MQTT 能力已就绪待启用**：`generateMqttToken()`（HMAC-SHA1 产品级签名）、`startDevice()`、断线重连逻辑均已实现，仅需在初始化中开启即可切换为实时推送（见 §十二）。

### 5.3 OneNET 平台对接（OneNetApiClient）
- `generateApiToken()`：HMAC-SHA256，版本 `2022-05-01`，用于 REST 鉴权。
- 主要能力：
  - `queryDeviceLatestApi()`：拉取设备最新属性（后端当前主用）。
  - `queryDeviceHistory()` / `queryDeviceAllHistory()`：历史数据查询。
  - `setDesiredProperty()`：`POST /thingmodel/set-device-desired-property`，**指令下行**通道。
- 使用 JDK `HttpClient` 实现，不依赖额外 REST 框架。

### 5.4 阈值数据库与物候模型（JPA + H2）
采用**物候期动态阈值**，而非固定阈值，更贴合苹果生长规律：

| 实体 | 作用 |
| --- | --- |
| `CropConfig` | 作物（苹果）基础配置 |
| `SoilConfig` | 园区土壤基础配置 |
| `PhenologyStage` | 物候期（萌芽/开花/膨果/成熟…）及 GDD 积温参数 |
| `StageThreshold` | 各物候期下 A–E、M–O 的阈值区间 |
| `ThresholdSnapshot` | 阈值变更快照（审计/回溯） |
| `AlertRule` | 报警规则（基于阈值命中） |
| `DecisionLog` | 指令/决策执行日志（可追溯） |

- 当前生效阈值由 `ThresholdService` 自动根据**当前物候期**（由 `PhenologyService` 依 GDD 推算）确定，可通过 `POST /api/thresholds/phenology/override` 手动覆盖。
- 阈值修改自动生成 `ThresholdSnapshot`，保证可回溯。
- 存储引擎为 **H2 嵌入式数据库**（文件模式 `./data/threshold_db`，MySQL 兼容模式），零运维、随应用启动。

### 5.5 指令下行（控制中枢）
- `POST /api/command`：校验属性 `mode == 读写`，调用 `OneNetApiClient.setDesiredProperty()` 下发到 OneNET，由平台转发至 STM32 执行，并写入 `DecisionLog`。
- 可下发项：水泵 F（0/1）、PH 阈高低 I/J、土壤湿度阈高低 K/L。
- 只读属性（如 M/N/O 氮磷钾、G/H 报警状态）拒绝下发，前端对应控件自动禁用。

### 5.6 接口与文档
- 设备类：`GET /api/devices`、`/api/latest`、`/api/latest/api`、`/api/history`、`/api/history/all`、`/api/stream`(SSE)、`POST /api/command`、`GET /api/decision-logs`。
- 阈值类：`GET /api/thresholds/current`、`/stages`、`/stages/{id}`、`PUT /{id}`、`/{id}/history`、`POST/DELETE /phenology/override`。
- 接口文档：Knife4j 中文界面 `/swagger-ui.html`；H2 控制台 `/h2-console`。

---

## 六、智能决策 / AI 助理服务（:8001 小沂）

- 独立的 AI 微服务于 `http://localhost:8001`，由 Vite 代理暴露为 `/api/decision`、`/api/chat`、`/api/asr`、`/api/memory`。
- 名称「**小沂**」——苹果种植 AI 农技助理，提供：
  - **决策建议**（`/api/decision/summary`）：结合当前实时数据 + 物候期阈值，输出灌溉/施肥/调控建议（Markdown 格式，前端用 `marked` 渲染）。
  - **对话**（`/api/chat/stream`）：流式问答，前端浮动抽屉式聊天窗。
  - **ASR / TTS**：语音识别与语音播报（`/api/asr`，TTS 端口 `:9000`/`:9001`）。
  - **记忆**（`/api/memory`）：用户与园区上下文记忆。
- 与服务端后端关系：前端从 `:8086` 取实时/阈值数据，从 `:8001` 取 AI 建议；后端亦可在决策流程中调用 `:8001` 获取建议。

---

## 七、前端（Vue 3）架构与功能

- 技术：Vue 3 + Vite 8 + TypeScript 6 + Element Plus + Pinia + vue-router 5 + Chart.js 4。
- 统一入口 `main.ts` 装配 Pinia、Element Plus（zh-cn）、路由；`App.vue` 仅 `<router-view/>`。
- 布局 `LayoutView.vue` 标题「🍎 LightAI 苹果智慧果园管理系统」，四大页签：

| 路由 | 视图 | 功能 |
| --- | --- | --- |
| `/realtime` | RealTimeView | 设备卡片 + 传感器网格 + **SSE 状态条**；可配置展示设备/属性，含「土壤养分(M/N/O)」模板 |
| `/history` | HistoryView | Chart.js 折线（断点虚线、双 Y 轴）、最大/最小/均值统计、表格、CSV 导出、时间区间筛选 |
| `/decision` | DecisionView | 设备控制（水泵 F、阈值 I/J/K/L 下发）、当前生效阈值、小沂 AI 建议、决策日志表、AI 聊天抽屉 |
| `/thresholds` | ThresholdConfigView | 物候期条 + 阶段阈值表(A–E,M–O) + 编辑弹窗(自动快照) + 变更历史 + 物候手动覆盖 |

- 状态管理：`stores/devices.ts`（`useDeviceStore`）：`init()` 加载设备→拉最新→`startSSE()`→`startPolling(10s)`；SSE 经 `createSSEConnection`（EventSource `/api/stream`，带重连退避）推送；在线/离线状态跟踪。
- 数据层：`api/index.ts` 封装全部 fetch（设备/最新/历史/阈值/决策/指令/AI 对话/ASR/TTS）。
- 实时策略：**SSE 主 + 10s 轮询兜底**，保证任何网络环境下监测不断档。

---

## 八、端到端数据流与接口

### 8.1 上行（采集 → 展示）
```
STM32 采集传感器
  → OneNET MQTT 属性上报 (A-O)
  → 后端 DeviceDataManager.refreshFromApi() 定时(2s)拉取回填内存缓存
  → 前端 SSE(/api/stream, 2s) / 10s 轮询 读取 /api/latest/api
  → RealTimeView / HistoryView 渲染
```

### 8.2 下行（建议/人工 → 设备）
```
前端 DecisionView 点击「开泵」/改阈值
  → POST /api/command (校验 mode=读写)
  → OneNetApiClient.setDesiredProperty()
  → OneNET 下发 set 指令
  → STM32 执行（继电器/更新本地阈值）
  → 结果写入 DecisionLog
```

### 8.3 智能决策闭环
```
实时数据 + 物候期阈值 → /api/decision/summary (调用 :8001 小沂)
  → 返回 Markdown 建议 → DecisionView 展示
  → 用户确认 → /api/command 下发执行 → DecisionLog 留痕
```

### 8.4 属性标识符统一约定（A–O）
采集端、OneNET 物模型、后端 `DeviceConfig`、前端展示**完全统一**，是系统解耦的关键契约（详见 §十一附录）。

---

## 九、部署与运行

| 服务 | 端口 | 启动方式 | 依赖 |
| --- | --- | --- | --- |
| 后端 :8086 | 8086 | `mvn spring-boot:run` | Java 17、Maven、H2（内嵌） |
| AI 决策 :8001 | 8001 | 独立进程（Python 服务） | 模型/API Key |
| 前端 :3000 | 3000 | `npm install && npm run dev` | Node（Vite 8） |
| OneNET | 云端 | SaaS | 中移物联网账号、产品/设备已建 |

- 后端关键配置（`application.yml`）：服务端口 8086；H2 文件库 `./data/threshold_db;DB_CLOSE_DELAY=-1;MODE=MySQL`；Knife4j 语言 zh_cn；h2-console 路径 `/h2-console`。
- 前端代理（`vite.config.ts`）：`/api/decision|chat|asr|memory → :8001`，`/api/decision-logs → :8086`，其余 `/api/* → :8086`。
- 数据初始化：`DataInitializer` 在启动时为阈值/物候/作物等表写入默认种子数据。

---

## 十、目录结构（服务端）

```
LightAISoilMoist-IrrigationBackend/        # 后端 Spring Boot
├── pom.xml / application.yml
├── src/main/java/org/example/lightaisoilmoistirrigationbackend/
│   ├── config/      DeviceConfig, Knife4jConfig
│   ├── model/       Result, entity/(7 张表)
│   ├── repository/  *Repository (JPA)
│   ├── service/     DeviceDataManager, OneNetApiClient,
│   │                ThresholdService, PhenologyService, DataInitializer
│   └── controller/  DeviceController, ThresholdController
├── src/test/        MiotMqttTest, *ServiceTest 等
└── data/            H2 数据库文件(运行时生成)

LightAISoilMoist-IrrigationFrontend/       # 前端 Vue3
├── package.json / vite.config.ts / tsconfig*.json
└── src/
    ├── main.ts / App.vue
    ├── router/index.ts
    ├── stores/devices.ts
    ├── api/index.ts
    └── views/  LayoutView, RealTimeView, HistoryView, DecisionView, ThresholdConfigView

(采集端代码位于独立工程，见《采集端方案.md》)
```

---

## 十一、附录：属性标识符表（A–O）

| 标识 | 名称 | 单位 | 类型 | 模式 | 说明 |
| --- | --- | --- | --- | --- | --- |
| A | PH值 | — | float | 只读 | 土壤酸碱度 |
| B | 土壤湿度 | % | int32 | 只读 | |
| C | 环境温度 | °C | int32 | 只读 | |
| D | 环境湿度 | % | int32 | 只读 | |
| E | 光照 | lux | int32 | 只读 | |
| F | 水泵状态 | — | enum | **读写** | 0=关闭 / 1=开启（可下发） |
| G | PH状态 | — | enum | 只读 | 0=正常 / 1=报警 |
| H | 土壤湿度状态 | — | enum | 只读 | 0=正常 / 1=异常 |
| I | PH阈值低 | — | string | **读写** | 可下发 |
| J | PH阈值高 | — | string | **读写** | 可下发 |
| K | 土壤湿度阈值低 | % | string | **读写** | 可下发 |
| L | 土壤湿度阈值高 | % | string | **读写** | 可下发 |
| M | 氮含量 | mg/kg | int32 | 只读 | NPK |
| N | 磷含量 | mg/kg | int32 | 只读 | NPK |
| O | 钾含量 | mg/kg | int32 | 只读 | NPK |

> 注：M/N/O（氮磷钾）已端到端打通——采集端 NPK 传感器上报、后端 `DeviceConfig` 登记、前端 RealTimeView「土壤养分」模板与 ThresholdConfigView 阶段阈值表均已支持。

---

## 十二、关键设计说明与后续演进

1. **实时性当前方案（REST 轮询）**：因 `DeviceDataManager.afterPropertiesSet()` 中 MQTT `startDevice()` 被注释，当前为「方案 B」——每 2s 经 OneNET REST 拉取最新值。优点是零额外平台配置即可运行；缺点是近实时（秒级延迟、有平台限频风险）。
2. **实时化演进（MQTT）**：`generateMqttToken()`（HMAC-SHA1 产品级签名）、`startDevice()`、断线重连均已实现。切换到实时需：
   - 在 OneNET 控制台开通 **MQ 服务**（接收设备属性上报消息）；
   - 取消 `afterPropertiesSet()` 中 `startDevice(dev)` 注释；
   - 处理「产品级身份 vs 设备级身份」的 topic/订阅权限冲突（详见后端设计文档《OneNet平台对接方案.md》）。
3. **多服务协作**：后端 `:8086` 与 AI 服务 `:8001` 解耦，前端经 Vite 代理无感访问，便于 AI 能力独立迭代/替换。
4. **数据契约统一**：A–O 标识符三端一致 + `DeviceConfig` 驱动（非硬编码），新增传感器只需扩展配置与物模型，前端/后端代码改动最小。
5. **可追溯性**：阈值变更留 `ThresholdSnapshot`、指令执行留 `DecisionLog`，满足农事决策审计需求。

---

*文档生成说明：本文档基于对后端（Spring Boot）、前端（Vue 3）、AI 决策服务（:8001）源码的实际阅读，并结合采集端方案《采集端方案.md》整理，反映截至 2026-08-23 的代码实现现状。*
