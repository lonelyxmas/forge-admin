<template>
  <div class="social-config-page">
    <n-alert type="info" class="mb-3" title="配置管理已迁移" closable>
      三方登录连接、物理应用与 Secret 凭据已统一迁移至「企业协同 - 连接管理」维护，本页仅保留只读兼容视图，不再支持新增、编辑和删除。
      <template #action>
        <n-button size="small" type="primary" @click="handleGoCollaboration">
          前往连接管理
        </n-button>
      </template>
    </n-alert>

    <AiCrudPage
      ref="crudRef"
      api="/system/socialConfig"
      :api-config="{ list: 'get@/system/socialConfig/page' }"
      :search-schema="searchSchema"
      :columns="tableColumns"
      row-key="id"
      :hide-add="true"
      :hide-batch-delete="true"
      :hide-selection="true"
    >
      <template #toolbar-end>
        <n-button
          type="warning"
          :loading="refreshLoading"
          @click="handleRefreshCache"
        >
          <template #icon>
            <i class="i-material-symbols:refresh" />
          </template>
          刷新缓存
        </n-button>
      </template>
    </AiCrudPage>

    <n-modal
      v-model:show="detailVisible"
      title="三方登录配置详情（只读）"
      preset="card"
      style="width: 800px"
      :mask-closable="false"
    >
      <div v-if="currentConfig" class="detail-content">
        <n-descriptions bordered :column="2">
          <n-descriptions-item label="ID">
            {{ currentConfig.id }}
          </n-descriptions-item>
          <n-descriptions-item label="平台类型">
            <DictTag dict-type="sys_social_platform" :value="currentConfig.platform" size="small" />
          </n-descriptions-item>
          <n-descriptions-item label="平台名称">
            {{ currentConfig.platformName }}
          </n-descriptions-item>
          <n-descriptions-item label="平台Logo">
            <AuthImage
              v-if="currentConfig.platformLogo"
              :src="currentConfig.platformLogo"
              :img-style="{ width: '60px', height: '60px', objectFit: 'cover', borderRadius: '4px' }"
            />
            <span v-else>-</span>
          </n-descriptions-item>
          <n-descriptions-item label="连接编码">
            {{ currentConfig.connectionCode || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="连接名称">
            {{ currentConfig.connectionName || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="外部企业ID">
            {{ currentConfig.enterpriseId || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="应用ID">
            {{ currentConfig.clientId || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="应用Secret">
            {{ currentConfig.secretConfigured ? (currentConfig.secretMasked || '已配置') : '未配置' }}
          </n-descriptions-item>
          <n-descriptions-item label="AgentId">
            {{ currentConfig.agentId || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="回调地址" :span="2">
            {{ currentConfig.redirectUri || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="授权范围">
            {{ currentConfig.scope || '-' }}
          </n-descriptions-item>
          <n-descriptions-item label="状态">
            <DictTag dict-type="sys_normal_disable" :value="String(currentConfig.status)" size="small" />
          </n-descriptions-item>
          <n-descriptions-item label="创建时间">
            {{ currentConfig.createTime }}
          </n-descriptions-item>
          <n-descriptions-item label="更新时间">
            {{ currentConfig.updateTime }}
          </n-descriptions-item>
          <n-descriptions-item label="备注说明" :span="2">
            {{ currentConfig.remark || '-' }}
          </n-descriptions-item>
        </n-descriptions>
      </div>
      <template #footer>
        <n-space justify="end">
          <n-button @click="detailVisible = false">
            关闭
          </n-button>
          <n-button type="primary" @click="handleGoCollaboration">
            去连接管理维护
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AiCrudPage } from '@/components/ai-form'
import AuthImage from '@/components/common/AuthImage.vue'
import SystemTableCell from '@/components/common/SystemTableCell.vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'
import { request } from '@/utils'

defineOptions({ name: 'SocialConfig' })

const router = useRouter()
const crudRef = ref(null)
const detailVisible = ref(false)
const currentConfig = ref(null)
const refreshLoading = ref(false)

const { dict } = useDict('sys_social_platform', 'sys_normal_disable')

// 使用 computed 从字典获取选项，响应式更新
const platformOptions = computed(() => dict.value.sys_social_platform || [])
const statusOptions = computed(() => dict.value.sys_normal_disable || [])

const searchSchema = computed(() => [
  {
    field: 'platform',
    label: '平台类型',
    type: 'select',
    props: {
      placeholder: '请选择平台类型',
      options: platformOptions.value,
      clearable: true,
    },
  },
  {
    field: 'platformName',
    label: '平台名称',
    type: 'input',
    props: {
      placeholder: '请输入平台名称',
    },
  },
  {
    field: 'status',
    label: '状态',
    type: 'select',
    props: {
      placeholder: '请选择',
      options: statusOptions.value,
      clearable: true,
    },
  },
])

const tableColumns = computed(() => [
  {
    prop: 'platform',
    label: '平台类型',
    width: 120,
    render: (row) => {
      return h(DictTag, { dictType: 'sys_social_platform', value: row.platform, size: 'small' },
      )
    },
  },
  {
    prop: 'platformName',
    label: '平台',
    minWidth: 150,
    render: row => h(SystemTableCell, {
      title: row.platformName,
      subtitle: row.platform,
      interactive: true,
      tooltip: `查看三方登录配置：${row.platformName || row.platform || '-'}`,
      onActivate: () => handleView(row),
    }),
  },
  {
    prop: 'platformLogo',
    label: 'Logo',
    width: 80,
    render: (row) => {
      if (row.platformLogo) {
        return h(AuthImage, {
          src: row.platformLogo,
          imgStyle: { width: '40px', height: '40px', objectFit: 'cover', borderRadius: '4px' },
        })
      }
      return '-'
    },
  },
  {
    prop: 'connectionCode',
    label: '连接编码',
    width: 150,
    showOverflowTooltip: true,
    render: row => row.connectionCode || '-',
  },
  {
    prop: 'clientId',
    label: '应用ID',
    width: 180,
    showOverflowTooltip: true,
  },
  {
    prop: 'secretConfigured',
    label: 'Secret',
    width: 90,
    render: row => (row.secretConfigured ? '已配置' : '未配置'),
  },
  {
    prop: 'status',
    label: '状态',
    width: 80,
    render: (row) => {
      return h(DictTag, { dictType: 'sys_normal_disable', value: String(row.status), size: 'small' },
      )
    },
  },
  {
    prop: 'createTime',
    label: '创建时间',
    width: 160,
  },
  {
    prop: 'action',
    label: '操作',
    width: 130,
    fixed: 'right',
    actions: [
      { label: '查看', key: 'view', type: 'primary', onClick: handleView },
      { label: '去维护', key: 'maintain', type: 'primary', onClick: handleGoCollaboration },
    ],
  },
])

async function handleView(row) {
  try {
    const res = await request.post('/system/socialConfig/getById', null, {
      params: { id: row.id },
    })
    if (res.code === 200) {
      currentConfig.value = res.data
      detailVisible.value = true
    }
  }
  catch {
    window.$message.error('获取详情失败')
  }
}

function handleGoCollaboration() {
  router.push('/system/collaboration/connections')
}

async function handleRefreshCache() {
  window.$dialog.warning({
    title: '确认刷新',
    content: '确定要刷新三方登录配置缓存吗？',
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        refreshLoading.value = true
        const res = await request.post('/system/socialConfig/refreshCache')
        if (res.code === 200) {
          window.$message.success('缓存刷新成功')
        }
      }
      catch {
        window.$message.error('缓存刷新失败')
      }
      finally {
        refreshLoading.value = false
      }
    },
  })
}
</script>

<style scoped>
.social-config-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.social-config-page :deep(.ai-crud-page) {
  flex: 1;
  min-height: 0;
}

.detail-content {
  padding: 8px 0;
}
</style>
