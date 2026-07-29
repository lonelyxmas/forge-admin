<template>
  <div class="collaboration-sync-page">
    <AiCrudPage
      ref="crudRef"
      api="/system/collaboration/sync-logs"
      :api-config="{ list: 'get@/system/collaboration/sync-logs/page', delete: 'delete@/system/collaboration/sync-logs/:id' }"
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

defineOptions({ name: 'CollaborationSync' })

const crudRef = ref(null)
const connectionOptions = ref([])

const { dict } = useDict('sys_collab_sync_status', 'sys_collab_sync_type', 'sys_collab_trigger_source')

const syncStatusOptions = computed(() => dict.value.sys_collab_sync_status || [])
const syncTypeOptions = computed(() => dict.value.sys_collab_sync_type || [])

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
    field: 'syncType',
    label: '同步类型',
    type: 'select',
    props: { placeholder: '请选择', options: syncTypeOptions.value, clearable: true },
  },
  {
    field: 'status',
    label: '批次状态',
    type: 'select',
    props: { placeholder: '请选择', options: syncStatusOptions.value, clearable: true },
  },
])

const tableColumns = computed(() => [
  { prop: 'id', label: '批次ID', width: 90 },
  {
    prop: 'connectionId',
    label: '连接',
    width: 180,
    showOverflowTooltip: true,
    render: row => connectionLabel(row.connectionId),
  },
  {
    prop: 'syncType',
    label: '同步类型',
    width: 100,
    render: row => h(DictTag, { dictType: 'sys_collab_sync_type', value: row.syncType, size: 'small' }),
  },
  {
    prop: 'triggerSource',
    label: '触发来源',
    width: 100,
    render: row => h(DictTag, { dictType: 'sys_collab_trigger_source', value: row.triggerSource, size: 'small' }),
  },
  { prop: 'stage', label: '当前阶段', width: 100 },
  {
    prop: 'status',
    label: '状态',
    width: 100,
    render: row => h(DictTag, { dictType: 'sys_collab_sync_status', value: row.status, size: 'small' }),
  },
  { prop: 'deptCount', label: '部门', width: 70 },
  { prop: 'userCount', label: '成员', width: 70 },
  { prop: 'tagCount', label: '标签', width: 70 },
  { prop: 'createdCount', label: '新建', width: 70 },
  { prop: 'updatedCount', label: '更新', width: 70 },
  { prop: 'inactivatedCount', label: '停用', width: 70 },
  { prop: 'issueCount', label: '问题单', width: 80 },
  { prop: 'errorSummary', label: '错误摘要', minWidth: 160, showOverflowTooltip: true },
  { prop: 'startTime', label: '开始时间', width: 160 },
  { prop: 'endTime', label: '结束时间', width: 160 },
  {
    prop: 'action',
    label: '操作',
    width: 80,
    // 运行时表只允许删除已收敛批次，运行中批次由后端拒绝且前端不展示入口
    actions: [
      { label: '删除', key: 'delete', type: 'error', visible: row => row.status !== 'RUNNING' },
    ],
  },
])

function connectionLabel(connectionId) {
  const option = connectionOptions.value.find(item => item.value === connectionId)
  return option ? option.label : String(connectionId ?? '-')
}
</script>

<style scoped>
.collaboration-sync-page {
  height: 100%;
}
</style>
