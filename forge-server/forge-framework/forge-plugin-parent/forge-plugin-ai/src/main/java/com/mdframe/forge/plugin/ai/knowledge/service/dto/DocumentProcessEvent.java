package com.mdframe.forge.plugin.ai.knowledge.service.dto;

import lombok.Data;

/**
 * 文档处理进度事件（SSE 推送）
 */
@Data
public class DocumentProcessEvent {

    private Long documentId;

    private String docName;

    private String stage;

    private int progress;

    private String message;

    private String status;

    public static DocumentProcessEvent of(Long documentId, String docName, String stage, int progress, String message, String status) {
        DocumentProcessEvent event = new DocumentProcessEvent();
        event.setDocumentId(documentId);
        event.setDocName(docName);
        event.setStage(stage);
        event.setProgress(progress);
        event.setMessage(message);
        event.setStatus(status);
        return event;
    }
}
