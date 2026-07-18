package com.mdframe.forge.starter.property;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

public class DbPropertySourcePostProcessor implements EnvironmentPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(DbPropertySourcePostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 1. 先加载数据库配置（从默认配置源，如application.properties）
        ConfigDbProperties configDbProperties = new ConfigDbProperties();
        Binder.get(environment).bind("config.datasource", Bindable.ofInstance(configDbProperties));
        if (configDbProperties.getUrl() == null || configDbProperties.getUsername() == null
                || configDbProperties.getPassword() == null) {
            return;
        }
        // 2. 创建数据源
        DataSource dataSource = DataSourceBuilder.create()
                .url(configDbProperties.getUrl())
                .username(configDbProperties.getUsername())
                .password(configDbProperties.getPassword())
                .driverClassName(configDbProperties.getDriverClassName())
                .build();

        // 3. 创建JdbcTemplate并加载属性（sys_config 与 sys_config_group 内存合并，分组优先）
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try {
            DbPropertySource dbPropertySource = new DbPropertySource(DbConfigLoader.load(jdbcTemplate));
            // 4. 将自定义PropertySource添加到环境中（优先级高于默认配置）
            environment.getPropertySources().addFirst(dbPropertySource);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        log.info("数据库配置源已注册");
    }
}
