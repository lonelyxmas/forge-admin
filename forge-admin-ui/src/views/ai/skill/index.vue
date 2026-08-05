<template>
  <div class="skill-page">
    <AiCrudPage
      ref="crudRef"
      api="/ai/skill"
      :api-config="{
        list: 'get@/ai/skill/page',
        detail: 'get@/ai/skill/:id',
        add: 'post@/ai/skill',
        update: 'put@/ai/skill',
        delete: 'delete@/ai/skill/:id',
      }"
      :search-schema="searchSchema"
      :columns="tableColumns"
      :edit-schema="editSchema"
      row-key="id"
      :edit-grid-cols="2"
      modal-width="700px"
      add-button-text="新增技能"
      :toolbar-actions="toolbarActions"
    />

    <!-- ZIP 上传弹窗 -->
    <n-modal v-model:show="showUploadModal" preset="card" title="上传技能包" style="width: 500px">
      <n-upload
        :max="1"
        accept=".zip"
        :custom-request="handleUploadZip"
        :show-file-list="false"
      >
        <n-upload-dragger>
          <div style="padding: 20px; text-align: center">
            <n-icon size="40" :depth="3"><cloud-upload-outline /></n-icon>
            <p>点击或拖拽 ZIP 文件到此处</p>
            <p style="font-size: 12px; color: var(--n-text-color-3)">
              ZIP 包需包含 SKILL.md 文件
            </p>
          </div>
        </n-upload-dragger>
      </n-upload>
    </n-modal>

    <!-- AI 生成弹窗 -->
    <n-modal v-model:show="showGenerateModal" preset="card" title="AI 生成技能" style="width: 600px">
      <n-form label-placement="top">
        <n-form-item label="技能描述">
          <n-input
            v-model:value="generateDescription"
            type="textarea"
            :rows="4"
            placeholder="描述你需要的技能功能..."
          />
        </n-form-item>
      </n-form>
      <template #action>
        <n-space>
          <n-button @click="showGenerateModal = false">取消</n-button>
          <n-button type="primary" :loading="generating" @click="handleAiGenerate">生成</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- AI 优化弹窗 -->
    <n-modal v-model:show="showOptimizeModal" preset="card" title="AI 优化技能" style="width: 600px">
      <n-form label-placement="top">
        <n-form-item label="优化指令">
          <n-input
            v-model:value="optimizeInstruction"
            type="textarea"
            :rows="3"
            placeholder="描述你希望如何优化..."
          />
        </n-form-item>
      </n-form>
      <template #action>
        <n-space>
          <n-button @click="showOptimizeModal = false">取消</n-button>
          <n-button type="primary" :loading="optimizing" @click="handleAiOptimize">优化</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 技能文件查看弹窗 -->
    <n-modal v-model:show="showFilesModal" preset="card" title="技能文件" style="width: 800px">
      <n-spin :show="loadingFiles">
        <div v-if="skillFiles.length">
          <n-collapse>
            <n-collapse-item
              v-for="file in skillFiles"
              :key="file.id"
              :title="file.filePath"
              :name="file.id"
            >
              <pre class="file-content">{{ file.fileContent }}</pre>
            </n-collapse-item>
          </n-collapse>
        </div>
        <n-empty v-else description="暂无文件" />
      </n-spin>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, h } from 'vue'
import { NButton, NTag, NModal, NUpload, NUploadDragger, NIcon, NForm, NFormItem, NInput, NSpace, NCollapse, NCollapseItem, NSpin, NEmpty, useMessage } from 'naive-ui'
import { CloudUploadOutline } from '@vicons/ionicons5'
import { AiCrudPage } from '@/components/ai-form'
import { DictTag } from '@/components'
import { skillUploadZip, skillAiGenerate, skillAiOptimize, skillGetFiles } from '@/api/ai'

defineOptions({ name: 'AiSkill' })

const crudRef = ref(null)
const message = useMessage()

// 弹窗状态
const showUploadModal = ref(false)
const showGenerateModal = ref(false)
const showOptimizeModal = ref(false)
const showFilesModal = ref(false)

// AI 生成/优化
const generateDescription = ref('')
const optimizeInstruction = ref('')
const optimizeSkillId = ref(null)
const generating = ref(false)
const optimizing = ref(false)

// 技能文件
const skillFiles = ref([])
const loadingFiles = ref(false)

const searchSchema = [
  { field: 'keyword', label: '关键词', type: 'input', placeholder: '名称/编码/描述' },
  { field: 'status', label: '状态', type: 'select', dictType: 'ai_status' },
]

const tableColumns = [
  { title: '技能名称', key: 'skillName', width: 160 },
  { title: '技能编码', key: 'skillCode', width: 160 },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '版本', key: 'version', width: 80 },
  {
    title: '状态',
    key: 'status',
    width: 80,
    render: (row) => h(DictTag, { dictType: 'ai_status', value: row.status, size: 'small' }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 260,
    actions: [
      { label: '编辑', key: 'edit', type: 'primary', onClick: (row) => crudRef.value?.showEdit(row) },
      { label: '文件', key: 'files', onClick: (row) => viewFiles(row.id) },
      { label: 'AI优化', key: 'optimize', type: 'warning', onClick: (row) => openOptimize(row.id) },
      { label: '删除', key: 'delete', type: 'error', onClick: (row) => crudRef.value?.handleDelete(row) },
    ],
  },
]

const editSchema = [
  { field: 'skillName', label: '技能名称', type: 'input', required: true, span: 1 },
  { field: 'skillCode', label: '技能编码', type: 'input', required: true, span: 1 },
  { field: 'description', label: '描述', type: 'textarea', span: 2 },
  { field: 'version', label: '版本', type: 'input', defaultValue: '1.0.0', span: 1 },
  { field: 'status', label: '状态', type: 'select', dictType: 'ai_status', defaultValue: '0', span: 1 },
]

const toolbarActions = [
  { label: '上传ZIP', key: 'upload-zip', type: 'primary', onClick: () => { showUploadModal.value = true } },
  { label: 'AI生成', key: 'ai-generate', type: 'warning', onClick: () => { showGenerateModal.value = true } },
]

async function handleUploadZip({ file }) {
  try {
    const formData = new FormData()
    formData.append('file', file.file)
    const res = await skillUploadZip(formData)
    if (res.code === 200) {
      message.success('技能包上传成功')
      showUploadModal.value = false
      crudRef.value?.refresh()
    } else {
      message.error(res.msg || '上传失败')
    }
  } catch (e) {
    message.error('上传失败: ' + (e.message || e))
  }
}

async function handleAiGenerate() {
  if (!generateDescription.value.trim()) {
    message.warning('请输入技能描述')
    return
  }
  generating.value = true
  try {
    const res = await skillAiGenerate(generateDescription.value.trim())
    if (res.code === 200) {
      message.success('AI生成成功，请查看并创建技能')
      showGenerateModal.value = false
      generateDescription.value = ''
    } else {
      message.error(res.msg || '生成失败')
    }
  } catch (e) {
    message.error('生成失败: ' + (e.message || e))
  } finally {
    generating.value = false
  }
}

function openOptimize(skillId) {
  optimizeSkillId.value = skillId
  optimizeInstruction.value = ''
  showOptimizeModal.value = true
}

async function handleAiOptimize() {
  if (!optimizeInstruction.value.trim()) {
    message.warning('请输入优化指令')
    return
  }
  optimizing.value = true
  try {
    const res = await skillAiOptimize(optimizeSkillId.value, optimizeInstruction.value.trim())
    if (res.code === 200) {
      message.success('AI优化成功')
      showOptimizeModal.value = false
    } else {
      message.error(res.msg || '优化失败')
    }
  } catch (e) {
    message.error('优化失败: ' + (e.message || e))
  } finally {
    optimizing.value = false
  }
}

async function viewFiles(skillId) {
  showFilesModal.value = true
  loadingFiles.value = true
  try {
    const res = await skillGetFiles(skillId)
    skillFiles.value = res.data || []
  } catch (e) {
    skillFiles.value = []
  } finally {
    loadingFiles.value = false
  }
}
</script>

<style scoped>
.skill-page {
  padding: 0;
}

.file-content {
  background: var(--n-color-embedded);
  padding: 12px;
  border-radius: 6px;
  font-size: 12px;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
