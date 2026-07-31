<template>
  <div class="collaboration-callback-events-page">
    <AiCrudPage
      ref="crudRef"
      api="/system/collaboration/callback-events"
      :api-config="{ list: 'get@/system/collaboration/callback-events/page' }"
      :search-schema="searchSchema"
      :columns="tableColumns"
      row-key="id"
      :hide-add="true"
      :hide-batch-delete="true"
      :hide-selection="true"
    />

    <CollaborationDetailModal
      v-model:show="detailVisible"
      title="回调事件详情"
      :width="760"
      :column="2"
      :fields="detailFields"
      :data="detailRow"
    />
  </div>
</template>

<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { fetchConnectionOptions } from '@/api/collaboration'
import { AiCrudPage } from '@/components/ai-form'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'
import CollaborationDetailModal from './CollaborationDetailModal.vue'

defineOptions({ name: 'CollaborationCallbackEvents' })

const crudRef = ref(null)
const connectionOptions = ref([])
const detailVisible = ref(false)
const detailRow = ref(null)

const { dict } = useDict('sys_collab_callback_status')

const callbackStatusOptions = computed(() => dict.value.sys_collab_callback_status || [])

onMounted(async () => {
  connectionOptions.value = await fetchConnectionOptions()
})

const searchSchema = computed(() => [
  {
    field: 'connectionId',
    label: '连接',
    type: 'select',
    props: { placeholder: '请选择连接', options: connectionOptions.value, clearable: true },
  },
  {
    field: 'eventType',
    label: '事件类型',
    type: 'input',
    props: { placeholder: '请输入事件类型' },
  },
  {
    field: 'processStatus',
    label: '处理状态',
    type: 'select',
    props: { placeholder: '请选择', options: callbackStatusOptions.value, clearable: true },
  },
])

const tableColumns = computed(() => [
  { prop: 'id', label: 'ID', width: 90 },
  {
    prop: 'connectionId',
    label: '连接',
    width: 180,
    showOverflowTooltip: true,
    render: row => connectionLabel(row.connectionId),
  },
  { prop: 'appConfigId', label: '应用ID', width: 100 },
  { prop: 'eventId', label: '事件ID', width: 160, showOverflowTooltip: true },
  { prop: 'eventType', label: '事件类型', width: 160, showOverflowTooltip: true },
  { prop: 'eventTime', label: '事件时间', width: 160 },
  { prop: 'signatureStatus', label: '验签状态', width: 100 },
  {
    prop: 'processStatus',
    label: '处理状态',
    width: 100,
    render: row => h(DictTag, { dictType: 'sys_collab_callback_status', value: row.processStatus, size: 'small' }),
  },
  { prop: 'retryCount', label: '重试次数', width: 90 },
  { prop: 'nextRetryTime', label: '下次重试时间', width: 160 },
  { prop: 'errorCode', label: '错误码', width: 120, showOverflowTooltip: true },
  { prop: 'errorSummary', label: '错误摘要', minWidth: 180, showOverflowTooltip: true },
  {
    prop: 'action',
    label: '操作',
    width: 100,
    fixed: 'right',
    actions: [
      { label: '查看详情', key: 'detail', type: 'primary', onClick: handleViewDetail },
    ],
  },
])

const detailFields = computed(() => [
  { key: 'id', label: '事件ID' },
  { key: 'connectionId', label: '连接', render: row => connectionLabel(row.connectionId) },
  { key: 'appConfigId', label: '应用ID' },
  { key: 'eventId', label: '外部事件ID' },
  { key: 'eventType', label: '事件类型' },
  { key: 'eventTime', label: '事件时间' },
  { key: 'signatureStatus', label: '验签状态' },
  { key: 'processStatus', label: '处理状态', dictType: 'sys_collab_callback_status' },
  { key: 'retryCount', label: '重试次数' },
  { key: 'nextRetryTime', label: '下次重试时间' },
  { key: 'claimedBy', label: '处理Worker' },
  { key: 'claimTime', label: '领取时间' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '更新时间' },
  { key: 'errorCode', label: '错误码', span: 2 },
  { key: 'errorSummary', label: '错误摘要', span: 2, pre: true },
])

function handleViewDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

function connectionLabel(connectionId) {
  const option = connectionOptions.value.find(item => item.value === connectionId)
  return option ? option.label : String(connectionId ?? '-')
}
</script>

<style scoped>
.collaboration-callback-events-page {
  height: 100%;
}
</style>
