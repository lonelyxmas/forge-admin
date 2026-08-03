package com.mdframe.forge.starter.datascope.handler;

import com.mdframe.forge.starter.datascope.context.DataScopeContext;
import com.mdframe.forge.starter.datascope.config.DataScopeProperties;
import com.mdframe.forge.starter.datascope.entity.SysDataScopeConfig;
import com.mdframe.forge.starter.datascope.enums.DataScopeType;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataScopeInterceptorTest {

    @Test
    void shouldApplyTenantScopeInsideWrappedCountQuery() throws Exception {
        DataScopeInterceptor interceptor = new DataScopeInterceptor(mock(com.mdframe.forge.starter.datascope.service.IDataScopeService.class),
                new DataScopeProperties());
        SysDataScopeConfig config = buildTenantScopeConfig();
        DataScopeContext context = buildTenantScopeContext();
        String originalSql = """
                SELECT COUNT(*) FROM (
                    SELECT u.id
                    FROM sys_user u
                    WHERE u.username LIKE '%admin%'
                ) TOTAL
                """;

        String modifiedSql = invokeBuildDataScopeSql(interceptor, originalSql, config, context, DataScopeType.TENANT_ALL);

        Select parsedSelect = (Select) CCJSqlParserUtil.parse(modifiedSql);
        PlainSelect outerSelect = parsedSelect.getPlainSelect();
        assertNull(outerSelect.getWhere(), "count 外层不应追加内部表别名条件");
        assertTrue(outerSelect.getFromItem() instanceof ParenthesedSelect);

        ParenthesedSelect wrappedSelect = (ParenthesedSelect) outerSelect.getFromItem();
        PlainSelect innerSelect = wrappedSelect.getPlainSelect();
        assertNotNull(innerSelect.getWhere());
        assertTrue(innerSelect.getWhere().toString().contains("u.tenant_id = 2"));
        assertTrue(innerSelect.getWhere().toString().contains("u.username LIKE '%admin%'"));
    }

    @Test
    void shouldApplyTenantScopeToTopLevelSelectWhenNotWrapped() throws Exception {
        DataScopeInterceptor interceptor = new DataScopeInterceptor(mock(com.mdframe.forge.starter.datascope.service.IDataScopeService.class),
                new DataScopeProperties());
        SysDataScopeConfig config = buildTenantScopeConfig();
        DataScopeContext context = buildTenantScopeContext();
        String originalSql = """
                SELECT u.id
                FROM sys_user u
                WHERE u.username LIKE '%admin%'
                """;

        String modifiedSql = invokeBuildDataScopeSql(interceptor, originalSql, config, context, DataScopeType.TENANT_ALL);

        Select parsedSelect = (Select) CCJSqlParserUtil.parse(modifiedSql);
        PlainSelect plainSelect = parsedSelect.getPlainSelect();
        assertNotNull(plainSelect.getWhere());
        assertTrue(plainSelect.getWhere().toString().contains("u.tenant_id = 2"));
        assertTrue(plainSelect.getWhere().toString().contains("u.username LIKE '%admin%'"));
    }

    private SysDataScopeConfig buildTenantScopeConfig() {
        SysDataScopeConfig config = new SysDataScopeConfig();
        config.setTableAlias("u");
        config.setTenantIdColumn("tenant_id");
        return config;
    }

    private DataScopeContext buildTenantScopeContext() {
        DataScopeContext context = new DataScopeContext();
        context.setTenantId(2L);
        return context;
    }

    private String invokeBuildDataScopeSql(DataScopeInterceptor interceptor, String originalSql,
            SysDataScopeConfig config, DataScopeContext context, DataScopeType scopeType) throws Exception {
        Method method = DataScopeInterceptor.class.getDeclaredMethod("buildDataScopeSql",
                String.class, SysDataScopeConfig.class, DataScopeContext.class, DataScopeType.class);
        method.setAccessible(true);
        return (String) method.invoke(interceptor, originalSql, config, context, scopeType);
    }
}
