import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  resolveDataModelTab,
  resolveDefaultViewTab,
  resolveStandaloneObjectDesignerSection,
  standaloneObjectDesignerSections,
} from '../object-designer-navigation'

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(process.cwd(), relativePath), 'utf8')
}

describe('standalone object designer navigation', () => {
  it('exposes only the five object-owned configuration dimensions', () => {
    expect(standaloneObjectDesignerSections.map(item => item.key)).toEqual([
      'basic',
      'fields',
      'data-model',
      'default-view',
      'triggers',
    ])
  })

  it('maps legacy deep links into the grouped sections', () => {
    expect(resolveStandaloneObjectDesignerSection('list')).toBe('default-view')
    expect(resolveStandaloneObjectDesignerSection('detail')).toBe('default-view')
    expect(resolveStandaloneObjectDesignerSection('relations')).toBe('data-model')
    expect(resolveStandaloneObjectDesignerSection('flow-app')).toBe('data-model')
    expect(resolveStandaloneObjectDesignerSection('permission')).toBe('data-model')
    expect(resolveStandaloneObjectDesignerSection('form')).toBe('default-view')
    expect(resolveStandaloneObjectDesignerSection('actions')).toBe('default-view')
  })

  it('preserves the legacy target as the grouped sub-tab', () => {
    expect(resolveDataModelTab('flow-app')).toBe('flow-app')
    expect(resolveDataModelTab('permission')).toBe('permission')
    expect(resolveDefaultViewTab('detail')).toBe('detail')
    expect(resolveDefaultViewTab('form')).toBe('list')
  })

  it('keeps application-owned actions out of the standalone default view', () => {
    const objectDesigner = readSource('src/views/app-center/object-designer.[objectCode].vue')
    const listDesigner = readSource('src/views/app-center/components/designer/BusinessListDesigner.vue')
    const gridDesigner = readSource('src/components/lowcode-builder/page/ListPageGridDesigner.vue')

    expect(objectDesigner).toContain('default-view-only')
    expect(objectDesigner).toContain('const compatibilityPanel = [\'publish\', \'advanced\'].includes(normalizedPanel)')
    expect(listDesigner).toContain('v-if="!defaultViewOnly" class="list-custom-actions-entry"')
    expect(listDesigner).toContain('const visibleListCustomActions = computed(() => props.defaultViewOnly ? [] : listCustomActions.value)')
    expect(listDesigner).toContain('if (!props.defaultViewOnly)\n      await saveBusinessObjectActions')
    expect(gridDesigner).toContain('customActionsEditable')
  })
})
