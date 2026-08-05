<script setup>
import { computed } from 'vue'
import { getBusinessProcessNodeDefinition } from './business-process-node-types.js'

const props = defineProps({
  node: { type: Object, required: true },
  position: { type: Object, default: null },
  selected: { type: Boolean, default: false },
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['select'])

const definition = computed(() => getBusinessProcessNodeDefinition(props.node.type) || ({
  label: props.node.type || '未知节点',
  tone: 'unknown',
  ports: [],
}))

const style = computed(() => {
  if (!props.position)
    return { display: 'none' }
  return {
    left: `${props.position.x}px`,
    top: `${props.position.y}px`,
    width: `${props.position.width}px`,
    minHeight: `${props.position.height}px`,
  }
})

const summary = computed(() => {
  if (props.node.type === 'APPROVAL')
    return props.node.config?.flowModelKey || '选择已发布审批模型'
  if (props.node.type === 'ACTION')
    return actionLabel(props.node.config?.actionType)
  if (props.node.type === 'CONDITION')
    return `${props.node.config?.branches?.length || props.node.ports?.length || 0} 个结果分支`
  if (props.node.type === 'SUB_PROCESS')
    return props.node.config?.processCode || '选择当前应用已发布流程'
  return definition.value.label
})

const visiblePorts = computed(() => {
  if (props.node.type === 'CONDITION' || props.node.type === 'APPROVAL')
    return props.node.ports || []
  return []
})

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
</script>

<template>
  <button
    type="button"
    class="business-process-node absolute z-2 flex items-stretch overflow-visible text-left"
    :class="[
      `is-${definition.tone}`,
      { 'is-selected': selected },
    ]"
    :style="style"
    :data-node-id="node.id"
    data-business-process-node
    :aria-pressed="selected"
    :disabled="readonly"
    @click.stop="emit('select', node)"
  >
    <span class="node-shell min-w-0 flex flex-1 items-stretch overflow-hidden">
      <span class="node-rail" aria-hidden="true" />
      <span class="min-w-0 flex flex-col flex-1 justify-center px-4 py-3">
        <span class="node-kicker">{{ definition.label }}</span>
        <span class="node-title mt-1 truncate">{{ node.name || definition.label }}</span>
        <span class="node-summary mt-1 truncate">{{ summary }}</span>
      </span>
      <span class="node-status flex items-center pr-4" aria-hidden="true">
        <span class="node-status-dot" />
      </span>
    </span>

    <span v-if="visiblePorts.length" class="node-ports" aria-label="节点结果出口">
      <span
        v-for="port in visiblePorts"
        :key="port"
        class="node-port"
        data-business-port
      >
        {{ port }}
      </span>
    </span>
  </button>
</template>

<style scoped>
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

.business-process-node:hover:not(:disabled) {
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

.business-process-node:disabled {
  cursor: default;
}

.node-shell {
  border-radius: inherit;
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

.node-ports {
  position: absolute;
  top: calc(100% + 7px);
  left: 8px;
  display: flex;
  max-width: calc(100% - 16px);
  gap: 4px;
}

.node-port {
  overflow: hidden;
  padding: 2px 5px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 4px;
  background: var(--card-color, #fff);
  color: var(--text-color-3, #64748b);
  font-size: 9px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .business-process-node {
    transition: none;
  }
}
</style>
