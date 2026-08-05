# 变更规格 - 后端数据库性能 P1 分页查询优化

> status: confirmed
> created: 2026-08-05
> complexity: 🟡中等
> 前置变更：backend-db-perf-and-injection-hardening（P0 已完成）
> 本轮执行范围：Task 5-12（P1 分页性能优化）
> Q1=方案A（Flyway迁移统一ID）；Q2=方案B（增加90天时间范围过滤）；Q3=实现时确认

## 1. 背景与动机

P0 安全紧急修复已完成（SQL 注入 + 事务拆分）。本轮聚焦 P1 分页性能问题：N+1 查询、全表扫描、LIKE 全表匹配、JOIN OR 索引失效、全量加载。这些问题在数据量增长后会显著影响列表页响应时间。

## 2. 变更范围

| Task | 模块 | 问题类型 | 影响方法 |
|------|------|---------|---------|
| Task 5 | forge-plugin-message | N+1 查询 | MessageManageServiceImpl.pageMessages |
| Task 6 | forge-plugin-system | N+1 查询（双层） | SysNoticeServiceImpl.selectUserNoticePage |
| Task 7 | forge-plugin-system | 循环单条 | SysUserServiceImpl.batchBindUserTenant |
| Task 8 | forge-plugin-data | N+1 + 循环插入 | DataBusinessDefinitionServiceImpl |
| Task 9 | forge-plugin-ai | 全表子查询 | AiChatSessionMapper.selectSessionPage / selectStatistics |
| Task 10 | forge-plugin-flow | LIKE 全表 | FlowTaskMapper.selectTodoTasks / countWorkspaceTodo |
| Task 11 | forge-plugin-flow | JOIN OR 索引失效 | FlowTaskMapper（6 处查询） |
| Task 12 | forge-plugin-system | 全量加载 | SysRoleServiceImpl.bindRoleResources |

## 3. 详细问题分析

### Task 5：消息管理 N+1（F5）

**文件**：`forge-plugin-message/.../service/impl/MessageManageServiceImpl.java:81-101`

**问题**：`pageMessages` 方法分页后循环 `receiverMapper.selectList` 查每条消息的接收人，用于统计 receiverCount/readCount/unreadCount。分页 N 条触发 N 次查询。

**修复**：新增 Mapper 方法 `selectReceiverStatsByMessageIds(List<Long> messageIds)` 一次 IN 查询，按 messageId 分组聚合 count。

### Task 6：公告列表 N+1（F6）

**文件**：`forge-plugin-system/.../service/impl/SysNoticeServiceImpl.java`

**问题**：
- 第 291-294 行：循环内 `selectCount` 查已读状态 → N 次
- 第 297-316 行：循环内 `selectById` 查附件 → N×M 次

**修复**：
- 已读状态：新增 `selectReadNoticeIds(userId, noticeIds)` 一次 IN 查询返回已读 Set
- 附件：新增 `selectFileMapByBusinessIds` 一次 IN 查询，按 businessId 分组

### Task 7：用户租户批量绑定（F7）

**文件**：`forge-plugin-system/.../service/impl/SysUserServiceImpl.java:624-638`

**问题**：`batchBindUserTenant` 循环内 `selectById` + `updateById`，N 个用户触发 2N 次查询 + N 次更新。

**修复**：改为 `selectBatchIds(userIds)` 一次查询，批量更新默认租户。`hasEnabledTenantMembership` 也需批量版本。

### Task 8：业务定义 N+1（F8）

**文件**：`forge-plugin-data/.../service/impl/DataBusinessDefinitionServiceImpl.java`

**问题**：
- `validateDatasetBindings` (147-168)：循环 `datasetService.getById` → N 次
- `saveDatasetBindings` (183-198)：循环 `businessDatasetMapper.insert` → N 次
- `listBusinessDatasets` (208-223)：循环 `datasetService.getById` + `datasetFieldService.listByDatasetId` → 2N 次

**修复**：
- 校验：一次 `listByIds(datasetIds)` 查询
- 保存：改 `saveBatch` 批量插入
- 查询：一次 `selectBatchIds` 查 dataset + 一次 IN 查询 fields

### Task 9：AI 统计查询优化（F9）

**文件**：`forge-plugin-ai/.../resources/mapper/AiChatSessionMapper.xml`

**问题**：
- `selectSessionPage` (19-47)：每行 session 两个子查询 COUNT 和 SUM → 改 LEFT JOIN + GROUP BY
- `selectStatistics` (49-55)：4 个独立全表子查询 → 合并为 JOIN 聚合或增加时间范围

**修复**：
```sql
-- 分页：子查询改 JOIN
SELECT s.*, u.real_name, u.avatar,
       COUNT(r.id) AS message_count, COALESCE(SUM(r.token_usage), 0) AS token_usage
FROM ai_chat_session s
LEFT JOIN sys_user u ON s.user_id = u.id AND u.del_flag = 0
LEFT JOIN ai_chat_record r ON r.session_id = s.id
GROUP BY s.id
```

### Task 10：待办查询 LIKE 优化（F10）

**文件**：`forge-plugin-flow/.../resources/mapper/FlowTaskMapper.xml`

**问题**：`selectTodoTasks` (44-48) 和 `countWorkspaceTodo` (154-158) 使用 4 个 LIKE 匹配候选人。

**修复**：改为 `FIND_IN_SET(#{userId}, t.candidate_users)`，单条件替代 4 个 LIKE。

### Task 11：流程 JOIN OR 优化（F11）

**文件**：`forge-plugin-flow/.../resources/mapper/FlowTaskMapper.xml`

**问题**：6 处查询都有 `LEFT JOIN sys_flow_category c ON c.id = m.category OR c.category_code = m.category`，OR 导致索引失效。

**修复**：统一 `m.category` 存储为 ID，JOIN 条件改为 `c.id = m.category`。需要 Flyway 迁移脚本将存量 `category_code` 值更新为对应 ID。

### Task 12：角色资源加载优化（F12）

**文件**：`forge-plugin-system/.../service/impl/SysRoleServiceImpl.java:172`

**问题**：`bindRoleResources` 调用 `resourceService.list()` 全量加载所有资源。

**修复**：
- clientCode 为空时：`resourceService.listByIds(resourceIds)` 按需查询
- clientCode 不为空时：按 clientCode 条件查询该客户端资源集合 + listByIds 合并

## 4. 待澄清问题

### Q1：Task 11 category 字段类型
`sys_flow_model.category` 当前可能存储 ID 或 category_code 两种值。需要确认：
- A) 先跑 Flyway 迁移统一为 ID，再改 JOIN 条件（推荐）
- B) 只改 JOIN 为 `c.id = m.category`，不做数据迁移（风险：存量 code 值查不到）

### Q2：Task 9 统计查询优化策略
`selectStatistics` 的 4 个子查询是否需要全部保留？是否可以：
- A) 改为 JOIN 聚合（4 表 CROSS JOIN 性能可能更差）
- B) 增加时间范围过滤（近 90 天）+ 保留子查询（推荐）
- C) 使用缓存（Redis 定时刷新）

### Q3：Task 8 saveBatch 批量插入
`businessDatasetMapper` 是否已有 `saveBatch` 能力？（实现时确认，若继承 IService 则可直接使用）

## 5. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| Task 11 数据迁移不完整 | 中 | Flyway 脚本先 SELECT 查存量 code 值，UPDATE 为对应 ID |
| Task 9 GROUP BY 分页性能 | 低 | MySQL 8 对 GROUP BY 优化较好，且 LEFT JOIN 有 session_id 索引 |
| Task 12 权限校验遗漏 | 中 | clientCode 不为空时仍需查全量客户端资源做校验 |

## 6. 验证策略

- 每个 Task 修改后单独编译对应模块
- 全部完成后 `mvn clean install -DskipTests` 全量编译
- Mapper XML 修改后检查 namespace 和 SQL 语法
- 不涉及行为变化，仅性能优化

## 7. 不在范围内

- P2 循环批量化（Task 13-15）→ 另开变更
- P3 LambdaQueryWrapper 迁移（Task 17）→ 另开变更
- 前端改动 → 无
- 数据库表结构变更 → 仅 Task 11 需要数据迁移脚本
