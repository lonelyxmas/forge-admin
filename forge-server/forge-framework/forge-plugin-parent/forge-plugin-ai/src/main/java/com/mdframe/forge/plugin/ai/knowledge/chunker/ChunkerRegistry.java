package com.mdframe.forge.plugin.ai.knowledge.chunker;

import com.mdframe.forge.plugin.ai.knowledge.chunker.dto.ChunkResult;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分块器注册表。按策略名称路由到具体分块器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkerRegistry {

    private final List<DocumentChunker> chunkers;
    private Map<String, DocumentChunker> chunkerMap;

    private Map<String, DocumentChunker> getChunkerMap() {
        if (chunkerMap == null) {
            chunkerMap = chunkers.stream()
                    .collect(Collectors.toMap(
                            DocumentChunker::getStrategy,
                            Function.identity(),
                            (a, b) -> a
                    ));
        }
        return chunkerMap;
    }

    /**
     * 分块
     *
     * @param content    文档全文
     * @param strategy   分块策略（length/delimiter/regex/smart/qa）
     * @param configJson 分块参数JSON
     * @return 分块结果列表
     */
    public List<ChunkResult> chunk(String content, String strategy, String configJson) {
        if (strategy == null || strategy.isBlank()) {
            strategy = "length";
        }
        DocumentChunker chunker = getChunkerMap().get(strategy);
        if (chunker == null) {
            throw new BusinessException("未找到支持该分块策略的分块器: " + strategy);
        }
        return chunker.chunk(content, configJson);
    }
}
