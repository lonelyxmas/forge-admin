package com.mdframe.forge.plugin.capability.identity.oauth;

import com.mdframe.forge.plugin.capability.controlplane.audit.CapabilityActorType;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityProperties;
import com.mdframe.forge.plugin.capability.identity.security.AuthenticatedCapabilityIdentity;
import com.mdframe.forge.plugin.capability.identity.security.CapabilitySecurityPrincipal;
import com.mdframe.forge.plugin.capability.identity.token.CapabilityAccessTokenService;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityUserInfoControllerTest {

    @Test
    void shouldReturnCurrentForgeUserInformationForDelegatedToken() {
        CapabilityAccessTokenService tokenService = mock(CapabilityAccessTokenService.class);
        CapabilityIdentityProperties properties = new CapabilityIdentityProperties();
        properties.setResource("http://localhost:8580/mcp");
        CapabilityUserInfoController controller = new CapabilityUserInfoController(
                tokenService, properties);
        LoginUser user = new LoginUser();
        user.setUserId(101L);
        user.setUsername("zhangsan");
        user.setRealName("张三");
        user.setPhone("13800000000");
        user.setTenantId(1L);
        user.setTenantName("默认租户");
        user.setActiveOrgId(201L);
        user.setActiveOrgName("审批中心");
        CapabilitySecurityPrincipal principal = new CapabilitySecurityPrincipal(
                301L, "partner_client", CapabilityActorType.USER, 101L, null,
                1L, 201L, 1, "token-key", "http://localhost:8580/mcp", Set.of());
        when(tokenService.authenticate("fdu_token", "http://localhost:8580/mcp", Set.of()))
                .thenReturn(new AuthenticatedCapabilityIdentity(principal, user));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer fdu_token");

        ResponseEntity<Map<String, Object>> response = controller.userInfo(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody())
                .containsEntry("sub", "101")
                .containsEntry("name", "张三")
                .containsEntry("phone_number", "13800000000")
                .containsEntry("active_org_id", "201")
                .containsEntry("client_id", "301");
    }
}
