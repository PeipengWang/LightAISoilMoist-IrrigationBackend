---
name: java-coding-standards
description: LightAISoilMoist-IrrigationBackend 的 Java 编码规范。用于编写、修改或评审本项目 Java/Spring 代码时，优先提供可落地的命名、DTO、响应、校验、格式与可维护性指导。
---

# Java 编码规范

本 skill 是本项目的可执行编码约定，不是《阿里巴巴 Java 开发手册》的全文镜像。编写代码时优先遵守本文件；遇到没有覆盖的细节，再按 Java/Spring Boot 常规实践和阿里规约判断。

## 项目优先级

1. 先跟随现有 Spring Boot 3、Java 17、Spring Data JPA、Lombok、Knife4j 的技术栈。
2. 新代码要比周围代码更规范，但不要为了规范大改无关模块。
3. 能用类型表达的结构，不用 `Map<String, Object>` 表达；能用 DTO/VO 的接口，不返回临时拼装的裸 `Map`。
4. 公开 API 的 JSON key 使用 lowerCamelCase。兼容旧前端时可以临时保留旧字段，但新增接口不要继续扩散 `device_name`、`enum_desc` 这类 snake_case。
5. 常量、魔法值、设备属性编码、错误码、状态值要集中命名，避免散落在 Controller 中。

## 命名和结构

- 类名使用 UpperCamelCase；方法、参数、字段使用 lowerCamelCase；常量使用 UPPER_SNAKE_CASE。
- 包名保持全小写，按职责分包：`controller`、`service`、`repository`、`model/entity`、功能子域包。
- 不使用拼音和中英混杂命名。业务展示文案可以中文，代码标识符用英文。
- 测试类放在与被测类对应的包路径下，类名以被测类名加 `Test` 结尾。不要再新增顶层 `Test` 包。

## API 代码

- Controller 只做路由、基础参数绑定、HTTP 语义处理。业务校验、类型转换、外部平台调用、日志记录、数据组装应放到 Service 或专门的 assembler/mapper。
- 请求体使用明确的 request DTO，并配合 Bean Validation，例如 `@NotBlank`、`@NotNull`、`@Min`、`@Max`。复杂请求不要用 `Map<String, Object>`。
- 响应优先使用统一响应对象。成功响应包含业务数据；错误响应要能区分用户参数错误、系统错误、第三方服务错误。
- 列表为空返回空集合，不返回 `null`。
- 对外接口新增字段时使用清晰英文名，避免缩写和平台内部编码直接泄露给前端，除非它本身就是业务协议字段。

## 可维护性

- 单个方法尽量保持短小。超过约 80 行或同时做三件以上事情时，优先拆成私有方法或下沉到 Service。
- 避免超过三层嵌套。优先使用卫语句处理异常输入和边界情况。
- 不在 Controller 中启动裸线程。需要异步、SSE、定时、重试时，放到受 Spring 管理的组件或 executor 中。
- 不吞异常。确实需要忽略时，注释原因，并至少保留可排查的日志。
- 日志使用 SLF4J 占位符，不使用字符串拼接打印运行日志。

## 注释

- 注释解释业务规则、外部平台约束、非显然的设计取舍；不要复述代码。
- 类或公共方法不强制补齐模板化 Javadoc。只有对外 API、复杂业务规则、易误用方法需要说明。
- TODO/FIXME 要写明原因和后续动作；临时代码在完成需求时清理。

## 修改代码时的检查

提交前自查：

- 新增 API 是否有 DTO、校验、统一响应。
- 是否新增了裸 `Map<String, Object>` 作为公开接口契约。
- 是否引入硬编码密钥、URL、设备配置、状态码或魔法值。
- 是否把业务逻辑塞进 Controller。
- 是否有 `System.out`、`printStackTrace`、空 `catch`。
- 是否为有风险的业务变化补了合适测试。
