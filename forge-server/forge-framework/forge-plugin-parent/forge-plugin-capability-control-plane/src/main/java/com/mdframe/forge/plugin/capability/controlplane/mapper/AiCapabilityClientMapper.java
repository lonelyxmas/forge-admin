package com.mdframe.forge.plugin.capability.controlplane.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityClient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiCapabilityClientMapper extends BaseMapper<AiCapabilityClient> {

    Page<AiCapabilityClient> selectPage(Page<AiCapabilityClient> page,
                                        @Param("tenantId") Long tenantId,
                                        @Param("keyword") String keyword,
                                        @Param("status") String status);

    AiCapabilityClient selectTenantById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    AiCapabilityClient selectByCode(@Param("tenantId") Long tenantId, @Param("clientCode") String clientCode);

    List<AiCapabilityClient> selectGrantOptions(@Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true")
    AiCapabilityClient selectCredentialByKeyId(@Param("keyId") String keyId);

    @InterceptorIgnore(tenantLine = "true")
    AiCapabilityClient selectCredentialById(@Param("id") Long id);

    @InterceptorIgnore(tenantLine = "true")
    int touchLastUsed(@Param("tenantId") Long tenantId,
                      @Param("id") Long id,
                      @Param("credentialVersion") Integer credentialVersion,
                      @Param("keyHash") String keyHash,
                      @Param("lastUsedAt") java.time.LocalDateTime lastUsedAt);

    int rotateCredential(@Param("tenantId") Long tenantId,
                         @Param("id") Long id,
                         @Param("credentialVersion") Integer credentialVersion,
                         @Param("keyId") String keyId,
                         @Param("keyPrefix") String keyPrefix,
                         @Param("keyHash") String keyHash);

    int rotateSigningKey(@Param("tenantId") Long tenantId,
                         @Param("id") Long id,
                         @Param("signingKeyVersion") Integer signingKeyVersion,
                         @Param("signingKeyCipher") String signingKeyCipher);

    int rotateUserAssertionKey(
            @Param("tenantId") Long tenantId,
            @Param("id") Long id,
            @Param("credentialVersion") Integer credentialVersion,
            @Param("expectedKeyVersion") Integer expectedKeyVersion,
            @Param("keyId") String keyId,
            @Param("publicKeyPem") String publicKeyPem);

    int disableUserAssertion(
            @Param("tenantId") Long tenantId,
            @Param("id") Long id,
            @Param("credentialVersion") Integer credentialVersion,
            @Param("expectedKeyVersion") Integer expectedKeyVersion);

    int revokeCredential(@Param("tenantId") Long tenantId,
                         @Param("id") Long id,
                         @Param("credentialVersion") Integer credentialVersion);

    int configureOAuth(@Param("tenantId") Long tenantId,
                       @Param("id") Long id,
                       @Param("credentialVersion") Integer credentialVersion,
                       @Param("oauthEnabled") Integer oauthEnabled,
                       @Param("oauthClientType") String oauthClientType);
}
