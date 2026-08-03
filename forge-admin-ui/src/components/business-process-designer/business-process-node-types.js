export const BUSINESS_PROCESS_NODE_TYPE = Object.freeze({
  START_MANUAL: 'START_MANUAL',
  START_EVENT: 'START_EVENT',
  START_SCHEDULE: 'START_SCHEDULE',
  CONDITION: 'CONDITION',
  ACTION: 'ACTION',
  APPROVAL: 'APPROVAL',
  SUB_PROCESS: 'SUB_PROCESS',
  END: 'END',
})

export const BUSINESS_PROCESS_START_TYPES = Object.freeze([
  BUSINESS_PROCESS_NODE_TYPE.START_MANUAL,
  BUSINESS_PROCESS_NODE_TYPE.START_EVENT,
  BUSINESS_PROCESS_NODE_TYPE.START_SCHEDULE,
])

const NODE_DEFINITIONS = Object.freeze({
  [BUSINESS_PROCESS_NODE_TYPE.START_MANUAL]: definition({
    label: '手动开始',
    category: 'TRIGGER',
    ports: ['NEXT'],
    tone: 'manual',
    createConfig: () => ({
      positions: ['ROW', 'DETAIL'],
      permission: 'ai:businessProcess:start',
    }),
  }),
  [BUSINESS_PROCESS_NODE_TYPE.START_EVENT]: definition({
    label: '事件触发',
    category: 'TRIGGER',
    ports: ['NEXT'],
    tone: 'event',
    createConfig: () => ({ eventType: 'RECORD_CREATED' }),
  }),
  [BUSINESS_PROCESS_NODE_TYPE.START_SCHEDULE]: definition({
    label: '定时触发',
    category: 'TRIGGER',
    ports: ['NEXT'],
    tone: 'schedule',
    createConfig: () => ({
      dueField: '',
      serviceActor: {
        mode: 'CONFIGURED_USER',
        userConfigKey: 'business.process.schedule.service-user',
      },
    }),
  }),
  [BUSINESS_PROCESS_NODE_TYPE.CONDITION]: definition({
    label: '条件分支',
    category: 'CONTROL',
    ports: ['MATCHED', 'OTHERWISE'],
    tone: 'condition',
    createConfig: () => ({
      branches: [
        {
          port: 'MATCHED',
          condition: { operator: 'AND', rules: [] },
        },
        { port: 'OTHERWISE', isDefault: true },
      ],
    }),
  }),
  [BUSINESS_PROCESS_NODE_TYPE.ACTION]: definition({
    label: '执行动作',
    category: 'EXECUTION',
    ports: ['NEXT'],
    tone: 'action',
    createConfig: () => ({ actionType: 'UPDATE_RECORD' }),
  }),
  [BUSINESS_PROCESS_NODE_TYPE.APPROVAL]: definition({
    label: '审批流程',
    category: 'EXECUTION',
    ports: ['APPROVED', 'REJECTED', 'CANCELED', 'FAILED'],
    tone: 'approval',
    createConfig: () => ({ versionPolicy: 'PINNED_AT_APPLICATION_PUBLISH' }),
  }),
  [BUSINESS_PROCESS_NODE_TYPE.SUB_PROCESS]: definition({
    label: '调用子流程',
    category: 'EXECUTION',
    ports: ['NEXT'],
    tone: 'sub-process',
    createConfig: () => ({}),
  }),
  [BUSINESS_PROCESS_NODE_TYPE.END]: definition({
    label: '结束',
    category: 'RESULT',
    ports: [],
    tone: 'end',
    createConfig: () => ({ result: 'SUCCESS' }),
  }),
})

export function getBusinessProcessNodeDefinition(type) {
  const normalizedType = typeof type === 'string' ? type.trim().toUpperCase() : ''
  return NODE_DEFINITIONS[normalizedType] || null
}

export function listBusinessProcessNodeDefinitions() {
  return Object.entries(NODE_DEFINITIONS).map(([type, item]) => ({ type, ...item }))
}

export function createBusinessProcessNodeTemplate(type) {
  const nodeType = typeof type === 'string' ? type.trim().toUpperCase() : ''
  const item = getBusinessProcessNodeDefinition(nodeType)
  if (!item)
    throw new Error(`不支持的业务流程节点类型：${type || '空'}`)

  const persistsDeclaredPorts = [
    BUSINESS_PROCESS_NODE_TYPE.CONDITION,
    BUSINESS_PROCESS_NODE_TYPE.APPROVAL,
  ].includes(nodeType)

  return {
    type: nodeType,
    name: item.label,
    ports: persistsDeclaredPorts ? [...item.ports] : [],
    config: item.createConfig(),
  }
}

export function isBusinessProcessStartType(type) {
  return BUSINESS_PROCESS_START_TYPES.includes(type)
}

function definition(input) {
  return Object.freeze({
    ...input,
    ports: Object.freeze([...input.ports]),
  })
}
