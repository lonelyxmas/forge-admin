import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { convertJsonToBpmn } from '../../flow-designer/converter/json-to-bpmn.js'
import {
  BUSINESS_PROCESS_NODE_TYPE,
  getBusinessProcessNodeDefinition,
} from '../business-process-node-types.js'
import {
  businessProcessHashInput,
  createBusinessProcessSchema,
  normalizeBusinessProcessSchema,
  validateBusinessProcessGraph,
} from '../business-process-schema.js'
import BusinessProcessCanvas from '../BusinessProcessCanvas.vue'
import { useBusinessProcessDesigner } from '../useBusinessProcessDesigner.js'

const objectRef = {
  objectId: '1900000000000001001',
  objectCode: 'sample_purchase_order',
}

describe('businessProcessJson 1.0 protocol', () => {
  it('creates an independent manual-start to success-end draft with string IDs', () => {
    const schema = createBusinessProcessSchema({
      processCode: 'purchase_submit',
      objectRef,
      startType: BUSINESS_PROCESS_NODE_TYPE.START_MANUAL,
    })

    expect(schema).toMatchObject({
      schemaVersion: '1.0',
      processCode: 'purchase_submit',
      subject: {
        objectId: '1900000000000001001',
        objectCode: 'sample_purchase_order',
        recordIdSource: 'RUNTIME_RECORD',
      },
    })
    expect(schema.nodes.map(node => node.type)).toEqual(['START_MANUAL', 'END'])
    expect(schema.nodes.every(node => typeof node.id === 'string')).toBe(true)
    expect(schema.nodes.some(node => 'nodeType' in node || 'bpmnElementId' in node)).toBe(false)
    expect(validateBusinessProcessGraph(schema).isValid).toBe(true)
  })

  it('normalizes node, edge, port and dependency ordering for stable dirty checks', () => {
    const source = createBusinessProcessSchema({
      processCode: 'purchase_submit',
      objectRef,
    })
    source.dependencies.objects.push('aaa_object', 'sample_purchase_order')
    source.nodes.reverse()
    source.edges[0].sourcePort = ' next '

    const normalized = normalizeBusinessProcessSchema(source)

    expect(normalized.nodes.map(node => node.id)).toEqual(['end_success', 'start_manual'])
    expect(normalized.edges[0].sourcePort).toBe('NEXT')
    expect(normalized.dependencies.objects).toEqual(['aaa_object', 'sample_purchase_order'])
    expect(businessProcessHashInput(source)).toBe(businessProcessHashInput(normalized))
  })

  it('rejects numeric IDs and BPMN flowJson instead of coercing them', () => {
    const numericId = createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef })
    numericId.subject.objectId = Number('1900000000000001001')

    expect(() => normalizeBusinessProcessSchema(numericId)).toThrow(/ID.*字符串/)
    expect(() => normalizeBusinessProcessSchema({
      processId: 'Process_1',
      nodes: [{ id: 'S', nodeType: 'start' }],
      edges: [],
    })).toThrow(/BPMN|flowJson/)
  })

  it('business process schema is explicitly rejected by the BPMN converter', () => {
    const schema = createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef })
    expect(() => convertJsonToBpmn(schema)).toThrow(/businessProcessJson/)
  })
})

describe('business process graph validation', () => {
  it('reports multiple starts, dangling edges and cycles with stable issue codes', () => {
    const schema = createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef })
    schema.nodes.push({
      id: 'start_event',
      type: 'START_EVENT',
      name: '记录新增',
      ports: [],
      config: { eventType: 'RECORD_CREATED' },
    })
    schema.edges.push({
      id: 'edge_cycle',
      source: 'end_success',
      target: 'start_manual',
      sourcePort: 'NEXT',
      condition: {},
      isDefault: false,
    }, {
      id: 'edge_dangling',
      source: 'missing',
      target: 'end_success',
      sourcePort: 'NEXT',
      condition: {},
      isDefault: false,
    })

    const validation = validateBusinessProcessGraph(schema)
    const codes = validation.issues.map(issue => issue.code)

    expect(validation.isValid).toBe(false)
    expect(codes).toContain('START_NODE_COUNT')
    expect(codes).toContain('EDGE_SOURCE_MISSING')
    expect(codes).toContain('GRAPH_CYCLE')
  })

  it('node registry keeps business labels and ports independent from BPMN types', () => {
    expect(getBusinessProcessNodeDefinition('APPROVAL')).toMatchObject({
      label: '审批流程',
      ports: ['APPROVED', 'REJECTED', 'CANCELED', 'FAILED'],
    })
    expect(getBusinessProcessNodeDefinition('ACTION').bpmnType).toBeUndefined()
  })
})

describe('useBusinessProcessDesigner', () => {
  it('inserts, copies and deletes a business action while preserving the DAG', () => {
    const designer = useBusinessProcessDesigner(
      createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef }),
    )

    const actionId = designer.addNode('start_manual', 'ACTION', {
      name: '更新采购单',
      config: { actionType: 'UPDATE_RECORD', objectCode: 'sample_purchase_order' },
    })
    const copiedId = designer.copyNode(actionId)

    expect(designer.getNode(copiedId).name).toBe('更新采购单 副本')
    expect(validateBusinessProcessGraph(designer.schema.value).isValid).toBe(true)

    designer.deleteNode(actionId)
    expect(designer.getNode(actionId)).toBeNull()
    expect(validateBusinessProcessGraph(designer.schema.value).isValid).toBe(true)
  })

  it('condition insertion creates business branches rather than approval nodes', () => {
    const designer = useBusinessProcessDesigner(
      createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef }),
    )

    const conditionId = designer.addNode('start_manual', 'CONDITION', { name: '判断金额' })
    const condition = designer.getNode(conditionId)
    const outgoing = designer.getOutgoingEdges(conditionId)

    expect(condition.config.branches).toHaveLength(2)
    expect(outgoing).toHaveLength(2)
    expect(outgoing.filter(edge => edge.isDefault)).toHaveLength(1)
    expect(designer.schema.value.nodes.filter(node => node.type === 'APPROVAL')).toHaveLength(0)
    expect(validateBusinessProcessGraph(designer.schema.value).isValid).toBe(true)
  })

  it('supports undo, redo, dirty baseline and deep-cloned export', () => {
    const designer = useBusinessProcessDesigner(
      createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef }),
    )
    const actionId = designer.addNode('start_manual', 'ACTION')

    expect(designer.isDirty.value).toBe(true)
    expect(designer.undo()).toBe(true)
    expect(designer.getNode(actionId)).toBeNull()
    expect(designer.redo()).toBe(true)
    expect(designer.getNode(actionId)).toBeTruthy()

    designer.markSaved()
    expect(designer.isDirty.value).toBe(false)
    const exported = designer.exportSchema()
    exported.nodes.push({ id: 'mutated' })
    expect(designer.getNode('mutated')).toBeNull()
  })
})

describe('business process canvas', () => {
  it('reuses the shared viewport and edge layers while rendering business nodes', async () => {
    const schema = createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef })
    const wrapper = mount(BusinessProcessCanvas, {
      props: { schema, selectedNodeId: 'start_manual' },
    })

    expect(wrapper.find('.flow-canvas').exists()).toBe(true)
    expect(wrapper.find('.edge-layer').exists()).toBe(true)
    expect(wrapper.findAll('[data-business-process-node]')).toHaveLength(2)
    expect(wrapper.find('[data-node-id="start_manual"]').classes()).toContain('is-selected')

    await wrapper.find('[data-node-id="end_success"]').trigger('click')
    expect(wrapper.emitted('nodeSelect')[0][0].id).toBe('end_success')
  })
})
