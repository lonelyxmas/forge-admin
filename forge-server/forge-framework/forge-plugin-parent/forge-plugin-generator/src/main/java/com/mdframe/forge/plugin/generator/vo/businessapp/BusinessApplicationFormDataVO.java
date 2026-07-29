package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.Data;

/**
 * 页面表单数据存储准备结果。
 */
@Data
public class BusinessApplicationFormDataVO {

    private String formAssetId;

    private Long objectId;

    private String objectCode;

    private String objectName;

    private String configKey;

    private Boolean created;
}
