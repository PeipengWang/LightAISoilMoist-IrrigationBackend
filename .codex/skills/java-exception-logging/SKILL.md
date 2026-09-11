---
name: java-exception-logging
description: LightAISoilMoist-IrrigationBackend 的异常、错误响应和日志规范。用于处理异常、错误码、日志记录、第三方调用失败和接口错误返回时。
---

# 异常与日志规范

异常和日志的目标是让前端知道发生了什么，让开发能快速定位问题，同时避免泄露密钥、token、上传文件内容或用户敏感信息。

## 错误模型

- 对外 API 应统一错误结构。至少区分：
  - 参数错误：用户输入不合法、缺字段、格式错误。
  - 业务错误：资源不存在、状态不允许、只读属性不可写。
  - 第三方错误：OneNET、MQTT、文件系统、数据库等外部依赖失败。
  - 系统错误：未预期异常。
- 新增接口不要只返回 `Map.of("error", "...")`。使用统一响应对象或 `ResponseEntity` 搭配统一错误体。
- 错误消息不要包含 accessKey、token、完整请求体、完整文件路径等敏感信息。
- 业务错误可以返回清晰中文提示；日志中应包含足够的上下文 ID，例如设备名、属性标识、记录 ID。

## 异常处理

- 不用 `catch (Exception)` 包住大段业务逻辑，除非位于边界层并且会转换成统一错误。
- 不通过异常做正常流程控制。
- 可预检查的问题先校验，例如空值、页大小、文件类型、设备是否存在。
- Service 层可以抛出有业务含义的异常；Controller 或全局异常处理器负责转成 HTTP 响应。
- 第三方客户端层捕获底层异常后，转换为项目异常或错误结果，并保留原始异常作为 cause。
- 捕获 `InterruptedException` 后必须恢复中断状态：`Thread.currentThread().interrupt()`。

## 日志

- 使用 SLF4J：`log.info("message: {}", value)`。
- 禁止 `System.out`、`System.err`、`printStackTrace` 出现在生产代码。
- `debug/info` 用于正常过程和关键状态，`warn` 用于可恢复异常或用户输入错误，`error` 用于系统异常、第三方不可用、数据不一致。
- 记录异常时带堆栈：`log.error("调用 OneNET 失败 deviceName={}", deviceName, e)`。
- 不直接打印完整 token、accessKey、Authorization header、上传文件二进制、过大的第三方响应。
- 不重复打日志。异常只在被处理或跨边界转换的位置记录一次。

## 本项目特别注意

- OneNET token 生成失败、MQTT 连接失败、HTTP 超时要作为第三方错误处理。
- 决策日志写入失败不应影响命令下发主流程，但要用 `warn` 记录设备、属性和失败原因。
- 文件上传元数据提取失败可以降级，但文件写入失败必须返回明确错误。
- SSE 推送循环里的异常不能静默吞掉，应记录可诊断信息，并确保连接清理。
