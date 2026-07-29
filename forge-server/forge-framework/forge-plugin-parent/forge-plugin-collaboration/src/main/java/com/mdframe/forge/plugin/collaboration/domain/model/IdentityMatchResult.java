package com.mdframe.forge.plugin.collaboration.domain.model;

/**
 * 身份匹配结果。
 * <p>
 * 验收红线：手机号/邮箱相同不自动合并，命中已有用户一律 RAISE_ISSUE 人工确认。
 *
 * @param decision     匹配决策
 * @param bindingId    已有绑定ID（UPDATE_BOUND 时非空）
 * @param forgeUserId  已绑定的 Forge 用户ID（UPDATE_BOUND 时非空）
 * @param issueCode    问题码（RAISE_ISSUE 时非空）
 * @param issueSummary 脱敏问题摘要（RAISE_ISSUE 时非空，禁止明文手机号/邮箱/姓名）
 */
public record IdentityMatchResult(
        Decision decision,
        Long bindingId,
        Long forgeUserId,
        String issueCode,
        String issueSummary
) {

    /**
     * 匹配决策
     */
    public enum Decision {
        /** 已有绑定，更新既有用户与绑定 */
        UPDATE_BOUND,
        /** 无绑定且无冲突，按策略自动创建用户并绑定 */
        CREATE_NEW,
        /** 冲突或策略要求人工处理，建问题单 */
        RAISE_ISSUE,
        /** 策略仅绑定已有且无匹配，本批次跳过 */
        SKIP
    }

    public static IdentityMatchResult bound(Long bindingId, Long forgeUserId) {
        return new IdentityMatchResult(Decision.UPDATE_BOUND, bindingId, forgeUserId, null, null);
    }

    public static IdentityMatchResult createNew() {
        return new IdentityMatchResult(Decision.CREATE_NEW, null, null, null, null);
    }

    public static IdentityMatchResult issue(String issueCode, String issueSummary) {
        return new IdentityMatchResult(Decision.RAISE_ISSUE, null, null, issueCode, issueSummary);
    }

    public static IdentityMatchResult skip() {
        return new IdentityMatchResult(Decision.SKIP, null, null, null, null);
    }
}
