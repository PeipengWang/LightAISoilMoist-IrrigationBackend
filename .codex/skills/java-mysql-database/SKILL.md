---
name: java-mysql-database
description: LightAISoilMoist-IrrigationBackend 的数据库与 JPA 规范。用于设计表结构、实体、Repository、查询、事务、初始化数据或从 H2 迁移到 MySQL 时。
---

# 数据库与 JPA 规范

本项目当前使用 H2 文件库并开启 MySQL 模式，但设计应为后续迁移 MySQL 留空间。写实体和查询时不要依赖 H2 特有行为。

## 表和字段

- 表名、字段名使用小写下划线风格。
- 每张业务表应有 `id`、`created_at`、`updated_at`。现有表缺失时，不在无关需求中强行重构，但新增表要遵守。
- 表示布尔含义的数据库字段使用 `is_xxx`；Java 属性不要命名为 `isXxx`，使用 `active`、`deleted` 等清晰属性，并显式映射列名。
- 金额、阈值、pH、湿度等需要精度的数据使用 `BigDecimal`，不要用 `float/double` 存储核心业务阈值。
- 有唯一业务含义的字段要加唯一约束或唯一索引。

## JPA 实体

- Entity 只表达持久化结构，不放复杂业务流程。
- 时间字段使用 `LocalDateTime` 或合适的 Java Time 类型。
- 不直接把 Entity 当成长期 API 响应模型；新增接口优先转 DTO/VO。
- 字段类型要与数据库类型匹配，ID 使用 `Long`。
- 更新业务记录时维护 `updatedAt`；新增记录时维护 `createdAt`。

## Repository 和查询

- 简单查询使用 Spring Data 方法名；复杂查询使用 `@Query` 或 Specification，并保持可读。
- 分页接口必须限制 page size，上限通常不超过 100。
- 查询结果为空时返回空集合。
- 不在 Controller 中写复杂 Specification 组装；复杂筛选下沉到 Service 或查询组件。
- 避免 N+1 查询。新增关联关系时明确懒加载行为。

## 事务

- 修改多个表或写入快照/日志的业务方法放在 Service 层，并用 `@Transactional` 标出事务边界。
- 不在 Controller 上开启事务。
- 第三方调用和数据库事务混用时谨慎设计顺序，避免长事务等待外部网络。
- 事务中捕获异常后如果仍需回滚，要重新抛出或显式标记回滚。

## H2 到 MySQL 的兼容

- 不使用 H2 专有 SQL。
- 生产环境不要长期依赖 `ddl-auto: update`，表结构变化应沉淀迁移脚本或升级说明。
- SQL、字段名、索引名按 MySQL 约定设计，避免大小写差异导致 Linux/MySQL 问题。
