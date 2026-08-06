/**
 * 把当前用户已拥有的角色放在授权列表前面，同时保持每一组原有顺序。
 */
export function orderRolesWithCurrentFirst(roles = [], currentRoleIds = []) {
  const currentIds = new Set((currentRoleIds || []).map(id => String(id)))
  return (Array.isArray(roles) ? roles : [])
    .map((role, index) => ({ role, index }))
    .sort((left, right) => {
      const leftPriority = currentIds.has(String(left.role?.id)) ? 0 : 1
      const rightPriority = currentIds.has(String(right.role?.id)) ? 0 : 1
      return leftPriority - rightPriority || left.index - right.index
    })
    .map(item => item.role)
}
