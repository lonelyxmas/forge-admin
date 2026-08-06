<template>
  <div class="agent-tool-page">
    <AiCrudPage
      ref="crudRef"
      api="/ai/agent-tool"
      :api-config="{
        list: 'get@/ai/agent-tool/page',
        detail: 'get@/ai/agent-tool/:id',
        add: 'post@/ai/agent-tool',
        update: 'put@/ai/agent-tool',
        delete: 'delete@/ai/agent-tool/:id',
      }"
      :search-schema="searchSchema"
      :columns="tableColumns"
      :edit-schema="editSchema"
      row-key="id"
      :edit-grid-cols="2"
      modal-width="560px"
      add-button-text="新增工具绑定"
      :before-search="handleBeforeSearch"
    />
  </div>
</template>

<script setup>
import { h, ref } from 'vue'
import { useRoute } from 'vue-router'
import { NTag } from 'naive-ui'
import { AiCrudPage } from '@/components/ai-form'

defineOptions({ name: 'AiAgentTool' })

const route = useRoute()
const crudRef = ref(null)

const searchSchema = [
  { field: 'agentId', label: 'Agent', type: 'number', placeholder: '输入Agent ID' },
  { field: 'toolSource', label: '工具来源', type: 'input', placeholder: 'mcp/builtin/capability' },
  { field: 'keyword', label: '工具标识', type: 'input', placeholder: '搜索' },
]

const tableColumns = [
  { type: 'index', label: '#', width: 60 },
  { prop: 'id', label: 'ID', width: 80 },
  { prop: 'agentId', label: 'Agent ID', width: 100 },
  { prop: 'toolSource', label: '来源', width: 100, formatter: (row) => h(NTag, { size: 'small', type: row.toolSource === 'mcp' ? 'info' : 'default', bordered: false }, () => row.toolSource) },
  { prop: 'toolKey', label: '工具标识', minWidth: 140 },
  { prop: 'toolGroup', label: '工具组', width: 100, formatter: (row) => row.toolGroup || '-' },
  { prop: 'enabled', label: '启用', type: 'switch', width: 80, activeValue: '1', inactiveValue: '0' },
  {
    label: '操作',
    width: 140,
    formatter: (row) => {
      const editBtn = h('a', { class: 'text-primary cursor-pointer hover:text-primary-hover', onClick: () => crudRef.value?.handleEdit?.(row) }, '编辑')
      const delBtn = h('a', { class: 'text-error cursor-pointer hover:text-error-hover', style: 'margin-left:12px', onClick: () => crudRef.value?.handleDelete?.(row) }, '删除')
      return h('span', null, [editBtn, delBtn])
    },
  },
]

const editSchema = [
  { field: 'agentId', label: 'Agent ID', component: 'InputNumber', required: true, componentProps: { min: 1 } },
  { field: 'toolSource', label: '工具来源', component: 'Input', required: true, componentProps: { placeholder: 'mcp/builtin/capability' } },
  { field: 'toolKey', label: '工具标识', component: 'Input', required: true, componentProps: { placeholder: '工具key' } },
  { field: 'toolGroup', label: '工具组', component: 'Input', componentProps: { placeholder: 'default' } },
  { field: 'enabled', label: '启用', component: 'Switch', activeValue: '1', inactiveValue: '0' },
]

// 从路由参数预填 agentId 搜索过滤条件
function handleBeforeSearch({ searchForm }) {
  const agentId = route.query.agentId
  if (agentId && !searchForm.agentId) {
    searchForm.agentId = Number(agentId)
  }
}
</script>
