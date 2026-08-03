package com.mdframe.forge.plugin.generator.vo.businessprocess;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务流程协议校验结果。
 */
@Data
public class BusinessProcessValidationVO {

    private boolean valid = true;

    private int errorCount;

    private int warningCount;

    private List<ValidationIssueVO> issues = new ArrayList<>();

    public void addError(String code, String message, String nodeId,
                         String fieldPath, String suggestion) {
        addIssue("ERROR", code, message, nodeId, fieldPath, suggestion);
    }

    public void addWarning(String code, String message, String nodeId,
                           String fieldPath, String suggestion) {
        addIssue("WARNING", code, message, nodeId, fieldPath, suggestion);
    }

    public BusinessProcessValidationVO finish() {
        errorCount = (int) issues.stream().filter(issue -> "ERROR".equals(issue.getLevel())).count();
        warningCount = (int) issues.stream().filter(issue -> "WARNING".equals(issue.getLevel())).count();
        valid = errorCount == 0;
        return this;
    }

    public boolean hasError(String code) {
        return issues.stream().anyMatch(issue -> "ERROR".equals(issue.getLevel())
                && code.equals(issue.getCode()));
    }

    private void addIssue(String level, String code, String message, String nodeId,
                          String fieldPath, String suggestion) {
        ValidationIssueVO issue = new ValidationIssueVO();
        issue.setLevel(level);
        issue.setCode(code);
        issue.setMessage(message);
        issue.setNodeId(nodeId);
        issue.setFieldPath(fieldPath);
        issue.setSuggestion(suggestion);
        issues.add(issue);
    }

    @Data
    public static class ValidationIssueVO {

        private String level;

        private String code;

        private String message;

        private String nodeId;

        private String fieldPath;

        private String suggestion;
    }
}
