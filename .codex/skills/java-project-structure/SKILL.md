---
name: java-project-structure
description: LightAISoilMoist-IrrigationBackend 的工程结构规范。用于组织 Spring Boot 包结构、依赖、配置、DTO、Service、Client、Repository 和资源文件。
---

# 工程结构规范

本项目是单模块 Spring Boot 后端。结构应服务于清晰分层和后续扩展，不追求复杂多模块。

## 推荐包结构

```text
org.example.lightaisoilmoistirrigationbackend
|-- config
|-- controller
|-- service
|-- client
|-- repository
|-- model
|   |-- dto
|   |-- entity
|   `-- enums
|-- image
|   |-- controller
|   |-- service
|   |-- repository
|   |-- entity
|   `-- dto
`-- common
    |-- exception
    `-- response
```

不需要为了移动一两个类而大重构；新增代码优先放到正确位置。

## 层级职责

- `controller`：只处理 HTTP 入参、响应和状态码。
- `service`：业务逻辑、事务、跨 repository 协作。
- `client`：OneNET REST、MQTT、其他第三方服务封装。
- `repository`：Spring Data JPA 数据访问。
- `model.dto`：请求/响应 DTO。
- `model.entity`：JPA 实体，不直接承载 API 展示逻辑。
- `common.response`：统一响应体、错误码、分页响应。
- `common.exception`：业务异常、第三方异常、全局异常处理。

## 配置

- 环境相关配置放到 `application.yml` 或 profile 配置中，不写死在 Java 类里。
- 设备列表、产品 ID、属性元数据可以先集中配置；密钥必须外部注入。
- 开发环境配置和生产环境配置要能区分，特别是 H2 console、Swagger、文件路径、DDL 策略。

## Maven 和依赖

- 新依赖必须服务于当前需求，避免引入大型工具包解决小问题。
- Spring Boot 已管理版本的依赖，优先不手写版本。
- 新增质量工具时优先考虑：格式化、静态检查、测试覆盖率、依赖安全检查。
- 不提交 `target/`、本地数据库、上传图片、IDE 私有配置等生成物。

## 资源和数据

- `src/main/resources` 放应用配置、证书模板或必要静态资源。真实证书和私钥不要提交。
- 本地运行数据放 `data/` 时应确认 `.gitignore` 覆盖数据库和上传文件。
- 初始化数据逻辑要可重复执行，不依赖手工数据库状态。
