<template>
  <AiLayoutPage :title="title" :subtitle="subtitle" :show-back="true">
    <view v-if="loading" class="runtime-state">
      <AiListSkeleton :rows="4" />
    </view>
    <view v-else-if="errorMessage" class="runtime-state">
      <AiResult type="error" :title="errorMessage" description="请检查应用发布状态或联系管理员" />
      <AiButton block variant="secondary" @click="loadRuntime">重新加载</AiButton>
    </view>

    <template v-else-if="mode === 'list'">
      <view class="runtime-list-head">
        <view class="runtime-toolbar__copy">
          <text class="runtime-toolbar__title">{{ config.tableComment || config.objectName || title }}</text>
          <text class="runtime-toolbar__desc">共 {{ total }} 条记录，按卡片浏览和办理</text>
        </view>
        <view class="runtime-list-head__actions">
          <AiButton v-if="searchFields.length" size="sm" variant="secondary" @click="toggleSearch">
            {{ searchExpanded ? '收起筛选' : `筛选${activeSearchCount ? `(${activeSearchCount})` : ''}` }}
          </AiButton>
          <AiButton size="sm" @click="openCreate">新增</AiButton>
        </view>
      </view>

      <view v-if="searchFields.length" class="runtime-filter-shell">
        <view v-if="!searchExpanded" class="runtime-filter-summary" @click="toggleSearch">
          <view>
            <text class="runtime-filter-summary__title">筛选条件</text>
            <text class="runtime-filter-summary__desc">{{ searchSummary }}</text>
          </view>
          <text class="runtime-filter-summary__arrow">展开</text>
        </view>
        <view v-else class="runtime-search-card">
          <LowcodeForm :fields="searchFields" :data="searchData" :dict-options="dictOptions" @update:data="loadListDebounced" />
          <view class="runtime-search-actions">
            <AiButton size="sm" variant="secondary" @click="resetSearch">重置</AiButton>
            <AiButton size="sm" @click="loadList">查询</AiButton>
          </view>
        </view>
      </view>

      <view v-if="records.length" class="runtime-record-list">
        <view v-for="row in records" :key="String(row[config.rowKey || 'id'])" class="runtime-record-card" @click="openDetail(row)">
          <view class="runtime-record-card__head">
            <text class="runtime-record-card__title">{{ rowTitle(row) }}</text>
            <text class="runtime-record-card__status">{{ displayStatus(row) }}</text>
          </view>
          <view class="runtime-record-card__grid">
            <view v-for="column in visibleColumns" :key="column.prop || column.field" class="runtime-record-card__item">
              <text class="runtime-record-card__label">{{ column.label || column.title || column.prop }}</text>
              <text class="runtime-record-card__value">{{ formatValue(row[column.prop || column.field], column) }}</text>
            </view>
          </view>
          <view v-if="mainActions(row).length" class="runtime-actions" @click.stop>
            <AiButton v-for="action in mainActions(row)" :key="action.actionCode || action.key" size="sm" variant="secondary" @click="runAction(action, row)">
              {{ action.label || action.actionName || action.actionCode }}
            </AiButton>
          </view>
        </view>
      </view>
      <AiEmpty v-else title="暂无记录" description="点击右上角新增一条记录" />
      <view v-if="total > pageSize" class="runtime-pagination">
        <AiButton size="sm" variant="secondary" :disabled="page <= 1" @click="changePage(-1)">上一页</AiButton>
        <text>第 {{ page }} / {{ pageCount }} 页</text>
        <AiButton size="sm" variant="secondary" :disabled="page >= pageCount" @click="changePage(1)">下一页</AiButton>
      </view>
    </template>

    <template v-else>
      <view class="runtime-form-card">
        <view class="runtime-form-card__head">
          <text class="runtime-form-card__title">{{ mode === 'create' ? '新建' : mode === 'detail' ? '详情' : '编辑' }}{{ title }}</text>
          <text class="runtime-form-card__desc">{{ mode === 'detail' ? '只读查看已保存信息' : '填写后保存，字段会按配置自动联动' }}</text>
        </view>
        <LowcodeForm
          ref="mainFormRef"
          :fields="mainFields"
          :data="mainData"
          :dict-options="dictOptions"
          :readonly="mode === 'detail'"
          :context="runtimeContext"
          @field-event="handleMainFieldEvent"
        />
      </view>

      <view v-for="child in visibleChildren" :key="child.key" class="runtime-child-card">
        <view class="runtime-child-card__head">
          <view>
            <text class="runtime-child-card__title">{{ childTitle(child) }}</text>
            <text class="runtime-child-card__count">{{ childSubtitle(child) || `${childRows(child).length} 条` }}</text>
          </view>
          <AiButton v-if="mode !== 'detail' && child.inlineCreateEnabled !== false && child.readonly !== true" size="sm" variant="secondary" @click="addChildRow(child)">添加</AiButton>
        </view>
        <view v-if="childRows(child).length" class="runtime-child-list">
          <view v-for="(row, rowIndex) in childRows(child)" :key="String(row.id || rowIndex)" class="runtime-child-row">
            <view class="runtime-child-row__head">
              <text class="runtime-child-row__title">第 {{ rowIndex + 1 }} 条</text>
              <view v-if="mode !== 'detail' && child.readonly !== true" class="runtime-child-row__tools">
                <AiButton size="sm" variant="danger" @click="removeChildRow(child, rowIndex)">删除</AiButton>
              </view>
            </view>
            <view class="runtime-child-row__body">
              <LowcodeForm
                :ref="instance => setChildFormRef(child, row, rowIndex, instance)"
                :fields="child.fields"
                :data="row"
                :dict-options="dictOptions"
                :current-children="childData"
                :readonly="mode === 'detail' || child.readonly === true"
                :context="runtimeContext"
                @field-event="payload => handleChildFieldEvent(child, row, payload)"
              />
            </view>
            <view v-if="childActions(child, row).length" class="runtime-actions runtime-actions--child">
              <AiButton v-for="action in childActions(child, row)" :key="action.actionCode || action.key" size="sm" variant="secondary" @click="runAction(action, row, child)">
                {{ action.label || action.actionName || action.actionCode }}
              </AiButton>
            </view>
          </view>
        </view>
        <view v-else class="runtime-child-empty">暂无明细</view>
      </view>

      <view v-if="mode !== 'detail'" class="runtime-footer-actions">
        <AiButton variant="secondary" @click="goList">取消</AiButton>
        <AiButton :loading="saving" @click="save">保存</AiButton>
      </view>
      <view v-else class="runtime-footer-actions">
        <AiButton variant="secondary" @click="goList">返回列表</AiButton>
        <AiButton v-if="canEdit" @click="openEdit">编辑</AiButton>
      </view>
    </template>
  </AiLayoutPage>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import AiButton from '@/components/AiButton.vue'
import AiEmpty from '@/components/AiEmpty.vue'
import AiLayoutPage from '@/components/AiLayoutPage.vue'
import AiListSkeleton from '@/components/AiListSkeleton.vue'
import AiResult from '@/components/AiResult.vue'
import LowcodeForm from '@/components/lowcode/LowcodeForm.vue'
import api from '@/api'
import { useAuthStore } from '@/store'
import { ensureLogin } from '@/utils/auth-guard'
import { toast } from '@/utils/notify'
import {
  actionInputSchema,
  actionVisible,
  applyEventMappings,
  buildActionPayload,
  buildDefaultData,
  buildEventClearPatch,
  buildEventParams,
  ensureChildRows,
  normalizeActions,
  normalizeChildrenConfig,
  normalizeDictOptions,
  normalizeMainFields,
  normalizeField,
  normalizeScanContext,
  parseJson,
  parseRuntimeConfig,
  resolveActionDefinition,
  resolveChildRows,
  resolveChildTitle,
  resolveChildSubtitle,
  safeEventRules,
  shouldSkipFieldEvent,
  syncChildRowAliases,
} from '@/utils/lowcode-runtime'

const authStore = useAuthStore()
const routeQuery = reactive({})
const configKey = ref('')
const title = ref('低代码应用')
const subtitle = ref('H5 运行页')
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const config = ref({})
const mode = ref('list')
const currentId = ref('')
const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const searchExpanded = ref(false)
const searchData = reactive({})
const mainData = reactive({})
const childData = reactive({})
const dictOptions = reactive({})
const mainFormRef = ref(null)
const childFormRefs = new Map()
const childRowScopes = new WeakMap()
const fieldEventTimers = new Map()
const fieldEventControllers = new Map()
const fieldEventSequences = new Map()
let loadTimer
let childRowScopeSequence = 0

const mainFields = computed(() => normalizeMainFields(config.value))
const searchFields = computed(() => (Array.isArray(config.value.searchSchema) ? config.value.searchSchema : []).map(normalizeField).filter(field => field.field))
const visibleColumns = computed(() => (Array.isArray(config.value.columnsSchema) ? config.value.columnsSchema : []).filter(column => column?.prop || column?.field))
const visibleChildren = computed(() => normalizeChildrenConfig(config.value).filter(childVisibleInCurrentMode))
const runtimeContext = computed(() => ({
  routeQuery,
  user: authStore.userInfo || {},
  currentUser: authStore.userInfo || {},
}))
const canEdit = computed(() => mode.value === 'detail' && String(mainData.status || '').toUpperCase() === 'DRAFT')
const pageCount = computed(() => Math.max(1, Math.ceil(Number(total.value || 0) / pageSize)))
const activeSearchCount = computed(() => Object.values(searchData).filter(value => value !== undefined && value !== null && value !== '' && !(Array.isArray(value) && !value.length)).length)
const searchSummary = computed(() => activeSearchCount.value ? `已设置 ${activeSearchCount.value} 个条件` : '默认收起，点击后输入条件')

onLoad(async query => {
  Object.assign(routeQuery, query || {})
  configKey.value = String(query?.configKey || query?.config || resolveConfigKey(query?.path) || '').trim()
  title.value = String(query?.title || '低代码应用')
  subtitle.value = String(query?.subtitle || 'H5 运行页')
  const requestedMode = String(query?.mode || '').toLowerCase()
  mode.value = requestedMode === 'create' ? 'create' : requestedMode === 'detail' ? 'detail' : requestedMode === 'edit' ? 'edit' : 'list'
  currentId.value = String(query?.recordId || query?.id || '')
  const ok = await ensureLogin({ redirect: `/pages/lowcode-runtime?${queryString(query)}` })
  if (ok) await loadRuntime()
})

onUnload(() => {
  clearTimeout(loadTimer)
  cancelFieldEvents()
  childFormRefs.clear()
})

async function loadRuntime() {
  if (!configKey.value) {
    errorMessage.value = '缺少低代码配置标识'
    loading.value = false
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await api.getLowcodeRenderConfig(configKey.value)
    config.value = parseRuntimeConfig(response?.data || {})
    title.value = String(routeQuery.title || config.value.appName || config.value.objectName || title.value)
    await loadDictionaries()
    if (mode.value === 'list') await loadList()
    else if (currentId.value) await loadDetail(currentId.value)
    else initializeForm()
  }
  catch (error) {
    errorMessage.value = error?.message || '低代码运行配置加载失败'
  }
  finally {
    loading.value = false
  }
}

async function loadDictionaries() {
  const types = new Set()
  const collect = fields => fields.forEach(field => {
    if (field.type === 'dictSelect' && (field.dictType || field.props?.dictType)) types.add(field.dictType || field.props.dictType)
  })
  collect(mainFields.value)
  normalizeChildrenConfig(config.value).forEach(child => collect(child.fields))
  await Promise.all([...types].map(async type => {
    try { dictOptions[type] = normalizeDictOptions((await api.getDictOptions(type))?.data) }
    catch { dictOptions[type] = [] }
  }))
}

async function loadList() {
  const response = await api.getLowcodePage(configKey.value, { pageNum: page.value, pageSize, ...searchData })
  const data = response?.data || {}
  records.value = data.records || data.list || data.rows || []
  total.value = Number(data.total || records.value.length || 0)
}

function loadListDebounced() {
  clearTimeout(loadTimer)
  loadTimer = setTimeout(() => { page.value = 1; loadList().catch(handleError) }, 350)
}

function resetSearch() {
  Object.keys(searchData).forEach(key => delete searchData[key])
  page.value = 1
  loadList().catch(handleError)
}

async function loadDetail(id) {
  const response = await api.getLowcodeDetail(configKey.value, id)
  const data = response?.data || {}
  Object.keys(mainData).forEach(key => delete mainData[key])
  Object.assign(mainData, data.main || data)
  Object.keys(childData).forEach(key => delete childData[key])
  Object.assign(childData, data.children || {})
  normalizeChildrenConfig(config.value).forEach(child => { syncChildRowAliases(child, childData) })
  await dispatchFormLoad(mainData, mainFields.value)
}

function initializeForm() {
  Object.keys(mainData).forEach(key => delete mainData[key])
  Object.assign(mainData, buildDefaultData(mainFields.value))
  Object.keys(childData).forEach(key => delete childData[key])
  normalizeChildrenConfig(config.value).forEach(child => { ensureChildRows(child, childData) })
  dispatchFormLoad(mainData, mainFields.value).catch(handleError)
}

function childRows(child) { return resolveChildRows(child, childData) }
function addChildRow(child) { ensureChildRows(child, childData).push(buildDefaultData(child.fields)) }
function removeChildRow(child, index) { childRows(child).splice(index, 1) }
function childTitle(child) { return resolveChildTitle(child) }

function setChildFormRef(child, row, rowIndex, instance) {
  const key = childFormRefKey(child, row, rowIndex)
  if (instance) childFormRefs.set(key, instance)
  else childFormRefs.delete(key)
}

function childFormRefKey(child, row, rowIndex) {
  return `${child.modelCode}:${row?.id || childRowScope(row, rowIndex)}`
}

function childRowScope(row, fallback = 0) {
  if (!row || typeof row !== 'object') return String(fallback)
  if (!childRowScopes.has(row)) childRowScopes.set(row, `new_${++childRowScopeSequence}`)
  return childRowScopes.get(row)
}

function openCreate() { mode.value = 'create'; currentId.value = ''; initializeForm() }
function openDetail(row) { currentId.value = String(row[config.value.rowKey || 'id']); mode.value = 'detail'; loadDetail(currentId.value).catch(handleError) }
function openEdit() { mode.value = 'edit' }
function goList() { mode.value = 'list'; currentId.value = ''; loadList().catch(handleError) }
function changePage(delta) { page.value = Math.min(pageCount.value, Math.max(1, page.value + delta)); loadList().catch(handleError) }
function toggleSearch() { searchExpanded.value = !searchExpanded.value }

async function save() {
  const mainValid = mainFormRef.value?.validate?.() === true
  const childrenValid = [...childFormRefs.values()].every(form => form?.validate?.() !== false)
  if (!mainValid || !childrenValid) return toast('请完善必填字段', { type: 'warning' })
  saving.value = true
  try {
    const payload = { main: { ...mainData }, children: buildChildrenPayload() }
    if (mode.value === 'create') await api.createLowcodeRecord(configKey.value, payload)
    else await api.updateLowcodeRecord(configKey.value, payload)
    toast('保存成功', { type: 'success' }); goList()
  }
  catch (error) { handleError(error) }
  finally { saving.value = false }
}

function childVisibleInCurrentMode(child = {}) {
  if (mode.value === 'detail')
    return child.showInDetail !== false
  if (mode.value === 'edit')
    return child.showInEdit !== false
  return child.showInCreate !== false
}

function buildChildrenPayload() {
  return Object.fromEntries(normalizeChildrenConfig(config.value)
    .map(child => [child.modelCode, childRows(child)])
    .filter(([key]) => key))
}

async function handleMainFieldEvent({ trigger, field, data, scan }) { await dispatchFieldEvent(trigger, field, data, mainFields.value, config.value.options?.formDesignerSchema?.settings?.governance?.fieldEvents, scan) }
async function handleChildFieldEvent(child, row, { trigger, field, data, scan }) { await dispatchFieldEvent(trigger, field, data, child.fields, child.fieldEvents, scan, child) }

async function dispatchFieldEvent(trigger, field, data, fields, rules, scan, child) {
  const normalizedTrigger = String(trigger || '').toUpperCase()
  const normalizedRules = safeEventRules(rules, fields).filter((rule) => {
    const ruleTrigger = String(rule.trigger || '').toUpperCase()
    return ruleTrigger === normalizedTrigger && (ruleTrigger === 'FORM_LOAD' || rule.sourceField === field.field)
  })
  const scope = child ? `${child.modelCode}:${childRowScope(data)}` : 'main'
  await Promise.all(normalizedRules.map(rule => scheduleFieldEvent(rule, data, scan, scope, normalizedTrigger)))
}

function scheduleFieldEvent(rule, data, scan, scope, trigger) {
  const key = `${scope}:${rule.id || rule.sourceKey}:${rule.sourceField || 'form'}`
  clearFieldEventTimer(key)
  if (shouldSkipFieldEvent(rule, data)) {
    cancelFieldEventRequest(key)
    Object.assign(data, buildEventClearPatch(rule))
    return Promise.resolve({ status: 'skipped' })
  }
  const delay = trigger === 'CHANGE' ? Math.max(0, Math.min(5000, Number(rule.debounceMs) || 0)) : 0
  if (!delay) return executeFieldEvent(rule, data, scan, key)
  cancelFieldEventRequest(key)
  return new Promise((resolve) => {
    const timer = setTimeout(async () => {
      fieldEventTimers.delete(key)
      resolve(await executeFieldEvent(rule, data, scan, key))
    }, delay)
    fieldEventTimers.set(key, { timer, resolve })
  })
}

async function executeFieldEvent(rule, data, scan, key) {
  cancelFieldEventRequest(key)
  const sequence = (fieldEventSequences.get(key) || 0) + 1
  fieldEventSequences.set(key, sequence)
  if (shouldSkipFieldEvent(rule, data)) {
    Object.assign(data, buildEventClearPatch(rule))
    return { status: 'skipped' }
  }
  if (rule.clearTargetsOnTrigger === true)
    Object.assign(data, buildEventClearPatch(rule))

  const controller = typeof AbortController === 'undefined' ? null : new AbortController()
  if (controller) fieldEventControllers.set(key, controller)
  try {
    const normalizedScan = normalizeScanContext(scan)
    const context = normalizedScan ? { ...runtimeContext.value, scan: normalizedScan } : runtimeContext.value
    const params = buildEventParams(rule, data, context, routeQuery)
    const response = await api.executeLowcodeQuerySource(
      { sourceType: rule.sourceType, sourceKey: rule.sourceKey, params },
      controller ? { signal: controller.signal } : {},
    )
    if (fieldEventSequences.get(key) !== sequence) return { status: 'stale' }
    const mapped = applyEventMappings(rule, unwrapQueryResult(response), data)
    if (mapped.found || Object.keys(mapped.patch).length) Object.assign(data, mapped.patch)
    if (!mapped.found && rule.notFoundMessage && String(rule.errorMode || 'MESSAGE').toUpperCase() !== 'SILENT')
      toast(rule.notFoundMessage, { type: 'warning' })
    return { status: mapped.found ? 'success' : 'not_found' }
  }
  catch (error) {
    if (fieldEventSequences.get(key) !== sequence || controller?.signal.aborted) return { status: 'cancelled' }
    if (rule.errorMessage && String(rule.errorMode || 'MESSAGE').toUpperCase() !== 'SILENT')
      toast(rule.errorMessage, { type: 'error' })
    console.warn('[lowcode h5] field event failed', error)
    return { status: 'error' }
  }
  finally {
    if (fieldEventControllers.get(key) === controller) fieldEventControllers.delete(key)
  }
}

function clearFieldEventTimer(key) {
  const pending = fieldEventTimers.get(key)
  if (!pending) return
  clearTimeout(pending.timer)
  fieldEventTimers.delete(key)
  pending.resolve({ status: 'cancelled' })
}

function cancelFieldEventRequest(key) {
  const controller = fieldEventControllers.get(key)
  if (controller && !controller.signal.aborted) controller.abort()
  fieldEventControllers.delete(key)
  fieldEventSequences.set(key, (fieldEventSequences.get(key) || 0) + 1)
}

function cancelFieldEvents() {
  for (const key of [...fieldEventTimers.keys()]) clearFieldEventTimer(key)
  for (const key of [...fieldEventControllers.keys()]) cancelFieldEventRequest(key)
}

async function dispatchFormLoad(data, fields) {
  const rules = config.value.options?.formDesignerSchema?.settings?.governance?.fieldEvents || []
  await dispatchFieldEvent('FORM_LOAD', { field: '' }, data, fields, rules)
}

async function runAction(action, row, child) {
  const resolved = resolveActionDefinition(config.value, action)
  if (!actionVisible(resolved, row)) return
  const confirmed = await confirmAction(resolved)
  if (!confirmed) return
  const inputs = actionInputSchema(resolved)
  const formData = {}
  for (const input of inputs) {
    const value = await promptActionInput(input)
    if (value === null) return
    formData[input.name] = input.type === 'INTEGER' || input.type === 'NUMBER' ? Number(value) : value
  }
  try {
    const objectCode = resolveActionObjectCode(child)
    const parentId = child ? (mainData.id || mainData[config.value.rowKey || 'id']) : undefined
    const response = await api.executeBusinessAction(buildActionPayload({
      action: resolved,
      config: config.value,
      objectCode,
      recordId: row?.id || row?.[config.value.rowKey || 'id'] || mainData.id,
      parentRecordId: parentId,
      childRecordId: child ? (row?.id || '') : undefined,
      relationKey: child?.relationKey,
      formData,
      routeQuery,
    }))
    const result = response?.data || {}
    if (String(result.executeStatus || '').toUpperCase() === 'FAILED') throw new Error(result.message || resolved.failureMessage || '动作执行失败')
    toast(resolved.successMessage || result.message || '操作成功', { type: 'success' })
    if (child && parentId) await loadDetail(parentId)
    else if (mode.value === 'list') await loadList()
    else if (mainData.id) await loadDetail(mainData.id)
  }
  catch (error) { handleError(error) }
}

function mainActions(row) { return normalizeActions(config.value).filter(action => !action.relationKey && actionVisible(action, row)) }
function childActions(child, row) { return child.rowActions.map(action => resolveActionDefinition(config.value, action)).filter(action => actionVisible(action, row)) }
function childSubtitle(child) { return resolveChildSubtitle(child) }
function resolveActionObjectCode(child) {
  return child?.businessObjectCode
    || config.value?.objectCode
    || child?.objectCode
    || child?.targetObjectCode
}
function rowTitle(row) { return row.presaleNo || row[config.value.rowKey || 'id'] || config.value.objectName || '记录' }
function displayStatus(row) { return formatValue(row.status, { dictType: 'ps_presale_status', prop: 'status' }) }
function formatValue(value, column = {}) { if (value === undefined || value === null || value === '') return '-'; if (column.dictType) return dictOptions[column.dictType]?.find(item => String(item.value) === String(value))?.label || value; return String(value) }
function handleError(error) { toast(error?.message || '操作失败，请稍后重试', { type: 'error' }) }
function confirmAction(action) { return new Promise(resolve => uni.showModal({ title: action.label || action.actionName || '确认操作', content: action.confirmText || `确认执行“${action.label || action.actionName || '操作'}”吗？`, success: result => resolve(result.confirm) })) }
function promptActionInput(input) { return new Promise(resolve => uni.showModal({ title: input.label || input.name, editable: true, placeholderText: input.placeholder || `请输入${input.label || input.name}`, success: result => resolve(result.confirm ? result.content : null) })) }
function unwrapQueryResult(response) { const value = response?.data; return value?.data !== undefined ? value.data : value }
function resolveConfigKey(path = '') { const match = String(path || '').match(/(?:crud-page|crud)\/([^/?]+)/); return match?.[1] || '' }
function queryString(query = {}) { return Object.entries(query).map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join('&') }
</script>

<style lang="scss" scoped>
.runtime-state { padding: 36rpx 0; }
.runtime-list-head { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin-bottom: 18rpx; padding: 22rpx 24rpx; border: 1rpx solid #e7edf5; border-radius: 18rpx; background: #fff; box-shadow: 0 10rpx 28rpx rgba(15, 23, 42, .04); }
.runtime-list-head__actions { display: flex; flex: 0 0 auto; align-items: center; gap: 10rpx; }
.runtime-toolbar__copy { display: flex; flex-direction: column; gap: 6rpx; }
.runtime-toolbar__title, .runtime-form-card__title, .runtime-child-card__title { color: var(--text-strong); font-size: 30rpx; font-weight: 850; }
.runtime-toolbar__desc, .runtime-form-card__desc, .runtime-child-card__count { color: #94a3b8; font-size: 22rpx; }
.runtime-filter-shell { margin-bottom: 18rpx; }
.runtime-filter-summary { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; padding: 18rpx 22rpx; border: 1rpx solid #e7edf5; border-radius: 16rpx; color: #334155; background: #fff; }
.runtime-filter-summary view { display: flex; min-width: 0; flex-direction: column; gap: 5rpx; }
.runtime-filter-summary__title { color: #475569; font-size: 24rpx; font-weight: 750; }
.runtime-filter-summary__desc { overflow: hidden; color: #94a3b8; font-size: 22rpx; text-overflow: ellipsis; white-space: nowrap; }
.runtime-filter-summary__arrow { flex: 0 0 auto; color: #2563eb; font-size: 22rpx; font-weight: 700; }
.runtime-search-card, .runtime-form-card, .runtime-child-card { margin-bottom: 24rpx; padding: 26rpx; border: 1rpx solid #e7edf5; border-radius: 18rpx; background: #fff; box-shadow: 0 10rpx 28rpx rgba(15, 23, 42, .04); }
.runtime-search-actions, .runtime-actions, .runtime-footer-actions, .runtime-child-row__tools { display: flex; align-items: center; justify-content: flex-end; gap: 12rpx; }
.runtime-search-actions { padding-top: 6rpx; }
.runtime-record-list, .runtime-child-list { display: flex; flex-direction: column; gap: 18rpx; }
.runtime-record-card { padding: 24rpx; border: 1rpx solid #e7edf5; border-radius: 18rpx; background: #fff; box-shadow: 0 10rpx 28rpx rgba(15, 23, 42, .04); }
.runtime-record-card__head, .runtime-child-card__head, .runtime-form-card__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18rpx; margin-bottom: 18rpx; }
.runtime-record-card__status { padding: 5rpx 12rpx; border-radius: 999rpx; color: #2563eb; font-size: 21rpx; background: #eff6ff; }
.runtime-record-card__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16rpx 20rpx; }
.runtime-record-card__item { min-width: 0; display: flex; flex-direction: column; gap: 5rpx; }
.runtime-record-card__label { color: #94a3b8; font-size: 21rpx; }
.runtime-record-card__value { overflow: hidden; color: #334155; font-size: 24rpx; text-overflow: ellipsis; white-space: nowrap; }
.runtime-actions { margin-top: 20rpx; }
.runtime-actions--child { justify-content: flex-start; margin-top: 16rpx; }
.runtime-pagination { display: flex; align-items: center; justify-content: center; gap: 18rpx; padding: 28rpx 0; color: #64748b; font-size: 23rpx; }
.runtime-form-card__head { flex-direction: column; gap: 6rpx; }
.runtime-child-card__head { align-items: center; }
.runtime-child-card__head > view { display: flex; align-items: baseline; gap: 12rpx; }
.runtime-child-row { padding: 20rpx; border: 1rpx solid #eef2f7; border-radius: 16rpx; background: #fbfdff; }
.runtime-child-row__head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin-bottom: 14rpx; }
.runtime-child-row__title { color: #334155; font-size: 24rpx; font-weight: 700; }
.runtime-child-row__tools { display: flex; align-items: center; justify-content: flex-end; gap: 12rpx; }
.runtime-child-row__body { padding: 4rpx 0 2rpx; }
.runtime-child-empty { padding: 30rpx 0; color: #94a3b8; font-size: 24rpx; text-align: center; }
.runtime-footer-actions { position: sticky; bottom: 0; z-index: 2; justify-content: stretch; padding: 18rpx 0 calc(18rpx + env(safe-area-inset-bottom)); background: rgba(248, 250, 252, .95); }
.runtime-footer-actions > * { flex: 1; }
</style>
