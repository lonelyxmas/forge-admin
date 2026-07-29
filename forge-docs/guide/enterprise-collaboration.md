# 企业协同集成（企业微信）

Forge 以 `sys_social_config` 为连接根建设平台无关的企业协同能力层，企业微信（WECOM）为首个完整适配的平台。本文覆盖连接配置、目录同步、消息推送、回调接入与日常运维。

## 能力总览

| 能力 | 说明 | 状态 |
|------|------|------|
| LOGIN | 企业成员 OAuth 扫码登录 | 一期 |
| DIRECTORY | 通讯录（部门/成员/岗位/标签）同步 | 一期 |
| MESSAGE | 站内消息扩展 COLLABORATION 渠道，投递到企微应用消息 | 一期 |
| TODO | Flowable 待办投影到外部平台 | 二期（暂未开放） |

核心模型：

- **连接（Connection）**：`sys_social_config`，对应一个外部企业（企微 CorpId），承载平台类型、身份匹配策略、目录权威来源等连接级配置。
- **物理应用（App）**：`sys_social_app_config`，连接下的企微自建应用，持有 AgentId 与 Secret（AES-GCM 加密落库）。
- **能力绑定（Capability Binding）**：`sys_social_capability_binding`，每连接每能力最多绑定一个启用中的物理应用；LOGIN/DIRECTORY/MESSAGE 可绑定不同应用。

## 企业微信侧准备

1. 企微管理后台创建（或复用）**自建应用**，记录 `CorpId`、`AgentId`、`Secret`。
2. 若需要通讯录同步，在「管理工具 → 通讯录同步」开启 API 同步并记录**通讯录 Secret**（可与应用 Secret 分开配置为两个物理应用）。
3. 若需要接收回调（通讯录变更事件），配置回调 URL、`Token` 与 `EncodingAESKey`，回调地址指向：
   `POST https://<forge-domain>/collaboration/callback/wecom/{connectionCode}`
4. 在应用「企业可信 IP」中加入 Forge 服务器出口 IP。

## Forge 侧配置流程

菜单入口：`系统管理 → 企业协同`（默认仅授予超级管理员，需要时按角色分配权限）。

1. **新建连接**：平台选择企业微信，填写连接编码、企业 CorpId、身份匹配策略（建议 `BIND_ONLY` 起步）、目录权威来源（企微为权威源选 `EXTERNAL`）与默认挂载组织。连接接口不接收任何凭据字段。
2. **新建物理应用**：在连接详情中添加应用，填写 AgentId 与 Secret；Secret 提交后服务端 AES-GCM 加密存储，回显只展示「已配置 + 固定掩码」。修改时留空或回传掩码表示保留现值；轮换支持 CAS 并发控制。
3. **绑定能力**：将 LOGIN/DIRECTORY/MESSAGE 各自绑定到对应物理应用。
4. **连通测试**：点击「连通测试」按能力验证凭据（DIRECTORY 走通讯录 Token，其余走应用 Token），接口不回显任何 Token。
5. **触发同步**：目录权威来源为 EXTERNAL 时，可手工触发全量同步；同步进度与结果在「同步批次」页查看。

> 安全红线：Secret/Token/AESKey 只在写入时提交明文，所有读接口一律脱敏；操作日志不记录含凭据的请求体；禁止把测试企业凭据写入代码或 SQL。

## 目录同步

- **全量同步**：手工触发或由 Job `collaborationDirectorySync`（默认每日 02:00，初始停用）执行；快照校验 + 差量写入，未出现在成功批次中的映射自动停用。
- **增量事件**：企微通讯录变更回调进入收件箱 `social_callback_event`，验签解密后异步处理；失败按指数退避重试，超限置为 DISCARDED。
- **问题单**：身份匹配失败、数据冲突等进入 `social_sync_issue`，在「问题单」页人工处理（BIND 绑定到已有用户 / IGNORE 忽略 / RETRY 待下轮同步重试）。
- **映射查询**：「映射查询」页支持按连接检索部门/用户/岗位/标签映射，用户绑定视图不含 access/refresh token。

## 消息推送

- 站内消息 `send_channel = COLLABORATION` 时经 `MessageChannel` SPI 投递到企微应用消息，支持 text 与模板卡片（textcard）。
- 逐人投递状态记录在 `sys_message_receiver`（SENT/FAILED/SKIPPED），可在「投递记录」页查询。
- **失败补偿**：Job `collaborationDeliveryRetry`（默认每 5 分钟，初始停用）扫描到期失败投递自动重发；也可在「投递记录」页手工重试。
- **重试限制**：渠道扩展参数（msgType/url）不落库，重试统一按 text 消息重发，textcard 消息重试会降级为纯文本。
- 未绑定外部账号或绑定停用的用户明确记为 SKIPPED，不进入重试。

## 定时任务

`sys_job_config`（COLLABORATION 分组）内置三个任务，**默认停用**，完成连接配置并通过连通测试后再手动启用：

| Handler | 用途 | 默认 Cron |
|---------|------|-----------|
| `collaborationDirectorySync` | 每日全量校准目录，修复丢失的增量事件 | `0 0 2 * * ?` |
| `collaborationCallbackRetry` | 重试回调收件箱中失败/待处理事件 | `0 */5 * * * ?` |
| `collaborationDeliveryRetry` | 补偿到期的失败消息投递（参数可传单轮最大条数，默认 100） | `0 2/5 * * * ?` |

## 常见问题排查

| 现象 | 排查方向 |
|------|----------|
| 连通测试失败 | 核对 CorpId/AgentId/Secret；确认 Forge 出口 IP 在企微「企业可信 IP」中；查看出站白名单场景 `COLLABORATION_PROVIDER` |
| 回调验签失败 | 核对回调 Token 与 EncodingAESKey；「回调事件」页查看 `signature_status` 与错误摘要 |
| 同步后用户未创建 | 身份匹配策略为 BIND_ONLY 时不会自动建用户，未匹配进入问题单人工绑定 |
| 消息投递 SKIPPED | 用户未绑定该连接外部账号或绑定已停用，先完成目录同步或人工绑定 |
| 投递 FAILED 不再重试 | 超过最大尝试次数（5 次）或命中永久性错误后停止自动重试，需在「投递记录」页人工重试 |
| 迁移脚本未执行 | 检查 `FORGE_FLYWAY_LOCATIONS` / `FORGE_FLYWAY_ENABLED` 环境变量；查询 `forge_schema_history` 确认 V1.0.57~V1.0.61 已执行 |
