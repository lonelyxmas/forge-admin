package com.mdframe.forge.starter.collaboration.model;

import java.util.List;
import java.util.Map;

/**
 * 外部平台成员快照。
 *
 * @param externalUserId      平台侧用户 ID（企微 userid）
 * @param name                姓名
 * @param mobile              手机号（可为空，落库前脱敏处理由编排层负责）
 * @param email               邮箱（可为空）
 * @param position            职务（可为空）
 * @param departmentIds       所属部门平台侧 ID 列表
 * @param leaderDepartmentIds 担任负责人的部门平台侧 ID 列表
 * @param active              平台侧是否在职/启用
 * @param avatar              头像（可为空）
 * @param sourceHash          源数据规范化摘要，用于比较更新
 * @param extAttributes       平台扩展属性（只读）
 */
public record ExternalUser(
        String externalUserId,
        String name,
        String mobile,
        String email,
        String position,
        List<String> departmentIds,
        List<String> leaderDepartmentIds,
        boolean active,
        String avatar,
        String sourceHash,
        Map<String, Object> extAttributes
) {

    public ExternalUser {
        departmentIds = departmentIds == null ? List.of() : List.copyOf(departmentIds);
        leaderDepartmentIds = leaderDepartmentIds == null ? List.of() : List.copyOf(leaderDepartmentIds);
        extAttributes = extAttributes == null ? Map.of() : Map.copyOf(extAttributes);
    }
}
