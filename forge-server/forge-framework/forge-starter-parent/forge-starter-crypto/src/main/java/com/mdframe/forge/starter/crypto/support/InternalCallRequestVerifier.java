package com.mdframe.forge.starter.crypto.support;

import com.mdframe.forge.starter.crypto.config.InternalCallProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 校验内部调用标记是否来自受信任的直接网络对端。
 */
@Slf4j
@RequiredArgsConstructor
public class InternalCallRequestVerifier {

    public static final String INNER_CALL_HEADER = "X-Inner-Call";

    private final InternalCallProperties properties;

    public boolean isTrustedInternalCall(HttpServletRequest request) {
        if (!"true".equalsIgnoreCase(request.getHeader(INNER_CALL_HEADER))) {
            return false;
        }

        String remoteAddress = request.getRemoteAddr();
        List<String> trustedAddresses = properties.getTrustedAddresses();
        if (remoteAddress != null && trustedAddresses != null) {
            for (String trustedAddress : trustedAddresses) {
                if (matches(remoteAddress, trustedAddress)) {
                    return true;
                }
            }
        }

        log.warn("拒绝不可信来源使用内部调用标记: remoteAddress={}", remoteAddress);
        return false;
    }

    private boolean matches(String remoteAddress, String trustedAddress) {
        if (trustedAddress == null || trustedAddress.isBlank()) {
            return false;
        }
        try {
            InetAddress remote = InetAddress.getByName(remoteAddress);
            String rule = trustedAddress.trim();
            int separator = rule.indexOf('/');
            if (separator < 0) {
                return remote.equals(InetAddress.getByName(rule));
            }

            InetAddress network = InetAddress.getByName(rule.substring(0, separator));
            byte[] remoteBytes = remote.getAddress();
            byte[] networkBytes = network.getAddress();
            if (remoteBytes.length != networkBytes.length) {
                return false;
            }

            int prefixLength = Integer.parseInt(rule.substring(separator + 1));
            if (prefixLength < 0 || prefixLength > remoteBytes.length * Byte.SIZE) {
                log.warn("忽略非法内部调用 CIDR 配置: {}", trustedAddress);
                return false;
            }
            return matchesPrefix(remoteBytes, networkBytes, prefixLength);
        } catch (UnknownHostException | NumberFormatException exception) {
            log.warn("忽略非法内部调用来源配置: {}", trustedAddress);
            return false;
        }
    }

    private boolean matchesPrefix(byte[] address, byte[] network, int prefixLength) {
        int completeBytes = prefixLength / Byte.SIZE;
        int remainingBits = prefixLength % Byte.SIZE;
        for (int index = 0; index < completeBytes; index++) {
            if (address[index] != network[index]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (Byte.SIZE - remainingBits);
        return (address[completeBytes] & mask) == (network[completeBytes] & mask);
    }
}
