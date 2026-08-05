# Forge Admin AI 功能升级 · 总设计

> 日期：2026-08-04
> 状态：已确认
> 参考项目：snail-ai、snail-ai-admin、agentscope-java、54doctor_ai、ag-ui、internet-hospital-web-vue3

## 一、目标

将 Forge Admin 的 AI 能力从"多供应商 + 单轮对话 + AI 代码生成"升级为企业级 AI Agent 平台：

1. **RAG 知识库**——让 AI 检索企业内部文档，回答基于私有数据的问题
2. **Agent 执行引擎**——从单轮对话升级为"推理 + 工具调用"迭代的智能体
3. **多模态**——Vision / 图片生成 / ASR / TTS
4. **技能系统**——结构化技能包 + 技能 IDE + AI 生成/优化
5. **对话体验**——用户级对话列表 + 中间状态展示 + 流式交互

## 二、现状评估（代码调研结论）

### 已有基础（保留复用）
- **供应商适配**：`AiProviderAdapterRegistry`，`openai_compatible` / `dashscope_native` 两种协议
- **模型路由**：`PolicyBasedAiModelRouter`，PINNED / POLICY 两种模式
- **熔断**：`InMemoryAiModelHealthRegistry`，5 状态健康注册表
- **调用治理**：`AiModelInvocationRecorder`，全量审计（token/成本/延迟）
- **会话记忆**：`DbChatMemory`，DB 持久化，20 条窗口
- **流式输出**：SSE，现有 `AiClientImpl.stream()` 已用 Reactor `Flux`
- **提示词模板**：`AiPromptTemplateRenderer`，`{{变量}}` 渲染
- **上下文注入**：`ContextInjector`，RULE/SPEC 上下文

### 缺失能力（本次建设）
- ❌ RAG / 知识库 / 向量检索（`model_type` 有 embedding 枚举但无实现）
- ❌ Embedding / Rerank 模型支持（仅 Chat 可用）
- ❌ Agent 工具调用循环（LLM 返回 `tool_call` 未被处理）
- ❌ HITL 人机协同
- ❌ 技能系统
- ❌ 多模态实现
- ❌ AI 创建 Agent

### 安全隐患（本次修复）
- ⚠️ API Key **明文存储**（`AiProviderService.requireSecret()` 仅 trim，无加密）
- 修复方案：复用 `PersistentCryptoService` 落库密文，存量数据迁移

## 二点五、前置升级（Phase 0）

> 四期方案的技术栈均基于 **Spring AI 2.0.0**（当前仓库为 1.1.2）。为隔离升级风险，先做独立的 Phase 0 迁移：把现有 `AiClientImpl`/`ChatClientCache`/两个 Provider 适配器/`forge-plugin-mcp` 的代码与测试全部迁移到 Spring AI 2.0.0 API，跑通全量测试后冻结。一期至四期的新代码直接写在 2.0.0 API 上，存量测试作为升级回归门。Phase 0 纯代码/依赖升级，无 DDL，无 Flyway 版本。

## 三、核心架构决策

| 维度 | 决策 | 理由 |
|------|------|------|
| 向量存储 | 多后端工厂模式，首期 Milvus（**直连 milvus-sdk**，不用 Spring AI MilvusVectorStore 组件） | 直连 SDK 才能管理 collection 的 sparse 向量/BM25 全文本索引（三期混合检索依赖）；且与一期 adapter `embed()→List<List<Float>>` 契约一致 |
| LLM 调用 | Spring AI `ChatModel` 底层，ReAct 循环自控 | 复用 Provider 适配器，不依赖 Spring AI 工具循环 |
| 响应式 | 引擎内部 Reactor，对外 **WebFlux `Flux<ServerSentEvent>`**（与现有 `AiClientController` 一致） | 复用现有 WebFlux 流式通道，二期进度/三期事件/四期 AI 创建统一走它 |
| 事件协议 | 自研（参考 AgentScope 28 种），不采用 AG-UI | 内部灵活；未来对接 AG-UI 加一层转换 |
| 对话存储 | **两层分层**：事件流单表 `ai_agent_event`（`session_id + turn_index + event_type`，纯审计回放）+ **消息层复用现有 `ai_chat_session`/`ai_chat_record`**（会话列表/消息回放/多轮记忆） | 事件流供完整回放，消息层供会话 UI；复用现有 `AiChatSessionService`/`AiChatRecordService`/`DbChatMemory`，零新表 |
| HITL | 新权限引擎（Agent 级 ALLOW/ASK/DENY，未配置默认 ALLOW + 风险关键词 ASK 兜底），中断-恢复式 | 生产安全；**中断状态纯 Redis（`ai_agent_interrupt` 不建表），TTL 10 分钟** |
| API Key | 复用 `PersistentCryptoService`，**legacy 无前缀密文**（跟随全局 `write-versioned=false`） | 与能力平台/data/social/低代码一致的现成加密体系；`decrypt` 兼容 legacy 与 versioned，无前缀判断，密钥轮换统一开启 versioned 时再支持 |
| 逻辑删除 | 新表统一 `del_flag bigint` + `@TableLogic(value="0", delval="id")` | 与现有 `ai_model`/`ai_agent` 完全一致，唯一键直接建在 `del_flag` 上 |
| 工具源 | `forge-plugin-ai` 定义 `AgentToolContributor` SPI，`mcp`/`capability` 各自实现 | 解耦可选能力，`ai` 不反向依赖 mcp/capability 插件 |
| 技能脚本 | 只读不执行（`ReadSkillTool` 从 `ai_skill_file` 表读内容给 LLM），不落盘、不沙箱 | 仓库无命令执行沙箱；执行任意脚本需另立安全专项 |
| 模型测试 | `AiModelConnectionTestService` 按 `modelType` 路由（chat 回 OK / embedding 验维度 / rerank 验分数） | 前端模型管理页的按类型连接测试有后端支撑 |
| 文档去重 | Service 层按 `dedup_strategy` 查重（name 比对 `knowledge_id+doc_name`，content 比对 `content_hash`），按 `dedup_action` 处理；DB 唯一键兜底 | 能覆盖 name/content/name_or_content 三策略；`ai_knowledge_chunk` 各自存向量，无共享向量引用计数（`ref_count` 恒 1） |
| 两步上传 | 按知识库 `upload_confirm` 分流：0 直接入库，1 先预览（解析+分块不向量化）确认后再入库 | 大文档/高价值文档先预览再向量化，避免误入库 |
| 语音模型源 | 对话语音通道由 **Agent 显式绑定** ASR/TTS 模型（`asr_model_id`/`tts_model_id` 可选） | 多 Agent 各配各的语音模型 |
| 检索演进 | 三期 `RagSearchPipeline` **保留复用**二期 `RagSearchService`（纯向量实现，pipeline 内 `VectorSearchHandler` 调用），不删除 | 二期刚建的调用方（Forced/QA/Controller）接口兼容，回归面小 |

### 评审确认的补充决策（2026-08-04）

> 以下决策在上表之外，由评审逐项确认后固化：

- **存量 model_type 迁移**：`image`→`image_generation`、`audio`→`asr`（枚举 `fromCode` 兼容映射 + 迁移脚本 UPDATE）。`AiModelType` 枚举保持 6 类。
- **适配器匹配**：一期 `AiModelAdapterRegistry` 纯 modelKey 前缀匹配；provider→baseUrl/apiKey 解密闭环放二期（`VectorStoreFactory`）。
- **前端模型页**：增量改造现有 `provider.vue`/`model.vue`/`provider-model.vue`，不重构。
- **模型类型测试**：`AiModelConnectionTestService` 一期就按 `modelType` 路由（见决策表"模型测试"）。
- **SSE 通道**：二期进度 / 三期事件 / 四期 AI 创建统一沿用现有 WebFlux `Flux<ServerSentEvent>`，不用 MVC `SseEmitter`。
- **异步线程池**：`forge-plugin-ai` 自带 `AiAsyncConfig`（`@EnableAsync` + `aiDocProcessExecutor`），不依赖其他模块。
- **分块器**：Spring AI 2.0.0 自带 `TokenTextSplitter`，不引 LangChain4j。
- **依赖注入**：`forge-plugin-ai` 新增 `forge-starter-crypto`（一期）与 `forge-starter-file`（二期）。
- **会话/中断/语音**：不新建会话表（复用 `ai_chat_session`/`ai_chat_record`）、不建 `ai_agent_interrupt`（纯 Redis）、不建 `ai_voice_session`。
- **QA 链路**：二期简化链路（纯向量检索 + 拼 `<documents>` + 单轮 ChatModel），三期引擎上线后切换。
- **Agent 列收拢**：`ai_agent` 全部新增列放 V1.0.88；二期 Agent 绑定 Forced 逻辑依赖三期列，二期不实现。
- **AI 创建 Agent**：推荐绑定仅展示供用户勾选（不自动绑定）；生成阶段用系统默认 Chat 模型。
- **连接测试/事件保留**：三期新增 `AiAgentEventRetentionJob`（参照 `AiInvocationLogRetentionJob`）清理 `ai_agent_event` 流水。

## 四、分期计划

| 期 | 主题 | 内容 |
|----|------|------|
| **Phase 0** | Spring AI 2.0.0 升级 | 存量 Chat/适配器/mcp 代码与测试迁移到 2.0.0 API，全量回归冻结 |
| **一期** | 模型管理升级 | 模型类型细分 6 类、全类型模型管理、API Key 加密（legacy 密文）、存量 `image`/`audio` 类型迁移、模型管理页增量改造 |
| **二期** | RAG 知识库 | 存储实例管理、知识库 CRUD、文档处理流水线（含两步上传分流）、**向量检索**、前端知识库页（BM25/混合检索/Rerank 顺延三期） |
| **三期** | Agent 执行引擎 | ReAct 循环、工具调用（MCP/内置/Capability 经 SPI 桥接）、事件流（消息复用 `ai_chat_session`/`ai_chat_record`）、HITL（纯 Redis 中断）、技能系统（只读）、对话 UI 升级、Vision、**RAG 混合检索增强（BM25/融合/Rerank）** |
| **四期** | 多模态 + AI 创建 | 图片生成、ASR/TTS（Agent 显式绑定模型）、AI 创建 Agent（流式逐字段 + 智能推荐绑定） |

### 依赖关系
```
Phase0(Spring AI 2.0.0) → 一期(模型) → 二期(RAG) → 三期(Agent引擎) → 四期(多模态/AI创建)
                               ↘ 三期的 Agent 引擎给 RAG Smart 模式提供工具调用
                               ↘ 三期的混合检索增强（BM25/融合/Rerank）依赖二期基础检索
```

## 五、数据库迁移策略

遵循现有 Flyway 规范：
- 版本 > 1.0.0 且单调递增
- 每期一个迁移脚本：`V1.0.86`（模型）、`V1.0.87`（RAG）、`V1.0.88`（Agent引擎+技能）、`V1.0.89`（多模态+AI创建）；Phase 0 无 DDL
- `ai_agent` 新增列（`knowledge_ids`/`rag_mode`/`greeting`/`preset_questions`/`max_iters`/`tool_group_mode`）**统一收拢到 V1.0.88**，V1.0.87 只建 RAG 表 + 字典 + 菜单
- **不新建会话/中断/语音会话表**：会话消息复用现有 `ai_chat_session`/`ai_chat_record`；HITL 中断状态纯 Redis；`ai_voice_session` 不建（语音转文本后进 `ai_chat_record`）
- SQL 必须幂等：`CREATE TABLE IF NOT EXISTS`、`INSERT ... SELECT ... WHERE NOT EXISTS`
- 菜单 `sys_resource` 用 `NOT EXISTS` 防重复，`tenant_id = 1`
- 业务数据 `tenant_id = 1`
- 新表统一 `del_flag bigint NOT NULL DEFAULT 0`（无 `logic_delete_active` 生成列，唯一键直接建在 `del_flag` 上）

## 六、遵循的现有规范

- 查询 SQL 写 Mapper XML（`DataScopeInterceptor` 按 MappedStatement id 改写）
- 业务表必备字段：`id` / `tenant_id` / `create_by` / `create_time` / `create_dept` / `update_by` / `update_time`
- 逻辑删除默认：`del_flag`（0 = 未删除），数值主键表用 `@TableLogic(value = "0", delval = "id")`
- 字典不硬编码：`useDict` / `DictSelect` / `DictTag`，内置字典通过 Flyway 写入
- 分页参数：`pageNum` / `pageSize`
- 图片渲染：`AuthImage`（按 fileId），下载用 `getFileUrl(fileId)`
- API Key 脱敏：保留前 4 后 4，中间 `****`
- 禁止 Service 互相注入，跨 Service 协调上提 Controller
- 禁止硬编码密钥 / AK / SK / 数据库密码；日志禁止打印手机号、身份证、银行卡

## 七、多项目亮点吸收

| 参考项目 | 吸收的亮点 |
|----------|-----------|
| **snail-ai** | RAG 全链路（解析→分块→双写→混合检索→融合→Rerank→Lost-in-Middle）、RAG 双调用模式、向量存储工厂、Embedding/Rerank 独立模型、文档去重、两步上传 |
| **agentscope-java** | ReAct 循环、28 种事件模型、洋葱中间件、权限引擎（ALLOW/ASK/DENY）、HITL 中断-恢复、分层记忆、技能自学习、优雅停机 |
| **54doctor_ai** | 三层记忆、动态 HarnessAgent、多策略检索融合、意图状态机、RAG 评估体系（首期不做）、Geodesic Rerank、领域提示词工程 |
| **snail-ai-admin** | Agent 详情页（左配置+右预览）、AI 创建 Agent 向导、模型管理页、技能 IDE、RAG 搜索调试、思考/工具/计划状态展示 |
| **ag-ui** | 事件驱动的 Agent-用户交互协议（仅参考事件类型，不采用协议） |
| **internet-hospital-web-vue3** | 消息气泡五段式布局、专用工具确认框、计划状态进度条、思考轮次数组、流式 TTS、语音输入、调试面板 |

## 八、关键风险

| 风险 | 缓解 |
|------|------|
| Milvus 部署成本 | 工厂模式预留 PgVector/ES 后端，Milvus 不可用时降级；首期直连 milvus-sdk，延迟连接（构建客户端不实际连接），CI/单测无 Milvus 可通过 |
| ReAct 无限循环 | Agent 级 `maxIters` 配置，超限自动摘要 |
| 长连接资源占用 | HITL 用中断-恢复式，不挂长连接 |
| Spring AI 2.0.0 升级破坏存量 | 独立 Phase 0 迁移，存量测试作回归门，升级冻结后再进入一期 |
| 大文档向量化耗时 | 异步处理 + WebFlux SSE 进度；两步上传先预览 |
| 逻辑删除写法不一致 | 新表统一跟随现有 AI 表（`del_flag bigint` + 数值主键回填），废弃生成列 |
