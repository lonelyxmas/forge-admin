<template>
  <div class="capability-invocation-page">
    <AiCrudPage
      ref="crudRef"
      :api-config="{
        list: 'get@/ai/capability/invocation/page',
      }"
      :search-schema="searchSchema"
      :columns="tableColumns"
      row-key="id"
      :hide-add="true"
      :hide-selection="true"
      :hide-batch-delete="true"
    />
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'
import { computed, h, ref } from 'vue'
import { AiCrudPage } from '@/components/ai-form'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables'

defineOptions({ name: 'CapabilityInvocation' })

const { dict } = useDict('ai_capability_actor_type')

const actorTypeOptions = computed(() => dict.value.ai_capability_actor_type || [])

const crudRef = ref(null)

// resultStatus 无字典（网关内部状态机取值），按状态着色展示
const RESULT_STATUS_TYPE = {
  SUCCESS: 'success',
  PENDING_APPROVAL: 'warning',
  FAILED: 'error',
}

// 搜索表单配置
const searchSchema = computed(() => [
  {
    field: 'clientId',
    label: '客户端ID',
    type: 'input',
    props: {
      placeholder: '请输入客户端ID',
    },
  },
  {
    field: 'capabilityCode',
    label: '能力编码',
    type: 'input',
    props: {
      placeholder: '请输入能力编码',
    },
  },
  {
    field: 'resultCode',
    label: '结果码',
    type: 'input',
    props: {
      placeholder: '如 SUCCESS / FORBIDDEN',
    },
  },
])

// 表格列配置
const tableColumns = computed(() => [
  {
    prop: 'requestId',
    label: '请求ID',
    width: 220,
    ellipsis: { tooltip: true },
  },
  {
    prop: 'clientCode',
    label: '客户端',
    width: 140,
    ellipsis: { tooltip: true },
    render: row => row.clientCode || '-',
  },
  {
    prop: 'capabilityCode',
    label: '能力编码',
    width: 200,
    ellipsis: { tooltip: true },
  },
  {
    prop: 'capabilityVersion',
    label: '版本',
    width: 90,
    render: row => row.capabilityVersion || '-',
  },
  {
    prop: 'actorType',
    label: '调用主体',
    width: 100,
    render: (row) => {
      return h(DictTag, {
        options: actorTypeOptions.value,
        value: row.actorType,
        size: 'small',
      })
    },
  },
  {
    prop: 'resultStatus',
    label: '结果状态',
    width: 110,
    render: (row) => {
      if (!row.resultStatus)
        return '-'
      return h(NTag, {
        type: RESULT_STATUS_TYPE[row.resultStatus] || 'default',
        size: 'small',
      }, { default: () => row.resultStatus })
    },
  },
  {
    prop: 'resultCode',
    label: '结果码',
    width: 140,
    ellipsis: { tooltip: true },
    render: row => row.resultCode || '-',
  },
  {
    prop: 'errorCode',
    label: '错误码',
    width: 160,
    ellipsis: { tooltip: true },
    render: row => row.errorCode || '-',
  },
  {
    prop: 'durationMs',
    label: '耗时(ms)',
    width: 100,
    render: row => row.durationMs ?? '-',
  },
  {
    prop: 'traceId',
    label: 'TraceID',
    width: 180,
    ellipsis: { tooltip: true },
    render: row => row.traceId || '-',
  },
  {
    prop: 'createTime',
    label: '调用时间',
    width: 160,
  },
])
</script>

<style scoped>
.capability-invocation-page {
  height: 100%;
}
</style>
