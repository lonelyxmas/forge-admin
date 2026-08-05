package com.mdframe.forge.starter.idempotent.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.starter.idempotent.util.SpelUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class DefaultIdempotentKeyGenerator implements IdempotentKeyGenerator {
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final ObjectMapper stableObjectMapper;

    public DefaultIdempotentKeyGenerator(ObjectMapper objectMapper) {
        this.stableObjectMapper = objectMapper.copy();
    }

    @Override
    public String generate(ProceedingJoinPoint joinPoint, String prefix, String key) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);

        String keyValue;
        if (key != null && !key.isEmpty()) {
            Object spelResult = SpelUtil.parse(key, args, paramNames);
            keyValue = spelResult != null ? spelResult.toString() : "";
        } else {
            String methodSign = method.getDeclaringClass().getName() + ":" + method.getName();
            keyValue = sha256(methodSign + ":" + serializeArguments(args));
        }
        return prefix + keyValue;
    }

    private String serializeArguments(Object[] args) {
        try {
            JsonNode tree = stableObjectMapper.valueToTree(args == null ? new Object[0] : args);
            return stableObjectMapper.writeValueAsString(canonicalize(tree));
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new IllegalStateException("无法稳定序列化幂等参数，请通过 SpEL 显式指定幂等 key", e);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            Map<String, JsonNode> fields = new TreeMap<>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
            ObjectNode result = stableObjectMapper.createObjectNode();
            fields.forEach((name, value) -> result.set(name, canonicalize(value)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = stableObjectMapper.createArrayNode();
            node.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return node;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }
}
