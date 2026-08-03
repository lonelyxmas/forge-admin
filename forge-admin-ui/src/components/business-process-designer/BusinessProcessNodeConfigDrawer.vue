<script setup>
import { NButton, NDrawer, NDrawerContent } from 'naive-ui'
import { computed, ref, watch } from 'vue'
import ActionAndApprovalNodeConfig from './ActionAndApprovalNodeConfig.vue'
import { createBusinessProcessNodeTemplate, isBusinessProcessStartType } from './business-process-node-types.js'
import StartNodeConfig from './StartNodeConfig.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  node: { type: Object, default: null },
  objectCode: { type: String, default: '' },
  objectName: { type: String, default: '' },
  fields: { type: Array, default: () => [] },
  objects: { type: Array, default: () => [] },
  flowModels: { type: Array, default: () => [] },
  formAssets: { type: Array, default: () => [] },
  businessActions: { type: Array, default: () => [] },
  messageTemplates: { type: Array, default: () => [] },
  capabilities: { type: Array, default: () => [] },
  subProcesses: { type: Array, default: () => [] },
  serviceActors: { type: Array, default: () => [] },
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:visible',
  'save',
  'openFlowDesigner',
  'refreshFlowModel',
])

const draftNode = ref(null)
const draftRecordIdSource = ref(null)

const title = computed(() => draftNode.value?.name || '节点配置')
const isExecutionNode = computed(() => ['ACTION', 'APPROVAL', 'SUB_PROCESS'].includes(draftNode.value?.type))

watch([
  () => props.node,
  () => props.visible,
], ([node, visible]) => {
  if (visible && node) {
    draftNode.value = clone(node)
    draftRecordIdSource.value = null
  }
}, { deep: true, immediate: true })

function patchConfig(config) {
  draftNode.value.config = clone(config)
}

function handleStartType(type) {
  const template = createBusinessProcessNodeTemplate(type)
  draftNode.value = {
    ...draftNode.value,
    type,
    ports: template.ports,
    config: template.config,
  }
}

function handleConditionPort(index, value) {
  const branches = clone(draftNode.value.config?.branches || [])
  branches[index] = { ...branches[index], port: normalizePort(value, index) }
  patchBranches(branches)
}

function setDefaultBranch(index) {
  const branches = clone(draftNode.value.config?.branches || [])
    .map((branch, branchIndex) => ({
      ...branch,
      isDefault: branchIndex === index ? true : undefined,
      condition: branchIndex === index
        ? undefined
        : branch.condition || { operator: 'AND', rules: [] },
    }))
  patchBranches(branches)
}

function addConditionBranch() {
  const branches = clone(draftNode.value.config?.branches || [])
  const nextIndex = branches.length + 1
  branches.splice(Math.max(branches.length - 1, 0), 0, {
    port: `BRANCH_${nextIndex}`,
    condition: { operator: 'AND', rules: [] },
  })
  patchBranches(branches)
}

function removeConditionBranch(index) {
  const branches = clone(draftNode.value.config?.branches || [])
  if (branches.length <= 2)
    return
  branches.splice(index, 1)
  if (!branches.some(branch => branch.isDefault))
    branches[branches.length - 1].isDefault = true
  patchBranches(branches)
}

function patchBranches(branches) {
  draftNode.value.config = {
    ...(draftNode.value.config || {}),
    branches,
  }
  draftNode.value.ports = branches.map(branch => branch.port)
}

function handleSave() {
  if (!draftNode.value || props.readonly)
    return
  emit('save', clone(draftNode.value), {
    recordIdSource: draftRecordIdSource.value,
  })
  emit('update:visible', false)
}

function normalizePort(value, index) {
  const normalized = String(value || '')
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9_]/g, '_')
  return normalized || `BRANCH_${index + 1}`
}

function clone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}
</script>

<template>
  <NDrawer
    :show="visible"
    width="520"
    placement="right"
    :mask-closable="false"
    @update:show="emit('update:visible', $event)"
  >
    <NDrawerContent class="business-node-config-drawer" closable>
      <template #header>
        <div class="drawer-heading">
          <strong>{{ title }}</strong>
          <span>{{ draftNode?.type }}</span>
        </div>
      </template>

      <div v-if="draftNode" class="drawer-form">
        <label class="name-field">
          <span>节点名称</span>
          <input v-model="draftNode.name" :disabled="readonly" maxlength="80">
        </label>

        <StartNodeConfig
          v-if="isBusinessProcessStartType(draftNode.type)"
          :type="draftNode.type"
          :config="draftNode.config"
          :fields="fields"
          :service-actors="serviceActors"
          @update:type="handleStartType"
          @update:config="patchConfig"
          @update:record-id-source="draftRecordIdSource = $event"
        />

        <ActionAndApprovalNodeConfig
          v-else-if="isExecutionNode"
          :node="draftNode"
          :object-code="objectCode"
          :object-name="objectName"
          :objects="objects"
          :fields="fields"
          :flow-models="flowModels"
          :form-assets="formAssets"
          :business-actions="businessActions"
          :message-templates="messageTemplates"
          :capabilities="capabilities"
          :sub-processes="subProcesses"
          @update:config="patchConfig"
          @open-flow-designer="emit('openFlowDesigner', $event)"
          @refresh-flow-model="emit('refreshFlowModel', $event)"
        />

        <section v-else-if="draftNode.type === 'CONDITION'" class="condition-editor">
          <div class="condition-editor-head">
            <div>
              <strong>结果分支</strong>
              <span>按顺序判断，默认分支不配置条件。</span>
            </div>
            <button type="button" @click="addConditionBranch">
              添加分支
            </button>
          </div>
          <div
            v-for="(branch, index) in draftNode.config?.branches || []"
            :key="`${branch.port}-${index}`"
            class="branch-row"
          >
            <input :value="branch.port" @change="handleConditionPort(index, $event.target.value)">
            <label>
              <input type="radio" name="default-branch" :checked="branch.isDefault" @change="setDefaultBranch(index)">
              默认
            </label>
            <button type="button" :disabled="draftNode.config.branches.length <= 2" @click="removeConditionBranch(index)">
              删除
            </button>
          </div>
          <p>具体字段规则将在该分支的结构化条件面板中维护，不接受脚本表达式。</p>
        </section>

        <label v-else-if="draftNode.type === 'END'" class="name-field">
          <span>结束结果</span>
          <select v-model="draftNode.config.result">
            <option value="SUCCESS">成功完成</option>
            <option value="REJECTED">业务驳回</option>
            <option value="CANCELED">流程取消</option>
            <option value="FAILED">执行失败</option>
          </select>
        </label>
      </div>

      <template #footer>
        <div class="drawer-actions">
          <NButton @click="emit('update:visible', false)">
            取消
          </NButton>
          <NButton type="primary" :disabled="readonly" @click="handleSave">
            应用配置
          </NButton>
        </div>
      </template>
    </NDrawerContent>
  </NDrawer>
</template>

<style scoped>
.drawer-heading {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.drawer-heading strong {
  color: var(--text-color-1, #0f172a);
  font-size: 15px;
}

.drawer-heading span {
  color: var(--text-color-3, #64748b);
  font-size: 11px;
}

.drawer-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.name-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  color: var(--text-color-1, #0f172a);
  font-size: 13px;
  font-weight: 600;
}

.name-field input,
.name-field select,
.branch-row > input {
  min-height: 34px;
  border: 1px solid rgba(148, 163, 184, 0.45);
  border-radius: 6px;
  background: var(--input-color, #fff);
  padding: 6px 9px;
  color: var(--text-color-1, #0f172a);
  font-weight: 400;
  outline: none;
}

.condition-editor {
  border-top: 1px solid rgba(148, 163, 184, 0.25);
  padding-top: 16px;
}

.condition-editor-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.condition-editor-head div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.condition-editor-head span,
.condition-editor p {
  color: var(--text-color-3, #64748b);
  font-size: 12px;
}

.condition-editor-head button {
  color: var(--primary-color, #2563eb);
  font-size: 12px;
}

.branch-row {
  display: grid;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  grid-template-columns: 1fr auto auto;
}

.branch-row label {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-color-2, #475569);
  font-size: 12px;
}

.branch-row button {
  color: var(--error-color, #dc2626);
  font-size: 12px;
}

.branch-row button:disabled {
  opacity: 0.4;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  width: 100%;
}
</style>
