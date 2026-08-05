package com.mdframe.forge.plugin.ai.health;

import com.mdframe.forge.plugin.ai.model.constant.AiModelType;
import com.mdframe.forge.plugin.ai.model.domain.AiModel;
import com.mdframe.forge.plugin.ai.model.mapper.AiModelMapper;
import com.mdframe.forge.plugin.ai.provider.adapter.AiModelRuntimeOptions;
import com.mdframe.forge.plugin.ai.provider.adapter.AiProviderAdapterRegistry;
import com.mdframe.forge.plugin.ai.provider.domain.AiProvider;
import com.mdframe.forge.plugin.ai.provider.service.AiProviderService;
import com.mdframe.forge.plugin.ai.provider.support.AiProviderFailureDiagnostics;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j @Service @RequiredArgsConstructor
public class AiModelConnectionTestService {
    private final AiModelMapper modelMapper;
    private final AiProviderService providerService;
    private final AiProviderAdapterRegistry adapterRegistry;
    private final AiModelHealthRegistry healthRegistry;
    private final AiModelFailureClassifier failureClassifier;

    public String test(Long modelPk) {
        AiModel model = modelMapper.selectEnabledById(modelPk);
        if (model == null) throw new BusinessException("模型不存在或已停用");
        AiProvider provider = providerService.getById(model.getProviderId());
        if (provider == null || !"0".equals(provider.getStatus())) throw new BusinessException("模型供应商不存在或已停用");
        Long tenantId = model.getTenantId() != null ? model.getTenantId() : SessionHelper.getTenantId();
        if (tenantId == null) throw new BusinessException("无法确定当前模型所属租户");

        AiModelType modelType = AiModelType.fromCode(model.getModelType());
        if (modelType == null) {
            modelType = AiModelType.CHAT;
        }

        // 按模型类型路由连接测试
        return switch (modelType) {
            case CHAT -> testChatModel(model, provider, tenantId);
            case EMBEDDING -> testEmbeddingModel(model, provider, tenantId);
            case RERANK -> testRerankModel(model, provider, tenantId);
            case IMAGE_GENERATION, ASR, TTS -> {
                log.info("[AI模型测试] 类型{}连接测试暂未实现, modelId={}", modelType.getCode(), model.getId());
                throw new BusinessException(modelType.getCode() + "类型模型连接测试暂未实现，将在后续版本支持");
            }
        };
    }

    private String testChatModel(AiModel model, AiProvider provider, Long tenantId) {
        AiModelHealthKey key = new AiModelHealthKey(tenantId, provider.getId(), model.getId());
        AiModelHealthLease lease = healthRegistry.acquireManualProbe(key);
        try {
            adapterRegistry.createChatModel(provider, new AiModelRuntimeOptions(model.getModelId(), 0D, 32))
                    .call(new Prompt(List.of(new UserMessage("请只回复 OK"))));
            lease.success();
            log.info("[AI模型测试] Chat连接成功, providerId={}, modelId={}", provider.getId(), model.getId());
            return "连接成功";
        } catch (Exception e) {
            return handleTestFailure(e, provider, model, lease);
        } finally {
            lease.close();
        }
    }

    private String testEmbeddingModel(AiModel model, AiProvider provider, Long tenantId) {
        // Embedding 模型测试：通过适配器注册表获取 Embedding 适配器，调用 embed 验证返回维度
        // 一期 Embedding 适配器尚未实现，返回提示信息
        log.info("[AI模型测试] Embedding连接测试将在Embedding适配器实现后支持, modelId={}", model.getId());
        throw new BusinessException("Embedding模型连接测试将在Embedding适配器实现后支持");
    }

    private String testRerankModel(AiModel model, AiProvider provider, Long tenantId) {
        // Rerank 模型测试：通过适配器注册表获取 Rerank 适配器，调用 rerank 验证返回分数
        // 一期 Rerank 适配器尚未实现，返回提示信息
        log.info("[AI模型测试] Rerank连接测试将在Rerank适配器实现后支持, modelId={}", model.getId());
        throw new BusinessException("Rerank模型连接测试将在Rerank适配器实现后支持");
    }

    private String handleTestFailure(Exception e, AiProvider provider, AiModel model, AiModelHealthLease lease) {
        AiModelFailureCategory category = failureClassifier.classify(e);
        if (category == AiModelFailureCategory.VALIDATION
                || category == AiModelFailureCategory.CONTENT_POLICY
                || category == AiModelFailureCategory.CANCELLED) {
            lease.cancel();
        } else {
            lease.failure(category);
        }
        AiProviderFailureDiagnostics d = AiProviderFailureDiagnostics.from(e);
        log.warn("[AI模型测试] 连接失败, providerId={}, modelId={}, category={}, httpStatus={}, errorCode={}, exceptionType={}",
                provider.getId(), model.getId(), category, d.httpStatus(), d.errorCode(), e.getClass().getSimpleName());
        throw new BusinessException("模型连接失败，请检查供应商配置和模型标识");
    }
}
