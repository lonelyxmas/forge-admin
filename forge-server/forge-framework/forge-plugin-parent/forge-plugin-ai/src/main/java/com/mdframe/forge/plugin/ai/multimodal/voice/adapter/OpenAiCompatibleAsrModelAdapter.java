package com.mdframe.forge.plugin.ai.multimodal.voice.adapter;

import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容协议的 ASR 模型适配器。
 * 覆盖 OpenAI / Azure / DashScope 兼容等供应商。
 */
@Slf4j
@Component
public class OpenAiCompatibleAsrModelAdapter implements AiAsrModelAdapter {

    @Override
    public String getSupportedProvider() {
        return "openai_compatible";
    }

    @Override
    public boolean supports(String modelKey) {
        if (modelKey == null) {
            return false;
        }
        String lower = modelKey.toLowerCase();
        return lower.startsWith("whisper")
                || lower.contains("asr")
                || lower.contains("speech-to-text")
                || lower.contains("paraformer");
    }

    @Override
    public String transcribe(String baseUrl, String apiKey, String model, byte[] audio, String mimeType) {
        try {
            OpenAiAudioApi audioApi = OpenAiAudioApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .build();

            OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
                    .model(model)
                    .build();

            OpenAiAudioTranscriptionModel transcriptionModel =
                    new OpenAiAudioTranscriptionModel(audioApi, options);

            ByteArrayResource audioResource = new ByteArrayResource(audio);
            AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(audioResource, options);
            AudioTranscriptionResponse response = transcriptionModel.call(prompt);

            AudioTranscription transcript = response.getResult();
            if (transcript == null || transcript.getOutput() == null) {
                throw new BusinessException("语音识别返回空结果");
            }

            return transcript.getOutput();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI ASR] 语音识别失败, baseUrl={}, model={}, error={}", baseUrl, model, e.getMessage());
            throw new BusinessException("语音识别失败: " + e.getMessage());
        }
    }
}
