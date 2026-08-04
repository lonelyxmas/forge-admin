package com.mdframe.forge.plugin.capability.identity.external;

import com.mdframe.forge.plugin.capability.identity.domain.AiCapabilityExternalIdentity;
import com.mdframe.forge.plugin.capability.identity.mapper.AiCapabilityExternalIdentityMapper;
import com.mdframe.forge.plugin.capability.identity.security.CapabilityIdentityInfrastructureException;
import com.mdframe.forge.plugin.system.service.IUserLoadService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalIdentityMappingServiceTest {

    private OidcExternalIdentityVerifier verifier;
    private AiCapabilityExternalIdentityMapper identityMapper;
    private IUserLoadService userLoadService;
    private ExternalIdentityMappingService service;

    @BeforeEach
    void setUp() {
        verifier = mock(OidcExternalIdentityVerifier.class);
        identityMapper = mock(AiCapabilityExternalIdentityMapper.class);
        userLoadService = mock(IUserLoadService.class);
        service = new ExternalIdentityMappingService(
                verifier, identityMapper, userLoadService,
                Clock.fixed(Instant.parse("2026-08-01T05:00:00Z"), ZoneOffset.UTC));
        when(identityMapper.insert(any(AiCapabilityExternalIdentity.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AiCapabilityExternalIdentity.class).setId(700L);
            return 1;
        });
        when(identityMapper.touchAuthenticated(1L, 700L, java.time.LocalDateTime.of(
                2026, 8, 1, 5, 0))).thenReturn(1);
    }

    @Test
    void shouldMatchPhoneOnlyOnFirstLoginAndPersistHashedStableSubject() {
        ExternalIdentityClaims claims = new ExternalIdentityClaims(
                "partner", "https://id.example.com", "stable-subject", 1L,
                "13800000000", "张三", 201L);
        LoginUser user = user();
        when(verifier.verify("jwt-token")).thenReturn(claims);
        when(userLoadService.loadUniqueUserByVerifiedPhone(
                "13800000000", 1L, 201L)).thenReturn(user);

        ResolvedExternalIdentity resolved = service.authenticate("jwt-token");

        assertThat(resolved.loginUser().getUserId()).isEqualTo(101L);
        ArgumentCaptor<AiCapabilityExternalIdentity> mapping =
                ArgumentCaptor.forClass(AiCapabilityExternalIdentity.class);
        verify(identityMapper).insert(mapping.capture());
        assertThat(mapping.getValue().getIssuerHash()).hasSize(64);
        assertThat(mapping.getValue().getSubjectHash()).hasSize(64);
        assertThat(mapping.getValue().getUserId()).isEqualTo(101L);
        assertThat(mapping.getValue().toString()).doesNotContain("13800000000", "张三", "jwt-token");
    }

    @Test
    void shouldUseExistingIssuerSubjectMappingWithoutPhoneLookup() {
        ExternalIdentityClaims claims = new ExternalIdentityClaims(
                "partner", "https://id.example.com", "stable-subject", 1L,
                null, null, 201L);
        AiCapabilityExternalIdentity mapping = new AiCapabilityExternalIdentity();
        mapping.setId(700L);
        mapping.setUserId(101L);
        when(verifier.verify("jwt-token")).thenReturn(claims);
        when(identityMapper.selectActive(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("partner"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(mapping);
        when(userLoadService.loadUserByUserId(101L, 1L, 201L)).thenReturn(user());

        ResolvedExternalIdentity resolved = service.authenticate("jwt-token");

        assertThat(resolved.loginUser().getUserId()).isEqualTo(101L);
        verify(userLoadService, never()).loadUniqueUserByVerifiedPhone(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
        verify(identityMapper, never()).insert(any(AiCapabilityExternalIdentity.class));
    }

    @Test
    void shouldRejectDirectoryDomainFailureAsInvalidGrant() {
        ExternalIdentityClaims claims = new ExternalIdentityClaims(
                "partner", "https://id.example.com", "stable-subject", 1L,
                "13800000000", "张三", 201L);
        when(verifier.verify("jwt-token")).thenReturn(claims);
        when(userLoadService.loadUniqueUserByVerifiedPhone(
                "13800000000", 1L, 201L)).thenThrow(new RuntimeException("用户不存在或手机号重复"));

        assertThatThrownBy(() -> service.authenticate("jwt-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).isEqualTo("invalid_grant");
                });
    }

    @Test
    void shouldRejectMismatchedVerifiedNameOnFirstLogin() {
        ExternalIdentityClaims claims = new ExternalIdentityClaims(
                "partner", "https://id.example.com", "stable-subject", 1L,
                "13800000000", "李四", 201L);
        when(verifier.verify("jwt-token")).thenReturn(claims);
        when(userLoadService.loadUniqueUserByVerifiedPhone(
                "13800000000", 1L, 201L)).thenReturn(user());

        assertThatThrownBy(() -> service.authenticate("jwt-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).isEqualTo("invalid_grant");
                });
        verify(identityMapper, never()).insert(any(AiCapabilityExternalIdentity.class));
    }

    @Test
    void shouldFailClosedWhenUserDirectoryDatabaseIsUnavailable() {
        ExternalIdentityClaims claims = new ExternalIdentityClaims(
                "partner", "https://id.example.com", "stable-subject", 1L,
                "13800000000", "张三", 201L);
        when(verifier.verify("jwt-token")).thenReturn(claims);
        when(userLoadService.loadUniqueUserByVerifiedPhone(
                "13800000000", 1L, 201L)).thenThrow(
                new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> service.authenticate("jwt-token"))
                .isInstanceOf(CapabilityIdentityInfrastructureException.class)
                .hasMessage("Forge 用户目录暂不可用");
    }

    private LoginUser user() {
        LoginUser user = new LoginUser();
        user.setUserId(101L);
        user.setTenantId(1L);
        user.setActiveOrgId(201L);
        user.setRealName("张三");
        user.setUserStatus(1);
        user.setForcePasswordChange(false);
        user.setRoleIds(List.of(301L));
        return user;
    }
}
