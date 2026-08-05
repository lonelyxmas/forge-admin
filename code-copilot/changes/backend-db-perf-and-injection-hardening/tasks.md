# 任务拆分 - 后端数据库性能与注入加固
> status: confirmed
> created: 2026-08-05
> 拆分顺序：P0 安全紧急 -> P1 分页性能 -> P2 循环批量化 -> P3 规范治理
> 每个 Task = 可独立提交的原子变更（3-5 个文件）
> **本轮执行范围：Task 1-4（P0 安全紧急修复），Task 5-18 后续另开独立变更**

## 前置条件

- [x] 已完成全量扫描，88 处问题已记录到 spec.md
- [x] 待澄清问题 Q1-Q5 已确认：
  - Q1 F9：SQL 优化 LEFT JOIN GROUP BY
  - Q2 F10：FIND_IN_SET 替代 LIKE
  - Q3 F2：发送失败仅标记状态，人工处理
  - Q4 F18：分批排期，本轮不做
  - Q5：拆分独立变更，本轮只做 P0（Task 1-4）

## Task 1：SQL 注入紧急修复（P0-F1）

- **目标**：消除 `CrudGeneratorStreamService` 的 SQL 注入漏洞
- **涉及文件**：
  - `forge-plugin-generator/.../service/CrudGeneratorStreamService.java:194-197` - 修改，移除字符串拼接
  - `forge-plugin-generator/.../mapper/GenTableColumnMapper.java` - 新增方法 `selectByTableName(String tableName)`
  - `forge-plugin-generator/.../resources/mapper/GenTableColumnMapper.xml` - 新增 `SELECT table_id FROM gen_table WHERE table_name = #{tableName}` 参数化查询
- **关键签名**：
  ```java
  // GenTableColumnMapper.java
  List<GenTableColumn> selectConfiguredColumnsByTableName(@Param("tableName") String tableName);
  ```
- **验证**：
  - 单测覆盖：正常 tableName、含单引号 tableName、空 tableName
  - `mvn test -pl forge-plugin-generator`

## Task 2：消息发送事务拆分（P0-F2）

- **目标**：`MessageServiceImpl.send()` 事务内远程调用移出事务
- **涉及文件**：
  - `forge-plugin-message/.../service/impl/MessageServiceImpl.java:92-140` - 修改，事务只保留落库，发送移到 `@TransactionalEventListener(AFTER_COMMIT)`
  - `forge-plugin-message/.../event/MessageSendEvent.java` - 新增事件类
  - `forge-plugin-message/.../listener/MessageSendListener.java` - 新增监听器，事务提交后执行发送
  - `forge-plugin-message/.../service/impl/MessageServiceImpl.java:132` - 协同渠道同样拆分
- **关键签名**：
  ```java
  // 事务内只做：createMessageRecord + batchCreateReceiverRecords
  // 事务后：publishEvent(new MessageSendEvent(msg.getId()))
  // 监听器：@TransactionalEventListener(phase = AFTER_COMMIT)
  ```
- **验证**：
  - 单测：发送失败时消息记录已落库且状态标记为"发送失败"
  - 单测：发送成功时状态更新为"已发送"
  - `mvn test -pl forge-plugin-message`

## Task 3：采购单提交事务拆分（P0-F3）

- **目标**：`SamplePurchaseOrderServiceImpl.submit()` 事务内 RPC 移出事务
- **涉及文件**：
  - `forge-business-core/.../purchase/service/impl/SamplePurchaseOrderServiceImpl.java:159-200` - 修改，流程启动移到事务提交后
  - 新增 `PurchaseOrderSubmitEvent.java` + 监听器
- **关键签名**：
  ```java
  // 事务内：单据状态预变更（待提交->审核中）
  // 事务后：flowClient.startProcess()
  // 监听器失败：回滚单据状态（补偿事务）
  ```
- **验证**：
  - 单测：流程启动成功 -> 单据状态确认
  - 单测：流程启动失败 -> 单据状态回滚到待提交
  - `mvn test -pl forge-business-core`

## Task 4：文件删除事务拆分（P0-F4）

- **目标**：`SysFileMetadataServiceImpl` 文件 IO 移出事务
- **涉及文件**：
  - `forge-starter-file/.../service/impl/SysFileMetadataServiceImpl.java:111-135` - 修改，元数据逻辑删除在事务内，物理文件删除移到事务后
  - 新增 `FileDeleteEvent.java` + 监听器
- **关键签名**：
  ```java
  // 事务内：metadata 逻辑删除（del_flag = id）
  // 事务后：fileManager.delete(fileId) - 失败只告警 log.error
  ```
- **验证**：
  - 单测：元数据删除成功 + 物理文件删除失败 -> 元数据保持已删除，日志告警
  - `mvn test -pl forge-starter-file`

## Task 5：消息管理 N+1 修复（P1-F5）

- **目标**：`MessageManageServiceImpl` 分页列表 N+1 改批量查询
- **涉及文件**：
  - `forge-plugin-message/.../service/impl/MessageManageServiceImpl.java:81-101` - 修改，移除循环 selectList
  - `forge-plugin-message/.../mapper/SysMessageReceiverMapper.java` - 新增 `selectReceiverMapByMessageIds(List<Long> messageIds)`
  - `forge-plugin-message/.../resources/mapper/SysMessageReceiverMapper.xml` - 新增 `WHERE message_id IN (...)` 批量查询
- **关键签名**：
  ```java
  // 一次查询所有 receiver，按 messageId 分组后内存组装
  Map<Long, List<SysMessageReceiver>> receiverMap = receiverMapper.selectReceiverMapByMessageIds(messageIds);
  ```
- **验证**：
  - 单测：分页查询只调用 1 次 receiver 批量查询
  - 单测：数据顺序与原实现一致
  - `mvn test -pl forge-plugin-message`

## Task 6：公告列表 N+1 修复（P1-F6）

- **目标**：`SysNoticeServiceImpl` 分页列表 N+1 改批量查询
- **涉及文件**：
  - `forge-plugin-system/.../service/impl/SysNoticeServiceImpl.java:286-314` - 修改，已读状态 + 附件改批量
  - `forge-plugin-system/.../mapper/SysNoticeReadRecordMapper.java` - 新增 `selectReadMapByNoticeIds`
  - `forge-plugin-system/.../mapper/SysFileMetadataMapper.java` - 新增 `selectFileMapByBusinessIds`
- **关键签名**：
  ```java
  // 已读：一次 IN 查询 + Set 去重
  // 附件：一次 IN 查询 + 按 businessId 分组
  Set<Long> readNoticeIds = readRecordMapper.selectReadNoticeIds(userId, noticeIds);
  Map<Long, List<SysFileMetadata>> fileMap = fileMapper.selectFileMapByBusinessIds(noticeIds);
  ```
- **验证**：
  - 单测：分页查询只调用 1 次已读批量 + 1 次附件批量
  - `mvn test -pl forge-plugin-system`

## Task 7：用户租户批量绑定优化（P1-F7）

- **目标**：`SysUserServiceImpl.batchBindUserTenant` 循环改批量
- **涉及文件**：
  - `forge-plugin-system/.../service/impl/SysUserServiceImpl.java:604-660` - 修改，移除循环 selectById + updateById
  - `forge-plugin-system/.../mapper/SysUserMapper.java` - 新增 `selectUsersByIds`
  - `forge-plugin-system/.../mapper/SysUserTenantMapper.java` - 新增 `batchUpsert`
- **关键签名**：
  ```java
  // 一次查询所有用户，一次批量 upsert 租户关系，一次批量更新默认租户
  List<SysUser> users = userMapper.selectUsersByIds(userIds);
  userTenantMapper.batchUpsert(tenantBindings);
  userMapper.batchUpdateDefaultTenant(userIds, tenantId);
  ```
- **验证**：
  - 单测：批量绑定 100 用户只调用 3 次 DB 操作
  - `mvn test -pl forge-plugin-system`

## Task 8：业务定义 N+1 修复（P1-F8）

- **目标**：`DataBusinessDefinitionServiceImpl` 循环 getById 改批量
- **涉及文件**：
  - `forge-plugin-data/.../service/impl/DataBusinessDefinitionServiceImpl.java:149-167,187-222` - 修改
  - `forge-plugin-data/.../mapper/DataDatasetMapper.java` - 新增 `selectDatasetsByIds`
  - `forge-plugin-data/.../mapper/DataDatasetFieldMapper.java` - 新增 `selectFieldsByDatasetIds`
- **验证**：
  - 单测：保存校验和详情查询只调用 2 次 DB 操作
  - `mvn test -pl forge-plugin-data`

## Task 9：AI 统计查询优化（P1-F9）

- **目标**：`AiChatSessionMapper` 全表统计 + 分页子查询优化
- **涉及文件**：
  - `forge-plugin-ai/.../resources/mapper/AiChatSessionMapper.xml:22-23,52,54` - 修改
  - 分页查询：关联子查询改为 `LEFT JOIN ... GROUP BY`
  - 统计查询：增加时间范围过滤（近 90 天）或改为 `LEFT JOIN` 聚合
- **关键改动**：
  ```sql
  -- 分页：子查询改 JOIN
  SELECT s.*, COUNT(r.id) AS message_count, COALESCE(SUM(r.token_usage),0) AS token_usage
  FROM ai_chat_session s
  LEFT JOIN ai_chat_record r ON r.session_id = s.id
  GROUP BY s.id
  -- 统计：增加 WHERE create_time >= DATE_SUB(NOW(), INTERVAL 90 DAY)
  ```
- **验证**：
  - 单测：统计查询返回正确数值
  - `mvn test -pl forge-plugin-ai`

## Task 10：待办查询 LIKE 优化（P1-F10）

- **目标**：`FlowTaskMapper` 候选人 LIKE 匹配改 EXISTS 或关联表
- **涉及文件**：
  - `forge-plugin-flow/.../resources/mapper/FlowTaskMapper.xml:43-49` - 修改，3 个 LIKE 改 EXISTS
- **关键改动**：
  ```sql
  -- 原：candidate_users LIKE CONCAT(#{userId}, ',%') OR ...
  -- 改：EXISTS (SELECT 1 FROM sys_flow_task_candidate tc
  --            WHERE tc.task_id = t.id AND tc.user_id = #{userId})
  -- 或临时方案：FIND_IN_SET(#{userId}, t.candidate_users)
  ```
- **验证**：
  - 单测：待办列表查询正确
  - `mvn test -pl forge-plugin-flow`
  - 如选关联表方案：新增 Flyway 迁移 + 存量数据迁移脚本

## Task 11：流程 JOIN OR 优化（P1-F11）

- **目标**：`FlowTaskMapper` JOIN OR 条件改统一存储
- **涉及文件**：
  - `forge-plugin-flow/.../resources/mapper/FlowTaskMapper.xml:11,29,41,79` - 修改
  - 统一 `m.category` 存储为 ID，JOIN 条件改为 `c.id = m.category`
- **验证**：
  - 单测：4 个核心查询结果正确
  - `mvn test -pl forge-plugin-flow`

## Task 12：角色资源加载优化（P1-F12）

- **目标**：`SysRoleServiceImpl` 全量加载改按 ID 批量
- **涉及文件**：
  - `forge-plugin-system/.../service/impl/SysRoleServiceImpl.java:172` - 修改
  - `forge-plugin-system/.../mapper/SysResourceMapper.java` - 新增 `selectResourcesByIds`
- **关键签名**：
  ```java
  // 原：resourceService.list()
  // 改：resourceService.listByIds(resourceIds) 或 resourceMapper.selectResourcesByIds(resourceIds)
  ```
- **验证**：
  - `mvn test -pl forge-plugin-system`

## Task 13：循环单条操作批量化（P2-F13）

- **目标**：15 处循环 insert/update/delete 改批量
- **涉及文件**（按优先级分组）：
  - `SysUserServiceImpl.java:522-531,697-718,1423-1431,1661-1681` - 4 处改 `saveBatch`
  - `DataDimensionServiceImpl.java:297-300` - 改 `saveBatch`
  - `DataBusinessDefinitionServiceImpl.java:187-196` - 改 `saveBatch`
  - `SysNoticeServiceImpl.java:244-249` - 改 `saveBatch`
  - `FlowFillBatchServiceImpl.java:95-113` - 改 `saveBatch`
  - `SysUserServiceImpl.java:217-219` - 循环 delete 改 IN 批量
  - `MessageServiceImpl.java:330-335` - 循环 update 改 `updateBatchById`
  - `SamplePurchaseOrderServiceImpl.java:481-493` - 循环 update 改 `updateBatchById`
- **验证**：
  - 按模块分别 `mvn test`

## Task 14：分页 SELECT * 优化（P2-F14）

- **目标**：日志/连接分页查询移除大文本和敏感字段
- **涉及文件**：
  - `forge-plugin-external/.../resources/mapper/ExternalApiLogMapper.xml:47` - 改精确列
  - `forge-plugin-data/.../resources/mapper/DataConnectionMapper.xml:29` - 移除 `password_cipher`
- **验证**：
  - `mvn test -pl forge-plugin-external,forge-plugin-data`

## Task 15：LIKE 与子查询优化（P2-F15/F17）

- **目标**：权限表 LIKE、流程 ancestors LIKE、用户嵌套 IN 子查询优化
- **涉及文件**：
  - `forge-plugin-system/.../resources/mapper/SysResourceMapper.xml:14,17,20` - 评估改前缀匹配或全文索引
  - `forge-plugin-flow/.../resources/mapper/FlowTaskMapper.xml:62,94,125` - 改 `FIND_IN_SET`
  - `forge-plugin-system/.../resources/mapper/SysUserMapper.xml:136-150` - 拆分嵌套 IN
- **验证**：
  - `mvn test -pl forge-plugin-system,forge-plugin-flow`

## Task 16：租户缺失失败关闭（P2-F16）

- **目标**：`SocialConfigServiceImpl` 租户为空时拒绝查询
- **涉及文件**：
  - `forge-starter-social/.../service/impl/SocialConfigServiceImpl.java:87-89` - 修改，tenantId 为空抛异常
- **验证**：
  - `mvn test -pl forge-starter-social`

## Task 17：LambdaQueryWrapper 迁移（P3-F18，渐进）

- **目标**：按模块将 Service 层 LambdaQueryWrapper 查询迁移到 Mapper XML
- **涉及文件**：
  - 第一批：`SysUserServiceImpl`（54 处）、`SysRoleServiceImpl`（15 处）
  - 第二批：`FlowOrgIntegrationServiceImpl`（14 处）、`SysNoticeServiceImpl`（含 exists 子查询）
  - 第三批：`SysConfigServiceImpl`、`SysTenantServiceImpl`、`SysFileMetadataServiceImpl` 等
- **验证**：
  - 逐方法验证 `DataScopeInterceptor` 配置
  - 按模块 `mvn test`

## Task 18：聚合验证

- [ ] 执行 `cd forge-server && mvn clean install -DskipTests`
- [ ] 执行全量 `mvn test`
- [ ] Mapper XML 静态解析
- [ ] 回填 spec.md 执行日志和审查结论
- [ ] 精确暂存本变更文件并提交
