package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

import java.util.List;

/**
 * 应用页面表单自动准备数据存储参数。
 */
@Data
public class BusinessApplicationFormDataProvisionDTO {

    private String formAssetId;

    private String formName;

    private List<BusinessFieldDTO> fields;

    private FormDesignerSchemaDTO formDesignerSchema;
}
