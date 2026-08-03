# 执行日志 — 统一能力开放平台产品化改造

## 1. 基线

- 日期：2026-08-01。
- 工作区包含一期开放平台及其它用户未提交差异；本轮不重置、不清理、不提交无关文件。
- 已读取根目录/`code-copilot` 指令、项目记忆、编码规范、流程开发 Skill、前端设计 Skill与自动化测试标准。
- 已复用 `unified-capability-open-platform` 的 Spec、测试规格和执行日志。

## 2. 设计结论

- 采用代码显式注册的系统服务，不提供任意 URL 代理。
- 网关增加通用执行适配器，低代码业务动作/对象流程动作作为兼容适配器保留。
- 首个系统服务为固定已发布模型的流程启动，调用者不可传入模型或身份字段。
- Markdown 为默认人读文档，OpenAPI JSON 保留为机器文档。
- REST OpenAPI resource 与 MCP resource 分离。

## 3. 验证记录

### 3.1 执行环境与范围

- 执行日期：2026-08-02。
- Java：JDK 17，`JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home`。
- 前端：Node `v20.19.0`；生产构建使用 `NODE_OPTIONS=--max-old-space-size=8192`。
- Maven 测试均显式启用 `-Penable-tests`，并核对 Surefire `Tests run` 非 0。
- 验证范围：Control Plane、Identity、Secure Actions、Flow Actions、Open Gateway、MCP 委托身份集成、Admin 聚合装配、Capability 前端页面与 V1.0.77 权限迁移。

### 3.2 编译与定向测试

以下命令均在 `forge-server/` 执行，并使用上述 Java 17 环境：

```bash
mvn -pl :forge-plugin-capability-control-plane,:forge-plugin-capability-identity,:forge-plugin-capability-secure-actions,:forge-plugin-capability-flow-actions,:forge-plugin-capability-open-gateway -am -DskipTests test-compile

mvn -Penable-tests -pl :forge-plugin-capability-control-plane -Dtest=CapabilityOpenApiDocumentServiceTest,CapabilityCallGuideServiceTest test
mvn -Penable-tests -pl :forge-plugin-capability-secure-actions -Dtest=SystemServiceDefinitionRegistryTest,SystemServiceCapabilityPublisherTest,SystemServiceOpenGatewayAdapterTest test
mvn -Penable-tests -pl :forge-plugin-capability-flow-actions -Dtest=FlowProcessStartSystemServiceTest,FlowActionExecutionAdapterTest test
mvn -Penable-tests -pl :forge-plugin-capability-identity -Dtest=CapabilityAccessTokenServiceTest test
mvn -Penable-tests -pl :forge-plugin-capability-open-gateway -Dtest=BusinessActionOpenGatewayAdapterTest,OpenGatewayCapabilityResolverTest,CapabilityInvokeOrchestratorTest,OpenGatewayAuthenticatorTest test

mvn -pl :forge-admin-server -am -DskipTests compile
```

结果：

| 范围 | 结果 | 覆盖结论 |
|---|---|---|
| 关联模块 `test-compile` | ✅ 36/36 Reactor 模块成功 | 主码与新增测试源码均完成编译 |
| Control Plane 定向测试 | ✅ 6/6 | Markdown/OpenAPI 文档、调用指南 readiness 与安全示例 |
| Secure Actions 定向测试 | ✅ 10/10 | 注册表重复/未知定义失败关闭、发布参数白名单、系统服务网关适配 |
| Flow Actions 定向测试 | ✅ 11/11 | 流程固定快照、变量白名单、USER 委托、模型漂移拒绝及既有流程动作兼容 |
| Identity 定向测试 | ✅ 7/7 | OpenAPI/MCP audience 严格隔离 |
| Open Gateway 定向测试 | ✅ 20/20 | 适配器分派、未知来源、Bearer resource 校验和异常映射 |
| 定向测试合计 | ✅ 54/54 | 本轮 P0 核心后端差异全部通过 |
| Admin 聚合编译 | ✅ 47/47 Reactor 模块成功 | 主应用依赖和自动装配可编译 |

MCP 委托身份集成测试另行执行：

```bash
mvn -Penable-tests -pl :forge-starter-crypto,:forge-plugin-capability-identity -am -Dtest=McpDelegatedIdentityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：✅ `McpDelegatedIdentityIntegrationTest` 3/3，通过 USER 委托身份上下文集成回归。

### 3.3 模块全量测试补充说明

执行五个目标插件的全量测试时，Control Plane 为 ✅ 41/41；Identity 共 49 个用例，其中首轮 46 个普通测试通过，3 个 `McpDelegatedIdentityIntegrationTest` 在 Spring Context 初始化阶段失败。失败信息为：

```text
自动密钥文件包含不允许的配置键: forge.capability.identity.authorization-code-pepper
```

根因不是产品逻辑失败，而是该次 Reactor 未包含当前工作区的 `forge-starter-crypto`，测试类路径解析到了本地 Maven 仓库中的旧版 Starter。将 `:forge-starter-crypto` 显式加入同一 Reactor 后，3/3 集成测试通过。首轮命令在 Identity 处停止，后续 Secure Actions、Flow Actions、Open Gateway 全量模块被 Maven 标为 skipped；不将它们记作全量通过，仅采用 3.2 节已经实际通过的定向用例作为本轮证据。

`CapabilityGrantPolicyTest` 的 Surefire 报告显示 `Time elapsed: 925.9 s`，但测试命令实际墙钟仅数秒且 6/6 通过，判定为测试计时元数据异常，不是性能结论。目标测试日志中的 WARN/ERROR 来自故障映射和失败关闭用例的预期分支。

### 3.4 前端与 SQL 静态验证

以下命令在 `forge-admin-ui/`、Node `v20.19.0` 下执行：

```bash
pnpm exec eslint src/views/ai/capability/catalog.vue src/views/ai/capability/grant.vue src/views/ai/capability/components/CapabilityRegisterModal.vue src/views/ai/capability/components/CapabilityCallGuideModal.vue
NODE_OPTIONS=--max-old-space-size=8192 pnpm build
```

结果：

- ✅ 4 个本轮直接相关文件定向 ESLint 零错误。
- ✅ 前端生产构建成功，构建产物包含调用指南和系统服务注册组件。
- ⚠️ 构建保留既有非阻断警告：`UserSelectModal` 组件命名冲突、静态/动态导入导致无法拆分 chunk、CSS 使用 `//` 注释；均不属于本轮功能错误。

V1.0.77 与差异静态检查：

```bash
rg -n '\$\{[^}]+\}' forge-server/db/migration/V1.0.77__add_capability_system_service_permissions.sql
git diff --check
```

结果：✅ V1.0.77 无 Flyway `${...}` 占位符，权限资源使用 `tenant_id=1` 与 `NOT EXISTS` 防重复；`git diff --check` 无空白错误。全迁移目录扫描发现的 `${...}` 仅位于既有 V1.0.72 消息模板文本，与本变更无关。

## 4. 未执行项与环境门禁

- 未连接真实 MySQL/Redis，未实际执行 V1.0.77，也未查询 `forge_schema_history` 或权限资源落库结果。
- 未启动 Admin/Flow 服务，未执行真实 OIDC Token Exchange、OAuth Client Credentials、HMAC 签名、nonce 重放、幂等重试和流程实例落库 E2E。
- 未执行浏览器登录态下的“注册流程启动能力 → 创建客户端 → 授权 → 查看调用指南 → 下载 Markdown/OpenAPI → 复制 curl 调用”完整走查。
- 上述项目依赖用户真实开发环境与已发布流程模型，保留为部署前 P1 门禁，不将静态验证或 Mock 单测替代为通过结论。

## 5. 服务清理

- 本轮未启动任何 Admin、Flow、Vite、MySQL 或 Redis 进程，无本轮 PID 需要停止。
- 工作区原有进程与其它未提交修改均未触碰。

## 6. Capability 文档服务启动失败修复

- 日期：2026-08-02。
- 现象：Admin 创建 `CapabilityOpenApiDocumentService` 时抛出 `No default constructor found`。
- 根因：该 Service 同时存在生产四参数构造器与测试便利三参数构造器；多构造器场景下未显式标记注入构造器，Spring 回退为无参实例化。
- 修复：在生产四参数构造器上显式添加 `@Autowired`，保留必需依赖的构造器注入，不增加可能产生空依赖的无参构造器。
- 回归：新增 `ApplicationContextRunner + @Import` 容器装配测试，真实经过 Spring Bean 创建路径。

| 范围 | 命令 | 结果 |
|---|---|---|
| 失败基线 | `mvn -Penable-tests -pl :forge-plugin-capability-control-plane -Dtest=CapabilityOpenApiDocumentServiceTest test` | 按预期复现：3 个用例中新增容器测试 1 个失败，异常与用户日志一致 |
| 定向复跑 | 同上 | ✅ 3/3，Spring Context 正常创建文档服务 |
| Control Plane 全量 | `mvn -Penable-tests -pl :forge-plugin-capability-control-plane test` | ✅ 42/42，Failures 0、Errors 0、Skipped 0 |
| Admin 聚合编译 | `mvn -pl :forge-admin-server -am -DskipTests compile` | ✅ 47/47 Reactor 模块，`BUILD SUCCESS`；仅保留既有 deprecated/unchecked 编译警告 |

- 本次没有启动 Admin；需要重启现有 Admin 进程后加载修复类。

## 7. Open Gateway 缺少 Token Service 装配修复

- 日期：2026-08-02。
- 现象：Admin 创建 `OpenGatewayAuthenticator` 时找不到 `CapabilityAccessTokenService`。
- 根因：Open Gateway 与 Identity 使用两个独立的自动配置条件；存在 `open-gateway.enabled=true`、`identity.enabled=false` 的半开启组合，网关已创建但令牌服务未装配。
- 修复：Identity 自动配置改为“Identity 开启或 Open Gateway 开启”任一满足即生效；Open Gateway 声明在 Identity 之后装配。应用配置恢复安全默认关闭网关，显式开启网关时自动带起 Identity。
- 一致性：调用指南用 `identityEnabled || gatewayEnabled` 计算有效 Identity 状态，避免运行态可用但页面误报 OAuth 不可用。

验证命令均在 `forge-server/` 下使用 JDK 17 执行：

```bash
mvn -Penable-tests -pl :forge-plugin-capability-identity -Dtest=CapabilityIdentityAutoConfigurationTest test
mvn -Penable-tests -pl :forge-plugin-capability-open-gateway -Dtest=OpenGatewayAutoConfigurationTest test
mvn -Penable-tests -pl :forge-plugin-capability-control-plane -Dtest=CapabilityCallGuideServiceTest test
mvn -Penable-tests -pl :forge-plugin-capability-control-plane,:forge-plugin-capability-identity,:forge-plugin-capability-open-gateway test
mvn -pl :forge-admin-server -am -DskipTests compile
git diff --check
```

| 范围 | 结果 |
|---|---|
| Identity 失败基线 | 新增网关强制 Identity 用例按预期失败：2 个用例中 1 个找不到 `CapabilityAccessTokenService` |
| Identity 定向复跑 | ✅ 2/2，网关开启且 Identity 显式关闭时仍提供 Token Service |
| Open Gateway 联合装配 | ✅ 1/1，同时创建 Token Service、认证器和调用编排器 |
| 调用指南失败基线 | 新增有效 Identity 状态用例按预期失败：5 个用例中 1 个 `ready=false` |
| 调用指南定向复跑 | ✅ 5/5，OAuth 状态及示例与真实运行态一致 |
| 三模块全量测试 | ✅ Control Plane 43/43、Identity 50/50、Open Gateway 21/21，共 114/114 |
| Admin 聚合编译 | ✅ 47/47 Reactor 模块，`BUILD SUCCESS` |
| 差异静态检查 | ✅ `git diff --check` 无空白错误 |

- Identity 测试中的数据库不可用 ERROR、MCP 认证 WARN，以及 Open Gateway 的 `INTERNAL_ERROR` WARN，均为既有故障映射测试的预期分支，不是测试失败。
- 本轮未启动或重启 Admin、Flow、MySQL、Redis；修改生效前需要重启现有 Admin 进程。

## 8. 授权空参数与字典英文展示修复

- 日期：2026-08-02。
- 现象一：新增能力授权时后端返回“不能为null；不能为空；不能为空；不能为null”，日志显示四个必填字段均为空。
- 根因一：页面运行期间后端加密配置发生变化；浏览器仍按旧配置发送 `{data, algorithm}` 加密信封，而当前后端已关闭 API 解密，Spring 因此只能绑定出空业务 DTO。
- 修复一：所有 `postEncrypt` 显式敏感请求在提交前重新读取 `/crypto/config`。服务端关闭加密时发送普通 DTO；开启时继续强制密钥协商与加密；配置无法确认时失败关闭。授权 DTO 同时补充字段级中文校验消息。
- 现象二：能力目录和授权流程操作偶发显示英文枚举，来源类型与行为类型始终没有中文字典。
- 根因二：`useDict` 将请求失败返回的空数组写入全局缓存，单次瞬时失败会持续到 SPA 整体刷新；同时数据库缺少来源类型、行为类型两个字典。
- 修复二：字典改为 `Promise.allSettled` 逐项处理，仅缓存成功结果，失败项自动重试一次且不覆盖已成功数据；授权弹窗支持刷新字典并在流程操作未翻译时禁止提交。新增 V1.0.78 字典迁移，能力目录表格/详情和系统服务摘要统一读取字典。
- 变更范围：前端 HTTP 加密拦截器、公共 `useDict`、能力目录/授权/注册组件、授权 DTO、V1.0.78 迁移及本变更文档。
- 验证状态：按用户明确要求，本轮未执行 Maven、前端 ESLint/build、Flyway、服务启动、接口调用或浏览器测试；所有结果保持“待用户验证”，未表述为通过。
- 服务清理：本轮未启动、停止或重启任何服务，未触碰用户现有 Admin 进程。

## 9. OAuth Token 404、能力重新启用与在线测试

- 日期：2026-08-02。
- OAuth 404 根因：Open Gateway 开启时 Identity 自动配置会提供 `CapabilityAccessTokenService`，但 Token Controller 等公开组件仍只判断 `identity.enabled=true`，形成“服务已装配、路由未注册”的半开启状态。
- OAuth 修复：新增统一 `CapabilityIdentityRequiredCondition`；Identity 或 Open Gateway 任一开启时，自动配置、Token/OAuth 路由、UserInfo、MCP 身份上下文、调用审计和 Token 清理任务采用同一装配语义。
- 能力恢复：新增 `/ai/capability/enable/{id}`，重新启用前校验当前版本存在且为 `PUBLISHED`；前端停用状态显示绿色“启用”操作并二次确认。
- 在线测试：调用指南使用浏览器临时凭据依次调用真实 Token/开放网关；支持 OAuth Client Credentials、OIDC Token Exchange 和 HMAC-SHA256。副作用能力二次确认并自动携带一次性幂等键。
- 接入材料：新增 OAuth/HMAC Java 17 标准库示例、客户端级 Markdown 接入示例和最近一次测试 JSON 报文下载；凭据、Token 与签名在页面展示及下载时统一脱敏。
- 验证状态：按用户明确要求，本轮未执行 Maven、前端 ESLint/build、Flyway、服务启动、接口调用或浏览器测试；所有结果保持“待用户验证”，未表述为通过。
- 服务清理：本轮未启动、停止或重启任何服务。

## 10. 无统一 OIDC 的客户端签名用户断言

- 日期：2026-08-02。
- 身份协议：为 USER_DELEGATION/HYBRID OAuth 客户端增加独立 RSA-2048 密钥；外围系统使用最长两分钟的 RS256 JWT 和专用 `subject_token_type` 做 Token Exchange。
- 可信映射：管理员预绑定外围 `sub` 到 Forge 普通用户；原始 `sub` 不落库，只保存 SHA-256 与脱敏提示。运行时禁止管理员身份，并实时加载用户状态、租户、组织和角色。
- 安全校验：固定校验 `alg/kid/iss/aud/client_id/sub/iat/exp/jti`，使用 Redis 防重放；验签、防重放或目录基础设施不可用时失败关闭，OIDC 与客户端断言不得回退。
- 密钥治理：Forge 只保存 X.509 公钥、`kid` 和版本；PKCS#8 私钥通过加密响应只展示一次。轮换和停用同步递增客户端 `credential_version`，撤销旧客户端 Token。
- 管理端：客户端页面增加“用户断言”入口、密钥下载、协议参数、用户选择和脱敏映射列表；停用后可再次轮换生成新密钥。
- 调用闭环：调用指南默认生成专用 Token Exchange curl，增加完整 Java 17 RS256 示例；在线测试可选择 OIDC 或客户端断言，并用浏览器 Web Crypto 临时签名。
- 数据迁移：新增 `V1.0.79__add_capability_client_user_assertion.sql`，全部新增字段使用 `information_schema` 防重复，未写入任何密钥或真实用户标识。
- 验证状态：按用户明确要求，本轮未执行 Maven、前端 ESLint/build、Flyway、服务启动、接口调用或浏览器测试；所有结果保持“待用户验证”，未表述为通过。
- 服务清理：本轮未启动、停止或重启任何服务，未触碰用户现有 Admin/Flow/Vite 进程。

## 11. Capability Token 误报与执行适配器现场修复

- 日期：2026-08-02。
- 日志结论：`/oauth2/token` 未登录属于 OAuth 公开端点的正常状态；`fdu_` 是 Capability 短期 Token，不是 Sa-Token。通用操作日志切面在网关控制器执行前尝试读取 Sa-Token 用户，造成“token 无效”堆栈，但真实网关认证随后已成功。
- 协议隔离：租户拦截器直接跳过 Capability OAuth/OpenAPI 协议入口的 Sa-Token 解析；通用操作日志默认且硬性排除 `/oauth2/token`、`/oauth2/revoke` 与能力开放网关路径，凭据和业务报文继续由专用安全审计治理。
- 适配器修复：Open Gateway 自动配置显式等待 Secure Action/Flow Action 配置，并强制注入业务动作、系统服务基础适配器；缺失来源错误增加 `sourceType/behavior` 提示。
- 易用性：调用指南增加“执行能力”阻断项。`BUSINESS_ACTION/ACTION`、`SYSTEM_SERVICE/ACTION` 和已开启的 `FLOW_ACTION/FLOW` 可进入测试；旧的未知来源或关闭的流程执行器会提前给出中文修复建议。
- 用户映射：客户端签名用户断言仍默认要求管理员预绑定，但每个外围 `sub` 只需绑定一次，后续所有 Token Exchange 复用。该边界用于阻止客户端任意指定 Forge 用户；受信 OIDC 模式仍可按已验证手机号完成首次自动映射。
- 验证状态：按用户明确要求，本轮未执行 Maven、前端 ESLint/build、服务启动或接口测试；`git diff --check` 无新增空白错误，目标文件未发现冲突标记，运行结果待用户验证。
