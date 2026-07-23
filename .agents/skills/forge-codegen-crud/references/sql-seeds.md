# SQL Seed Reference

## Flyway File Rules

- Place SQL in `forge/db/migration/V<next>__<lower_snake_case_description>.sql`.
- Inspect existing `forge/db/migration/V*__*.sql` files and choose the next unused version.
- Use `CREATE TABLE IF NOT EXISTS`.
- Use `INSERT ... SELECT ... WHERE NOT EXISTS` for dictionaries, Excel configs, resources, and built-in data.
- Include explicit column lists in every `INSERT`.
- Before dropping a legacy index, query `information_schema.STATISTICS`; do not assume an index from an older migration still exists. When one atomic `ALTER TABLE` must replace optional legacy indexes, dynamically include only the index names that currently exist.
- Do not commit production secrets, real credentials, tokens, AK/SK, or passwords.

## Business Table DDL

Generated business tables must include the system-required fields.

```sql
CREATE TABLE IF NOT EXISTS `biz_example` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `example_name` varchar(128) NOT NULL COMMENT '示例名称',
  `status` varchar(16) NOT NULL DEFAULT '1' COMMENT '状态',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `del_flag` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0正常，1删除',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz_example_tenant_status` (`tenant_id`, `status`),
  KEY `idx_biz_example_update_time` (`tenant_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例业务表';
```

Use `bigint` amounts in cents, `datetime` for time fields mapped to `LocalDateTime`, and `utf8mb4` for tables.

### Logical-delete unique keys

不是所有逻辑删除表都需要删除标记唯一索引。只有业务键要求“未删除记录唯一”、且删除后允许使用相同业务键重建时，才把 `del_flag` 升级为主键墓碑：

```sql
`del_flag` bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0正常，删除后写主键',
UNIQUE KEY `uk_biz_example_code_active` (`tenant_id`, `example_code`, `del_flag`)
```

对应数值主键实体必须使用 `Long delFlag` 和 `@TableLogic(value = "0", delval = "id")`。自定义批量删除 SQL 使用 `SET del_flag = id WHERE del_flag = 0`，不能固定写 `1`。禁止创建可见的 `logic_delete_active` 生成列，也不要用函数索引、部分索引或只在 Service 层先查后插代替数据库唯一约束。不要使用 `UNIQUE (business_key, deleted_at)` 且让有效行的 `deleted_at` 为 `NULL`；MySQL 唯一索引允许多个 `NULL`，不能约束有效业务键唯一。

若业务键要求跨已删除历史永久唯一，唯一索引不要包含 `del_flag`；没有业务唯一键的表继续使用普通 `tinyint` 逻辑删除即可。

## Dictionary Seeds

First search existing migrations for reusable dictionaries:

```bash
rg -n "dict_type|sys_enable_disable|<candidate_dict_type>" forge/db/migration forge/forge-admin-server/src/main/resources/sql
```

If no existing dictionary fits, add `sys_dict_type` and `sys_dict_data` inserts with `tenant_id = 1`.

```sql
INSERT INTO sys_dict_type (tenant_id, dict_name, dict_type, dict_status, remark, create_time, update_time)
SELECT seed.tenant_id, seed.dict_name, seed.dict_type, seed.dict_status, seed.remark, NOW(), NOW()
FROM (
  SELECT 1 tenant_id, '示例状态' dict_name, 'biz_example_status' dict_type, 1 dict_status, '示例业务状态' remark
) seed
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_dict_type t
  WHERE t.tenant_id = seed.tenant_id
    AND t.dict_type = seed.dict_type
);

INSERT INTO sys_dict_data (tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, dict_status, remark, create_time, update_time)
SELECT seed.tenant_id, seed.dict_sort, seed.dict_label, seed.dict_value, seed.dict_type, NULL, seed.list_class, seed.is_default, 1, seed.remark, NOW(), NOW()
FROM (
  SELECT 1 tenant_id, 1 dict_sort, '启用' dict_label, '1' dict_value, 'biz_example_status' dict_type, 'success' list_class, 'Y' is_default, '启用状态' remark
  UNION ALL SELECT 1, 2, '禁用', '0', 'biz_example_status', 'error', 'N', '禁用状态'
) seed
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_dict_data d
  WHERE d.tenant_id = seed.tenant_id
    AND d.dict_type = seed.dict_type
    AND d.dict_value = seed.dict_value
);
```

Dictionary naming rules:

- Use lower snake case.
- Prefer `sys_` only for reusable system dictionaries.
- Keep `dict_value` equal to backend enum/storage values.
- Use `dict_label` only for display text.
- Use `list_class` for tag style.

## Excel Config Seeds

When import/export is enabled, generate both export config and column config. These tables do not include `tenant_id`; they are ignored by tenant interception.

```sql
INSERT INTO sys_excel_export_config (
  config_key, export_name, sheet_name, file_name_template, data_source_bean, query_method,
  auto_trans, pageable, max_rows, sort_field, sort_order, status, include_sample, allow_import,
  remark, create_time, update_time, create_by, update_by, create_dept
)
SELECT seed.config_key, seed.export_name, seed.sheet_name, seed.file_name_template, seed.data_source_bean, seed.query_method,
       seed.auto_trans, seed.pageable, seed.max_rows, seed.sort_field, seed.sort_order, seed.status, seed.include_sample, seed.allow_import,
       seed.remark, NOW(), NOW(), 1, 1, 1
FROM (
  SELECT 'biz_example_export' config_key,
         '示例导出' export_name,
         '示例数据' sheet_name,
         '示例列表_{date}.xlsx' file_name_template,
         'bizExampleService' data_source_bean,
         'selectExportList' query_method,
         1 auto_trans,
         0 pageable,
         50000 max_rows,
         'update_time' sort_field,
         'DESC' sort_order,
         1 status,
         1 include_sample,
         1 allow_import,
         '示例导入导出配置' remark
) seed
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_excel_export_config c
  WHERE c.config_key = seed.config_key
);

INSERT INTO sys_excel_column_config (
  config_key, field_name, column_name, width, order_num, export, date_format, number_format,
  dict_type, importable, required, example_value, validation_rule, validation_message,
  create_time, update_time, create_by, update_by, create_dept
)
SELECT seed.config_key, seed.field_name, seed.column_name, seed.width, seed.order_num, seed.export, seed.date_format, seed.number_format,
       seed.dict_type, seed.importable, seed.required, seed.example_value, seed.validation_rule, seed.validation_message,
       NOW(), NOW(), 1, 1, 1
FROM (
  SELECT 'biz_example_export' config_key, 'exampleName' field_name, '示例名称' column_name, 24 width, 1 order_num, 1 export,
         NULL date_format, NULL number_format, NULL dict_type, 1 importable, 1 required, '示例A' example_value,
         '^.{1,128}$' validation_rule, '示例名称长度必须为1-128个字符' validation_message
  UNION ALL SELECT 'biz_example_export', 'status', '状态', 16, 2, 1,
         NULL, NULL, 'biz_example_status', 1, 1, '启用', NULL, NULL
  UNION ALL SELECT 'biz_example_export', 'createTime', '创建时间', 20, 3, 1,
         'yyyy-MM-dd HH:mm:ss', NULL, NULL, 0, 0, NULL, NULL, NULL
) seed
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_excel_column_config c
  WHERE c.config_key = seed.config_key
    AND c.field_name = seed.field_name
);
```

`data_source_bean` must match the Spring bean name of the generated service, and `query_method` must exist on that bean if using the fixed Bean export engine.

## Resource Seeds

Generate menu and button permissions after route/API paths are known. Use `tenant_id = 1`, `client_code = 'pc'`, and `NOT EXISTS` guards.

```sql
SET @parent_menu_id := (
  SELECT id
  FROM sys_resource
  WHERE tenant_id = 1
    AND resource_type IN (1, 2)
    AND path = '/biz'
  LIMIT 1
);

INSERT INTO sys_resource (tenant_id, resource_name, parent_id, resource_type, sort, path, component, is_external,
                          sso_enabled, sso_target_client, open_target, is_public, menu_status, visible, perms, icon,
                          api_method, api_url, keep_alive, always_show, redirect, remark, create_by, create_time,
                          update_by, update_time, create_dept, client_code)
SELECT 1, '示例管理', @parent_menu_id, 2, 10, '/biz/example', 'biz/example/index', 0,
       0, NULL, '_self', 0, 1, 1, 'biz:example:list', 'ionicons5:ListOutline',
       NULL, NULL, 1, 0, NULL, '示例管理菜单', 1, NOW(), 1, NOW(), 1, 'pc'
WHERE @parent_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM sys_resource
    WHERE tenant_id = 1
      AND resource_type = 2
      AND perms = 'biz:example:list'
  );

SET @example_menu_id := (
  SELECT id
  FROM sys_resource
  WHERE tenant_id = 1
    AND resource_type = 2
    AND perms = 'biz:example:list'
  LIMIT 1
);

INSERT INTO sys_resource (tenant_id, resource_name, parent_id, resource_type, sort, path, component, is_external,
                          sso_enabled, sso_target_client, open_target, is_public, menu_status, visible, perms, icon,
                          api_method, api_url, keep_alive, always_show, redirect, remark, create_by, create_time,
                          update_by, update_time, create_dept, client_code)
SELECT seed.tenant_id, seed.resource_name, @example_menu_id, 3, seed.sort, NULL, NULL, 0,
       0, NULL, '_self', 0, 1, 1, seed.perms, NULL,
       NULL, NULL, 0, 0, NULL, seed.remark, 1, NOW(), 1, NOW(), 1, 'pc'
FROM (
  SELECT 1 tenant_id, '示例查询' resource_name, 1 sort, 'biz:example:query' perms, '示例查询按钮权限' remark
  UNION ALL SELECT 1, '示例新增', 2, 'biz:example:add', '示例新增按钮权限'
  UNION ALL SELECT 1, '示例修改', 3, 'biz:example:edit', '示例修改按钮权限'
  UNION ALL SELECT 1, '示例删除', 4, 'biz:example:remove', '示例删除按钮权限'
  UNION ALL SELECT 1, '示例导入', 5, 'biz:example:import', '示例导入按钮权限'
  UNION ALL SELECT 1, '示例导出', 6, 'biz:example:export', '示例导出按钮权限'
) seed
WHERE @example_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM sys_resource r
    WHERE r.tenant_id = seed.tenant_id
      AND r.resource_type = 3
      AND r.perms = seed.perms
  );
```

If the module uses API permission resources, also generate `resource_type = 4` rows with `api_method` and `api_url`, guarded by method + URL or `perms`.
For generated CRUD APIs, use POST-safe codegen routes for detail, create, update, and delete permission resources:

- `GET /.../page`
- `GET /.../list`
- `POST /.../getById`
- `POST /.../add`
- `POST /.../edit`
- `POST /.../remove/{id}`
- `POST /.../removeBatch`

Do not seed `PUT` or `DELETE` API resources for generated CRUD modules.
