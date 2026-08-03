# 执行日志 — 应用级业务流程编排器

> change: `application-business-process-orchestrator`
> started: 2026-08-03

## 2026-08-03 Task 0：HARD-GATE 与实施基线

- 变更范围：仅当前变更的 `spec.md`、`tasks.md`、`test-spec.md`、`execution-log.md`。
- 用户授权：当前会话明确要求开始开发，按 Spec 第 9 章六项推荐默认值完成 HARD-GATE。
- 规则基线：已读取根 `AGENTS.md`、`code-copilot/AGENTS.md`、三份 memory、自动化测试标准、流程业务 Skill 共享参考、Forge 编码规范与前端设计 Skill。
- Git 基线：当前分支为 `main`；工作区已有能力开放平台相关修改和 `.DS_Store` 变化，均不属于本变更，实施中禁止覆盖或提交。
- Flyway 基线：当前最新脚本为并行变更中的 `V1.0.82__improve_capability_client_workbench.sql`；本变更分配 `V1.0.83/V1.0.84`，不修改 `V1.0.82`。
- 协议基线：冻结 `businessProcessJson 1.0`、三类完整样例、可信身份矩阵、Process/Node/Approval 状态机与 CAS 条件。
- 安全结论：DAG、单活动审批、受限定时普通用户、同应用子流程深度 5、分阶段旧入口停写、禁止自由 Webhook；任一身份/权限/版本/关联不可信时失败关闭。
- 已启动服务：无。
- 数据库/运行态变更：无。
- 文档检查：四份变更文档通过 `git diff --no-index --check`，无空白错误。
- 协议检查：Ruby `JSON.parse` 成功解析 `test-spec.md` 中 3 个 JSON 协议样例；Ruby 输出一条系统目录权限 warning，不影响解析结论。
- 状态检查：Spec 为 `apply`、HARD-GATE 为 `completed`、Tasks 为 `Apply/M1`；未发现仍要求“仅允许 Proposal”或“待用户确认”的门禁文本。
- 已知非阻断：Spec Research 与任务前置中出现的 `TODO` 是对旧 `BusinessTriggerExecutor` 未实现 Webhook 的现状描述，不是本变更占位实现；新节点必须把该能力标为不可用。
