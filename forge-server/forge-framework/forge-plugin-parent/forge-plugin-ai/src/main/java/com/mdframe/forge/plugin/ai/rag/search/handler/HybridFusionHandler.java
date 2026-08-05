package com.mdframe.forge.plugin.ai.rag.search.handler;

import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchContext;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchHandler;
import com.mdframe.forge.plugin.ai.rag.search.fusion.RrfFusion;
import com.mdframe.forge.plugin.ai.rag.search.fusion.WeightedSumFusion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 混合融合处理器。
 * 合并向量检索 + BM25 检索结果，支持 RRF 和加权求和两种融合策略。
 */
@Slf4j
@Component
public class HybridFusionHandler implements RagSearchHandler {

    @Override
    public String getName() {
        return "hybrid_fusion";
    }

    @Override
    public void handle(RagSearchContext context) {
        List<KnowledgeSearchResult> vectorResults = context.getVectorResults();
        List<KnowledgeSearchResult> bm25Results = context.getBm25Results();

        if (vectorResults == null) vectorResults = new ArrayList<>();
        if (bm25Results == null) bm25Results = new ArrayList<>();

        // 如果只有一路结果，直接使用
        if (bm25Results.isEmpty()) {
            context.setFusedResults(vectorResults);
            return;
        }
        if (vectorResults.isEmpty()) {
            context.setFusedResults(bm25Results);
            return;
        }

        // 根据融合策略选择融合方式
        String strategy = context.getRequest().getFusionStrategy();
        List<KnowledgeSearchResult> fused;

        if ("weighted_sum".equalsIgnoreCase(strategy)) {
            fused = WeightedSumFusion.fuse(vectorResults, bm25Results);
            log.debug("[HybridFusionHandler] 使用加权求和融合, vector={}, bm25={}",
                    vectorResults.size(), bm25Results.size());
        } else {
            // 默认使用 RRF
            fused = RrfFusion.fuse(List.of(vectorResults, bm25Results));
            log.debug("[HybridFusionHandler] 使用RRF融合, vector={}, bm25={}",
                    vectorResults.size(), bm25Results.size());
        }

        context.setFusedResults(fused);
    }
}
