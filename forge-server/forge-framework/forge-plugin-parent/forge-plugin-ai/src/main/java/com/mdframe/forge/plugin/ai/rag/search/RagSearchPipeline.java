package com.mdframe.forge.plugin.ai.rag.search;

import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.plugin.ai.rag.search.handler.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 检索管道。
 * 将多个 Handler 串联执行：向量检索 -> BM25 -> 融合 -> Rerank -> 最终化。
 * 包装现有 KnowledgeSearchService（向量检索），在其上增加 BM25、融合、Rerank 和查询补全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchPipeline {

    private final VectorSearchHandler vectorSearchHandler;
    private final Bm25SearchHandler bm25SearchHandler;
    private final HybridFusionHandler hybridFusionHandler;
    private final RerankHandler rerankHandler;
    private final FinalizeHandler finalizeHandler;
    private final QueryCompleter queryCompleter;

    /**
     * 执行检索管道
     *
     * @param request 检索请求
     * @return 检索结果
     */
    public List<KnowledgeSearchResult> search(KnowledgeSearchRequest request) {
        RagSearchContext context = new RagSearchContext(request);

        // 0. 查询补全（可选）
        if (Boolean.TRUE.equals(request.getQueryComplete())) {
            String expandedQuery = queryCompleter.expand(request.getQuery());
            if (expandedQuery != null && !expandedQuery.isBlank()) {
                context.setExpandedQuery(expandedQuery);
                // 使用扩展后的查询进行检索
                request.setQuery(expandedQuery);
                log.debug("[RagSearchPipeline] 查询补全: {} -> {}", request.getQuery(), expandedQuery);
            }
        }

        // 1. 向量检索
        vectorSearchHandler.handle(context);

        // 2. BM25 检索
        bm25SearchHandler.handle(context);

        // 3. 混合融合（如果有 BM25 结果）
        if (context.getBm25Results() != null && !context.getBm25Results().isEmpty()) {
            hybridFusionHandler.handle(context);
        } else {
            // 无 BM25 结果，直接使用向量结果
            context.setFusedResults(context.getVectorResults());
        }

        // 4. Rerank
        rerankHandler.handle(context);

        // 5. 最终化（Lost-in-Middle + Nearby + 截断）
        finalizeHandler.handle(context);

        List<KnowledgeSearchResult> results = context.getFinalResults();
        if (results == null) {
            results = new ArrayList<>();
        }

        log.debug("[RagSearchPipeline] 检索完成, query={}, results={}", request.getQuery(), results.size());
        return results;
    }
}
