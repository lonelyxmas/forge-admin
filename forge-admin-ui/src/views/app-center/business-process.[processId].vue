<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import {
  businessFlowFormAssets,
  businessObjectActions,
  businessObjectFields,
} from '@/api/business-app'
import { businessApplicationObjects } from '@/api/business-application'
import {
  businessProcessDesigner,
  businessProcessFlowModels,
  businessProcessPage,
  saveBusinessProcessSchema,
  validateBusinessProcess,
} from '@/api/business-process'
import messageApi from '@/api/message'
import { businessProcessHashInput } from '@/components/business-process-designer/business-process-schema.js'
import BusinessProcessDesigner from '@/components/business-process-designer/BusinessProcessDesigner.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const process = ref(null)
const draftSchema = ref(null)
const draftSchemaHash = ref('')
const serverValidation = ref(null)
const saveState = ref('idle')
const saveError = ref('')
const dirty = ref(false)
const saveQueued = ref(false)
const applicationObjects = ref([])
const fields = ref([])
const flowModels = ref([])
const formAssets = ref([])
const businessActions = ref([])
const messageTemplates = ref([])
const subProcesses = ref([])
const capabilities = ref([])
const serviceActors = ref([])
let activeSavePromise = null

const processId = computed(() => stringValue(route.params.processId))
const subjectObject = computed(() => applicationObjects.value.find(item => (
  stringValue(item.objectId || item.id) === stringValue(process.value?.subjectObjectId)
  || item.objectCode === process.value?.subjectObjectCode
)) || null)
const pageTitle = computed(() => process.value?.processName || '业务流程设计')

watch(() => route.params.processId, loadDesigner)
onMounted(loadDesigner)

onBeforeRouteLeave(() => {
  if (!dirty.value)
    return true
  return confirmLeave()
})

async function loadDesigner() {
  if (!processId.value || loading.value)
    return
  loading.value = true
  loadError.value = ''
  try {
    const response = await businessProcessDesigner(processId.value)
    const data = normalizeIds(response.data || {})
    if (!data.id || !data.businessProcessJson)
      throw new Error('业务流程草稿不存在或已删除')
    process.value = data
    draftSchema.value = clone(data.businessProcessJson)
    draftSchemaHash.value = stringValue(data.draftSchemaHash)
    serverValidation.value = data.validation || null
    dirty.value = false
    saveQueued.value = false
    saveState.value = 'idle'
    saveError.value = ''
    await loadCatalogs()
  }
  catch (error) {
    process.value = null
    draftSchema.value = null
    loadError.value = errorMessage(error, '业务流程草稿加载失败')
  }
  finally {
    loading.value = false
  }
}

async function loadCatalogs() {
  const applicationId = stringValue(process.value?.applicationId)
  const objectId = stringValue(process.value?.subjectObjectId)
  const objectCode = process.value?.subjectObjectCode || draftSchema.value?.subject?.objectCode || ''

  const [objectsResult, fieldsResult, actionsResult, modelsResult, assetsResult, templatesResult, processesResult]
    = await Promise.allSettled([
      applicationId ? businessApplicationObjects(applicationId) : Promise.resolve({ data: [] }),
      objectId ? businessObjectFields(objectId) : Promise.resolve({ data: [] }),
      objectId ? businessObjectActions(objectId) : Promise.resolve({ data: [] }),
      businessProcessFlowModels(processId.value),
      objectCode
        ? businessFlowFormAssets(objectCode, { includeInternal: true })
        : Promise.resolve({ data: { formAssets: [] } }),
      messageApi.getTemplatePage({ pageNum: 1, pageSize: 200, status: 1 }),
      applicationId
        ? businessProcessPage({ applicationId, pageNum: 1, pageSize: 200, status: 1 })
        : Promise.resolve({ data: { records: [] } }),
    ])

  applicationObjects.value = settledData(objectsResult, [])
    .map(normalizeIds)
  fields.value = settledData(fieldsResult, []).map(normalizeIds)
  businessActions.value = settledData(actionsResult, [])
    .filter(item => item && Number(item.status ?? 1) !== 0)
    .map(normalizeIds)
  flowModels.value = settledData(modelsResult, [])
    .map(item => normalizeIds({
      ...item,
      modelId: item.modelId || item.id,
      deployed: item.deployed === true || Boolean(item.deploymentId),
    }))
  const assetData = settledData(assetsResult, { formAssets: [] })
  formAssets.value = (Array.isArray(assetData) ? assetData : assetData.formAssets || []).map(normalizeIds)
  const templateData = settledData(templatesResult, { records: [] })
  messageTemplates.value = (Array.isArray(templateData) ? templateData : templateData.records || [])
    .filter(item => item && Number(item.status ?? 1) !== 0)
    .map(normalizeIds)
  const processData = settledData(processesResult, { records: [] })
  subProcesses.value = (Array.isArray(processData) ? processData : processData.records || [])
    .filter(item => stringValue(item.id) !== processId.value && item.publishedVersion != null)
    .map(normalizeIds)

  // 受治理能力桥接和定时服务账号目录尚未交付时保持空目录，配置节点据此失败关闭。
  capabilities.value = []
  serviceActors.value = []
}

async function refreshFlowCatalog() {
  const objectCode = process.value?.subjectObjectCode || ''
  const [modelsResult, assetsResult] = await Promise.allSettled([
    businessProcessFlowModels(processId.value),
    objectCode
      ? businessFlowFormAssets(objectCode, { includeInternal: true })
      : Promise.resolve({ data: { formAssets: [] } }),
  ])
  flowModels.value = settledData(modelsResult, [])
    .map(item => normalizeIds({
      ...item,
      modelId: item.modelId || item.id,
      deployed: item.deployed === true || Boolean(item.deploymentId),
    }))
  const assetData = settledData(assetsResult, { formAssets: [] })
  formAssets.value = (Array.isArray(assetData) ? assetData : assetData.formAssets || []).map(normalizeIds)
}

function handleSchemaUpdate(value) {
  draftSchema.value = clone(value)
  if (saveState.value === 'saving')
    saveQueued.value = true
}

function handleDirtyChange(value) {
  dirty.value = Boolean(value)
}

async function handleSave(schema, metadata = {}) {
  return persistSchema(schema, metadata)
}

function persistSchema(schema, metadata = {}) {
  if (activeSavePromise)
    return activeSavePromise
  const task = persistSchemaInternal(schema, metadata)
  activeSavePromise = task
  return task.finally(() => {
    if (activeSavePromise === task)
      activeSavePromise = null
  })
}

async function persistSchemaInternal(schema, metadata = {}) {
  if (!processId.value || !schema)
    return false
  if (!isServerHash(draftSchemaHash.value)) {
    saveState.value = 'error'
    saveError.value = '草稿缺少服务端并发基线，请刷新后重试。'
    return false
  }

  const submittedSchema = clone(schema)
  const submittedHashInput = businessProcessHashInput(submittedSchema)
  saveQueued.value = false
  saveState.value = 'saving'
  saveError.value = ''
  try {
    const response = await saveBusinessProcessSchema(processId.value, {
      businessProcessJson: submittedSchema,
      expectedSchemaHash: draftSchemaHash.value,
    })
    const data = normalizeIds(response.data || {})
    const nextServerHash = stringValue(data.draftSchemaHash)
    if (!isServerHash(nextServerHash))
      throw new Error('服务端未返回有效草稿摘要')
    draftSchemaHash.value = nextServerHash
    process.value = { ...process.value, ...data }
    serverValidation.value = data.validation || serverValidation.value

    const currentHashInput = businessProcessHashInput(draftSchema.value)
    if (saveQueued.value || currentHashInput !== submittedHashInput) {
      saveState.value = 'idle'
      dirty.value = true
      saveQueued.value = false
      return persistSchemaInternal(draftSchema.value, { ...metadata, reason: 'queued' })
    }

    draftSchema.value = clone(data.businessProcessJson || submittedSchema)
    dirty.value = false
    saveState.value = 'saved'
    if (metadata.reason === 'manual')
      notify('success', '业务流程草稿已保存')
    return true
  }
  catch (error) {
    dirty.value = true
    if (isConflict(error)) {
      saveState.value = 'conflict'
      saveError.value = errorMessage(error, '草稿已被其他人更新')
    }
    else {
      saveState.value = 'error'
      saveError.value = errorMessage(error, '业务流程草稿保存失败')
    }
    return false
  }
}

async function handleValidate(schema) {
  if (activeSavePromise)
    await activeSavePromise
  const currentSchema = clone(draftSchema.value || schema)
  if (dirty.value) {
    const saved = await persistSchema(currentSchema, { reason: 'validate' })
    if (!saved)
      return
  }
  try {
    const response = await validateBusinessProcess(processId.value)
    serverValidation.value = response.data || null
    if (serverValidation.value?.valid)
      notify('success', '流程检查通过，可进入应用发布检查')
    else
      notify('warning', `流程检查发现 ${serverValidation.value?.errorCount || 0} 项错误`)
  }
  catch (error) {
    notify('error', errorMessage(error, '业务流程检查失败'))
  }
}

function returnToApplication() {
  const returnTo = String(route.query.returnTo || '')
  if (isLocalPath(returnTo)) {
    router.push(resolveReturnTarget(returnTo))
    return
  }
  const applicationCode = String(route.query.applicationCode || '')
  if (applicationCode) {
    router.push({
      name: 'BusinessApplicationWorkspace',
      params: { applicationCode },
      query: { section: 'automation' },
    })
    return
  }
  router.push('/app-center')
}

function resolveReturnTarget(returnTo) {
  if (route.query.from !== 'button')
    return returnTo
  const target = new URL(returnTo, window.location.origin)
  target.searchParams.set('processRefresh', processId.value)
  return `${target.pathname}${target.search}${target.hash}`
}

function confirmLeave() {
  return new Promise((resolve) => {
    if (!window.$dialog) {
      resolve(false)
      return
    }
    window.$dialog.warning({
      title: '未保存变更',
      content: '当前业务流程有未保存的修改，确认离开吗？',
      positiveText: '离开',
      negativeText: '取消',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
    })
  })
}

function settledData(result, fallback) {
  return result.status === 'fulfilled' ? (result.value?.data ?? fallback) : fallback
}

function normalizeIds(value) {
  if (Array.isArray(value))
    return value.map(normalizeIds)
  if (!value || typeof value !== 'object')
    return value
  const result = {}
  Object.entries(value).forEach(([key, item]) => {
    if ((key === 'id' || key.endsWith('Id')) && item != null && ['number', 'string'].includes(typeof item)) {
      result[key] = String(item)
      return
    }
    if (key.endsWith('Ids') && Array.isArray(item)) {
      result[key] = item.map(id => stringValue(id))
      return
    }
    result[key] = normalizeIds(item)
  })
  return result
}

function clone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}

function stringValue(value) {
  return value == null ? '' : String(value)
}

function isServerHash(value) {
  return /^[a-f0-9]{64}$/.test(String(value || ''))
}

function isConflict(error) {
  return Number(error?.response?.status || error?.status) === 409
}

function isLocalPath(value) {
  return value.startsWith('/') && !value.startsWith('//')
}

function errorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

function notify(type, message) {
  window.$message?.[type]?.(message)
}
</script>

<template>
  <div class="process-designer-page">
    <header class="process-page-header">
      <button
        type="button"
        class="back-button"
        data-process-action="back"
        @click="returnToApplication"
      >
        <span aria-hidden="true">←</span>
        返回业务流程
      </button>
      <div v-if="process" class="process-page-identity">
        <strong>{{ pageTitle }}</strong>
        <code>{{ process.processCode }}</code>
        <span>{{ subjectObject?.objectName || process.subjectObjectCode }}</span>
      </div>
      <div class="process-page-boundary">
        应用画布负责业务编排，审批内部配置仍由 Flowable 管理
      </div>
    </header>

    <main class="process-page-main">
      <div v-if="loading" class="page-loading">
        <n-spin size="medium" />
        <span>正在加载业务流程草稿…</span>
      </div>

      <n-result
        v-else-if="loadError || !draftSchema"
        status="error"
        title="业务流程无法打开"
        :description="loadError || '草稿不存在或无权访问'"
      >
        <template #footer>
          <n-button @click="returnToApplication">
            返回应用工作台
          </n-button>
        </template>
      </n-result>

      <BusinessProcessDesigner
        v-else
        :schema="draftSchema"
        :process-name="process.processName"
        :save-state="saveState"
        :save-error="saveError"
        :server-validation="serverValidation"
        :object-name="subjectObject?.objectName || process.subjectObjectCode"
        :objects="applicationObjects"
        :fields="fields"
        :flow-models="flowModels"
        :form-assets="formAssets"
        :business-actions="businessActions"
        :message-templates="messageTemplates"
        :capabilities="capabilities"
        :sub-processes="subProcesses"
        :service-actors="serviceActors"
        @update:schema="handleSchemaUpdate"
        @save="handleSave"
        @validate="handleValidate"
        @dirty-change="handleDirtyChange"
        @refresh-flow-model="refreshFlowCatalog"
        @reload="loadDesigner"
      />
    </main>
  </div>
</template>

<style scoped>
.process-designer-page {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  overflow: hidden;
  background: var(--body-color, #f5f6f8);
}

.process-page-header {
  display: grid;
  min-height: 58px;
  flex: 0 0 auto;
  align-items: center;
  gap: 16px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--border-color, #e5e7eb);
  background: var(--card-color, #fff);
  grid-template-columns: auto minmax(0, 1fr) auto;
}

.back-button {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  gap: 7px;
  padding: 0 10px;
  border: 1px solid var(--border-color, #d1d5db);
  border-radius: 6px;
  color: var(--text-color-2, #334155);
  background: var(--card-color, #fff);
  cursor: pointer;
  font-size: 13px;
}

.back-button:hover {
  border-color: var(--primary-color, #2563eb);
  color: var(--primary-color, #2563eb);
}

.process-page-identity {
  display: grid;
  min-width: 0;
  align-items: baseline;
  column-gap: 8px;
  grid-template-columns: auto minmax(0, 1fr);
}

.process-page-identity strong {
  overflow: hidden;
  color: var(--text-color-1, #0f172a);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.process-page-identity code,
.process-page-identity span,
.process-page-boundary {
  color: var(--text-color-3, #64748b);
  font-size: 11px;
}

.process-page-identity span {
  grid-column: 1 / -1;
}

.process-page-boundary {
  max-width: 360px;
  padding-left: 14px;
  border-left: 1px solid var(--border-color, #e5e7eb);
  line-height: 1.5;
  text-align: right;
}

.process-page-main {
  min-height: 0;
  flex: 1;
  padding: 10px;
}

.process-page-main > :deep(.business-process-designer) {
  height: calc(100vh - 78px);
  min-height: 620px;
}

.page-loading {
  display: flex;
  min-height: calc(100vh - 90px);
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  color: var(--text-color-3, #64748b);
  font-size: 13px;
}

@media (max-width: 900px) {
  .process-page-header {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .process-page-boundary {
    display: none;
  }
}
</style>
