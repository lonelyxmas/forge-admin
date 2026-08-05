package com.mdframe.forge.plugin.collaboration.message;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 企业协同消息模板校验策略（Task 13）。
 * <p>
 * 发送前对消息类型、正文/卡片字段长度和跳转 URL 做校验，非法或超长直接拒绝，
 * 避免把必然失败的请求发到平台侧；限制取企业微信文档的字节上限。
 */
@Component
public class CollaborationMessageTemplatePolicy {

    public static final String MSG_TYPE_TEXT = "text";
    public static final String MSG_TYPE_TEXTCARD = "textcard";

    private static final Set<String> SUPPORTED_MSG_TYPES = Set.of(MSG_TYPE_TEXT, MSG_TYPE_TEXTCARD);

    /** 企微 text.content 上限 2048 字节 */
    private static final int TEXT_CONTENT_MAX_BYTES = 2048;
    /** 企微 textcard.title 上限 128 字节 */
    private static final int CARD_TITLE_MAX_BYTES = 128;
    /** 企微 textcard.description 上限 512 字节 */
    private static final int CARD_DESCRIPTION_MAX_BYTES = 512;
    /** 企微 textcard.url 上限 2048 字节 */
    private static final int CARD_URL_MAX_BYTES = 2048;

    /** params 中指定消息类型的键 */
    public static final String PARAM_MSG_TYPE = "msgType";
    /** params 中指定卡片跳转链接的键 */
    public static final String PARAM_URL = "url";

    /**
     * 解析并校验消息类型；未指定时默认文本
     *
     * @return 规范化消息类型
     */
    public String resolveMsgType(Map<String, Object> params) {
        Object raw = params == null ? null : params.get(PARAM_MSG_TYPE);
        if (raw == null || !StringUtils.hasText(String.valueOf(raw))) {
            return MSG_TYPE_TEXT;
        }
        String msgType = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_MSG_TYPES.contains(msgType)) {
            return null;
        }
        return msgType;
    }

    /**
     * 校验消息内容；返回 null 表示通过，否则返回拒绝原因（不含消息内容本身）
     */
    public String validate(String msgType, String title, String content, String url) {
        if (msgType == null) {
            return "不支持的消息类型";
        }
        if (!StringUtils.hasText(content)) {
            return "消息正文不能为空";
        }
        if (MSG_TYPE_TEXT.equals(msgType)) {
            if (utf8Length(content) > TEXT_CONTENT_MAX_BYTES) {
                return "文本消息正文超过" + TEXT_CONTENT_MAX_BYTES + "字节上限";
            }
            return null;
        }
        // 模板卡片：标题与跳转链接必填
        if (!StringUtils.hasText(title)) {
            return "卡片消息标题不能为空";
        }
        if (utf8Length(title) > CARD_TITLE_MAX_BYTES) {
            return "卡片消息标题超过" + CARD_TITLE_MAX_BYTES + "字节上限";
        }
        if (utf8Length(content) > CARD_DESCRIPTION_MAX_BYTES) {
            return "卡片消息描述超过" + CARD_DESCRIPTION_MAX_BYTES + "字节上限";
        }
        if (!StringUtils.hasText(url)) {
            return "卡片消息跳转链接不能为空";
        }
        if (utf8Length(url) > CARD_URL_MAX_BYTES) {
            return "卡片消息跳转链接超过" + CARD_URL_MAX_BYTES + "字节上限";
        }
        if (!isHttpUrl(url)) {
            return "卡片消息跳转链接必须是http/https地址";
        }
        return null;
    }

    /**
     * 从 params 提取卡片跳转链接
     */
    public String resolveUrl(Map<String, Object> params) {
        Object raw = params == null ? null : params.get(PARAM_URL);
        return raw == null ? null : String.valueOf(raw).trim();
    }

    private int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private boolean isHttpUrl(String url) {
        try {
            String scheme = URI.create(url).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
