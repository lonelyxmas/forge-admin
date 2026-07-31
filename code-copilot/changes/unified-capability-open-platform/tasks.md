# 任务拆分 — 统一能力开放平台（一期：REST 开放网关闭环）
> 拆分顺序：数据模型 → 接口协议 → 底层实现 → 上层编排 → 入口层
> 每个任务 = 可独立提交的原子变更（3-5 个文件）
> 每个任务必须精确到文件路径和函数签名

## 前置条件
- [x] spec.md 第 9 节待澄清问题全部确认（2026-07-31：签名模式一期；FLOW_ACTION USER 委托 REST 一期；新建"开放平台"一级目录）
- [ ] 本地 Redis 可用（防重放 nonce、限流、幂等锁均依赖）
- [ ] `forge.capability.client-pepper` / `identity.token-pepper` 已在 dev 环境配置（网关联调前提）

## Task 1: Flyway 迁移与字典
- **目标**: 落地一期全部结构变更与内置数据
- **涉及文件**:
    - `forge-server/db/migration/V1.0.74__capability_open_gateway.sql` — 新增：ai_capability / ai_capability_version 加 `required_actor_type`（存量 FLOW_ACTION 回填 USER）；ai_capability_client 加 `auth_modes`、`signing_key_cipher`、`signing_key_version`；新建 `ai_capability_openapi_idempotency` 表（含 del_flag、logic_delete_active 唯一索引）；字典 `ai_capability_actor_type`、`ai_capability_auth_mode`；新建"开放平台"一级目录（resource_type=1）+ 其下 4 个菜单 sys_resource + admin 角色绑定
- **关键约束**: 所有 DDL 查 `information_schema` 防重复；INSERT 显式列名 + NOT EXISTS；tenant_id=1

## Task 2: 通用开放 API 安全组件 starter
- **目标**: 泛化 job 模块的限流/幂等实现为可复用组件，并新增防重放组件（job 存量不动）
- **涉及文件**:
    - `forge-server/forge-framework/forge-starter-parent/forge-starter-openapi-security/pom.xml` — 新模块，注册到 forge-starter-parent 与 forge-dependencies
    - `.../openapi/security/ratelimit/OpenApiRateLimitManager.java` — 新增，Redisson RRateLimiter，key 前缀参数化
    - `.../openapi/security/idempotency/OpenApiIdempotencyManager.java` — 新增，Idempotency-Key 校验 + SHA-256 + Redisson 锁模板方法
    - `.../openapi/security/replay/OpenApiReplayGuard.java` — 新增，timestamp 窗口 + nonce SETNX
    - `.../openapi/security/config/OpenApiSecurityAutoConfiguration.java` — 新增，条件装配
- **关键签名**:
  ```java
  public void acquire(String scopeKey, String operation, RateLimitPolicy policy); // 超限抛 429 BusinessException
  public <T> IdempotencyResult<T> execute(IdempotencyCommand command, Supplier<T> action);
  public void assertNotReplayed(String appId, long timestampMillis, String nonce); // 失败关闭
  ```

## Task 3: 客户端签名凭据（依赖 Task 1；已确认一期交付）
- **目标**: 机器客户端支持签名密钥的创建/轮换/吊销与 KEK 加密存储
- **涉及文件**:
    - `forge-plugin-capability-control-plane/.../domain/AiCapabilityClient.java` — 修改，新增 authModes / signingKeyCipher / signingKeyVersion 字段
    - `.../dto/CapabilityClientCreateDTO.java` — 修改，新增 `authModes`
    - `.../service/CapabilityClientService.java` + impl — 修改，创建含 SIGNATURE 模式时生成 32 字节随机密钥、KEK 加密落库、明文仅返回一次；新增 `rotateSigningKey(Long id)`
    - `.../controller/CapabilityClientController.java` — 修改，新增 `POST /ai/capability/client/signing-key/rotate/:id`（`@SaCheckPermission("ai:capability:client:edit")` + `@ApiEncrypt` + `@OperationLog`）
    - `.../mapper/AiCapabilityClientMapper.xml` — 修改，查询列补齐，列表查询不返回密文列
- **关键签名**:
  ```java
  public CapabilitySigningKeyVO rotateSigningKey(Long clientId); // 返回一次性明文 + keyVersion
  ```

## Task 4: 能力元数据 requiredActorType（依赖 Task 1）
- **目标**: 发布链路声明并快照 required_actor_type，授权校验可读取
- **涉及文件**:
    - `forge-plugin-capability-control-plane/.../domain/AiCapability.java`、`AiCapabilityVersion.java` — 修改，新增 requiredActorType
    - `.../dto/CapabilityPublishDTO.java` — 修改，新增 `requiredActorType`（默认 SERVICE；FLOW_ACTION 发布器强制 USER）
    - `.../service/impl/CapabilityPublishServiceImpl.java` — 修改，发布/版本快照写入
    - `forge-plugin-capability-flow-actions/.../publish/FlowActionCapabilityPublisher.java` — 修改，发布时固定 requiredActorType=USER
    - 相关 Mapper XML — 修改，查询列补齐
- **关键约束**: 存量数据由 Task 1 迁移脚本回填，代码不做运行时兜底猜测

## Task 5: 开放网关插件 — 认证与编排核心（依赖 Task 2/3/4）
- **目标**: 新插件实现认证解析 + 九步编排服务（先不含 Controller）
- **涉及文件**:
    - `forge-server/forge-framework/forge-plugin-parent/forge-plugin-capability-open-gateway/pom.xml` — 新模块，admin-server 引入
    - `.../gateway/auth/OpenGatewayAuthenticator.java` — 新增，双模式认证：Bearer 委托给 `CapabilityAccessTokenService.authenticate()`；签名模式验签（KEK 解密签名密钥 → HMAC-SHA256 常量时间比较）+ `OpenApiReplayGuard.assertNotReplayed()`，产出 `AuthenticatedCapabilityIdentity`（SERVICE 身份复用 loadCurrentUser 语义加载服务用户）
    - `.../gateway/service/CapabilityInvokeOrchestrator.java` — 新增，编排：能力/grant 解析 → requiredActorType 校验 → 限流 → 幂等（写操作） → input_schema 校验 + field_policy 过滤 → `ExecutionIdentityContextHolder.open(identity)` 内调用 CapabilityExecutor → 统一响应组装 → invocation_log 落库（含拒绝路径）
    - `.../gateway/entity/AiCapabilityOpenapiIdempotency.java` + Mapper + XML — 新增，`@TableLogic(value="0", delval="id")`，查询过滤 logic_delete_active
    - `.../gateway/dto/OpenGatewayResponse.java` — 新增，`{code,message,requestId,timestamp,data}`
- **关键签名**:
  ```java
  public AuthenticatedCapabilityIdentity authenticate(HttpServletRequest request);
  public OpenGatewayResponse invoke(AuthenticatedCapabilityIdentity identity, String capabilityCode,
          String idempotencyKey, Map<String, Object> payload, String requestId);
  ```

## Task 6: 开放网关入口层与配置（依赖 Task 5）
- **目标**: 对外 Controller、白名单、配置组、失败关闭开关
- **涉及文件**:
    - `forge-plugin-capability-open-gateway/.../controller/CapabilityOpenGatewayController.java` — 新增，`POST /openapi/v1/capabilities/{capabilityCode}/invoke`，禁用开关时 404；异常统一转 OpenGatewayResponse 错误码
    - `.../config/OpenGatewayProperties.java` + `OpenGatewayAutoConfiguration.java` — 新增，`forge.capability.open-gateway.*`（enabled 默认 false、timestamp-window、read/write 限流、nonce-ttl、idempotency-ttl）
    - `forge-starter-auth/.../config/SaTokenConfig.java` — 修改，白名单追加 `/openapi/v1/capabilities/**`
    - `forge-admin-server/src/main/resources/application.yml` — 修改，配置组 + 环境变量 `FORGE_CAPABILITY_OPEN_GATEWAY_ENABLED`
- **关键约束**: 白名单只放行路径，鉴权全部在 OpenGatewayAuthenticator 内完成；网关响应不走 RespInfo（对外契约独立）

## Task 7: 幂等快照清理任务（依赖 Task 5）
- **目标**: 超期幂等记录物理清理（留存清理场景，spec 8-4 已说明）
- **涉及文件**:
    - `forge-plugin-capability-open-gateway/.../job/OpenapiIdempotencyCleanJob.java` — 新增，每小时清理 `expires_at < NOW()` 记录
    - Task 1 迁移脚本中同步插入 `sys_job_config` 内置任务（NOT EXISTS 防重复，默认停用）

## Task 8: 控制台前端 — API 层与能力目录/调用日志页
- **目标**: 前端 API 封装 + 只读侧两个页面
- **涉及文件**:
    - `forge-admin-ui/src/api/ai/capability.js` — 新增，catalog/client/grant/invocation 全部接口，轮换密钥用 `postEncrypt`
    - `forge-admin-ui/src/views/ai/capability/catalog/index.vue` — 新增，能力目录（AiCrudPage，占位符 `:id`；来源类型/风险等级/actorType 用 DictTag；schema 查看抽屉）
    - `forge-admin-ui/src/views/ai/capability/invocation/index.vue` — 新增，调用日志（分页查询、结果状态 DictTag、耗时/错误码列，无删除操作）
- **关键约束**: 字典用 `useDict()` + computed schema；分页参数 pageNum/pageSize

## Task 9: 控制台前端 — 机器客户端/授权管理页（依赖 Task 3/8）
- **目标**: 管理侧两个页面
- **涉及文件**:
    - `forge-admin-ui/src/views/ai/capability/client/index.vue` — 新增，客户端 CRUD + 凭据/签名密钥一次性展示弹窗（参考 `job-api-token.vue` 模式）+ 轮换/吊销（text-warning / text-error）
    - `forge-admin-ui/src/views/ai/capability/grant/index.vue` — 新增，授权管理（客户端×能力矩阵、field_policy 编辑、版本策略选择、HIGH 风险禁选提示）
- **关键约束**: 密钥展示脱敏保留前4后4；一次性明文弹窗关闭后不可再取

## Task 10: 测试与端到端验证（依赖全部）
- **目标**: 按 spec 8.5 落地 test-spec 并执行
- **涉及文件**:
    - `code-copilot/changes/unified-capability-open-platform/test-spec.md` — 新增
    - `forge-starter-openapi-security/src/test/java/.../OpenApiReplayGuardTest.java` 等 — 新增，签名/时间窗/nonce/限流/幂等并发单测
    - `forge-plugin-capability-open-gateway/src/test/java/.../CapabilityInvokeOrchestratorTest.java` — 新增，九步编排分支覆盖（含 ACTOR_TYPE_NOT_ALLOWED、RATE_LIMITED、幂等命中）
- **验证清单**: `mvn clean install -DskipTests` 编译通过 → 单测通过 → 启动后 curl 端到端（OAuth 模式建单 + 幂等重试同 Key 返回原结果 + 签名模式验签 + 篡改 body 拒绝 + 重放 nonce 拒绝 + USER 委托 Token 经网关调用 FLOW_ACTION 审批成功、非办理人被 FLOW_TASK_ASSIGNEE_MISMATCH 拒绝、SERVICE 身份调同能力返回 ACTOR_TYPE_NOT_ALLOWED）→ `pnpm lint:fix` 通过 → 控制台 4 页面手工走查
