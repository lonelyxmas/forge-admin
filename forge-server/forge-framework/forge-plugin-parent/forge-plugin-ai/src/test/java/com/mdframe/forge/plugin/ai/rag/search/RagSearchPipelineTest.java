package com.mdframe.forge.plugin.ai.rag.search;

import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.plugin.ai.rag.search.fusion.RrfFusion;
import com.mdframe.forge.plugin.ai.rag.search.fusion.WeightedSumFusion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 检索管道测试。
 * 重点测试 RRF 和加权求和融合算法。
 */
class RagSearchPipelineTest {

    @Test
    void rrfFusion_shouldMergeAndSortResults() {
        // 向量检索结果
        List<KnowledgeSearchResult> vectorResults = new ArrayList<>();
        vectorResults.add(result("c1", 0.9));
        vectorResults.add(result("c2", 0.8));
        vectorResults.add(result("c3", 0.7));

        // BM25 检索结果
        List<KnowledgeSearchResult> bm25Results = new ArrayList<>();
        bm25Results.add(result("c3", 5.0));
        bm25Results.add(result("c4", 4.0));
        bm25Results.add(result("c1", 3.0));

        List<KnowledgeSearchResult> fused = RrfFusion.fuse(List.of(vectorResults, bm25Results));

        // c1 在两路中都排名靠前，RRF 分数应最高
        assertFalse(fused.isEmpty());
        assertEquals(4, fused.size()); // c1, c2, c3, c4

        // c1 应排在最前（向量 rank=0, BM25 rank=2 -> 1/61 + 1/63）
        // c3 也出现在两路（向量 rank=2, BM25 rank=0 -> 1/63 + 1/61）
        // c1 和 c3 的 RRF 分数相同，但 c1 在向量中排名更高
        String topChunkId = fused.get(0).getChunkId();
        assertTrue(topChunkId.equals("c1") || topChunkId.equals("c3"),
                "Top result should be c1 or c3 (both appear in two lists)");
    }

    @Test
    void rrfFusion_withEmptyList_shouldReturnEmpty() {
        List<KnowledgeSearchResult> fused = RrfFusion.fuse(List.of());
        assertTrue(fused.isEmpty());
    }

    @Test
    void rrfFusion_withSingleList_shouldReturnSameResults() {
        List<KnowledgeSearchResult> results = List.of(result("c1", 0.9), result("c2", 0.8));
        List<KnowledgeSearchResult> fused = RrfFusion.fuse(List.of(results));
        assertEquals(2, fused.size());
    }

    @Test
    void weightedSumFusion_shouldMergeAndSortResults() {
        List<KnowledgeSearchResult> vectorResults = new ArrayList<>();
        vectorResults.add(result("c1", 0.9));
        vectorResults.add(result("c2", 0.5));

        List<KnowledgeSearchResult> bm25Results = new ArrayList<>();
        bm25Results.add(result("c2", 10.0));
        bm25Results.add(result("c3", 8.0));

        // alpha=0.7, 向量权重更高
        List<KnowledgeSearchResult> fused = WeightedSumFusion.fuse(vectorResults, bm25Results, 0.7);

        assertFalse(fused.isEmpty());
        assertEquals(3, fused.size()); // c1, c2, c3

        // c1 只在向量中出现（归一化后=1.0），c2 在两路中都出现
        // c1: 0.7*1.0 + 0.3*0.0 = 0.7
        // c2: 0.7*0.0 + 0.3*1.0 = 0.3 (向量归一化后=0, BM25归一化后=1.0)
        // 实际归一化取决于 min-max，c1 应该排最前
        assertEquals("c1", fused.get(0).getChunkId());
    }

    @Test
    void weightedSumFusion_withEmptyBm25_shouldReturnVectorResults() {
        List<KnowledgeSearchResult> vectorResults = List.of(result("c1", 0.9));
        List<KnowledgeSearchResult> fused = WeightedSumFusion.fuse(vectorResults, new ArrayList<>());
        assertEquals(1, fused.size());
        assertEquals("c1", fused.get(0).getChunkId());
    }

    @Test
    void weightedSumFusion_withEmptyVector_shouldReturnBm25Results() {
        List<KnowledgeSearchResult> bm25Results = List.of(result("c1", 5.0));
        List<KnowledgeSearchResult> fused = WeightedSumFusion.fuse(new ArrayList<>(), bm25Results);
        assertEquals(1, fused.size());
        assertEquals("c1", fused.get(0).getChunkId());
    }

    @Test
    void rrfFusion_customK_shouldAffectRanking() {
        List<KnowledgeSearchResult> vectorResults = List.of(result("c1", 0.9));
        List<KnowledgeSearchResult> bm25Results = List.of(result("c1", 5.0));

        // k=1: 更强调排名靠前的结果
        List<KnowledgeSearchResult> fusedK1 = RrfFusion.fuse(List.of(vectorResults, bm25Results), 1);
        // k=100: 更平滑
        List<KnowledgeSearchResult> fusedK100 = RrfFusion.fuse(List.of(vectorResults, bm25Results), 100);

        // 两种 k 值下 c1 都应存在
        assertEquals(1, fusedK1.size());
        assertEquals(1, fusedK100.size());

        // k=1 时分数更高
        assertTrue(fusedK1.get(0).getScore() > fusedK100.get(0).getScore());
    }

    private KnowledgeSearchResult result(String chunkId, double score) {
        KnowledgeSearchResult r = new KnowledgeSearchResult();
        r.setChunkId(chunkId);
        r.setScore(score);
        r.setContent("content of " + chunkId);
        return r;
    }
}
