<template>
  <n-modal
    :show="show"
    preset="card"
    title="注册开放能力"
    class="capability-register-modal"
    :mask-closable="false"
    @update:show="emit('update:show', $event)"
  >
    <n-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-placement="left"
      label-width="112px"
    >
      <n-form-item label="能力类型">
        <n-radio-group v-model:value="form.sourceType" @update:value="handleSourceTypeChange">
          <n-radio-button v-if="allowedTypes.includes('BUSINESS_ACTION')" value="BUSINESS_ACTION">
            业务动作
          </n-radio-button>
          <n-radio-button v-if="allowedTypes.includes('FLOW_ACTION')" value="FLOW_ACTION">
            流程动作
          </n-radio-button>
          <n-radio-button v-if="allowedTypes.includes('SYSTEM_SERVICE')" value="SYSTEM_SERVICE">
            系统服务
          </n-radio-button>
        </n-radio-group>
      </n-form-item>

      <n-alert v-if="sourceError" type="error" class="form-alert">
        {{ sourceError }}
      </n-alert>

      <n-form-item v-if="form.sourceType !== 'SYSTEM_SERVICE'" label="业务对象" path="objectId">
        <n-select
          v-model:value="form.objectId"
          :options="objectOptions"
          :loading="objectLoading"
          placeholder="请选择已发布业务对象"
          filterable
          @update:value="handleObjectChange"
        >
          <template #empty>
            <n-empty size="small" description="暂无已发布业务对象" />
          </template>
        </n-select>
      </n-form-item>

      <n-alert v-if="form.sourceType === 'FLOW_ACTION' && flowSourceError" type="error" class="form-alert">
        {{ flowSourceError }}
      </n-alert>
      <n-alert
        v-else-if="form.sourceType === 'FLOW_ACTION' && flowSource"
        type="success"
        class="form-alert"
      >
        已匹配主流程 {{ flowSource.flowModelKey }}，发布对象版本 v{{ flowSource.publishedObjectVersion }}。
      </n-alert>

      <template v-if="form.sourceType === 'BUSINESS_ACTION'">
        <n-form-item label="业务动作" path="actionCode">
          <n-select
            v-model:value="form.actionCode"
            :options="actionOptions"
            :loading="detailLoading"
            :disabled="!form.objectId"
            placeholder="请选择已启用业务动作"
            filterable
            @update:value="handleActionChange"
          >
            <template #empty>
              <n-empty size="small" description="该对象暂无可发布动作" />
            </template>
          </n-select>
        </n-form-item>
        <n-form-item label="允许字段" path="allowedFields">
          <n-select
            v-model:value="form.allowedFields"
            :options="fieldOptions"
            :loading="detailLoading"
            :disabled="!form.objectId"
            placeholder="选择外部调用可以写入的字段"
            multiple
            filterable
            clearable
          >
            <template #empty>
              <n-empty size="small" description="该对象暂无可写业务字段" />
            </template>
          </n-select>
        </n-form-item>
        <n-form-item label="必填字段">
          <n-select
            v-model:value="form.requiredFields"
            :options="requiredFieldOptions"
            :disabled="form.allowedFields.length === 0"
            placeholder="可选，必须属于允许字段"
            multiple
            filterable
            clearable
          />
        </n-form-item>
      </template>

      <template v-else-if="form.sourceType === 'FLOW_ACTION'">
        <n-form-item label="流程动作" path="operation">
          <n-select
            v-model:value="form.operation"
            :options="flowOperationOptions"
            :loading="dictLoading"
            :disabled="!form.objectId || detailLoading || !flowSource"
            placeholder="请选择流程动作"
            @update:value="handleOperationChange"
          >
            <template #empty>
              <n-empty size="small" description="流程动作字典尚未初始化" />
            </template>
          </n-select>
        </n-form-item>
        <n-alert type="info" class="form-alert">
          流程动作只能通过用户委托 Token 调用，办理人和组织从可信登录身份解析。
        </n-alert>
      </template>

      <template v-else>
        <n-form-item label="系统服务" path="systemServiceCode">
          <n-select
            v-model:value="form.systemServiceCode"
            :options="systemServiceOptions"
            :loading="systemSourceLoading"
            placeholder="请选择平台已注册的系统服务"
            filterable
            @update:value="handleSystemServiceChange"
          >
            <template #empty>
              <n-empty size="small" description="暂无代码注册的系统服务" />
            </template>
          </n-select>
        </n-form-item>
        <n-alert v-if="selectedSystemService" type="info" class="form-alert">
          <div class="service-summary">
            <strong>{{ selectedSystemService.serviceName }}</strong>
            <span>{{ selectedSystemService.description }}</span>
            <span>
              调用主体：{{ selectedSystemServiceActorLabel }}；风险等级：{{ selectedSystemServiceRiskLabel }}
            </span>
          </div>
        </n-alert>
        <n-form-item label="流程模型" path="systemModelId">
          <n-select
            v-model:value="form.systemModelId"
            :options="systemModelOptions"
            :disabled="!selectedSystemService"
            placeholder="请选择已发布且启用的流程模型"
            filterable
            @update:value="updateGeneratedCode"
          >
            <template #empty>
              <n-empty size="small" description="暂无可开放的已发布流程模型" />
            </template>
          </n-select>
        </n-form-item>

        <n-form-item label="开放流程变量">
          <div class="variable-editor">
            <n-alert type="warning" :show-icon="true">
              流程变量会影响审批人和分支路由，默认不开放。只有外围系统确实需要传入的变量才应逐项添加。
            </n-alert>
            <div v-if="form.systemVariables.length" class="variable-list">
              <div
                v-for="(variable, index) in form.systemVariables"
                :key="variable.key"
                class="variable-row"
              >
                <n-input
                  v-model:value="variable.name"
                  placeholder="变量名"
                  maxlength="64"
                />
                <n-select
                  v-model:value="variable.type"
                  :options="systemVariableTypeOptions"
                  placeholder="类型"
                />
                <n-input
                  v-model:value="variable.description"
                  placeholder="业务含义和取值说明"
                  maxlength="200"
                />
                <n-checkbox v-model:checked="variable.required">
                  必填
                </n-checkbox>
                <n-button quaternary circle type="error" aria-label="删除变量" @click="removeSystemVariable(index)">
                  <template #icon>
                    <i class="i-material-symbols:delete-outline-rounded" />
                  </template>
                </n-button>
              </div>
            </div>
            <n-button dashed block :disabled="form.systemVariables.length >= 50" @click="addSystemVariable">
              <template #icon>
                <i class="i-material-symbols:add-rounded" />
              </template>
              添加允许外围传入的变量
            </n-button>
          </div>
        </n-form-item>
        <n-alert type="info" class="form-alert">
          外围请求不能传入模型、租户、用户、组织或发起人；这些信息由发布快照和用户委托身份固定。
        </n-alert>
      </template>

      <n-form-item label="能力编码" path="capabilityCode">
        <n-input
          v-model:value="form.capabilityCode"
          placeholder="如 business.order.create"
          maxlength="128"
          show-count
        />
      </n-form-item>
      <n-form-item label="能力版本" path="version">
        <n-input v-model:value="form.version" placeholder="如 1.0.0" />
      </n-form-item>
      <n-form-item label="能力描述">
        <n-input
          v-model:value="form.description"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-count
          placeholder="可选"
        />
      </n-form-item>
    </n-form>

    <template #footer>
      <n-space justify="end">
        <n-button @click="emit('update:show', false)">
          取消
        </n-button>
        <n-button
          type="primary"
          :loading="submitting"
          :disabled="submitDisabled"
          @click="handleSubmit"
        >
          注册并发布
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import {
  getFlowActionRegistrationSource,
  getSystemServiceRegistrationSources,
  publishBusinessActionCapability,
  publishFlowActionCapability,
  publishSystemServiceCapability,
} from '@/api/ai/capability'
import {
  businessObjectActions,
  businessObjectFields,
  businessObjectList,
} from '@/api/business-app'
import { useDict } from '@/composables'

const props = defineProps({
  show: {
    type: Boolean,
    default: false,
  },
  allowedTypes: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['update:show', 'success'])

const { dict, loading: dictLoading } = useDict(
  'ai_capability_flow_operation',
  'ai_capability_actor_type',
  'ai_capability_risk_level',
)
const formRef = ref(null)
const objectLoading = ref(false)
const detailLoading = ref(false)
const systemSourceLoading = ref(false)
const submitting = ref(false)
const sourceError = ref('')
const flowSourceError = ref('')
const flowSource = ref(null)
const objects = ref([])
const actions = ref([])
const fields = ref([])
const systemServices = ref([])
const lastGeneratedCode = ref('')
let variableKeySequence = 0

const form = reactive({
  sourceType: 'BUSINESS_ACTION',
  objectId: null,
  suiteCode: '',
  objectCode: '',
  actionCode: null,
  operation: null,
  capabilityCode: '',
  version: '1.0.0',
  description: '',
  allowedFields: [],
  requiredFields: [],
  systemServiceCode: null,
  systemModelId: null,
  systemVariables: [],
})

const flowOperationOptions = computed(() => (dict.value.ai_capability_flow_operation || [])
  .map(option => ({
    ...option,
    disabled: option.value === 'START' && flowSource.value && !flowSource.value.startSupported,
  })))

const selectedSystemService = computed(() => systemServices.value
  .find(item => item.serviceCode === form.systemServiceCode))

const selectedSystemServiceActorLabel = computed(() => resolveDictLabel(
  'ai_capability_actor_type',
  selectedSystemService.value?.requiredActorType,
))

const selectedSystemServiceRiskLabel = computed(() => resolveDictLabel(
  'ai_capability_risk_level',
  selectedSystemService.value?.riskLevel,
))

const systemServiceOptions = computed(() => systemServices.value.map(item => ({
  label: `${item.serviceName}（${item.serviceCode}）`,
  value: item.serviceCode,
})))

const systemModelOptions = computed(() => (selectedSystemService.value?.options?.models || [])
  .map(model => ({
    label: `${model.modelName}（${model.modelKey} · v${model.modelVersion}）`,
    value: model.modelId,
  })))

const systemVariableTypeOptions = computed(() => (selectedSystemService.value?.options?.variableTypes || [])
  .map(type => ({
    label: variableTypeLabel(type),
    value: type,
  })))

const submitDisabled = computed(() => {
  if (form.sourceType === 'FLOW_ACTION')
    return !flowSource.value || detailLoading.value
  if (form.sourceType === 'SYSTEM_SERVICE')
    return systemSourceLoading.value || !selectedSystemService.value || !form.systemModelId
  return false
})

const rules = {
  objectId: {
    trigger: 'change',
    validator: (_rule, value) => form.sourceType === 'SYSTEM_SERVICE' || isPositiveId(value)
      ? true
      : new Error('请选择已发布业务对象'),
  },
  actionCode: {
    trigger: 'change',
    validator: () => form.sourceType !== 'BUSINESS_ACTION' || form.actionCode
      ? true
      : new Error('请选择业务动作'),
  },
  operation: {
    trigger: 'change',
    validator: () => {
      if (form.sourceType !== 'FLOW_ACTION')
        return true
      if (!flowSource.value)
        return new Error('所选对象未匹配到可发布的主流程')
      if (!form.operation)
        return new Error('请选择流程动作')
      if (form.operation === 'START' && !flowSource.value.startSupported)
        return new Error('该对象不是平台托管运行对象，不能注册发起流程能力')
      return true
    },
  },
  allowedFields: {
    trigger: 'change',
    validator: () => form.sourceType !== 'BUSINESS_ACTION' || form.allowedFields.length > 0
      ? true
      : new Error('请至少选择一个允许字段'),
  },
  systemServiceCode: {
    trigger: 'change',
    validator: () => form.sourceType !== 'SYSTEM_SERVICE' || form.systemServiceCode
      ? true
      : new Error('请选择系统服务'),
  },
  systemModelId: {
    trigger: 'change',
    validator: () => form.sourceType !== 'SYSTEM_SERVICE' || form.systemModelId
      ? true
      : new Error('请选择已发布流程模型'),
  },
  capabilityCode: [
    { required: true, message: '请输入能力编码', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$/,
      message: '使用小写点分编码，每段以字母开头',
      trigger: 'blur',
    },
  ],
  version: [
    { required: true, message: '请输入能力版本', trigger: 'blur' },
    {
      pattern: /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/,
      message: '版本必须使用三段语义版本，如 1.0.0',
      trigger: 'blur',
    },
  ],
}

function isPositiveId(value) {
  if (typeof value === 'number')
    return Number.isInteger(value) && value > 0
  return typeof value === 'string' && /^[1-9]\d*$/.test(value)
}

const objectOptions = computed(() => objects.value.map(item => ({
  label: `${item.objectName || item.objectCode}（${item.objectCode}）`,
  value: item.id,
})))

const actionOptions = computed(() => actions.value
  .filter(item => item.status !== 0)
  .map(item => ({
    label: `${item.actionName || item.actionCode}（${item.actionCode}）`,
    value: item.actionCode,
  })))

const fieldOptions = computed(() => fields.value
  .filter(item => !item.systemField && !item.readonly && item.fieldStatus !== 'DISABLED')
  .map(item => ({
    label: `${item.fieldName || item.fieldCode}（${item.fieldCode}）`,
    value: item.fieldCode,
  })))

const requiredFieldOptions = computed(() => fieldOptions.value
  .filter(item => form.allowedFields.includes(item.value)))

function resolveDictLabel(dictType, value) {
  if (!value)
    return '-'
  const option = (dict.value[dictType] || [])
    .find(item => String(item.value) === String(value))
  return option?.label || value
}

watch(() => props.show, async (visible) => {
  if (!visible)
    return
  resetForm()
  if (form.sourceType === 'SYSTEM_SERVICE')
    await loadSystemServices()
  else
    await loadObjects()
})

watch(flowOperationOptions, (options) => {
  if (form.sourceType !== 'FLOW_ACTION' || form.operation || options.length === 0)
    return
  const defaultOption = options.find(item => item.isDefault === 'Y') || options[0]
  form.operation = defaultOption.value
  updateGeneratedCode()
}, { immediate: true })

watch(() => form.allowedFields, (allowedFields) => {
  form.requiredFields = form.requiredFields.filter(field => allowedFields.includes(field))
}, { deep: true })

function resetForm() {
  const sourceType = props.allowedTypes.includes('BUSINESS_ACTION')
    ? 'BUSINESS_ACTION'
    : props.allowedTypes[0] || 'BUSINESS_ACTION'
  Object.assign(form, {
    sourceType,
    objectId: null,
    suiteCode: '',
    objectCode: '',
    actionCode: null,
    operation: resolveDefaultOperation(),
    capabilityCode: '',
    version: '1.0.0',
    description: '',
    allowedFields: [],
    requiredFields: [],
    systemServiceCode: null,
    systemModelId: null,
    systemVariables: [],
  })
  actions.value = []
  fields.value = []
  sourceError.value = ''
  flowSourceError.value = ''
  flowSource.value = null
  systemServices.value = []
  lastGeneratedCode.value = ''
}

function resolveDefaultOperation() {
  const options = flowOperationOptions.value
  return (options.find(item => item.isDefault === 'Y') || options[0])?.value || null
}

async function loadObjects() {
  objectLoading.value = true
  sourceError.value = ''
  try {
    const res = await businessObjectList({})
    objects.value = (res.data || []).filter(item => item.status === 1
      && item.designStatus === 'PUBLISHED'
      && Number(item.lastPublishVersion || 0) > 0)
  }
  catch (error) {
    objects.value = []
    sourceError.value = error?.message || '已发布业务对象加载失败'
  }
  finally {
    objectLoading.value = false
  }
}

async function loadSystemServices() {
  systemSourceLoading.value = true
  sourceError.value = ''
  systemServices.value = []
  try {
    const res = await getSystemServiceRegistrationSources()
    systemServices.value = res.data || []
    if (systemServices.value.length === 1) {
      form.systemServiceCode = systemServices.value[0].serviceCode
      handleSystemServiceChange(form.systemServiceCode)
    }
  }
  catch (error) {
    sourceError.value = error?.message || '系统服务注册来源加载失败'
  }
  finally {
    systemSourceLoading.value = false
  }
}

async function handleSourceTypeChange() {
  form.objectId = null
  form.suiteCode = ''
  form.objectCode = ''
  form.actionCode = null
  form.operation = resolveDefaultOperation()
  form.allowedFields = []
  form.requiredFields = []
  form.systemServiceCode = null
  form.systemModelId = null
  form.systemVariables = []
  actions.value = []
  fields.value = []
  flowSourceError.value = ''
  flowSource.value = null
  updateGeneratedCode(true)
  if (form.sourceType === 'SYSTEM_SERVICE')
    await loadSystemServices()
  else if (objects.value.length === 0)
    await loadObjects()
}

async function handleObjectChange(objectId) {
  const selected = objects.value.find(item => item.id === objectId)
  form.suiteCode = selected?.suiteCode || ''
  form.objectCode = selected?.objectCode || ''
  form.actionCode = null
  form.allowedFields = []
  form.requiredFields = []
  actions.value = []
  fields.value = []
  flowSourceError.value = ''
  flowSource.value = null
  updateGeneratedCode()
  if (!selected)
    return

  detailLoading.value = true
  sourceError.value = ''
  try {
    if (form.sourceType === 'BUSINESS_ACTION') {
      const [actionRes, fieldRes] = await Promise.all([
        businessObjectActions(selected.id),
        businessObjectFields(selected.id),
      ])
      actions.value = actionRes.data || []
      fields.value = fieldRes.data || []
    }
    else {
      const res = await getFlowActionRegistrationSource({
        suiteCode: selected.suiteCode,
        objectCode: selected.objectCode,
      })
      flowSource.value = res.data
      const selectedOperation = flowOperationOptions.value
        .find(option => option.value === form.operation && !option.disabled)
      if (!selectedOperation)
        form.operation = flowOperationOptions.value.find(option => !option.disabled)?.value || null
      updateGeneratedCode()
    }
  }
  catch (error) {
    if (form.sourceType === 'FLOW_ACTION') {
      flowSourceError.value = Number(error?.code) === 404
        ? '流程能力注册接口未装配，请更新并重启 Admin 服务'
        : error?.message || '该对象未配置已启用的主流程，暂不能注册流程能力'
    }
    else {
      sourceError.value = error?.message || '业务动作和字段加载失败'
    }
  }
  finally {
    detailLoading.value = false
  }
}

function handleActionChange() {
  updateGeneratedCode()
}

function handleOperationChange() {
  updateGeneratedCode()
}

function handleSystemServiceChange() {
  form.systemModelId = null
  form.systemVariables = []
  updateGeneratedCode()
}

function addSystemVariable() {
  if (form.systemVariables.length >= 50)
    return
  const defaultType = selectedSystemService.value?.options?.variableTypes?.includes('string')
    ? 'string'
    : selectedSystemService.value?.options?.variableTypes?.[0] || 'string'
  variableKeySequence += 1
  form.systemVariables.push({
    key: `variable-${variableKeySequence}`,
    name: '',
    type: defaultType,
    description: '',
    required: false,
  })
}

function removeSystemVariable(index) {
  form.systemVariables.splice(index, 1)
}

function variableTypeLabel(type) {
  return {
    string: '文本（string）',
    integer: '整数（integer）',
    number: '数值（number）',
    boolean: '布尔（boolean）',
    object: '对象（object）',
    array: '数组（array）',
  }[type] || type
}

function updateGeneratedCode(force = false) {
  if (form.sourceType === 'SYSTEM_SERVICE') {
    const model = selectedSystemService.value?.options?.models
      ?.find(item => item.modelId === form.systemModelId)
    const parts = [
      'system',
      ...String(form.systemServiceCode || '').split('.'),
      model?.modelKey,
    ].map(normalizeCodeSegment).filter(Boolean)
    const nextCode = form.systemServiceCode && model ? parts.join('.') : ''
    if (force || !form.capabilityCode || form.capabilityCode === lastGeneratedCode.value)
      form.capabilityCode = nextCode
    lastGeneratedCode.value = nextCode
    return
  }
  const actionSegment = form.sourceType === 'BUSINESS_ACTION' ? form.actionCode : form.operation
  const parts = [
    form.sourceType === 'BUSINESS_ACTION' ? 'business' : 'flow',
    form.suiteCode,
    form.objectCode,
    actionSegment,
  ].map(normalizeCodeSegment).filter(Boolean)
  const nextCode = parts.length === 4 ? parts.join('.') : ''
  if (force || !form.capabilityCode || form.capabilityCode === lastGeneratedCode.value)
    form.capabilityCode = nextCode
  lastGeneratedCode.value = nextCode
}

function normalizeCodeSegment(value) {
  let segment = String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]+/g, '_')
    .replace(/^_+|_+$/g, '')
  if (segment && !/^[a-z]/.test(segment))
    segment = `x_${segment}`
  return segment
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
  }
  catch {
    return
  }
  if (form.sourceType === 'SYSTEM_SERVICE' && !validateSystemVariables())
    return

  submitting.value = true
  try {
    const common = {
      capabilityCode: form.capabilityCode,
      version: form.version,
      suiteCode: form.suiteCode,
      objectCode: form.objectCode,
      description: form.description || null,
    }
    let res
    if (form.sourceType === 'BUSINESS_ACTION') {
      res = await publishBusinessActionCapability({
        ...common,
        actionCode: form.actionCode,
        allowedFields: form.allowedFields,
        requiredFields: form.requiredFields,
      })
    }
    else if (form.sourceType === 'FLOW_ACTION') {
      res = await publishFlowActionCapability({
        ...common,
        operation: form.operation,
      })
    }
    else {
      res = await publishSystemServiceCapability({
        serviceCode: form.systemServiceCode,
        capabilityCode: form.capabilityCode,
        version: form.version,
        description: form.description || null,
        parameters: {
          modelId: form.systemModelId,
          variables: form.systemVariables.map(variable => ({
            name: variable.name.trim(),
            type: variable.type,
            description: variable.description.trim(),
            required: variable.required,
          })),
        },
      })
    }
    if (res.code === 200) {
      window.$message.success('能力已注册并发布')
      emit('update:show', false)
      emit('success', res.data)
    }
  }
  finally {
    submitting.value = false
  }
}

function validateSystemVariables() {
  const names = new Set()
  for (const [index, variable] of form.systemVariables.entries()) {
    const name = variable.name.trim()
    if (!/^[a-z]\w{0,63}$/i.test(name)) {
      window.$message.error(`第 ${index + 1} 个流程变量名称无效，只能以字母开头并包含字母、数字、下划线`)
      return false
    }
    if (names.has(name)) {
      window.$message.error(`流程变量名称重复：${name}`)
      return false
    }
    names.add(name)
    if (!variable.description.trim()) {
      window.$message.error(`请填写流程变量 ${name} 的业务含义和取值说明`)
      return false
    }
  }
  return true
}
</script>

<style scoped>
.form-alert {
  margin-bottom: 18px;
}

.service-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
  line-height: 1.55;
}

.variable-editor {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
  gap: 12px;
}

.variable-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.variable-row {
  display: grid;
  grid-template-columns: minmax(120px, 0.9fr) 150px minmax(180px, 1.4fr) auto 34px;
  align-items: center;
  gap: 8px;
}

@media (max-width: 760px) {
  .variable-row {
    grid-template-columns: 1fr;
    padding: 12px;
    border: 1px solid var(--border-light);
  }
}

:global(.capability-register-modal) {
  width: min(920px, calc(100vw - 32px));
}
</style>
