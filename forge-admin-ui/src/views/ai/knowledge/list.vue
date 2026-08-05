<template>
  <div class="ai-knowledge-page">
    <div class="kb-layout">
      <!-- 左侧：知识库列表 -->
      <aside class="kb-list-panel">
        <div class="kb-list-panel__header">
          <h2>知识库</h2>
          <NButton
            type="primary"
            circle
            size="small"
            aria-label="新增知识库"
            title="新增知识库"
            @click="handleAdd"
          >
            <template #icon>
              <i class="ai-icon:plus" aria-hidden="true" />
            </template>
          </NButton>
        </div>
        <div class="kb-list-filters">
          <n-input
            v-model:value="search.name"
            placeholder="搜索知识库名称"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <i class="ai-icon:search" aria-hidden="true" />
            </template>
          </n-input>
          <div class="kb-list-filters__actions">
            <NButton type="primary" size="small" @click="handleSearch">查询</NButton>
            <NButton size="small" @click="handleReset">重置</NButton>
          </div>
        </div>
        <n-spin :show="loading">
          <div class="kb-list">
            <button
              v-for="kb in kbList"
              :key="kb.id"
              type="button"
              class="kb-list-item"
              :class="{ 'kb-list-item--selected': selectedKb?.id === kb.id }"
              :aria-pressed="selectedKb?.id === kb.id"
              @click="handleSelect(kb)"
            >
              <span class="kb-list-item__icon">
                <i v-if="kb.icon" :class="`ai-icon:${kb.icon}`" />
                <i v-else class="ai-icon:apps" />
              </span>
              <span class="kb-list-item__content">
                <span class="kb-list-item__title">
                  <strong>{{ kb.knowledgeName }}</strong>
                  <DictTag dict-type="ai_status" :value="kb.status" size="small" />
                </span>
                <span class="kb-list-item__desc">{{ kb.description || '暂无描述' }}</span>
              </span>
            </button>
            <n-empty v-if="!loading && kbList.length === 0" description="暂无知识库" size="small" />
          </div>
        </n-spin>
        <div class="kb-list-pagination">
          <span>共 {{ pagination.itemCount }} 条</span>
          <n-pagination
            :page="pagination.pageNum"
            :page-size="pagination.pageSize"
            :item-count="pagination.itemCount"
            :page-sizes="pageSizes"
            show-size-picker
            size="small"
            @update:page="handlePageChange"
            @update:page-size="handlePageSizeChange"
          />
        </div>
      </aside>

      <!-- 右侧：文档管理 -->
      <section class="kb-detail-panel">
        <template v-if="selectedKb">
          <div class="kb-detail-header">
            <div class="kb-detail-header__identity">
              <span class="kb-detail-header__icon">
                <i v-if="selectedKb.icon" :class="`ai-icon:${selectedKb.icon}`" />
                <i v-else class="ai-icon:apps" />
              </span>
              <div>
                <div class="kb-detail-header__title">
                  <h2>{{ selectedKb.knowledgeName }}</h2>
                  <DictTag dict-type="ai_status" :value="selectedKb.status" size="small" />
                </div>
                <p class="kb-detail-header__desc">{{ selectedKb.description || '暂无描述' }}</p>
              </div>
            </div>
            <div class="kb-detail-header__actions">
              <NButton secondary @click="handleEdit(selectedKb)">编辑</NButton>
              <NButton secondary @click="searchModal.show = true">检索调试</NButton>
              <NPopconfirm @positive-click="handleDelete(selectedKb.id)">
                <template #trigger>
                  <NButton text class="text-error">删除</NButton>
                </template>
                确定删除知识库“{{ selectedKb.knowledgeName }}”吗？该操作将删除其下所有文档。
              </NPopconfirm>
            </div>
          </div>

          <div class="kb-doc-toolbar">
            <div class="kb-doc-toolbar__left">
              <strong>文档管理</strong>
              <n-select
                v-model:value="docSearch.processStatus"
                placeholder="全部状态"
                clearable
                :options="processStatusOptions"
                size="small"
                style="width: 130px"
                @update:value="handleDocSearch"
              />
            </div>
            <div class="kb-doc-toolbar__actions">
              <n-upload
                :action="`${uploadPrefix}/system/file/upload`"
                :headers="uploadHeaders"
                :max="5"
                :default-upload="true"
                accept=".pdf,.doc,.docx,.xls,.xlsx,.md,.markdown,.txt,.html,.htm"
                @finish="handleUploadFinish"
                @error="handleUploadError"
              >
                <NButton type="primary">
                  <template #icon>
                    <i class="ai-icon:upload" />
                  </template>
                  上传文档
                </NButton>
              </n-upload>
            </div>
          </div>

          <n-data-table
            :columns="docColumns"
            :data="docList"
            :loading="docLoading"
            :row-key="row => row.id"
            :scroll-x="1000"
            size="small"
            class="kb-doc-table"
          />
          <div class="kb-doc-pagination">
            <n-pagination
              :page="docPagination.pageNum"
              :page-size="docPagination.pageSize"
              :item-count="docPagination.itemCount"
              :page-sizes="pageSizes"
              show-size-picker
              show-quick-jumper
              size="small"
              @update:page="handleDocPageChange"
              @update:page-size="handleDocPageSizeChange"
            />
          </div>
        </template>

        <div v-else class="kb-detail-empty">
          <i class="ai-icon:apps" aria-hidden="true" />
          <h2>请选择知识库</h2>
          <p>从左侧选择一个知识库，查看和管理其下的文档。</p>
        </div>
      </section>
    </div>

    <!-- 新建/编辑知识库 · 右侧抽屉 -->
    <n-drawer
      v-model:show="kbModal.show"
      :width="kbDrawerWidth"
      placement="right"
      display-directive="if"
    >
      <n-drawer-content
        :title="kbModal.isEdit ? '编辑知识库' : '新增知识库'"
        closable
        body-content-style="padding: 0 20px 20px;"
        :native-scrollbar="false"
      >
        <div class="kb-drawer-scroll">
          <n-form ref="kbFormRef" :model="kbModal.form" :rules="kbRules" label-placement="top" size="medium">
            <div class="kb-section-card">
              <div class="section-title">
                <i class="ai-icon:database" aria-hidden="true" />
                <span>基础信息</span>
              </div>
              <n-form-item label="向量存储实例" path="vectorStoreInstanceId" required>
                <n-select
                  v-model:value="kbModal.form.vectorStoreInstanceId"
                  placeholder="请选择向量存储实例"
                  clearable
                  :options="storeInstanceOptions"
                />
              </n-form-item>
              <n-form-item label="知识库名称" path="knowledgeName" required>
                <n-input v-model:value="kbModal.form.knowledgeName" placeholder="请输入知识库名称" maxlength="100" show-count />
              </n-form-item>
              <n-form-item label="描述" path="description">
                <n-input v-model:value="kbModal.form.description" type="textarea" :rows="2" placeholder="请输入知识库描述" />
              </n-form-item>
              <n-form-item label="向量模型（Embedding）" path="embeddingModelId">
                <n-select
                  v-model:value="kbModal.form.embeddingModelId"
                  placeholder="请选择 Embedding 模型"
                  clearable
                  :options="embeddingModelOptions"
                />
              </n-form-item>
              <n-form-item label="Rerank 模型" path="rerankModelId">
                <n-select
                  v-model:value="kbModal.form.rerankModelId"
                  placeholder="请选择 Rerank 模型"
                  clearable
                  :options="rerankModelOptions"
                />
              </n-form-item>
            </div>

            <div class="kb-section-card">
              <div class="section-title">
                <i class="ai-icon:copy" aria-hidden="true" />
                <span>上传去重</span>
              </div>
              <p class="section-desc">控制同一知识库内重复文档的判定与处理方式。</p>
              <n-form-item label="去重策略" path="dedupStrategy">
                <n-select
                  v-model:value="kbModal.form.dedupStrategy"
                  placeholder="请选择去重策略"
                  :options="dedupStrategyOptions"
                />
              </n-form-item>
            </div>

            <div class="kb-section-card">
              <div class="section-title">
                <i class="ai-icon:cut" aria-hidden="true" />
                <span>切片策略</span>
              </div>
              <p class="section-desc">选择文档入库时的分块方式。</p>
              <div class="chunk-mode-seg" role="tablist" aria-label="切片策略">
                <button
                  v-for="opt in chunkStrategyOptions"
                  :key="opt.value"
                  type="button"
                  role="tab"
                  :aria-selected="kbModal.form.chunkStrategy === opt.value"
                  class="chunk-mode-seg__item"
                  :class="[kbModal.form.chunkStrategy === opt.value && 'chunk-mode-seg__item--active']"
                  @click="kbModal.form.chunkStrategy = opt.value"
                >
                  <span class="chunk-mode-seg__icon">{{ chunkStrategyIcons[opt.value] }}</span>
                  <span class="chunk-mode-seg__label">{{ opt.label }}</span>
                </button>
              </div>

              <div v-if="kbModal.form.chunkStrategy === 'length'" class="chunk-panel">
                <div class="chunk-panel__row">
                  <span class="chunk-panel__label">分块长度</span>
                  <n-input-number v-model:value="chunkMaxTokens" :min="50" :max="32000" :show-button="true" size="small" class="chunk-panel__input" />
                </div>
                <div class="chunk-panel__row">
                  <span class="chunk-panel__label">重叠长度</span>
                  <n-input-number v-model:value="chunkOverlap" :min="0" :max="4096" clearable :show-button="true" size="small" class="chunk-panel__input" placeholder="默认 16" />
                </div>
              </div>
              <div v-else-if="kbModal.form.chunkStrategy === 'delimiter'" class="chunk-panel">
                <div class="chunk-panel__row">
                  <span class="chunk-panel__label">分隔符</span>
                  <n-input v-model:value="chunkDelimiters" size="small" placeholder="如 \n\n 或 。" />
                </div>
              </div>
              <div v-else-if="kbModal.form.chunkStrategy === 'regex'" class="chunk-panel">
                <div class="chunk-panel__row">
                  <span class="chunk-panel__label">正则表达式</span>
                  <n-input v-model:value="chunkRegex" type="textarea" :rows="2" size="small" placeholder="一级切分正则" />
                </div>
              </div>
            </div>

            <div class="kb-section-card">
              <div class="section-title">
                <i class="ai-icon:settings" aria-hidden="true" />
                <span>其他</span>
              </div>
              <n-form-item label="状态" path="status">
                <n-radio-group v-model:value="kbModal.form.status">
                  <n-radio v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </n-radio>
                </n-radio-group>
              </n-form-item>
              <n-form-item label="图标" path="icon">
                <n-input v-model:value="kbModal.form.icon" placeholder="图标标识，如 library-outline" />
              </n-form-item>
            </div>
          </n-form>
        </div>
        <template #footer>
          <div class="kb-drawer-footer">
            <NButton @click="kbModal.show = false">取消</NButton>
            <NButton type="primary" :loading="kbModal.saving" @click="handleSave">确定</NButton>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>

    <!-- 检索调试 -->
    <n-modal
      v-model:show="searchModal.show"
      preset="card"
      title="知识库检索调试"
      :style="{ maxWidth: '760px', width: 'calc(100vw - 32px)' }"
    >
      <div class="search-form">
        <n-input
          v-model:value="searchModal.query"
          type="textarea"
          :rows="3"
          placeholder="输入检索问题，例如：如何配置 API Key？"
        />
        <div class="search-form__actions">
          <n-input-number v-model:value="searchModal.topK" :min="1" :max="20" placeholder="TopK" style="width: 120px" />
          <NButton type="primary" :loading="searchModal.loading" @click="handleSearch">
            检索
          </NButton>
        </div>
      </div>
      <div v-if="searchModal.results.length" class="search-results">
        <div v-for="(r, i) in searchModal.results" :key="i" class="search-result-item">
          <div class="search-result-item__meta">
            <NTag size="small" :bordered="false">{{ r.score?.toFixed?.(3) ?? '—' }}</NTag>
            <code>{{ r.docName || `文档 #${r.documentId}` }}</code>
          </div>
          <p>{{ r.content }}</p>
        </div>
      </div>
      <template #action>
        <div class="modal-footer-actions">
          <NButton @click="searchModal.show = false">关闭</NButton>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { NButton, NPopconfirm, NTag } from 'naive-ui'
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  knowledgePage as fetchKbPage,
  knowledgeCreate,
  knowledgeDelete,
  knowledgeDocumentPage as fetchDocPage,
  knowledgeDocumentUpload,
  knowledgeDocumentDelete,
  knowledgeDocumentProgressSSE,
  knowledgeSearch,
  knowledgeUpdate,
  storeInstancePage as fetchStorePage,
  modelPage as fetchModelPage,
} from '@/api/ai'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'

defineOptions({ name: 'AiKnowledge' })

const { dict } = useDict('ai_status', 'ai_knowledge_process_status', 'ai_store_instance_category', 'ai_vector_store_type')

const statusOptions = computed(() => dict.value.ai_status || [])
const processStatusOptions = computed(() => dict.value.ai_knowledge_process_status || [])
const pageSizes = [10, 20, 50]
const modalCardStyle = { maxWidth: '860px', width: 'calc(100vw - 32px)' }

// 创建/编辑抽屉宽度：窄屏时自适应，避免横向溢出
const kbDrawerWidth = computed(() => {
  if (typeof window === 'undefined')
    return 760
  return Math.min(760, Math.max(400, window.innerWidth - 24))
})

const chunkStrategyOptions = [
  { label: '长度分块', value: 'length' },
  { label: '分隔符分块', value: 'delimiter' },
  { label: '正则分块', value: 'regex' },
  { label: '智能分块', value: 'smart' },
  { label: '问答分块', value: 'qa' },
]
const chunkStrategyIcons = {
  length: '⚡',
  delimiter: '✂',
  regex: '.*',
  smart: '🧠',
  qa: '❓',
}
const dedupStrategyOptions = [
  { label: '不去重', value: 'none' },
  { label: '按名称去重', value: 'name' },
  { label: '按内容去重', value: 'content' },
  { label: '名称或内容', value: 'name_or_content' },
]

// 切片配置（按策略展开）
const chunkMaxTokens = ref(600)
const chunkOverlap = ref(null)
const chunkDelimiters = ref('')
const chunkRegex = ref('')

const uploadPrefix = import.meta.env.VITE_API_BASEURL || '/api'
const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token') || ''
  return { Authorization: `Bearer ${token}` }
})

const search = reactive({ name: '' })
const kbList = ref([])
const loading = ref(false)
const selectedKb = ref(null)
const pagination = reactive({ pageNum: 1, pageSize: 10, itemCount: 0 })

const docSearch = reactive({ processStatus: null })
const docList = ref([])
const docLoading = ref(false)
const docPagination = reactive({ pageNum: 1, pageSize: 10, itemCount: 0 })
const docProcessing = reactive({})

const kbFormRef = ref(null)
const kbModal = reactive({ show: false, isEdit: false, saving: false, form: createKbForm() })
const searchModal = reactive({ show: false, query: '', topK: 5, loading: false, results: [] })

// 存储实例 / 模型下拉数据
const storeInstanceOptions = ref([])
const embeddingModelOptions = ref([])
const rerankModelOptions = ref([])

function createKbForm() {
  return {
    knowledgeName: '',
    description: '',
    icon: '',
    vectorStoreInstanceId: null,
    embeddingModelId: null,
    rerankModelId: null,
    dimensionOfVectorModel: null,
    chunkStrategy: 'length',
    chunkConfigJson: '',
    searchConfigJson: '',
    dedupStrategy: 'none',
    dedupAction: 'reject',
    uploadConfirm: '0',
    status: '0',
  }
}

const kbRules = {
  knowledgeName: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  vectorStoreInstanceId: [{ required: true, message: '请选择向量存储实例', trigger: 'change' }],
}

async function loadKbs() {
  loading.value = true
  try {
    const res = await fetchKbPage({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      ...(search.name ? { knowledgeName: search.name } : {}),
    })
    if (res.code === 200 && res.data) {
      kbList.value = res.data.records || []
      pagination.itemCount = Number(res.data.total || 0)
      if (selectedKb.value) {
        const current = kbList.value.find(k => k.id === selectedKb.value.id)
        if (current)
          selectedKb.value = current
      }
    }
  }
  catch {}
  finally {
    loading.value = false
  }
}

async function loadStoreInstances() {
  try {
    const res = await fetchStorePage({ pageNum: 1, pageSize: 100 })
    if (res.code === 200 && res.data)
      storeInstanceOptions.value = (res.data.records || []).map(s => ({ label: s.instanceName, value: s.id }))
  }
  catch {}
}

async function loadModelOptions() {
  try {
    const [embRes, rerankRes] = await Promise.all([
      fetchModelPage({ pageNum: 1, pageSize: 100, modelType: 'embedding' }),
      fetchModelPage({ pageNum: 1, pageSize: 100, modelType: 'rerank' }),
    ])
    if (embRes.code === 200 && embRes.data)
      embeddingModelOptions.value = (embRes.data.records || []).map(m => ({ label: m.modelName || m.modelId, value: m.id }))
    if (rerankRes.code === 200 && rerankRes.data)
      rerankModelOptions.value = (rerankRes.data.records || []).map(m => ({ label: m.modelName || m.modelId, value: m.id }))
  }
  catch {}
}

function handleSearch() {
  pagination.pageNum = 1
  loadKbs()
}

function handleReset() {
  search.name = ''
  pagination.pageNum = 1
  loadKbs()
}

function handlePageChange(page) {
  pagination.pageNum = page
  loadKbs()
}

function handlePageSizeChange(pageSize) {
  pagination.pageSize = pageSize
  pagination.pageNum = 1
  loadKbs()
}

function handleSelect(kb) {
  if (selectedKb.value?.id === kb.id)
    return
  selectedKb.value = kb
  docPagination.pageNum = 1
  loadDocs()
}

function handleAdd() {
  kbModal.isEdit = false
  kbModal.form = createKbForm()
  kbModal.show = true
}

async function handleEdit(kb) {
  kbModal.isEdit = true
  kbModal.form = { ...createKbForm(), ...kb }
  kbModal.show = true
}

async function handleSave() {
  try {
    await kbFormRef.value?.validate()
  }
  catch { return }
  kbModal.saving = true
  try {
    const payload = { ...kbModal.form }
    // 按切片策略拼 chunkConfigJson
    const strategy = payload.chunkStrategy
    if (strategy === 'length') {
      payload.chunkConfigJson = JSON.stringify({
        max_tokens: chunkMaxTokens.value,
        overlap: chunkOverlap.value ?? 16,
      })
    }
    else if (strategy === 'delimiter') {
      payload.chunkConfigJson = JSON.stringify({ delimiters: chunkDelimiters.value })
    }
    else if (strategy === 'regex') {
      payload.chunkConfigJson = JSON.stringify({ regex: chunkRegex.value })
    }
    const res = kbModal.isEdit ? await knowledgeUpdate(payload) : await knowledgeCreate(payload)
    if (res.code === 200) {
      window.$message.success(kbModal.isEdit ? '更新成功' : '新增成功')
      kbModal.show = false
      await loadKbs()
    }
    else {
      window.$message.error(res.msg || '操作失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '操作失败')
  }
  finally {
    kbModal.saving = false
  }
}

async function handleDelete(id) {
  try {
    const res = await knowledgeDelete(id)
    if (res.code === 200) {
      window.$message.success('删除成功')
      if (selectedKb.value?.id === id)
        selectedKb.value = null
      await loadKbs()
    }
    else {
      window.$message.error(res.msg || '删除失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '删除失败')
  }
}

// ===== 文档管理 =====

async function loadDocs() {
  if (!selectedKb.value) {
    docList.value = []
    return
  }
  docLoading.value = true
  try {
    const res = await fetchDocPage({
      pageNum: docPagination.pageNum,
      pageSize: docPagination.pageSize,
      knowledgeId: selectedKb.value.id,
      ...(docSearch.processStatus ? { processStatus: docSearch.processStatus } : {}),
    })
    if (res.code === 200 && res.data) {
      docList.value = res.data.records || []
      docPagination.itemCount = Number(res.data.total || 0)
    }
  }
  catch {}
  finally {
    docLoading.value = false
  }
}

function handleDocSearch() {
  docPagination.pageNum = 1
  loadDocs()
}

function handleDocPageChange(page) {
  docPagination.pageNum = page
  loadDocs()
}

function handleDocPageSizeChange(pageSize) {
  docPagination.pageSize = pageSize
  docPagination.pageNum = 1
  loadDocs()
}

function handleUploadFinish({ event }) {
  try {
    const res = JSON.parse(event.target.response)
    if (res.code === 200 && res.data) {
      const fileId = res.data.fileId || res.data.id
      submitDocumentUpload(fileId, res.data)
    }
    else {
      window.$message.error(res.msg || '上传失败')
    }
  }
  catch {
    window.$message.error('上传失败')
  }
  return false
}

function handleUploadError() {
  window.$message.error('文件上传失败')
}

async function submitDocumentUpload(fileId, fileData) {
  try {
    const res = await knowledgeDocumentUpload({
      knowledgeId: selectedKb.value.id,
      fileId,
      docName: fileData.originalName || fileData.fileName || `文档${Date.now()}`,
      sourceType: 'upload',
      confirm: true,
    })
    if (res.code === 200) {
      window.$message.success('文档上传成功，开始处理')
      await loadDocs()
    }
    else {
      window.$message.error(res.msg || '文档上传失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '文档上传失败')
  }
}

function subscribeDocProgress(doc) {
  if (docProcessing[doc.id])
    return
  docProcessing[doc.id] = true
  knowledgeDocumentProgressSSE(
    doc.id,
    (event) => {
      if (event && (event.percent === 100 || event.status === 'success' || event.status === 'failed')) {
        docProcessing[doc.id] = false
        loadDocs()
      }
    },
    () => { docProcessing[doc.id] = false },
    () => { docProcessing[doc.id] = false },
  )
}

async function handleDeleteDoc(doc) {
  try {
    const res = await knowledgeDocumentDelete(doc.id)
    if (res.code === 200) {
      window.$message.success('删除成功')
      await loadDocs()
    }
    else {
      window.$message.error(res.msg || '删除失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '删除失败')
  }
}

// ===== 检索调试 =====

async function handleSearchDebug() {
  if (!searchModal.query.trim()) {
    window.$message.warning('请输入检索问题')
    return
  }
  searchModal.loading = true
  searchModal.results = []
  try {
    const res = await knowledgeSearch({
      knowledgeId: selectedKb.value.id,
      query: searchModal.query,
      topK: searchModal.topK || 5,
    })
    if (res.code === 200) {
      searchModal.results = res.data || []
    }
    else {
      window.$message.error(res.msg || '检索失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '检索失败')
  }
  finally {
    searchModal.loading = false
  }
}

// ===== 表格列 =====

const docColumns = [
  { title: '文档名称', key: 'docName', width: 240, ellipsis: { tooltip: true } },
  {
    title: '类型',
    key: 'docType',
    width: 90,
    render(row) { return h('span', {}, row.docType || '—') },
  },
  {
    title: '处理状态',
    key: 'processStatus',
    width: 110,
    render(row) {
      const opts = processStatusOptions.value
      return h(DictTag, { dictType: 'ai_knowledge_process_status', value: row.processStatus, size: 'small' })
    },
  },
  { title: '分块数', key: 'chunkCount', width: 80, align: 'right', render(row) { return row.chunkCount ?? '-' } },
  {
    title: '处理时间',
    key: 'updateTime',
    width: 150,
    render(row) { return row.updateTime ? String(row.updateTime).replace('T', ' ').slice(0, 16) : '—' },
  },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    fixed: 'right',
    render(row) {
      const actions = []
      if (row.processStatus === 'pending' || row.processStatus === 'processing') {
        actions.push(h(NButton, { text: true, size: 'small', class: 'text-warning', loading: !!docProcessing[row.id], onClick: () => subscribeDocProgress(row) }, { default: () => docProcessing[row.id] ? '处理中' : '刷新进度' }))
      }
      actions.push(h(NPopconfirm, { onPositiveClick: () => handleDeleteDoc(row) }, {
        trigger: () => h(NButton, { text: true, size: 'small', class: 'text-error' }, { default: () => '删除' }),
        default: () => '确定删除该文档吗？',
      }))
      return h('div', { class: 'table-actions' }, actions)
    },
  },
]

onMounted(() => {
  loadKbs()
  loadStoreInstances()
  loadModelOptions()
})
</script>

<style scoped>
.ai-knowledge-page {
  --page-bg: #f3f6fa;
  --panel-bg: #ffffff;
  --panel-subtle: #f8fafc;
  --panel-border: #dfe6ee;
  --text-strong: #111827;
  --text-body: #475569;
  --text-muted: #64748b;
  --accent: #0369a1;
  --accent-soft: #eaf4fb;
  --accent-border: #b9d9ec;
  --shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
  min-height: 100%;
  padding: 20px;
  color: var(--text-body);
  background: var(--page-bg);
}

:global(.dark) .ai-knowledge-page {
  --page-bg: #0d1420;
  --panel-bg: #151f2d;
  --panel-subtle: #111a27;
  --panel-border: #2c3a4d;
  --text-strong: #f1f5f9;
  --text-body: #cbd5e1;
  --text-muted: #94a3b8;
  --accent: #38bdf8;
  --accent-soft: rgba(14, 165, 233, 0.12);
  --accent-border: rgba(56, 189, 248, 0.3);
}

.kb-layout {
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(0, 1.6fr);
  gap: 16px;
  align-items: start;
}

.kb-layout > * {
  min-width: 0;
}

.kb-list-panel,
.kb-detail-panel {
  min-height: calc(100vh - 150px);
  overflow: hidden;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 9px;
  box-shadow: var(--shadow);
}

.kb-list-panel {
  display: flex;
  flex-direction: column;
}

.kb-list-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--panel-border);
}

.kb-list-panel__header h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 15px;
  font-weight: 600;
}

.kb-list-filters {
  padding: 12px;
  background: var(--panel-subtle);
  border-bottom: 1px solid var(--panel-border);
}

.kb-list-filters__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.kb-list {
  min-height: 200px;
  max-height: calc(100vh - 340px);
  overflow-y: auto;
}

.kb-list-item {
  display: flex;
  width: 100%;
  padding: 14px 13px;
  align-items: center;
  gap: 10px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--panel-border);
  transition: background-color 160ms ease;
}

.kb-list-item:hover {
  background: var(--panel-subtle);
}

.kb-list-item--selected,
.kb-list-item--selected:hover {
  background: var(--accent-soft);
  box-shadow: inset 3px 0 0 var(--accent);
}

.kb-list-item__icon {
  display: grid;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  color: var(--accent);
  font-size: 16px;
  place-items: center;
  background: var(--accent-soft);
  border: 1px solid var(--accent-border);
  border-radius: 8px;
}

.kb-list-item__content {
  min-width: 0;
  flex: 1;
}

.kb-list-item__title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.kb-list-item__title strong {
  overflow: hidden;
  color: var(--text-strong);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-list-item__desc {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-list-pagination {
  display: flex;
  padding: 11px 12px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  overflow-x: auto;
  color: var(--text-muted);
  border-top: 1px solid var(--panel-border);
  font-size: 11px;
}

.kb-detail-header {
  display: flex;
  padding: 18px 20px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid var(--panel-border);
}

.kb-detail-header__identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.kb-detail-header__icon {
  display: grid;
  flex: 0 0 46px;
  width: 46px;
  height: 46px;
  color: var(--accent);
  font-size: 20px;
  place-items: center;
  background: var(--accent-soft);
  border: 1px solid var(--accent-border);
  border-radius: 10px;
}

.kb-detail-header__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kb-detail-header__title h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 17px;
}

.kb-detail-header__desc {
  margin: 5px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}

.kb-detail-header__actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.kb-doc-toolbar {
  display: flex;
  padding: 13px 16px;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  color: var(--text-muted);
  font-size: 12px;
}

.kb-doc-toolbar__left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.kb-doc-toolbar__left strong {
  color: var(--text-strong);
  font-size: 13px;
}

.kb-doc-toolbar__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.kb-doc-pagination {
  display: flex;
  padding: 12px 16px;
  justify-content: flex-end;
  overflow-x: auto;
  border-top: 1px solid var(--panel-border);
}

.kb-detail-empty {
  display: flex;
  min-height: calc(100vh - 152px);
  padding: 32px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: var(--text-muted);
  text-align: center;
}

.kb-detail-empty > i {
  margin-bottom: 12px;
  font-size: 32px;
}

.kb-detail-empty h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 18px;
}

.kb-detail-empty p {
  margin: 7px 0 0;
  font-size: 12px;
}

.kb-drawer-scroll {
  max-height: calc(100vh - 140px);
  overflow-y: auto;
  padding-right: 4px;
}

.kb-section-card {
  margin-bottom: 14px;
  padding: 16px;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
}

.kb-section-card:last-child {
  margin-bottom: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 600;
}

.section-title i {
  color: var(--accent);
  font-size: 16px;
}

.section-desc {
  margin: -6px 0 12px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.kb-drawer-footer {
  padding-top: 8px;
  border-top: 1px solid var(--panel-border);
}

.chunk-mode-seg {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  padding: 4px;
  border-radius: 10px;
  background: var(--panel-subtle);
  border: 1px solid var(--panel-border);
}

.chunk-mode-seg__item {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 9px 6px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-muted);
  transition: background 0.2s, color 0.2s, border-color 0.2s;
}

.chunk-mode-seg__item:hover {
  color: var(--text-strong);
  background: color-mix(in srgb, var(--text-strong) 6%, transparent);
}

.chunk-mode-seg__item--active {
  color: var(--accent);
  font-weight: 600;
  background: var(--accent-soft);
  border-color: var(--accent-border);
}

.chunk-mode-seg__icon {
  font-size: 14px;
  line-height: 1;
}

.chunk-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
  padding: 12px 14px;
  background: var(--panel-subtle);
  border: 1px solid var(--panel-border);
  border-radius: 8px;
}

.chunk-panel__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.chunk-panel__label {
  flex: 0 0 auto;
  color: var(--text-body);
  font-size: 12px;
}

.chunk-panel__input {
  max-width: 220px;
}

.modal-footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.table-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  white-space: nowrap;
}

.search-form__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}

.search-results {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
  max-height: 420px;
  overflow-y: auto;
}

.search-result-item {
  padding: 12px 14px;
  background: var(--panel-subtle);
  border: 1px solid var(--panel-border);
  border-radius: 8px;
}

.search-result-item__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.search-result-item__meta code {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-result-item p {
  margin: 0;
  color: var(--text-body);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1120px) {
  .kb-layout {
    grid-template-columns: 1fr;
  }

  .kb-list-panel,
  .kb-detail-panel {
    min-height: auto;
  }

  .kb-list {
    max-height: 360px;
  }
}
</style>
