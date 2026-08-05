package com.mdframe.forge.plugin.ai.rag.search.handler;

import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchContext;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * BM25 检索处理器。
 * 当前为占位实现（Milvus BM25 尚未集成），返回空结果。
 * 后续集成 Milvus BM25 后替换为实际检索逻辑。
 */
@Slf4j
@Component
public class Bm25SearchHandler implements RagSearchHandler {

    @Override
    public String getName() {
        return "bm25_search";
    }

    @Override
    public void handle(RagSearchContext context) {
        // TODO: 集成 Milvus BM25 检索
        log.debug("[Bm25SearchHandler] BM25检索暂未实现，返回空结果");
        context.setBm25Results(new ArrayList<>());
    }
}
