package com.mdframe.forge.plugin.ai.model.adapter;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AiModelAdapterRegistryTest {

    @Test
    void noMatchThrowsBusinessException() {
        AiModelAdapterRegistry registry = new AiModelAdapterRegistry(List.of(), List.of(), List.of(), List.of(), List.of());
        assertThrows(BusinessException.class, () -> registry.getEmbedding("nonexistent-model"));
        assertThrows(BusinessException.class, () -> registry.getRerank("nonexistent-model"));
    }

    @Test
    void embeddingAdapterSupportsMatchingModelKeys() {
        OpenAiCompatibleEmbeddingModelAdapter adapter = new OpenAiCompatibleEmbeddingModelAdapter();
        assertTrue(adapter.supports("text-embedding-3-small"));
        assertTrue(adapter.supports("embedding-3"));
        assertTrue(adapter.supports("bge-large-zh"));
        assertFalse(adapter.supports("gpt-4o"));
        assertFalse(adapter.supports(null));
        assertEquals("openai_compatible", adapter.getSupportedProvider());
    }

    @Test
    void rerankAdapterSupportsMatchingModelKeys() {
        OpenAiCompatibleRerankModelAdapter adapter = new OpenAiCompatibleRerankModelAdapter();
        assertTrue(adapter.supports("jina-rerank-v2"));
        assertTrue(adapter.supports("bge-rerank-large"));
        assertTrue(adapter.supports("cohere-rerank-v3"));
        assertFalse(adapter.supports("gpt-4o"));
        assertFalse(adapter.supports(null));
        assertEquals("openai_compatible", adapter.getSupportedProvider());
    }
}
