package com.mdframe.forge.plugin.ai.rag.search.handler;

import com.mdframe.forge.plugin.ai.knowledge.service.KnowledgeSearchService;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchContext;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量检索处理器。
 * 委托给现有 KnowledgeSearchService 执行向量检索。
 */
@Component
@RequiredArgsConstructor
public class VectorSearchHandler implements RagSearchHandler {

    private final KnowledgeSearchService knowledgeSearchService;

    @Override
    public String getName() {
        return "vector_search";
    }

    @Override
    public void handle(RagSearchContext context) {
        KnowledgeSearchRequest request = context.getRequest();
        List<KnowledgeSearchResult> results = knowledgeSearchService.search(request);
        context.setVectorResults(results);
    }
}
