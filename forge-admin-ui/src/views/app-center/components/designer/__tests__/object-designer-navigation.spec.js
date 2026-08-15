import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  resolveDataModelTab,
  resolveStandaloneObjectDesignerSection,
  standaloneObjectDesignerSections,
} from '../object-designer-navigation'

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(process.cwd(), relativePath), 'utf8')
}

describe('standalone object designer navigation', () => {
  it('exposes only the three object-owned configuration dimensions', () => {
    expect(standaloneObjectDesignerSections.map(item => item.key)).toEqual([
      'basic',
      'fields',
      'data-model',
    ])
  })

  it('maps only data-owned legacy deep links into the grouped sections', () => {
    expect(resolveStandaloneObjectDesignerSection('relations')).toBe('data-model')
    expect(resolveStandaloneObjectDesignerSection('permission')).toBe('data-model')
    expect(resolveStandaloneObjectDesignerSection('flow-app')).toBe('fields')
    expect(resolveStandaloneObjectDesignerSection('list')).toBe('fields')
    expect(resolveStandaloneObjectDesignerSection('triggers')).toBe('fields')
  })

  it('preserves only relation and permission targets as data-model sub-tabs', () => {
    expect(resolveDataModelTab('permission')).toBe('permission')
    expect(resolveDataModelTab('flow-app')).toBe('relations')
  })

  it('keeps application-owned designers out of the standalone object designer', () => {
    const objectDesigner = readSource('src/views/app-center/object-designer.[objectCode].vue')
    const listDesigner = readSource('src/views/app-center/components/designer/BusinessListDesigner.vue')
    const gridDesigner = readSource('src/components/lowcode-builder/page/ListPageGridDesigner.vue')

    expect(objectDesigner).not.toContain('activePanel === \'default-view\'')
    expect(objectDesigner).not.toContain('activePanel === \'triggers\'')
    expect(objectDesigner).not.toContain('BusinessTriggerConfigPanel')
    expect(objectDesigner).not.toContain('BusinessActionDesigner')
    expect(objectDesigner).toContain('const compatibilityPanel = [\'publish\', \'advanced\'].includes(normalizedPanel)')
    expect(listDesigner).toContain('v-if="!defaultViewOnly" class="list-custom-actions-entry"')
    expect(listDesigner).toContain('const visibleListCustomActions = computed(() => props.defaultViewOnly ? [] : listCustomActions.value)')
    expect(listDesigner).toContain('if (!props.defaultViewOnly)\n      await saveBusinessObjectActions')
    expect(gridDesigner).toContain('customActionsEditable')
  })

  it('shows only relation and permission tabs with a read-only process summary', () => {
    const objectDesigner = readSource('src/views/app-center/object-designer.[objectCode].vue')
    const processPanel = readSource('src/views/app-center/components/designer/ObjectProcessReadOnlyPanel.vue')

    expect(objectDesigner).not.toContain('<n-tab-pane name="flow-app"')
    expect(objectDesigner).toContain('<ObjectProcessReadOnlyPanel')
    expect(processPanel).toContain('businessObjectProcesses(props.objectCode)')
    expect(processPanel).toContain('去应用工作台配置')
  })

  it('guides standalone users to the application process workspace', () => {
    const objectDesigner = readSource('src/views/app-center/object-designer.[objectCode].vue')

    expect(objectDesigner).toContain('流程与自动化配置已移至应用工作台')
    expect(objectDesigner).toContain('触发器、流程绑定和业务动作已统一为业务流程画布')
    expect(objectDesigner).toContain('@click="openProcessWorkspace"')
  })
})
