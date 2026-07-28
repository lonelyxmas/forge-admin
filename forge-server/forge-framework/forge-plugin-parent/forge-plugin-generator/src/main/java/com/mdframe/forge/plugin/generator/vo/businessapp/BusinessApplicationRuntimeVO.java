package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 已发布应用运行配置，只包含当前用户可访问的页面编排。 */
@Data
public class BusinessApplicationRuntimeVO {

    private Integer versionNo;

    private BusinessApplicationVO application;

    private List<BusinessApplicationObjectVO> objects = new ArrayList<>();

    private List<BusinessAppVO> entries = new ArrayList<>();
}
