package com.mdframe.forge.plugin.generator.businessprocess.validation;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 发布校验时由当前租户和应用解析出的受治理依赖目录。
 */
@Data
@Accessors(chain = true)
public class BusinessProcessValidationContext {

    private String expectedProcessCode;

    /** objectCode -> 无损字符串 objectId。 */
    private Map<String, String> objectIdsByCode = new LinkedHashMap<>();

    /** objectCode -> 当前可用字段编码。 */
    private Map<String, Set<String>> fieldsByObjectCode = new LinkedHashMap<>();

    /** objectCode -> 当前不可变发布版本 ID。 */
    private Map<String, String> publishedObjectVersionIdsByCode = new LinkedHashMap<>();

    private Set<String> availableFlowModelKeys = new LinkedHashSet<>();

    private Set<String> availableFormAssetKeys = new LinkedHashSet<>();

    private Set<String> availableBusinessActionCodes = new LinkedHashSet<>();

    private Set<String> availableMessageTemplateCodes = new LinkedHashSet<>();

    private Set<String> availableCapabilityCodes = new LinkedHashSet<>();

    /** 仅包含同应用已发布业务流程。 */
    private Set<String> publishedSubProcessCodes = new LinkedHashSet<>();

    /** 已发布流程依赖图，用于当前流程发布前做间接递归检查。 */
    private Map<String, Set<String>> subProcessDependencies = new LinkedHashMap<>();

    private Set<String> knownPermissions = new LinkedHashSet<>();

    /** Task 9B 完成受控桥接前保持 false。 */
    private boolean capabilityBridgeAvailable;
}
