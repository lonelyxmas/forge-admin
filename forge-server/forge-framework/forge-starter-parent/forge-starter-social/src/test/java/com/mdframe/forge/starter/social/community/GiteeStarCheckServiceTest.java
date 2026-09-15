package com.mdframe.forge.starter.social.community;

import com.mdframe.forge.starter.config.config.LoginConfig;
import com.mdframe.forge.starter.config.service.ConfigManagerService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GiteeStarCheckServiceTest {

    private GiteeStarCheckService service;

    @BeforeEach
    void setUp() {
        LoginConfig loginConfig = new LoginConfig();
        loginConfig.setGiteeCommunityEnabled(true);
        loginConfig.setGiteeCommunityRequireStar(true);
        loginConfig.setGiteeCommunityRepoUrl("https://gitee.com/ForgeLab/forge-admin");
        ConfigManagerService configManagerService = mock(ConfigManagerService.class);
        when(configManagerService.getLoginConfig()).thenReturn(loginConfig);
        GiteeCommunityLoginSupport support = new GiteeCommunityLoginSupport(configManagerService);
        service = new GiteeStarCheckService(support);
    }

    @Test
    void starredReturnsQuietly() {
        assertDoesNotThrow(() -> service.interpret(204, "https://gitee.com/ForgeLab/forge-admin"));
    }

    @Test
    void notStarredTellsUserToStarRepo() {
        assertThatThrownBy(() -> service.interpret(404, "https://gitee.com/ForgeLab/forge-admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("点 Star")
                .hasMessageContaining("https://gitee.com/ForgeLab/forge-admin");
    }

    @Test
    void missingTokenFailsClosed() {
        assertThatThrownBy(() -> service.assertStarred(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("授权");
    }

    @Test
    void springCreatesServiceThroughProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ConfigManagerService.class, () -> mock(ConfigManagerService.class));
            context.register(GiteeCommunityLoginSupport.class, GiteeStarCheckService.class);

            context.refresh();

            assertThat(context.getBeansOfType(GiteeStarCheckService.class)).hasSize(1);
        }
    }
}
