import { describe, expect, it } from 'vitest'
import { orderRolesWithCurrentFirst } from '../user-role-order'

describe('orderRolesWithCurrentFirst', () => {
  it('把当前用户角色放到最前面并保持组内原顺序', () => {
    const roles = [
      { id: 3, roleName: '普通' },
      { id: 1, roleName: '管理员' },
      { id: 2, roleName: '审批' },
      { id: 4, roleName: '访客' },
    ]

    expect(orderRolesWithCurrentFirst(roles, ['2', 1]).map(role => role.id)).toEqual([1, 2, 3, 4])
  })

  it('对空数据和不存在的当前角色安全回退', () => {
    expect(orderRolesWithCurrentFirst([], [1])).toEqual([])
    const roles = [{ id: 4 }, { id: 5 }]
    expect(orderRolesWithCurrentFirst(roles, [9])).toEqual(roles)
  })
})
