package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 业务应用创建结果。
 */
@Data
@AllArgsConstructor
public class BusinessApplicationCreateVO {

    private Long id;

    private String applicationCode;
}
