package com.mdframe.forge.plugin.collaboration.provider.wecom;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.ProviderError;
import com.mdframe.forge.starter.outbound.client.SecureOutboundClient;
import com.mdframe.forge.starter.outbound.constant.OutboundScenes;
import com.mdframe.forge.starter.outbound.model.OutboundRequest;
import com.mdframe.forge.starter.outbound.model.OutboundResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 企业微信 API 传输客户端。
 * <p>
 * 统一通过安全出站客户端调用固定官方端点，解析 errcode/errmsg/requestId 并做平台无关错误分类；
 * Token 失效时主动失效缓存并单次重试；日志不记录 URL、Header、Token 和响应正文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComApiClient {

    private final SecureOutboundClient outboundClient;
    private final WeComAccessTokenProvider tokenProvider;
    private final WeComErrorClassifier errorClassifier;
    private final WeComEndpointResolver endpointResolver;

    /**
     * 执行企微 API 调用；access_token 失效时强制刷新后只重试一次
     */
    public <T> T execute(WeComRequest<T> request, CollaborationExecutionContext context) {
        try {
            return doExecute(request, context);
        } catch (WeComApiException exception) {
            if (exception.getError().category() != ProviderError.Category.TOKEN_INVALID) {
                throw exception;
            }
            tokenProvider.invalidate(context, request.getTokenType());
            return doExecute(request, context);
        }
    }

    private <T> T doExecute(WeComRequest<T> request, CollaborationExecutionContext context) {
        String token = tokenProvider.getAccessToken(context, request.getTokenType());
        String url = buildUrl(request, token, endpointResolver.resolveBaseUrl(context));
        byte[] body = request.getJsonBody() == null
                ? new byte[0]
                : request.getJsonBody().getBytes(StandardCharsets.UTF_8);
        OutboundResponse response = outboundClient.execute(OutboundRequest.builder()
                .scene(OutboundScenes.COLLABORATION_PROVIDER)
                .url(url)
                .method(request.getMethod())
                .contentType(body.length > 0 ? "application/json" : null)
                .body(body)
                .build());
        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            ProviderError error = errorClassifier.classify(response.getStatusCode(), 0,
                    null, response.firstHeader("x-request-id"));
            log.warn("企业微信接口HTTP异常: path={}, status={}, category={}",
                    request.getPath(), response.getStatusCode(), error.category());
            throw new WeComApiException(error);
        }
        JSONObject json = JSON.parseObject(response.bodyAsUtf8());
        if (json == null) {
            ProviderError error = new ProviderError(ProviderError.Category.PERMANENT,
                    null, "响应正文解析失败", response.firstHeader("x-request-id"));
            throw new WeComApiException(error);
        }
        int errcode = json.getIntValue("errcode", 0);
        if (errcode != 0) {
            String requestId = StringUtils.hasText(json.getString("requestid"))
                    ? json.getString("requestid")
                    : response.firstHeader("x-request-id");
            ProviderError error = errorClassifier.classify(response.getStatusCode(), errcode,
                    json.getString("errmsg"), requestId);
            log.warn("企业微信接口业务异常: path={}, errcode={}, category={}",
                    request.getPath(), errcode, error.category());
            throw new WeComApiException(error);
        }
        if (request.getResponseType() == null || request.getResponseType() == JSONObject.class) {
            @SuppressWarnings("unchecked")
            T result = (T) json;
            return result;
        }
        return json.toJavaObject(request.getResponseType());
    }

    private <T> String buildUrl(WeComRequest<T> request, String token, String baseUrl) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append(request.getPath())
                .append("?access_token=")
                .append(encode(token));
        for (Map.Entry<String, String> entry : request.getQueryParams().entrySet()) {
            url.append('&').append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return url.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
