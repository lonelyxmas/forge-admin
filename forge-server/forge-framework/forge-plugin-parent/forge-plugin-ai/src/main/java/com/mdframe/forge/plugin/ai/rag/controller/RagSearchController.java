package com.mdframe.forge.plugin.ai.rag.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchRequest;
import com.mdframe.forge.plugin.ai.knowledge.service.dto.KnowledgeSearchResult;
import com.mdframe.forge.plugin.ai.rag.search.RagSearchPipeline;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RAG 增强检索控制器
 */
@RestController
@RequestMapping("/ai/rag/search")
@RequiredArgsConstructor
public class RagSearchController {

    private final RagSearchPipeline searchPipeline;

    /**
     * 增强检索（支持融合/Rerank/查询补全）
     */
    @PostMapping
    @SaCheckPermission("ai:knowledge:search")
    public RespInfo<List<KnowledgeSearchResult>> search(@RequestBody KnowledgeSearchRequest request) {
        return RespInfo.success(searchPipeline.search(request));
    }
}
