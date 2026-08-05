package com.mdframe.forge.starter.idempotent.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultIdempotentKeyGeneratorTest {

    private final DefaultIdempotentKeyGenerator generator =
            new DefaultIdempotentKeyGenerator(new ObjectMapper());

    @Test
    void shouldUsePojoContentInsteadOfObjectToString() throws Exception {
        String first = generate(new Request("A", 1));
        String sameContent = generate(new Request("A", 1));
        String different = generate(new Request("B", 1));

        assertThat(first).isEqualTo(sameContent);
        assertThat(first).isNotEqualTo(different);
    }

    @Test
    void shouldGenerateSameKeyForMapsWithDifferentInsertionOrder() throws Exception {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("tenantId", 1L);
        first.put("code", "A");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("code", "A");
        second.put("tenantId", 1L);

        assertThat(generate(first)).isEqualTo(generate(second));
    }

    private String generate(Object argument) throws Exception {
        Method method = SampleService.class.getDeclaredMethod("submit", Object.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{argument});
        return generator.generate(joinPoint, "idem:", "");
    }

    static class SampleService {
        void submit(Object request) {
        }
    }

    record Request(String code, int version) {
    }
}
