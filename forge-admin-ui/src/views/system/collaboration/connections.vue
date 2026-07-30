<template>
  <div class="collaboration-connections-page">
    <AiCrudPage
      ref="crudRef"
      api="/system/collaboration/connections"
      :api-config="{
        list: 'get@/system/collaboration/connections/page',
        add: 'post@/system/collaboration/connections',
        update: 'put@/system/collaboration/connections',
        delete: 'delete@/system/collaboration/connections/:id',
      }"
      :search-schema="searchSchema"
      :columns="tableColumns"
      :edit-schema="editSchema"
      row-key="id"
      add-button-text="新建连接"
      :edit-grid-cols="2"
      modal-width="900px"
      :hide-batch-delete="true"
      :hide-selection="true"
      :before-render-detail="handleBeforeRenderDetail"
      :before-submit="handleBeforeSubmit"
    />

    <!-- 连接详情（只读查看）与应用管理（新增/编辑/绑定）共用弹窗，由 manageMode 区分 -->
    <n-modal
      v-model:show="detailVisible"
      :title="manageMode ? '应用与能力管理' : '连接详情'"
      preset="card"
      style="width: 1000px"
      :mask-closable="false"
    >
      <n-spin :show="detailLoading">
        <div v-if="detail" class="detail-content">
          <n-descriptions v-if="!manageMode" bordered :column="3" size="small">
            <n-descriptions-item label="平台">
              <DictTag dict-type="sys_collab_platform" :value="detail.connection?.platform" size="small" />
            </n-descriptions-item>
            <n-descriptions-item label="连接编码">
              {{ detail.connection?.connectionCode || '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="连接名称">
              {{ detail.connection?.connectionName || '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="企业ID">
              {{ detail.connection?.enterpriseId || '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="身份匹配策略">
              <DictTag dict-type="sys_collab_identity_policy" :value="detail.connection?.identityPolicy" size="small" />
            </n-descriptions-item>
            <n-descriptions-item label="默认角色">
              {{ formatDefaultRoles(detail.connection?.defaultRoleIds) }}
            </n-descriptions-item>
            <n-descriptions-item label="目录权威来源">
              <DictTag dict-type="sys_collab_directory_authority" :value="detail.connection?.directoryAuthority" size="small" />
            </n-descriptions-item>
            <n-descriptions-item label="默认挂载组织">
              {{ detail.connection?.defaultOrgId || '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="API基础地址">
              {{ detail.connection?.apiBaseUrl || '官方地址' }}
            </n-descriptions-item>
            <n-descriptions-item label="状态">
              <DictTag dict-type="sys_normal_disable" :value="String(detail.connection?.status ?? '')" size="small" />
            </n-descriptions-item>
            <n-descriptions-item label="更新时间">
              {{ detail.connection?.updateTime || '-' }}
            </n-descriptions-item>
          </n-descriptions>
          <n-alert v-if="manageMode" type="info" :show-icon="false" size="small">
            当前连接：{{ detail.connection?.connectionName || '-' }}（{{ detail.connection?.connectionCode || '-' }}）
          </n-alert>

          <n-divider title-placement="left">
            物理应用
            <n-button
              v-if="manageMode && canManageConnection"
              size="tiny"
              type="primary"
              class="ml-2"
              @click="handleAddApp"
            >
              新增应用
            </n-button>
          </n-divider>
          <n-data-table
            :columns="appColumns"
            :data="detail.apps || []"
            :bordered="true"
            size="small"
          />

          <n-divider title-placement="left">
            能力绑定
            <n-button
              v-if="manageMode && canManageConnection"
              size="tiny"
              type="primary"
              class="ml-2"
              @click="handleOpenBind"
            >
              绑定能力
            </n-button>
          </n-divider>
          <n-data-table
            :columns="bindingColumns"
            :data="detail.bindings || []"
            :bordered="true"
            size="small"
          />
        </div>
      </n-spin>
      <template #footer>
        <n-space justify="end">
          <n-button @click="detailVisible = false">
            关闭
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 物理应用新增/编辑：Secret 编辑时留空表示保留现值 -->
    <n-modal
      v-model:show="appModalVisible"
      :title="appForm.id ? '编辑应用' : '新增应用'"
      preset="card"
      style="width: 720px"
      :mask-closable="false"
    >
      <n-form
        ref="appFormRef"
        :model="appForm"
        :rules="appFormRules"
        label-placement="left"
        label-width="130px"
      >
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="应用编码" path="appCode">
            <n-input v-model:value="appForm.appCode" placeholder="连接内唯一，如 main-app" :disabled="!!appForm.id" />
          </n-form-item-gi>
          <n-form-item-gi label="应用名称" path="appName">
            <n-input v-model:value="appForm.appName" placeholder="请输入应用名称" />
          </n-form-item-gi>
          <n-form-item-gi label="应用ID/Key" path="clientId">
            <n-input v-model:value="appForm.clientId" placeholder="企微填 CorpId，OAuth 平台填 ClientID" />
          </n-form-item-gi>
          <n-form-item-gi label="AgentId" path="agentId">
            <n-input v-model:value="appForm.agentId" placeholder="企微自建应用 AgentId，OAuth 平台可空" />
          </n-form-item-gi>
          <n-form-item-gi label="应用Secret" path="secret" :span="2">
            <n-input
              v-model:value="appForm.secret"
              type="password"
              show-password-on="click"
              :placeholder="appForm.id ? '留空表示保留现有Secret' : '企微填 Secret，OAuth 平台填 ClientSecret'"
            />
          </n-form-item-gi>
          <n-form-item-gi label="回调Token" path="callbackToken">
            <n-input
              v-model:value="appForm.callbackToken"
              type="password"
              show-password-on="click"
              :placeholder="appForm.id ? '留空保留现值' : '仅企业平台接收事件回调时填写'"
            />
          </n-form-item-gi>
          <n-form-item-gi label="EncodingAESKey" path="encodingAesKey">
            <n-input
              v-model:value="appForm.encodingAesKey"
              type="password"
              show-password-on="click"
              :placeholder="appForm.id ? '留空保留现值' : '仅企业平台接收事件回调时填写'"
            />
          </n-form-item-gi>
          <n-form-item-gi label="OAuth回调地址" path="redirectUri" :span="2">
            <n-input v-model:value="appForm.redirectUri" placeholder="OAuth 登录回调地址，须与平台侧登记一致" />
          </n-form-item-gi>
          <n-form-item-gi label="授权范围" path="scope">
            <n-input v-model:value="appForm.scope" placeholder="如 snsapi_base / user_info，多个用逗号分隔" />
          </n-form-item-gi>
          <n-form-item-gi label="状态" path="status">
            <n-select v-model:value="appForm.status" :options="appStatusOptions" />
          </n-form-item-gi>
          <n-form-item-gi label="备注" path="remark" :span="2">
            <n-input v-model:value="appForm.remark" type="textarea" :rows="2" placeholder="备注说明" />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="appModalVisible = false">
            取消
          </n-button>
          <n-button type="primary" :loading="appSubmitLoading" @click="handleSubmitApp">
            保存
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 能力绑定 -->
    <n-modal
      v-model:show="bindModalVisible"
      title="绑定能力"
      preset="card"
      style="width: 480px"
      :mask-closable="false"
    >
      <n-form label-placement="left" label-width="100px">
        <n-form-item label="业务能力">
          <n-select v-model:value="bindForm.capability" :options="capabilityOptions" placeholder="请选择能力" />
        </n-form-item>
        <n-form-item label="物理应用">
          <n-select v-model:value="bindForm.appConfigId" :options="appSelectOptions" placeholder="请选择应用" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="bindModalVisible = false">
            取消
          </n-button>
          <n-button type="primary" :loading="bindSubmitLoading" @click="handleSubmitBind">
            绑定
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 连通测试 -->
    <n-modal
      v-model:show="testModalVisible"
      title="连通测试"
      preset="card"
      style="width: 420px"
      :mask-closable="false"
    >
      <n-form label-placement="left" label-width="100px">
        <n-form-item label="测试能力">
          <n-select v-model:value="testCapability" :options="capabilityOptions" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="testModalVisible = false">
            取消
          </n-button>
          <n-button type="primary" :loading="testLoading" @click="handleSubmitTest">
            开始测试
          </n-button>
        </n-space>
      </template>
    </n-modal>
    <!-- 消息测试：显式指定接收人发送协同消息，返回逐人投递结果 -->
    <n-modal
      v-model:show="msgTestVisible"
      title="消息测试"
      preset="card"
      style="width: 560px"
      :mask-closable="false"
    >
      <n-form
        ref="msgTestFormRef"
        :model="msgTestForm"
        :rules="msgTestRules"
        label-placement="left"
        label-width="80px"
      >
        <n-form-item label="接收人" path="userIds">
          <UserSelectPicker
            v-model:model-value="msgTestForm.userIds"
            v-model:label-value="msgTestForm.userLabels"
            multiple
            placeholder="请选择测试接收人（最多10人）"
            title="选择测试接收人"
          />
        </n-form-item>
        <n-form-item label="标题" path="title">
          <n-input v-model:value="msgTestForm.title" placeholder="消息标题，可空" />
        </n-form-item>
        <n-form-item label="正文" path="content">
          <n-input v-model:value="msgTestForm.content" type="textarea" :rows="3" placeholder="请输入消息正文" />
        </n-form-item>
      </n-form>
      <n-alert type="info" :show-icon="false" size="small" class="mb-3">
        接收人必须已同步/绑定该连接的外部账号，且在企微应用可见范围内，否则会被平台标记无效。
      </n-alert>
      <n-data-table
        v-if="msgTestResult"
        :columns="msgTestResultColumns"
        :data="msgTestResult.deliveries || []"
        :bordered="true"
        size="small"
      />
      <template #footer>
        <n-space justify="end">
          <n-button @click="msgTestVisible = false">
            关闭
          </n-button>
          <n-button type="primary" :loading="msgTestLoading" @click="handleSubmitMsgTest">
            发送
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'
import { computed, h, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  bindConnectionCapability,
  createConnectionApp,
  deleteConnectionApp,
  getConnectionDetail,
  sendTestMessage,
  testConnection,
  triggerConnectionSync,
  unbindConnectionCapability,
  updateConnectionApp,
} from '@/api/collaboration'
import { AiCrudPage } from '@/components/ai-form'
import UserSelectPicker from '@/components/common/UserSelectPicker.vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'
import { useUserStore } from '@/store'
import { request } from '@/utils'

defineOptions({ name: 'CollaborationConnections' })

/**
 * 企业型平台：具备企业ID、通讯录同步与应用消息能力。
 * 其余平台（Gitee/GitHub/QQ 等）仅支持 OAuth 登录，不具备企业ID/通讯录/消息投递概念。
 */
const ENTERPRISE_PLATFORMS = new Set(['WECHAT_ENTERPRISE', 'DINGTALK', 'DINGTALK_ACCOUNT', 'FEISHU'])
const WECOM_API_BASE_URL = 'https://qyapi.weixin.qq.com'

function isEnterprisePlatform(platform) {
  return ENTERPRISE_PLATFORMS.has(platform)
}

const crudRef = ref(null)
const route = useRoute()
const userStore = useUserStore()

// 超管直接放行；普通用户按登录权限 + 当前路由按钮编码判断（v-permission 指令无超管旁路，不适用弹窗内按钮）
const canManageConnection = computed(() => {
  if (userStore.isAdmin)
    return true
  const routeBtns = (route.meta?.btns || []).map(item => item.code)
  const grants = new Set([...(userStore.permissions || []), ...routeBtns])
  return grants.has('system:collaboration:connection:update')
    || grants.has('**')
    || grants.has('*:*:*')
})

const { dict, getLabel } = useDict(
  'sys_collab_platform',
  'sys_collab_capability',
  'sys_collab_identity_policy',
  'sys_collab_directory_authority',
  'sys_collab_connection_type',
  'sys_normal_disable',
)

const platformOptions = computed(() => dict.value.sys_collab_platform || [])
const identityPolicyOptions = computed(() => dict.value.sys_collab_identity_policy || [])
const directoryAuthorityOptions = computed(() => dict.value.sys_collab_directory_authority || [])
const connectionTypeOptions = computed(() => dict.value.sys_collab_connection_type || [])
const statusOptions = computed(() => dict.value.sys_normal_disable || [])
// 一期只放开 LOGIN/DIRECTORY/MESSAGE，TODO 待办联动二期开放
const capabilityOptions = computed(() =>
  (dict.value.sys_collab_capability || []).filter(item => item.value !== 'TODO'),
)

// ==================== 角色选项（默认角色配置用） ====================
const roleOptions = ref([])

async function loadRoleOptions() {
  try {
    const res = await request.get('/system/role/page', {
      params: { pageNum: 1, pageSize: 200 },
    })
    if (res.code === 200) {
      roleOptions.value = (res.data?.records || []).map(item => ({
        label: item.roleName,
        value: item.id,
      }))
    }
  }
  catch (e) {
    console.warn('加载角色列表失败:', e)
  }
}
loadRoleOptions()

/**
 * 格式化默认角色：逗号分隔的角色ID字符串 → 角色名称列表
 */
function formatDefaultRoles(roleIdsStr) {
  if (!roleIdsStr)
    return '跟随全局配置'
  const ids = roleIdsStr.split(',').map(id => Number(id.trim())).filter(Boolean)
  if (ids.length === 0)
    return '跟随全局配置'
  const names = ids.map((id) => {
    const role = roleOptions.value.find(r => r.value === id)
    return role ? role.label : `ID:${id}`
  })
  return names.join('、')
}

// ==================== 主表 ====================

const searchSchema = computed(() => [
  {
    field: 'platform',
    label: '平台',
    type: 'select',
    props: { placeholder: '请选择平台', options: platformOptions.value, clearable: true },
  },
  {
    field: 'connectionName',
    label: '连接名称',
    type: 'input',
    props: { placeholder: '请输入连接名称' },
  },
  {
    field: 'status',
    label: '状态',
    type: 'select',
    props: { placeholder: '请选择', options: statusOptions.value, clearable: true },
  },
])

const tableColumns = computed(() => [
  {
    prop: 'platform',
    label: '平台',
    width: 110,
    render: row => h(DictTag, { dictType: 'sys_collab_platform', value: row.platform, size: 'small' }),
  },
  { prop: 'connectionName', label: '连接名称', minWidth: 140, showOverflowTooltip: true },
  { prop: 'connectionCode', label: '连接编码', width: 140, showOverflowTooltip: true },
  { prop: 'enterpriseId', label: '企业ID', width: 180, showOverflowTooltip: true },
  {
    prop: 'identityPolicy',
    label: '身份策略',
    width: 120,
    render: row => h(DictTag, { dictType: 'sys_collab_identity_policy', value: row.identityPolicy, size: 'small' }),
  },
  {
    prop: 'directoryAuthority',
    label: '目录权威',
    width: 100,
    render: row => h(DictTag, { dictType: 'sys_collab_directory_authority', value: row.directoryAuthority, size: 'small' }),
  },
  {
    prop: 'status',
    label: '状态',
    width: 80,
    render: row => h(DictTag, { dictType: 'sys_normal_disable', value: String(row.status ?? ''), size: 'small' }),
  },
  { prop: 'createTime', label: '创建时间', width: 160 },
  {
    prop: 'action',
    label: '操作',
    width: 330,
    fixed: 'right',
    actions: [
      { label: '详情', key: 'view', type: 'info', onClick: handleView },
      { label: '编辑', key: 'edit', type: 'primary', onClick: row => crudRef.value?.showEdit(row) },
      { label: '应用管理', key: 'manage', type: 'primary', onClick: handleManage },
      {
        label: '测试',
        key: 'test',
        type: 'primary',
        // 连通测试依赖 AccessToken 换取，纯 OAuth 登录平台无该能力
        visible: row => isEnterprisePlatform(row.platform),
        onClick: handleOpenTest,
      },
      {
        label: '同步',
        key: 'sync',
        type: 'primary',
        visible: row => isEnterprisePlatform(row.platform) && row.directoryAuthority === 'EXTERNAL',
        onClick: handleTriggerSync,
      },
      {
        label: '消息测试',
        key: 'msgTest',
        type: 'info',
        // 消息推送走应用 Token，仅企业型平台具备该能力
        visible: row => isEnterprisePlatform(row.platform),
        onClick: handleOpenMsgTest,
      },
      { label: '删除', key: 'delete', type: 'error', onClick: handleDelete },
    ],
  },
])

const editSchema = computed(() => [
  { type: 'divider', label: '基础信息', props: { titlePlacement: 'left' }, span: 2 },
  {
    field: 'platform',
    label: '平台',
    type: 'select',
    rules: [{ required: true, message: '请选择平台', trigger: 'change' }],
    props: { options: platformOptions.value, clearable: false },
  },
  {
    field: 'platformName',
    label: '平台显示名称',
    type: 'input',
    rules: [{ required: true, message: '请输入平台显示名称', trigger: 'blur' }],
    props: { placeholder: '如：企业微信' },
  },
  {
    field: 'connectionCode',
    label: '连接编码',
    type: 'input',
    rules: [{ required: true, message: '请输入连接编码', trigger: 'blur' }],
    props: { placeholder: '全局唯一，如 wecom-main' },
  },
  {
    field: 'connectionName',
    label: '连接名称',
    type: 'input',
    rules: [{ required: true, message: '请输入连接名称', trigger: 'blur' }],
    props: { placeholder: '如：XX科技企业微信' },
  },
  {
    field: 'enterpriseId',
    label: '外部企业ID',
    type: 'input',
    props: { placeholder: '企业平台必填（企微 CorpId）；纯 OAuth 登录平台可留空' },
  },
  {
    field: 'connectionType',
    label: '连接类型',
    type: 'select',
    defaultValue: 'CORP_INTERNAL',
    props: { options: connectionTypeOptions.value, clearable: false },
  },
  { type: 'divider', label: '目录与身份', props: { titlePlacement: 'left' }, span: 2 },
  {
    field: 'identityPolicy',
    label: '身份匹配策略',
    type: 'select',
    defaultValue: 'BIND_ONLY',
    rules: [{ required: true, message: '请选择身份匹配策略', trigger: 'change' }],
    props: { options: identityPolicyOptions.value, clearable: false },
  },
  {
    field: 'defaultRoleIds',
    label: '默认角色',
    type: 'select',
    span: 2,
    props: {
      options: roleOptions.value,
      multiple: true,
      clearable: true,
      placeholder: '自动建号时分配的默认角色（可多选），为空走全局配置',
    },
  },
  {
    field: 'directoryAuthority',
    label: '目录权威来源',
    type: 'select',
    defaultValue: 'NONE',
    rules: [{ required: true, message: '请选择目录权威来源', trigger: 'change' }],
    props: { options: directoryAuthorityOptions.value, clearable: false },
  },
  {
    field: 'defaultOrgId',
    label: '默认挂载组织ID',
    type: 'input',
    props: { placeholder: '目录同步根组织ID，可空' },
  },
  {
    field: 'apiBaseUrl',
    label: 'API基础地址',
    type: 'input',
    span: 2,
    props: { placeholder: '留空使用平台官方地址，私有化部署可自定义' },
  },
  {
    field: 'status',
    label: '状态',
    type: 'select',
    defaultValue: '1',
    rules: [{ required: true, message: '请选择状态', trigger: 'change' }],
    props: { options: statusOptions.value, clearable: false },
  },
  {
    field: 'remark',
    label: '备注',
    type: 'textarea',
    span: 2,
    props: { placeholder: '备注说明', rows: 2 },
  },
])

function handleBeforeRenderDetail(data) {
  if (!data)
    return data
  if (data.status !== null && data.status !== undefined)
    data.status = String(data.status)
  if (data.defaultOrgId !== null && data.defaultOrgId !== undefined)
    data.defaultOrgId = String(data.defaultOrgId)
  // 默认角色：逗号分隔字符串 → 数值数组（供多选组件回显）
  if (data.defaultRoleIds && typeof data.defaultRoleIds === 'string') {
    data.defaultRoleIds = data.defaultRoleIds.split(',').map(id => Number(id.trim())).filter(Boolean)
  }
  else {
    data.defaultRoleIds = []
  }
  return data
}

function handleBeforeSubmit(formData) {
  // 企业型平台依赖企业ID换取凭据，纯 OAuth 登录平台无此概念，故按平台条件校验而非静态必填
  if (isEnterprisePlatform(formData.platform) && !formData.enterpriseId) {
    window.$message.warning('该平台为企业型连接，请填写外部企业ID')
    return false
  }
  if (formData.status !== null && formData.status !== undefined)
    formData.status = Number(formData.status)
  formData.defaultOrgId = formData.defaultOrgId ? Number(formData.defaultOrgId) : null
  // 默认角色：数值数组 → 逗号分隔字符串（后端存储格式）
  if (Array.isArray(formData.defaultRoleIds) && formData.defaultRoleIds.length > 0) {
    formData.defaultRoleIds = formData.defaultRoleIds.join(',')
  }
  else {
    formData.defaultRoleIds = null
  }
  // 企微留空时兜底官方地址，其余平台交由后端按平台默认地址处理
  if (!formData.apiBaseUrl && formData.platform === 'WECHAT_ENTERPRISE')
    formData.apiBaseUrl = WECOM_API_BASE_URL
  // 纯 OAuth 平台不存在自建/第三方应用形态，统一归一为仅登录连接类型
  if (!isEnterprisePlatform(formData.platform))
    formData.connectionType = 'OAUTH_ONLY'
  return formData
}

function handleDelete(row) {
  crudRef.value?.handleDelete(row)
}

// ==================== 详情与应用管理 ====================

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const currentConnectionId = ref(null)
/** true=应用与能力管理（可增删改），false=只读详情 */
const manageMode = ref(false)

async function loadDetail(id) {
  detailLoading.value = true
  try {
    const res = await getConnectionDetail(id)
    if (res.code === 200)
      detail.value = res.data
  }
  catch {
    window.$message.error('获取连接详情失败')
  }
  finally {
    detailLoading.value = false
  }
}

function handleView(row) {
  manageMode.value = false
  currentConnectionId.value = row.id
  detail.value = null
  detailVisible.value = true
  loadDetail(row.id)
}

function handleManage(row) {
  manageMode.value = true
  currentConnectionId.value = row.id
  detail.value = null
  detailVisible.value = true
  loadDetail(row.id)
}

const appColumns = computed(() => {
  const columns = [
    { title: '应用编码', key: 'appCode', width: 120 },
    { title: '应用名称', key: 'appName', width: 140 },
    { title: 'AgentId', key: 'agentId', width: 100 },
    {
      title: 'Secret',
      key: 'secretMasked',
      width: 120,
      render: row => row.secretConfigured ? (row.secretMasked || '已配置') : '未配置',
    },
    {
      title: '回调凭据',
      key: 'callbackTokenConfigured',
      width: 100,
      render: row => (row.callbackTokenConfigured && row.encodingAesKeyConfigured) ? '已配置' : '未配置',
    },
    {
      title: '状态',
      key: 'status',
      width: 80,
      render: row => h(DictTag, { dictType: 'sys_normal_disable', value: String(row.status ?? ''), size: 'small' }),
    },
  ]
  if (manageMode.value) {
    columns.push({
      title: '操作',
      key: 'action',
      width: 120,
      render: row => h('div', [
        h('a', {
          class: 'text-primary cursor-pointer hover:text-primary-hover mr-3',
          onClick: () => handleEditApp(row),
        }, '编辑'),
        h('a', {
          class: 'text-error cursor-pointer hover:text-error-hover',
          onClick: () => handleDeleteApp(row),
        }, '删除'),
      ]),
    })
  }
  return columns
})

const bindingColumns = computed(() => {
  const columns = [
    {
      title: '业务能力',
      key: 'capability',
      width: 140,
      render: row => h(DictTag, { dictType: 'sys_collab_capability', value: row.capability, size: 'small' }),
    },
    {
      title: '绑定应用',
      key: 'appConfigId',
      render: (row) => {
        const app = (detail.value?.apps || []).find(item => item.id === row.appConfigId)
        return app ? `${app.appName}（${app.appCode}）` : String(row.appConfigId ?? '-')
      },
    },
    {
      title: '状态',
      key: 'status',
      width: 80,
      render: row => h(DictTag, { dictType: 'sys_normal_disable', value: String(row.status ?? ''), size: 'small' }),
    },
  ]
  if (manageMode.value) {
    columns.push({
      title: '操作',
      key: 'action',
      width: 80,
      render: row => h('a', {
        class: 'text-error cursor-pointer hover:text-error-hover',
        onClick: () => handleUnbind(row),
      }, '解绑'),
    })
  }
  return columns
})

const appModalVisible = ref(false)
const appFormRef = ref(null)
const appSubmitLoading = ref(false)
const appForm = ref({})
const appStatusOptions = computed(() =>
  statusOptions.value.map(item => ({ ...item, value: Number(item.value) })),
)

const appFormRules = {
  appCode: [{ required: true, message: '请输入应用编码', trigger: 'blur' }],
  appName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
  clientId: [{ required: true, message: '请输入应用ID/Key', trigger: 'blur' }],
}

function handleAddApp() {
  appForm.value = { status: 1 }
  appModalVisible.value = true
}

function handleEditApp(row) {
  // Secret/Token/AESKey 不回显，留空提交即保留现值
  appForm.value = {
    id: row.id,
    appCode: row.appCode,
    appName: row.appName,
    clientId: row.clientId,
    agentId: row.agentId,
    secret: '',
    callbackToken: '',
    encodingAesKey: '',
    redirectUri: row.redirectUri,
    scope: row.scope,
    status: row.status,
    remark: row.remark,
  }
  appModalVisible.value = true
}

async function handleSubmitApp() {
  try {
    await appFormRef.value?.validate()
  }
  catch {
    return
  }
  appSubmitLoading.value = true
  try {
    const payload = { ...appForm.value }
    const res = payload.id
      ? await updateConnectionApp(currentConnectionId.value, payload)
      : await createConnectionApp(currentConnectionId.value, payload)
    if (res.code === 200) {
      window.$message.success('保存成功')
      appModalVisible.value = false
      loadDetail(currentConnectionId.value)
    }
  }
  catch {
    window.$message.error('保存应用失败')
  }
  finally {
    appSubmitLoading.value = false
  }
}

function handleDeleteApp(row) {
  window.$dialog.warning({
    title: '确认删除',
    content: `确定要删除应用「${row.appName}」吗？被能力绑定引用的应用无法删除。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await deleteConnectionApp(currentConnectionId.value, row.id)
        if (res.code === 200) {
          window.$message.success('删除成功')
          loadDetail(currentConnectionId.value)
        }
      }
      catch {
        window.$message.error('删除应用失败')
      }
    },
  })
}

// ==================== 能力绑定 ====================

const bindModalVisible = ref(false)
const bindSubmitLoading = ref(false)
const bindForm = ref({ capability: null, appConfigId: null })

const appSelectOptions = computed(() =>
  (detail.value?.apps || [])
    .filter(app => app.status === 1)
    .map(app => ({ label: `${app.appName}（${app.appCode}）`, value: app.id })),
)

function handleOpenBind() {
  bindForm.value = { capability: null, appConfigId: null }
  bindModalVisible.value = true
}

async function handleSubmitBind() {
  if (!bindForm.value.capability || !bindForm.value.appConfigId) {
    window.$message.warning('请选择能力与应用')
    return
  }
  bindSubmitLoading.value = true
  try {
    const res = await bindConnectionCapability(currentConnectionId.value, bindForm.value)
    if (res.code === 200) {
      window.$message.success('绑定成功')
      bindModalVisible.value = false
      loadDetail(currentConnectionId.value)
    }
  }
  catch {
    window.$message.error('绑定能力失败')
  }
  finally {
    bindSubmitLoading.value = false
  }
}

function handleUnbind(row) {
  window.$dialog.warning({
    title: '确认解绑',
    content: `确定要解绑「${getLabel('sys_collab_capability', row.capability)}」能力吗？解绑后该能力不可用。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await unbindConnectionCapability(currentConnectionId.value, row.capability)
        if (res.code === 200) {
          window.$message.success('解绑成功')
          loadDetail(currentConnectionId.value)
        }
      }
      catch {
        window.$message.error('解绑失败')
      }
    },
  })
}

// ==================== 连通测试与目录同步 ====================

const testModalVisible = ref(false)
const testLoading = ref(false)
const testCapability = ref('MESSAGE')
const testConnectionId = ref(null)

function handleOpenTest(row) {
  testConnectionId.value = row.id
  testCapability.value = 'MESSAGE'
  testModalVisible.value = true
}

async function handleSubmitTest() {
  testLoading.value = true
  try {
    const res = await testConnection(testConnectionId.value, testCapability.value)
    if (res.code === 200) {
      window.$message.success(res.data || '连通测试通过')
      testModalVisible.value = false
    }
  }
  catch {
    window.$message.error('连通测试失败，请检查凭据与能力绑定')
  }
  finally {
    testLoading.value = false
  }
}

function handleTriggerSync(row) {
  window.$dialog.warning({
    title: '触发全量同步',
    content: `确定要对「${row.connectionName}」执行全量目录同步吗？同步结果可在「同步批次」页查看。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await triggerConnectionSync(row.id, { syncType: 'FULL' })
        if (res.code === 200)
          window.$message.success('同步已完成，请到「同步批次」页查看结果')
      }
      catch {
        window.$message.error('触发同步失败')
      }
    },
  })
}

// ==================== 消息测试 ====================

const msgTestVisible = ref(false)
const msgTestLoading = ref(false)
const msgTestFormRef = ref(null)
const msgTestForm = ref({ connectionId: null, userIds: [], userLabels: [], title: '', content: '' })
const msgTestResult = ref(null)

const msgTestRules = {
  userIds: [{
    validator: () => {
      const ids = msgTestForm.value.userIds || []
      if (!ids.length)
        return new Error('请选择测试接收人')
      if (ids.length > 10)
        return new Error('测试接收人不能超过10人')
      return true
    },
    trigger: 'change',
  }],
  content: [{ required: true, message: '请输入消息正文', trigger: 'blur' }],
}

const msgTestResultColumns = [
  { title: '用户ID', key: 'userId', width: 90 },
  {
    title: '投递状态',
    key: 'status',
    width: 90,
    render: (row) => {
      const type = row.status === 'SENT' ? 'success' : row.status === 'SKIPPED' ? 'warning' : 'error'
      return h(NTag, { type, size: 'small' }, () => row.status)
    },
  },
  {
    title: '失败原因',
    key: 'errorMessage',
    render: row => row.errorMessage || (row.status === 'SENT' ? '-' : row.errorCode || '-'),
  },
]

function handleOpenMsgTest(row) {
  msgTestForm.value = {
    connectionId: row.id,
    userIds: [],
    userLabels: [],
    title: 'Forge 推送测试',
    content: '',
  }
  msgTestResult.value = null
  msgTestVisible.value = true
}

async function handleSubmitMsgTest() {
  try {
    await msgTestFormRef.value?.validate()
  }
  catch {
    return
  }
  msgTestLoading.value = true
  msgTestResult.value = null
  try {
    const { connectionId, userIds, title, content } = msgTestForm.value
    const res = await sendTestMessage({ connectionId, userIds, title, content })
    if (res.code === 200) {
      msgTestResult.value = res.data
      const deliveries = res.data?.deliveries || []
      const sent = deliveries.filter(item => item.status === 'SENT').length
      if (sent === deliveries.length && sent > 0)
        window.$message.success('全部发送成功，请在企微客户端查看')
      else
        window.$message.warning(`发送完成：成功 ${sent}/${deliveries.length}，失败原因见下方明细`)
    }
  }
  catch {
    window.$message.error('消息测试发送失败')
  }
  finally {
    msgTestLoading.value = false
  }
}
</script>

<style scoped>
.collaboration-connections-page {
  height: 100%;
}

.detail-content {
  padding: 4px 0;
}
</style>
