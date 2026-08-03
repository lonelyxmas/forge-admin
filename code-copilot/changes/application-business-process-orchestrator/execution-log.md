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
- 提交证据：`57fc3acb [application-business-process-orchestrator] 冻结编排协议与验证基线`；提交统计上传因本机 DNS/网络失败，Git 提交本身成功，未执行 push。

## 2026-08-03 Task 1：流程数据库结构与资源

- 新增 `V1.0.83__add_application_business_process.sql`：创建流程定义、不可变版本、运行实例和节点运行四张表；定义/版本使用 `BIGINT del_flag` 主键墓碑，运行表不提供删除标记。
- 新增 `V1.0.84__add_application_business_process_resources.sql`：写入设计/运行/节点/触发字典、应用发布 `PROCESSES` 步骤、隐藏设计器路由和管理/运行/迁移权限；权限只继承既有应用查看、编辑和发布角色，不扩大无应用权限角色。
- 幂等与租户：建表使用 `CREATE TABLE IF NOT EXISTS`；字典、资源和角色资源均使用 `NOT EXISTS`；内置数据统一 `tenant_id=1`。
- 静态验证：`git diff --check` 通过；两个新迁移的 Flyway placeholder 扫描无输出；`tenant_id DEFAULT 0/=0/,0` 扫描无输出；迁移版本重复扫描无输出。
- 轻量结构检查：`V1.0.83` 单引号 202、左右括号 60/60；`V1.0.84` 单引号 496、左右括号 31/31。
- 合同核对：Flowable 使用 `BusinessFlowService/FlowClient`；消息与企业协同使用 `BusinessActionStepExecutor + MessageService/CollaborationMessageChannel`；统一能力平台仅确认 `CapabilityRegistry`，generator 尚无受控桥接，Task 9B 前按不可用处理。
- 安全发现：旧 `SendMessageActionStepExecutor#resolveUserId` 在无 Session 时回退 `1L`，违反本变更“无合法普通用户失败关闭”；列入 Task 9B 修复，业务流程运行时不得复用该回退。
- 跳过项：未连接 MySQL，未执行新库/存量库/重复 Flyway 和 `forge_schema_history` 检查；原因是本轮不自动修改真实数据库，留待 Task 19 目标环境验收。
- 已启动服务：无。
