package com.mdframe.forge.plugin.ai.rag.search.handler;

import com.mdframe.forge.plugin.ai.knowledge.domain.AiKnowledgeChunk;
import com.mdframe.forge.plugin.ai.knowledge.mapper.AiKnowledgeChunkMapper;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchContext;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 最终化处理器。
 * 执行 Lost-in-Middle 重排 + Nearby 上下文扩展。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinalizeHandler implements RagSearchHandler {

    private final AiKnowledgeChunkMapper chunkMapper;

    @Override
    public String getName() {
        return "finalize";
    }

    @Override
    public void handle(RagSearchContext context) {
        List<KnowledgeSearchResult> results = context.getFinalResults();
        if (results == null) {
            // 如果 rerank 未执行，从 fusedResults 或 vectorResults 取
            results = context.getFusedResults() != null ? context.getFusedResults() : context.getVectorResults();
        }

        if (results == null || results.isEmpty()) {
            context.setFinalResults(List.of());
            return;
        }

        // Lost-in-Middle 重排
        Boolean lostInMiddle = context.getRequest().getLostInMiddle();
        if (lostInMiddle != null && lostInMiddle) {
            results = lostInMiddleRerank(results);
            log.debug("[FinalizeHandler] Lost-in-Middle重排完成");
        }

        // Nearby 上下文扩展（默认2个相邻分块）
        results = expandNearby(results, 2);

        // 截断到 topK
        Integer topK = context.getRequest().getTopK();
        if (topK != null && topK > 0 && results.size() > topK) {
            results = results.subList(0, topK);
        }

        context.setFinalResults(results);
    }

    /**
     * Lost-in-Middle 重排：将最相关和最不相关的放在开头和结尾，中等相关的放在中间。
     */
    private List<KnowledgeSearchResult> lostInMiddleRerank(List<KnowledgeSearchResult> results) {
        if (results.size() <= 2) return results;

        List<KnowledgeSearchResult> reranked = new ArrayList<>();
        int left = 0;
        int right = results.size() - 1;
        boolean takeLeft = true;

        while (left <= right) {
            if (takeLeft) {
                reranked.add(results.get(left++));
            } else {
                reranked.add(results.get(right--));
            }
            takeLeft = !takeLeft;
        }

        return reranked;
    }

    /**
     * Nearby 上下文扩展：为每个命中的分块追加前后相邻分块
     */
    private List<KnowledgeSearchResult> expandNearby(List<KnowledgeSearchResult> results, int nearbyCount) {
        Set<String> seen = new HashSet<>();
        List<KnowledgeSearchResult> expanded = new ArrayList<>();

        for (KnowledgeSearchResult result : results) {
            if (seen.add(result.getChunkId())) {
                expanded.add(result);
            }

            Long documentId = result.getDocumentId();
            int chunkIndex = result.getChunkIndex();

            for (int offset = -nearbyCount; offset <= nearbyCount; offset++) {
                if (offset == 0) continue;
                int targetIndex = chunkIndex + offset;
                if (targetIndex < 0) continue;

                List<AiKnowledgeChunk> nearbyChunks = chunkMapper.selectByDocumentId(documentId);
                for (AiKnowledgeChunk chunk : nearbyChunks) {
                    if (chunk.getChunkIndex() == targetIndex) {
                        String key = String.valueOf(chunk.getId());
                        if (seen.add(key)) {
                            KnowledgeSearchResult nearby = new KnowledgeSearchResult();
                            nearby.setChunkId(key);
                            nearby.setDocumentId(chunk.getDocumentId());
                            nearby.setChunkIndex(chunk.getChunkIndex());
                            nearby.setContent(chunk.getContent());
                            nearby.setTitle(chunk.getTitle());
                            nearby.setScore(result.getScore() * 0.8);
                            expanded.add(nearby);
                        }
                    }
                }
            }
        }

        return expanded;
    }
}
