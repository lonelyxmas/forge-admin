package com.mdframe.forge.plugin.collaboration.provider.wecom;

import com.mdframe.forge.starter.collaboration.connector.AccessTokenProvider;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 企业微信 API 请求描述。
 * <p>
 * 只描述固定官方端点的路径、查询参数与 JSON 正文；access_token 由传输层按
 * {@link AccessTokenProvider.TokenType} 自动附加，调用方禁止在参数中携带 Token。
 *
 * @param <T> 响应映射类型
 */
@Getter
@Builder
public class WeComRequest<T> {

    /** 官方端点路径，如 /cgi-bin/department/list */
    private final String path;

    @Builder.Default
    private final String method = "GET";

    /** 查询参数（不含 access_token） */
    @Builder.Default
    private final Map<String, String> queryParams = Map.of();

    /** POST JSON 正文，可为空 */
    private final String jsonBody;

    /** 所需 Token 类型，默认应用级 Token */
    @Builder.Default
    private final AccessTokenProvider.TokenType tokenType = AccessTokenProvider.TokenType.APP;

    /** 响应映射类型（fastjson2 反序列化目标） */
    private final Class<T> responseType;
}
