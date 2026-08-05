<script setup>
import { computed, ref } from 'vue'
import EdgeLayer from '../flow-designer/canvas/EdgeLayer.vue'
import FlowCanvas from '../flow-designer/canvas/FlowCanvas.vue'
import { layoutFlow } from '../flow-designer/canvas/layout-engine.js'
import BusinessProcessNodeRenderer from './BusinessProcessNodeRenderer.vue'

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
        <BusinessProcessNodeRenderer
          v-for="node in schema.nodes"
          :key="node.id"
          :node="node"
          :position="layoutResult.nodePositions.get(node.id)"
          :selected="selectedNodeId === node.id"
          :readonly="readonly"
          @select="handleNodeSelect"
        />
      </template>
    </FlowCanvas>
  </div>
</template>

<style scoped>
.business-process-canvas {
  background: var(--body-color, #f7f9fa);
}
</style>
