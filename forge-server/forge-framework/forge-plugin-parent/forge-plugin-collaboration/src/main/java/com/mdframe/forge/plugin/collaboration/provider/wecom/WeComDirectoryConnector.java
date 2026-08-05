package com.mdframe.forge.plugin.collaboration.provider.wecom;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.starter.collaboration.connector.AccessTokenProvider;
import com.mdframe.forge.starter.collaboration.connector.DirectoryConnector;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.DirectorySnapshot;
import com.mdframe.forge.starter.collaboration.model.DirectorySyncScope;
import com.mdframe.forge.starter.collaboration.model.ExternalDepartment;
import com.mdframe.forge.starter.collaboration.model.ExternalTag;
import com.mdframe.forge.starter.collaboration.model.ExternalUser;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.social.enums.SocialPlatform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 企业微信目录 Connector（Task 9）。
 * <p>
 * 通过通讯录 Token 读取部门/成员/标签全量数据；任一环节失败直接抛错，
 * 只有全部读取成功才返回 complete=true 的快照。成员按根部门 fetch_child 拉取并按 userid 去重；
 * sourceHash 为字段规范化摘要（列表字段排序后参与），供规划器比较更新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComDirectoryConnector implements DirectoryConnector {

    private final WeComApiClient apiClient;

    @Override
    public String platform() {
        return SocialPlatform.WECHAT_ENTERPRISE.getCode();
    }

    @Override
    public DirectorySnapshot fetchSnapshot(CollaborationExecutionContext context, DirectorySyncScope scope) {
        DirectorySyncScope effectiveScope = scope == null ? DirectorySyncScope.FULL : scope;
        List<ExternalDepartment> departments = fetchDepartments(context);
        List<ExternalUser> users = effectiveScope == DirectorySyncScope.TAG_ONLY
                ? List.of()
                : fetchUsers(context, departments);
        List<ExternalTag> tags = effectiveScope == DirectorySyncScope.DIRECTORY_ONLY
                ? List.of()
                : fetchTags(context);
        log.info("企业微信目录快照拉取完成: connectionId={}, deptCount={}, userCount={}, tagCount={}",
                context.connectionId(), departments.size(), users.size(), tags.size());
        return new DirectorySnapshot(effectiveScope, departments, users, tags, Instant.now(), true);
    }

    private List<ExternalDepartment> fetchDepartments(CollaborationExecutionContext context) {
        JSONObject response = apiClient.execute(WeComRequest.<JSONObject>builder()
                .path("/cgi-bin/department/list")
                .tokenType(AccessTokenProvider.TokenType.CONTACT)
                .responseType(JSONObject.class)
                .build(), context);
        JSONArray array = response.getJSONArray("department");
        if (array == null) {
            throw new BusinessException("企业微信部门列表响应缺少department字段");
        }
        List<ExternalDepartment> departments = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            long id = item.getLongValue("id");
            long parentId = item.getLongValue("parentid");
            String name = item.getString("name");
            Long order = item.getLong("order");
            String sourceHash = sha256Hex("dept|" + id + "|" + parentId + "|" + name + "|" + order);
            departments.add(new ExternalDepartment(
                    Long.toString(id),
                    parentId <= 0 ? null : Long.toString(parentId),
                    name,
                    order,
                    sourceHash));
        }
        return departments;
    }

    private List<ExternalUser> fetchUsers(CollaborationExecutionContext context,
                                          List<ExternalDepartment> departments) {
        // 从根部门 fetch_child 覆盖全员；多根场景逐根拉取后按 userid 去重
        Map<String, ExternalUser> userById = new LinkedHashMap<>();
        for (ExternalDepartment root : departments) {
            if (StringUtils.hasText(root.parentExternalId())) {
                continue;
            }
            JSONObject response = apiClient.execute(WeComRequest.<JSONObject>builder()
                    .path("/cgi-bin/user/list")
                    .queryParams(Map.of("department_id", root.externalId(), "fetch_child", "1"))
                    .tokenType(AccessTokenProvider.TokenType.CONTACT)
                    .responseType(JSONObject.class)
                    .build(), context);
            JSONArray array = response.getJSONArray("userlist");
            if (array == null) {
                throw new BusinessException("企业微信成员列表响应缺少userlist字段");
            }
            for (int i = 0; i < array.size(); i++) {
                ExternalUser user = toExternalUser(array.getJSONObject(i));
                userById.putIfAbsent(user.externalUserId(), user);
            }
        }
        return List.copyOf(userById.values());
    }

    private ExternalUser toExternalUser(JSONObject item) {
        String userId = item.getString("userid");
        String name = item.getString("name");
        String mobile = item.getString("mobile");
        String email = StringUtils.hasText(item.getString("email"))
                ? item.getString("email")
                : item.getString("biz_mail");
        String position = item.getString("position");
        String avatar = item.getString("avatar");
        // 企微 status：1已激活 2已禁用 4未激活 5退出企业
        int status = item.getIntValue("status", 1);
        boolean active = status == 1;

        List<String> departmentIds = new ArrayList<>();
        List<String> leaderDepartmentIds = new ArrayList<>();
        JSONArray deptArray = item.getJSONArray("department");
        JSONArray leaderArray = item.getJSONArray("is_leader_in_dept");
        if (deptArray != null) {
            for (int i = 0; i < deptArray.size(); i++) {
                String deptId = Long.toString(deptArray.getLongValue(i));
                departmentIds.add(deptId);
                if (leaderArray != null && i < leaderArray.size() && leaderArray.getIntValue(i) == 1) {
                    leaderDepartmentIds.add(deptId);
                }
            }
        }
        Long mainDepartment = item.getLong("main_department");
        Map<String, Object> extAttributes = mainDepartment == null
                ? Map.of()
                : Map.of("mainDepartment", Long.toString(mainDepartment));

        String sourceHash = sha256Hex("user|" + userId + "|" + name + "|" + mobile + "|" + email
                + "|" + position + "|" + sorted(departmentIds) + "|" + sorted(leaderDepartmentIds)
                + "|" + status + "|" + avatar);
        return new ExternalUser(userId, name, mobile, email, position,
                departmentIds, leaderDepartmentIds, active, avatar, sourceHash, extAttributes);
    }

    private List<ExternalTag> fetchTags(CollaborationExecutionContext context) {
        JSONObject response = apiClient.execute(WeComRequest.<JSONObject>builder()
                .path("/cgi-bin/tag/list")
                .tokenType(AccessTokenProvider.TokenType.CONTACT)
                .responseType(JSONObject.class)
                .build(), context);
        JSONArray tagArray = response.getJSONArray("taglist");
        if (tagArray == null) {
            return List.of();
        }
        List<ExternalTag> tags = new ArrayList<>(tagArray.size());
        for (int i = 0; i < tagArray.size(); i++) {
            JSONObject item = tagArray.getJSONObject(i);
            long tagId = item.getLongValue("tagid");
            String tagName = item.getString("tagname");
            JSONObject detail = apiClient.execute(WeComRequest.<JSONObject>builder()
                    .path("/cgi-bin/tag/get")
                    .queryParams(Map.of("tagid", Long.toString(tagId)))
                    .tokenType(AccessTokenProvider.TokenType.CONTACT)
                    .responseType(JSONObject.class)
                    .build(), context);
            List<String> memberUserIds = new ArrayList<>();
            JSONArray userList = detail.getJSONArray("userlist");
            if (userList != null) {
                for (int j = 0; j < userList.size(); j++) {
                    memberUserIds.add(userList.getJSONObject(j).getString("userid"));
                }
            }
            List<String> departmentIds = new ArrayList<>();
            JSONArray partyList = detail.getJSONArray("partylist");
            if (partyList != null) {
                for (int j = 0; j < partyList.size(); j++) {
                    departmentIds.add(Long.toString(partyList.getLongValue(j)));
                }
            }
            String sourceHash = sha256Hex("tag|" + tagId + "|" + tagName
                    + "|" + sorted(memberUserIds) + "|" + sorted(departmentIds));
            tags.add(new ExternalTag(Long.toString(tagId), tagName, memberUserIds, departmentIds, sourceHash));
        }
        return tags;
    }

    private String sorted(List<String> values) {
        return String.join(",", new TreeSet<>(values));
    }

    private String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256算法不可用", exception);
        }
    }
}
