<template>
  <div class="agent-chat-page">
    <!-- 左侧会话列表 -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <n-button type="primary" block @click="createSession" size="small">
          <template #icon><n-icon><chatbubbles-outline /></n-icon></template>
          新建对话
        </n-button>
      </div>
      <div class="session-list">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: currentSessionId === session.id }"
          @click="switchSession(session.id)"
        >
          <div class="session-info">
            <span class="session-name">{{ session.name || '新对话' }}</span>
            <span class="session-time">{{ formatTime(session.createTime) }}</span>
          </div>
          <n-button quaternary circle size="tiny" @click.stop="deleteSession(session.id)">
            <template #icon><n-icon size="14"><close-outline /></n-icon></template>
          </n-button>
        </div>
      </div>
    </div>

    <!-- 右侧对话区域 -->
    <div class="chat-main">
      <!-- Agent 头部信息 -->
      <div class="chat-header">
        <div class="chat-header-left">
          <n-button v-if="agentId" size="small" text @click="backToBuilder" title="返回Agent设计器">
            <template #icon><n-icon><arrow-back-outline /></n-icon></template>
          </n-button>
          <n-tag v-if="currentAgent" type="info" size="small" :bordered="false">
            {{ currentAgent.agentName || currentAgent.agentCode }}
          </n-tag>
          <span v-if="currentAgent" class="chat-header-title">{{ currentAgent.description }}</span>
        </div>
      </div>
      <template v-if="currentSessionId">
        <!-- 消息区域 -->
        <div class="message-area" ref="messageAreaRef">
          <div v-for="msg in messages" :key="msg.id" :class="['message-bubble', msg.role]">
            <!-- 用户消息 -->
            <template v-if="msg.role === 'user'">
              <div class="message-content user-content">{{ msg.content }}</div>
            </template>

            <!-- 助手消息 -->
            <template v-else>
              <!-- 思考块 -->
              <template v-if="msg.thinking">
                <n-collapse class="thinking-block">
                  <n-collapse-item title="思考过程" name="thinking">
                    <div class="thinking-content">{{ msg.thinking }}</div>
                  </n-collapse-item>
                </n-collapse>
              </template>

              <!-- 工具调用卡片 -->
              <template v-if="msg.toolCalls && msg.toolCalls.length">
                <div v-for="(tc, idx) in msg.toolCalls" :key="idx" class="tool-call-card">
                  <div class="tool-header">
                    <n-tag size="small" type="info">{{ tc.tool }}</n-tag>
                  </div>
                  <div v-if="tc.args" class="tool-args">
                    <span class="tool-label">参数:</span>
                    <code>{{ tc.args }}</code>
                  </div>
                  <div v-if="tc.result" class="tool-result">
                    <span class="tool-label">结果:</span>
                    <pre class="tool-result-content">{{ tc.result }}</pre>
                  </div>
                </div>
              </template>

              <!-- 文本内容 -->
              <div v-if="msg.content" class="message-content assistant-content" v-html="renderMarkdown(msg.content)"></div>

              <!-- 流式光标 -->
              <span v-if="msg.streaming" class="streaming-cursor">|</span>
            </template>
          </div>

          <!-- HITL 确认对话框 -->
          <div v-if="pendingConfirm" class="hitl-confirm">
            <n-card title="需要确认" size="small" :bordered="true">
              <p>工具 <n-tag size="small" type="warning">{{ pendingConfirm.tool }}</n-tag> 请求执行确认</p>
              <div v-if="pendingConfirm.args" class="tool-args">
                <code>{{ pendingConfirm.args }}</code>
              </div>
              <template #action>
                <n-space>
                  <n-button type="error" size="small" @click="handleConfirm(false)">拒绝</n-button>
                  <n-button type="primary" size="small" @click="handleConfirm(true)">确认</n-button>
                </n-space>
              </template>
            </n-card>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <n-input
            v-model:value="inputText"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入消息..."
            @keydown.enter.exact="handleSend"
            :disabled="isStreaming"
          />
          <n-button
            :type="isStreaming ? 'error' : 'primary'"
            size="small"
            @click="isStreaming ? stopChat() : handleSend()"
            :disabled="!isStreaming && !inputText.trim()"
          >
            {{ isStreaming ? '停止' : '发送' }}
          </n-button>
        </div>
      </template>

      <div v-else class="empty-state">
        <n-icon size="48" :depth="3"><chatbubbles-outline /></n-icon>
        <p>选择或创建一个对话开始</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NInput, NIcon, NTag, NCard, NCollapse, NCollapseItem, NSpace, useMessage } from 'naive-ui'
import { ChatbubblesOutline, CloseOutline, ArrowBackOutline } from '@vicons/ionicons5'
import { streamEngineChat, engineResume, agentList, agentGetById } from '@/api/ai'
import { marked } from 'marked'

defineOptions({ name: 'AiAgentChat' })

const route = useRoute()
const router = useRouter()
const message = useMessage()

// 会话管理
const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const inputText = ref('')
const isStreaming = ref(false)
const pendingConfirm = ref(null)
const messageAreaRef = ref(null)
let abortController = null

// Agent 信息
const currentAgent = ref(null)
const agentId = ref(null)

// Agent 选择
const agents = ref([])
const selectedAgentCode = ref('')

async function loadAgent() {
  const id = route.query.agentId
  if (id) {
    agentId.value = Number(id)
    try {
      const res = await agentGetById(agentId.value)
      currentAgent.value = res.data
      if (currentAgent.value) {
        selectedAgentCode.value = currentAgent.value.agentCode
      }
    } catch { /* ignore */ }
  }
  // 同时加载 agent 列表（供侧栏切换）
  try {
    const res = await agentList()
    agents.value = res.data || []
  } catch { /* ignore */ }
}

function backToBuilder() {
  if (agentId.value) {
    router.push({ path: '/ai/agent', query: { agentId: agentId.value, mode: 'builder' } })
  } else {
    router.push('/ai/agent')
  }
}

onMounted(async () => {
  await loadAgent()
  // 如果没有指定 agentId，默认选第一个
  if (!selectedAgentCode.value && agents.value.length > 0) {
    selectedAgentCode.value = agents.value[0].agentCode
  }
})

function createSession() {
  const id = 'session_' + Date.now()
  sessions.value.unshift({
    id,
    name: '新对话',
    createTime: new Date().toISOString(),
  })
  switchSession(id)
}

function switchSession(id) {
  currentSessionId.value = id
  messages.value = []
  pendingConfirm.value = null
}

function deleteSession(id) {
  sessions.value = sessions.value.filter(s => s.id !== id)
  if (currentSessionId.value === id) {
    currentSessionId.value = sessions.value.length > 0 ? sessions.value[0].id : null
    messages.value = []
  }
}

function handleSend(e) {
  if (e && e.shiftKey) return
  if (e) e.preventDefault()
  if (!inputText.value.trim() || isStreaming.value) return
  if (!selectedAgentCode.value) {
    message.warning('请先选择一个Agent')
    return
  }

  const text = inputText.value.trim()
  inputText.value = ''

  // 添加用户消息
  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: text,
  })

  // 添加助手占位消息
  const assistantMsg = {
    id: Date.now() + 1,
    role: 'assistant',
    content: '',
    thinking: '',
    toolCalls: [],
    streaming: true,
  }
  messages.value.push(assistantMsg)

  startStream(text, assistantMsg)
}

function startStream(text, assistantMsg) {
  isStreaming.value = true

  abortController = streamEngineChat(
    {
      agentCode: selectedAgentCode.value,
      sessionId: currentSessionId.value,
      message: text,
    },
    (eventType, data) => {
      handleSSEEvent(eventType, data, assistantMsg)
    },
    () => {
      assistantMsg.streaming = false
      isStreaming.value = false
      scrollToBottom()
    },
    (err) => {
      assistantMsg.streaming = false
      isStreaming.value = false
      message.error('对话失败: ' + (err.message || err))
    }
  )
}

function handleSSEEvent(eventType, data, assistantMsg) {
  switch (eventType) {
    case 'TEXT_BLOCK_DELTA': {
      const text = data?.text || ''
      assistantMsg.content += text
      break
    }
    case 'THINKING_BLOCK_DELTA': {
      const text = data?.text || ''
      assistantMsg.thinking += text
      break
    }
    case 'TOOL_CALL_START': {
      assistantMsg.toolCalls.push({
        tool: data?.tool || '',
        args: data?.args || '',
        result: '',
      })
      break
    }
    case 'TOOL_RESULT_TEXT_DELTA':
    case 'TOOL_RESULT_DATA_DELTA': {
      const lastTool = assistantMsg.toolCalls[assistantMsg.toolCalls.length - 1]
      if (lastTool) {
        lastTool.result += data?.content || ''
      }
      break
    }
    case 'REQUIRE_USER_CONFIRM': {
      pendingConfirm.value = {
        tool: data?.tool || '',
        args: data?.args || '',
      }
      break
    }
    case 'HINT_BLOCK': {
      message.info(data?.hint || '提示信息')
      break
    }
    case 'AGENT_END': {
      assistantMsg.streaming = false
      break
    }
    default:
      break
  }
  scrollToBottom()
}

async function handleConfirm(confirmed) {
  if (!pendingConfirm.value) return
  const tool = pendingConfirm.value.tool
  pendingConfirm.value = null

  try {
    await engineResume(currentSessionId.value, confirmed)
  } catch (e) {
    message.error('确认操作失败')
  }
}

function stopChat() {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  isStreaming.value = false
}

function scrollToBottom() {
  nextTick(() => {
    if (messageAreaRef.value) {
      messageAreaRef.value.scrollTop = messageAreaRef.value.scrollHeight
    }
  })
}

function renderMarkdown(text) {
  if (!text) return ''
  try {
    return marked(text)
  } catch {
    return text
  }
}

function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}
</script>

<style scoped>
.agent-chat-page {
  display: flex;
  height: calc(100vh - 120px);
  background: var(--n-color);
}

.chat-sidebar {
  width: 240px;
  border-right: 1px solid var(--n-border-color);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 12px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 2px;
}

.session-item:hover {
  background: var(--n-color-hover);
}

.session-item.active {
  background: var(--n-color-pressed);
}

.session-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.session-name {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 11px;
  color: var(--n-text-color-3);
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid var(--n-border-color);
  background: var(--n-color-embedded);
}

.chat-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-header-title {
  font-size: 12px;
  color: var(--n-text-color-3);
  margin-left: 8px;
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.message-bubble {
  margin-bottom: 12px;
  max-width: 80%;
}

.message-bubble.user {
  margin-left: auto;
}

.message-bubble.assistant {
  margin-right: auto;
}

.user-content {
  background: var(--n-primary-color);
  color: #fff;
  padding: 8px 12px;
  border-radius: 12px 12px 2px 12px;
  font-size: 14px;
  word-break: break-word;
}

.assistant-content {
  background: var(--n-color-embedded);
  padding: 8px 12px;
  border-radius: 12px 12px 12px 2px;
  font-size: 14px;
  word-break: break-word;
}

.thinking-block {
  margin-bottom: 8px;
  font-size: 12px;
}

.thinking-content {
  color: var(--n-text-color-3);
  font-size: 12px;
  white-space: pre-wrap;
}

.tool-call-card {
  background: var(--n-color-embedded);
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 8px;
  font-size: 12px;
}

.tool-header {
  margin-bottom: 4px;
}

.tool-args, .tool-result {
  margin-top: 4px;
}

.tool-label {
  font-weight: 500;
  margin-right: 4px;
}

.tool-result-content {
  margin: 0;
  max-height: 120px;
  overflow-y: auto;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-all;
}

.streaming-cursor {
  animation: blink 1s step-end infinite;
  color: var(--n-primary-color);
}

@keyframes blink {
  50% { opacity: 0; }
}

.hitl-confirm {
  margin: 12px 0;
  max-width: 400px;
}

.input-area {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--n-border-color);
  align-items: flex-end;
}

.input-area .n-input {
  flex: 1;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--n-text-color-3);
}

.empty-state p {
  margin-top: 12px;
  font-size: 14px;
}
</style>
