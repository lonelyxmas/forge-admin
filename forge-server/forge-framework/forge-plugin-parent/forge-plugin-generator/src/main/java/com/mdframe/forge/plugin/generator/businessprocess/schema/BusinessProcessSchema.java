package com.mdframe.forge.plugin.generator.businessprocess.schema;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 独立于 BPMN/flowJson 的应用级业务编排协议。
 */
@Data
public class BusinessProcessSchema {

    private String schemaVersion;

    private String processCode;

    private Subject subject;

    private List<BusinessProcessNode> nodes = new ArrayList<>();

    private List<BusinessProcessEdge> edges = new ArrayList<>();

    private Policies policies;

    private Dependencies dependencies = new Dependencies();

    /** 迁移来源等非执行元数据，仍受敏感键检查。 */
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Data
    public static class Subject {

        /** 雪花 ID 使用字符串，禁止 JSON 长整型精度损失。 */
        private String objectId;

        private String objectCode;

        private String objectVersionId;

        private String recordIdSource;
    }

    @Data
    public static class Policies {

        private String approvalConcurrency;

        private Integer maxSubProcessDepth;

        private RetryPolicy retry;
    }

    @Data
    public static class RetryPolicy {

        private String mode;

        private Integer maxAttempts;

        private List<Integer> backoffSeconds = new ArrayList<>();
    }

    @Data
    public static class Dependencies {

        private List<String> objects = new ArrayList<>();

        private List<String> flowModels = new ArrayList<>();

        private List<String> formAssets = new ArrayList<>();

        private List<String> businessActions = new ArrayList<>();

        private List<String> messageTemplates = new ArrayList<>();

        private List<String> capabilities = new ArrayList<>();

        private List<String> subProcesses = new ArrayList<>();
    }
}
