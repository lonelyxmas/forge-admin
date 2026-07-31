# 企业协同集成（企业微信）

Forge 以 `sys_social_config` 为连接根建设平台无关的企业协同能力层，企业微信（WECOM）为首个完整适配的平台。本文覆盖连接配置、目录同步、消息推送、回调接入与日常运维。

## 能力总览

| 能力 | 说明 | 状态 |
|------|------|------|
| LOGIN | 企业成员 OAuth 扫码登录 | 一期 |
| DIRECTORY | 通讯录（部门/成员/岗位/标签）同步 | 一期 |
| MESSAGE | 站内消息扩展 COLLABORATION 渠道，投递到企微应用消息 | 一期 |
| TODO | Flowable 待办投影为外部平台待办卡片（textcard），支持深链跳转与可配置模板 | 已开放 |
| SCHEDULE | 连接管理页直接配置定时目录同步，自动维护对应定时任务 | 已开放 |

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
6. **配置定时同步（可选）**：在连接编辑页开启「定时目录同步」并填写 Cron 表达式，保存后系统会自动创建/更新该连接专属的定时任务（执行器 `collaborationDirectorySync`，HANDLER 模式，jobParam 为连接ID），无需再进入定时任务模块手工新建；关闭开关或删除连接时对应任务自动删除。

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

`sys_job_config`（COLLABORATION 分组）内置三个全局任务，**默认停用**，完成连接配置并通过连通测试后再手动启用：

| Handler | 用途 | 默认 Cron |
|---------|------|-----------|
| `collaborationDirectorySync` | 每日全量校准目录，修复丢失的增量事件 | `0 0 2 * * ?` |
| `collaborationCallbackRetry` | 重试回调收件箱中失败/待处理事件 | `0 */5 * * * ?` |
| `collaborationDeliveryRetry` | 补偿到期的失败消息投递（参数可传单轮最大条数，默认 100） | `0 2/5 * * * ?` |

### 连接级定时目录同步（推荐）

除上述全局任务外，**每个连接可在连接编辑页单独配置定时目录同步**，这是面向业务用户的推荐入口：

- 连接表 `sys_social_config` 新增 `sync_schedule_enabled`（开关）与 `sync_cron`（Cron 表达式）两列。
- 开启并保存连接后，系统自动维护一条连接专属任务（名称 `collab-dir-sync-{连接ID}`，分组 COLLABORATION，执行器 `collaborationDirectorySync`，HANDLER 模式，jobParam 为连接ID），只同步该连接目录。
- 保存时按连接当前配置幂等对账：需要则新建/更新（修改 Cron 同步更新任务），不需要（关开关或连接停用）则删除任务；删除连接时同步移除任务，无需人工进入定时任务模块。
- 与全局 `collaborationDirectorySync` 互补：全局任务适合统一兜底校准，连接级任务适合按连接差异化的同步频率。

## 待办卡片投递与跳转配置

Flowable 产生待办任务时，`FlowTaskNotifyListener` 会把待办投影为企业协同待办卡片（企微 textcard）推送给办理人，卡片点击后跳转到 H5 详情页办理。

### 跳转地址（深链）如何决定

1. **H5 域名**：来自连接的「待办推送 H5 访问地址」（`sys_social_config.todo_push_h5_url`），必须是合法 http/https，否则该连接整批跳过并告警（避免企微 `TEMPLATE_INVALID`）。
2. **跳转路径模板**：优先取流程模型 `sys_flow_model.todo_detail_url_template`；为空时使用全局默认 `/#/pages/todo-detail?taskId={taskId}`。
3. **占位符渲染**：支持 `{taskId}`、`{businessKey}`、`{processInstanceId}`，渲染时自动做 URL 编码。
4. **相对/绝对**：模板可填相对路径（自动拼接连接 H5 域名），也可直接填完整 `http/https` 地址（此时不再拼域名）。

### 按流程模型自定义跳转（需求4）

在 `流程管理 → 流程模型` 编辑弹窗的「待办跳转」字段配置，例如订单审批跳自己的详情页：

```
/#/pages/order-detail?bizKey={businessKey}
```

留空则回落到全局默认待办详情页，无需改代码即可让不同业务待办跳到各自页面。

## 待办卡片消息模板配置（需求5）

待办卡片的标题与正文不再写死在代码里，改由 `sys_message_template` 驱动，可在 `消息中心 → 模板管理` 编辑：

| 模板编码 | 用途 | 默认状态 |
|----------|------|----------|
| `FLOW_TODO_CARD` | 通用待办卡片（所有平台兜底） | 启用 |
| `FLOW_TODO_CARD_WECOM` | 企业微信差异化文案 | 停用 |
| `FLOW_TODO_CARD_DINGTALK` | 钉钉差异化文案（markdown） | 停用 |
| `FLOW_TODO_CARD_FEISHU` | 飞书差异化文案 | 停用 |

- **平台差异化选择**：监听器按 `FLOW_TODO_CARD_{platform}` → `FLOW_TODO_CARD` 顺序取首个「存在且启用」的模板；都没有则回退代码内置硬编码文案（保证不因误删模板而中断投递）。
- **可用占位符**：`${taskTitle}`（任务标题）、`${processName}`（流程名）、`${startUserName}`（发起人）、`${url}`（跳转地址，钉钉/飞书正文可用）。
- **模板引擎能力**：仅做 `${key}`/`{key}` 字符串替换，不支持条件/循环；企微 textcard 正文仅支持 gray/normal/highlight 三种颜色 `div`。
- 后续对接钉钉/飞书时，把对应模板 `enabled` 置 1 并按平台文案调整即可，无需改代码。

## 开发者指南：其它业务后台对接消息与待办卡片

业务模块统一通过 `MessageService` 发送消息，无需直连各平台 SDK。注入接口后构造 `MessageSendRequestDTO`。

### 发送站内信 / 多渠道消息

```java
@Resource
private MessageService messageService;

MessageSendRequestDTO req = new MessageSendRequestDTO();
req.setType("SYSTEM");            // SYSTEM/SMS/EMAIL/CUSTOM
req.setChannel("WEB");           // WEB/SMS/EMAIL/PUSH/COLLABORATION
req.setSendScope("USERS");        // ALL/ORG/USERS
req.setUserIds(Set.of(1001L));    // sendScope=USERS 时指定接收人
req.setTitle("标题");
req.setContent("正文");           // 或改用 templateCode + params 走模板渲染
messageService.send(req);
```

模板渲染规则：设置 `templateCode` 且模板 `enabled=1` 时，`title`/`content`/`channel` 三者分别在请求对应字段为空时才用模板渲染；想强制走模板就把 `content` 留空，想固定渠道就显式设 `channel`。

### 幂等发送（避免重复推送）

同一业务事件可能触发多次，用 `sendIfAbsent` 按业务类型 + 业务键去重：

```java
req.setBizType("ORDER");
req.setBizKey(orderId);
messageService.sendIfAbsent(req, "ORDER", orderId);
```

### 发送企业协同待办卡片（COLLABORATION 渠道）

要投递到企微/钉钉等外部平台待办卡片，`channel` 设为 `COLLABORATION`，并通过 `params` 传递卡片扩展字段：

```java
req.setType("SYSTEM");
req.setChannel("COLLABORATION");
req.setSendScope("USERS");
req.setUserIds(receiverIds);
req.setConnectionId(connectionId);   // COLLABORATION 渠道必填；为空时自动解析租户唯一可用消息连接

Map<String, Object> params = new HashMap<>();
params.put("msgType", "textcard");   // text / textcard
params.put("url", detailUrl);        // textcard 点击跳转地址（合法 http/https）
params.put("taskTitle", "请假审批"); // 供模板 ${taskTitle} 使用
params.put("processName", "请假流程");
params.put("startUserName", "张三");
req.setParams(params);

// 走可配置模板；无启用模板时回退到显式 title/content
String code = messageService.resolveEnabledTemplateCode("FLOW_TODO_CARD_WECOM", "FLOW_TODO_CARD");
if (code != null) {
    req.setTemplateCode(code);       // content 留空，走模板渲染
} else {
    req.setTitle("您有新的流程待办");
    req.setContent("<div class=\"normal\">任务：请假审批</div>");
}
messageService.send(req);
```

约定与注意：

- `connectionId` 建议显式指定；不传时渠道会解析租户下唯一「启用 + 支持 MESSAGE + 已绑定启用应用」的连接，命中多个取第一个并告警。
- 未绑定外部账号或绑定停用的接收人记为 SKIPPED，不进入重试；`msgType`/`url` 等扩展参数不落库，失败重试统一降级为纯文本。
- 卡片跳转地址请复用「待办卡片投递与跳转配置」的域名 + 模板方案，避免在业务代码里硬编码 H5 地址。
- 投递逐人结果落 `sys_message_receiver`（SENT/FAILED/SKIPPED），可在「投递记录」页查看完整推送内容与接收人账号/姓名。

## 常见问题排查

| 现象 | 排查方向 |
|------|----------|
| 连通测试失败 | 核对 CorpId/AgentId/Secret；确认 Forge 出口 IP 在企微「企业可信 IP」中；查看出站白名单场景 `COLLABORATION_PROVIDER` |
| 回调验签失败 | 核对回调 Token 与 EncodingAESKey；「回调事件」页查看 `signature_status` 与错误摘要 |
| 同步后用户未创建 | 身份匹配策略为 BIND_ONLY 时不会自动建用户，未匹配进入问题单人工绑定 |
| 消息投递 SKIPPED | 用户未绑定该连接外部账号或绑定已停用，先完成目录同步或人工绑定 |
| 投递 FAILED 不再重试 | 超过最大尝试次数（5 次）或命中永久性错误后停止自动重试，需在「投递记录」页人工重试 |
| 迁移脚本未执行 | 检查 `FORGE_FLYWAY_LOCATIONS` / `FORGE_FLYWAY_ENABLED` 环境变量；查询 `forge_schema_history` 确认 V1.0.57~V1.0.61、V1.0.72 已执行 |
| 待办卡片未推送/整批跳过 | 确认连接已配置合法 http/https 的「待办推送 H5 访问地址」；日志搜 `待办H5访问地址不是合法` 定位 |
| 待办卡片跳错页面 | 检查流程模型「待办跳转」模板占位符拼写；确认相对路径与连接 H5 域名拼接后可达 |
| 连接级定时同步未生效 | 确认连接已启用且 `sync_cron` 合法；到定时任务模块查 `collab-dir-sync-{连接ID}` 任务状态；app-server 等不启调度器的服务不维护该任务 |
