<template>
  <div class="excel-export-config-page">
    <AiCrudPage
      ref="crudRef"
      :api-config="{
        list: 'get@/system/excel/export-config/page',
        detail: 'post@/system/excel/export-config/detail',
        add: 'post@/system/excel/export-config',
        update: 'put@/system/excel/export-config',
        delete: 'delete@/system/excel/export-config/:id',
      }"
      :search-schema="searchSchema"
      :columns="tableColumns"
      :edit-schema="editSchema"
      :before-submit="normalizeConfigBeforeSubmit"
      row-key="id"
      add-button-text="新增配置"
      :load-detail-on-edit="true"
      :edit-grid-cols="2"
      modal-width="1000px"
    >
      <!-- 工具栏扩展按钮 -->
      <template #toolbar-end>
        <NButton type="info" size="small" @click="handleRefresh">
          <template #icon>
            <i class="i-material-symbols:refresh" />
          </template>
          刷新
        </NButton>
      </template>
    </AiCrudPage>

    <!-- 列配置管理弹窗 -->
    <NModal
      v-model:show="showColumnModal"
      preset="card"
      title="列配置管理"
      :style="{ width: '1200px' }"
      :mask-closable="false"
    >
      <ExcelColumnConfig
        v-if="showColumnModal"
        :config-key="currentConfigKey"
        :config-name="currentConfigName"
        :config-type="currentConfigType"
      />
    </NModal>

    <!-- 复制配置弹窗 -->
    <NModal
      v-model:show="showCopyModal"
      preset="dialog"
      title="复制配置"
      positive-text="确定"
      negative-text="取消"
      @positive-click="handleConfirmCopy"
    >
      <NForm ref="copyFormRef" :model="copyForm" label-placement="left" label-width="120">
        <NFormItem
          label="新配置键"
          path="newConfigKey"
          :rule="{ required: true, message: '请输入新配置键', trigger: 'blur' }"
        >
          <NInput
            v-model:value="copyForm.newConfigKey"
            placeholder="请输入新配置键，如：user_list_export_v2"
          />
        </NFormItem>
      </NForm>
    </NModal>
  </div>
</template>

<script setup>
import { NButton, NForm, NFormItem, NInput, NModal } from 'naive-ui'
import { computed, h, ref } from 'vue'
import { AiCrudPage } from '@/components/ai-form'
import SystemTableCell from '@/components/common/SystemTableCell.vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'
import { managedFetch } from '@/composables/useGlobalLoading'
import { useAuthStore } from '@/store'
import { request } from '@/utils'
import ExcelColumnConfig from './excel-column-config.vue'

defineOptions({ name: 'ExcelExportConfig' })

const CONFIG_TYPE_DICT = 'sys_excel_config_type'
const STATUS_DICT = 'sys_enable_disable'

const authStore = useAuthStore()
const crudRef = ref(null)
const showColumnModal = ref(false)
const showCopyModal = ref(false)
const copyFormRef = ref(null)
const currentConfigKey = ref('')
const currentConfigName = ref('')
const currentConfigType = ref('BOTH')
const copyForm = ref({
  sourceId: null,
  newConfigKey: '',
})
const { dict } = useDict(CONFIG_TYPE_DICT, STATUS_DICT)
const configTypeOptions = computed(() => dict.value[CONFIG_TYPE_DICT] || [])
const statusOptions = computed(() => toNumberOptions(dict.value[STATUS_DICT]))

// 搜索表单配置
const searchSchema = computed(() => [
  {
    field: 'configKey',
    label: '配置键',
    type: 'input',
    props: {
      placeholder: '请输入配置键',
    },
  },
  {
    field: 'exportName',
    label: '配置名称',
    type: 'input',
    props: {
      placeholder: '请输入配置名称',
    },
  },
  {
    field: 'configType',
    label: '配置类型',
    type: 'select',
    props: {
      placeholder: '请选择配置类型',
      clearable: true,
      options: configTypeOptions.value,
    },
  },
  {
    field: 'status',
    label: '状态',
    type: 'select',
    props: {
      placeholder: '请选择状态',
      clearable: true,
      options: statusOptions.value,
    },
  },
])

// 表格列配置
const tableColumns = computed(() => [
  {
    prop: 'configType',
    label: '配置类型',
    width: 110,
    render: row => h(DictTag, { dictType: CONFIG_TYPE_DICT, value: row.configType || 'BOTH', size: 'small' }),
  },
  {
    prop: 'exportName',
    label: '导出配置',
    minWidth: 210,
    render: row => h(SystemTableCell, {
      title: row.exportName,
      subtitle: row.configKey,
      interactive: true,
      tooltip: `查看导出配置：${row.exportName || row.configKey || '-'}`,
      onActivate: () => crudRef.value?.showDetail(row),
    }),
  },
  {
    prop: 'sheetName',
    label: 'Sheet名称',
    width: 120,
  },
  {
    prop: 'dataSourceBean',
    label: '数据源Bean',
    minWidth: 150,
    showOverflowTooltip: true,
  },
  {
    prop: 'queryMethod',
    label: '查询方法',
    width: 120,
  },
  {
    prop: 'maxRows',
    label: '最大行数',
    width: 100,
  },
  {
    prop: 'status',
    label: '状态',
    width: 80,
    render: row => h(DictTag, { dictType: STATUS_DICT, value: String(row.status ?? ''), size: 'small' }),
  },
  {
    prop: 'createTime',
    label: '创建时间',
    width: 180,
  },
  {
    prop: 'action',
    label: '操作',
    width: 160,
    fixed: 'right',
    actions: [
      { label: '编辑', key: 'edit', onClick: handleEdit },
      { label: '列配置', key: 'columns', type: 'info', onClick: handleManageColumns },
      { label: '导出测试', key: 'test', onClick: handleTestExport, visible: row => isExportConfig(row) },
      { label: '复制配置', key: 'copy', onClick: handleCopy },
      { label: '禁用', key: 'disable', type: 'warning', onClick: handleToggleStatus, visible: row => row.status === 1 },
      { label: '启用', key: 'enable', type: 'success', onClick: handleToggleStatus, visible: row => row.status !== 1 },
      { label: '删除', key: 'delete', type: 'error', onClick: handleDelete },
    ],
  },
])

// 编辑表单配置
const editSchema = computed(() => [
  // ==================== 基础信息 ====================
  {
    type: 'divider',
    label: '基础信息',
    props: {
      titlePlacement: 'left',
    },
    span: 2,
  },
  {
    field: 'configKey',
    label: '配置键',
    type: 'input',
    rules: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
    props: {
      placeholder: '请输入唯一配置键，如：user_list_export',
    },
  },
  {
    field: 'exportName',
    label: '配置名称',
    type: 'input',
    rules: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
    props: {
      placeholder: '请输入配置名称，如：用户列表导入导出',
    },
  },
  {
    field: 'configType',
    label: '配置类型',
    type: 'select',
    defaultValue: 'BOTH',
    rules: [{ required: true, message: '请选择配置类型', trigger: 'change' }],
    props: {
      options: configTypeOptions.value,
      placeholder: '请选择配置类型',
    },
    help: '选择后页面会自动区分导入设置和导出设置',
  },

  // ==================== 导出数据源配置 ====================
  {
    type: 'divider',
    label: '导出设置',
    props: {
      titlePlacement: 'left',
    },
    span: 2,
    vIf: formData => isExportType(formData.configType),
  },
  {
    field: 'sheetName',
    label: '导出Sheet',
    type: 'input',
    vIf: formData => isExportType(formData.configType),
    props: {
      placeholder: '请输入导出Sheet名称，默认：Sheet1',
    },
  },
  {
    field: 'fileNameTemplate',
    label: '导出文件名模板',
    type: 'input',
    span: 2,
    vIf: formData => isExportType(formData.configType),
    props: {
      placeholder: '支持占位符：{date}、{time}，如：用户列表_{date}.xlsx',
    },
    help: '支持占位符：{date}（日期如20240101）、{time}（时间如120530）',
  },
  {
    field: 'dataSourceBean',
    label: '导出数据源Bean',
    type: 'input',
    vIf: formData => isExportType(formData.configType),
    props: {
      placeholder: '仅导出/导入导出必填，如：sysUserService',
    },
  },
  {
    field: 'queryMethod',
    label: '导出查询方法',
    type: 'input',
    vIf: formData => isExportType(formData.configType),
    props: {
      placeholder: '仅导出/导入导出必填，如：list、page',
    },
  },
  {
    field: 'pageable',
    label: '导出分页查询',
    type: 'switch',
    vIf: formData => isExportType(formData.configType),
    props: {
      checkedValue: true,
      uncheckedValue: false,
    },
  },
  {
    field: 'maxRows',
    label: '最大导出行数',
    type: 'number',
    vIf: formData => isExportType(formData.configType),
    props: {
      placeholder: '最大导出条数',
      min: 1,
      max: 1000000,
    },
  },

  // ==================== 高级配置 ====================
  {
    type: 'divider',
    label: '高级配置',
    props: {
      titlePlacement: 'left',
    },
    span: 2,
  },
  {
    field: 'autoTrans',
    label: '导出字典翻译',
    type: 'switch',
    vIf: formData => isExportType(formData.configType),
    props: {
      checkedValue: true,
      uncheckedValue: false,
    },
    help: '是否自动翻译字典类型字段',
  },
  {
    field: 'sortField',
    label: '导出排序字段',
    type: 'input',
    vIf: formData => isExportType(formData.configType),
    props: {
      placeholder: '请输入排序字段名',
    },
  },
  {
    field: 'sortOrder',
    label: '导出排序方向',
    type: 'select',
    vIf: formData => isExportType(formData.configType),
    props: {
      options: [
        { label: '升序', value: 'ASC' },
        { label: '降序', value: 'DESC' },
      ],
    },
  },

  // ==================== 导入配置 ====================
  {
    type: 'divider',
    label: '导入设置',
    props: {
      titlePlacement: 'left',
    },
    span: 2,
    vIf: formData => isImportType(formData.configType),
  },
  {
    field: 'includeSample',
    label: '导入模板示例',
    type: 'switch',
    vIf: formData => isImportType(formData.configType),
    props: {
      checkedValue: true,
      uncheckedValue: false,
    },
    help: '下载导入模板时是否生成示例数据',
  },
  {
    field: 'status',
    label: '状态',
    type: 'select',
    defaultValue: 1,
    props: {
      options: statusOptions.value,
    },
  },
  {
    field: 'remark',
    label: '备注',
    type: 'textarea',
    span: 2,
    props: {
      placeholder: '请输入备注信息',
      rows: 3,
    },
  },
])

function isExportConfig(row) {
  return isExportType(row?.configType)
}

function isExportType(configType) {
  return (configType || 'BOTH') !== 'IMPORT'
}

function isImportType(configType) {
  return (configType || 'BOTH') !== 'EXPORT'
}

function normalizeConfigBeforeSubmit(formData) {
  const configType = formData.configType || 'BOTH'
  const exportEnabled = isExportType(configType)
  if (exportEnabled && (!formData.dataSourceBean || !formData.queryMethod)) {
    window.$message.error('仅导出或导入导出配置必须填写数据源Bean和查询方法')
    return false
  }
  return {
    ...formData,
    configType,
    allowImport: configType !== 'EXPORT',
    dataSourceBean: exportEnabled ? formData.dataSourceBean : null,
    queryMethod: exportEnabled ? formData.queryMethod : null,
    maxRows: exportEnabled ? formData.maxRows : null,
    pageable: exportEnabled ? formData.pageable : false,
  }
}

function toNumberOptions(options = []) {
  return (options || []).map(item => ({
    ...item,
    value: Number(item.value),
  }))
}

// 刷新
function handleRefresh() {
  crudRef.value?.refresh()
}

// 编辑
function handleEdit(row) {
  crudRef.value?.showEdit(row)
}

// 删除
function handleDelete(row) {
  window.$dialog.warning({
    title: '确认删除',
    content: `确定要删除配置“${row.exportName}”吗？删除后将同时删除所有关联的列配置！`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await request.delete(`/system/excel/export-config/${row.id}`)
        if (res.code === 200) {
          window.$message.success('删除成功')
          crudRef.value?.refresh()
        }
        else {
          window.$message.error(res.respMsg || '删除失败')
        }
      }
      catch (error) {
        window.$message.error(`删除失败：${error.message || '未知错误'}`)
      }
    },
  })
}

// 管理列配置
function handleManageColumns(row) {
  currentConfigKey.value = row.configKey
  currentConfigName.value = row.exportName
  currentConfigType.value = row.configType || 'BOTH'
  showColumnModal.value = true
}

// 导出测试
async function handleTestExport(row) {
  if (!isExportConfig(row)) {
    window.$message.warning('仅导入配置不支持导出测试')
    return
  }
  try {
    // 使用系统统一的请求前缀
    const token = authStore.accessToken
    const baseUrl = import.meta.env.VITE_REQUEST_PREFIX || '/dev-api'
    const url = `${baseUrl}/system/excel/export-config/test/${row.id}`

    // 使用 fetch 下载文件
    const response = await managedFetch(url, {
      method: 'GET',
      headers: {
        Authorization: token ? `Bearer ${token}` : '',
      },
    }, {
      globalLoadingType: 'export',
      globalLoadingText: '文件导出处理中，请稍候...',
    })

    if (!response.ok) {
      const error = await response.text()
      console.error('导出失败响应:', error)
      throw new Error(error || '导出失败')
    }

    // 获取文件名（从 Content-Disposition 头中获取）
    const contentDisposition = response.headers.get('Content-Disposition')
    let fileName = `${row.exportName}_测试.xlsx`
    if (contentDisposition) {
      const match = contentDisposition.match(/filename\*=utf-8''(.+)/)
      if (match) {
        fileName = decodeURIComponent(match[1])
      }
    }

    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)

    // 创建下载链接
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)

    // 释放 blob URL
    setTimeout(() => URL.revokeObjectURL(blobUrl), 100)

    window.$message.success('导出成功')
  }
  catch (error) {
    console.error('导出测试失败:', error)
    window.$message.error(`导出失败：${error.message || '未知错误'}`)
  }
}

// 复制配置
function handleCopy(row) {
  copyForm.value = {
    sourceId: row.id,
    newConfigKey: `${row.configKey}_copy`,
  }
  showCopyModal.value = true
}

// 确认复制
async function handleConfirmCopy() {
  try {
    await copyFormRef.value?.validate()
    const loading = window.$message.loading('正在复制配置...', { duration: 0 })

    try {
      const res = await request.post('/system/excel/export-config/copy', null, {
        params: {
          id: copyForm.value.sourceId,
          newConfigKey: copyForm.value.newConfigKey,
        },
      })

      if (res.code === 200) {
        window.$message.success('复制成功')
        showCopyModal.value = false
        crudRef.value?.refresh()
      }
      else {
        window.$message.error(res.respMsg || '复制失败')
      }
    }
    finally {
      loading.destroy()
    }
  }
  catch (error) {
    if (error?.message !== '验证失败') {
      window.$message.error(`复制失败：${error.message || '未知错误'}`)
    }
  }
}

// 切换状态
async function handleToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1

  try {
    const res = await request.put('/system/excel/export-config/status', null, {
      params: {
        id: row.id,
        status: newStatus,
      },
    })

    if (res.code === 200) {
      window.$message.success('状态更新成功')
    }
    else {
      window.$message.error(res.respMsg || '状态更新失败')
    }
  }
  catch (error) {
    window.$message.error(`状态更新失败：${error.message || '未知错误'}`)
  }
}
</script>

<style scoped>
.excel-export-config-page {
  height: 100%;
}
</style>
