<script setup>
import { computed, ref } from 'vue'
import EdgeLayer from '../flow-designer/canvas/EdgeLayer.vue'
import FlowCanvas from '../flow-designer/canvas/FlowCanvas.vue'
import { layoutFlow } from '../flow-designer/canvas/layout-engine.js'
import { getBusinessProcessNodeDefinition } from './business-process-node-types.js'

const props = defineProps({
  schema: { type: Object, required: true },
  selectedNodeId: { type: String, default: null },
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['nodeSelect', 'canvasClick'])
const canvasRef = ref(null)

const layoutInput = computed(() => ({
  nodes: (props.schema?.nodes || []).map(node => ({
    id: node.id,
    nodeType: layoutNodeType(node.type),
  })),
  edges: (props.schema?.edges || []).map(edge => ({ ...edge })),
}))

const layoutResult = computed(() => layoutFlow(layoutInput.value, {
  NODE_WIDTH: 288,
  NODE_HEIGHT: 92,
  V_GAP: 72,
  H_GAP: 84,
  MARGIN_TOP: 64,
  MARGIN_LEFT: 220,
}))

function nodeStyle(nodeId) {
  const position = layoutResult.value.nodePositions.get(nodeId)
  if (!position)
    return { display: 'none' }
  return {
    left: `${position.x}px`,
    top: `${position.y}px`,
    width: `${position.width}px`,
    minHeight: `${position.height}px`,
  }
}

function nodeDefinition(node) {
  return getBusinessProcessNodeDefinition(node.type) || {
    label: node.type || '未知节点',
    tone: 'unknown',
    ports: [],
  }
}

function nodeSummary(node) {
  const definition = nodeDefinition(node)
  if (node.type === 'APPROVAL')
    return node.config?.flowModelKey || '选择已发布审批模型'
  if (node.type === 'ACTION')
    return actionLabel(node.config?.actionType)
  if (node.type === 'CONDITION')
    return `${node.config?.branches?.length || node.ports?.length || 0} 个结果分支`
  if (node.type === 'SUB_PROCESS')
    return node.config?.processCode || '选择当前应用已发布流程'
  return definition.label
}

function actionLabel(actionType) {
  const labels = {
    UPDATE_RECORD: '更新记录',
    CREATE_RECORD: '创建记录',
    BUSINESS_ACTION: '执行业务动作',
    EXECUTE_BUSINESS_ACTION: '执行业务动作',
    DOMAIN_ACTION: '执行领域动作',
    SEND_MESSAGE: '发送消息',
    INVOKE_CAPABILITY: '调用受治理能力',
  }
  return labels[actionType] || '配置受控动作'
}

function layoutNodeType(type) {
  if (type?.startsWith('START_'))
    return 'start'
  if (type === 'CONDITION')
    return 'condition'
  if (type === 'END')
    return 'end'
  return 'service'
}

function handleNodeSelect(node) {
  emit('nodeSelect', node)
}

defineExpose({
  canvasRef,
  layoutResult,
})
</script>

<template>
  <div class="business-process-canvas relative h-full min-h-120 w-full overflow-hidden">
    <FlowCanvas ref="canvasRef" :readonly="readonly" @canvas-click="emit('canvasClick', $event)">
      <template #edges>
        <EdgeLayer
          :edges="layoutInput.edges"
          :paths="layoutResult.edgePaths"
          :canvas-bounds="layoutResult.canvasBounds"
          :show-labels="false"
        />
      </template>

      <template #nodes>
        <button
          v-for="node in schema.nodes"
          :key="node.id"
          type="button"
          class="business-process-node absolute z-2 flex items-stretch overflow-hidden text-left"
          :class="[
            `is-${nodeDefinition(node).tone}`,
            { 'is-selected': selectedNodeId === node.id },
          ]"
          :style="nodeStyle(node.id)"
          :data-node-id="node.id"
          data-business-process-node
          :aria-pressed="selectedNodeId === node.id"
          @click.stop="handleNodeSelect(node)"
        >
          <span class="node-rail" aria-hidden="true" />
          <span class="min-w-0 flex flex-col flex-1 justify-center px-4 py-3">
            <span class="node-kicker">{{ nodeDefinition(node).label }}</span>
            <span class="node-title mt-1 truncate">{{ node.name || nodeDefinition(node).label }}</span>
            <span class="node-summary mt-1 truncate">{{ nodeSummary(node) }}</span>
          </span>
          <span class="node-status flex items-center pr-4" aria-hidden="true">
            <span class="node-status-dot" />
          </span>
        </button>
      </template>
    </FlowCanvas>
  </div>
</template>

<style scoped>
.business-process-canvas {
  background: var(--body-color, #f7f9fa);
}

.business-process-node {
  border: 1px solid rgba(100, 116, 139, 0.28);
  border-radius: 9px;
  background: var(--card-color, #fff);
  color: var(--text-color-base, #0f172a);
  box-shadow: 0 5px 16px rgba(15, 23, 42, 0.07);
  transition:
    border-color 150ms ease,
    box-shadow 150ms ease,
    transform 150ms ease;
}

.business-process-node:hover {
  border-color: rgba(37, 99, 235, 0.42);
  box-shadow: 0 9px 24px rgba(15, 23, 42, 0.1);
  transform: translateY(-1px);
}

.business-process-node.is-selected {
  border-color: var(--primary-color, #2563eb);
  box-shadow:
    0 0 0 2px rgba(37, 99, 235, 0.12),
    0 9px 24px rgba(15, 23, 42, 0.1);
}

.node-rail {
  width: 4px;
  flex: 0 0 4px;
  background: #64748b;
}

.is-manual .node-rail,
.is-event .node-rail,
.is-schedule .node-rail {
  background: #0f766e;
}

.is-condition .node-rail {
  background: #c17a16;
}

.is-action .node-rail,
.is-sub-process .node-rail {
  background: #2563eb;
}

.is-approval .node-rail {
  background: #7c3aed;
}

.is-end .node-rail {
  background: #475569;
}

.node-kicker {
  color: var(--text-color-3, #64748b);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.node-title {
  color: var(--text-color-1, #0f172a);
  font-size: 14px;
  font-weight: 650;
  line-height: 1.35;
}

.node-summary {
  color: var(--text-color-3, #64748b);
  font-size: 12px;
  line-height: 1.35;
}

.node-status-dot {
  width: 7px;
  height: 7px;
  border: 2px solid rgba(100, 116, 139, 0.55);
  border-radius: 999px;
}

.business-process-node.is-selected .node-status-dot {
  border-color: var(--primary-color, #2563eb);
  background: var(--primary-color, #2563eb);
}

@media (prefers-reduced-motion: reduce) {
  .business-process-node {
    transition: none;
  }
}
</style>
