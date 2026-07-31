# 统一能力开放平台（一期：REST 开放网关闭环）
> status: propose
> created: 2026-07-31
> complexity: 🔴复杂

## 1. 背景与目标

客户需求（20260728需求）中 API-01~07（外部机器身份 API 底座）阻断差评流程 F1/NR-04 外部建单。经代码核查，现有 Capability 平台（AI 中枢）已具备机器身份、授权、审计、OAuth2.1、字段白名单等核心底座，但**只有 MCP 出口，没有面向普通外部系统的 REST 出口**，也没有管理控制台。

本变更目标（一期范围）：
1. 新增统一 REST 开放网关 `POST /openapi/v1/capabilities/{capabilityCode}/invoke`，形成"认证→防重放→授权→限流→幂等→Schema 校验→执行→统一响应→审计"完整闭环。
2. 新增第二种认证模式：AppId + timestamp + nonce + HMAC-SHA256 请求签名（补齐 API-02/API-03）。
3. 能力元数据新增 `required_actor_type`（SERVICE/USER/BOTH），身份类型校验前移到网关授权阶段；USER 委托 Token 在 REST 网关复用现有 `ExecutionIdentityContextHolder` 链路。
4. 将定时任务开放 API 的限流/幂等模式抽取为通用组件供网关复用（job 模块存量不动）。
5. 补齐控制台前端 4 个页面：能力目录、机器客户端、授权管理、调用日志。

完成后可验证效果：外部系统使用机器客户端凭据（OAuth 或签名模式），调用已授权的 `BUSINESS_ACTION` 能力（如差评建单），带 `Idempotency-Key` 重试不产生重复单据；持 USER 委托 Token 可通过网关调用 `FLOW_ACTION` 能力完成流程审批（办理人校验生效）；调用记录可在控制台查询；机器身份调用 `requiredActorType=USER` 的能力被网关直接拒绝。

## 2. 代码现状（Research Findings）

### 2.1 相关入口与链路
- MCP 出口：`forge-plugin-mcp/config/ForgeMcpServerConfiguration.java`，Streamable HTTP 单端点 `/mcp`，默认 `FORGE_MCP_ENABLED=false`。
- OAuth2.1 端点：`forge-plugin-capability-identity/token/CapabilityTokenController.java`（`/oauth2/token`、`/oauth2/revoke`），授权码+PKCE 见 `oauth/CapabilityAuthorizationController.java`。
- 控制面接口：`forge-plugin-capability-control-plane/controller/CapabilityCatalogController.java`（`/ai/capability`）、`CapabilityClientController.java`（`/ai/capability/client`）、`CapabilityGrantController.java`（`/ai/capability/grant`），权限点 `ai:capability:*` 已由 V1.0.21 落库并授予 admin。
- Sa-Token 白名单机制：`forge-starter-auth/config/SaTokenConfig.java`，已有 `/mcp`、`/oauth2/*`、`/openapi/v1/jobs/**` 放行先例。

### 2.2 现有实现
- **身份认证**：`CapabilityAccessTokenService.authenticate()` 校验 `fdu_` 短期 Token（HMAC 哈希存储、audience、scope、credential_version），`loadCurrentUser()` 每次请求实时从用户目录重建 `LoginUser`（校验状态/租户/组织/强制改密），返回 `AuthenticatedCapabilityIdentity(principal, loginUser)`。
- **执行上下文**：`forge-starter-core/context/ExecutionIdentityContextHolder`（ThreadLocal）承载 `ExecutionIdentity(loginUser, actorType, ...)`；异步透传由 `TenantBusinessDataSourceTaskDecorator` 完成；跨服务流程调用由 `SaTokenFlowTokenProvider` 铸造短期委托会话，`FlowDelegationSessionVerifier` 校验。
- **执行层身份约束**：`FlowActionExecutionAdapter.requireIdentity()` 强制 `actorType=USER`，机器身份调流程动作抛 `USER_DELEGATION_REQUIRED`——但这个约束目前在执行层兜底，网关/授权层无 actorType 概念。
- **数据模型**：`ai_capability`/`ai_capability_version`/`ai_capability_client`/`ai_capability_grant`/`ai_capability_invocation_log`（V1.0.21）、`ai_capability_access_token`（V1.0.22）。`ai_capability_invocation_log` 已有 `UNIQUE (tenant_id, request_id)`。
- **可复用的限流/幂等参考实现**：`forge-plugin-job/manager/JobApiRateLimitManager.java`（Redisson RRateLimiter，按 keyId+操作分钟级，Redis 不可用失败关闭 503）、`JobApiIdempotencyManager.java`（Idempotency-Key 格式校验 + SHA-256 哈希 + Redisson 锁 + DB 唯一约束回查）。
- **前端现状**：无任何 capability 管理页面；`job-api-token.vue` 是成熟的凭据管理页参考（创建/轮换/吊销/一次性展示/调用说明）。

### 2.3 发现与风险
- `ai_capability_client.key_hash` 是 HMAC-SHA256 单向哈希，**服务端无法还原密钥原文，不能直接用于请求签名验签**。签名模式必须新增可逆加密存储的独立签名密钥（KEK 加密，复用 forge-starter-crypto 体系），不能改动现有 Bearer 凭据的哈希语义。
- 配置默认全关且失败关闭：`forge.capability.client-pepper`、`identity.token-pepper` 等无默认值。网关须延续该策略。
- `ai_capability_grant.field_policy` 字段白名单已被 secure-actions 链消费，REST 网关须走同一策略入口，不得旁路。
- 幂等需要缓存首次响应用于重试返回，`invocation_log` 不存响应体（安全设计），需新增专用幂等表。

## 3. 功能点

- [ ] 功能 1：REST 开放网关（外部请求 → 认证/防重放/授权/限流/幂等/Schema 校验 → CapabilityExecutor → 统一响应 `{code,message,requestId,timestamp,data}` + 审计入库）
- [ ] 功能 2：签名认证模式（`X-Forge-App-Id` + `X-Forge-Timestamp` + `X-Forge-Nonce` + `X-Forge-Signature` → 验签 + 时间窗 ±5 分钟 + nonce Redis 一次性校验 → 得到 SERVICE 身份）
- [ ] 功能 3：Bearer 认证模式复用（`Authorization: Bearer fdu_...` → `CapabilityAccessTokenService.authenticate()` → SERVICE 或 USER 身份）
- [ ] 功能 4：能力 `required_actor_type` 元数据（发布时声明 → 网关授权阶段校验身份类型 → 不匹配返回 403 `ACTOR_TYPE_NOT_ALLOWED`）
- [ ] 功能 5：通用开放 API 限流/幂等组件（新 starter，泛化 job 模块实现；写操作强制 `Idempotency-Key`，命中幂等返回首次响应快照 + `idempotentHit=true`）
- [ ] 功能 6：控制台前端 4 页面（能力目录/机器客户端/授权管理/调用日志，对接现有 `/ai/capability/*` 接口；客户端页新增签名密钥管理）
- [ ] 功能 7：客户端签名凭据生命周期（创建时一次性展示、轮换、吊销；KEK 加密存储；前端展示脱敏保留前4后4）

## 4. 业务规则

1. 网关只允许调用 `publish_status=PUBLISHED`、`enabled=1` 且对当前客户端存在 `ENABLED` 未过期 grant 的能力；版本按 grant 的 `version_strategy` 解析。一期开放能力类型不设限（含 `FLOW_ACTION`），由 `required_actor_type` + grant 控制边界。
2. 身份类型规则：`required_actor_type=USER` 的能力（流程审批类）拒绝签名模式和 SERVICE Bearer；`SERVICE` 的能力拒绝 USER 委托调用；`BOTH` 均可。存量能力回填默认值：`FLOW_ACTION` → `USER`，其余 → `SERVICE`。
3. 写操作（`behavior != READ_ONLY`）必须携带 `Idempotency-Key`（8-128 位 `[A-Za-z0-9._:-]`），缺失返回 400 `missing_idempotency_key`。
4. 租户、用户、组织一律从凭据绑定关系解析（客户端的 `service_user_id`/`active_org_id` 或委托 Token 的 `actor_user_id`），**禁止**从 Header/Body 接受调用方指定。
5. 防重放：timestamp 偏差超过 ±5 分钟拒绝；nonce 在 Redis 内 SETNX 保存 10 分钟，重复即拒绝；Redis 不可用时失败关闭（503）。
6. 签名串固定为 `appId\ntimestamp\nnonce\nMETHOD\npath\nsha256(body)`，HMAC-SHA256，常量时间比较。
7. 限流默认：读能力 120 次/分钟/客户端，写能力 20 次/分钟/客户端，可配置；超限 429。
8. 高风险（HIGH）能力延续现有禁止授权策略，网关不作特殊放开。
9. 审计：每次调用（含被拒绝的）写 `ai_capability_invocation_log`，不保存请求/响应原文。

## 5. 数据变更

| 操作 | 表名 | 字段/索引 | 说明 |
|------|------|-----------|------|
| 新增列 | ai_capability | `required_actor_type varchar(16) NOT NULL DEFAULT 'SERVICE'` | 能力要求的调用主体类型；存量 FLOW_ACTION 回填 USER |
| 新增列 | ai_capability_version | `required_actor_type varchar(16) NOT NULL DEFAULT 'SERVICE'` | 版本快照同步 |
| 新增列 | ai_capability_client | `auth_modes varchar(64) NOT NULL DEFAULT 'OAUTH'` | 允许的认证模式：OAUTH / SIGNATURE / 两者 |
| 新增列 | ai_capability_client | `signing_key_cipher varchar(512) DEFAULT NULL`、`signing_key_version int DEFAULT NULL` | KEK 加密的签名密钥密文及密钥版本 |
| 新增表 | ai_capability_openapi_idempotency | `(id, tenant_id, client_id, capability_id, idempotency_key_hash, request_id, response_snapshot json, expires_at, 标准审计列, del_flag)`；`UNIQUE(tenant_id, client_id, capability_id, idempotency_key_hash, logic_delete_active)` | 幂等记录 + 首次响应快照（24h TTL 清理） |
| 新增数据 | sys_dict_type / sys_dict_data | `ai_capability_actor_type`、`ai_capability_auth_mode` | 字典，tenant_id=1，NOT EXISTS 防重复 |
| 新增数据 | sys_resource / sys_role_resource | 新建"开放平台"一级目录（resource_type=1）+ 其下 4 个菜单（resource_type=2） | 权限点复用 V1.0.21 已有 `ai:capability:*`，仅补目录与菜单路由 |

脚本：`V1.0.74__capability_open_gateway.sql`（防重复保护、tenant_id=1、显式列名）。

## 6. 接口变更

| 操作 | 接口 | 方法 | 变更内容 |
|------|------|------|----------|
| 新增 | `/openapi/v1/capabilities/:capabilityCode/invoke` | POST | 统一能力调用入口（外部，Sa-Token 白名单放行后自行鉴权） |
| 新增 | `/ai/capability/client/signing-key/rotate/:id` | POST | 轮换签名密钥，`@ApiEncrypt` 一次性返回明文 |
| 修改 | `/ai/capability/publish` | POST | `CapabilityPublishDTO` 增加 `requiredActorType` |
| 修改 | `/ai/capability/client/add` | POST | `CapabilityClientCreateDTO` 增加 `authModes`；选择 SIGNATURE 时一次性返回签名密钥 |
| 新增 | `/ai/capability/invocation/page` | GET | 调用日志分页查询（权限点 `ai:capability:invocation:query` 已存在） |

统一响应（网关）：`{"code":"SUCCESS|错误码","message":"...","requestId":"...","timestamp":epochMillis,"data":{}}`；错误码集合：`UNAUTHORIZED`/`REPLAY_REJECTED`/`FORBIDDEN`/`ACTOR_TYPE_NOT_ALLOWED`/`RATE_LIMITED`/`SCHEMA_INVALID`/`CONFLICT`/`INTERNAL_ERROR`。

## 7. 影响范围

- 后端新增：`forge-plugin-capability-open-gateway`（新插件：网关 Controller、认证过滤链、编排服务）、`forge-starter-openapi-security`（通用限流/幂等/防重放组件）。
- 后端修改：capability-control-plane（发布 DTO/实体/客户端凭据）、forge-starter-auth `SaTokenConfig`（白名单追加 `/openapi/v1/capabilities/**`）、application.yml（`forge.capability.open-gateway.*` 配置组，默认关闭）。
- 前端新增：`forge-admin-ui/src/views/ai/capability/` 4 个页面 + `src/api/ai/capability.js`。
- 不动：forge-plugin-job 存量代码与 `/openapi/v1/jobs` 契约（迁移复用组件列为后续变更）；MCP 链路；Flowable。

## 8. 风险与关注点

> ⚠️ 本变更属于**权限边界扩展**（将内部能力开放给外部系统调用），必须人工审查。

1. **权限面扩大**：网关是新的外部攻击面。缓解：默认关闭（`FORGE_CAPABILITY_OPEN_GATEWAY_ENABLED=false`）、失败关闭策略、能力需显式授权、HIGH 风险禁止授权延续。
2. **签名密钥可逆存储**：与现有哈希凭据不同，签名密钥必须 KEK 加密可逆存储。缓解：复用 crypto 密钥体系、密文列脱敏、明文仅创建/轮换时一次性展示、审计轮换操作。
3. **on-behalf-of 明确排除**：一期不实现"机器身份传操作人"模式，`required_actor_type=USER` 能力只接受用户委托 Token。
4. **幂等响应快照含业务数据**：`response_snapshot` 可能含敏感字段。缓解：24h TTL 清理任务物理清理（属留存清理场景，允许物理删除）、快照只存网关统一响应体、控制台不展示快照内容。
5. **Schema 校验旁路风险**：执行前必须按解析到的版本 `input_schema` 校验并按 grant `field_policy` 过滤，禁止直通。

## 8.5 测试策略

- **测试范围**：签名验签/时间窗/nonce 重放单测；网关编排链（认证→授权→actorType→限流→幂等→Schema）分支单测；幂等并发（Redisson 锁 + 唯一约束回查）单测；控制台页面 lint + 手工验证；端到端 curl 验证（OAuth 模式 + 签名模式各一条，含幂等重试）。
- **覆盖率目标**：网关编排与安全组件核心类 ≥ 80%。
- **独立 Test Spec**：是（安全链路分支多）。

## 9. 待澄清

- [x] 问题 1：签名模式是否一期必须交付？→ **确认：放到一期**（Task 3/签名相关字段全部保留）。
- [x] 问题 2：一期开放能力范围是否限定 `READ_ONLY + BUSINESS_ACTION`？→ **确认：不限定，`FLOW_ACTION` 的 USER 委托 REST 调用放到一期**（网关不按能力类型设限，端到端测试须覆盖 USER 委托审批链路）。
- [x] 问题 3：控制台菜单挂载位置？→ **确认：新建"开放平台"一级目录**，4 个菜单挂其下。

## 10. 技术决策

1. **不另建开放平台内核**：复用 ai_capability 数据模型与 CapabilityExecutor 执行内核，网关只是新出口。
2. **双认证模式并存**：Bearer（OAuth，SERVICE/USER）为主，签名（仅 SERVICE）为兼容传统对接方的补充；按客户端 `auth_modes` 白名单启用。
3. **身份桥接复用 MCP 链路**：网关认证成功后 `ExecutionIdentityContextHolder.open(identity)` 包裹执行，下游租户/数据权限/审计零改动。
4. **job 模块不动**：通用组件以 job 实现为蓝本重写于新 starter，job 存量迁移作为独立后续变更，避免影响已上线契约。
5. **actorType 校验前移**：授权阶段依据能力 `required_actor_type` 拒绝，比执行层兜底（`USER_DELEGATION_REQUIRED`）提前失败；执行层约束保留作为纵深防御。
6. **FLOW_ACTION 一期入网关**（2026-07-31 确认）：USER 委托 Token 经网关调用流程动作，复用 `SaTokenFlowTokenProvider` 铸造委托会话与 `FLOW_TASK_ASSIGNEE_MISMATCH` 办理人校验，网关侧不重复实现流程语义。

## 11. 执行日志

| Task | 状态 | 实际改动文件 | 备注 |
|------|------|--------------|------|

## 12. 审查结论

## 13. 确认记录（HARD-GATE）
- **确认时间**：2026-07-31
- **确认人**：yaomindong
- **确认内容**：第 9 节 3 个待澄清问题全部裁决——签名模式一期交付；FLOW_ACTION USER 委托 REST 调用一期交付；控制台新建"开放平台"一级目录。可进入 /apply。
