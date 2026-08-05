package com.mdframe.forge.plugin.collaboration.provider.wecom;

import com.mdframe.forge.plugin.collaboration.domain.callback.VerifiedCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

/**
 * 企业微信回调验签与解密（对应官方 WXBizMsgCrypt 协议）。
 * <p>
 * SHA1 字典序验签 + AES/CBC/NoPadding 解密 + ReceiveId 校验，任一环节失败即失败关闭；
 * 异常消息只包含失败原因分类，不携带密钥、密文或明文。
 */
@Component
public class WeComCallbackCrypto {

    private static final int AES_KEY_LENGTH = 32;
    private static final int RANDOM_PREFIX_LENGTH = 16;
    private static final int MSG_LENGTH_BYTES = 4;
    private static final int MAX_PKCS7_PAD = 32;

    /**
     * 回调 URL 有效性验证（GET）：验签解密 echostr 并返回明文
     */
    public String verifyUrl(WeComCallbackRequest request, CallbackCredential credential) {
        return verifyAndDecrypt(request, credential).plaintext();
    }

    /**
     * 验签并解密回调密文，校验 ReceiveId 与 CorpId 一致
     */
    public CallbackVerificationResult verifyAndDecrypt(WeComCallbackRequest request, CallbackCredential credential) {
        if (request == null || !StringUtils.hasText(request.msgSignature())
                || !StringUtils.hasText(request.timestamp())
                || !StringUtils.hasText(request.nonce())
                || !StringUtils.hasText(request.encrypted())) {
            throw new WeComCallbackException("回调验签参数不完整");
        }
        if (credential == null || !StringUtils.hasText(credential.token())
                || !StringUtils.hasText(credential.encodingAesKey())) {
            throw new WeComCallbackException("回调凭据未配置");
        }
        verifySignature(request, credential.token());
        byte[] plainBytes = decrypt(request.encrypted(), credential.encodingAesKey());
        return parsePlainBuffer(plainBytes, credential.corpId());
    }

    /**
     * 从回调正文 XML 中提取 Encrypt 密文节点（禁用外部实体，防 XXE）
     */
    public String extractEncrypt(String xmlBody) {
        String encrypt = firstTagText(parseXml(xmlBody), "Encrypt");
        if (!StringUtils.hasText(encrypt)) {
            throw new WeComCallbackException("回调正文缺少 Encrypt 节点");
        }
        return encrypt;
    }

    /**
     * 解析明文事件 XML 的元信息，组装收件箱事件
     */
    public VerifiedCallback parseEvent(String plaintext, WeComCallbackRequest request) {
        Document document = parseXml(plaintext);
        String event = firstTagText(document, "Event");
        String changeType = firstTagText(document, "ChangeType");
        String eventType = null;
        if (StringUtils.hasText(event)) {
            eventType = StringUtils.hasText(changeType) ? event + "." + changeType : event;
        }
        LocalDateTime eventTime = null;
        String createTime = firstTagText(document, "CreateTime");
        if (StringUtils.hasText(createTime)) {
            try {
                eventTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(Long.parseLong(createTime.trim())), ZoneId.systemDefault());
            } catch (NumberFormatException ignored) {
                // CreateTime 非法时不阻断受理，事件时间留空
            }
        }
        String eventId = firstTagText(document, "MsgId");
        return new VerifiedCallback(eventId, eventType, eventTime, plaintext,
                request.msgSignature(), request.timestamp(), request.nonce());
    }

    private void verifySignature(WeComCallbackRequest request, String token) {
        String[] parts = {token, request.timestamp(), request.nonce(), request.encrypted()};
        Arrays.sort(parts);
        String expected = sha1Hex(String.join("", parts));
        String actual = request.msgSignature().trim().toLowerCase(Locale.ROOT);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8))) {
            throw new WeComCallbackException("回调签名校验失败");
        }
    }

    private byte[] decrypt(String encrypted, String encodingAesKey) {
        byte[] aesKey;
        try {
            aesKey = Base64.getDecoder().decode(encodingAesKey + "=");
        } catch (IllegalArgumentException e) {
            throw new WeComCallbackException("EncodingAESKey 格式非法");
        }
        if (aesKey.length != AES_KEY_LENGTH) {
            throw new WeComCallbackException("EncodingAESKey 长度非法");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new IvParameterSpec(aesKey, 0, 16));
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            return removePkcs7Padding(decrypted);
        } catch (WeComCallbackException e) {
            throw e;
        } catch (Exception e) {
            throw new WeComCallbackException("回调密文解密失败");
        } finally {
            Arrays.fill(aesKey, (byte) 0);
        }
    }

    private byte[] removePkcs7Padding(byte[] decrypted) {
        if (decrypted.length == 0) {
            throw new WeComCallbackException("回调密文解密失败");
        }
        int pad = decrypted[decrypted.length - 1] & 0xFF;
        if (pad < 1 || pad > MAX_PKCS7_PAD || pad > decrypted.length) {
            throw new WeComCallbackException("回调密文填充非法");
        }
        return Arrays.copyOfRange(decrypted, 0, decrypted.length - pad);
    }

    private CallbackVerificationResult parsePlainBuffer(byte[] plainBytes, String expectedCorpId) {
        if (plainBytes.length < RANDOM_PREFIX_LENGTH + MSG_LENGTH_BYTES) {
            throw new WeComCallbackException("回调明文结构非法");
        }
        int msgLength = ByteBuffer.wrap(plainBytes, RANDOM_PREFIX_LENGTH, MSG_LENGTH_BYTES).getInt();
        int msgStart = RANDOM_PREFIX_LENGTH + MSG_LENGTH_BYTES;
        if (msgLength < 0 || msgStart + msgLength > plainBytes.length) {
            throw new WeComCallbackException("回调明文长度非法");
        }
        String plaintext = new String(plainBytes, msgStart, msgLength, StandardCharsets.UTF_8);
        String receiveId = new String(plainBytes, msgStart + msgLength,
                plainBytes.length - msgStart - msgLength, StandardCharsets.UTF_8);
        if (StringUtils.hasText(expectedCorpId) && !expectedCorpId.equals(receiveId)) {
            throw new WeComCallbackException("回调 ReceiveId 与连接 CorpId 不一致");
        }
        return new CallbackVerificationResult(plaintext, receiveId);
    }

    private Document parseXml(String xml) {
        if (!StringUtils.hasText(xml)) {
            throw new WeComCallbackException("回调 XML 内容为空");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new WeComCallbackException("回调 XML 解析失败");
        }
    }

    private String firstTagText(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? null : text.trim();
    }

    private String sha1Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new WeComCallbackException("签名摘要计算失败");
        }
    }
}
