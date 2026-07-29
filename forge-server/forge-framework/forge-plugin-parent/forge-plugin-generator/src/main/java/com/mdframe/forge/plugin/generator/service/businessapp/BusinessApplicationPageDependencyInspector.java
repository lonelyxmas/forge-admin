package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessApplicationObjectRole;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检查应用页面对真实业务对象的依赖。页面级表单资产不属于数据库对象依赖。
 */
@Service
@RequiredArgsConstructor
public class BusinessApplicationPageDependencyInspector {

    private final ObjectMapper objectMapper;

    public InspectionResult inspect(BusinessApplicationVO application,
                                    List<BusinessApplicationObjectVO> objects) {
        List<BusinessApplicationObjectVO> available = objects == null ? List.of() : objects;
        List<DependencyIssue> issues = new ArrayList<>();
        List<BusinessApplicationObjectVO> primaryObjects = available.stream()
                .filter(item -> BusinessApplicationObjectRole.PRIMARY.equalsIgnoreCase(item.getObjectRole()))
                .toList();
        if (primaryObjects.size() > 1) {
            addIssue(issues, new DependencyIssue(
                    "MULTIPLE_PRIMARY_OBJECTS",
                    "主对象配置冲突",
                    "应用配置了多个主对象，请只保留一个；多对象页面也可以全部显式绑定而不设置主对象。",
                    null));
        }

        Map<String, Object> options = readMap(application == null ? null : application.getOptions());
        Map<String, Object> builder = map(options.get("inAppBuilder"));
        List<Map<String, Object>> nodes = maps(builder.get("nodes"));
        Map<String, Object> pages = map(builder.get("pages"));
        boolean[] hasDataDependencies = {false};
        for (Map<String, Object> node : nodes) {
            if (!"page".equalsIgnoreCase(string(node.get("type")))) {
                continue;
            }
            String pageId = StringUtils.trimToNull(string(node.get("id")));
            String pageTitle = StringUtils.defaultIfBlank(string(node.get("title")), "未命名页面");
            Map<String, Object> pageRef = map(node.get("objectRef"));
            if ("object".equalsIgnoreCase(string(node.get("pageType")))) {
                hasDataDependencies[0] = true;
                validateConsumer("页面“" + pageTitle + "”", pageRef, Map.of(), available, primaryObjects, pageId, issues);
            }
            inspectValue(pages.get(pageId), pageRef, pageTitle, pageId,
                    available, primaryObjects, issues, hasDataDependencies);
        }
        return new InspectionResult(hasDataDependencies[0], List.copyOf(issues));
    }

    private void inspectValue(Object value,
                              Map<String, Object> pageRef,
                              String pageTitle,
                              String pageId,
                              List<BusinessApplicationObjectVO> objects,
                              List<BusinessApplicationObjectVO> primaryObjects,
                              List<DependencyIssue> issues,
                              boolean[] hasDataDependencies) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> item = map(value);
            if ("AiCrudPage".equalsIgnoreCase(string(item.get("blockType")))) {
                hasDataDependencies[0] = true;
                Map<String, Object> props = map(item.get("props"));
                Map<String, Object> blockRef = firstNonEmptyMap(
                        props.get("objectRef"), props.get("businessObjectRef"), item.get("objectRef"));
                String blockTitle = StringUtils.defaultIfBlank(string(props.get("title")), "数据列表");
                validateConsumer("页面“" + pageTitle + "”中的“" + blockTitle + "”",
                        blockRef, pageRef, objects, primaryObjects, pageId, issues);
            }
            item.values().forEach(child -> inspectValue(child, pageRef, pageTitle, pageId,
                    objects, primaryObjects, issues, hasDataDependencies));
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(child -> inspectValue(child, pageRef, pageTitle, pageId,
                    objects, primaryObjects, issues, hasDataDependencies));
        }
    }

    private void validateConsumer(String consumerName,
                                  Map<String, Object> directRef,
                                  Map<String, Object> pageRef,
                                  List<BusinessApplicationObjectVO> objects,
                                  List<BusinessApplicationObjectVO> primaryObjects,
                                  String pageId,
                                  List<DependencyIssue> issues) {
        Map<String, Object> explicitRef = !directRef.isEmpty() ? directRef : pageRef;
        if (!explicitRef.isEmpty()) {
            if (resolveExplicit(explicitRef, objects) == null) {
                addIssue(issues, new DependencyIssue(
                        "PAGE_OBJECT_REFERENCE_INVALID",
                        "页面业务对象引用失效",
                        consumerName + "引用的业务对象不属于当前应用或已被移除。",
                        pageId));
            }
            return;
        }
        boolean hasFallback = primaryObjects.size() == 1 || objects.size() == 1;
        if (!hasFallback) {
            addIssue(issues, new DependencyIssue(
                    "PAGE_OBJECT_BINDING_MISSING",
                    "数据页面尚未绑定业务对象",
                    consumerName + "需要选择一个业务对象；可在当前页面新建、导入或使用已有对象。",
                    pageId));
        }
    }

    private void addIssue(List<DependencyIssue> issues, DependencyIssue issue) {
        boolean duplicate = issues.stream().anyMatch(current -> current.code().equals(issue.code())
                && java.util.Objects.equals(current.pageId(), issue.pageId()));
        if (!duplicate) {
            issues.add(issue);
        }
    }

    private BusinessApplicationObjectVO resolveExplicit(Map<String, Object> ref,
                                                        List<BusinessApplicationObjectVO> objects) {
        String objectId = StringUtils.trimToNull(string(firstNonNull(ref.get("objectId"), ref.get("id"))));
        String objectCode = StringUtils.trimToNull(string(ref.get("objectCode")));
        if (objectId == null && objectCode == null) {
            return null;
        }
        return objects.stream().filter(item -> {
            boolean idMatches = objectId == null || objectId.equals(String.valueOf(item.getObjectId()));
            boolean codeMatches = objectCode == null || objectCode.equals(item.getObjectCode());
            return idMatches && codeMatches;
        }).findFirst().orElse(null);
    }

    private Map<String, Object> readMap(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> firstNonEmptyMap(Object... values) {
        for (Object value : values) {
            Map<String, Object> candidate = map(value);
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return Map.of();
    }

    private Object firstNonNull(Object left, Object right) {
        return left != null ? left : right;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw
                ? new LinkedHashMap<>((Map<String, Object>) raw) : Map.of();
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(this::map).toList();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record InspectionResult(boolean hasDataDependencies, List<DependencyIssue> issues) {
        public boolean valid() {
            return issues.isEmpty();
        }
    }

    public record DependencyIssue(String code, String title, String message, String pageId) {
    }
}
