<template>
  <section class="field-event-editor">
    <div class="field-event-editor__head">
      <div>
        <strong>字段查询回填</strong>
        <p>从受管接口或数据集中查询，并按规则回填表单字段。</p>
      </div>
      <n-button size="tiny" type="primary" secondary @click="openCreate">
        添加规则
      </n-button>
    </div>

    <div v-if="normalizedRules.length" class="field-event-list">
      <article v-for="(rule, index) in normalizedRules" :key="rule.id || index" class="field-event-card">
        <div class="field-event-card__main">
          <div class="field-event-card__title">
            <span>{{ rule.name || '未命名查询' }}</span>
            <n-tag size="tiny" :type="rule.enabled === false ? 'default' : 'success'">
              {{ rule.enabled === false ? '停用' : triggerLabel(rule.trigger) }}
            </n-tag>
          </div>
          <p>{{ fieldLabel(rule.sourceField) }} · {{ sourceLabel(rule) }}</p>
          <span>{{ rule.paramMappings?.length || 0 }} 个参数，{{ rule.resultMappings?.length || 0 }} 个回填字段</span>
        </div>
        <div class="field-event-card__actions">
          <n-button text size="tiny" type="primary" @click="openEdit(index)">
            设置
          </n-button>
          <n-dropdown
            trigger="click"
            :options="ruleActionOptions(index, rule)"
            @select="handleRuleAction"
          >
            <n-button text size="tiny">
              更多
            </n-button>
          </n-dropdown>
        </div>
      </article>
    </div>
    <n-empty v-else size="small" description="还没有字段查询规则" />

    <n-modal
      v-model:show="modalVisible"
      preset="card"
      :title="editingIndex < 0 ? '添加字段查询规则' : '设置字段查询规则'"
      class="field-event-modal"
      style="width: min(920px, calc(100vw - 32px))"
      :mask-closable="false"
    >
      <div class="field-event-form">
        <section class="field-event-form__section">
          <h4>触发与查询源</h4>
          <div class="field-event-form__grid">
            <n-form-item label="规则名称" required>
              <n-input v-model:value="draft.name" maxlength="80" placeholder="例如：手机号查询联系人" />
            </n-form-item>
            <n-form-item label="触发时机" required>
              <n-select v-model:value="draft.trigger" :options="triggerOptions" @update:value="handleTriggerChange" />
            </n-form-item>
            <n-form-item v-if="draft.trigger !== 'FORM_LOAD'" label="触发字段" required>
              <n-select v-model:value="draft.sourceField" :options="fieldOptions" filterable placeholder="选择表单字段" />
            </n-form-item>
            <n-form-item label="受管查询源" required>
              <n-select
                :value="selectedSourceValue"
                :options="sourceOptions"
                :loading="catalogLoading"
                filterable
                placeholder="选择已开放的接口或数据集"
                @update:value="handleSourceChange"
              />
            </n-form-item>
          </div>
          <div class="field-event-form__switches">
            <label><span>启用规则</span><n-switch v-model:value="draft.enabled" size="small" /></label>
            <label><span>空值时跳过</span><n-switch v-model:value="draft.skipWhenEmpty" size="small" /></label>
            <label><span>查询前清空旧结果</span><n-switch v-model:value="draft.clearTargetsOnTrigger" size="small" /></label>
            <label v-if="draft.trigger === 'CHANGE'">
              <span>防抖毫秒</span>
              <n-input-number v-model:value="draft.debounceMs" :min="0" :max="5000" :step="100" size="small" />
            </label>
          </div>
        </section>

        <section class="field-event-form__section">
          <div class="field-event-form__section-head">
            <div>
              <h4>查询参数</h4>
              <p>参数只从当前表单、只读运行上下文或路由参数取值。</p>
            </div>
          </div>
          <n-spin :show="metadataLoading">
            <div v-if="draft.paramMappings.length" class="mapping-list">
              <div v-for="(mapping, index) in draft.paramMappings" :key="`${mapping.param}_${index}`" class="mapping-row mapping-row--params">
                <div class="mapping-fixed">
                  <strong>{{ inputParamLabel(mapping.param) }}</strong>
                  <span>{{ mapping.param }}</span>
                </div>
                <n-select
                  v-model:value="mapping.source"
                  :options="paramSourceOptions"
                  size="small"
                  @update:value="resetParamMappingPath(mapping)"
                />
                <n-select
                  v-if="mapping.source === 'FORM_FIELD'"
                  v-model:value="mapping.field"
                  :options="fieldOptions"
                  filterable
                  size="small"
                  placeholder="选择字段"
                />
                <n-input
                  v-else
                  v-model:value="mapping.path"
                  size="small"
                  :placeholder="mapping.source === 'CONTEXT_PATH' ? '如 currentUser.userId' : '如 channel'"
                />
              </div>
            </div>
            <n-empty v-else size="small" description="该查询源不需要参数" />
          </n-spin>
        </section>

        <section class="field-event-form__section">
          <div class="field-event-form__section-head">
            <div>
              <h4>结果回填</h4>
              <p>只会写入这里显式选择的目标字段。</p>
            </div>
            <n-button size="tiny" secondary @click="addResultMapping">
              添加回填
            </n-button>
          </div>
          <n-form-item label="结果取值方式" class="result-mode-field">
            <n-radio-group v-model:value="draft.resultMode" size="small">
              <n-radio-button value="ROOT">
                根对象
              </n-radio-button>
              <n-radio-button value="FIRST_ROW">
                列表首行
              </n-radio-button>
            </n-radio-group>
          </n-form-item>
          <div v-if="draft.resultMappings.length" class="mapping-list">
            <div v-for="(mapping, index) in draft.resultMappings" :key="index" class="mapping-row mapping-row--result">
              <n-select
                v-model:value="mapping.from"
                :options="resultFieldOptions"
                filterable
                tag
                size="small"
                placeholder="返回字段路径"
              />
              <span class="mapping-arrow">→</span>
              <n-select v-model:value="mapping.to" :options="fieldOptions" filterable size="small" placeholder="表单目标字段" />
              <n-select v-model:value="mapping.whenMissing" :options="missingOptions" size="small" />
              <n-button text size="tiny" type="error" @click="removeResultMapping(index)">
                删除
              </n-button>
            </div>
          </div>
          <n-empty v-else size="small" description="至少添加一个回填字段" />
        </section>

        <section class="field-event-form__section">
          <h4>用户反馈</h4>
          <div class="field-event-form__grid">
            <n-form-item label="未找到提示">
              <n-input v-model:value="draft.notFoundMessage" maxlength="200" />
            </n-form-item>
            <n-form-item label="查询失败提示">
              <n-input v-model:value="draft.errorMessage" maxlength="200" />
            </n-form-item>
          </div>
        </section>
        <n-alert v-if="validationMessage" type="warning" :show-icon="false">
          {{ validationMessage }}
        </n-alert>
      </div>

      <template #footer>
        <n-space justify="end">
          <n-button @click="modalVisible = false">
            取消
          </n-button>
          <n-button type="primary" @click="saveDraft">
            保存规则
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getLowcodeQuerySourceCatalog, getLowcodeQuerySourceMetadata } from '@/api/lowcode-query-source'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => [],
  },
  fieldOptions: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['update:modelValue'])
const modalVisible = ref(false)
const editingIndex = ref(-1)
const draft = ref(createDraft())
const catalog = ref([])
const metadata = ref(null)
const catalogLoading = ref(false)
const metadataLoading = ref(false)
const validationMessage = ref('')

const triggerOptions = [
  { label: '表单打开后', value: 'FORM_LOAD' },
  { label: '字段值变化', value: 'CHANGE' },
  { label: '字段失去焦点', value: 'BLUR' },
  { label: '点击查询按钮', value: 'MANUAL' },
  { label: '扫码或按 Enter', value: 'SCAN_COMPLETE' },
]
const paramSourceOptions = [
  { label: '表单字段', value: 'FORM_FIELD' },
  { label: '运行上下文', value: 'CONTEXT_PATH' },
  { label: '路由参数', value: 'ROUTE_QUERY' },
]
const missingOptions = [
  { label: '清空旧值', value: 'CLEAR' },
  { label: '保留旧值', value: 'KEEP' },
]

const normalizedRules = computed(() => Array.isArray(props.modelValue) ? props.modelValue : [])
const sourceOptions = computed(() => catalog.value.map(item => ({
  label: `${item.sourceName || item.sourceKey} · ${item.sourceType === 'DATASET' ? '数据集' : '外部接口'}`,
  value: `${item.sourceType}::${item.sourceKey}`,
})))
const selectedSourceValue = computed(() => draft.value.sourceType && draft.value.sourceKey
  ? `${draft.value.sourceType}::${draft.value.sourceKey}`
  : '')
const inputSchema = computed(() => parseInputSchema(metadata.value?.inputSchemaJson))
const resultFieldOptions = computed(() => (Array.isArray(metadata.value?.fields) ? metadata.value.fields : []).map(field => ({
  label: `${field.label || field.field} · ${field.path || field.field}`,
  value: field.path || field.field,
})))

onMounted(loadCatalog)

function createDraft(source = {}) {
  return {
    id: source.id || `field_query_${Date.now()}`,
    name: source.name || '',
    enabled: source.enabled !== false,
    trigger: source.trigger || 'BLUR',
    sourceField: source.sourceField || '',
    sourceType: source.sourceType || '',
    sourceKey: source.sourceKey || '',
    debounceMs: Number.isInteger(Number(source.debounceMs)) ? Number(source.debounceMs) : 300,
    skipWhenEmpty: source.skipWhenEmpty !== false,
    clearTargetsOnTrigger: source.clearTargetsOnTrigger === true,
    paramMappings: clone(source.paramMappings || []),
    resultMode: source.resultMode || 'ROOT',
    resultMappings: clone(source.resultMappings || []),
    notFoundMessage: source.notFoundMessage || '未匹配到数据',
    errorMessage: source.errorMessage || '查询失败，请重试',
    errorMode: source.errorMode || 'MESSAGE',
  }
}

async function loadCatalog() {
  catalogLoading.value = true
  try {
    const response = await getLowcodeQuerySourceCatalog()
    catalog.value = Array.isArray(response?.data) ? response.data : []
  }
  catch {
    catalog.value = []
  }
  finally {
    catalogLoading.value = false
  }
}

async function loadMetadata() {
  if (!draft.value.sourceType || !draft.value.sourceKey) {
    metadata.value = null
    return
  }
  metadataLoading.value = true
  try {
    const response = await getLowcodeQuerySourceMetadata({
      sourceType: draft.value.sourceType,
      sourceKey: draft.value.sourceKey,
    })
    metadata.value = response?.data || null
    syncParamMappings()
    if (draft.value.resultMode === 'ROOT' && draft.value.sourceType === 'DATASET')
      draft.value.resultMode = 'FIRST_ROW'
  }
  catch {
    metadata.value = null
  }
  finally {
    metadataLoading.value = false
  }
}

function syncParamMappings() {
  const current = new Map(draft.value.paramMappings.map(item => [item.param, item]))
  draft.value.paramMappings = inputSchema.value.map((item) => {
    const existing = current.get(item.name)
    if (existing)
      return existing
    const sameField = props.fieldOptions.find(option => option.value === item.name)
    return {
      param: item.name,
      source: 'FORM_FIELD',
      field: sameField?.value || draft.value.sourceField || '',
    }
  })
}

function openCreate() {
  editingIndex.value = -1
  draft.value = createDraft()
  metadata.value = null
  validationMessage.value = ''
  modalVisible.value = true
  if (!catalog.value.length)
    loadCatalog()
}

function openEdit(index) {
  editingIndex.value = index
  draft.value = createDraft(normalizedRules.value[index])
  validationMessage.value = ''
  modalVisible.value = true
  loadMetadata()
}

function handleTriggerChange(trigger) {
  if (trigger === 'FORM_LOAD')
    draft.value.sourceField = ''
  if (trigger !== 'CHANGE')
    draft.value.debounceMs = 0
}

function handleSourceChange(value) {
  const [sourceType = '', ...sourceKeyParts] = String(value || '').split('::')
  draft.value.sourceType = sourceType
  draft.value.sourceKey = sourceKeyParts.join('::')
  draft.value.paramMappings = []
  draft.value.resultMappings = []
  loadMetadata()
}

function resetParamMappingPath(mapping) {
  mapping.field = ''
  mapping.path = ''
}

function addResultMapping() {
  draft.value.resultMappings.push({ from: '', to: '', whenMissing: 'CLEAR' })
}

function removeResultMapping(index) {
  draft.value.resultMappings.splice(index, 1)
}

function saveDraft() {
  validationMessage.value = validateDraft()
  if (validationMessage.value)
    return
  const list = clone(normalizedRules.value)
  if (editingIndex.value < 0)
    list.push(clone(draft.value))
  else
    list.splice(editingIndex.value, 1, clone(draft.value))
  emit('update:modelValue', list)
  modalVisible.value = false
}

function validateDraft() {
  if (!draft.value.name.trim())
    return '请填写规则名称'
  if (draft.value.trigger !== 'FORM_LOAD' && !draft.value.sourceField)
    return '请选择触发字段'
  if (!draft.value.sourceType || !draft.value.sourceKey)
    return '请选择受管查询源'
  if (!draft.value.resultMappings.length)
    return '请至少添加一个结果回填字段'
  if (draft.value.paramMappings.some(item => !item.param || !item.source || (item.source === 'FORM_FIELD' ? !item.field : !item.path)))
    return '请完整配置查询参数来源'
  if (draft.value.resultMappings.some(item => !item.to))
    return '请完整配置结果目标字段'
  const targetFields = draft.value.resultMappings.map(item => item.to)
  if (new Set(targetFields).size !== targetFields.length)
    return '同一规则不能重复回填同一个字段'
  return ''
}

function ruleActionOptions(index, rule) {
  return [
    { label: rule.enabled === false ? '启用' : '停用', key: `toggle:${index}` },
    { label: '复制', key: `copy:${index}` },
    { label: '上移', key: `up:${index}`, disabled: index === 0 },
    { label: '下移', key: `down:${index}`, disabled: index === normalizedRules.value.length - 1 },
    { label: '删除', key: `delete:${index}` },
  ]
}

function handleRuleAction(key) {
  const [action, rawIndex] = String(key || '').split(':')
  const index = Number(rawIndex)
  const list = clone(normalizedRules.value)
  if (!Number.isInteger(index) || !list[index])
    return
  if (action === 'toggle')
    list[index].enabled = list[index].enabled === false
  else if (action === 'copy')
    list.splice(index + 1, 0, { ...list[index], id: `field_query_${Date.now()}`, name: `${list[index].name || '查询规则'} 副本` })
  else if (action === 'delete')
    list.splice(index, 1)
  else if (action === 'up' && index > 0)
    [list[index - 1], list[index]] = [list[index], list[index - 1]]
  else if (action === 'down' && index < list.length - 1)
    [list[index + 1], list[index]] = [list[index], list[index + 1]]
  emit('update:modelValue', list)
}

function parseInputSchema(value) {
  if (Array.isArray(value))
    return value.filter(item => item?.name)
  try {
    const parsed = JSON.parse(value || '[]')
    return Array.isArray(parsed) ? parsed.filter(item => item?.name) : []
  }
  catch {
    return []
  }
}

function inputParamLabel(param) {
  return inputSchema.value.find(item => item.name === param)?.label || param
}

function triggerLabel(trigger) {
  return triggerOptions.find(item => item.value === trigger)?.label || trigger || '未配置'
}

function fieldLabel(field) {
  if (!field)
    return '表单打开'
  return props.fieldOptions.find(item => item.value === field)?.label || field
}

function sourceLabel(rule) {
  return catalog.value.find(item => item.sourceType === rule.sourceType && item.sourceKey === rule.sourceKey)?.sourceName
    || rule.sourceKey
    || '未选择查询源'
}

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}
</script>

<style scoped>
.field-event-editor {
  display: grid;
  gap: 10px;
}

.field-event-editor__head,
.field-event-form__section-head,
.field-event-card,
.field-event-card__title,
.field-event-card__actions,
.field-event-form__switches,
.mapping-row {
  display: flex;
  align-items: center;
}

.field-event-editor__head,
.field-event-form__section-head,
.field-event-card {
  justify-content: space-between;
  gap: 12px;
}

.field-event-editor__head strong,
.field-event-form h4 {
  color: var(--n-text-color);
  font-size: 13px;
  font-weight: 600;
}

.field-event-editor__head p,
.field-event-form__section-head p,
.field-event-card p {
  margin: 3px 0 0;
  color: var(--n-text-color-3);
  font-size: 11px;
  line-height: 16px;
}

.field-event-list,
.field-event-form,
.mapping-list {
  display: grid;
  gap: 8px;
}

.field-event-card {
  padding: 10px;
  border: 1px solid var(--n-border-color);
  border-radius: 6px;
  background: var(--n-color);
}

.field-event-card__main {
  overflow: hidden;
  min-width: 0;
}

.field-event-card__title {
  gap: 6px;
}

.field-event-card__title > span:first-child {
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-event-card__main > span {
  color: var(--n-text-color-3);
  font-size: 11px;
}

.field-event-card__actions {
  flex: 0 0 auto;
  gap: 8px;
}

.field-event-form {
  max-height: min(680px, calc(100vh - 190px));
  overflow-y: auto;
  padding-right: 4px;
}

.field-event-form__section {
  padding: 14px;
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
}

.field-event-form h4 {
  margin: 0 0 12px;
}

.field-event-form__section-head h4 {
  margin-bottom: 0;
}

.field-event-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.field-event-form__switches {
  flex-wrap: wrap;
  gap: 8px 18px;
}

.field-event-form__switches label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--n-text-color-2);
  font-size: 12px;
}

.mapping-row {
  gap: 8px;
}

.mapping-row--params {
  display: grid;
  grid-template-columns: minmax(120px, 0.7fr) minmax(120px, 0.7fr) minmax(180px, 1.2fr);
}

.mapping-row--result {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 22px minmax(180px, 1fr) 110px auto;
}

.mapping-fixed {
  overflow: hidden;
  min-width: 0;
}

.mapping-fixed strong,
.mapping-fixed span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mapping-fixed strong {
  font-size: 12px;
}

.mapping-fixed span,
.mapping-arrow {
  color: var(--n-text-color-3);
  font-size: 11px;
}

.mapping-arrow {
  text-align: center;
}

.result-mode-field {
  margin-top: 10px;
}

@media (max-width: 760px) {
  .field-event-form__grid,
  .mapping-row--params,
  .mapping-row--result {
    grid-template-columns: 1fr;
  }

  .mapping-arrow {
    display: none;
  }
}
</style>
