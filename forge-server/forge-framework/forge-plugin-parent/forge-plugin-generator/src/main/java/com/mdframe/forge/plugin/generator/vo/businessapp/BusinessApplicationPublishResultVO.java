package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用协调发布或恢复结果。
 */
@Data
public class BusinessApplicationPublishResultVO {

    private Long runId;

    private Long applicationId;

    private String operationType;

    private String runStatus;

    private Integer targetVersionNo;

    private Long resultVersionId;

    private Boolean recoverable;

    private String currentStep;

    /** 失败时返回安全错误码，便于前端展示和客服定位，不包含堆栈或原始异常。 */
    private String errorCode;

    private String message;

    private List<BusinessApplicationPublishStepVO> steps = new ArrayList<>();
}
