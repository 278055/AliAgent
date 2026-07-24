# P5 AI Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现可恢复、可审计、可降级的只读 AI 回复编排。

**Architecture:** 集成分支独占契约/迁移；A 做核心、B 做适配、C 做治理，以固定 ports 协作。

**Tech Stack:** Java 17、Spring Boot、RabbitMQ、PostgreSQL/Flyway、Spring AI Alibaba、Sentinel。

---

### Task 1: P5-A

- [ ] 写重复 v2 消息只产生一次 Inbox/工具计划、v1 无 generationId 不回写、重启不重放的失败测试。
- [ ] 实现消费、状态机、规则路由、五工作流和恢复，运行服务测试。
- [ ] 执行 Maven、diff 检查，提交 `feat(p5-a): add orchestration core`。

### Task 2: P5-B

- [ ] 写 Mock 默认、无密钥不调 DashScope、可信头透传和 mall 故障无事实的失败测试。
- [ ] 实现 chat/knowledge/mall/conversation adapters 与引用、幂等片段，运行测试。
- [ ] 执行 Maven、diff 检查，提交 `feat(p5-b): add orchestration adapters`。

### Task 3: P5-C

- [ ] 写默认发布、灰度、版本固定、配额耗尽、模型故障转人工的失败测试。
- [ ] 实现版本、内部 API、限流熔断、审计，运行测试。
- [ ] 执行 Maven、diff 检查，提交 `feat(p5-c): add orchestration governance`。
