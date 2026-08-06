import { describe, expect, it } from 'vitest'
import {
  buildDefaultPlaceholder,
  buildFieldAssetPlaceholderPatch,
  shouldSyncPlaceholder,
} from '../placeholder-utils'

describe('form designer placeholder synchronization', () => {
  it.each([
    ['input', '员工姓名', '请填写员工姓名'],
    ['userSelect', '申请人', '请选择申请人'],
    ['orgTreeSelect', '所属部门', '请选择所属部门'],
    ['regionTreeSelect', '所属区域', '请选择所属区域'],
    ['departmentSelect', '所属组织', '请选择所属组织'],
  ])('uses the field label for %s placeholders', (componentKey, label, expected) => {
    expect(buildDefaultPlaceholder(componentKey, label)).toBe(expected)
  })

  it('updates an automatically generated placeholder from the field asset panel', () => {
    expect(buildFieldAssetPlaceholderPatch({
      componentKey: 'userSelect',
      label: '人员选择',
      props: { placeholder: '请选择人员选择' },
    }, { fieldName: '申请人' })).toEqual({ placeholder: '请选择申请人' })
  })

  it('preserves a placeholder manually entered by the user', () => {
    const component = {
      componentKey: 'orgTreeSelect',
      label: '组织选择',
      props: { placeholder: '仅选择本部门人员' },
    }

    expect(shouldSyncPlaceholder(component)).toBe(false)
    expect(buildFieldAssetPlaceholderPatch(component, { fieldName: '所属部门' })).toEqual({})
  })

  it('honors an explicitly saved placeholder from the field asset panel', () => {
    expect(buildFieldAssetPlaceholderPatch({ componentKey: 'input' }, {
      fieldName: '员工姓名',
      placeholder: '输入花名或真实姓名',
    })).toEqual({ placeholder: '输入花名或真实姓名' })
  })
})
