# 统一能力开放平台产品化改造
> status: implemented_pending_environment_e2e_review
> created: 2026-08-01
> complexity: 🔴复杂

## 1. 背景与目标

现有统一能力开放平台已经具备 REST 网关、OAuth/HMAC 认证、客户端授权、限流、幂等和审计能力，但执行端仍只识别低代码业务动作与对象流程动作；能力文档仅提供 OpenAPI JSON 且当前前端下载为空；管理员完成客户端与授权配置后，也无法直接判断是否可调用或复制一条正确请求。

本变更目标：

1. 将开放网关从低代码 `suite/object/action` 来源键解耦为可扩展执行适配层，保留现有业务动作、流程动作行为。
2. 新增受控的 `SYSTEM_SERVICE` 能力来源。系统服务只能由代码显式注册，管理端不可填写任意 URL、Bean 名或请求目标。
3. 交付首个系统服务“启动已发布流程”：发布时固定流程模型及允许变量，调用者不能指定模型、租户、用户或组织。
4. 默认生成详细 Markdown 调用文档，同时保留 OpenAPI 3.1 JSON；两者均来自不可变的已发布能力版本。
5. 能力目录主操作改为“调用指南”，让管理员选择客户端后看到可调用状态、阻断原因、认证参数及可复制的 OAuth/HMAC 示例。
6. 将 REST OAuth audience 与 MCP audience 分离，避免为 `/mcp` 签发的 Token 被误用于 REST 开放网关，同时保持 MCP 兼容。

## 2. 范围与非目标

### 2.1 本期范围

- 通用执行适配器注册、来源解析和受控执行。
- `SYSTEM_SERVICE` 注册来源查询、发布和网关调用。
- 已发布流程启动系统服务。
- Markdown/OpenAPI 文档生成与下载。
- 客户端调用指南和就绪诊断。
- 独立 OpenAPI OAuth resource 配置与校验。

### 2.2 明确不做

- 不实现任意内部 REST URL、HTTP Method 或 Header 的动态代理。
- 不允许外部请求指定 `tenantId`、`userId`、`activeOrgId`、`initiator` 或流程 `modelKey`。
- 不将 Spring Bean 名、Controller 路径或数据库 SQL 暴露为配置项。
- 不自动绕过 Forge 权限、业务权限、流程模型状态或租户隔离。
- 不移除低代码“业务对象 → 流程”绑定；该绑定继续负责业务单据状态回调、表单权限与记录/流程关联。

## 3. 架构设计

### 3.1 通用执行边界

开放网关通过执行适配器集合解析已发布能力，而不再自行拆解低代码来源键。每个适配器必须：

- 明确支持的 `sourceType` 与 `behavior`。
- 根据版本快照解析并校验来源。
- 返回稳定的执行描述符与所需权限。
- 在可信 `ExecutionIdentity` 上下文内执行。
- 对未知来源、失效来源或快照漂移失败关闭。

首批适配器：

| 适配器 | 来源类型 | 兼容性 |
|---|---|---|
| 业务动作适配器 | `BUSINESS_ACTION` + `ACTION` | 保持现有低代码动作调用 |
| 对象流程适配器 | `FLOW_ACTION` + `FLOW` | 保持现有对象流程 START/APPROVE 等调用 |
| 系统服务适配器 | `SYSTEM_SERVICE` + `ACTION` | 新增，仅执行代码注册的服务定义 |

### 3.2 系统服务注册模型

系统服务定义由 Spring Bean 代码注册，每项必须声明：

- 稳定 `serviceCode`、名称、描述和版本。
- 行为类型、风险等级、调用主体类型。
- 输入/输出 JSON Schema 与示例。
- Forge 权限点和业务校验说明。
- 发布快照生成器、快照重校验器和执行器。

管理端只能从注册表中选择服务并填写该服务显式允许的发布参数。未知 `serviceCode`、重复注册、发布快照缺失或注册定义变化均拒绝发布或调用。

### 3.3 流程启动系统服务

发布参数由管理员选择已发布且启用的 `sys_flow_model`。发布版本策略快照固定：

- `serviceCode=flow.process.start`
- `modelId`、`modelKey`、模型版本及部署标识
- 允许传入的流程变量 Schema
- `requiredActorType=USER`
- 需要的 Forge 权限
- 业务规则和错误说明

外部请求只允许：

- `businessKey`：外围业务唯一键。
- `title`：可选流程标题。
- `variables`：发布时允许的变量集合。

执行前重新校验模型仍为已发布/启用状态且关键快照一致；真实用户、租户、组织从 USER 委托身份获取。调用 `FlowClient.startProcessForDelegatedUser(...)`，并复用流程服务基于 `tenant + businessKey` 的幂等约束。

### 3.4 文档事实来源

文档只从当前已发布不可变版本读取：`inputSchema`、`outputSchema`、`policySnapshot`、actor type、behavior、risk level 和版本信息。业务说明存入 `policySnapshot.documentation`：

```json
{
  "documentation": {
    "businessRules": ["..."],
    "requestNotes": ["..."],
    "responseNotes": ["..."]
  }
}
```

Markdown 包含概述、地址/版本、主体要求、认证方式、Header、递归入参/返回参数表、示例、业务规则、权限、幂等/限流、错误码、OAuth/HMAC 示例和排障说明。OpenAPI 3.1 继续用于机器导入；`Idempotency-Key` 仅作为 Header，不得出现在请求 Body Schema 中。

## 4. 功能需求

- [x] 功能 1：通用网关执行适配器及未知来源失败关闭。
- [x] 功能 2：现有 BUSINESS_ACTION/FLOW_ACTION 迁移到适配器，调用契约不变。
- [x] 功能 3：系统服务注册来源列表与受控发布接口。
- [x] 功能 4：流程启动系统服务发布、快照校验和执行。
- [x] 功能 5：默认 Markdown 文档下载及 OpenAPI JSON 下载。
- [x] 功能 6：修复 Blob 下载链路，响应拦截器支持显式保留 Blob。
- [x] 功能 7：客户端调用指南与就绪诊断。
- [x] 功能 8：OpenAPI/MCP OAuth audience 分离。
- [x] 功能 9：显式敏感请求提交前同步后端运行态加密开关，避免配置切换后业务 DTO 绑定为空。
- [x] 功能 10：能力目录、注册摘要与授权操作统一使用中文字典，字典瞬时失败不缓存空结果。
- [x] 功能 11：Open Gateway 开启时同步注册 OAuth Token、元数据、用户信息和身份运行组件，消除 `/oauth2/token` 404 的半开启状态。
- [x] 功能 12：停用能力可在当前已发布版本仍有效时重新启用。
- [x] 功能 13：调用指南支持真实 OAuth/HMAC 在线测试、脱敏完整报文下载和可复制 Java 17 示例。
- [x] 功能 14：无统一 OIDC 的客户端可使用独立 RSA 用户断言密钥，通过预绑定外部用户标识安全委托 Forge 真实用户。

## 5. 业务与安全规则

1. 任何未由代码注册的来源均不得执行；不提供任意 URL 转发兜底。
2. 能力调用始终经过原有认证、防重放、grant、actor、权限、限流、幂等、Schema 与字段策略校验。
3. 系统服务发布与执行都必须校验注册定义，发布快照是唯一运行契约。
4. 流程启动必须使用 USER 委托身份；SERVICE/HMAC 身份返回 `ACTOR_TYPE_NOT_ALLOWED`。
5. `modelKey/modelId/tenantId/userId/activeOrgId/initiator` 不属于流程启动请求 Schema；额外字段由 Schema 校验拒绝。
6. 流程模型在执行时失效、被停用、重新部署或快照不一致时失败关闭，不产生流程实例。
7. 文档、指南和日志不得包含客户端密钥、签名密钥、Bearer Token、用户手机号或敏感业务报文。
8. 调用指南中的 Secret/Token 只使用占位符；服务端不得尝试还原 OAuth 客户端密钥。
9. readiness 只展示当前可静态判断的结果；USER 最终业务权限标明由实际委托用户在运行时校验。
10. OpenAPI REST Token 的 audience 为独立 resource；MCP Token 不得调用 REST 网关，反之亦然。
11. 高风险能力继续禁止授权，不因系统服务类型放开。
12. 默认网关开关保持关闭，未启用时指南明确显示阻断原因和配置项。
13. 在线测试必须走真实 `/oauth2/token` 和 `/openapi/v1/capabilities/:code/invoke` 链路，不得提供绕过认证、授权、限流、幂等或业务校验的测试后门。
14. Secret、Signing Key、Bearer Token 和 HMAC Signature 只允许存在于浏览器弹窗内存；切换客户端或关闭弹窗即清理，展示和下载报文必须脱敏。
15. `ACTION/FLOW/MESSAGE/EXTERNAL` 在线测试属于真实副作用操作，必须二次确认并自动生成一次性 `Idempotency-Key`。
16. 重新启用能力前必须确认 `currentVersion` 对应版本仍存在且为 `PUBLISHED`；不自动恢复已撤销授权。
17. 客户端用户断言只接受 RS256；私钥只展示一次且不得落库，Forge只保存公钥、`kid` 和版本。
18. 外围 `sub` 必须由管理员预绑定到 Forge 普通用户；禁止直接传 Forge `userId/tenantId/roleId/permission`，禁止自动绑定管理员身份。
19. 断言最长有效两分钟，必须携带唯一 `jti` 并通过 Redis 一次性校验；验签、防重放或用户目录不可用时失败关闭。
20. 客户端断言与受信 OIDC 使用不同 `subject_token_type`，不得在验签失败后相互回退。

## 6. 接口变更

| 操作 | 接口 | 方法 | 说明 |
|---|---|---|---|
| 修改 | `/ai/capability/:id/openapi` | GET | 保留 OpenAPI 3.1 JSON 下载 |
| 新增 | `/ai/capability/:id/document` | GET | 默认下载 Markdown 调用文档 |
| 新增 | `/ai/capability/:id/call-guide` | GET | 按客户端返回就绪诊断与安全调用示例 |
| 新增 | `/ai/capability/enable/:id` | POST | 校验当前发布版本后重新启用已停用能力 |
| 新增 | `/ai/capability/system-service/registration-source` | GET | 返回可注册系统服务及受控参数来源 |
| 新增 | `/ai/capability/system-service/publish` | POST | 发布受控系统服务能力 |
| 修改 | `/oauth2/token` | POST | resource 参数支持独立 OpenAPI resource |
| 新增 | `/ai/capability/client/:id/user-assertion` | GET | 查看客户端用户断言协议和脱敏映射 |
| 新增 | `/ai/capability/client/:id/user-assertion/key/rotate` | POST | 生成/轮换 RSA-2048 密钥，PKCS#8 私钥仅一次返回 |
| 新增 | `/ai/capability/client/:id/user-assertion/disable` | POST | 停用用户断言并递增客户端凭据版本 |
| 新增 | `/ai/capability/client/:id/user-assertion/mapping` | POST | 预绑定外围 `sub` 到 Forge 普通用户 |
| 新增 | `/ai/capability/client/:id/user-assertion/mapping/:mappingId` | DELETE | 解除外围用户映射 |
| 不变 | `/openapi/v1/capabilities/:capabilityCode/invoke` | POST | 外部统一调用入口不变 |

## 7. 配置变更

```yaml
forge:
  capability:
    identity:
      resource: ${FORGE_CAPABILITY_MCP_RESOURCE:http://localhost:8580/mcp}
      openapi-resource: ${FORGE_CAPABILITY_OPENAPI_RESOURCE:http://localhost:8580/openapi}
      user-assertion-max-ttl: ${FORGE_CAPABILITY_USER_ASSERTION_MAX_TTL:2m}
      user-assertion-clock-skew: ${FORGE_CAPABILITY_USER_ASSERTION_CLOCK_SKEW:30s}
```

`resource` 保留为 MCP resource 以兼容存量配置；REST 网关只接受 `openapi-resource`。

## 8. 数据变更

系统服务及流程模型固定信息、业务文档写入已存在的能力版本 Schema/`policy_snapshot`；能力来源继续使用 `source_type/source_key/source_version`。

新增 Flyway `V1.0.79__add_capability_client_user_assertion.sql`：客户端表增加用户断言开关、`kid`、X.509 PEM 公钥和密钥版本；外部身份映射增加脱敏 `subject_hint`。私钥和原始外围 `sub` 均不落库，已有逻辑删除唯一索引继续允许解除后重新绑定。

新增 Flyway 权限资源迁移 `V1.0.77__add_capability_system_service_permissions.sql`，为系统服务来源查询和受控发布接口补充菜单/API 权限资源；新增 `V1.0.78__add_capability_catalog_dicts.sql`，补齐能力来源类型与行为类型中文字典。脚本均使用 `tenant_id=1` 和 `NOT EXISTS` 防重复保护，不包含业务数据表结构变更。

## 9. 易用性验收

管理员在能力目录应能完成以下闭环：

1. 点击“注册能力”，选择“系统服务 → 启动流程”，再选择一个已发布流程模型。
2. 发布后点击“调用指南”，选择一个客户端。
3. 页面明确显示“可调用”或逐条阻断原因，而不是只返回模糊 403。
4. 页面可复制 OAuth 或 HMAC 请求；例子中的 URL、能力编码、resource、Header 和 Body 与真实网关契约一致。
5. 下载 `.md` 可读到完整入参、返回参数、业务校验和错误排查；下载 `.json` 可导入 OpenAPI 工具。
6. 页面可临时输入 Client Secret、Signing Key 或受信 OIDC Token，使用真实网关在线测试；有副作用能力执行前必须明确确认。
7. 测试结果展示 Token/调用两段请求与响应、HTTP 状态、Header、Body 和耗时，并可下载脱敏 JSON 报文。
8. 页面提供 OAuth 与 HMAC 的 Java 17 标准库示例；凭据从环境变量注入，接入示例可下载为 Markdown。
9. 能力停用后显示“启用”，重新启用成功后已有有效授权可继续使用。
10. 没有统一 OIDC 时，可在客户端页面生成/轮换 RSA 密钥、一次下载私钥，并把外围 `sub` 预绑定到 Forge 普通用户。
11. 在线测试可在“受信 OIDC JWT / 客户端签名用户断言”之间明确选择；客户端断言模式可粘贴私钥和外围 `sub` 后由浏览器临时生成两分钟 JWT。
12. 接入示例包含专用 `subject_token_type`、固定 claims 和完整 Java 17 RS256 签名/Token Exchange/能力调用代码。

## 10. 测试策略

- 通用适配器：正确分派、重复适配器、未知来源、来源失效、执行异常映射。
- 文档：递归 Schema 表、业务规则、认证示例、敏感字段排除、Body/Header 幂等一致性。
- Audience：MCP/REST 分离与存量兼容。
- 流程启动：禁止调用方指定身份/模型字段、必须 USER、模型快照漂移拒绝、正常委托启动。
- 指南：网关开关、客户端状态、grant 状态、actor/auth/resource/权限诊断。
- 前端：Blob 保留、调用指南交互、Markdown/OpenAPI 下载、定向 ESLint 与生产构建。
- 在线测试：OAuth client credentials、USER Token Exchange、HMAC 签名、副作用确认、幂等 Header、敏感字段脱敏和报文下载。
- 状态恢复：当前版本有效时可重新启用，版本缺失或未发布时拒绝。
- 用户断言：错误签名/`kid/iss/aud/client_id`、过期/超长 TTL、`jti` 重放、Redis 不可用、未映射用户、管理员用户、密钥轮换/停用与 OIDC 无回退。
- 聚合：相关 Maven 模块测试及 Admin 聚合编译。

## 11. 风险与回滚

本变更扩展外部调用边界，属于权限敏感变更，需人工审查。代码回滚时可先停用所有客户端用户断言，再回滚身份验证、控制面和前端代码；V1.0.79 新增列可保留为未使用兼容字段，避免破坏已执行的 Flyway 历史。既有 OIDC、业务动作、对象流程动作和 REST 网关路径保持兼容，客户端断言验签失败不会回退到 OIDC。

## 12. 确认记录

- 2026-08-01：用户确认按上述方向开发，并强调易用性、可扩展性和安全性，以普通使用者能够看懂并完成调用为验收标准。
