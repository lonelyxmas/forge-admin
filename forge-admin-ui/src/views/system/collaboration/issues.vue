<template>
  <div class="collaboration-issues-page">
    <AiCrudPage
      ref="crudRef"
      api="/system/collaboration/sync-issues"
      :api-config="{ list: 'get@/system/collaboration/sync-issues/page' }"
      :search-schema="searchSchema"
      :columns="tableColumns"
      row-key="id"
      :hide-add="true"
      :hide-batch-delete="true"
      :hide-selection="true"
    />

    <!-- 问题单处理：BIND 绑定已有用户 / IGNORE 忽略 / RETRY 待下轮重试 -->
    <n-modal
      v-model:show="resolveVisible"
      title="处理问题单"
      preset="card"
      style="width: 520px"
      :mask-closable="false"
    >
      <n-descriptions v-if="currentIssue" bordered :column="1" size="small" class="mb-4">
        <n-descriptions-item label="对象类型">
          {{ currentIssue.objectType }}
        </n-descriptions-item>
        <n-descriptions-item label="外部ID">
          {{ currentIssue.externalId }}
        </n-descriptions-item>
        <n-descriptions-item label="问题摘要">
          {{ currentIssue.issueSummary || currentIssue.issueCode }}
        </n-descriptions-item>
      </n-descriptions>
      <n-form label-placement="left" label-width="110px">
        <n-form-item label="处理动作">
          <n-radio-group v-model:value="resolveForm.action">
            <n-space>
              <n-radio value="BIND">
                绑定已有用户
              </n-radio>
              <n-radio value="IGNORE">
                忽略
              </n-radio>
              <n-radio value="RETRY">
                下轮重试
              </n-radio>
            </n-space>
          </n-radio-group>
        </n-form-item>
        <n-form-item v-if="resolveForm.action === 'BIND'" label="目标用户ID">
          <n-input-number
            v-model:value="resolveForm.targetUserId"
            placeholder="请输入要绑定的系统用户ID"
            :show-button="false"
            style="width: 100%"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="resolveVisible = false">
            取消
          </n-button>
          <n-button type="primary" :loading="resolveLoading" @click="handleSubmitResolve">
            确认处理
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <CollaborationDetailModal
      v-model:show="detailVisible"
      title="问题单详情"
      :width="720"
      :fields="detailFields"
      :data="detailRow"
    />
  </div>
</template>

<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { fetchConnectionOptions, resolveSyncIssue } from '@/api/collaboration'
import { AiCrudPage } from '@/components/ai-form'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'
import CollaborationDetailModal from './CollaborationDetailModal.vue'

defineOptions({ name: 'CollaborationIssues' })

const crudRef = ref(null)
const connectionOptions = ref([])
const detailVisible = ref(false)
const detailRow = ref(null)

const { dict } = useDict('sys_collab_issue_status')

const issueStatusOptions = computed(() => dict.value.sys_collab_issue_status || [])

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
    field: 'processStatus',
    label: '处理状态',
    type: 'select',
    props: { placeholder: '请选择', options: issueStatusOptions.value, clearable: true },
  },
  {
    field: 'issueCode',
    label: '问题编码',
    type: 'input',
    props: { placeholder: '请输入问题编码' },
  },
])

const tableColumns = computed(() => [
  { prop: 'id', label: 'ID', width: 90 },
  { prop: 'syncLogId', label: '批次ID', width: 90 },
  { prop: 'objectType', label: '对象类型', width: 100 },
  { prop: 'externalId', label: '外部ID', width: 140, showOverflowTooltip: true },
  { prop: 'issueCode', label: '问题编码', width: 160, showOverflowTooltip: true },
  { prop: 'issueSummary', label: '问题摘要', minWidth: 200, showOverflowTooltip: true },
  {
    prop: 'processStatus',
    label: '处理状态',
    width: 100,
    render: row => h(DictTag, { dictType: 'sys_collab_issue_status', value: row.processStatus, size: 'small' }),
  },
  { prop: 'processAction', label: '处理动作', width: 100 },
  { prop: 'retryCount', label: '重试次数', width: 90 },
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
        label: '处理',
        key: 'resolve',
        type: 'primary',
        visible: row => row.processStatus === 'PENDING',
        onClick: handleOpenResolve,
      },
    ],
  },
])

const detailFields = computed(() => [
  { key: 'id', label: '问题单ID' },
  { key: 'syncLogId', label: '同步批次ID' },
  { key: 'connectionId', label: '连接', render: row => connectionLabel(row.connectionId) },
  { key: 'objectType', label: '对象类型' },
  { key: 'externalId', label: '外部ID' },
  { key: 'issueCode', label: '问题编码' },
  { key: 'processStatus', label: '处理状态', dictType: 'sys_collab_issue_status' },
  { key: 'processAction', label: '处理动作' },
  { key: 'processBy', label: '处理人ID' },
  { key: 'processTime', label: '处理时间' },
  { key: 'retryCount', label: '重试次数' },
  { key: 'createTime', label: '创建时间' },
  { key: 'issueSummary', label: '问题摘要', pre: true },
])

function connectionLabel(connectionId) {
  const option = connectionOptions.value.find(item => item.value === connectionId)
  return option ? option.label : String(connectionId ?? '-')
}

function handleViewDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

// ==================== 处理弹窗 ====================

const resolveVisible = ref(false)
const resolveLoading = ref(false)
const currentIssue = ref(null)
const resolveForm = ref({ action: 'BIND', targetUserId: null })

function handleOpenResolve(row) {
  currentIssue.value = row
  resolveForm.value = { action: 'BIND', targetUserId: null }
  resolveVisible.value = true
}

async function handleSubmitResolve() {
  if (resolveForm.value.action === 'BIND' && !resolveForm.value.targetUserId) {
    window.$message.warning('绑定时必须填写目标用户ID')
    return
  }
  resolveLoading.value = true
  try {
    const res = await resolveSyncIssue(currentIssue.value.id, resolveForm.value)
    if (res.code === 200) {
      window.$message.success('处理成功')
      resolveVisible.value = false
      crudRef.value?.refresh()
    }
  }
  catch {
    window.$message.error('处理失败')
  }
  finally {
    resolveLoading.value = false
  }
}
</script>

<style scoped>
.collaboration-issues-page {
  height: 100%;
}
</style>
