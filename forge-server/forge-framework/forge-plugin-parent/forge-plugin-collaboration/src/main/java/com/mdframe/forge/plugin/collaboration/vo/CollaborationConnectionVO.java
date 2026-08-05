package com.mdframe.forge.plugin.collaboration.vo;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import com.mdframe.forge.starter.social.domain.entity.SysSocialCapabilityBinding;
import com.mdframe.forge.starter.social.domain.vo.SocialConnectionSummaryVO;
import com.mdframe.forge.starter.social.security.SecretSummary;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业协同连接详情 VO（Task 18）。
 * <p>
 * 组合连接摘要、物理应用与能力绑定；所有凭据只输出「是否已配置 + 固定掩码」，
 * 禁止任何密文、外部引用或明文出现在响应中。
 */
@Data
public class CollaborationConnectionVO {

    /** 连接摘要（旧凭据仅掩码状态） */
    private SocialConnectionSummaryVO connection;

    /** 连接下物理应用（凭据脱敏） */
    private List<AppVO> apps;

    /** 能力绑定关系 */
    private List<CapabilityBindingVO> bindings;

    /**
     * 物理应用脱敏视图
     */
    @Data
    public static class AppVO {

        private Long id;
        private String appCode;
        private String appName;
        private String clientId;
        private String agentId;
        /** Secret存储模式：CIPHER/EXTERNAL_REF */
        private String secretMode;
        private Boolean secretConfigured;
        private String secretMasked;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime secretUpdateTime;
        private Boolean callbackTokenConfigured;
        private Boolean encodingAesKeyConfigured;
        private String redirectUri;
        private String scope;
        private Integer status;
        private String remark;

        public static AppVO from(SysSocialAppConfig app, SecretSummary secretSummary) {
            AppVO vo = new AppVO();
            vo.setId(app.getId());
            vo.setAppCode(app.getAppCode());
            vo.setAppName(app.getAppName());
            vo.setClientId(app.getClientId());
            vo.setAgentId(app.getAgentId());
            vo.setSecretMode(app.getSecretMode());
            vo.setSecretConfigured(secretSummary != null && secretSummary.configured());
            vo.setSecretMasked(secretSummary != null ? secretSummary.masked() : "");
            vo.setSecretUpdateTime(app.getSecretUpdateTime());
            vo.setCallbackTokenConfigured(StrUtil.isNotBlank(app.getCallbackTokenCipher()));
            vo.setEncodingAesKeyConfigured(StrUtil.isNotBlank(app.getEncodingAesKeyCipher()));
            vo.setRedirectUri(app.getRedirectUri());
            vo.setScope(app.getScope());
            vo.setStatus(app.getStatus());
            vo.setRemark(app.getRemark());
            return vo;
        }
    }

    /**
     * 能力绑定视图
     */
    @Data
    public static class CapabilityBindingVO {

        private Long id;
        /** 业务能力：LOGIN/DIRECTORY/MESSAGE/TODO/CALLBACK */
        private String capability;
        private Long appConfigId;
        private Integer status;
        private String remark;

        public static CapabilityBindingVO from(SysSocialCapabilityBinding binding) {
            CapabilityBindingVO vo = new CapabilityBindingVO();
            vo.setId(binding.getId());
            vo.setCapability(binding.getCapability());
            vo.setAppConfigId(binding.getAppConfigId());
            vo.setStatus(binding.getStatus());
            vo.setRemark(binding.getRemark());
            return vo;
        }
    }
}
