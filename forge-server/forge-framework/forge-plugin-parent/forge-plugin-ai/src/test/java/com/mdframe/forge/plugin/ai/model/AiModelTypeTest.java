package com.mdframe.forge.plugin.ai.model;

import com.mdframe.forge.plugin.ai.model.constant.AiModelType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiModelTypeTest {

    @Test
    void allCodesAreUniqueAndMatchModelTypes() {
        assertEquals("chat", AiModelType.CHAT.getCode());
        assertEquals("embedding", AiModelType.EMBEDDING.getCode());
        assertEquals("rerank", AiModelType.RERANK.getCode());
        assertEquals("image_generation", AiModelType.IMAGE_GENERATION.getCode());
        assertEquals("asr", AiModelType.ASR.getCode());
        assertEquals("tts", AiModelType.TTS.getCode());
    }

    @Test
    void fromCodeResolvesKnownAndUnknown() {
        assertEquals(AiModelType.CHAT, AiModelType.fromCode("chat"));
        assertEquals(AiModelType.EMBEDDING, AiModelType.fromCode("embedding"));
        assertEquals(AiModelType.RERANK, AiModelType.fromCode("rerank"));
        assertEquals(AiModelType.IMAGE_GENERATION, AiModelType.fromCode("image_generation"));
        assertEquals(AiModelType.ASR, AiModelType.fromCode("asr"));
        assertEquals(AiModelType.TTS, AiModelType.fromCode("tts"));
    }

    @Test
    void fromCodeMapsLegacyValues() {
        assertEquals(AiModelType.IMAGE_GENERATION, AiModelType.fromCode("image"));
        assertEquals(AiModelType.ASR, AiModelType.fromCode("audio"));
    }

    @Test
    void fromCodeReturnsNullForUnknownAndNull() {
        assertNull(AiModelType.fromCode("unknown_type"));
        assertNull(AiModelType.fromCode(null));
    }
}
