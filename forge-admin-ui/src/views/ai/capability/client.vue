<template>
  <div class="capability-client-page">
    <AiCrudPage
      ref="crudRef"
      :api-config="{
        list: 'get@/ai/capability/client/page',
      }"
      :search-schema="searchSchema"
      :columns="tableColumns"
      row-key="id"
      :hide-add="true"
      :hide-selection="true"
      :hide-batch-delete="true"
    >
      <template #toolbar-start>
        <n-button v-if="canAdd" type="primary" @click="openAddModal">
          新增客户端
        </n-button>
      </template>
    </AiCrudPage>

    <!-- 新增客户端弹窗 -->
    <n-modal
      v-model:show="addVisible"
      title="新增能力客户端"
      preset="card"
      style="width: 560px"
    >
      <n-form
        ref="addFormRef"
        :model="addForm"
        :rules="addRules"
        label-placement="left"
        label-width="110px"
      >
        <n-form-item label="客户端编码" path="clientCode">
          <n-input v-model:value="addForm.clientCode" placeholder="如 erp-sync，创建后不可修改" />
        </n-form-item>
        <n-form-item label="客户端名称" path="clientName">
          <n-input v-model:value="addForm.clientName" placeholder="请输入客户端名称" />
        </n-form-item>
        <n-form-item label="主体模式" path="actorMode">
          <n-select
            v-model:value="addForm.actorMode"
            :options="actorModeOptions"
            placeholder="请选择主体模式"
          />
        </n-form-item>
        <n-alert v-if="!actorModeOptions.length" type="error" class="form-alert">
          主体模式字典尚未初始化，请先确认 Flyway V1.0.76 已成功执行。
        </n-alert>
        <n-form-item v-if="requiresServiceIdentity" label="服务账号" path="serviceUserId">
          <UserSelectPicker
            v-model="addForm.serviceUserId"
            v-model:label-value="serviceUserLabel"
            title="选择机器客户端服务账号"
            placeholder="请选择服务账号"
            :clearable="false"
            @select="handleServiceUserSelect"
          />
        </n-form-item>
        <n-form-item v-if="requiresServiceIdentity" label="生效组织" path="activeOrgId">
          <n-select
            v-model:value="addForm.activeOrgId"
            :options="serviceOrgOptions"
            :loading="serviceOrgLoading"
            :disabled="!addForm.serviceUserId"
            placeholder="请选择服务账号所属组织"
            filterable
          >
            <template #empty>
              <n-empty size="small" description="该账号未绑定可用组织" />
            </template>
          </n-select>
        </n-form-item>
        <n-form-item label="认证模式" path="authModes">
          <n-select
            v-model:value="addForm.authModes"
            multiple
            placeholder="默认 OAUTH"
            :options="authModeOptions"
            :disabled="addForm.actorMode === 'USER_DELEGATION'"
            clearable
          />
        </n-form-item>
        <n-alert v-if="!authModeOptions.length" type="error" class="form-alert">
          认证模式字典尚未初始化，请先确认 Flyway V1.0.74 已成功执行。
        </n-alert>
        <n-form-item label="过期时间" path="expiresAt">
          <n-date-picker
            v-model:value="addForm.expiresAt"
            type="datetime"
            placeholder="不填则长期有效"
            clearable
            class="w-full"
          />
        </n-form-item>
        <n-form-item label="备注" path="remark">
          <n-input
            v-model:value="addForm.remark"
            type="textarea"
            placeholder="请输入备注"
            :rows="2"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="addVisible = false">
            取消
          </n-button>
          <n-button
            type="primary"
            :loading="addLoading"
            :disabled="!authModeOptions.length || !actorModeOptions.length"
            @click="handleAddSubmit"
          >
            创建
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 客户端签名用户断言配置 -->
    <n-modal
      v-model:show="userAssertionVisible"
      preset="card"
      title="用户身份断言"
      class="user-assertion-modal"
      style="width: min(920px, calc(100vw - 32px))"
      :mask-closable="false"
      @after-leave="clearUserAssertionState"
    >
      <n-spin :show="userAssertionLoading">
        <template v-if="userAssertionConfig">
          <div class="assertion-heading">
            <div>
              <strong>{{ userAssertionConfig.clientName }}</strong>
              <span>{{ userAssertionConfig.clientCode }} · AppId {{ userAssertionConfig.clientId }}</span>
            </div>
            <n-tag :type="userAssertionConfig.enabled ? 'success' : 'default'" size="small">
              {{ userAssertionConfig.enabled ? '已启用' : '未启用' }}
            </n-tag>
          </div>

          <n-alert type="info" :show-icon="true" class="assertion-alert">
            外围系统使用当前客户端私钥签发最长 {{ userAssertionConfig.maxTtlSeconds }} 秒的 RS256 JWT，Forge 仅接受已预绑定的普通用户。
          </n-alert>

          <div class="assertion-protocol-grid">
            <div>
              <span>Issuer</span>
              <code>{{ userAssertionConfig.issuer }}</code>
            </div>
            <div>
              <span>Audience</span>
              <code>{{ userAssertionConfig.audience }}</code>
            </div>
            <div class="protocol-wide">
              <span>Subject Token Type</span>
              <code>{{ userAssertionConfig.subjectTokenType }}</code>
            </div>
            <div>
              <span>当前 kid</span>
              <code>{{ userAssertionConfig.keyId || '尚未生成' }}</code>
            </div>
            <div>
              <span>密钥版本</span>
              <code>{{ userAssertionConfig.keyVersion ? `v${userAssertionConfig.keyVersion}` : '-' }}</code>
            </div>
          </div>

          <div class="assertion-actions">
            <n-button type="primary" :loading="userAssertionRotating" @click="handleRotateUserAssertionKey">
              <template #icon>
                <i class="i-material-symbols:key-vertical-outline-rounded" />
              </template>
              {{ userAssertionConfig.keyVersion ? '轮换 RSA 密钥' : '生成 RSA 密钥' }}
            </n-button>
            <n-button
              v-if="userAssertionConfig.enabled"
              type="warning"
              :loading="userAssertionDisabling"
              @click="handleDisableUserAssertion"
            >
              <template #icon>
                <i class="i-material-symbols:pause-circle-outline-rounded" />
              </template>
              停用用户断言
            </n-button>
          </div>

          <section class="mapping-section">
            <div class="mapping-heading">
              <div>
                <h3>外围用户映射</h3>
                <p>原始外围用户标识只用于生成 SHA-256 指纹，不会保存到数据库。</p>
              </div>
            </div>
            <n-form
              ref="mappingFormRef"
              :model="mappingForm"
              :rules="mappingRules"
              label-placement="top"
              class="mapping-form"
            >
              <n-form-item label="外围用户标识（JWT sub）" path="externalSubject">
                <n-input
                  v-model:value="mappingForm.externalSubject"
                  maxlength="512"
                  show-count
                  placeholder="请输入外围系统中稳定且唯一的用户标识"
                />
              </n-form-item>
              <n-form-item label="Forge 普通用户" path="userId">
                <UserSelectPicker
                  v-model="mappingForm.userId"
                  v-model:label-value="mappingUserLabel"
                  title="选择要绑定的 Forge 普通用户"
                  placeholder="请选择已分配组织和角色的普通用户"
                  :clearable="true"
                />
              </n-form-item>
              <n-button type="primary" :loading="mappingSubmitting" @click="handleAddUserAssertionMapping">
                <template #icon>
                  <i class="i-material-symbols:link-rounded" />
                </template>
                添加映射
              </n-button>
            </n-form>

            <n-data-table
              :columns="mappingColumns"
              :data="userAssertionConfig.mappings || []"
              :row-key="row => row.id"
              :bordered="false"
              size="small"
              class="mapping-table"
            />
          </section>
        </template>
      </n-spin>
    </n-modal>

    <!-- 一次性凭据展示弹窗 -->
    <n-modal
      v-model:show="issuedVisible"
      preset="card"
      :title="issuedCredential?.title || '凭据已生成'"
      style="width: min(640px, calc(100vw - 32px))"
      :mask-closable="false"
      :closable="false"
      @after-leave="clearIssuedCredential"
    >
      <n-alert type="warning" :show-icon="true">
        以下凭据仅展示一次。关闭前请完成保存，之后无法从系统再次查看。
      </n-alert>

      <div v-if="issuedCredential?.clientSecret" class="issued-block">
        <div class="issued-label">
          客户端密钥（clientSecret）
        </div>
        <div class="issued-value">
          <code>{{ issuedCredential.clientSecret }}</code>
          <n-button quaternary circle aria-label="复制客户端密钥" @click="copy(issuedCredential.clientSecret, '客户端密钥已复制')">
            <template #icon>
              <i class="i-material-symbols:content-copy-outline-rounded" />
            </template>
          </n-button>
        </div>
      </div>

      <div v-if="issuedCredential?.signingKey" class="issued-block">
        <div class="issued-label">
          签名密钥（signingKey）
        </div>
        <div class="issued-value">
          <code>{{ issuedCredential.signingKey }}</code>
          <n-button quaternary circle aria-label="复制签名密钥" @click="copy(issuedCredential.signingKey, '签名密钥已复制')">
            <template #icon>
              <i class="i-material-symbols:content-copy-outline-rounded" />
            </template>
          </n-button>
        </div>
      </div>

      <div v-if="issuedCredential?.privateKeyPem" class="issued-block">
        <div class="issued-label">
          用户断言私钥（PKCS#8 PEM）
        </div>
        <n-input
          :value="issuedCredential.privateKeyPem"
          type="textarea"
          readonly
          :autosize="{ minRows: 8, maxRows: 14 }"
          class="private-key-value"
        />
        <n-space class="private-key-actions">
          <n-button size="small" @click="copy(issuedCredential.privateKeyPem, '用户断言私钥已复制')">
            <template #icon>
              <i class="i-material-symbols:content-copy-outline-rounded" />
            </template>
            复制私钥
          </n-button>
          <n-button size="small" @click="downloadIssuedPrivateKey">
            <template #icon>
              <i class="i-material-symbols:download-rounded" />
            </template>
            下载 PEM
          </n-button>
        </n-space>
      </div>

      <div class="issued-meta">
        <span>客户端 ID / AppId</span>
        <strong>{{ issuedCredential?.clientId || '-' }}</strong>
        <span>客户端编码</span>
        <strong>{{ issuedCredential?.clientCode || '-' }}</strong>
        <template v-if="issuedCredential?.keyPrefix">
          <span>凭据前缀</span>
          <strong>{{ issuedCredential.keyPrefix }}</strong>
        </template>
        <template v-if="issuedCredential?.credentialVersion != null">
          <span>密钥版本</span>
          <strong>v{{ issuedCredential.credentialVersion }}</strong>
        </template>
        <template v-if="issuedCredential?.signingKeyVersion != null">
          <span>签名密钥版本</span>
          <strong>v{{ issuedCredential.signingKeyVersion }}</strong>
        </template>
        <template v-if="issuedCredential?.keyId">
          <span>用户断言 kid</span>
          <strong>{{ issuedCredential.keyId }}</strong>
        </template>
        <template v-if="issuedCredential?.keyVersion != null">
          <span>用户断言密钥版本</span>
          <strong>v{{ issuedCredential.keyVersion }}</strong>
        </template>
        <template v-if="issuedCredential?.audience">
          <span>Audience</span>
          <strong>{{ issuedCredential.audience }}</strong>
          <span>最大有效期</span>
          <strong>{{ issuedCredential.maxTtlSeconds }} 秒</strong>
        </template>
      </div>

      <template #footer>
        <n-space justify="end">
          <n-button type="primary" @click="issuedVisible = false">
            我已安全保存
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { computed, h, reactive, ref, watch } from 'vue'
import {
  addClientUserAssertionMapping,
  addCapabilityClient,
  disableClientUserAssertion,
  getClientUserAssertionConfig,
  removeClientUserAssertionMapping,
  revokeCapabilityClient,
  rotateClientUserAssertionKey,
  rotateCapabilityClientSecret,
  rotateCapabilityClientSigningKey,
} from '@/api/ai/capability'
import { AiCrudPage } from '@/components/ai-form'
import UserSelectPicker from '@/components/common/UserSelectPicker.vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables'
import { useUserStore } from '@/store'
import { formatDateTime, request } from '@/utils'
import { copy } from '@/utils/clipboard'

defineOptions({ name: 'CapabilityClient' })

const userStore = useUserStore()
const { dict } = useDict(
  'ai_capability_client_status',
  'ai_capability_auth_mode',
  'ai_capability_client_actor_mode',
)

const clientStatusOptions = computed(() => dict.value.ai_capability_client_status || [])
const authModeOptions = computed(() => dict.value.ai_capability_auth_mode || [])
const actorModeOptions = computed(() => dict.value.ai_capability_client_actor_mode || [])

function hasPermission(permission) {
  if (userStore?.isAdmin)
    return true
  const permissions = Array.isArray(userStore?.permissions) ? userStore.permissions : []
  return permissions.includes(permission) || permissions.includes('*:*:*')
}

const canAdd = computed(() => hasPermission('ai:capability:client:add'))
const canRotate = computed(() => hasPermission('ai:capability:client:rotate'))
const canEdit = computed(() => hasPermission('ai:capability:client:edit'))
const canRevoke = computed(() => hasPermission('ai:capability:client:revoke'))

const crudRef = ref(null)

// ===== 新增客户端 =====
const addVisible = ref(false)
const addLoading = ref(false)
const addFormRef = ref(null)
const serviceUserLabel = ref('')
const serviceOrgOptions = ref([])
const serviceOrgLoading = ref(false)
const addForm = reactive({
  clientCode: '',
  clientName: '',
  actorMode: 'USER_DELEGATION',
  serviceUserId: null,
  activeOrgId: null,
  authModes: [],
  expiresAt: null,
  remark: '',
})

const addRules = {
  clientCode: { required: true, message: '请输入客户端编码', trigger: 'blur' },
  clientName: { required: true, message: '请输入客户端名称', trigger: 'blur' },
  actorMode: { required: true, message: '请选择主体模式', trigger: 'change' },
  serviceUserId: conditionalSelectedIdRule('请选择服务账号'),
  activeOrgId: conditionalSelectedIdRule('请选择生效组织'),
  authModes: {
    trigger: 'change',
    validator: (_rule, value) => validateAuthModes(value),
  },
}

const requiresServiceIdentity = computed(() => ['SERVICE', 'HYBRID'].includes(addForm.actorMode))

function conditionalSelectedIdRule(message) {
  return {
    trigger: 'change',
    validator: (_rule, value) => !requiresServiceIdentity.value || isPositiveId(value)
      ? true
      : new Error(message),
  }
}

function validateAuthModes(value) {
  if (!Array.isArray(value) || value.length === 0)
    return new Error('请至少选择一种认证模式')
  if (addForm.actorMode === 'USER_DELEGATION' && (value.length !== 1 || value[0] !== 'OAUTH'))
    return new Error('用户委托模式只支持 OAUTH')
  if (addForm.actorMode === 'HYBRID' && !value.includes('OAUTH'))
    return new Error('混合模式必须启用 OAUTH')
  return true
}

function isPositiveId(value) {
  if (typeof value === 'number')
    return Number.isInteger(value) && value > 0
  return typeof value === 'string' && /^[1-9]\d*$/.test(value)
}

watch(authModeOptions, (options) => {
  if (!addVisible.value || addForm.authModes.length > 0 || options.length === 0)
    return
  addForm.authModes = resolveDefaultAuthModes()
})

watch(() => addForm.actorMode, (actorMode) => {
  if (actorMode === 'USER_DELEGATION') {
    addForm.serviceUserId = null
    addForm.activeOrgId = null
    addForm.authModes = ['OAUTH']
    serviceUserLabel.value = ''
    serviceOrgOptions.value = []
  }
})

function openAddModal() {
  Object.assign(addForm, {
    clientCode: '',
    clientName: '',
    actorMode: 'USER_DELEGATION',
    serviceUserId: null,
    activeOrgId: null,
    authModes: [],
    expiresAt: null,
    remark: '',
  })
  serviceUserLabel.value = ''
  serviceOrgOptions.value = []
  addForm.authModes = ['OAUTH']
  addVisible.value = true
}

function resolveDefaultAuthModes() {
  const defaults = authModeOptions.value
    .filter(item => item.isDefault === 'Y')
    .map(item => item.value)
  return defaults.length ? defaults : authModeOptions.value.slice(0, 1).map(item => item.value)
}

async function handleServiceUserSelect(user) {
  addForm.activeOrgId = null
  serviceOrgOptions.value = []
  if (!user?.id)
    return

  serviceOrgLoading.value = true
  try {
    const res = await request.get(`/system/user/${user.id}/org-bindings`)
    serviceOrgOptions.value = uniqueOrganizationBindings(res.data || [])
      .map(item => ({
        label: item.orgName || `组织 ${item.orgId}`,
        value: item.orgId,
        isMain: item.isMain,
      }))
    const defaultOrg = serviceOrgOptions.value.find(item => item.isMain === 1)
      || serviceOrgOptions.value[0]
    addForm.activeOrgId = defaultOrg?.value || null
    if (!defaultOrg)
      window.$message.warning('所选账号未绑定可用组织')
  }
  catch (error) {
    window.$message.error(error?.message || '服务账号组织加载失败')
  }
  finally {
    serviceOrgLoading.value = false
  }
}

function uniqueOrganizationBindings(bindings) {
  const unique = new Map()
  bindings.forEach((item) => {
    if (item.orgId && !unique.has(item.orgId))
      unique.set(item.orgId, item)
  })
  return [...unique.values()]
}

async function handleAddSubmit() {
  try {
    await addFormRef.value?.validate()
  }
  catch {
    return
  }
  addLoading.value = true
  try {
    const res = await addCapabilityClient({
      clientCode: addForm.clientCode,
      clientName: addForm.clientName,
      actorMode: addForm.actorMode,
      serviceUserId: requiresServiceIdentity.value ? addForm.serviceUserId : null,
      activeOrgId: requiresServiceIdentity.value ? addForm.activeOrgId : null,
      authModes: addForm.authModes.length ? addForm.authModes.join(',') : null,
      expiresAt: addForm.expiresAt ? formatDateTime(addForm.expiresAt) : null,
      remark: addForm.remark || null,
    })
    if (res.code === 200) {
      addVisible.value = false
      showIssuedCredential('客户端已创建', res.data)
      crudRef.value?.refresh()
    }
  }
  finally {
    addLoading.value = false
  }
}

// ===== 一次性凭据展示 =====
const issuedVisible = ref(false)
const issuedCredential = ref(null)

function showIssuedCredential(title, data) {
  if (!data)
    return
  issuedCredential.value = { ...data, title }
  issuedVisible.value = true
}

function clearIssuedCredential() {
  issuedCredential.value = null
}

function downloadIssuedPrivateKey() {
  const privateKey = issuedCredential.value?.privateKeyPem
  if (!privateKey)
    return
  const clientCode = issuedCredential.value?.clientCode || 'forge-client'
  const url = URL.createObjectURL(new Blob([privateKey], { type: 'application/x-pem-file' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `${clientCode}-user-assertion-private-key.pem`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

// ===== 客户端签名用户断言 =====
const userAssertionVisible = ref(false)
const userAssertionLoading = ref(false)
const userAssertionRotating = ref(false)
const userAssertionDisabling = ref(false)
const currentUserAssertionClient = ref(null)
const userAssertionConfig = ref(null)
const mappingFormRef = ref(null)
const mappingSubmitting = ref(false)
const mappingUserLabel = ref('')
const mappingForm = reactive({
  externalSubject: '',
  userId: null,
})
const mappingRules = {
  externalSubject: [
    { required: true, message: '请输入外围用户标识', trigger: ['blur', 'input'] },
    { max: 512, message: '外围用户标识长度不能超过512个字符', trigger: ['blur', 'input'] },
  ],
  userId: {
    trigger: 'change',
    validator: (_rule, value) => isPositiveId(value) ? true : new Error('请选择 Forge 普通用户'),
  },
}

const mappingColumns = computed(() => [
  {
    title: '外围用户标识',
    key: 'subjectHint',
    minWidth: 150,
    render: row => row.subjectHint || `指纹 ${row.subjectHashPrefix || '-'}`,
  },
  {
    title: 'Forge 用户',
    key: 'userId',
    minWidth: 180,
    render: row => `${row.realName || row.username || '-'}（${row.username || row.userId}）`,
  },
  {
    title: '最近认证',
    key: 'lastAuthenticatedAt',
    width: 170,
    render: row => row.lastAuthenticatedAt || '尚未认证',
  },
  {
    title: '绑定时间',
    key: 'createTime',
    width: 170,
    render: row => row.createTime || '-',
  },
  {
    title: '操作',
    key: 'action',
    width: 80,
    fixed: 'right',
    render: row => h('a', {
      class: 'text-error cursor-pointer hover:text-error-hover',
      onClick: () => handleRemoveUserAssertionMapping(row),
    }, '解除'),
  },
])

async function openUserAssertion(row) {
  currentUserAssertionClient.value = row
  userAssertionVisible.value = true
  await loadUserAssertionConfig()
}

async function loadUserAssertionConfig() {
  const clientId = currentUserAssertionClient.value?.id
  if (!clientId)
    return
  userAssertionLoading.value = true
  try {
    const res = await getClientUserAssertionConfig(clientId)
    userAssertionConfig.value = res.data || null
  }
  catch (error) {
    window.$message.error(error?.message || '用户断言配置加载失败')
    userAssertionVisible.value = false
  }
  finally {
    userAssertionLoading.value = false
  }
}

function clearUserAssertionState() {
  currentUserAssertionClient.value = null
  userAssertionConfig.value = null
  resetMappingForm()
}

function resetMappingForm() {
  mappingForm.externalSubject = ''
  mappingForm.userId = null
  mappingUserLabel.value = ''
  mappingFormRef.value?.restoreValidation?.()
}

function handleRotateUserAssertionKey() {
  const config = userAssertionConfig.value
  if (!config)
    return
  window.$dialog.warning({
    title: config.keyVersion ? '轮换用户断言密钥' : '生成用户断言密钥',
    content: config.keyVersion
      ? '轮换后旧私钥签发的断言和当前客户端短期令牌将立即失效。新私钥只展示一次，是否继续？'
      : '系统将生成独立 RSA-2048 密钥对，私钥只展示一次。是否继续？',
    positiveText: config.keyVersion ? '确认轮换' : '确认生成',
    negativeText: '取消',
    onPositiveClick: async () => {
      userAssertionRotating.value = true
      try {
        const res = await rotateClientUserAssertionKey(config.clientId)
        if (res.code === 200) {
          showIssuedCredential('用户断言私钥已生成', res.data)
          await loadUserAssertionConfig()
          crudRef.value?.refresh()
        }
      }
      finally {
        userAssertionRotating.value = false
      }
    },
  })
}

function handleDisableUserAssertion() {
  const config = userAssertionConfig.value
  if (!config)
    return
  window.$dialog.warning({
    title: '停用用户断言',
    content: '停用后外围系统不能再用客户端签名 JWT 换取令牌，已签发的当前客户端短期令牌也会失效。用户映射会保留，便于以后重新启用。',
    positiveText: '确认停用',
    negativeText: '取消',
    onPositiveClick: async () => {
      userAssertionDisabling.value = true
      try {
        const res = await disableClientUserAssertion(config.clientId)
        if (res.code === 200) {
          window.$message.success('用户断言已停用')
          await loadUserAssertionConfig()
          crudRef.value?.refresh()
        }
      }
      finally {
        userAssertionDisabling.value = false
      }
    },
  })
}

async function handleAddUserAssertionMapping() {
  try {
    await mappingFormRef.value?.validate()
  }
  catch {
    return
  }
  const clientId = userAssertionConfig.value?.clientId
  if (!clientId)
    return
  mappingSubmitting.value = true
  try {
    const res = await addClientUserAssertionMapping(clientId, {
      externalSubject: mappingForm.externalSubject.trim(),
      userId: mappingForm.userId,
    })
    if (res.code === 200) {
      window.$message.success('外围用户映射已保存')
      resetMappingForm()
      await loadUserAssertionConfig()
    }
  }
  finally {
    mappingSubmitting.value = false
  }
}

function handleRemoveUserAssertionMapping(row) {
  const clientId = userAssertionConfig.value?.clientId
  if (!clientId)
    return
  window.$dialog.warning({
    title: '解除用户映射',
    content: `确定解除「${row.subjectHint || row.subjectHashPrefix}」与 Forge 用户的映射吗？该外围用户之后将无法换取令牌。`,
    positiveText: '确认解除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const res = await removeClientUserAssertionMapping(clientId, row.id)
      if (res.code === 200) {
        window.$message.success('用户映射已解除')
        await loadUserAssertionConfig()
      }
    },
  })
}

// ===== 轮换 / 吊销 =====
function handleRotateSecret(row) {
  window.$dialog.warning({
    title: '轮换密钥确认',
    content: `确定轮换客户端「${row.clientName}」的密钥吗？旧密钥将立即失效，新密钥仅展示一次。`,
    positiveText: '确定轮换',
    negativeText: '取消',
    onPositiveClick: async () => {
      const res = await rotateCapabilityClientSecret(row.id)
      if (res.code === 200) {
        showIssuedCredential('客户端密钥已轮换', res.data)
        crudRef.value?.refresh()
      }
    },
  })
}

function handleRotateSigningKey(row) {
  window.$dialog.warning({
    title: '轮换签名密钥确认',
    content: `确定轮换客户端「${row.clientName}」的签名密钥吗？旧签名密钥将立即失效，新密钥仅展示一次。`,
    positiveText: '确定轮换',
    negativeText: '取消',
    onPositiveClick: async () => {
      const res = await rotateCapabilityClientSigningKey(row.id)
      if (res.code === 200) {
        showIssuedCredential('签名密钥已轮换', res.data)
        crudRef.value?.refresh()
      }
    },
  })
}

function handleRevoke(row) {
  window.$dialog.warning({
    title: '吊销确认',
    content: `确定吊销客户端「${row.clientName}」吗？吊销后该客户端所有凭据立即失效且不可恢复。`,
    positiveText: '确定吊销',
    negativeText: '取消',
    onPositiveClick: async () => {
      const res = await revokeCapabilityClient(row.id)
      if (res.code === 200) {
        window.$message.success('客户端已吊销')
        crudRef.value?.refresh()
      }
    },
  })
}

// ===== 搜索与表格 =====
const searchSchema = computed(() => [
  {
    field: 'keyword',
    label: '关键字',
    type: 'input',
    props: {
      placeholder: '客户端编码/名称',
    },
  },
  {
    field: 'status',
    label: '状态',
    type: 'select',
    props: {
      placeholder: '请选择状态',
      clearable: true,
      options: clientStatusOptions.value,
    },
  },
])

const tableColumns = computed(() => [
  {
    prop: 'clientCode',
    label: '客户端编码',
    width: 150,
    ellipsis: { tooltip: true },
  },
  {
    prop: 'clientName',
    label: '客户端名称',
    minWidth: 140,
    ellipsis: { tooltip: true },
  },
  {
    prop: 'id',
    label: '客户端 ID / AppId',
    width: 150,
  },
  {
    prop: 'keyPrefix',
    label: '凭据前缀',
    width: 130,
    ellipsis: { tooltip: true },
    render: row => row.keyPrefix || '-',
  },
  {
    prop: 'authModes',
    label: '认证模式',
    width: 150,
    render: row => row.authModes || '-',
  },
  {
    prop: 'actorMode',
    label: '主体模式',
    width: 130,
    render: (row) => {
      return h(DictTag, {
        options: actorModeOptions.value,
        value: row.actorMode,
        size: 'small',
      })
    },
  },
  {
    prop: 'credentialVersion',
    label: '密钥版本',
    width: 90,
    render: row => row.credentialVersion != null ? `v${row.credentialVersion}` : '-',
  },
  {
    prop: 'signingKeyVersion',
    label: '签名密钥版本',
    width: 110,
    render: row => row.signingKeyVersion != null ? `v${row.signingKeyVersion}` : '-',
  },
  {
    prop: 'status',
    label: '状态',
    width: 90,
    render: (row) => {
      return h(DictTag, {
        options: clientStatusOptions.value,
        value: row.status,
        size: 'small',
      })
    },
  },
  {
    prop: 'expiresAt',
    label: '过期时间',
    width: 160,
    render: row => row.expiresAt || '长期有效',
  },
  {
    prop: 'lastUsedAt',
    label: '最近调用',
    width: 160,
    render: row => row.lastUsedAt || '-',
  },
  {
    prop: 'createTime',
    label: '创建时间',
    width: 160,
  },
  {
    prop: 'action',
    label: '操作',
    width: 330,
    fixed: 'right',
    actions: [
      {
        label: '用户断言',
        key: 'userAssertion',
        type: 'primary',
        onClick: openUserAssertion,
        visible: row => canEdit.value
          && row.status === 'ENABLED'
          && Number(row.oauthEnabled) === 1
          && ['USER_DELEGATION', 'HYBRID'].includes(row.actorMode),
      },
      {
        label: '轮换密钥',
        key: 'rotate',
        type: 'warning',
        onClick: handleRotateSecret,
        visible: row => canRotate.value && row.status === 'ENABLED',
      },
      {
        label: '轮换签名密钥',
        key: 'rotateSigningKey',
        type: 'warning',
        onClick: handleRotateSigningKey,
        visible: row => canEdit.value
          && row.status === 'ENABLED'
          && String(row.authModes || '').split(',').includes('SIGNATURE'),
      },
      {
        label: '吊销',
        key: 'revoke',
        type: 'error',
        onClick: handleRevoke,
        visible: row => canRevoke.value && row.status === 'ENABLED',
      },
    ],
  },
])
</script>

<style scoped>
.capability-client-page {
  height: 100%;
}

.w-full {
  width: 100%;
}

.form-alert {
  margin-bottom: 18px;
}

.issued-block {
  margin-top: 16px;
}

.issued-label {
  font-size: 13px;
  font-weight: 500;
  color: #666;
  margin-bottom: 6px;
}

.issued-value {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f5f5f5;
  border-radius: 4px;
  padding: 8px 12px;
}

.issued-value code {
  flex: 1;
  font-family: 'Courier New', 'Consolas', monospace;
  font-size: 13px;
  word-break: break-all;
}

.issued-meta {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px 16px;
  margin-top: 16px;
  font-size: 13px;
}

.issued-meta span {
  color: #666;
}

.issued-meta strong {
  font-family: 'Courier New', 'Consolas', monospace;
}

.user-assertion-modal :deep(.n-spin-container) {
  min-height: 260px;
}

.assertion-heading,
.assertion-actions,
.mapping-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.assertion-heading > div {
  min-width: 0;
}

.assertion-heading strong,
.assertion-heading span {
  display: block;
}

.assertion-heading strong {
  color: var(--text-primary);
  font-size: 15px;
}

.assertion-heading span {
  margin-top: 4px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.assertion-alert {
  margin-top: 16px;
}

.assertion-protocol-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 16px;
  border-top: 1px solid var(--border-light);
  border-left: 1px solid var(--border-light);
}

.assertion-protocol-grid > div {
  min-width: 0;
  padding: 11px 13px;
  border-right: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-secondary);
}

.assertion-protocol-grid span,
.assertion-protocol-grid code {
  display: block;
}

.assertion-protocol-grid span {
  margin-bottom: 5px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.assertion-protocol-grid code {
  overflow-wrap: anywhere;
  color: var(--text-primary);
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
}

.protocol-wide {
  grid-column: 1 / -1;
}

.assertion-actions {
  justify-content: flex-start;
  margin-top: 16px;
}

.mapping-section {
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}

.mapping-heading h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 14px;
}

.mapping-heading p {
  margin: 5px 0 0;
  color: var(--text-tertiary);
  font-size: 12px;
}

.mapping-form {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(240px, 1fr) auto;
  align-items: end;
  gap: 12px;
  margin-top: 14px;
}

.mapping-form :deep(.n-form-item) {
  min-width: 0;
  margin-bottom: 0;
}

.mapping-form > .n-button {
  margin-bottom: 1px;
}

.mapping-table {
  margin-top: 16px;
  border-top: 1px solid var(--border-light);
}

.private-key-value :deep(textarea) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
}

.private-key-actions {
  margin-top: 10px;
}

@media (max-width: 760px) {
  .assertion-protocol-grid,
  .mapping-form {
    grid-template-columns: 1fr;
  }

  .protocol-wide {
    grid-column: auto;
  }

  .assertion-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
