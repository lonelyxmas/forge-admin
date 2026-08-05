package com.mdframe.forge.starter.crypto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 内部服务调用来源配置。
 */
@Data
@ConfigurationProperties(prefix = "forge.crypto.internal-call")
public class InternalCallProperties {

    /**
     * 允许使用 X-Inner-Call 的直接对端 IP 或 CIDR，不读取客户端可伪造的转发头。
     */
    private List<String> trustedAddresses = new ArrayList<>(List.of(
            "127.0.0.1",
            "::1",
            "0:0:0:0:0:0:0:1"
    ));
}
