package com.mdframe.forge.plugin.capability.identity.external;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 外部用户标识的不可逆指纹和低泄露展示提示。
 */
final class ExternalIdentityFingerprint {

    private ExternalIdentityFingerprint() {
    }

    static String sha256(String value) {
        String normalized = requireSubject(value);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    static String subjectHint(String value) {
        String normalized = requireSubject(value);
        if (normalized.length() == 1) {
            return "***";
        }
        if (normalized.length() <= 4) {
            return normalized.charAt(0) + "***" + normalized.substring(normalized.length() - 1);
        }
        return normalized.substring(0, 2) + "***" + normalized.substring(normalized.length() - 2);
    }

    static String requireSubject(String value) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null || normalized.length() > 512) {
            throw new IllegalArgumentException("external subject is invalid");
        }
        return normalized;
    }
}
