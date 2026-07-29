<template>
  <div class="collaboration-deliveries-page">
    <AiCrudPage
      ref="crudRef"
      api="/system/collaboration/deliveries"
      :api-config="{ list: 'get@/system/collaboration/deliveries/page' }"
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
import { fetchConnectionOptions, retryDelivery } from '@/api/collaboration'
import { AiCrudPage } from '@/components/ai-form'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'

defineOptions({ name: 'CollaborationDeliveries' })

const crudRef = ref(null)
const connectionOptions = ref([])

const { dict } = useDict('sys_collab_delivery_status')

const deliveryStatusOptions = computed(() => dict.value.sys_collab_delivery_status || [])

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
    field: 'deliveryStatus',
    label: '投递状态',
    type: 'select',
    props: { placeholder: '请选择', options: deliveryStatusOptions.value, clearable: true },
  },
  {
    field: 'messageId',
    label: '消息ID',
    type: 'input',
    props: { placeholder: '请输入消息ID' },
  },
])

const tableColumns = computed(() => [
  { prop: 'id', label: 'ID', width: 90 },
  { prop: 'messageId', label: '消息ID', width: 110 },
  { prop: 'userId', label: '用户ID', width: 100 },
  {
    prop: 'connectionId',
    label: '连接',
    width: 180,
    showOverflowTooltip: true,
    render: row => connectionLabel(row.connectionId),
  },
  { prop: 'title', label: '消息标题', minWidth: 180, showOverflowTooltip: true },
  {
    prop: 'deliveryStatus',
    label: '投递状态',
    width: 100,
    render: row => h(DictTag, { dictType: 'sys_collab_delivery_status', value: row.deliveryStatus, size: 'small' }),
  },
  { prop: 'deliveryAttempts', label: '投递次数', width: 90 },
  { prop: 'externalId', label: '外部消息ID', width: 150, showOverflowTooltip: true },
  { prop: 'lastErrorCode', label: '最近错误码', width: 120, showOverflowTooltip: true },
  { prop: 'lastAttemptTime', label: '最近投递时间', width: 160 },
  { prop: 'nextRetryTime', label: '下次重试时间', width: 160 },
  { prop: 'createTime', label: '创建时间', width: 160 },
  {
    prop: 'action',
    label: '操作',
    width: 90,
    fixed: 'right',
    actions: [
      {
        label: '重试',
        key: 'retry',
        type: 'primary',
        visible: row => row.deliveryStatus === 'FAILED',
        onClick: handleRetry,
      },
    ],
  },
])

function connectionLabel(connectionId) {
  const option = connectionOptions.value.find(item => item.value === connectionId)
  return option ? option.label : String(connectionId ?? '-')
}

async function handleRetry(row) {
  try {
    const res = await retryDelivery(row.id)
    if (res.code === 200) {
      window.$message.success(res.data || '已重新投递')
      crudRef.value?.refresh()
    }
  }
  catch {
    window.$message.error('重试失败')
  }
}
</script>

<style scoped>
.collaboration-deliveries-page {
  height: 100%;
}
</style>
