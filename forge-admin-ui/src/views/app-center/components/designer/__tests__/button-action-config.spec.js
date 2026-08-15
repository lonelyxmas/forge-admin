import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  buildBottomActionConfig,
  normalizeButtonActionDraft,
} from '../button-action-config'

describe('button action config', () => {
  it('loads only published processes from the current application and exposes process creation', () => {
    const source = fs.readFileSync(path.resolve(
      process.cwd(),
      'src/views/app-center/components/designer/ButtonActionConfig.vue',
    ), 'utf8')

    expect(source).toContain('businessApplicationDetailByCode(props.applicationCode)')
    expect(source).toContain('businessProcessPage({')
    expect(source).toContain('designStatus: \'PUBLISHED\'')
    expect(source).toContain('+ 新建业务流程')
    expect(source).toContain('name: \'BusinessApplicationWorkspace\'')
  })

  it('maps every designer behavior to the bottom-bar runtime protocol', () => {
    expect(buildBottomActionConfig({ label: '保存' }, { behaviorType: 'submit' })).toMatchObject({
      label: '保存',
      type: 'save',
      actionCode: '',
    })
    expect(buildBottomActionConfig({}, {
      behaviorType: 'navigate',
      targetPageKey: 'page_order_detail',
    })).toMatchObject({
      type: 'navigate',
      actionType: 'NAVIGATE',
      actionCode: 'page_order_detail',
      targetPageKey: 'page_order_detail',
    })
    expect(buildBottomActionConfig({}, {
      behaviorType: 'process',
      processCode: 'order_submit',
      processId: '2088',
      permissionCode: 'order:submit',
    })).toMatchObject({
      type: 'process',
      actionType: 'START_PROCESS',
      actionCode: 'order_submit',
      processCode: 'order_submit',
      processId: '2088',
      permissionKey: 'order:submit',
      permissionCode: 'order:submit',
    })
    expect(buildBottomActionConfig({}, {
      behaviorType: 'custom',
      processCode: 'order_automation',
      processId: '2099',
    })).toMatchObject({
      type: 'action',
      actionType: 'BUSINESS_PROCESS_ACTION',
      actionCode: 'order_automation',
      processCode: 'order_automation',
      processId: '2099',
    })
  })

  it('round-trips existing process and navigation actions for editing', () => {
    expect(normalizeButtonActionDraft({
      type: 'process',
      actionCode: 'order_submit',
      processId: '2088',
      permissionKey: 'order:submit',
    })).toEqual({
      behaviorType: 'process',
      targetPageKey: '',
      processCode: 'order_submit',
      processId: '2088',
      permissionCode: 'order:submit',
    })
    expect(normalizeButtonActionDraft({
      type: 'navigate',
      actionCode: 'page_order_detail',
    })).toMatchObject({
      behaviorType: 'navigate',
      targetPageKey: 'page_order_detail',
    })
  })
})
