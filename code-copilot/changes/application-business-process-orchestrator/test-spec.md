# 测试规格 — 应用级业务流程编排器

> change: `application-business-process-orchestrator`
> status: active
> baseline: 2026-08-03

## 1. 验证目标与边界

本测试规格覆盖 Spec 功能 1-40，采用“协议与控制面 → 持久化运行 → Flowable 等待恢复 → 应用发布 → 迁移收口”的增量顺序。自动化验证可以执行静态检查、单元测试、模块编译和前端构建；真实 MySQL Flyway、Admin/Flow 服务启动、真实流程运行和迁移演练由用户在目标环境执行，未回填结果前不得表述为通过。

P0 阻断项：协议污染 BPMN、多个开始节点、环或无结束路径、跨租户/跨应用引用、重复审批、无可信身份回退管理员、自由 URL/Secret、回调重复消费、已发布版本原地修改、旧配置物理删除。

P1 必验项：CRUD 与草稿 hash 冲突、节点配置完整性、条件分支、动作幂等、重试/取消、应用发布与回滚、运行时间线、迁移预览和旧入口停写。

## 2. 冻结的 businessProcessJson 1.0 协议

### 2.1 根协议

- `schemaVersion`：首版固定为字符串 `1.0`。
- `processCode`：应用内稳定编码，创建后不可修改。
- `subject`：固定主业务对象；`objectId/objectVersionId/recordId` 均按字符串传输。
- `nodes`：只允许注册表节点，首版为 `START_MANUAL/START_EVENT/START_SCHEDULE/CONDITION/ACTION/APPROVAL/SUB_PROCESS/END`。
- `edges`：`source/target/sourcePort` 均引用节点注册表；条件分支最多一个 `isDefault=true`。
- `policies`：冻结审批并发、重试、调用深度和执行身份策略，画布不得覆盖可信身份。
- `dependencies`：只保存对象、Flowable 模型、表单、消息模板、业务动作、受治理能力和同应用子流程的稳定引用；发布时解析为不可变版本快照。
- 禁止键：`url/webhook/secret/token/password/privateKey/authorization/cookie/javaClass/sql/script/spel`（大小写和嵌套路径均检查）。

### 2.2 手动提交审批

```json
{
  "schemaVersion": "1.0",
  "processCode": "purchase_submit_approval",
  "subject": {
    "objectId": "1900000000000001001",
    "objectCode": "sample_purchase_order",
    "objectVersionId": null,
    "recordIdSource": "RUNTIME_RECORD"
  },
  "nodes": [
    {
      "id": "start_manual",
      "type": "START_MANUAL",
      "name": "提交审批",
      "config": {
        "positions": ["ROW", "DETAIL"],
        "permission": "ai:businessProcess:start",
        "confirmText": "确认提交当前采购单审批？",
        "visibleCondition": {"operator": "AND", "rules": [{"source": "record", "field": "status", "operator": "EQ", "value": "DRAFT"}]}
      }
    },
    {
      "id": "approval_purchase",
      "type": "APPROVAL",
      "name": "采购审批",
      "ports": ["APPROVED", "REJECTED", "CANCELED", "FAILED"],
      "config": {
        "flowModelKey": "sample_purchase_order_approval",
        "versionPolicy": "PINNED_AT_APPLICATION_PUBLISH",
        "titleTemplate": "采购审批-{orderNo}",
        "formAsset": {"formKey": "sample_purchase_order_approval_form", "formMode": "BUSINESS_CODE_FORM", "providerKey": "samplePurchaseOrder"},
        "variableMappings": [
          {"field": "id", "variable": "recordId"},
          {"field": "orderNo", "variable": "orderNo"},
          {"field": "title", "variable": "title"},
          {"field": "amountCent", "variable": "amountCent"}
        ]
      }
    },
    {"id": "mark_approved", "type": "ACTION", "name": "更新为已通过", "config": {"actionType": "UPDATE_RECORD", "objectCode": "sample_purchase_order", "fieldMappings": [{"field": "status", "valueSource": "CONSTANT", "value": "APPROVED"}]}},
    {"id": "mark_rejected", "type": "ACTION", "name": "更新为待修改", "config": {"actionType": "UPDATE_RECORD", "objectCode": "sample_purchase_order", "fieldMappings": [{"field": "status", "valueSource": "CONSTANT", "value": "NEED_MODIFY"}]}},
    {"id": "mark_canceled", "type": "ACTION", "name": "更新为已取消", "config": {"actionType": "UPDATE_RECORD", "objectCode": "sample_purchase_order", "fieldMappings": [{"field": "status", "valueSource": "CONSTANT", "value": "CANCELED"}]}},
    {"id": "end_success", "type": "END", "name": "审批完成", "config": {"result": "SUCCESS"}},
    {"id": "end_rejected", "type": "END", "name": "等待修改", "config": {"result": "REJECTED"}},
    {"id": "end_canceled", "type": "END", "name": "流程取消", "config": {"result": "CANCELED"}},
    {"id": "end_failed", "type": "END", "name": "启动失败", "config": {"result": "FAILED"}}
  ],
  "edges": [
    {"id": "e1", "source": "start_manual", "target": "approval_purchase", "sourcePort": "NEXT"},
    {"id": "e2", "source": "approval_purchase", "target": "mark_approved", "sourcePort": "APPROVED"},
    {"id": "e3", "source": "approval_purchase", "target": "mark_rejected", "sourcePort": "REJECTED"},
    {"id": "e4", "source": "approval_purchase", "target": "mark_canceled", "sourcePort": "CANCELED"},
    {"id": "e5", "source": "approval_purchase", "target": "end_failed", "sourcePort": "FAILED"},
    {"id": "e6", "source": "mark_approved", "target": "end_success", "sourcePort": "NEXT"},
    {"id": "e7", "source": "mark_rejected", "target": "end_rejected", "sourcePort": "NEXT"},
    {"id": "e8", "source": "mark_canceled", "target": "end_canceled", "sourcePort": "NEXT"}
  ],
  "policies": {"approvalConcurrency": "ONE_ACTIVE_PER_BUSINESS_KEY", "maxSubProcessDepth": 5, "retry": {"mode": "LIMITED", "maxAttempts": 3, "backoffSeconds": [30, 120, 600]}},
  "dependencies": {"objects": ["sample_purchase_order"], "flowModels": ["sample_purchase_order_approval"], "formAssets": ["sample_purchase_order_approval_form"], "businessActions": [], "messageTemplates": [], "capabilities": [], "subProcesses": []}
}
```

### 2.3 记录新增后自动审批

```json
{
  "schemaVersion": "1.0",
  "processCode": "purchase_created_auto_approval",
  "subject": {"objectId": "1900000000000001001", "objectCode": "sample_purchase_order", "objectVersionId": null, "recordIdSource": "EVENT_RECORD"},
  "nodes": [
    {"id": "start_created", "type": "START_EVENT", "name": "采购单新增", "config": {"eventType": "RECORD_CREATED", "condition": {"operator": "AND", "rules": [{"source": "record", "field": "autoSubmit", "operator": "EQ", "value": true}]}}},
    {"id": "approval_purchase", "type": "APPROVAL", "name": "采购审批", "ports": ["APPROVED", "REJECTED", "CANCELED", "FAILED"], "config": {"flowModelKey": "sample_purchase_order_approval", "versionPolicy": "PINNED_AT_APPLICATION_PUBLISH", "titleTemplate": "采购审批-{orderNo}", "formAsset": {"formKey": "sample_purchase_order_approval_form", "formMode": "BUSINESS_CODE_FORM", "providerKey": "samplePurchaseOrder"}, "variableMappings": [{"field": "id", "variable": "recordId"}, {"field": "orderNo", "variable": "orderNo"}]}},
    {"id": "notify_approved", "type": "ACTION", "name": "通知申请人", "config": {"actionType": "SEND_MESSAGE", "messageTemplateCode": "purchase_approval_approved", "channels": ["WEB"], "recipientRule": {"type": "RECORD_FIELD", "field": "applicantId"}}},
    {"id": "end_success", "type": "END", "name": "完成", "config": {"result": "SUCCESS"}},
    {"id": "end_rejected", "type": "END", "name": "驳回", "config": {"result": "REJECTED"}},
    {"id": "end_canceled", "type": "END", "name": "取消", "config": {"result": "CANCELED"}},
    {"id": "end_failed", "type": "END", "name": "失败", "config": {"result": "FAILED"}}
  ],
  "edges": [
    {"id": "e1", "source": "start_created", "target": "approval_purchase", "sourcePort": "NEXT"},
    {"id": "e2", "source": "approval_purchase", "target": "notify_approved", "sourcePort": "APPROVED"},
    {"id": "e3", "source": "approval_purchase", "target": "end_rejected", "sourcePort": "REJECTED"},
    {"id": "e4", "source": "approval_purchase", "target": "end_canceled", "sourcePort": "CANCELED"},
    {"id": "e5", "source": "approval_purchase", "target": "end_failed", "sourcePort": "FAILED"},
    {"id": "e6", "source": "notify_approved", "target": "end_success", "sourcePort": "NEXT"}
  ],
  "policies": {"approvalConcurrency": "ONE_ACTIVE_PER_BUSINESS_KEY", "maxSubProcessDepth": 5, "retry": {"mode": "LIMITED", "maxAttempts": 3, "backoffSeconds": [30, 120, 600]}},
  "dependencies": {"objects": ["sample_purchase_order"], "flowModels": ["sample_purchase_order_approval"], "formAssets": ["sample_purchase_order_approval_form"], "businessActions": [], "messageTemplates": ["purchase_approval_approved"], "capabilities": [], "subProcesses": []}
}
```

### 2.4 定时分层提醒

```json
{
  "schemaVersion": "1.0",
  "processCode": "purchase_due_tiered_reminder",
  "subject": {"objectId": "1900000000000001001", "objectCode": "sample_purchase_order", "objectVersionId": null, "recordIdSource": "SCHEDULE_SCAN_RECORD"},
  "nodes": [
    {"id": "start_due", "type": "START_SCHEDULE", "name": "到期扫描", "config": {"dueField": "expectedArrivalDate", "lookAheadDays": 3, "lookBackDays": 7, "batchSize": 100, "minimumIntervalMinutes": 1440, "serviceActor": {"mode": "CONFIGURED_USER", "userConfigKey": "business.process.schedule.service-user"}}},
    {"id": "check_overdue", "type": "CONDITION", "name": "判断是否逾期", "ports": ["OVERDUE", "DUE_SOON"], "config": {"branches": [{"port": "OVERDUE", "condition": {"operator": "AND", "rules": [{"source": "context", "field": "daysUntilDue", "operator": "LT", "value": 0}]}}, {"port": "DUE_SOON", "isDefault": true}]}},
    {"id": "notify_overdue", "type": "ACTION", "name": "发送逾期提醒", "config": {"actionType": "SEND_MESSAGE", "messageTemplateCode": "purchase_overdue_notice", "channels": ["WEB", "EMAIL"], "recipientRule": {"type": "RECORD_FIELD", "field": "ownerId"}}},
    {"id": "notify_due", "type": "ACTION", "name": "发送到期提醒", "config": {"actionType": "SEND_MESSAGE", "messageTemplateCode": "purchase_due_notice", "channels": ["WEB"], "recipientRule": {"type": "RECORD_FIELD", "field": "ownerId"}}},
    {"id": "end_success", "type": "END", "name": "提醒完成", "config": {"result": "SUCCESS"}}
  ],
  "edges": [
    {"id": "e1", "source": "start_due", "target": "check_overdue", "sourcePort": "NEXT"},
    {"id": "e2", "source": "check_overdue", "target": "notify_overdue", "sourcePort": "OVERDUE"},
    {"id": "e3", "source": "check_overdue", "target": "notify_due", "sourcePort": "DUE_SOON", "isDefault": true},
    {"id": "e4", "source": "notify_overdue", "target": "end_success", "sourcePort": "NEXT"},
    {"id": "e5", "source": "notify_due", "target": "end_success", "sourcePort": "NEXT"}
  ],
  "policies": {"approvalConcurrency": "ONE_ACTIVE_PER_BUSINESS_KEY", "maxSubProcessDepth": 5, "retry": {"mode": "LIMITED", "maxAttempts": 3, "backoffSeconds": [60, 300, 1800]}},
  "dependencies": {"objects": ["sample_purchase_order"], "flowModels": [], "formAssets": [], "businessActions": [], "messageTemplates": ["purchase_overdue_notice", "purchase_due_notice"], "capabilities": [], "subProcesses": []}
}
```

## 3. 可信身份矩阵

| 触发来源 | actor | tenant | activeOrg | 可信来源 | 失败策略 |
|---|---|---|---|---|---|
| `MANUAL` | 当前登录普通用户 | 当前 Session 租户 | 当前 Session 组织 | Sa-Token `LoginUser` | 任一上下文缺失或无权限立即拒绝 |
| `EVENT` | 原业务操作人 | 事务事件中的已验证租户 | 原操作组织 | 事务完成后发布的服务端事件快照 | actor/tenant/org 不完整则不创建 run |
| `SCHEDULE` | 配置的受限普通服务用户，或记录字段唯一解析出的普通用户 | 扫描记录的权威租户 | 服务用户配置组织或记录权威组织 | 服务端配置 + 记录读取 | 无合法普通用户时失败关闭，禁止回退 admin |
| `PROCESS_CALLBACK` | 回调系统身份；后续人工责任仍引用原 run actor | 持久化 run/link 的租户 | 持久化 run 的组织 | 已验证 Flowable 结果事件 + `processInstanceId/businessKey` | 关联不唯一、跨租户、状态不匹配或结果已消费时拒绝/幂等返回 |
| `EXTERNAL` | 可信 USER delegation；纯服务身份只允许非人工责任动作 | Token 中的权威租户 | Token 中的权威组织 | Capability/OAuth 执行身份 | 请求 Header/Body 自报身份无效；审批无普通用户时失败关闭 |

所有来源进入数据库前在最小边界建立租户和数据权限上下文，并在 `finally` 恢复。画布只能选择身份策略引用，不能保存用户 Token、Secret 或任意 actor ID。

## 4. 状态机与 CAS 基线

### 4.1 Process Run

| 当前状态 | 允许下一状态 | CAS 条件 |
|---|---|---|
| `PENDING` | `RUNNING/FAILED/CANCELED` | `tenant_id + id + status=PENDING` |
| `RUNNING` | `WAITING/SUCCESS/FAILED/CANCELED` | `tenant_id + id + status=RUNNING + current_node_id` |
| `WAITING` | `RUNNING/FAILED/CANCELED` | `tenant_id + id + status=WAITING + current_node_id + correlation` |
| `FAILED` | `PENDING` | 仅 retryable、未超过次数、人工权限通过；增加 retry count |
| `SUCCESS/CANCELED` | 无 | 终态不可逆 |

### 4.2 Node Run

| 当前状态 | 允许下一状态 | 规则 |
|---|---|---|
| `PENDING` | `RUNNING/CANCELED` | 每个 `attemptNo` 只认领一次 |
| `RUNNING` | `SUCCESS/WAITING/FAILED` | 输出、错误和关联 ID 只通过 CAS 写入 |
| `WAITING` | `SUCCESS/FAILED/CANCELED` | 只由匹配 correlation 的恢复事件消费 |
| `FAILED` | 无 | 重试必须新增 `attemptNo`，禁止复活旧尝试 |
| `SUCCESS/CANCELED` | 无 | 终态不可逆 |

### 4.3 Approval Wait

审批启动成功并同时获得 `processInstanceId + businessKey` 后，节点才可由 `RUNNING` 进入 `WAITING`。回调必须匹配 `tenantId + runId + nodeId + processInstanceId + businessKey`，并将 `APPROVED/REJECTED/CANCELED/FAILED` 映射到同名出口；首次 CAS 消费后节点进入终态并恢复 run，重复或乱序回调不得再次调度后继节点。

## 5. 安全、权限与状态机审查

- 应用拥有流程，业务对象拥有记录；`businessKey` 固定 `<objectCode>:<recordId>`。
- 业务画布为 DAG，Flowable 内部仍可表达会签、驳回、退回和受控循环。
- 同一业务记录只允许一个活动审批子流程，启动前后均以稳定幂等键和 Flowable 侧 businessKey 防重。
- 手动、事件、定时、回调和外部入口分别使用可信身份，不允许 admin 兜底。
- 状态字段只能通过领域状态服务、审批状态映射或显式受控动作更新，结束节点无隐藏副作用。
- 外部调用只引用受治理能力/连接；自由 URL、Secret、任意 SQL/Java/脚本全部失败关闭。
- 版本与运行记录不可变/可审计；旧配置只读兼容和幂等迁移，不物理删除。
- 当前用户已明确授权按上述默认值进入开发；真实数据库、Flowable 和状态迁移验收仍保留人工执行门禁。

## 6. 功能覆盖矩阵

| 功能 | 自动化证据 | 环境证据 |
|---|---|---|
| 1-8 | CRUD/Schema/图校验单测、前端协议与画布测试 | 应用工作台创建、保存、问题定位 |
| 9-18 | 开始、条件、动作、结束、子流程执行器合同测试 | 事件、定时、手动三类真实记录 |
| 19-23 | Approval executor、回调重复/乱序/跨租户测试 | Flowable 通过、驳回、取消、状态修复 |
| 24-30 | run/node CAS、幂等、恢复、身份和日志脱敏测试 | 服务重启恢复、人工重试、权限拒绝 |
| 31-35 | 发布版本、快照、回滚、运行查询与 readiness 测试 | 应用发布/回滚后新旧实例并存 |
| 36-40 | migration preview/apply、旧入口守卫与样例协议测试 | 存量配置沙箱迁移和采购样例 E2E |

## 7. 增量验证命令

低成本检查：

```bash
git diff --check -- code-copilot/changes/application-business-process-orchestrator
rg -n '\$\{[^}]+\}' forge-server/db/migration/V1.0.83__add_application_business_process.sql forge-server/db/migration/V1.0.84__add_application_business_process_resources.sql
```

后端定向测试与编译：

```bash
cd forge-server
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am \
  -Dtest=BusinessProcessSchemaValidatorTest,BusinessProcessOrchestratorTest,ApprovalProcessNodeExecutorTest,BusinessProcessMigrationServiceTest,GovernedActionStepExecutorTest test

JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am compile -DskipTests
```

前端定向测试、Lint 与构建：

```bash
source ~/.nvm/nvm.sh && nvm use v20.19.0
cd forge-admin-ui
pnpm exec vitest run src/components/business-process-designer/__tests__/business-process-designer.spec.js
pnpm exec eslint src/components/business-process-designer src/views/app-center src/api/business-process.js
NODE_OPTIONS=--max-old-space-size=8192 pnpm build
```

## 8. 真实环境验收清单

- [ ] MySQL 8 新库、存量库、重复执行 Flyway，检查 `forge_schema_history`、四张表、唯一索引和墓碑删除。
- [ ] Admin 与 Flow 服务启动装配，确认无 Mapper 重复、循环依赖和缺失 Bean。
- [ ] 手动提交、记录新增、定时分层提醒分别创建唯一 run。
- [ ] 审批通过、驳回、申请人修改重提、取消、终结和完成后状态修复。
- [ ] 服务在 `RUNNING/WAITING/FAILED` 时重启，扫描恢复不重复副作用。
- [ ] 应用发布、草稿修改、再次发布和回滚，运行实例固定原版本。
- [ ] 旧触发器/FLOW Binding/动作迁移预览、重复 apply、停写和旧实例继续办理。
- [ ] 跨租户、跨应用、无数据权限、伪造 actor、无服务发起人、回调重放和 Secret/URL 注入全部失败关闭。

## 9. 执行记录约定

每轮结果追加到 `execution-log.md`，写明实际命令、Tests run、构建结果、警告、跳过项和本轮服务 PID。未执行的真实 E2E、数据库迁移或浏览器验收不得标记通过。

## 10. Task 7 增量验证

- 控制面服务：创建默认草稿、编码不可变、结构不完整草稿可保存、跨应用主对象拒绝、草稿 hash 409、复制重建图 ID、运行/发布引用删除门禁。
- API 合同：独立加解密命名空间、`pageNum/pageSize`、CRUD/设计/校验/启停/删除权限注解。
- 校验上下文：应用对象和字段、发布动作快照、真实权限资源、已绑定且已部署的 Flowable 模型、表单/消息/同应用已发布子流程；能力桥接继续失败关闭。
- 数据库静态门禁：`V1.0.85` 使用 `tenant_id=1`、显式列、`NOT EXISTS` 和既有运行权限继承；无 Flyway `${...}` 占位符和 `tenant_id=0`。
- 必跑命令：Task 6/7 的 5 个定向测试类、三份 Mapper XML `xmllint`、目标差异 `git diff --check`、`forge-admin-server -am compile -DskipTests`。
- 环境门禁：真实 Flyway、权限继承查询、Flowable 已发布/未发布模型响应和加密 HTTP API 留待 Task 19；未执行前不标记通过。
