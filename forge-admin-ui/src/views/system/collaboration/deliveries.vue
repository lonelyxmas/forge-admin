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

    <CollaborationDetailModal
      v-model:show="detailVisible"
      title="投递记录详情"
      :width="720"
      :fields="detailFields"
      :data="detailRow"
    />
  </div>
</template>

<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { fetchConnectionOptions, retryDelivery } from '@/api/collaboration'
import { AiCrudPage } from '@/components/ai-form'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'
import CollaborationDetailModal from './CollaborationDetailModal.vue'

defineOptions({ name: 'CollaborationDeliveries' })

const crudRef = ref(null)
const connectionOptions = ref([])
const detailVisible = ref(false)
const detailRow = ref(null)

const { dict } = useDict('sys_collab_delivery_status', 'sys_collab_platform')

const deliveryStatusOptions = computed(() => dict.value.sys_collab_delivery_status || [])
const platformOptions = computed(() => dict.value.sys_collab_platform || [])

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
    field: 'platform',
    label: '平台',
    type: 'select',
    props: { placeholder: '请选择平台', options: platformOptions.value, clearable: true },
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
  { prop: 'userId', label: '用户ID', width: 90 },
  { prop: 'userAccount', label: '账号', width: 120, showOverflowTooltip: true, render: row => row.userAccount || '-' },
  { prop: 'userName', label: '姓名', width: 100, showOverflowTooltip: true, render: row => row.userName || '-' },
  {
    prop: 'connectionId',
    label: '连接',
    width: 180,
    showOverflowTooltip: true,
    render: row => connectionLabel(row.connectionId),
  },
  {
    prop: 'platform',
    label: '平台',
    width: 110,
    render: row => (row.platform ? h(DictTag, { dictType: 'sys_collab_platform', value: row.platform, size: 'small' }) : '-'),
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
    width: 140,
    fixed: 'right',
    actions: [
      {
        label: '查看详情',
        key: 'detail',
        type: 'primary',
        onClick: handleViewDetail,
      },
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

const detailFields = computed(() => [
  { key: 'id', label: '接收人记录ID' },
  { key: 'messageId', label: '消息ID' },
  { key: 'userId', label: '用户ID' },
  { key: 'userAccount', label: '登录账号' },
  { key: 'userName', label: '姓名' },
  { key: 'connectionId', label: '连接', render: row => connectionLabel(row.connectionId) },
  { key: 'platform', label: '平台', dictType: 'sys_collab_platform' },
  { key: 'title', label: '消息标题' },
  { key: 'deliveryStatus', label: '投递状态', dictType: 'sys_collab_delivery_status' },
  { key: 'deliveryAttempts', label: '投递次数' },
  { key: 'externalId', label: '外部消息ID' },
  { key: 'lastErrorCode', label: '最近错误码' },
  { key: 'lastAttemptTime', label: '最近投递时间' },
  { key: 'nextRetryTime', label: '下次重试时间' },
  { key: 'createTime', label: '创建时间' },
  { key: 'messageContent', label: '推送消息内容', pre: true },
])

function handleViewDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

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
