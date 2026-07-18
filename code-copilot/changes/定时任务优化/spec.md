# 定时任务调度中心优化
> status: propose
> created: 2026-07-17
> complexity: 🔴复杂
> change: `定时任务优化`
> source: `MES定时任务模块_需求文档.html` v1.4
> recommended-first-apply: Phase 0 + Phase 1

## 1. 背景与目标

Forge 已有基于 Quartz JDBC 的系统定时任务模块，具备任务配置、启停、立即执行、Bean/Handler/RPC 路由和基础执行日志能力。现有页面主要面向平台超级管理员和开发人员，调度类型只有 Cron，Cron 配置依赖手工输入或硬编码快捷项，任务执行来源、下一次触发、一次性任务、任务时区、并发策略、可配置 Misfire、有效告警、开放 API 和流程编排均不完整。

参考产品需求文档提出的 MES 场景，本变更不新建一套与 Forge 平行的 `mes_job` 调度中心，而是在现有 `forge-plugin-job`、Quartz、消息中心、幂等 starter 和 Flowable 能力上，将系统定时任务升级为平台级通用调度中心。

完成总体变更后应达到以下可验证结果：

- 现有任务配置可无损迁移，既有 `BEAN/HANDLER/RPC` 任务继续按原 Cron 和启停状态运行。
- 配置数据库与 Quartz 运行态之间具备可观测、可重试的同步机制，服务重启或 Quartz 任务缺失后可按数据库期望状态恢复。
- 周期任务支持简单向导和专家模式，提供服务端权威 Cron 校验、中文描述和未来 5 次触发预览。
- 一次性任务使用独立触发时间，到点只执行一次并进入“已结束”，不伪装成每天执行的 Cron。
- 每个任务可以使用独立 IANA 时区，Cron 预览、下一次触发和 Quartz 实际执行结果一致。
- 执行记录能够区分计划、手动和开放 API 触发，覆盖运行中、成功、失败和跳过状态，并关联任务、流程实例和幂等键。
- 并发、Misfire、失败重试、告警、操作审计和批量治理具备明确、可测试的业务规则。
- 外部系统只能通过独立开放 API 和最小 Scope Token 触发或查询任务，不能直接访问内部 Handler 执行端点。
- 技术任务流程复用现有 Flowable 模型和真实流程设计器，仅允许绑定已发布版本，不创建第二套 `mes_flow/mes_flow_node` 设计与执行体系。
- Webhook 和 API 调用节点统一经过出站地址策略、超时、响应大小和重试上限治理，默认阻断 SSRF。

## 2. 代码现状（Research Findings）

### 2.1 当前任务控制面

- `forge-admin-ui/src/views/system/job-config.vue` 使用 `AiCrudPage` 调用 `/job/config/page`、`/job/config/:id`、新增、修改和删除接口，行操作已经具备启动、停止、运行一次、运行日志和删除。
- `job-config.vue#commonCronList` 在页面中硬编码常用 Cron；当前“选择常用”只负责把固定表达式填入输入框，不支持频率向导、双向反解析、时区、中文描述或未来触发预览。
- `job-config.vue#editSchema` 已维护 Bean、Handler、Cron、失败重试次数、告警邮箱和 Webhook 字段，但表单仍是一个通用 CRUD 弹窗，复杂调度配置继续堆入该弹窗会降低可维护性和可用性。
- `forge-admin-ui/src/views/system/job-log-list.vue#loadLogList` 会提交 `startTime/endTime`，但后端实体和查询没有对应筛选条件，因此当前时间范围筛选没有形成完整契约；页面也未提供导出和执行来源字段。
- 当前字典已包含 `sys_job_status` 和 `sys_job_run_mode`，但没有调度类型、触发来源、并发策略、Misfire、执行状态、告警渠道等本次需要的通用字典。

### 2.2 当前管理接口与权限

- `JobConfigController` 提供任务分页、详情、新增、修改、删除、启停、立即执行和单独更新 Cron 的 REST 接口。
- `JobLogController` 提供日志分页、详情和按保留天数物理清理接口。
- 两个 Controller 均使用 `@ApiPermissionIgnore`，再通过 `SessionHelper.assertAdmin` 限制超级管理员，无法表达 MES 运维、业务配置员、集成开发者和审计员的细粒度权限。
- Controller 直接接收和返回 `SysJobConfig/SysJobLog` 实体，没有独立 DTO/VO、字段级校验、敏感配置裁剪或乐观并发控制。
- `JobExecutorEndpoint#/job/executor/execute` 接收调用方传入的 Handler 名并从 Spring 容器取 Bean 执行。该接口是内部执行器协议，不能复用为开放 API，也不能向外部网络直接暴露。

### 2.3 当前数据库与 Service 一致性

- `forge-starter-job/sql/job_tables.sql` 和现有数据库定义的 `sys_job_config` 只包含执行器、Cron、状态、重试次数、邮箱和 Webhook 等基础字段；`cron_expression` 当前为非空，不支持一次性任务。
- `sys_job_log` 只在执行结束时保存任务名、分组、时间、耗时、成功/失败、结果和异常，没有稳定任务 ID、执行来源、运行中/跳过状态、流程实例或幂等键。
- `V1.0.3__add_logic_delete_to_platform_internal_tables.sql` 已为 `sys_job_config/sys_job_log` 增加 `del_flag`，普通删除使用逻辑删除，日志留存清理允许通过专用 Mapper 物理清理。
- `SysJobConfigServiceImpl#addJob` 先保存数据库再注册 Quartz，事务注解被注释；Quartz 注册失败时可能保留无效配置。
- `SysJobConfigServiceImpl#updateJob` 先更新数据库再使用请求中的新 `jobName/jobGroup` 查 Quartz。若编辑调度身份，旧 Quartz Job 无法被可靠定位；调度器返回 `false` 也不会触发事务回滚。
- `SysJobConfigServiceImpl#selectJobPage` 和 `SysJobLogServiceImpl#selectLogPage` 使用 `LambdaQueryWrapper` 构建查询，不符合当前 Forge 查询 SQL 必须进入 Mapper XML 的规范。
- `JobConfigLoader` 的 `@Component` 被注释，数据库任务启动加载链路实际未启用；`JobAutoRegistrar` 只在数据库不存在记录时注册，数据库有记录但 Quartz 任务缺失时不会恢复。

### 2.4 当前 Quartz 调度与执行链路

- `JobScheduler#addJob/updateJob/updateCron` 只创建 `CronTrigger`，并固定使用 `withMisfireHandlingInstructionDoNothing()`。
- `JobScheduler` 未设置任务时区、一次性 SimpleTrigger、按任务配置的 Misfire、下一次执行查询或调度同步状态。
- `QuartzJobExecutor#execute` 从 JobDataMap 读取执行器配置，调用 `JobExecutorRouterManager` 后在 `finally` 中记录日志；自动和手动触发走同一入口且没有触发来源标记。
- `JobConfig.retryCount/SysJobConfig.retryCount` 已存在，但 `QuartzJobExecutor` 没有按该值执行任务级重试；日志中的实际重试次数也未被设置。
- 当前执行类没有动态并发策略；需求所述“上一轮未完成则跳过”尚未实现。
- `ScheduleConfig` 使用平台 master 数据源、Quartz JDBC JobStore 和集群配置，`forge.job.clustered` 默认开启，可作为集群主防重和故障恢复基础继续复用。
- `RemoteJobExecutorRouter` 已有超时和全局重试，但服务发现仍为 TODO，当前只是按 `http://{serviceName}/job/executor/execute` 拼接地址；本变更不能把“分布式服务发现已完成”作为前提。

### 2.5 当前日志与告警

- `JobMonitor#recordLog` 截断结果和异常后保存日志，失败时遍历 `IJobAlarmNotifier`。
- 仓库中没有 `IJobAlarmNotifier` 的具体实现，`alarmEmail/webhookUrl` 也未由 `JobMonitor` 按任务读取，因此现有告警字段只是配置外壳。
- 日志异常最多保存 4000 字符，需求中的“异常信息完整”需要改为受控长度、下载或独立大字段策略，不能无上限写入或返回响应。
- 日志当前会保存 `jobParam` 和执行结果；若参数包含 Token、手机号或业务敏感数据，存在日志泄露风险，必须新增脱敏和大小限制。

### 2.6 可复用的平台能力

- `forge-plugin-message` 已具备站内信、邮件、短信、模板和发送记录能力，失败告警应复用消息中心，不在任务插件内重新实现邮件系统。
- `forge-starter-idempotent` 已支持 `RETURN_CACHE`、严格拒绝和 Token 模式，可复用 Redisson 锁与结果缓存；开放 API 仍需把幂等键写入执行记录并返回稳定执行 ID。
- `forge-plugin-capability-identity` 已有随机 Token、Key ID、Token Hash、Scope、过期和吊销的安全实现模式，但其 audience 和业务语义面向 MCP，不能直接把 MCP Token 表当作任务开放 Token 表。
- `sys_operation_log` 和 `@OperationLog` 可承载管理端写操作审计；任务执行审计仍需写入任务执行记录。

### 2.7 当前 Flowable 能力与限制

- `sys_flow_model/FlowModel` 已包含模型 Key、BPMN XML、版本、部署 ID、发布时间和 `0=设计/1=已发布`状态。
- 现有 BPMN 设计器已能编辑 ServiceTask、ScriptTask、条件流、异步等基础属性，节点配置应继续归真实流程设计器所有。
- `FlowModelServiceImpl#deployModel` 会形成已发布版本和部署 ID，可以作为任务绑定的发布事实来源。
- `FlowInstanceServiceImpl#deployModelOnDemand` 在流程定义缺失时会尝试自动部署模型。任务技术流程不能直接使用这一宽松行为，必须在绑定和执行前严格要求模型 `status=1` 且发布版本存在。
- 当前 ServiceTask 配置偏开发者类名/表达式，没有 MES 采集、转换、校验、受治理 API 调用和通知节点的业务化配置，也没有本需求要求的节点级重试、超时和失败处置协议。
- `FlowWebhookNotifier` 已有超时和有限重试，但没有统一出站白名单、私网地址、DNS 重绑定和重定向校验。本变更引入出站安全组件时必须同时评估该既有调用点，避免保留旁路。

### 2.8 需求文档与 Forge 现状的差异

- 原需求假设前端为 Vben Admin + Ant Design Vue，实际项目是 Vue 3 + Naive UI + AiCrudPage。
- 原需求建议新建 `mes_job/mes_job_log/mes_flow/mes_flow_node`，Forge 已有 `sys_job_*` 和 Flowable 模型；直接照搬会形成双控制面和双流程设计器。
- 原需求提出把星期 `0=周日…6=周六`作为存储口径，Quartz 原生口径为 `1=SUN…7=SAT`。Forge 应只在前端向导内部使用 0～6，落库和专家模式始终使用可直接执行的 Quartz 表达式。
- 原需求任务状态只描述运行/暂停，但一次性任务还需要“已结束”；状态值也与 Forge 现有 `0=停止、1=运行`相反。迁移必须保留现有值，禁止反转历史语义。
- 原需求同时定义“等待下一次”和“丢弃”两种 Misfire，Cron 场景下两者可能等价，进入实现前必须冻结精确定义。

## 3. 功能点与分阶段范围

### 3.1 阶段划分

| 阶段 | 目标 | 原需求映射 | 是否建议首轮实施 |
|------|------|------------|------------------|
| Phase 0 | 调度内核加固与兼容基线 | FR-03～08 的可靠性前置 | 是 |
| Phase 1 | 基础易用调度中心 | FR-01～08、10、13、21、23、24 | 是 |
| Phase 2 | 治理、告警与监控 | FR-09、11、12、14～18 | 否，独立闸门 |
| Phase 3 | 开放 API 与出站安全 | FR-20、25、26 | 否，安全专项闸门 |
| Phase 4 | Flowable 技术任务编排 | FR-19、22、27、28 | 否，流程专项闸门 |

本 Spec 描述总体目标，但第一次 `/apply` 默认只允许实施 Phase 0 + Phase 1。后续阶段必须在前一阶段验证通过、对应待确认项关闭后再进入 `/apply`。

首轮边界冻结：

- Phase 0/1 创建任务类型字段和 `sys_job_task_type` 字典，但不创建可维护模板；模板表和“选择模板带默认值”属于 Phase 2。
- Phase 0/1 的触发来源枚举包含 `UNKNOWN/SCHEDULED/MANUAL/API`，其中 `API` 只是兼容协议保留值，只有 Phase 3 才能产生新的 API 执行记录；存量记录统一回填 `UNKNOWN`。
- Phase 0/1 必须完成任务查询、新增、编辑、启停、手动执行、删除、日志查看、导出和同步重试的基础资源权限；Phase 2 再增加按任务类型/任务组授权和运维/配置员角色模板。
- Phase 0/1 只迁移本阶段实际读取的字段、索引、字典、路由和权限；并发、Misfire 可配置化、告警、Token、白名单和流程字段分别随 Phase 2～4 迁移，不允许首轮先建没有运行语义的空字段。

### 3.2 Phase 0：调度内核加固

- [ ] `REQ-JOB-P0-01`：引入任务请求 DTO、查询 DTO 和响应 VO，不再把数据库 Entity 作为管理端写入协议。
- [ ] `REQ-JOB-P0-02`：新增服务端字段校验，包括任务身份、执行方式必填关系、JSON 参数格式、Cron 合法性、时区合法性和状态转换。
- [ ] `REQ-JOB-P0-03`：将任务配置和日志分页查询迁移到 Mapper XML，显式过滤 `del_flag=0`并使用 `pageNum/pageSize`。
- [ ] `REQ-JOB-P0-04`：冻结 `(job_name, job_group)` 为现阶段不可编辑的 Quartz 调度身份；编辑页面只允许修改展示和运行配置，避免旧 JobKey 遗留。
- [ ] `REQ-JOB-P0-05`：数据库配置作为期望状态，任务新增/修改/启停/删除后触发统一调度同步；同步失败必须可见、可重试，不允许静默返回 `false`。
- [ ] `REQ-JOB-P0-06`：增加启动协调器，按数据库未删除配置与 Quartz 现状执行新增、更新、暂停和清理孤儿任务；代码注解任务也必须经过同一协调器。
- [ ] `REQ-JOB-P0-07`：所有调度写操作按任务 ID 加互斥锁，防止并发编辑、启停和删除产生交叉状态。
- [ ] `REQ-JOB-P0-08`：日志记录失败不得吞掉任务结果，Phase 0 至少输出结构化 ERROR 和失败计数指标；面向用户的站内/邮件告警属于 Phase 2。调度配置失败不得以“数据库已成功”形式返回。
- [ ] `REQ-JOB-P0-09`：补齐任务模块单元测试基础，覆盖调度同步、状态转换、配置校验和执行日志生命周期。
- [ ] `REQ-JOB-P0-10`：移除管理端 Controller 的统一 `@ApiPermissionIgnore + assertAdmin`模式，增加基础资源权限并在 Service 对执行目标和保护任务做二次校验。

### 3.3 Phase 1：基础易用调度中心

- [ ] `REQ-JOB-P1-01`：任务分页支持名称、分组、状态、执行方式、调度类型、任务类型筛选，返回 Cron/一次性时间、时区、上次和下次执行摘要。
- [ ] `REQ-JOB-P1-02`：新增/编辑使用独立全屏任务配置工作台，列表继续保留在现有系统任务页面；复杂配置不继续堆入通用 CRUD 弹窗。
- [ ] `REQ-JOB-P1-03`：执行方式支持单任务和技术流程占位协议；Phase 1 只开放单任务，流程方式在 Phase 4 实现前不可保存为启用状态。
- [ ] `REQ-JOB-P1-04`：周期调度提供简单模式和专家模式。简单模式覆盖每隔 N 分钟、每小时、每天、每周、每月及常用快捷项。
- [ ] `REQ-JOB-P1-05`：专家模式直接编辑 Quartz 6 段表达式；非法表达式在前端即时提示，后端权威校验失败时禁止保存。
- [ ] `REQ-JOB-P1-06`：简单模式与专家模式可双向转换；复杂表达式无法精确反解析时保留原表达式并回退专家模式，不得丢失配置。
- [ ] `REQ-JOB-P1-07`：后端预览接口按 Cron、IANA 时区和基准时间返回标准化表达式、中文描述和未来 5 次触发时间；保存与实际 Quartz 调度复用同一解析逻辑。
- [ ] `REQ-JOB-P1-08`：支持一次性任务。一次性任务只保存 `fire_once_time`，不保存 Cron；使用 Quartz SimpleTrigger，到点执行一次后状态进入 `COMPLETED`。
- [ ] `REQ-JOB-P1-09`：手动执行一次不改变周期任务或一次性任务的计划状态；一次性任务只有计划触发完成后才自动结束。
- [ ] `REQ-JOB-P1-10`：任务支持 IANA 时区，默认 `Asia/Shanghai`；时区变更后重新计算触发器和未来时间。
- [ ] `REQ-JOB-P1-11`：任务详情聚合配置、调度同步状态、上次/下次执行、连续失败数和最近执行记录。
- [ ] `REQ-JOB-P1-12`：执行记录区分 `UNKNOWN/SCHEDULED/MANUAL/API`，状态支持 `ACCEPTED/RUNNING/SUCCESS/FAILED/SKIPPED`，记录任务 ID、计划触发时间、开始/结束时间和耗时；Phase 1 不产生新的 API 来源记录。
- [ ] `REQ-JOB-P1-13`：日志支持任务、分组、状态、触发来源和时间范围筛选，支持详情和按导出配置导出；异常与结果按权限查看。
- [ ] `REQ-JOB-P1-14`：删除任务继续使用逻辑删除并清理 Quartz 注册，历史执行记录保留；留存清理继续使用专用物理清理方法。

### 3.4 Phase 2：治理、告警与监控

- [ ] `REQ-JOB-P2-01`：新增通用任务类型字典和任务模板，模板可带出执行方式、默认目标、默认参数、调度和告警配置；MES 类型作为模板数据，不硬编码到 Vue。
- [ ] `REQ-JOB-P2-02`：并发策略支持允许并发和禁止并发。禁止并发时上一实例未完成则本次记录为 `SKIPPED`，不得排队后补跑。
- [ ] `REQ-JOB-P2-03`：任务级失败重试在执行记录中逐次留痕。非幂等任务默认不得开启自动重试，启用时必须由配置人员确认副作用风险。
- [ ] `REQ-JOB-P2-04`：Cron Misfire 至少支持“立即补一次”和“等待下一周期”；第三种策略只有在待确认项冻结出可区别语义后实现。
- [ ] `REQ-JOB-P2-05`：失败告警复用消息中心，支持站内信和邮件；Webhook 统一走 Phase 3 出站安全能力，Phase 3 未完成前不得开放任意 Webhook。
- [ ] `REQ-JOB-P2-06`：告警只在最终失败后发送，包含任务安全摘要、执行 ID、失败时间和详情入口；不得发送完整参数、Token 或完整响应体。
- [ ] `REQ-JOB-P2-07`：在 Phase 0/1 基础资源权限上增加按任务类型/任务组授权，以及 MES 运维、业务配置员和审计员角色模板；模板、审计和告警配置使用独立权限。
- [ ] `REQ-JOB-P2-08`：新增、编辑、删除、启停、立即执行、批量操作和同步重试写入操作审计，记录安全裁剪后的前后值。
- [ ] `REQ-JOB-P2-09`：支持批量启动、停止和逻辑删除；每一条任务独立校验并返回成功/失败明细，禁止部分失败后只返回统一成功。
- [ ] `REQ-JOB-P2-10`：调度监控展示近 24 小时执行量、成功率、失败率、跳过量、失败任务 TOP 和连续失败任务；指标全部来源于执行记录聚合。
- [ ] `REQ-JOB-P2-11`：执行记录默认保留不少于 90 天，先通过索引和留存清理满足容量；是否做 MySQL 分区由容量评估决定，不在缺少数据量证据时直接改造历史表分区。

### 3.5 Phase 3：开放 API 与出站安全

- [ ] `REQ-JOB-P3-01`：新增与内部 `/job/executor/execute` 完全隔离的 `/openapi/v1/jobs/**` 资源接口。
- [ ] `REQ-JOB-P3-02`：开放 Token 使用高强度随机值，只在创建时显示一次；数据库只保存 Key ID、前缀和 Hash，不保存可还原明文。
- [ ] `REQ-JOB-P3-03`：Token 支持启用、吊销、过期、最后使用时间、调用方身份和最小 Scope；Scope 至少区分任务读取、执行、日志读取和流程执行。
- [ ] `REQ-JOB-P3-04`：Token 资源范围可以限制到任务组、任务 ID 或已发布技术流程；Scope 通过不等于拥有所有资源。
- [ ] `REQ-JOB-P3-05`：开放触发请求必须携带 `Idempotency-Key`。同一 Token、目标和 Key 在 24 小时内只创建一个执行记录，重复请求返回原执行 ID 和当前状态。
- [ ] `REQ-JOB-P3-06`：幂等使用 Redisson 锁串行化同 Key 请求，并在锁内先查询/预占执行记录；Redis 不可用时开放触发默认失败关闭，不能降级为无幂等执行。
- [ ] `REQ-JOB-P3-07`：开放 API 返回标准 HTTP 401/403/409/429 和 Forge 统一响应语义，不暴露内部异常类名、Bean 名、堆栈或数据库信息。
- [ ] `REQ-JOB-P3-08`：新增平台级出站白名单，按使用场景区分任务告警 Webhook 和技术流程 API 节点。
- [ ] `REQ-JOB-P3-09`：Webhook 只允许 HTTP/HTTPS、禁止 URL userinfo、禁止环回/链路本地/私网/保留 IPv4 和 IPv6，禁止未校验重定向；域名所有 A/AAAA 结果都必须校验。
- [ ] `REQ-JOB-P3-10`：技术流程 API 节点访问内网地址必须由平台管理员在独立场景中明确允许；Webhook 场景即使命中普通白名单也不得访问私网。
- [ ] `REQ-JOB-P3-11`：出站请求默认连接和读取超时 3 秒，节点整体超时可以更长但不得突破平台上限；响应体设置大小上限且不默认写入任务日志。
- [ ] `REQ-JOB-P3-12`：现有 `FlowWebhookNotifier` 等同类出站调用点接入共享安全校验或明确封禁旁路。

### 3.6 Phase 4：Flowable 技术任务编排

- [ ] `REQ-JOB-P4-01`：技术任务流程复用 `sys_flow_model`、Flowable 部署版本和现有 BPMN 设计器，增加明确的技术流程类型，不新增 `mes_flow/mes_flow_node`。
- [ ] `REQ-JOB-P4-02`：任务保存和启动前校验流程 `status=1`、部署 ID 和流程定义均存在；草稿、挂起、禁用或无部署模型不可绑定。
- [ ] `REQ-JOB-P4-03`：任务绑定保存已发布模型 Key/版本策略，不以可变数据库 UUID 作为唯一业务契约；默认启动绑定时的发布版本还是最新发布版本见待确认项。
- [ ] `REQ-JOB-P4-04`：任务插件通过 `JobFlowExecutor` SPI 调用流程能力，本地聚合部署和独立 flow 服务均由适配器实现，禁止 `forge-plugin-job` 直接依赖审批业务 Service。
- [ ] `REQ-JOB-P4-05`：首期安全节点支持开始、结束、已注册采集处理器、受治理转换、受治理校验、API 调用、条件分支和通知。
- [ ] `REQ-JOB-P4-06`：API 调用节点复用 Phase 3 出站安全，Header/Body 中的凭据使用安全引用，不在 BPMN XML、日志或前端响应中存明文 Secret。
- [ ] `REQ-JOB-P4-07`：每次技术流程执行关联一个任务执行 ID 和 Flowable `processInstanceId`，详情可以下钻历史节点、耗时、状态和失败节点。
- [ ] `REQ-JOB-P4-08`：节点支持有限重试次数、固定/指数退避和平台上限内超时；最终失败后按流程策略中止、跳过或告警后继续。
- [ ] `REQ-JOB-P4-09`：任意 Script 节点默认禁用。只有独立脚本沙箱、安全评审、资源限制和审计全部完成后才能另立变更开放，不允许直接执行任意 Java、Shell、SQL 或不受控 SpEL。
- [ ] `REQ-JOB-P4-10`：流程模型另存草稿不影响已发布版本；任务运行期间继续使用本次启动的不可变定义版本。

## 4. 业务规则

### 4.1 调度身份与配置来源

- 现阶段 Quartz JobKey 固定为 `(job_name, job_group)`；任务创建后两字段只读。
- 代码注解任务首次启动时可以创建数据库配置，数据库配置一旦存在即成为运行期事实来源；后续代码默认值不得静默覆盖管理员修改。
- 系统保护任务可以禁止删除或修改执行目标，但仍可按授权启停、调整 Cron 和查看日志。
- 任务参数必须是合法 JSON 或空值。服务端只向声明支持 String/JSON 参数的执行器传递，禁止通过反射任意匹配多参数方法。

### 4.2 Cron 与星期口径

- 数据库存储、管理 API、专家模式和 Quartz 始终使用 Quartz 6 段表达式。
- 专家模式严格接收 6 段表达式，拒绝带可选年份的第 7 段，避免前端、后端和 Quartz 能力边界不一致。
- 简单向导内部星期可以使用 JavaScript `0=周日…6=周六`，提交前转换为 Quartz `1=周日…7=周六`或英文星期；该内部值不直接落入 Cron 字符串。
- 中文描述由服务端根据标准化 Cron 和时区生成或校正；前端描述仅作即时预览。
- 未来 5 次触发由服务端使用与 Quartz 相同的解析器计算，前端不得使用硬编码日期或独立的近似算法作为验收事实。
- 复杂 Cron 不能精确映射简单向导时显示“自定义周期”，原表达式保持不变。
- “每隔 N 分钟”固定对齐自然时钟分钟，生成 `0 0/N * * * ?`；不是从保存时刻开始滚动 N 分钟。
- 每月 29/30/31 日在不存在该日期的月份跳过，遵循 Quartz Cron 语义，不自动调整到月末。
- 预览基准时间为排他下界，返回值必须严格晚于 `baseTime`。
- 可反解析到简单模式的集合仅限本 Spec 五类向导生成的标准表达式和列出的快捷表达式；其它等价或复杂写法允许安全回退专家模式，不要求实现 Cron 语义化简器。

### 4.3 任务状态机

保留现有数据库状态语义：

| 值 | 状态 | 可执行操作 |
|----|------|------------|
| `0` | `STOPPED` 已停止 | 启动、编辑、手动执行、删除 |
| `1` | `RUNNING` 运行中 | 停止、编辑非身份字段、手动执行、删除 |
| `2` | `COMPLETED` 已结束 | 查看、复制、删除；重新调度必须显式编辑未来时间并选择新状态 |

状态转换规则：

```text
创建启用任务 -> RUNNING
创建停用任务 -> STOPPED
STOPPED --启动--> RUNNING
RUNNING --停止--> STOPPED
RUNNING(ONCE) --计划触发完成--> COMPLETED
任意未删除状态 --删除--> del_flag=1
```

- 手动执行不会改变任务状态。
- 一次性任务无论本次最终成功或失败都进入 `COMPLETED` 并保留执行记录；Phase 1 不做任务级自动重试，失败后由用户复制或重新安排。
- 已结束一次性任务不能直接“启动”旧触发时间。
- 所有状态变更必须通过状态机方法，禁止 Controller 或普通 Service 直接 `setStatus`完成业务流转。

### 4.4 调度配置与 Quartz 同步

- 数据库保存任务期望状态，Quartz 保存执行运行态，两者不使用分布式事务伪装强一致。
- 配置写入后标记调度同步为 `PENDING`，提交成功后由协调器应用到 Quartz，成功更新为 `SYNCED`，失败更新为 `FAILED`并保存安全错误摘要。
- 正常管理请求在数据库提交后同步调用一次协调器并等待本次结果；后台协调任务只负责故障恢复和重试，不以不可见的长时间异步代替“提交后即时生效”。
- 管理接口在同步失败时返回明确失败或待重试状态，页面展示“调度未同步”，不能显示为正常运行。
- 协调器在服务启动后执行全量对账，并按默认 60 秒、可配置周期持续扫描 `PENDING/FAILED/DELETE_PENDING`及 Quartz 缺失任务；不使用阻塞线程 `sleep`实现重试。
- 同步请求携带配置 `version`，获取任务锁后重新读取最新版；旧版本同步结果不得覆盖新版本配置或同步状态。
- Quartz JobDataMap 必须写入 `forgeManaged=true`、`jobConfigId`和`configVersion`。只有带 Forge 管理标识且能与数据库记录核对的孤儿任务才允许清理。
- 同步失败使用 `next_sync_time/sync_retry_count`做非阻塞退避，建议 1 分钟、5 分钟、30 分钟后进入 30 分钟周期重试；管理员可手动立即重试。
- 删除任务先进入 `DELETE_PENDING` 并尝试移除 Quartz Job，成功后再写逻辑删除；失败时保留可见配置和重试入口，避免逻辑删除后留下无法追踪的孤儿调度。
- 新增前按 `(job_name, job_group)`摘要加配置锁，记录创建后按稳定任务 ID 加锁；Redis/Redisson 不可用时管理端调度写操作失败关闭，已有 Quartz 调度继续运行。
- 配置同步锁和运行期“禁止并发”执行锁使用不同命名空间和生命周期，禁止互相复用造成调度写操作被长任务阻塞。

### 4.5 一次性任务

- `schedule_type=ONCE` 时 `cron_expression` 必须为空，`fire_once_time` 必填。
- `schedule_type=CRON` 时 `cron_expression` 必填，`fire_once_time` 必须为空。
- `fire_once_time` 按任务时区解释并转换为时间点交给 Quartz。
- 管理 API 使用不带 offset 的秒级本地时间 `yyyy-MM-dd'T'HH:mm:ss` 加 `timezone`提交；响应同时返回本地时间、IANA 时区和解析后的 RFC 3339 offset 时间。
- DST 不存在时间和存在两个 offset 的歧义时间均拒绝保存，要求用户选择无歧义时刻，不在服务端静默前移或选择任一 offset。
- 创建/编辑拒绝小于或等于当前时间的触发时间；历史迁移可以保留已结束记录。
- 修改一次性任务时区会按新时区重新解释同一墙上时间并改变绝对触发时刻，页面必须展示变更前后时间并二次确认。
- 计划触发使用独立 Trigger 元数据标识，只有该触发完成才更新一次性任务状态。
- 一次性 SimpleTrigger 固定使用“服务恢复后立即补触发一次”的 Misfire 行为；补触发完成后按上述规则进入 `COMPLETED`。

### 4.6 执行、并发与重试

- 执行状态固定为 `FAILED=0、SUCCESS=1、RUNNING=2、SKIPPED=3、ACCEPTED=4`，保留历史 0/1 语义。
- 手动/API 触发先创建 `ACCEPTED`记录并返回执行 ID，执行器开始时更新为 `RUNNING`，结束后原记录更新为 `SUCCESS/FAILED/SKIPPED`；计划触发可以在执行器入口直接创建 `RUNNING`记录。
- Quartz 提交失败时把已创建的 `ACCEPTED`记录更新为 `FAILED`并记录安全错误摘要，不允许执行记录永久停留在已受理。
- 手动/API 触发通过临时 JobDataMap 传入执行 ID、触发来源、调用人或 Token Key ID；不得把原始 Token 写入 JobDataMap 或日志。
- 禁止并发任务在执行入口尝试获取任务级 Redisson 锁；获取失败立即写 `SKIPPED`，不等待上一轮完成后补跑。
- 重试必须在同一执行 ID 下记录尝试次数。只有最终失败才发送失败告警。
- 超时和重试不保证任意本地阻塞方法可安全中断；执行器必须遵守中断、超时和幂等契约，无法安全中断的任务不得宣称支持强制终止。

### 4.7 日志、隐私与留存

- `job_param`、Header、Body、Token、手机号、身份证、银行卡和密钥等在写日志前经过结构化脱敏。
- 执行结果和异常设置长度上限；完整诊断需要通过受权限控制的详情或文件化诊断方案获取，不能在列表和告警中返回完整堆栈。
- 普通行级删除走逻辑删除；90 天留存清理是专用物理清理任务并记录清理审计。
- 监控聚合默认排除逻辑删除记录，但留存清理前的历史任务删除不影响执行统计。

### 4.8 权限与任务范围

- 管理端权限由资源权限控制，不再用 `@ApiPermissionIgnore + assertAdmin`作为唯一边界。
- 系统管理员拥有全部权限；运维可以查询、启停、手动执行和查看日志；配置员是否可以新增/编辑由任务类型或任务组授权；审计员只读。
- Token 权限与登录用户权限相互独立，开放 API 只能按 Token Scope 和资源范围判断，不能伪造普通用户会话。
- 本 Spec 默认任务中心继续作为平台主库控制面，不自动纳入普通租户行级拦截；多租户自助任务配置是否进入本次范围见待确认项。

## 5. 数据变更

### 5.1 现有表扩展

分阶段迁移边界：

- Phase 0/1：`cron_expression`可空，新增 `schedule_type/fire_once_time/timezone/cron_human_desc/invoke_mode/task_type/config_source/protected_flag/sync_status/sync_error/sync_time/sync_retry_count/next_sync_time/consecutive_failures/version`及审计字段；日志新增 `job_config_id/trigger_type/scheduled_fire_time/fire_instance_id/operator_id/create_time/update_time`，扩展现有 `status`并增加对应索引。
- Phase 2：新增 `concurrent_policy/misfire_policy/alarm_enabled/alarm_channels/alarm_config`和模板表。
- Phase 3：日志新增 `token_key_id/idempotency_key`，新增 Token 表和出站白名单表。
- Phase 4：新增 `flow_model_key/flow_version_policy`及日志的流程实例/失败节点字段。

Phase 0/1 字段类型先冻结为：`schedule_type/invoke_mode/config_source/sync_status varchar(20)`、`timezone varchar(64)`、`task_type varchar(64)`、`cron_human_desc varchar(255)`、`fire_once_time datetime`、`sync_error varchar(1000)`、`sync_time/next_sync_time datetime`、`sync_retry_count/consecutive_failures/version int`。实施 Research 只能在不改变业务语义的前提下调整长度或索引名，并回填 Spec。

| 操作 | 表名 | 字段/索引 | 说明 |
|------|------|-----------|------|
| 调整 | `sys_job_config` | `cron_expression`改为可空 | 支持一次性任务；CRON/ONCE 互斥由服务端校验 |
| 新增字段 | `sys_job_config` | `schedule_type` | `CRON/ONCE`，存量回填 `CRON` |
| 新增字段 | `sys_job_config` | `fire_once_time` | 一次性任务触发本地时间 |
| 新增字段 | `sys_job_config` | `timezone` | IANA 时区，存量回填 `Asia/Shanghai` |
| 新增字段 | `sys_job_config` | `cron_human_desc` | 服务端生成的可读描述快照 |
| 新增字段 | `sys_job_config` | `invoke_mode` | `SINGLE/FLOW`，存量回填 `SINGLE` |
| 新增字段 | `sys_job_config` | `task_type` | 通用任务类型字典值 |
| 新增字段 | `sys_job_config` | `flow_model_key`、`flow_version_policy` | Phase 4 使用 |
| 新增字段 | `sys_job_config` | `concurrent_policy` | `ALLOW/SKIP_IF_RUNNING` |
| 新增字段 | `sys_job_config` | `misfire_policy` | 冻结后的 Misfire 策略值 |
| 新增字段 | `sys_job_config` | `alarm_enabled`、`alarm_channels`、`alarm_config` | 告警开关、渠道和安全配置；JSON 只保存非密钥引用 |
| 新增字段 | `sys_job_config` | `config_source`、`protected_flag` | 区分手工/注解/系统任务及保护策略 |
| 新增字段 | `sys_job_config` | `sync_status`、`sync_error`、`sync_time`、`sync_retry_count`、`next_sync_time` | 数据库与 Quartz 同步可观测状态和非阻塞重试 |
| 新增字段 | `sys_job_config` | `consecutive_failures`、`version` | 连续失败摘要和乐观锁 |
| 新增字段 | `sys_job_config` | `create_by`、`create_dept`、`update_by` | 补齐管理审计字段 |
| 保留索引 | `sys_job_config` | `uk_job_name_group_active` | 保持逻辑删除后可重建语义 |
| 新增索引 | `sys_job_config` | `(status, schedule_type, del_flag)`、`(sync_status, del_flag)` | 启动协调和列表查询 |
| 新增字段 | `sys_job_log` | `job_config_id` | 稳定关联配置，避免任务名变更后日志失联 |
| 新增/调整字段 | `sys_job_log` | 新增 `trigger_type`，扩展现有 `status` | 来源为 `UNKNOWN/SCHEDULED/MANUAL/API`；状态保留 `0=失败、1=成功`，新增 `2=运行中、3=已跳过、4=已受理` |
| 新增字段 | `sys_job_log` | `scheduled_fire_time`、`fire_instance_id` | Quartz 触发诊断信息 |
| 新增字段 | `sys_job_log` | `operator_id`、`token_key_id` | 手动或 API 调用方安全标识 |
| 新增字段 | `sys_job_log` | `process_instance_id`、`flow_model_key`、`fail_node_id`、`fail_node_name` | Phase 4 流程下钻 |
| 新增字段 | `sys_job_log` | `idempotency_key` | Phase 3 API 幂等审计，不包含 Token 原文 |
| 新增字段 | `sys_job_log` | `create_time`、`update_time` | 支持先创建 RUNNING 再更新最终状态 |
| 新增索引 | `sys_job_log` | `(job_config_id, trigger_time)`、`(status, trigger_time)`、`(idempotency_key, trigger_time)`、`process_instance_id` | 日志查询、监控和幂等回查 |

字段长度、默认值和索引名称在实施前由 Flyway Research 结合当前真实表结构确定；迁移必须使用 `information_schema` 防重复保护，不修改已执行脚本。

`sys_job_log.job_config_id` 对新记录必填，对存量历史允许为空。迁移按未删除及已删除任务的 `(job_name, job_group)`做唯一匹配；无法匹配、同名历史多义或配置已物理缺失时保持空值并标记为历史记录，禁止猜测关联。

现有 `alarm_email/webhook_url` 在 Phase 2/3 迁移到统一告警配置后先保留为兼容字段：迁移脚本只做安全可转换数据的回填，新代码不再把它们作为唯一事实源；完成兼容窗口和数据核验后才能另立迁移删除。

### 5.2 新增平台表

| 阶段 | 表名 | 用途 | 关键字段 |
|------|------|------|----------|
| Phase 2 | `sys_job_template` | 通用任务类型模板 | `template_code/task_type/default_config/status/tenant_id/审计字段/del_flag` |
| Phase 3 | `sys_job_api_token` | 开放 API Token 元数据 | `token_key_id/token_prefix/token_hash/scopes/resource_scope/status/expires_at/last_used_at/revoked_at/审计字段/del_flag` |
| Phase 3 | `sys_outbound_whitelist` | 通用出站地址治理 | `scene/match_type/match_value/allow_private/status/remark/tenant_id/审计字段/del_flag` |

- 新表必须包含 Forge 规定的 `id、tenant_id、create_by、create_time、create_dept、update_by、update_time`，默认租户内置数据使用 `tenant_id=1`。
- Token 只保存 Hash，不使用可逆加密保存原始 Token。
- 不新增 `mes_flow/mes_flow_node`；流程定义和节点继续存于 Flowable 模型/BPMN。

### 5.3 字典、菜单与导出配置

新增或补齐以下内置字典，全部通过 Flyway `NOT EXISTS` 写入。Phase 0/1 只创建任务调度类型、调用方式、触发来源、执行状态、任务类型和同步状态；其余字典随对应阶段创建：

- `sys_job_schedule_type`
- `sys_job_invoke_mode`
- `sys_job_trigger_type`
- `sys_job_execution_status`
- `sys_job_concurrent_policy`
- `sys_job_misfire_policy`
- `sys_job_alarm_channel`
- `sys_job_task_type`
- `sys_job_sync_status`
- `sys_job_token_status`
- `sys_outbound_match_type`

同步新增：

- 任务编辑/详情隐藏路由和按钮权限资源。
- 日志导出的 `sys_excel_export_config/sys_excel_column_config`。
- Phase 2～4 对应模板、Token、白名单、流程绑定权限资源。

## 6. 接口变更

### 6.1 管理端任务接口

| 操作 | 接口 | 方法 | 阶段 | 说明 |
|------|------|------|------|------|
| 调整 | `/job/config/page` | GET | P0/P1 | 使用查询 DTO，返回任务摘要 VO 和调度状态 |
| 调整 | `/job/config/{id}` | GET | P0/P1 | 返回完整可编辑配置 VO，敏感字段裁剪 |
| 新增 | `/job/config/{id}/overview` | GET | P1 | 聚合上次/下次执行、连续失败和同步状态 |
| 调整 | `/job/config` | POST | P0/P1 | DTO 校验、保存期望状态并同步 Quartz |
| 调整 | `/job/config` | PUT | P0/P1 | JobKey 只读、乐观锁和状态机校验 |
| 调整 | `/job/config/{id}` | DELETE | P0 | 逻辑删除并清理调度注册 |
| 调整 | `/job/config/{id}/start` | POST | P0/P1 | 状态机启动并同步 Quartz |
| 调整 | `/job/config/{id}/stop` | POST | P0/P1 | 状态机停止并暂停 Quartz |
| 调整 | `/job/config/{id}/trigger` | POST | P1 | 创建 MANUAL 执行记录并返回执行 ID |
| 新增 | `/job/config/{id}/sync` | POST | P0 | 重试 FAILED/PENDING 调度同步 |
| 新增 | `/job/schedule/preview` | POST | P1 | 校验/标准化 Cron，返回描述和未来 5 次 |
| 新增 | `/job/config/batch/start` | POST | P2 | 批量启动并返回逐条结果 |
| 新增 | `/job/config/batch/stop` | POST | P2 | 批量停止并返回逐条结果 |
| 新增 | `/job/config/batch/delete` | POST | P2 | 批量逻辑删除并返回逐条结果 |

管理端敏感写接口继续使用 Forge 加解密链路和统一响应，权限由资源权限显式校验。

`POST /job/schedule/preview` 请求至少包含 `cronExpression、timezone、baseTime`；`baseTime` 使用 RFC 3339 offset 时间且可省略，省略时取服务端当前时刻。响应的 `nextFireTimes` 使用 RFC 3339 offset 时间并同时返回 `timezone`，避免浏览器按本地时区二次误解。

### 6.2 执行记录与监控接口

| 操作 | 接口 | 方法 | 阶段 | 说明 |
|------|------|------|------|------|
| 调整 | `/job/log/page` | GET | P1 | 支持任务 ID、来源、状态和时间范围 |
| 调整 | `/job/log/{id}` | GET | P1 | 返回受权限控制的执行详情 |
| 新增 | `/job/log/export` | POST | P1 | 按当前筛选条件导出 |
| 保留 | `/job/log/clean` | DELETE | P1/P2 | 专用留存清理，增加权限和审计 |
| 新增 | `/job/monitor/summary` | GET | P2 | 近 24 小时核心指标 |
| 新增 | `/job/monitor/failure-top` | GET | P2 | 失败 TOP 和连续失败任务 |

### 6.3 开放 API

| 操作 | 接口 | 方法 | Scope | 说明 |
|------|------|------|-------|------|
| 新增 | `/openapi/v1/jobs/{jobId}` | GET | `job:read` | 查询授权任务摘要 |
| 新增 | `/openapi/v1/jobs/{jobId}/executions` | POST | `job:trigger` | 携带 Idempotency-Key 触发一次，返回执行 ID |
| 新增 | `/openapi/v1/jobs/{jobId}/executions` | GET | `job:log:read` | 查询授权任务执行记录 |
| 新增 | `/openapi/v1/job-executions/{executionId}` | GET | `job:log:read` | 查询单次执行状态和安全结果摘要 |
| 新增 | `/openapi/v1/flows/{modelKey}/executions` | POST | `job:flow:trigger` | Phase 4 触发已授权发布流程 |
| 新增 | `/openapi/v1/flow-executions/{executionId}` | GET | `job:flow:read` | Phase 4 查询流程和节点进度 |

- 开放 API 使用标准 JSON + HTTPS + Bearer Token，不使用管理端专有报文加密协议。
- `/job/executor/execute` 保持内部协议，不属于开放 API；需增加内部认证或网络边界验证，但不得向外部文档暴露 Handler 调用能力。

### 6.4 Phase 2～4 管理接口

- `/job/template/**`：模板分页、详情、新增、修改、启停、逻辑删除和应用模板。
- `/job/token/**`：Token 元数据分页、创建、吊销；创建响应仅一次返回明文。
- `/job/outbound-whitelist/**`：白名单分页、校验、增删改和启停。
- `/job/flow/options`：只返回已发布、当前用户有权绑定的技术流程。
- `/job/flow/{executionId}/nodes`：按任务执行 ID 查询流程节点历史。

## 7. 前端信息架构与交互

### 7.1 任务列表

- 保留 `/system/job-config` 作为任务总览，使用紧凑企业控制台表格，不增加装饰性统计卡和大面积渐变。
- 列表核心字段：任务名称、分组、执行方式、调度摘要、时区、状态、同步状态、上次执行、下次执行。
- 主操作顺序：编辑、运行一次；详情、日志、启停、删除等低频操作进入更多菜单并遵循按钮语义颜色。
- 新建和编辑进入全屏工作台，路由建议 `/system/job-config/editor/:id?`；任务详情可以复用工作台只读模式或独立 `/detail/:id`。

### 7.2 任务配置工作台

工作台按以下区域组织：

1. 基本信息：任务身份、名称/说明、任务类型和状态。
2. 执行配置：单任务/流程、Bean/Handler/RPC 目标、参数和目标校验。
3. 调度配置：Cron/一次性、时区、简单/专家模式和未来触发预览。
4. 执行治理：并发、Misfire、重试和超时摘要。
5. 告警配置：渠道、接收人和受治理 Webhook。
6. 保存检查：显示标准化配置、下一次执行和安全提示。

Phase 1 只展示已经实现的区域；后续阶段未启用的配置不得以可编辑但保存无效的形式提前暴露。

### 7.3 CronBuilder

- 抽成通用组件，不把生成、解析和描述逻辑继续堆在 `job-config.vue`。
- 简单模式使用业务语言和选择控件，不要求普通用户输入表达式。
- 专家模式显示 Quartz 6 段说明和服务端校验结果。
- 星期控件内部使用 0～6 时必须在组件边界完成 Quartz 转换。
- 预览显示任务时区、未来 5 次真实日期时间和“自定义周期”降级提示。

### 7.4 日志与详情

- 日志默认按当前任务过滤，支持状态、来源和时间范围；日期参数使用后端 DTO 明确接收。
- 详情按“基本信息、执行信息、结果摘要、异常摘要、流程节点”分区。
- 完整异常、请求/响应等高敏感诊断信息按权限和脱敏规则展示，不使用超大 Tooltip 承载全部内容。

## 8. 影响范围

### 8.1 后端

- `forge-plugin-job`：Controller、DTO/VO、Service、Manager、Mapper XML、Scheduler、Executor、Monitor、SPI 和自动注册链路。
- `forge-starter-job`：注解协议、调度公共模型或文档；不得在 starter 中维护正式数据库迁移。
- `forge-plugin-message`：任务失败告警适配。
- `forge-starter-idempotent`：必要时扩展从请求参数生成业务幂等键的适配，但不能破坏既有场景。
- `forge-plugin-flow` / `forge-flow-client`：技术流程发布筛选、严格启动、节点适配和执行历史查询。
- `forge-starter-log` / 系统操作日志：任务管理操作审计。
- 新增共享出站安全能力的承载模块待 Phase 3 设计确认。
- `forge-admin-server` 和可选 `forge-flow` 的装配、配置和启动验证。

### 8.2 前端

- `forge-admin-ui/src/views/system/job-config.vue`
- `forge-admin-ui/src/views/system/job-log-list.vue`
- 新增任务编辑/详情工作台和通用 CronBuilder。
- 新增任务模板、Token、白名单和监控页面或工作台分区。
- BPMN 节点属性面板增加受治理技术节点配置，不另建节点配置页面。
- API、字典、路由和权限适配。

### 8.3 数据库和运行环境

- `forge-server/db/migration/` 新增单调递增 Flyway 脚本。
- `sys_job_config/sys_job_log` 兼容迁移和索引。
- 新增字典、菜单/按钮、导出配置、模板、Token 和白名单数据。
- Quartz QRTZ 表不做破坏性结构修改。
- Redis 用于并发锁、开放 API 幂等锁和结果缓存。

## 8.5 测试策略

- **测试范围**：任务配置校验、Cron 转换/预览、时区、一次性触发、状态机、数据库与 Quartz 协调、并发跳过、Misfire、重试、告警、权限、日志脱敏、开放 API Token/Scope/幂等、SSRF、Flowable 发布绑定和节点失败策略。
- **覆盖率目标**：Phase 0/1 新增核心 Service、状态机、Cron/时区和调度协调分支必须有单测；Phase 3 安全校验与 Phase 4 发布绑定/重试属于高风险路径，关键允许/拒绝分支必须覆盖。
- **独立 Test Spec**：是；进入 `/apply` 前基于本 Spec 生成 `test-spec.md`，后续阶段只做增量追加。

### 8.5.1 Phase 0/1 必测

- 存量任务迁移后 Cron、状态和 JobKey 不变。
- 合法/非法 Quartz Cron、简单模式五类生成、复杂 Cron 降级。
- 星期 0～6 UI 值到 Quartz 值的双向转换。
- Asia/Shanghai、UTC 和至少一个 DST 时区的未来 5 次触发。
- 一次性任务创建、编辑、停止、计划触发、失败和完成状态。
- 手动触发一次性任务不提前结束计划。
- 数据库写成功但 Quartz 同步失败、服务重启后重试恢复。
- Quartz 任务缺失、数据库配置存在时重建；保护任务不被错误清理。
- 日志时间筛选、触发来源、RUNNING 到最终状态更新和导出。

### 8.5.2 Phase 2 必测

- 禁止并发任务在两实例同时触发时只执行一次，另一条记录为 SKIPPED。
- 每种 Misfire 策略在服务停止/恢复后的行为。
- 任务重试次数、退避、最终告警和非幂等风险提示。
- 消息/邮件/Webhook 告警渠道选择、失败上限和敏感信息裁剪。
- 批量操作部分失败返回逐条结果。
- 监控聚合与底层执行记录一致。

### 8.5.3 Phase 3 必测

- Token 原文仅创建响应出现一次，数据库只保存 Hash。
- 过期、吊销、错误 audience、缺 Scope 和越资源范围分别返回 401/403。
- 同一 Idempotency-Key 并发请求只产生一个执行 ID；24 小时外可重新执行。
- Redis 不可用时开放触发失败关闭。
- 域名、IPv4、IPv6、CIDR、DNS 多结果、重定向、userinfo、非 HTTP 协议和内网保留地址 SSRF 用例。
- 出站超时、响应大小、重试上限和日志脱敏。

### 8.5.4 Phase 4 必测

- 草稿、挂起、禁用和无部署流程均不可绑定或启动。
- 发布流程另存草稿不影响已绑定运行版本。
- 顺序、条件分支、API 调用、通知节点执行和历史下钻。
- 节点固定/指数退避、超时、中止、跳过和告警后继续。
- 独立 flow 服务和本地聚合两种部署适配的最小契约测试。
- 任意 Script/Java/SQL 未授权执行被拒绝。

### 8.5.5 验证命令基线

- 文档阶段：`git diff --check`、Spec 路径/状态/待确认项检查。
- 后端阶段：相关 job/message/flow/idempotent 模块单测与 `forge-admin-server -am package -DskipTests`。
- 前端阶段：Node `v20.19.0` 下执行定向测试、Lint 和生产构建；Cron/工作台交互使用浏览器或 Playwright 验证。
- Flyway 阶段：防重复静态检查、`${...}` placeholder 扫描、开发库迁移和 `forge_schema_history` 核验。
- 集群/运行态：至少双实例验证禁止并发、幂等、Misfire 和重启恢复；未具备环境时必须明确记录为待用户联调，不得写成通过。

## 8.6 验收标准

### 8.6.1 Phase 0/1 产品验收

- 既有任务升级前后 JobKey、Cron、启停状态和有效 Quartz Trigger 数量一致；没有带 `forgeManaged=true` 的外部 Quartz Job 不被协调器删除。
- 新增、修改、启停或删除发生 Quartz 同步失败时，接口和页面均显示失败/待同步状态；故障解除后，在下一次 60 秒对账周期内恢复为 `SYNCED`。
- 简单模式五类向导、常用快捷和专家模式保存后，服务端标准 Cron、中文描述、未来 5 次与 Quartz Trigger 一致。
- 以 `baseTime=2026-07-17T10:07:30+08:00`、`timezone=Asia/Shanghai` 为固定样例：`0 0/5 * * * ?` 的首次预览是 `2026-07-17T10:10:00+08:00`；`0 15 * * * ?` 的首次预览是 `2026-07-17T10:15:00+08:00`；`0 0 8 * * ?` 的首次预览是 `2026-07-18T08:00:00+08:00`。
- 复杂 Cron 无法反解析时自动进入专家模式，原表达式逐字符保留；非法或 7 段表达式不能保存。
- 一次性任务数据库 Cron 为空，只产生一个 SimpleTrigger；计划触发成功或失败后均进入 `COMPLETED`，手动执行不改变计划状态。
- 一次性任务遇服务停机错过时间时，恢复后只补触发一次；重复启动和协调对账不能再次执行同一计划 Trigger。
- 时区预览至少覆盖 `Asia/Shanghai`、`UTC` 和一个 DST 时区；DST 不存在或歧义的本地时间被明确拒绝。
- 手动触发接口先返回稳定执行 ID，执行记录按 `ACCEPTED -> RUNNING -> SUCCESS/FAILED`流转；Quartz 提交失败直接进入 `FAILED`，不遗留长期 ACCEPTED。
- 日志按任务、状态、来源和时间范围组合筛选准确，导出与当前筛选一致，参数/结果/异常中的敏感字段不出现在列表、导出和告警摘要。
- 普通用户没有对应资源权限时不能查询、修改、启停、执行、删除或导出任务；平台管理员权限保持可用。

### 8.6.2 后续阶段验收闸门

- Phase 2：双实例禁止并发只产生一个真实业务执行，另一记录为 `SKIPPED`；Misfire、最终失败告警、批量明细和监控指标与执行记录一致。
- Phase 3：Token 原文只出现一次；过期/吊销/越 Scope 被拒；相同 Idempotency-Key 并发请求只有一个执行 ID；SSRF 测试矩阵全部通过。
- Phase 4：草稿流程不能绑定；发布版本可稳定执行；节点顺序/分支、重试/超时/失败策略和历史下钻可验证；任意脚本执行入口保持关闭。

## 9. 风险与关注点

- **状态流转风险**：一次性任务、启停、完成、同步失败均涉及状态机，必须人工审查状态转换和恢复规则。
- **权限变更风险**：现有超级管理员专用接口将改为资源权限控制，错误放权可能允许普通用户执行高权限 Bean；所有权限资源和 Service 二次校验必须审查。
- **远程代码执行风险**：Bean/Handler、Flowable ServiceTask、表达式和 Script 都可能成为 RCE 入口；不能把任意类名、方法名或脚本能力开放给非平台管理员。
- **SSRF 风险**：Webhook、API 调用节点和现有流程 Webhook 都是出站请求入口；只做字符串域名匹配不能满足安全要求。
- **重复副作用风险**：任务级重试、Misfire、手动/API 重复触发都可能重复同步订单、发送消息或扣减库存；非幂等任务必须默认关闭重试并显式提示。
- **双事实源风险**：数据库与 Quartz 不能处于不可见的不一致状态；本变更必须先完成同步状态和协调器，再扩展一次性/时区能力。
- **时区风险**：`LocalDateTime` 本身不含时区；转换必须始终同时携带 `ZoneId`，DST 跳变需要测试。
- **日志泄露风险**：当前参数和结果会写日志；开放 API、Header/Body 和流程变量加入后风险进一步扩大。
- **Flowable 语义风险**：现有流程服务偏审批业务，技术流程不得复用自动部署草稿、审批业务关联或用户任务状态回写等不适合的默认路径。
- **兼容风险**：现有任务状态值、JobKey、Cron 和 QRTZ 记录必须保持；禁止按需求文档反转 0/1 状态。
- **数据库容量风险**：执行生命周期、节点历史和开放 API 会显著增加日志量；先建立索引和清理策略，再以真实规模决定分区。
- **分布式模式风险**：远程路由服务发现仍未完成，本变更不能承诺仅靠当前 `executor_service` 即可稳定支持微服务执行。

## 10. 兼容、迁移与回滚

### 10.1 存量迁移

- 所有未删除 `sys_job_config` 在 Phase 0/1 回填：`schedule_type=CRON`、实际兼容时区、`invoke_mode=SINGLE`、`sync_status=PENDING`；Phase 2 再回填 `concurrent_policy=ALLOW`和`misfire_policy=DO_NOTHING`。
- 时区回填前必须核对现网 JVM/Quartz 默认时区；若存量环境不是 `Asia/Shanghai`，应按原实际时区回填，禁止因统一默认值改变既有任务触发时刻。
- 保留 `status=0/1` 语义，不改写现有启停状态。
- 现有日志 `status=1` 保持成功、`status=0` 保持失败；历史记录的触发来源统一标记为 `UNKNOWN`，不得伪造精确来源。
- 注解任务与手工任务 JobKey 冲突时以已存在数据库配置为事实源并记录冲突告警；逻辑删除过的注解任务默认不自动复活，只有管理员显式恢复才能重新注册。
- 迁移完成后由协调器只做差异同步，不批量删除正常 QRTZ 任务。

### 10.2 API 兼容

- 保留现有管理接口路径，响应从 Entity 切换为 VO 时保持 `AiCrudPage` 所需分页字段。
- `POST /{id}/cron` 可在 Phase 1 标记废弃并内部转发统一更新 Service，前端不再作为主编辑路径；删除需另立兼容窗口。
- 开放 API 使用新路径，不复用管理端或内部执行器路径。

### 10.3 回滚

- Phase 0/1 代码回退前必须确认数据库中新状态 `COMPLETED`、一次性任务和新增字段是否已产生数据；旧代码不识别一次性任务，回退不能只替换应用包。
- Flyway 已执行脚本不回改；需要回退时新增后续修复脚本或先停用新增能力。
- 新增字段以兼容旧查询为原则，但把 `cron_expression` 改为可空后，旧调度代码读取一次性任务会失败；回退前必须停止/迁移一次性任务。
- Phase 3 Token 可统一吊销，开放 API 路由可通过配置关闭。
- Phase 4 技术流程可以停止新启动，不删除已发布模型和历史流程实例。

## 11. 技术决策

- **TD-01**：继续扩展 `sys_job_config/sys_job_log`，不建立平行 `mes_job` 控制面。
- **TD-02**：Quartz 表达式是唯一持久化 Cron 口径；0～6 星期只属于前端向导内部状态。
- **TD-03**：保留现有 `0=停止、1=运行`，新增 `2=已结束`，不按外部需求文档反转状态。
- **TD-04**：数据库是任务期望配置事实源，Quartz 是运行态；通过同步状态和协调器收敛，不依赖跨数据库/Quartz 的伪分布式事务。
- **TD-05**：任务 JobKey 在本阶段创建后不可编辑；如未来需要可编辑名称，应另增稳定 `job_code`，不在更新时隐式重命名 Quartz Job。
- **TD-06**：复杂配置使用全屏任务工作台，列表继续复用现有任务总览；CronBuilder 抽为公共组件。
- **TD-07**：管理端权限使用资源权限，开放 API 使用独立 Bearer Token + Scope；内部执行器端点不对外开放。
- **TD-08**：Token 只保存 Hash，复用 capability identity 的安全模式但不直接复用 MCP audience 和数据表。
- **TD-09**：失败告警复用消息中心；Webhook 在出站安全能力完成前保持关闭。
- **TD-10**：技术流程复用现有 Flowable 模型和真实 BPMN 设计器，任务插件通过 SPI 适配本地/远程流程服务。
- **TD-11**：任意 Script 节点不进入本次默认范围；安全节点通过受控 Delegate/Handler 注册表提供。
- **TD-12**：监控数据从执行记录聚合，`nextFireTime` 优先从 Quartz 运行态读取；没有性能证据前不冗余第二套权威触发时间。

## 12. 待澄清（HARD-GATE）

- [ ] `Q-01 实施范围`：首次 `/apply` 是否确认只做 Phase 0 + Phase 1？推荐：是；Phase 2～4 分别评审后实施。
- [ ] `Q-02 管理边界`：任务中心继续只做平台主库控制面，还是允许普通租户自助创建任务？推荐：本期保持平台控制面；租户自助另立变更，否则需增加租户上下文注入、跨租户调度扫描和资源隔离设计。
- [ ] `Q-03 厂区范围`：多厂区只通过任务分组/任务时区表达，还是需要显式 `factory_id/org_id` 和数据权限？推荐：若 MES 上线确有厂区授权，本期增加 `owner_org_id` 并按当前组织权限过滤。
- [ ] `Q-04 过期一次性任务`：是否确认采用第 4.5 节规则——新增/编辑拒绝过去时间，只有历史迁移允许已结束记录？推荐：确认。
- [ ] `Q-05 一次性失败`：是否确认 Phase 1 单次执行失败后仍进入已结束且不做任务级自动重试，重新安排必须人工显式操作？推荐：确认；任务级重试留到 Phase 2。
- [ ] `Q-06 Misfire 第三策略`：“等待下一次”和“丢弃”在 Cron 场景如何区别？推荐：Phase 2 首期只实现 `FIRE_ONCE_NOW/DO_NOTHING`，没有独立语义前不实现第三个枚举。
- [ ] `Q-07 流程版本`：任务绑定“保存时的已发布版本”还是“每次启动时最新发布版本”？推荐：绑定发布版本，管理员显式升级，避免流程发布静默改变线上任务。
- [ ] `Q-08 技术流程部署`：首个目标环境是 admin 内嵌 Flowable、独立 flow 服务，还是两者都需支持？推荐：SPI 契约同时兼容，首轮只验证实际部署形态。
- [ ] `Q-09 Script 节点`：是否接受本次明确不开放任意脚本？推荐：接受；后续用独立沙箱 Spec 评审。
- [ ] `Q-10 开放 Token`：Phase 3 是否需要用户 Token 和服务账号 Token 两类，还是只支持服务账号？推荐：首期只支持服务账号 Token。
- [ ] `Q-11 日志分区`：是否已有日执行量、单条大小和 90 天容量估算？推荐：没有数据前先索引+清理，不直接改历史表分区。
- [ ] `Q-12 MES 模板`：需求文档列出的 8 类 MES 任务是正式内置数据还是演示模板？推荐：作为可选 seed/demo，不进入 required，避免平台硬编码 MES 业务。

用户已要求先拆分实施任务，因此允许在待澄清项关闭前生成状态为 `draft-blocked` 的 `tasks.md`，用于评审范围、依赖和难度；但待澄清项全部关闭、用户确认首轮 Phase 前，不得将 Spec 状态改为 `apply`，不得执行任务或生成实施阶段 `test-spec.md`。

## 13. 执行日志

| Task | 状态 | 实际改动文件 | 备注 |
|------|------|--------------|------|
| Spec Research | complete | `MES定时任务模块_需求文档.html`、job/flow/message/idempotent 相关源码 | 已完成需求与现状差距分析 |
| Spec Draft | complete | `code-copilot/changes/定时任务优化/spec.md` | 总体方案已起草，等待 HARD-GATE 确认 |
| Task Breakdown | complete | `code-copilot/changes/定时任务优化/tasks.md` | 已按 Phase 0/1 生成 `draft-blocked` 实施任务；门禁未关闭前不可执行 |
| Implementation | pending | — | 未授权进入代码实现 |

## 14. 审查结论

当前已完成提案起草和 Phase 0/1 任务拆分，未进入代码、数据库或运行态修改。Spec 已识别现有任务模块的可复用能力、关键一致性缺陷、需求文档与 Forge 架构差异，并将总体需求拆为四个可独立评审的实施阶段。`tasks.md` 当前为 `draft-blocked`，待用户关闭第 12 节待澄清项后才能执行；测试计划仍需在进入测试阶段后按自动化测试规范增量生成。

## 15. 确认记录（HARD-GATE）

- **确认时间**：待确认
- **确认人**：待确认
- **确认内容**：待确认首次 `/apply` 阶段、管理边界、一次性任务状态、Misfire、流程版本、Script 和开放 Token 范围。
