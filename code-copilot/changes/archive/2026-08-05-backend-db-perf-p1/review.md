# 审查报告 - 后端数据库性能 P1 分页查询优化

> review date: 2026-08-05
> reviewer: AI 自审
> status: pass（附 1 个 follow-up 项）

## Phase 1：Spec 合规审查

| Task | Spec 要求 | 实现情况 | 合规 |
|------|----------|---------|------|
| 5 | 新增 `selectReceiverStatsByMessageIds` IN 批量聚合 | 新增 ReceiverStatVO + Mapper 方法 + XML GROUP BY，pageMessages 循环 selectList 改一次 IN | ✅ |
| 6 | 已读状态 + 附件改批量 IN 查询 | 新增 `selectReadNoticeIds`，附件改 `selectBatchIds`；`selectNoticeById` 附件也改批量（额外修复） | ✅ |
| 7 | `selectBatchIds` 批量查询 + `hasEnabledTenantMembership` 批量化 | 循环 selectById 改 selectBatchIds，循环 selectCount 改一次 IN + select(userId) | ✅ |
| 8 | 校验改 `listByIds`，保存改 `saveBatch`，查询改批量 | 校验改 `listByIds` ✅；保存保留循环 insert（Q3 确认无 saveBatch 能力）；查询 dataset 改批量 ✅，字段仍循环（P2 范围） | ✅ |
| 9 | 分页子查询改 JOIN，统计增加 90 天范围 | 分页改 LEFT JOIN 派生表聚合（比直接 GROUP BY 更优）；统计 3 个子查询增加 90 天过滤 | ✅ |
| 10 | LIKE 改 FIND_IN_SET | 2 处 4 路 LIKE 改 `FIND_IN_SET` | ✅ |
| 11 | Flyway 迁移 + JOIN OR 改 `c.id = m.category` | Flyway V1.0.86 + FlowTaskMapper 6 处 + FlowModelMapper 1 处（额外修复） | ✅ |
| 12 | clientCode 为空 `listByIds`，不为空 `lambdaQuery(clientCode)` | clientCode 为空用 `loadResourcesWithAncestors`（含祖先链，更完善）；不为空 `lambdaQuery(clientCode)` + ancestors 合并 | ✅ |

**Phase 1 结论**：8 个 Task 全部合规。Q3 确认 `businessDatasetMapper` 无 saveBatch 能力，保存保留循环 insert 合理。Task 8 字段查询仍循环属 P2 范围，可接受。

## Phase 2：代码质量审查

### 安全性
- SQL 注入：所有新增 Mapper 方法均使用 `#{param}` 参数化 ✅
- Flyway 脚本：`UPDATE ... INNER JOIN ... SET` 无注入风险 ✅

### 性效
- N+1 消除：Task 5/6/7/8/12 均从循环查询改为批量 IN 查询 ✅
- 全表扫描：Task 9 统计增加 90 天过滤 ✅
- 索引失效：Task 10 LIKE 改 FIND_IN_SET ✅，Task 11 JOIN OR 消除 ✅
- 全量加载：Task 12 `list()` 改按需查询 ✅

### 边界保护
- 空列表保护：所有批量查询前都有 `isEmpty()` 检查，避免 `IN ()` 语法错误 ✅
- COALESCE 保护：Task 9 LEFT JOIN 聚合结果用 `COALESCE(..., 0)` ✅
- Task 12 `loadResourcesWithAncestors` 死循环风险：`resourceMap.put` 返回非 null 时跳过，循环引用不会死循环 ✅

### 事务一致性
- 本轮变更不涉及 `@Transactional` 修改 ✅
- Task 7 `upsertUserTenant` 保持逐条调用（含复杂 upsert 逻辑）✅

### 发现问题及处理

| # | 严重度 | Task | 问题 | 处理 |
|---|--------|------|------|------|
| 1 | 低 | 5 | `SysMessageReceiverMapper.java` 第 18-20 行多余空行 | 已修复（commit 2d67cfb0） |
| 2 | 低 | 8 | `java.util.Collections` / `java.util.HashMap` 使用全限定名而非 import | 已修复（commit 2d67cfb0） |
| 3 | 中 | 9 | 前端 `session.vue` 显示"会话总数"/"消息总数"/"Token消耗"，后端已改为近 90 天统计，文案不匹配 | **Follow-up**：需同步前端文案为"近90天会话"/"近90天消息"/"近90天Token消耗" |

## 审查结论

**通过**。8 个 P1 性能优化 Task 全部符合 Spec，代码质量良好。2 个低优先级代码风格问题已修复。1 个前端文案不匹配问题作为 follow-up 项跟踪。

### Follow-up 项

| 编号 | 描述 | 优先级 | 建议变更 |
|------|------|--------|---------|
| FU-1 | Task 9 前端文案同步：`session.vue` statsData 标签从"会话总数"/"消息总数"/"Token消耗"改为"近90天会话"/"近90天消息"/"近90天Token消耗" | 中 | 另开前端小变更 |
