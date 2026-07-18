# 定时任务调度中心 Phase 0/1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> status: draft-blocked
> change: `定时任务优化`
> spec: `code-copilot/changes/定时任务优化/spec.md`
> scope: Phase 0 + Phase 1 only

**Goal:** 在不建立平行 `mes_job` 控制面的前提下，将现有 Forge Quartz 定时任务升级为支持期望状态同步、Cron/一次性调度、独立时区、完整执行生命周期、资源权限和全屏配置工作台的通用调度中心。

**Architecture:** `sys_job_config` 保存期望状态，Quartz 保存运行态；管理写操作在数据库事务提交后由 `JobScheduleCoordinator` 同步 Quartz，并通过同步状态与定时对账收敛失败。后端使用 DTO/VO、Mapper XML、状态机和执行生命周期服务隔离协议、持久化与调度职责；前端保留任务总览，新增独立工作台和可复用 `CronBuilder`。

**Tech Stack:** Java 17、Spring Boot 3.2、MyBatis-Plus 3.5、Quartz JDBC、Redisson、Sa-Token、Flyway、Vue 3.5、Naive UI、Vitest、Vite 7。

---

## 0. 执行门禁

本文件是可执行级任务草案，但当前不得进入代码、SQL或运行态修改。只有以下条件全部满足，才能把本文件状态改为 `apply-ready` 并开始 Task 1：

- [ ] 用户明确确认首次 `/apply` 只实施 Phase 0 + Phase 1。
- [ ] 用户确认任务中心本期保持平台主库控制面，不开放普通租户自助创建任务。
- [ ] 用户明确多厂区是否需要 `owner_org_id`；若需要，先回写 Spec 的数据模型、权限和本任务清单，再实施迁移。
- [ ] 用户确认新增/编辑一次性任务拒绝过去时间。
- [ ] 用户确认一次性任务计划触发失败后仍进入 `COMPLETED`，Phase 1 不自动重试。
- [ ] 用户确认 Phase 2 Misfire 只保留 `FIRE_ONCE_NOW/DO_NOTHING`，不提前实现第三种无独立语义的策略。
- [ ] 用户确认 Phase 4 默认绑定保存时的已发布流程版本。
- [ ] 用户确认 Phase 4 的实际首验部署形态；SPI 仍需兼容本地聚合和独立 Flow 服务。
- [ ] 用户确认任意 Script/Java/Shell/SQL 执行能力不进入本变更。
- [ ] 用户确认 Phase 3 首期只支持服务账号 Token。
- [ ] 用户确认无容量证据前，日志先采用索引与留存清理，不改 MySQL 分区。
- [ ] 用户确认 8 类 MES 任务作为可选 demo/seed，不进入 required 初始化数据。

门禁关闭时只修改以下文档，不提前创建 `test-spec.md`：

- Modify: `code-copilot/changes/定时任务优化/spec.md` — 将 `status` 改为 `apply`，勾选第 12 节答案并填写第 15 节确认记录。
- Modify: `code-copilot/changes/定时任务优化/tasks.md` — 将 `status` 改为 `apply-ready`，勾选本节确认项。

验证命令：

```bash
git diff --check -- code-copilot/changes/定时任务优化/spec.md code-copilot/changes/定时任务优化/tasks.md
```

预期：退出码为 `0`，且 `rg -n "status: apply|status: apply-ready"` 能分别命中 Spec 和 Tasks。

## 1. 文件职责总图

### 1.1 数据库与后端

| 文件/目录 | 操作 | 单一职责 |
|---|---|---|
| `forge-server/db/migration/V1.0.38__enhance_job_scheduler_phase_1.sql` | Create | Phase 0/1 字段、索引、数据回填、字典、资源权限、隐藏路由和日志导出配置 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/constant/` | Create | 固定数据库状态值和合法状态转换输入；具体文件见 Task 2 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/entity/SysJobConfig.java` | Modify | 映射 Phase 0/1 任务期望状态和同步状态字段 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/entity/SysJobLog.java` | Modify | 映射执行来源、生命周期、任务关联和审计字段 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/dto/` | Create | 管理端任务、日志和调度预览请求协议；具体文件见 Task 3、5、11 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/vo/` | Create | 任务摘要、详情、概览、执行记录和命令结果响应协议；具体文件见 Task 3、5、11 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/mapper/SysJobConfigMapper.java` + XML | Modify/Create | 任务分页、详情、对账候选和同步状态更新 SQL |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/mapper/SysJobLogMapper.java` + XML | Modify | 日志分页、详情、导出和执行生命周期更新 SQL |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/JobScheduleDomainService.java` | Create | Quartz 6 段 Cron、时区、未来时间和一次性时间解析的唯一服务端口径 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/scheduler/JobScheduler.java` | Modify | 构建 CronTrigger/SimpleTrigger、写入 Forge 管理元数据并提供 Quartz 运行态快照 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/JobScheduleCoordinator.java` | Create | 数据库期望状态到 Quartz 运行态的幂等同步与对账 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/JobConfigCommandManager.java` | Create | 任务配置事务写入、状态机和乐观锁边界 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/JobConfigLockManager.java` | Create | 创建锁和任务 ID 锁；Redis 不可用时失败关闭 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/impl/SysJobConfigServiceImpl.java` | Modify | 管理命令编排：加锁、事务写期望状态、提交后同步、返回可见同步结果 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/impl/SysJobLogServiceImpl.java` | Modify | 日志查询、详情、导出数据源和留存清理 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/JobExecutionLifecycleService.java` | Create | `ACCEPTED/RUNNING/SUCCESS/FAILED/SKIPPED` 原记录流转 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/support/JobLogSanitizer.java` | Create | 参数、结果和异常的结构化脱敏与限长 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/registry/JobAutoRegistrar.java` | Modify | 注解任务只登记数据库期望状态，不直接形成第二调度入口 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/loader/JobConfigLoader.java` | Delete | 由统一启动/周期协调器替代失效加载器 |
| `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/controller/` | Modify/Create | DTO/VO 接口、Sa-Token 资源权限和操作审计入口；具体文件见 Task 10、15 |

### 1.2 前端

| 文件/目录 | 操作 | 单一职责 |
|---|---|---|
| `forge-admin-ui/src/api/system/job.js` | Create | 任务、调度预览和执行日志 API |
| `forge-admin-ui/src/components/job/CronBuilder.vue` | Create | 简单/专家 Cron 编辑与服务端预览 |
| `forge-admin-ui/src/components/job/cron-builder.js` | Create | 五类简单规则生成和安全反解析 |
| `forge-admin-ui/src/views/system/job-config/editor.vue` | Create | 新增任务路由入口 |
| `forge-admin-ui/src/views/system/job-config/editor.[id].vue` | Create | 编辑任务路由入口 |
| `forge-admin-ui/src/views/system/job-config/components/JobConfigWorkbench.vue` | Create | Phase 1 全屏任务配置工作台 |
| `forge-admin-ui/src/views/system/job-config.vue` | Modify | 任务总览、筛选、操作、同步状态和概览入口 |
| `forge-admin-ui/src/views/system/job-log-list.vue` | Modify | 日志组合筛选、详情和导出 |

## 2. 实施顺序与依赖

```text
Task 1
  -> Task 2 -> Task 3 -> Task 4 -> Task 5
  -> Task 6 -> Task 7 -> Task 8 -> Task 9 -> Task 10
  -> Task 11 -> Task 12 -> Task 13 -> Task 14
  -> Task 15 -> Task 16 -> Task 17 -> Task 18 -> Task 19
  -> Task 20
```

禁止跳过 Task 1 直接改实体；否则代码会引用数据库不存在的字段。禁止先做前端 Task 16–19 再冻结后端 DTO/VO；否则接口字段会反复返工。

### 2.1 工作量与难度预估

| 任务组 | Tasks | 难度 | 预估工作量 | 主要不确定性 |
|---|---|---|---|---|
| 数据迁移、枚举、DTO/VO、查询和时间领域 | 1–5 | 中～高 | 5–7 人日 | 现网默认时区、真实表结构、Cron 中文描述边界 |
| Quartz 适配、协调器、锁、状态机和配置编排 | 6–10 | 高 | 9–13 人日 | 集群一致性、旧 JobKey 兼容、Redis 失败关闭、ONCE 恢复 |
| 执行日志、生命周期、一次性完成和启动恢复 | 11–15 | 高 | 6–9 人日 | 原记录状态竞争、服务崩溃恢复、日志脱敏与导出 |
| 前端 API、CronBuilder、工作台、总览和日志 | 16–19 | 中～高 | 6–9 人日 | Cron 双向转换、复杂表达式无损降级、权限交互 |
| 全量迁移、构建、浏览器和运行态验收 | 20 | 高 | 2–4 人日 | 开发库、Redis、停机恢复和集群验证环境 |

整体约为 **28–42 人日**。这是单人顺序实施并包含测试、Review 修复和环境联调的规划级估算；若后端调度内核与前端工作台由两人并行，可缩短日历时间，但 Task 1–5 的协议冻结和 Task 15 的后端回归仍是前端联调前置。最高风险任务为 Task 6、7、12、13、20。

---

## Task 1: 建立 Phase 0/1 Flyway 迁移和迁移契约测试

- **目标**: 一次性完成 Phase 0/1 实际读取字段、兼容回填、索引、字典、权限和日志导出配置，不提前创建 Phase 2–4 空字段。
- **需求映射**: `REQ-JOB-P0-03`、`REQ-JOB-P0-10`、`REQ-JOB-P1-01`、`REQ-JOB-P1-08`、`REQ-JOB-P1-12`、`REQ-JOB-P1-13`、`REQ-JOB-P1-14`。
- **涉及文件**:
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/pom.xml` — 增加 `spring-boot-starter-test` 测试依赖；后续 Task 7 再增加缓存依赖。
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/migration/JobPhaseOneMigrationContractTest.java` — 静态核对迁移内容。
  - Create: `forge-server/db/migration/V1.0.38__enhance_job_scheduler_phase_1.sql` — 正式迁移；实施前若 `V1.0.38` 已被占用，停止实施并改用当时最大版本号加一，同时同步更新本文件路径。

- [ ] **Step 1: 添加测试依赖**

在 `pom.xml` 的 `<dependencies>` 中加入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 先写迁移契约失败测试**

测试类至少包含下列测试方法：

```java
@Test void shouldAddOnlyPhaseZeroAndOneJobColumns();
@Test void shouldPreserveLegacyStatusAndBackfillCompatibilityValues();
@Test void shouldCreatePhaseOneDictionariesWithTenantOne();
@Test void shouldCreateJobPermissionsAndHiddenEditorRoutes();
@Test void shouldCreateJobLogExportConfiguration();
@Test void shouldUseInformationSchemaAndNotExistsGuards();
@Test void shouldNotCreateDeferredPhaseColumnsOrTables();
```

`shouldNotCreateDeferredPhaseColumnsOrTables` 必须断言 SQL 不包含：

```text
concurrent_policy
misfire_policy
alarm_channels
sys_job_template
sys_job_api_token
sys_outbound_whitelist
flow_model_key
process_instance_id
idempotency_key
```

- [ ] **Step 3: 运行测试并确认失败**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=JobPhaseOneMigrationContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：`FAIL`，原因是迁移文件不存在或缺少约定字段。

- [ ] **Step 4: 编写迁移 SQL**

`sys_job_config` 必须完成以下精确定义：

```text
cron_expression       varchar(100) NULL
schedule_type         varchar(20)  NOT NULL DEFAULT 'CRON'
fire_once_time        datetime     NULL
timezone              varchar(64)  NOT NULL DEFAULT 'Asia/Shanghai'
cron_human_desc       varchar(255) NULL
invoke_mode           varchar(20)  NOT NULL DEFAULT 'SINGLE'
task_type             varchar(64)  NULL
config_source         varchar(20)  NOT NULL DEFAULT 'MANUAL'
protected_flag        tinyint      NOT NULL DEFAULT 0
sync_status           varchar(20)  NOT NULL DEFAULT 'PENDING'
sync_error            varchar(1000) NULL
sync_time             datetime     NULL
sync_retry_count      int          NOT NULL DEFAULT 0
next_sync_time        datetime     NULL
consecutive_failures  int          NOT NULL DEFAULT 0
version               int          NOT NULL DEFAULT 0
create_by             bigint       NULL
create_dept           bigint       NULL
update_by             bigint       NULL
```

`sys_job_log` 必须新增：

```text
job_config_id         bigint       NULL
trigger_type          varchar(20)  NOT NULL DEFAULT 'UNKNOWN'
scheduled_fire_time   datetime     NULL
fire_instance_id      varchar(100) NULL
operator_id           bigint       NULL
create_time           datetime     NULL
update_time           datetime     NULL
```

迁移还必须：

- 将存量任务回填为 `schedule_type='CRON'`、`invoke_mode='SINGLE'`、`sync_status='PENDING'`；时区回填值必须在实施前核对 JVM/Quartz 现网默认时区，不能无证据改变触发时刻。
- 保持任务 `status=0/1` 和日志 `status=0/1` 历史语义；新增任务状态字典值 `2=已结束`，新增执行状态 `2=运行中、3=已跳过、4=已受理`。
- 将存量日志 `trigger_type` 回填为 `UNKNOWN`，按 `(job_name, job_group)` 唯一匹配回填 `job_config_id`；多义或缺失时保持 `NULL`。
- 创建 `idx_job_status_schedule_del(status, schedule_type, del_flag)`、`idx_job_sync_status_del(sync_status, del_flag)`、`idx_job_log_config_trigger(job_config_id, trigger_time)`、`idx_job_log_status_trigger(status, trigger_time)`。
- 保留 `uk_job_name_group_active`，不修改 QRTZ 表。
- 创建 Phase 0/1 字典：`sys_job_schedule_type`、`sys_job_invoke_mode`、`sys_job_trigger_type`、`sys_job_execution_status`、`sys_job_task_type`、`sys_job_sync_status`；所有内置数据 `tenant_id=1`。
- 在 `/system/job-config` 菜单下创建 `system:jobConfig:list/query/add/edit/delete/start/stop/trigger/sync` 和对应 API 资源；兼容接口 `POST /job/config/{id}/cron` 归入 `system:jobConfig:edit`。
- 创建 `/system/job-config/editor`、`/system/job-config/editor/:id` 隐藏路由，并继承拥有 `system:jobConfig:list` 的角色资源。
- 创建 `system:jobLog:list/query/export/clean` 和对应 API 资源。
- 创建 `config_key='sys_job_log_export'` 的导出配置，数据源 Bean 为 `sysJobLogService`，查询方法为 `selectExportList`；导出列不得包含 `jobParam`、完整 `result`、完整 `exceptionMsg`。
- 所有新增列、索引、字典、资源和导出配置均使用 `information_schema` 或 `NOT EXISTS` 防重复保护。

- [ ] **Step 5: 运行迁移契约测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=JobPhaseOneMigrationContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：`PASS`，报告包含 `Tests run: 7, Failures: 0, Errors: 0`。

- [ ] **Step 6: 静态检查 SQL 和版本号**

```bash
rg -n '\$\{' forge-server/db/migration/V1.0.38__enhance_job_scheduler_phase_1.sql
ls -1 forge-server/db/migration | sort -V | tail -5
git diff --check -- forge-server/db/migration/V1.0.38__enhance_job_scheduler_phase_1.sql
```

预期：第一条无输出；`V1.0.38` 是当前最大且唯一版本；`git diff --check` 退出码为 `0`。

- [ ] **Step 7: 原子提交**

```bash
git add forge-server/db/migration/V1.0.38__enhance_job_scheduler_phase_1.sql \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/pom.xml \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/migration/JobPhaseOneMigrationContractTest.java
git commit -m "feat(job): add phase one scheduler migration"
```

## Task 2: 固定任务领域枚举和实体字段

- **目标**: 在代码中固定兼容状态值、调度类型、调用方式、来源和同步状态，实体与 Task 1 数据库字段一一对应。
- **需求映射**: `REQ-JOB-P0-01`、`REQ-JOB-P0-02`、`REQ-JOB-P0-04`、`REQ-JOB-P1-03`、`REQ-JOB-P1-08`、`REQ-JOB-P1-10`、`REQ-JOB-P1-12`。
- **涉及文件**:
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/constant/JobStatus.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/constant/JobScheduleType.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/constant/JobInvokeMode.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/constant/JobSyncStatus.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/constant/JobTriggerType.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/constant/JobExecutionStatus.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/constant/JobConfigSource.java`
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/entity/SysJobConfig.java`
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/entity/SysJobLog.java`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/constant/JobDomainConstantsTest.java`

- [ ] **Step 1: 写状态兼容测试**

```java
@Test void shouldKeepLegacyJobStatusValues() {
    assertEquals(0, JobStatus.STOPPED.getValue());
    assertEquals(1, JobStatus.RUNNING.getValue());
    assertEquals(2, JobStatus.COMPLETED.getValue());
}

@Test void shouldKeepLegacyExecutionStatusValues() {
    assertEquals(0, JobExecutionStatus.FAILED.getValue());
    assertEquals(1, JobExecutionStatus.SUCCESS.getValue());
    assertEquals(2, JobExecutionStatus.RUNNING.getValue());
    assertEquals(3, JobExecutionStatus.SKIPPED.getValue());
    assertEquals(4, JobExecutionStatus.ACCEPTED.getValue());
}
```

同时断言字符串枚举精确包含：

```text
JobScheduleType: CRON, ONCE
JobInvokeMode: SINGLE, FLOW
JobSyncStatus: PENDING, SYNCED, FAILED, DELETE_PENDING
JobTriggerType: UNKNOWN, SCHEDULED, MANUAL, API
JobConfigSource: MANUAL, ANNOTATION, SYSTEM
```

- [ ] **Step 2: 运行测试并确认编译失败**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=JobDomainConstantsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：`FAIL`，枚举类尚不存在。

- [ ] **Step 3: 实现枚举统一接口**

整数枚举提供：

```java
public int getValue();
public static JobStatus fromValue(Integer value);
```

字符串枚举提供：

```java
public String getCode();
public static JobScheduleType fromCode(String code);
```

所有 `fromValue/fromCode` 对 `null` 或未知值抛出 Forge 业务异常，不做静默默认。

- [ ] **Step 4: 扩展实体**

`SysJobConfig` 新增 Task 1 的全部 Phase 0/1 字段，并为 `version` 添加：

```java
@Version
private Integer version;
```

`SysJobLog` 新增 Task 1 的全部 Phase 0/1 日志字段；保留显式 `@TableLogic private Integer delFlag`。实体字段类型固定为 `String/Integer/Long/LocalDateTime`，不将数据库字符串列直接映射为枚举。

- [ ] **Step 5: 运行测试并确认通过**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=JobDomainConstantsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：`PASS`，状态值与 Spec 完全一致。

- [ ] **Step 6: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/constant \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/entity \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/constant/JobDomainConstantsTest.java
git commit -m "feat(job): define scheduler domain states"
```

## Task 3: 定义任务管理 DTO、VO 和字段校验器

- **目标**: 管理端不再直接接收或返回 Entity，并在进入持久化前完成执行目标、JSON、状态、调度类型和时区校验。
- **需求映射**: `REQ-JOB-P0-01`、`REQ-JOB-P0-02`、`REQ-JOB-P0-04`、`REQ-JOB-P1-01`、`REQ-JOB-P1-03`、`REQ-JOB-P1-11`。
- **涉及文件**:
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/dto/JobConfigQuery.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/dto/JobConfigSaveRequest.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/vo/JobConfigVO.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/vo/JobOverviewVO.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/vo/JobMutationResultVO.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/vo/JobTriggerResultVO.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/support/JobConfigValidator.java`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/support/JobConfigValidatorTest.java`

- [ ] **Step 1: 写校验器失败测试**

至少覆盖：

```java
@Test void cronTaskShouldRequireSixFieldCronAndRejectOnceTime();
@Test void onceTaskShouldRequireFutureLocalTimeAndRejectCron();
@Test void shouldRejectInvalidIanaTimezone();
@Test void shouldRejectInvalidJsonJobParam();
@Test void shouldRequireBeanFieldsForBeanMode();
@Test void shouldRequireHandlerForHandlerMode();
@Test void shouldRequireServiceAndHandlerForRpcMode();
@Test void shouldRejectEnabledFlowInvokeModeInPhaseOne();
@Test void shouldRejectChangedJobNameOrGroupOnUpdate();
```

- [ ] **Step 2: 定义请求和响应字段**

关键协议固定为：

```java
public class JobConfigQuery {
    private String jobName;
    private String jobGroup;
    private Integer status;
    private String executeMode;
    private String scheduleType;
    private String taskType;
}

public class JobConfigSaveRequest {
    private Long id;
    private String jobName;
    private String jobGroup;
    private String description;
    private String executorBean;
    private String executorMethod;
    private String executorHandler;
    private String executorService;
    private String jobParam;
    private Integer status;
    private String executeMode;
    private String scheduleType;
    private String cronExpression;
    private LocalDateTime fireOnceTime;
    private String timezone;
    private String invokeMode;
    private String taskType;
    private Integer version;
}
```

`JobConfigVO` 除可编辑字段外返回 `cronHumanDesc/configSource/protectedFlag/syncStatus/syncError/syncTime/syncRetryCount/consecutiveFailures/version/lastStartTime/lastExecutionStatus/nextFireTime`。`JobMutationResultVO` 固定返回 `id/status/syncStatus/syncError/version`；`JobTriggerResultVO` 固定返回 `executionId/status`。

- [ ] **Step 3: 实现校验器签名**

```java
public void validateCreate(JobConfigSaveRequest request, Clock clock);
public void validateUpdate(JobConfigSaveRequest request, SysJobConfig current, Clock clock);
public void validateExecutionTarget(JobConfigSaveRequest request);
public ZoneId requireZoneId(String timezone);
public void requireValidJsonOrBlank(String jobParam);
```

校验器使用 Jackson `ObjectMapper.readTree` 验证 JSON；`invokeMode=FLOW` 时只有 `status=STOPPED` 可以保存占位协议，不能启用或触发。

- [ ] **Step 4: 运行测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=JobConfigValidatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：全部校验用例通过。

- [ ] **Step 5: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/dto \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/vo \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/support/JobConfigValidator.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/support/JobConfigValidatorTest.java
git commit -m "feat(job): add scheduler management contracts"
```

## Task 4: 将任务配置查询迁移到 Mapper XML

- **目标**: 删除 Service 中的 `LambdaQueryWrapper` 查询路径，显式过滤 `del_flag=0`，并形成任务摘要、详情、概览和协调器候选查询。
- **需求映射**: `REQ-JOB-P0-03`、`REQ-JOB-P1-01`、`REQ-JOB-P1-11`。
- **涉及文件**:
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/mapper/SysJobConfigMapper.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/resources/mapper/SysJobConfigMapper.xml`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/mapper/SysJobConfigMapperContractTest.java`

- [ ] **Step 1: 写 Mapper XML 契约失败测试**

测试读取 XML 并断言存在以下 statement 和条件：

```text
selectJobPage
selectJobDetail
selectJobOverview
selectByJobKey
selectReconcileCandidates
updateSyncState
markOnceCompleted
logicalDeleteAfterQuartzRemoval
AND c.del_flag = 0
```

同时断言分页 SQL 支持 `jobName/jobGroup/status/executeMode/scheduleType/taskType`，日志摘要关联只返回最近一次执行，不产生一对多重复行。

- [ ] **Step 2: 定义 Mapper 签名**

```java
Page<JobConfigVO> selectJobPage(Page<JobConfigVO> page,
                                @Param("query") JobConfigQuery query);

JobConfigVO selectJobDetail(@Param("id") Long id);

JobOverviewVO selectJobOverview(@Param("id") Long id);

SysJobConfig selectByJobKey(@Param("jobName") String jobName,
                            @Param("jobGroup") String jobGroup);

List<SysJobConfig> selectReconcileCandidates(@Param("now") LocalDateTime now);

int updateSyncState(@Param("id") Long id,
                    @Param("version") Integer version,
                    @Param("syncStatus") String syncStatus,
                    @Param("syncError") String syncError,
                    @Param("syncTime") LocalDateTime syncTime,
                    @Param("syncRetryCount") Integer syncRetryCount,
                    @Param("nextSyncTime") LocalDateTime nextSyncTime);

int markOnceCompleted(@Param("id") Long id,
                      @Param("version") Integer version,
                      @Param("updateTime") LocalDateTime updateTime);

int logicalDeleteAfterQuartzRemoval(@Param("id") Long id,
                                    @Param("version") Integer version,
                                    @Param("updateTime") LocalDateTime updateTime);
```

- [ ] **Step 3: 实现 XML**

所有自定义查询显式包含 `del_flag=0`。`selectReconcileCandidates` 只选择：

```sql
sync_status IN ('PENDING', 'FAILED', 'DELETE_PENDING')
AND (next_sync_time IS NULL OR next_sync_time <= #{now})
```

`updateSyncState/markOnceCompleted/logicalDeleteAfterQuartzRemoval` 均使用 `WHERE id=#{id} AND version=#{version} AND del_flag=0`，成功更新时 `version=version+1`，防止旧同步结果覆盖新配置。

- [ ] **Step 4: 运行测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=SysJobConfigMapperContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：`PASS`，且源码扫描不再要求 Service 构造任务查询条件。

- [ ] **Step 5: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/mapper/SysJobConfigMapper.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/resources/mapper/SysJobConfigMapper.xml \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/mapper/SysJobConfigMapperContractTest.java
git commit -m "refactor(job): move config queries to mapper xml"
```

## Task 5: 实现服务端 Cron、时区和一次性时间领域服务

- **目标**: 让保存校验、预览和 Quartz 调度复用同一套解析逻辑，严格支持 Quartz 6 段表达式、IANA 时区和 DST 歧义拒绝。
- **需求映射**: `REQ-JOB-P1-04`、`REQ-JOB-P1-05`、`REQ-JOB-P1-06`、`REQ-JOB-P1-07`、`REQ-JOB-P1-08`、`REQ-JOB-P1-10`。
- **涉及文件**:
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/dto/SchedulePreviewRequest.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/vo/SchedulePreviewVO.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/JobScheduleDomainService.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/support/CronHumanDescriptionFormatter.java`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/service/JobScheduleDomainServiceTest.java`

- [ ] **Step 1: 写固定时间失败测试**

测试固定 `baseTime=2026-07-17T10:07:30+08:00`、`timezone=Asia/Shanghai`，断言：

```text
0 0/5 * * * ? -> 2026-07-17T10:10:00+08:00
0 15 * * * ?  -> 2026-07-17T10:15:00+08:00
0 0 8 * * ?   -> 2026-07-18T08:00:00+08:00
```

另覆盖：非法表达式、7 段表达式、`UTC`、DST 时区、排他下界、每月 31 日跳月、DST 不存在时间和重复时间。

- [ ] **Step 2: 定义服务签名**

```java
public SchedulePreviewVO preview(SchedulePreviewRequest request);
public String normalizeCron(String cronExpression);
public List<OffsetDateTime> nextFireTimes(String cronExpression,
                                          ZoneId zoneId,
                                          Instant exclusiveBaseTime,
                                          int count);
public Instant resolveOnceFireInstant(LocalDateTime localDateTime, ZoneId zoneId);
public String describe(String normalizedCron, ZoneId zoneId);
```

`SchedulePreviewRequest.baseTime` 使用 `OffsetDateTime`；`SchedulePreviewVO.nextFireTimes` 使用 `List<OffsetDateTime>`，固定返回 5 条。

- [ ] **Step 3: 实现最小领域逻辑**

- `normalizeCron` 对空白归一化后按空格切分，段数不是 6 立即拒绝，再使用 `CronExpression.isValidExpression` 权威校验。
- `nextFireTimes` 使用 Quartz `CronExpression` 并设置 `TimeZone.getTimeZone(zoneId)`；每次结果必须严格晚于基准时间。
- `resolveOnceFireInstant` 读取 `ZoneRules.getValidOffsets(localDateTime)`；offset 数量不是 1 时拒绝。
- 中文描述器对五类向导标准表达式和常用快捷项生成业务描述；其它合法表达式返回“自定义周期”，不尝试语义化简。

- [ ] **Step 4: 运行测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=JobScheduleDomainServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：固定时间、时区和 DST 用例全部通过。

- [ ] **Step 5: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/dto/SchedulePreviewRequest.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/vo/SchedulePreviewVO.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/JobScheduleDomainService.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/support/CronHumanDescriptionFormatter.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/service/JobScheduleDomainServiceTest.java
git commit -m "feat(job): add authoritative schedule preview"
```

## Task 6: 重构 Quartz 适配器，支持 Cron、ONCE、时区和管理元数据

- **目标**: 用抛异常的幂等调度 API 替换静默 `boolean`，并让 CronTrigger 与 SimpleTrigger 都可被协调器检查和恢复。
- **需求映射**: `REQ-JOB-P0-05`、`REQ-JOB-P0-06`、`REQ-JOB-P1-08`～`REQ-JOB-P1-10`。
- **涉及文件**:
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/model/JobConfig.java`
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/scheduler/JobScheduler.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/scheduler/JobRuntimeSnapshot.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/scheduler/JobScheduleException.java`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/scheduler/JobSchedulerTest.java`

- [ ] **Step 1: 写 Quartz Mock 失败测试**

覆盖：

```java
@Test void cronTriggerShouldUseConfiguredTimezoneAndDoNothingMisfire();
@Test void onceTriggerShouldFireOnceAndUseFireNowMisfire();
@Test void jobDataShouldContainForgeManagedIdAndVersion();
@Test void stoppedTaskShouldBePausedAfterUpsert();
@Test void upsertShouldReplaceChangedManagedJob();
@Test void shouldRejectDeletionOfUnmanagedQuartzJob();
@Test void manualTriggerShouldCarryExecutionContextWithoutChangingPlanTrigger();
```

- [ ] **Step 2: 扩展运行模型**

`JobConfig` 增加：

```java
private Long id;
private Integer version;
private String scheduleType;
private LocalDateTime fireOnceTime;
private String timezone;
private String invokeMode;
private String configSource;
private Integer protectedFlag;
```

`JobRuntimeSnapshot` 固定包含 `exists/forgeManaged/jobConfigId/configVersion/paused/scheduleType/nextFireTime/triggerCount`。

- [ ] **Step 3: 替换公开方法签名**

```java
public void upsertJob(JobConfig jobConfig);
public void deleteManagedJob(JobConfig jobConfig);
public void pauseManagedJob(JobConfig jobConfig);
public void resumeManagedJob(JobConfig jobConfig);
public void triggerJob(JobConfig jobConfig,
                       Long executionId,
                       JobTriggerType triggerType,
                       Long operatorId);
public JobRuntimeSnapshot inspect(JobConfig jobConfig);
public List<JobRuntimeSnapshot> listManagedJobs();
```

删除或废弃原 `addJob/updateJob/updateCron/exists` 的 `boolean` 语义；异常必须包含安全摘要，不把完整 JobDataMap 输出到日志。

- [ ] **Step 4: 构建 Trigger**

- CRON：使用 `CronScheduleBuilder.cronSchedule(normalizedCron).inTimeZone(timeZone).withMisfireHandlingInstructionDoNothing()`。
- ONCE：使用 `SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0).withMisfireHandlingInstructionFireNow()`，触发时间由 `JobScheduleDomainService.resolveOnceFireInstant` 生成。
- ONCE 的 `JobDetail` 必须保持 durable；SimpleTrigger 消耗后保留 `JobDetail + triggerCount=0` 作为“计划触发已被 Quartz 消耗”的运行态证据，协调器不得把该状态重新创建为第二个计划 Trigger。
- JobDataMap 固定写入 `forgeManaged=true`、`jobConfigId`、`configVersion`、执行目标和 `plannedTrigger=true`。
- 手动执行使用临时 JobDataMap 写入 `executionId`、`triggerType=MANUAL`、`operatorId`、`plannedTrigger=false`；不修改持久计划 Trigger。

- [ ] **Step 5: 运行测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=JobSchedulerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：全部 Quartz 构建和保护用例通过。

- [ ] **Step 6: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/model/JobConfig.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/scheduler \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/scheduler/JobSchedulerTest.java
git commit -m "refactor(job): support managed cron and once triggers"
```

## Task 7: 实现调度写锁、同步状态和对账协调器

- **目标**: 数据库成为期望状态事实源，所有调度写操作按任务加锁，失败可见并按 1/5/30 分钟退避重试。
- **需求映射**: `REQ-JOB-P0-05`～`REQ-JOB-P0-08`。
- **涉及文件**:
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/pom.xml` — 增加 `forge-starter-cache`。
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/JobConfigLockManager.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/JobScheduleCoordinator.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/model/JobSyncResult.java`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/manager/JobScheduleCoordinatorTest.java`

- [ ] **Step 1: 写协调器失败测试**

覆盖：

```java
@Test void missingQuartzJobShouldBeCreatedFromRunningDesiredState();
@Test void stoppedDesiredStateShouldCreateThenPauseJob();
@Test void changedVersionShouldReplaceManagedJob();
@Test void deletePendingShouldDeleteQuartzBeforeLogicalDelete();
@Test void unmanagedOrphanShouldNeverBeDeleted();
@Test void staleVersionResultShouldNotOverwriteNewConfig();
@Test void syncFailureShouldBecomeFailedWithBackoff();
@Test void redisUnavailableShouldFailClosedForManagementWrite();
```

- [ ] **Step 2: 增加缓存依赖并定义锁 API**

```java
public <T> T withCreateLock(String jobName,
                            String jobGroup,
                            Supplier<T> action);

public <T> T withConfigLock(Long jobConfigId,
                            Supplier<T> action);
```

锁名固定为：

```text
forge:job:config:create:{sha256(jobName + "\n" + jobGroup)}
forge:job:config:id:{jobConfigId}
```

使用 `ObjectProvider<RedissonClient>`；管理写操作无法取得 RedissonClient 或在配置的等待时间内无法取得锁时抛业务异常，已有 Quartz 执行不受影响。

- [ ] **Step 3: 实现协调器签名**

```java
public JobSyncResult synchronize(Long jobConfigId, Integer expectedVersion);
public JobSyncResult retryNow(Long jobConfigId);
public int reconcilePending(LocalDateTime now);
public int reconcileAll();
```

同步规则：

```text
RUNNING(CRON) + 不存在/版本不一致 -> upsert + resume -> SYNCED
RUNNING(ONCE) + JobDetail 存在且 triggerCount=0 -> 标记 COMPLETED，不重新创建 Trigger
RUNNING(ONCE) + JobDetail/Trigger 从未成功同步或仍存在差异 -> upsert -> SYNCED
STOPPED + 不存在/版本不一致 -> upsert + pause -> SYNCED
COMPLETED -> deleteManagedJob，删除残余 JobDetail/Trigger，历史执行记录继续保留
DELETE_PENDING -> 只删除 forgeManaged 且 jobConfigId 匹配的 Quartz Job；成功后逻辑删除配置
异常 -> FAILED + 安全摘要 + retryCount + nextSyncTime
```

退避精确为：第 1 次 `+1m`，第 2 次 `+5m`，第 3 次 `+30m`，第 4 次及以后 `+30m`。

- [ ] **Step 4: 运行测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=JobScheduleCoordinatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：同步、版本保护、孤儿保护和失败关闭用例全部通过。

- [ ] **Step 5: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/pom.xml \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/JobConfigLockManager.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/JobScheduleCoordinator.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/model/JobSyncResult.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/manager/JobScheduleCoordinatorTest.java
git commit -m "feat(job): reconcile desired scheduler state"
```

## Task 8: 实现任务状态机和事务命令管理器

- **目标**: 禁止 Controller/普通 Service 直接修改状态，冻结 JobKey，并用独立事务组件写入期望状态。
- **需求映射**: `REQ-JOB-P0-02`、`REQ-JOB-P0-04`、`REQ-JOB-P0-05`、`REQ-JOB-P1-08`、`REQ-JOB-P1-09`、`REQ-JOB-P1-14`。
- **涉及文件**:
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/support/JobStatusMachine.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/JobConfigCommandManager.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/model/JobSyncCommand.java`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/manager/JobConfigCommandManagerTest.java`

- [ ] **Step 1: 写状态机失败测试**

覆盖：

```java
@Test void stoppedCanStartButCompletedCannotStartOldOnceSchedule();
@Test void runningCanStop();
@Test void manualTriggerShouldNotChangeTaskStatus();
@Test void plannedOnceCompletionShouldBecomeCompletedOnSuccessOrFailure();
@Test void updateShouldRejectChangedJobKey();
@Test void deleteShouldFirstBecomeDeletePending();
@Test void staleVersionShouldFailOptimisticUpdate();
```

- [ ] **Step 2: 定义状态机签名**

```java
public JobStatus requireStartTransition(SysJobConfig current);
public JobStatus requireStopTransition(SysJobConfig current);
public JobStatus requirePlannedOnceCompletion(SysJobConfig current);
public void requireEditable(SysJobConfig current, JobConfigSaveRequest request);
public void requireTriggerable(SysJobConfig current);
public void requireDeletable(SysJobConfig current);
```

- [ ] **Step 3: 定义事务命令签名**

每个公开方法使用 `@Transactional(rollbackFor = Exception.class)`：

```java
public JobSyncCommand createDesiredState(JobConfigSaveRequest request);
public JobSyncCommand updateDesiredState(JobConfigSaveRequest request);
public JobSyncCommand changeStatus(Long id, Integer expectedVersion, JobStatus target);
public JobSyncCommand markDeletePending(Long id, Integer expectedVersion);
public JobSyncCommand markPlannedOnceCompleted(Long id, Integer expectedVersion);
```

`JobSyncCommand` 固定包含 `jobConfigId/configVersion/operation`。创建和修改必须写 `sync_status=PENDING`、清空 `sync_error`、`next_sync_time=now`；不能在事务中直接调用 Quartz。

- [ ] **Step 4: 运行测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=JobConfigCommandManagerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：所有状态转换、不可变 JobKey 和乐观锁用例通过。

- [ ] **Step 5: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/support/JobStatusMachine.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/JobConfigCommandManager.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/model/JobSyncCommand.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/manager/JobConfigCommandManagerTest.java
git commit -m "feat(job): enforce scheduler state machine"
```

## Task 9: 重构任务配置 Service 编排

- **目标**: 管理命令按“锁 -> 事务写期望状态 -> 提交后同步 Quartz -> 返回同步结果”执行，查询返回 VO。
- **需求映射**: `REQ-JOB-P0-01`、`REQ-JOB-P0-05`、`REQ-JOB-P0-07`、`REQ-JOB-P0-10`、`REQ-JOB-P1-01`、`REQ-JOB-P1-11`。
- **涉及文件**:
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/ISysJobConfigService.java`
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/impl/SysJobConfigServiceImpl.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/JobExecutionLifecycleService.java` — 本任务先实现手动触发的 `ACCEPTED` 和提交失败闭环，Task 12 再扩展完整生命周期。
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/support/JobExecutionTargetPolicy.java`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/service/SysJobConfigServiceImplTest.java`

- [ ] **Step 1: 写编排失败测试**

覆盖：

```java
@Test void createShouldPersistBeforeSynchronizingQuartz();
@Test void syncFailureShouldReturnFailedStateInsteadOfFalseSuccess();
@Test void updateShouldUseConfigLockAndExpectedVersion();
@Test void protectedTaskShouldRejectExecutionTargetChange();
@Test void nonPrivilegedManagerShouldNotChangeBeanTarget();
@Test void deleteShouldRemainVisibleWhenQuartzRemovalFails();
@Test void manualTriggerShouldReturnStableExecutionId();
```

- [ ] **Step 2: 替换 Service 接口**

```java
Page<JobConfigVO> selectJobPage(Page<JobConfigVO> page, JobConfigQuery query);
JobConfigVO getDetail(Long id);
JobOverviewVO getOverview(Long id);
JobMutationResultVO addJob(JobConfigSaveRequest request);
JobMutationResultVO updateJob(JobConfigSaveRequest request);
JobMutationResultVO deleteJob(Long id, Integer version);
JobMutationResultVO startJob(Long id, Integer version);
JobMutationResultVO stopJob(Long id, Integer version);
JobTriggerResultVO triggerJob(Long id);
JobMutationResultVO retrySync(Long id);
```

保留旧 `updateCron(Long, String)` 作为 `@Deprecated` 兼容方法，只允许把现有 CRON 任务转发为统一 `updateJob`；不得成为新的前端主路径。

- [ ] **Step 3: 实现二次目标策略**

```java
public void validateCreateTarget(JobConfigSaveRequest request);
public void validateUpdateTarget(SysJobConfig current, JobConfigSaveRequest request);
public void validateTriggerTarget(SysJobConfig current);
```

保护规则：`protected_flag=1` 时禁止修改 `executeMode/executorBean/executorMethod/executorHandler/executorService/invokeMode`；拥有普通编辑权限不等于可以任意指定 Spring Bean，Service 必须要求平台管理员或专用高权限资源才能更换执行目标。

- [ ] **Step 4: 实现编排**

- 创建：创建锁内调用 `createDesiredState`，获得 ID 后使用任务 ID 锁同步。
- 更新/启停/删除/重试：任务 ID 锁内写期望状态并调用协调器。
- 同步失败不回滚已提交期望状态，返回 `syncStatus=FAILED` 和安全摘要；页面能够继续重试。
- 查询只调用 Mapper XML，不构建 `LambdaQueryWrapper`。
- 手动触发先调用 `JobExecutionLifecycleService.acceptManual(job, operatorId)` 创建 `ACCEPTED` 记录，再把执行 ID 传给 `JobScheduler.triggerJob`；Quartz 提交异常时调用 `markSubmissionFailed(executionId, error)`，不得遗留长期 `ACCEPTED`。

本任务中生命周期服务先提供可独立编译的最小签名：

```java
public Long acceptManual(SysJobConfig job, Long operatorId);
public void markSubmissionFailed(Long executionId, Throwable error);
```

最小实现使用 MyBatis-Plus 内置 `insert/updateById` 创建 `triggerType=MANUAL,status=ACCEPTED` 记录并闭合提交失败；Task 12 在日志 XML 就绪后改为带 `expectedStatus` 的原子状态更新。该过渡实现不新增自定义 Wrapper 或 Service 查询。

- [ ] **Step 5: 运行测试并扫描查询违规**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=SysJobConfigServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
rg -n 'LambdaQueryWrapper' forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service
```

预期：测试通过；第二条无输出。

- [ ] **Step 6: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/support/JobExecutionTargetPolicy.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/service/SysJobConfigServiceImplTest.java
git commit -m "refactor(job): orchestrate desired state commands"
```

## Task 10: 改造管理 Controller、预览接口和资源权限

- **目标**: 移除统一管理员旁路，按操作使用 Sa-Token 权限，并切换 DTO/VO 协议。
- **需求映射**: `REQ-JOB-P0-01`、`REQ-JOB-P0-10`、`REQ-JOB-P1-07`、`REQ-JOB-P1-11`。
- **涉及文件**:
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/pom.xml` — 显式增加认证与操作日志依赖。
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/controller/JobConfigController.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/controller/JobScheduleController.java`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/controller/JobControllerContractTest.java`

- [ ] **Step 1: 写 Controller 协议失败测试**

通过反射或 MockMvc 断言：

```text
GET    /job/config/page             system:jobConfig:list
GET    /job/config/{id}             system:jobConfig:query
GET    /job/config/{id}/overview    system:jobConfig:query
POST   /job/config                  system:jobConfig:add
PUT    /job/config                  system:jobConfig:edit
DELETE /job/config/{id}             system:jobConfig:delete
POST   /job/config/{id}/start       system:jobConfig:start
POST   /job/config/{id}/stop        system:jobConfig:stop
POST   /job/config/{id}/trigger     system:jobConfig:trigger
POST   /job/config/{id}/sync        system:jobConfig:sync
POST   /job/config/{id}/cron        system:jobConfig:edit（兼容接口）
POST   /job/schedule/preview        system:jobConfig:query
```

同时断言类上不存在 `@ApiPermissionIgnore`，不存在 `assertPlatformAdmin()`。

- [ ] **Step 2: 替换方法签名**

```java
public RespInfo<Page<JobConfigVO>> page(PageQuery pageQuery, JobConfigQuery query);
public RespInfo<JobConfigVO> detail(@PathVariable Long id);
public RespInfo<JobOverviewVO> overview(@PathVariable Long id);
public RespInfo<JobMutationResultVO> add(@Valid @RequestBody JobConfigSaveRequest request);
public RespInfo<JobMutationResultVO> update(@Valid @RequestBody JobConfigSaveRequest request);
public RespInfo<JobMutationResultVO> delete(@PathVariable Long id,
                                            @RequestParam Integer version);
public RespInfo<JobTriggerResultVO> trigger(@PathVariable Long id);
@Deprecated
public RespInfo<JobMutationResultVO> updateCron(@PathVariable Long id,
                                                @RequestParam String cronExpression);
public RespInfo<SchedulePreviewVO> preview(@Valid @RequestBody SchedulePreviewRequest request);
```

启停和同步接口同样返回 `JobMutationResultVO`。敏感写接口继续保留 `@ApiDecrypt/@ApiEncrypt`。

- [ ] **Step 3: 增加权限和操作审计**

先在 `pom.xml` 增加：

```xml
<dependency>
    <groupId>com.mdframe.forge</groupId>
    <artifactId>forge-starter-auth</artifactId>
</dependency>
<dependency>
    <groupId>com.mdframe.forge</groupId>
    <artifactId>forge-starter-log</artifactId>
</dependency>
```

每个方法添加与 Step 1 对应的 `@SaCheckPermission`。新增、修改、删除、启停、立即执行和同步重试添加 `@OperationLog`；日志内容不得记录完整 `jobParam` 或执行结果。

- [ ] **Step 4: 运行测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=JobControllerContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：所有路由、权限和协议断言通过。

- [ ] **Step 5: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/controller/JobConfigController.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/controller/JobScheduleController.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/controller/JobControllerContractTest.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/pom.xml
git commit -m "feat(job): secure scheduler management api"
```

## Task 11: 定义执行日志查询、详情和导出协议

- **目标**: 日志支持任务、来源、状态和时间范围组合筛选，详情/导出使用安全 VO，留存清理继续物理删除。
- **需求映射**: `REQ-JOB-P0-03`、`REQ-JOB-P1-12`～`REQ-JOB-P1-14`。
- **涉及文件**:
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/dto/JobLogQuery.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/vo/JobLogVO.java`
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/mapper/SysJobLogMapper.java`
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/resources/mapper/SysJobLogMapper.xml`
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/ISysJobLogService.java`
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/impl/SysJobLogServiceImpl.java`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/mapper/SysJobLogMapperContractTest.java`

- [ ] **Step 1: 写日志 SQL 契约失败测试**

断言 XML 包含：

```text
selectLogPage
selectLogDetail
selectExportList
updateExecutionLifecycle
cleanPhysicalBefore
AND l.del_flag = 0
jobConfigId
triggerType
startTime
endTime
```

`cleanPhysicalBefore` 保持物理 `DELETE`，但必须明确仅供留存清理调用。

- [ ] **Step 2: 定义日志协议**

```java
public class JobLogQuery {
    private Long jobConfigId;
    private String jobName;
    private String jobGroup;
    private Integer status;
    private String triggerType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
```

`JobLogVO` 返回 `id/jobConfigId/jobName/jobGroup/triggerType/status/scheduledFireTime/triggerTime/startTime/endTime/duration/fireInstanceId/operatorId/resultSummary/exceptionSummary/createTime`；列表和导出不返回完整参数、结果或堆栈。

- [ ] **Step 3: 定义 Mapper 与 Service 签名**

```java
Page<JobLogVO> selectLogPage(Page<JobLogVO> page,
                             @Param("query") JobLogQuery query);
JobLogVO selectLogDetail(@Param("id") Long id);
List<JobLogVO> selectExportList(@Param("query") JobLogQuery query);

int updateExecutionLifecycle(@Param("id") Long id,
                             @Param("expectedStatus") Integer expectedStatus,
                             @Param("targetStatus") Integer targetStatus,
                             @Param("startTime") LocalDateTime startTime,
                             @Param("endTime") LocalDateTime endTime,
                             @Param("duration") Long duration,
                             @Param("result") String result,
                             @Param("exceptionMsg") String exceptionMsg,
                             @Param("fireInstanceId") String fireInstanceId,
                             @Param("updateTime") LocalDateTime updateTime);

Page<JobLogVO> selectLogPage(Page<JobLogVO> page, JobLogQuery query);
JobLogVO getDetail(Long id);
List<JobLogVO> selectExportList(JobLogQuery query);
int cleanLog(int days);
```

`SysJobLogServiceImpl` 显式声明 `@Service("sysJobLogService")`，与 Task 1 导出配置的 `data_source_bean` 完全一致。

时间范围规则固定为 `trigger_time >= startTime` 且 `trigger_time < endTime`；`endTime <= startTime` 时 Service 拒绝。

- [ ] **Step 4: 实现 XML 并删除日志 Service 的 LambdaQueryWrapper**

`selectExportList` 与分页查询复用相同 `<sql id="jobLogFilter">`，保证导出筛选一致；`cleanPhysicalBefore` 不增加 `del_flag` 条件，因为它是留存物理清理。

- [ ] **Step 5: 运行测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=SysJobLogMapperContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：日志查询/导出契约通过，Service 中不再出现 `LambdaQueryWrapper`。

- [ ] **Step 6: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/dto/JobLogQuery.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/vo/JobLogVO.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/mapper/SysJobLogMapper.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/resources/mapper/SysJobLogMapper.xml \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/ISysJobLogService.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/impl/SysJobLogServiceImpl.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/mapper/SysJobLogMapperContractTest.java
git commit -m "refactor(job): add execution log query contracts"
```

## Task 12: 实现执行生命周期和日志脱敏

- **目标**: 手动执行形成稳定执行 ID，计划执行创建 RUNNING 记录，并在同一记录上更新最终状态；日志失败不覆盖任务结果。
- **需求映射**: `REQ-JOB-P0-08`、`REQ-JOB-P0-09`、`REQ-JOB-P1-09`、`REQ-JOB-P1-12`、`REQ-JOB-P1-13`。
- **涉及文件**:
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/model/JobExecutionContext.java`
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/JobExecutionLifecycleService.java` — 在 Task 9 的手动受理闭环上增加计划触发、运行中和终态更新。
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/support/JobLogSanitizer.java`
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/monitor/JobMonitor.java`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/service/JobExecutionLifecycleServiceTest.java`

- [ ] **Step 1: 写生命周期失败测试**

覆盖：

```java
@Test void manualTriggerShouldCreateAcceptedBeforeQuartzSubmission();
@Test void executionStartShouldMoveAcceptedToRunning();
@Test void scheduledTriggerShouldCreateRunningDirectly();
@Test void completionShouldUpdateSameRowToSuccessOrFailure();
@Test void quartzSubmissionFailureShouldCloseAcceptedAsFailed();
@Test void logStorageFailureShouldBeCountedAndNotChangeBusinessResult();
@Test void sanitizerShouldMaskTokenPhoneIdCardBankCardAndSecretFields();
@Test void sanitizerShouldLimitResultAndExceptionLength();
```

- [ ] **Step 2: 定义生命周期签名**

```java
public Long acceptManual(SysJobConfig job, Long operatorId);
public Long startScheduled(SysJobConfig job, JobExecutionContext context);
public void markRunning(Long executionId, JobExecutionContext context);
public void markSuccess(Long executionId, String result, LocalDateTime endTime);
public void markFailed(Long executionId, Throwable error, LocalDateTime endTime);
public void markSkipped(Long executionId, String reason, LocalDateTime endTime);
public void markSubmissionFailed(Long executionId, Throwable error);
```

`JobExecutionContext` 固定包含 `executionId/triggerType/scheduledFireTime/fireInstanceId/operatorId/plannedTrigger/startTime`，不包含原始 Token。

- [ ] **Step 3: 实现脱敏器**

```java
public String sanitizeParameter(String value);
public String sanitizeResult(String value);
public String sanitizeException(Throwable throwable);
public String summarize(String value, int maxLength);
```

JSON 对象键名大小写不敏感匹配 `token/authorization/password/secret/apiKey/accessKey/idCard/bankCard/phone/mobile`；无法解析为 JSON 时执行手机号、身份证、银行卡和 Bearer Token 正则脱敏。结果摘要最大 2000 字符，异常摘要最大 4000 字符。

- [ ] **Step 4: 修改存储和监控**

`JobExecutionLifecycleService` 使用 `SysJobLogMapper.insert` 创建初始记录，使用 Task 11 的 `updateExecutionLifecycle` 按期望状态更新原记录；更新行数不是 1 时抛出生命周期冲突异常。`JobMonitor` 不再在 finally 中总是新增一条终态日志，而是调用生命周期服务更新同一记录。保存日志异常记录结构化 ERROR 和计数器，不重新抛出覆盖任务成功/失败结果；Phase 1 不发送站内信、邮件或 Webhook。现有 `IJobLogStorage/DatabaseJobLogStorage` 作为兼容 SPI 保留，不改变第三方实现契约。

- [ ] **Step 5: 运行测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=JobExecutionLifecycleServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：生命周期和脱敏测试全部通过。

- [ ] **Step 6: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/model/JobExecutionContext.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/service/JobExecutionLifecycleService.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/support/JobLogSanitizer.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/monitor/JobMonitor.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/service/JobExecutionLifecycleServiceTest.java
git commit -m "feat(job): track execution lifecycle safely"
```

## Task 13: 改造 Quartz 执行入口和一次性完成语义

- **目标**: 区分计划/手动触发，正确流转执行记录，并仅在一次性计划 Trigger 完成后将任务置为 `COMPLETED`。
- **需求映射**: `REQ-JOB-P1-08`、`REQ-JOB-P1-09`、`REQ-JOB-P1-12`。
- **涉及文件**:
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/scheduler/QuartzJobExecutor.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/OnceJobCompletionManager.java`
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/scheduler/JobScheduler.java`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/scheduler/QuartzJobExecutorTest.java`

- [ ] **Step 1: 写执行入口失败测试**

覆盖：

```java
@Test void scheduledExecutionShouldCreateRunningAndFinishSuccess();
@Test void manualExecutionShouldReuseAcceptedExecutionId();
@Test void failureShouldUpdateSameExecutionRow();
@Test void manualOnceExecutionShouldNotCompletePlan();
@Test void plannedOnceSuccessShouldCompleteTask();
@Test void plannedOnceFailureShouldAlsoCompleteTask();
@Test void recoveredOnceMisfireShouldCompleteOnlyOnce();
```

- [ ] **Step 2: 定义一次性完成签名**

```java
public void completeIfPlannedOnce(Long jobConfigId,
                                  Integer configVersion,
                                  boolean plannedTrigger);
```

只有 `scheduleType=ONCE && plannedTrigger=true` 才调用 `markPlannedOnceCompleted`，随后协调器删除残余计划 Trigger。手动执行和 API 保留值均不得改变任务状态。

- [ ] **Step 3: 重写执行流程**

```text
读取 merged JobDataMap
  -> 构造 JobExecutionContext
  -> 计划触发创建 RUNNING / 手动触发把 ACCEPTED 更新为 RUNNING
  -> 执行业务路由
  -> 更新原记录 SUCCESS 或 FAILED
  -> finally 中处理计划 ONCE 完成
```

不得在 INFO 日志输出完整 `jobParam` 或完整执行结果。

- [ ] **Step 4: 运行测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=QuartzJobExecutorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：计划、手动、失败和 ONCE 状态用例全部通过。

- [ ] **Step 5: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/scheduler/QuartzJobExecutor.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/OnceJobCompletionManager.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/scheduler/JobScheduler.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/scheduler/QuartzJobExecutorTest.java
git commit -m "feat(job): complete planned once executions"
```

## Task 14: 统一注解注册、启动恢复和 60 秒周期对账

- **目标**: 删除失效加载链路，注解任务和数据库任务都进入同一协调器；启动时全量对账，运行期按配置周期恢复失败。
- **需求映射**: `REQ-JOB-P0-06`、`REQ-JOB-P0-09`。
- **涉及文件**:
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/registry/JobAutoRegistrar.java`
  - Delete: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/loader/JobConfigLoader.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/JobReconcileScheduler.java`
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/config/JobProperties.java`
  - Test: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/registry/JobAutoRegistrarTest.java`

- [ ] **Step 1: 写恢复链路失败测试**

覆盖：

```java
@Test void annotationShouldCreateDesiredStateOnlyWhenDatabaseRecordMissing();
@Test void annotationShouldNotOverrideExistingAdminConfiguration();
@Test void softDeletedAnnotationTaskShouldNotBeRevived();
@Test void startupShouldRunFullReconciliationOnce();
@Test void periodicScanShouldRunEveryConfiguredInterval();
@Test void pendingAndFailedTasksShouldBeRetriedWithoutSleep();
```

- [ ] **Step 2: 扩展配置**

```java
private boolean autoLoad = true;
private Duration reconcileInterval = Duration.ofSeconds(60);
private Duration configLockWait = Duration.ofSeconds(3);
private Duration configLockLease = Duration.ofSeconds(30);
```

属性前缀保持 `forge.job`。

- [ ] **Step 3: 改造注解注册**

`JobAutoRegistrar` 查询必须调用 `SysJobConfigMapper.selectByJobKey(jobName, group)`，不存在时写入：

```text
configSource=ANNOTATION
protectedFlag=1
scheduleType=CRON
invokeMode=SINGLE
syncStatus=PENDING
```

注册器不得直接调用 `JobScheduler.addJob`；数据库已有记录时记录安全冲突摘要并保留管理员配置。

- [ ] **Step 4: 创建对账调度器并删除 Loader**

```java
@EventListener(ApplicationReadyEvent.class)
public void reconcileOnStartup();

@Scheduled(fixedDelayString = "${forge.job.reconcile-interval:60000}")
public void reconcilePending();
```

启动全量对账只清理 `forgeManaged=true` 且能与数据库 ID 核对的孤儿任务；周期扫描只处理到期候选。禁止使用 `Thread.sleep`。

- [ ] **Step 5: 运行测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am \
  -Dtest=JobAutoRegistrarTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：注解事实源和恢复链路用例通过。

- [ ] **Step 6: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/registry/JobAutoRegistrar.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/manager/JobReconcileScheduler.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/config/JobProperties.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/registry/JobAutoRegistrarTest.java
git rm forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/loader/JobConfigLoader.java
git commit -m "refactor(job): unify startup scheduler recovery"
```

## Task 15: 完成日志 Controller、导出入口和后端回归测试

- **目标**: 对外形成日志分页、详情、导出、清理协议，并用后端回归测试验证 Phase 0/1 主链路。
- **需求映射**: `REQ-JOB-P0-09`、`REQ-JOB-P0-10`、`REQ-JOB-P1-13`、`REQ-JOB-P1-14`。
- **涉及文件**:
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/pom.xml` — 增加动态 Excel 导出依赖。
  - Modify: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/controller/JobLogController.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/controller/JobLogExportController.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/controller/JobLogControllerContractTest.java`
  - Create: `forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/integration/JobPhaseOneRegressionTest.java`

- [ ] **Step 1: 写 Controller 权限失败测试**

断言：

```text
GET    /job/log/page    system:jobLog:list
GET    /job/log/{id}    system:jobLog:query
POST   /job/log/export  system:jobLog:export
DELETE /job/log/clean   system:jobLog:clean
```

Controller 类上不存在 `@ApiPermissionIgnore` 和 `assertPlatformAdmin()`。

- [ ] **Step 2: 定义入口签名**

先在 `pom.xml` 增加：

```xml
<dependency>
    <groupId>com.mdframe.forge</groupId>
    <artifactId>forge-starter-excel</artifactId>
</dependency>
```

```java
public RespInfo<Page<JobLogVO>> page(PageQuery pageQuery, JobLogQuery query);
public RespInfo<JobLogVO> detail(@PathVariable Long id);
public void export(@RequestBody JobLogQuery query, HttpServletResponse response);
public RespInfo<Integer> clean(@RequestParam(defaultValue = "90") int days);
```

导出 Controller 注入 `DynamicExportEngine`，调用：

```java
dynamicExportEngine.export(response, "sys_job_log_export", queryParams);
```

`queryParams` 只包含 `JobLogQuery` 的筛选字段。

- [ ] **Step 3: 写主链路回归测试**

`JobPhaseOneRegressionTest` 至少覆盖：

```text
CRON 创建 -> PENDING -> SYNCED -> Quartz CronTrigger
ONCE 创建 -> SimpleTrigger -> 计划成功/失败 -> COMPLETED
手动 ONCE -> 任务状态不变
同步失败 -> FAILED -> 手动重试 -> SYNCED
日志 ACCEPTED -> RUNNING -> SUCCESS/FAILED
删除失败保持可见 DELETE_PENDING；重试成功后逻辑删除
```

使用 Mock/内存 Quartz，不依赖开发数据库或 Redis；真实 Flyway、集群和 Redis 验证留到 Task 20。

- [ ] **Step 4: 运行 job 模块全部测试**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am test
```

预期：模块测试全部通过，无 `Failures` 和 `Errors`。

- [ ] **Step 5: 原子提交**

```bash
git add forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/main/java/com/mdframe/forge/plugin/job/controller \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/controller/JobLogControllerContractTest.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/src/test/java/com/mdframe/forge/plugin/job/integration/JobPhaseOneRegressionTest.java \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job/pom.xml
git commit -m "feat(job): expose secured execution log api"
```

## Task 16: 建立前端任务 API 和协议适配

- **目标**: 将任务页面从散落的 URL 调用迁移到单一 API 模块，固定 Phase 1 请求/响应字段。
- **需求映射**: `REQ-JOB-P1-01`、`REQ-JOB-P1-07`、`REQ-JOB-P1-11`～`REQ-JOB-P1-13`。
- **涉及文件**:
  - Create: `forge-admin-ui/src/api/system/job.js`
  - Create: `forge-admin-ui/src/api/system/__tests__/job.spec.js`
  - Create: `forge-admin-ui/src/views/system/job-config/job-options.js`

- [ ] **Step 1: 写 API URL 失败测试**

Mock `@/utils/request` 并断言以下函数的方法和路径：

```javascript
getJobPage(params)
getJobDetail(id)
getJobOverview(id)
createJob(data)
updateJob(data)
deleteJob(id, version)
startJob(id, version)
stopJob(id, version)
triggerJob(id)
retryJobSync(id)
previewJobSchedule(data)
getJobLogPage(params)
getJobLogDetail(id)
exportJobLogs(data)
cleanJobLogs(days)
```

- [ ] **Step 2: 实现 API**

关键实现固定为：

```javascript
export function getJobPage(params) {
  return request.get('/job/config/page', { params })
}

export function previewJobSchedule(data) {
  return request.post('/job/schedule/preview', data)
}

export function exportJobLogs(data) {
  return request.post('/job/log/export', data, { responseType: 'blob' })
}
```

删除、启动、停止请求通过 query 参数传 `version`；立即执行只传任务 ID，不传执行目标。

- [ ] **Step 3: 定义前端选项常量**

`job-options.js` 只定义 CronBuilder 内部模式和星期转换工具；任务状态、调度类型、调用方式、执行来源、执行状态、任务类型、同步状态必须由 `useDict` 获取，禁止硬编码显示标签。

- [ ] **Step 4: 运行测试**

```bash
cd forge-admin-ui
source ~/.nvm/nvm.sh
nvm use v20.19.0
pnpm test -- src/api/system/__tests__/job.spec.js
```

预期：API 方法、路径和参数测试全部通过。

- [ ] **Step 5: 原子提交**

```bash
git add forge-admin-ui/src/api/system/job.js \
  forge-admin-ui/src/api/system/__tests__/job.spec.js \
  forge-admin-ui/src/views/system/job-config/job-options.js
git commit -m "feat(job-ui): add scheduler api client"
```

## Task 17: 创建可复用 CronBuilder

- **目标**: 支持五类简单模式、专家模式、安全双向转换、星期口径转换和服务端未来 5 次预览。
- **需求映射**: `REQ-JOB-P1-04`、`REQ-JOB-P1-05`、`REQ-JOB-P1-06`、`REQ-JOB-P1-07`、`REQ-JOB-P1-10`。
- **涉及文件**:
  - Create: `forge-admin-ui/src/components/job/cron-builder.js`
  - Create: `forge-admin-ui/src/components/job/CronBuilder.vue`
  - Create: `forge-admin-ui/src/components/job/__tests__/cron-builder.spec.js`
  - Create: `forge-admin-ui/src/components/job/__tests__/CronBuilder.spec.js`

- [ ] **Step 1: 写生成/反解析失败测试**

覆盖精确表达式：

```text
每隔 5 分钟 -> 0 0/5 * * * ?
每小时 15 分 -> 0 15 * * * ?
每天 08:30 -> 0 30 8 * * ?
每周 JS=0 08:00 -> 0 0 8 ? * SUN
每月 31 日 23:00 -> 0 0 23 31 * ?
```

复杂表达式 `0 0 8 L * ?` 必须返回 `{ mode: 'EXPERT', expression: original, custom: true }`，原字符串逐字符保留。非法和 7 段表达式不得产生有效提交值。

- [ ] **Step 2: 实现纯函数签名**

```javascript
export function buildSimpleCron(model) {}
export function parseSimpleCron(expression) {}
export function jsWeekdayToQuartz(value) {}
export function quartzWeekdayToJs(value) {}
export function normalizeCronInput(expression) {}
```

简单模式只识别本组件生成的标准形式；不做 Cron 语义化简。

- [ ] **Step 3: 实现组件协议**

```javascript
defineProps({
  modelValue: { type: String, default: '' },
  timezone: { type: String, default: 'Asia/Shanghai' },
  disabled: { type: Boolean, default: false },
})

defineEmits(['update:modelValue', 'preview-change', 'validation-change'])
```

组件切换到专家模式时保留原表达式；切换到简单模式前只有 `parseSimpleCron` 成功才转换。输入变化防抖调用 `previewJobSchedule`，展示服务端 `normalizedCron/humanDescription/timezone/nextFireTimes`。

- [ ] **Step 4: 运行组件测试**

```bash
cd forge-admin-ui
source ~/.nvm/nvm.sh
nvm use v20.19.0
pnpm test -- src/components/job/__tests__/cron-builder.spec.js src/components/job/__tests__/CronBuilder.spec.js
```

预期：生成、反解析、复杂降级、服务端预览和错误提示用例通过。

- [ ] **Step 5: 原子提交**

```bash
git add forge-admin-ui/src/components/job
git commit -m "feat(job-ui): add quartz cron builder"
```

## Task 18: 创建全屏任务配置工作台

- **目标**: 新增/编辑不再使用 AiCrudPage 通用弹窗，Phase 1 只展示已实现配置区域。
- **需求映射**: `REQ-JOB-P1-02`、`REQ-JOB-P1-03`、`REQ-JOB-P1-08`、`REQ-JOB-P1-10`。
- **涉及文件**:
  - Create: `forge-admin-ui/src/views/system/job-config/editor.vue`
  - Create: `forge-admin-ui/src/views/system/job-config/editor.[id].vue`
  - Create: `forge-admin-ui/src/views/system/job-config/components/JobConfigWorkbench.vue`
  - Create: `forge-admin-ui/src/views/system/job-config/job-form.js`
  - Create: `forge-admin-ui/src/views/system/job-config/__tests__/job-form.spec.js`

- [ ] **Step 1: 写表单归一化失败测试**

覆盖：

```javascript
it('CRON 提交时清空 fireOnceTime')
it('ONCE 提交时清空 cronExpression')
it('编辑时保持 jobName/jobGroup 只读')
it('FLOW 占位只能以 STOPPED 保存')
it('时区变化后标记一次性时间需要二次确认')
it('复杂 Cron 原表达式不丢失')
```

- [ ] **Step 2: 实现表单工具**

```javascript
export function createEmptyJobForm() {}
export function detailToJobForm(detail) {}
export function normalizeJobPayload(form) {}
export function requiresOnceTimezoneConfirmation(before, after) {}
export function validatePhaseOneForm(form, previewState) {}
```

默认值固定为 `status=STOPPED`、`scheduleType=CRON`、`timezone=Asia/Shanghai`、`invokeMode=SINGLE`。

- [ ] **Step 3: 创建工作台**

工作台区域固定为：

```text
基本信息
执行配置
调度配置
保存检查
```

不展示 Phase 2 并发/Misfire/告警，不展示 Phase 3 Token/Webhook，不展示 Phase 4 流程节点。执行方式支持 `BEAN/HANDLER/RPC`；`invokeMode=FLOW` 只显示“后续阶段开放”的只读说明，不允许启用保存。

- [ ] **Step 4: 接入路由入口**

`editor.vue` 传递 `jobId=null`，`editor.[id].vue` 从 route params 传递 ID；保存成功后若 `syncStatus=FAILED`，停留在工作台展示错误和“重试同步”，不能直接提示完全成功后返回列表。

- [ ] **Step 5: 运行测试和构建**

```bash
cd forge-admin-ui
source ~/.nvm/nvm.sh
nvm use v20.19.0
pnpm test -- src/views/system/job-config/__tests__/job-form.spec.js
pnpm build
```

预期：表单测试通过，生产构建成功。

- [ ] **Step 6: 原子提交**

```bash
git add forge-admin-ui/src/views/system/job-config
git commit -m "feat(job-ui): add scheduler configuration workbench"
```

## Task 19: 重构任务总览、概览和日志页面

- **目标**: 列表展示调度/同步摘要，操作进入工作台；日志支持完整筛选、详情和当前条件导出。
- **需求映射**: `REQ-JOB-P1-01`、`REQ-JOB-P1-02`、`REQ-JOB-P1-11`～`REQ-JOB-P1-13`。
- **涉及文件**:
  - Modify: `forge-admin-ui/src/views/system/job-config.vue`
  - Modify: `forge-admin-ui/src/views/system/job-log-list.vue`
  - Create: `forge-admin-ui/src/views/system/job-config/components/JobOverviewDrawer.vue`
  - Create: `forge-admin-ui/src/views/system/job-config/__tests__/job-page-actions.spec.js`

- [ ] **Step 1: 写页面动作失败测试**

覆盖：

```javascript
it('新增和编辑跳转到全屏工作台')
it('FAILED/PENDING 同步状态显示重试入口')
it('手动执行展示后端返回 executionId')
it('COMPLETED 一次性任务不显示直接启动')
it('日志弹窗按 jobConfigId 而不是 jobName 过滤')
it('导出提交与当前筛选完全相同的参数')
```

- [ ] **Step 2: 重构任务列表**

列表筛选字段为 `jobName/jobGroup/status/executeMode/scheduleType/taskType`；列固定为：

```text
任务名称、分组、执行方式、调度摘要、时区、状态、同步状态、上次执行、下次执行、操作
```

主操作只保留“编辑、运行一次”；详情、日志、启停、重试同步、删除进入更多菜单。按钮语义色遵循 AGENTS.md：编辑/详情蓝色、启停绿色或黄色、同步重试黄色、删除红色。

- [ ] **Step 3: 实现任务概览**

`JobOverviewDrawer` 调用 `getJobOverview(id)`，分区展示配置摘要、同步状态、上次/下次执行、连续失败数和最近执行记录。`syncError` 只展示后端安全摘要。

- [ ] **Step 4: 重构日志页面**

- Props 改为 `jobConfigId`，仍允许全局页面不传 ID。
- 字典使用 `useDict('sys_job_execution_status', 'sys_job_trigger_type')`。
- 查询参数使用 `startTime/endTime`，结束时间按后端排他上界提交。
- 详情分区展示基本信息、执行信息、结果摘要、异常摘要；不使用超大 Tooltip。
- 导出使用 blob 下载，并从 `Content-Disposition` 获取文件名。

- [ ] **Step 5: 运行测试、Lint 和构建**

```bash
cd forge-admin-ui
source ~/.nvm/nvm.sh
nvm use v20.19.0
pnpm test -- src/views/system/job-config/__tests__/job-page-actions.spec.js
pnpm lint:fix src/views/system/job-config.vue src/views/system/job-log-list.vue src/views/system/job-config src/components/job src/api/system/job.js
pnpm build
```

预期：定向测试通过，Lint 无未修复错误，生产构建成功。

- [ ] **Step 6: 原子提交**

```bash
git add forge-admin-ui/src/views/system/job-config.vue \
  forge-admin-ui/src/views/system/job-log-list.vue \
  forge-admin-ui/src/views/system/job-config/components/JobOverviewDrawer.vue \
  forge-admin-ui/src/views/system/job-config/__tests__/job-page-actions.spec.js
git commit -m "feat(job-ui): upgrade scheduler operations console"
```

## Task 20: 执行 Phase 0/1 全量验证和文档收尾

- **目标**: 按项目自动化测试规范记录迁移、构建、接口、浏览器和运行态验证结果，关闭 Phase 0/1 验收项。
- **需求映射**: Phase 0/1 全部需求和 Spec 8.5、8.6。
- **涉及文件**:
  - Read: `code-copilot/rules/automated-testing-standard.md`
  - Create/Modify: `code-copilot/changes/定时任务优化/test-spec.md` — 仅在门禁关闭且进入测试阶段时创建或增量更新。
  - Create/Modify: `code-copilot/changes/定时任务优化/execution-log.md` — 记录命令、结果、警告、跳过项和服务清理。
  - Modify: `code-copilot/changes/定时任务优化/tasks.md` — 勾选已完成任务并记录实际偏差。
  - Modify: `code-copilot/changes/定时任务优化/spec.md` — 只回写已验证的实施差异和验收结论。

- [ ] **Step 1: 先读取测试标准和已有验证基线**

```bash
sed -n '1,260p' code-copilot/rules/automated-testing-standard.md
test -f code-copilot/changes/定时任务优化/test-spec.md && sed -n '1,260p' code-copilot/changes/定时任务优化/test-spec.md || true
test -f code-copilot/changes/定时任务优化/execution-log.md && tail -200 code-copilot/changes/定时任务优化/execution-log.md || true
```

预期：按现有基线做增量验证，不从零重复规划。

- [ ] **Step 2: 执行后端测试和构建**

```bash
cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-job -am test
mvn -pl forge-admin-server -am package -DskipTests
```

预期：测试全部通过；admin 聚合构建成功。

- [ ] **Step 3: 执行前端测试、Lint 和构建**

```bash
cd forge-admin-ui
source ~/.nvm/nvm.sh
nvm use v20.19.0
pnpm test -- src/api/system/__tests__/job.spec.js \
  src/components/job/__tests__/cron-builder.spec.js \
  src/components/job/__tests__/CronBuilder.spec.js \
  src/views/system/job-config/__tests__/job-form.spec.js \
  src/views/system/job-config/__tests__/job-page-actions.spec.js
pnpm lint:fix
pnpm build
```

预期：Node 为 `v20.19.0`，定向测试、Lint 和生产构建全部通过。

- [ ] **Step 4: 验证 Flyway**

在用户授权的开发库执行应用启动或 Flyway migration，随后查询：

```sql
SELECT installed_rank, version, description, success
FROM forge_schema_history
ORDER BY installed_rank DESC;
```

预期：`1.0.38 / enhance job scheduler phase 1 / success=1`。重复启动不得因列、索引、字典或权限数据重复而失败。

- [ ] **Step 5: 验证 API 和权限**

使用有权限用户验证分页、详情、新增、修改、启停、手动执行、同步重试、日志筛选、详情和导出；使用无权限用户逐项验证 403/无权访问。不得复用超级管理员结果替代普通授权验证。

预期：权限资源与 Controller 注解一致；Service 二次保护能阻止普通编辑权限用户更换高风险 Bean/Handler/RPC 目标。

- [ ] **Step 6: 验证运行态关键场景**

至少验证：

```text
既有 Cron 任务升级前后 JobKey、Cron、状态、有效 Trigger 数一致
Quartz 任务缺失后 60 秒内恢复
同步失败页面可见，故障解除后重试为 SYNCED
CRON 三个固定预览样例
Asia/Shanghai、UTC、DST 时区
ONCE 成功/失败后 COMPLETED
ONCE 停机错过后只补一次
手动 ONCE 不改变计划状态
ACCEPTED -> RUNNING -> SUCCESS/FAILED
日志组合筛选、导出一致和敏感信息脱敏
非 Forge 管理 Quartz Job 不被清理
```

若没有双实例、Redis 或可停机环境，必须在 `execution-log.md` 标记为“未执行，待用户联调”，不能记录为通过。

- [ ] **Step 7: 浏览器验证**

验证任务总览、全屏工作台、CronBuilder 简单/专家切换、复杂 Cron 回退、一次性时区确认、同步失败提示、概览、日志详情和导出下载。记录关键截图路径或 Playwright 结果。

- [ ] **Step 8: 文档检查**

```bash
git diff --check
rg -n 'TODO|TBD|ApiPermissionIgnore|assertPlatformAdmin|LambdaQueryWrapper' \
  forge-server/forge-framework/forge-plugin-parent/forge-plugin-job \
  code-copilot/changes/定时任务优化
```

预期：`git diff --check` 通过；源码中无本次禁止项。文档中若因历史上下文出现门禁文字，需要人工确认不是未关闭实施项。

- [ ] **Step 9: 记录结果并提交验证文档**

```bash
git add code-copilot/changes/定时任务优化/spec.md \
  code-copilot/changes/定时任务优化/tasks.md \
  code-copilot/changes/定时任务优化/test-spec.md \
  code-copilot/changes/定时任务优化/execution-log.md
git commit -m "docs(job): record phase one verification"
```

---

## 3. Phase 2–4 延后门禁

以下内容不属于本任务清单，不得在 Task 1–20 中顺手实现或预建空表/空字段：

| 阶段 | 独立变更主题 | 进入条件 |
|---|---|---|
| Phase 2 | 并发策略、任务级重试、Misfire、消息告警、模板、批量治理、监控 | Phase 0/1 运行态验收通过；Misfire 精确语义和告警渠道确认 |
| Phase 3 | Open API、服务账号 Token、Scope、幂等、出站白名单与 SSRF | 独立安全评审通过；Redis 失败关闭、Token 生命周期和资源范围确认 |
| Phase 4 | Flowable 技术任务编排 | Phase 3 出站安全可用；流程版本和部署形态确认；Script 保持禁用 |

## 4. Spec 覆盖矩阵

| Spec 需求 | 对应任务 |
|---|---|
| `REQ-JOB-P0-01` DTO/VO | Task 3、4、9、10、11 |
| `REQ-JOB-P0-02` 服务端校验 | Task 2、3、5、8 |
| `REQ-JOB-P0-03` Mapper XML | Task 4、11 |
| `REQ-JOB-P0-04` JobKey 不可编辑 | Task 3、8、9 |
| `REQ-JOB-P0-05` 期望状态同步 | Task 6、7、8、9 |
| `REQ-JOB-P0-06` 启动协调器 | Task 7、14 |
| `REQ-JOB-P0-07` 分布式写锁 | Task 7、9 |
| `REQ-JOB-P0-08` 可见失败和日志失败计数 | Task 7、9、12 |
| `REQ-JOB-P0-09` 测试基础 | Task 1–15、20 |
| `REQ-JOB-P0-10` 资源权限和二次校验 | Task 1、9、10、15、20 |
| `REQ-JOB-P1-01` 任务筛选和摘要 | Task 1、3、4、16、19 |
| `REQ-JOB-P1-02` 全屏工作台 | Task 18、19 |
| `REQ-JOB-P1-03` 单任务/流程占位 | Task 3、18 |
| `REQ-JOB-P1-04`、`REQ-JOB-P1-05`、`REQ-JOB-P1-06`、`REQ-JOB-P1-07` Cron 向导/专家/预览 | Task 5、10、16、17 |
| `REQ-JOB-P1-08` 一次性任务 | Task 1、2、3、5、6、8、13、18 |
| `REQ-JOB-P1-09` 手动执行不改变计划 | Task 6、8、12、13 |
| `REQ-JOB-P1-10` IANA 时区 | Task 1、3、5、6、17、18 |
| `REQ-JOB-P1-11` 任务概览 | Task 3、4、9、10、19 |
| `REQ-JOB-P1-12` 执行生命周期 | Task 1、2、11、12、13、15 |
| `REQ-JOB-P1-13` 日志筛选/详情/导出 | Task 1、11、12、15、16、19 |
| `REQ-JOB-P1-14` 逻辑删除和留存清理 | Task 1、7、8、11、15 |

## 5. 完成定义

Phase 0/1 只有同时满足以下条件才算完成：

- Task 1–20 全部勾选，且实际文件偏差已回写本文件。
- Spec 8.6.1 的产品验收项均有对应执行证据；环境不具备的项明确列为用户联调，不伪造通过。
- Flyway、后端测试、admin 聚合构建、前端定向测试、Lint 和生产构建全部通过。
- 管理接口不再依赖 `@ApiPermissionIgnore + assertAdmin`；高风险执行目标仍有 Service 二次保护。
- Service 查询不再使用 `LambdaQueryWrapper`；自定义 XML 均显式过滤 `del_flag=0`。
- 一次性任务、手动触发、同步失败恢复和执行生命周期满足 Spec 固定语义。
- Phase 2–4 未被提前实现或预建无运行语义的数据结构。
