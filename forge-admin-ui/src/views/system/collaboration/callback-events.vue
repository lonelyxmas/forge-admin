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
  </div>
</template>

<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { fetchConnectionOptions } from '@/api/collaboration'
import { AiCrudPage } from '@/components/ai-form'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'

defineOptions({ name: 'CollaborationCallbackEvents' })

const crudRef = ref(null)
const connectionOptions = ref([])

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
])

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
