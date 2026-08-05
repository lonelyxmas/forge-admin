# 任务拆分 - 后端数据库性能 P1 分页查询优化

> status: review-ready
> created: 2026-08-05
> 前置变更：backend-db-perf-and-injection-hardening（P0 已完成）
> 每个 Task = 可独立提交的原子变更

## 前置条件

- [x] P0 变更已完成（Task 1-4 commit bf613888, e6832fcf, db19e638, 6e18e9f0）
- [x] 已扫描 Task 5-12 涉及的 7 个核心文件，确认问题行号
- [x] Q1=方案A（Flyway迁移统一ID）；Q2=方案B（增加90天时间范围过滤）；Q3=实现时确认（businessDatasetMapper 无 saveBatch 能力，保留循环 insert）

## Task 5：消息管理 N+1 修复（P1-F5）

- **目标**：`MessageManageServiceImpl.pageMessages` 分页列表 N+1 改批量查询
- **涉及文件**：
  - `forge-plugin-message/.../service/impl/MessageManageServiceImpl.java:81-101` - 修改，移除循环 selectList
  - `forge-plugin-message/.../mapper/SysMessageReceiverMapper.java` - 新增 `selectReceiverStatsByMessageIds`
  - `forge-plugin-message/.../resources/mapper/SysMessageReceiverMapper.xml` - 新增 IN 批量查询 + GROUP BY
- **关键签名**：
  ```java
  // 一次 IN 查询，按 messageId 分组统计
  List<ReceiverStatVO> selectReceiverStatsByMessageIds(@Param("messageIds") List<Long> messageIds);
  ```
- **验证**：`mvn compile -pl forge-plugin-message -am`

## Task 6：公告列表 N+1 修复（P1-F6）

- **目标**：`SysNoticeServiceImpl.selectUserNoticePage` 已读状态 + 附件 N+1 改批量
- **涉及文件**：
  - `forge-plugin-system/.../service/impl/SysNoticeServiceImpl.java:286-316` - 修改
  - `forge-plugin-system/.../mapper/SysNoticeReadRecordMapper.java` - 新增 `selectReadNoticeIds`
  - `forge-plugin-system/.../mapper/SysFileMetadataMapper.java` - 新增 `selectFileMapByBusinessIds`
- **验证**：`mvn compile -pl forge-plugin-system -am`

## Task 7：用户租户批量绑定优化（P1-F7）

- **目标**：`SysUserServiceImpl.batchBindUserTenant` 循环改批量
- **涉及文件**：
  - `forge-plugin-system/.../service/impl/SysUserServiceImpl.java:624-660` - 修改
- **关键改动**：
  ```java
  // 原：循环 selectById + updateById
  // 改：selectBatchIds 一次查询 + 批量判断 + 批量更新
  List<SysUser> users = userMapper.selectBatchIds(userIds);
  ```
- **验证**：`mvn compile -pl forge-plugin-system -am`

## Task 8：业务定义 N+1 修复（P1-F8）

- **目标**：`DataBusinessDefinitionServiceImpl` 校验 + 保存 + 查询 N+1 改批量
- **涉及文件**：
  - `forge-plugin-data/.../service/impl/DataBusinessDefinitionServiceImpl.java:147-168,183-198,208-223` - 修改
- **验证**：`mvn compile -pl forge-plugin-data -am`

## Task 9：AI 统计查询优化（P1-F9）

- **目标**：`AiChatSessionMapper` 分页子查询改 JOIN + 统计优化
- **涉及文件**：
  - `forge-plugin-ai/.../resources/mapper/AiChatSessionMapper.xml:19-47,49-55` - 修改
- **关键改动**：
  ```sql
  -- 分页：子查询改 LEFT JOIN + GROUP BY
  -- 统计：增加 WHERE create_time >= DATE_SUB(NOW(), INTERVAL 90 DAY)
  ```
- **验证**：`mvn compile -pl forge-plugin-ai -am`

## Task 10：待办查询 LIKE 优化（P1-F10）

- **目标**：`FlowTaskMapper` 候选人 LIKE 改 FIND_IN_SET
- **涉及文件**：
  - `forge-plugin-flow/.../resources/mapper/FlowTaskMapper.xml:43-49,150-161` - 修改
- **关键改动**：
  ```sql
  -- 原：4 个 LIKE 匹配
  -- 改：FIND_IN_SET(#{userId}, t.candidate_users)
  ```
- **验证**：`mvn compile -pl forge-plugin-flow -am`

## Task 11：流程 JOIN OR 优化（P1-F11）

- **目标**：`FlowTaskMapper` 6 处 JOIN OR 改统一 ID
- **涉及文件**：
  - `forge-plugin-flow/.../resources/mapper/FlowTaskMapper.xml` - 6 处 JOIN 条件修改
  - `forge/db/migration/V1.0.x__unify_flow_category_to_id.sql` - Flyway 迁移脚本
- **关键改动**：
  ```sql
  -- 原：LEFT JOIN sys_flow_category c ON c.id = m.category OR c.category_code = m.category
  -- 改：LEFT JOIN sys_flow_category c ON c.id = m.category
  -- Flyway：UPDATE sys_flow_model SET category = (SELECT id FROM sys_flow_category WHERE category_code = sys_flow_model.category) WHERE category NOT REGEXP '^[0-9]+$'
  ```
- **验证**：`mvn compile -pl forge-plugin-flow -am`

## Task 12：角色资源加载优化（P1-F12）

- **目标**：`SysRoleServiceImpl.bindRoleResources` 全量加载改按需查询
- **涉及文件**：
  - `forge-plugin-system/.../service/impl/SysRoleServiceImpl.java:172` - 修改
- **关键改动**：
  ```java
  // 原：resourceService.list()
  // 改：clientCode 为空 -> listByIds(resourceIds)
  //     clientCode 不为空 -> lambdaQuery().eq(clientCode).list() 获取客户端资源集合
  ```
- **验证**：`mvn compile -pl forge-plugin-system -am`

## Task 13：聚合验证

- [x] 执行 `cd forge-server && mvn clean install -DskipTests` — 全量编译通过
- [x] 回填 spec.md 执行日志
- [x] 精确暂存本变更文件并提交

## 执行记录

| Task | Commit | 说明 |
|------|--------|------|
| 5 | b36d4031 | 消息管理 N+1 修复，循环 selectList 改一次 IN 批量查询 |
| 6 | 322e5c26 | 公告列表 N+1 修复，已读状态 + 附件改批量查询 |
| 7 | bf479768 | 用户租户批量绑定，循环 selectById/selectCount 改批量 |
| 8 | b3783b02 | 业务定义 N+1 修复，数据集校验和查询改批量 |
| 9 | 350f36db | AI 统计查询优化，子查询改 JOIN + 增加 90 天时间范围 |
| 10 | 2ec99da6 | 待办查询 LIKE 优化改 FIND_IN_SET |
| 11 | 764a26d7 | 流程 JOIN OR 优化，Flyway 统一 category 为 ID |
| 12 | f56c1836 | 角色资源加载优化，全量 list() 改按需查询 |
