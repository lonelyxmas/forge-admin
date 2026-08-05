package com.mdframe.forge.plugin.collaboration.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.mdframe.forge.plugin.collaboration.domain.callback.VerifiedCallback;
import com.mdframe.forge.plugin.collaboration.provider.wecom.CallbackCredential;
import com.mdframe.forge.plugin.collaboration.provider.wecom.CallbackVerificationResult;
import com.mdframe.forge.plugin.collaboration.provider.wecom.WeComCallbackCrypto;
import com.mdframe.forge.plugin.collaboration.provider.wecom.WeComCallbackException;
import com.mdframe.forge.plugin.collaboration.provider.wecom.WeComCallbackRequest;
import com.mdframe.forge.plugin.collaboration.service.CollaborationCallbackInboxService;
import com.mdframe.forge.starter.core.annotation.api.ApiPermissionIgnore;
import com.mdframe.forge.starter.core.annotation.tenant.IgnoreTenant;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import com.mdframe.forge.starter.social.service.ISocialAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;

/**
 * 企业协同回调入口（Task 7，匿名端点）。
 * <p>
 * GET 为回调 URL 有效性验证，POST 为事件接收；验签解密通过后写入幂等收件箱即快速应答，
 * 不等待目录同步或流程处理。所有失败只记录连接/应用编码与失败原因，禁止记录密文与凭据。
 */
@Slf4j
@SaIgnore
@IgnoreTenant
@ApiPermissionIgnore
@RestController
@RequestMapping("/collaboration/callback")
@RequiredArgsConstructor
public class CollaborationCallbackController {

    /**
     * 回调时间戳允许偏差（秒），超窗直接拒绝
     */
    private static final long TIMESTAMP_WINDOW_SECONDS = 300;

    /**
     * 回调正文大小上限（64KB），超大正文直接拒绝
     */
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final CollaborationCallbackInboxService inboxService;
    private final WeComCallbackCrypto callbackCrypto;
    private final ISocialAppConfigService socialAppConfigService;

    /**
     * 回调 URL 有效性验证（企微 GET）：验签解密 echostr 并原样应答明文
     */
    @GetMapping("/{connectionCode}/{appCode}")
    public ResponseEntity<String> verifyUrl(@PathVariable String connectionCode,
                                            @PathVariable String appCode,
                                            @RequestParam("msg_signature") String msgSignature,
                                            @RequestParam("timestamp") String timestamp,
                                            @RequestParam("nonce") String nonce,
                                            @RequestParam("echostr") String echostr) {
        try {
            checkTimestampWindow(timestamp);
            SysSocialConfig connection = inboxService.requireConnection(connectionCode);
            SysSocialAppConfig app = inboxService.requireApp(connection, appCode);
            WeComCallbackRequest request = new WeComCallbackRequest(msgSignature, timestamp, nonce, echostr);
            return ResponseEntity.ok(verify(request, connection, app).plaintext());
        } catch (Exception e) {
            log.warn("协同回调URL验证失败：connectionCode={}, appCode={}, reason={}",
                    connectionCode, appCode, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 回调事件接收（企微 POST）：验签解密后写入收件箱，快速应答 success
     */
    @PostMapping("/{connectionCode}/{appCode}")
    public ResponseEntity<String> receiveEvent(@PathVariable String connectionCode,
                                               @PathVariable String appCode,
                                               @RequestParam("msg_signature") String msgSignature,
                                               @RequestParam("timestamp") String timestamp,
                                               @RequestParam("nonce") String nonce,
                                               @RequestBody String body) {
        try {
            checkTimestampWindow(timestamp);
            checkBodySize(body);
            SysSocialConfig connection = inboxService.requireConnection(connectionCode);
            SysSocialAppConfig app = inboxService.requireApp(connection, appCode);
            String encrypted = callbackCrypto.extractEncrypt(body);
            WeComCallbackRequest request = new WeComCallbackRequest(msgSignature, timestamp, nonce, encrypted);
            CallbackVerificationResult result = verify(request, connection, app);
            VerifiedCallback callback = callbackCrypto.parseEvent(result.plaintext(), request);
            inboxService.accept(connection, app, callback);
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.warn("协同回调事件受理失败：connectionCode={}, appCode={}, reason={}",
                    connectionCode, appCode, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    private CallbackVerificationResult verify(WeComCallbackRequest request,
                                              SysSocialConfig connection, SysSocialAppConfig app) {
        char[] token = socialAppConfigService.decryptCallbackToken(app);
        char[] aesKey = socialAppConfigService.decryptEncodingAesKey(app);
        try {
            if (token == null || token.length == 0 || aesKey == null || aesKey.length == 0) {
                throw new WeComCallbackException("回调凭据未配置");
            }
            CallbackCredential credential = new CallbackCredential(
                    new String(token), new String(aesKey), connection.getEnterpriseId());
            return callbackCrypto.verifyAndDecrypt(request, credential);
        } finally {
            if (token != null) {
                Arrays.fill(token, '\0');
            }
            if (aesKey != null) {
                Arrays.fill(aesKey, '\0');
            }
        }
    }

    private void checkTimestampWindow(String timestamp) {
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            throw new WeComCallbackException("回调时间戳非法");
        }
        if (Math.abs(Instant.now().getEpochSecond() - ts) > TIMESTAMP_WINDOW_SECONDS) {
            throw new WeComCallbackException("回调时间戳超出允许窗口");
        }
    }

    private void checkBodySize(String body) {
        if (body == null || body.isBlank()) {
            throw new WeComCallbackException("回调正文为空");
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            throw new WeComCallbackException("回调正文超过大小上限");
        }
    }
}
