# NPK 传感器（M/N/O）接入方案

## 背景

设备端已新增氮磷钾传感器，在 OneNet 平台的属性标识符分别为 **M（氮）**、**N（磷）**、**O（钾）**，数据已能正常上传到 OneNet。后端需要支持这三类数据的实时展示和历史查询。

## 现状分析

当前 `device` 设备（产品 `FeGVC46Lne`）已定义属性 A-L，共 12 个属性：

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

现有架构的特点：**属性驱动，而非硬编码**。所有属性的实时展示、历史查询、SSE 推送均基于 `DeviceConfig.properties` 遍历实现。这意味着新增属性只需要在 `DeviceConfig` 中注册，其余链路自动生效。

## 方案设计

### 1. 新增属性定义（DeviceConfig.java）

在 `device` 设备的 `agriProps` 中追加三个属性：

```java
agriProps.put("M", new PropertyMeta("氮", "mg/kg", "int32"));
agriProps.put("N", new PropertyMeta("磷", "mg/kg", "int32"));
agriProps.put("O", new PropertyMeta("钾", "mg/kg", "int32"));
```

> **数据类型和单位待确认**：假定 NPK 传感器上报的是 int32 类型，单位为 mg/kg。如果设备端实际是 float 或单位不同（如 ppm / g/kg），需要对应调整。

### 2. 自动生效的功能

由于现有代码是基于属性遍历的通用设计，新增 M/N/O 后以下功能**无需额外编码**：

| 功能 | 涉及端点/代码 | 说明 |
|------|-------------|------|
| 实时数据展示 | `/api/latest`、`/api/latest/api` | `getDeviceLatest()` 遍历所有属性从缓存/API获取 |
| SSE 实时推送 | `/api/stream` | `getAllLatest()` 遍历所有设备所有属性 |
| 单属性历史查询 | `/api/history` | 按 identifier 查询，前端下拉自动出现 M/N/O |
| 全属性历史查询 | `/api/history/all` | 遍历 `dev.getProperties().keySet()` |
| 设备元数据 | `/api/devices` | 遍历所有属性返回元数据 |
| 前端渲染 | `index.html` | 动态从 `/api/devices` 和 `/api/latest` 读取并渲染 |

### 3. 需要手动编码的部分

#### 3.1 DeviceConfig.java — 追加属性定义（核心）

在 `agriProps` 的 L 后面追加 M、N、O 三个属性。这是唯一的必须改动。

#### 3.2 TECH_STACK.md — 更新传感器列表

在文档中补充 M/N/O 传感器的说明。

#### 3.3 DataInitializer.java（可选）— 预置 NPK 阈值

当前预置数据仅为每个物候期配置了湿度/pH/温度/光照阈值。如果后续需要对 NPK 做阈值告警，可以在 `stage_threshold` 中追加 NPK 相关阈值。**本期不强制**，因为 NPK 阈值与物候期的对应关系需要农学专家确认。

#### 3.4 测试用例

- 更新 `OneNetApiClientTest`：验证 NPK 属性历史查询
- 更新 `DeviceDataManagerTest`：验证 NPK 属性在缓存中的存在性
- 更新 `WaterPumpControlTest`：`queryAllProperties` 用例自动覆盖（遍历所有属性）

### 4. 前端影响

前端 `index.html` **无需修改**。原因：
- 传感器网格通过 `/api/devices` 获取属性列表后动态渲染
- 历史查询下拉框通过 `onDeviceChange()` 动态填充属性选项
- 图表渲染完全基于 API 返回的元数据

唯一的变化是：`device` 设备的传感器卡片会从 12 个变为 15 个，NPK 传感器卡片自动出现在实时数据显示面板中。

### 5. 涉及文件清单

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `src/main/java/.../config/DeviceConfig.java` | **修改** | 追加 M/N/O 属性定义 |
| `TECH_STACK.md` | **修改** | 更新传感器列表文档 |
| `src/test/java/.../OneNetApiClientTest.java` | **修改** | 新增 NPK 属性历史查询测试 |
| `src/test/java/.../DeviceDataManagerTest.java` | **修改** | 验证新属性缓存初始化 |

### 6. 待确认事项

1. **M/N/O 的数据类型**：设备端上传的是 int32 ？
2. **M/N/O 的单位**：mg/kg
3. **M/N/O 的读写模式**：只读，无需下发 NPK 阈值
4. **是否需要 NPK 阈值告警**：本期不需要，后续再补阈值配置。

---

## 实施步骤（代码编写阶段）

1. 在 `DeviceConfig.java` 的 `agriProps` 中追加 M、N、O 三个 `PropertyMeta`
2. 更新 `TECH_STACK.md` 文档
3. 编写测试用例验证 NPK 数据能正常获取
4. 运行测试确认通过
5. 启动应用，浏览器验证前端正确展示 15 个传感器

**预计改动量**：约 10 行核心代码 + 测试代码 + 文档更新。
