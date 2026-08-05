package com.mdframe.forge.plugin.ai.rag.search;

/**
 * RAG 检索处理器接口。
 * 每个处理器负责管道中的一个步骤。
 */
public interface RagSearchHandler {

    /**
     * 处理器名称
     */
    String getName();

    /**
     * 执行处理逻辑
     *
     * @param context 检索管道上下文
     */
    void handle(RagSearchContext context);
}
