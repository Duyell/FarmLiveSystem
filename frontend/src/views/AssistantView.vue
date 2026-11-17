<template>
  <div class="assistant">
    <div class="page-card chat-card">
      <div class="chat-head">
        <div>
          <h2 class="page-title">AI 助手</h2>
          <p class="page-subtitle">咨询直播预约、排队候补、农产品溯源等问题；AI 只做解答，不会替您提交或修改预约。</p>
        </div>
        <el-button size="small" @click="toggleHistory">{{ showHistory ? '收起历史' : '历史记录' }}</el-button>
      </div>

      <div class="chat-body" ref="chatBody">
        <el-empty v-if="messages.length === 0 && !asking" description="问点什么吧，例如：预约直播有哪些时间限制？" />
        <div v-for="(item, index) in messages" :key="index" class="msg" :class="item.role">
          <div class="bubble">
            <div class="text">{{ item.content }}</div>
            <div v-if="item.citations && item.citations.length > 0" class="citations">
              <el-icon class="cite-icon"><Collection /></el-icon>
              <span v-for="(cite, i) in item.citations" :key="i" class="cite-tag">{{ cite }}</span>
            </div>
          </div>
        </div>
        <div v-if="asking" class="msg assistant">
          <div class="bubble typing">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input
          v-model="question"
          placeholder="输入问题，回车发送"
          :disabled="asking"
          maxlength="500"
          @keyup.enter="ask"
        />
        <el-button type="primary" :loading="asking" @click="ask">发送</el-button>
      </div>
    </div>

    <div v-if="showHistory" class="page-card history-card">
      <h3 class="block-title">历史记录</h3>
      <el-empty v-if="history.length === 0" description="暂无历史提问" />
      <div v-for="item in history" :key="item.id" class="history-item" @click="refill(item.question)">
        <div class="history-question">Q：{{ item.question }}</div>
        <div class="history-answer">A：{{ item.answer.slice(0, 80) }}{{ item.answer.length > 80 ? '…' : '' }}</div>
      </div>
      <el-pagination
        v-if="historyTotal > historySize"
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="historyTotal"
        :page-size="historySize"
        :current-page="historyPage"
        @current-change="loadHistory"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Collection } from '@element-plus/icons-vue'
import { askQuestionStream, listQaHistory, type QaRecord } from '../api/ai'

interface Message {
  role: 'user' | 'assistant'
  content: string
  citations?: string[]
}

const messages = reactive<Message[]>([])
const question = ref('')
const asking = ref(false)
const chatBody = ref<HTMLElement | null>(null)

const showHistory = ref(false)
const history = ref<QaRecord[]>([])
const historyPage = ref(1)
const historySize = ref(10)
const historyTotal = ref(0)

function toggleHistory() {
  showHistory.value = !showHistory.value
  if (showHistory.value) loadHistory(1)
}

async function loadHistory(page: number) {
  historyPage.value = page
  try {
    const result = await listQaHistory({ page, size: historySize.value })
    history.value = result.items
    historyTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function refill(text: string) {
  question.value = text
}

// 打字机渲染：后端推送的片段先进缓冲，按固定节奏逐字显示。
// DeepSeek flash 生成速度快（整段 2 秒内推完），直接渲染会看不出流式效果。
const pendingText = ref('')
let typerTimer: number | null = null

function stopTyper() {
  if (typerTimer !== null) {
    window.clearInterval(typerTimer)
    typerTimer = null
  }
}

function startTyper(reply: { content: string }) {
  stopTyper()
  typerTimer = window.setInterval(() => {
    if (pendingText.value.length === 0) return
    // 缓冲积压越多吐字越快（3~10 字/帧），保证长回答不会拖太久
    const take = Math.min(10, Math.max(3, Math.ceil(pendingText.value.length / 24)))
    reply.content += pendingText.value.slice(0, take)
    pendingText.value = pendingText.value.slice(take)
    void scrollToEnd()
  }, 24)
}

function waitForTyperDrain(): Promise<void> {
  return new Promise((resolve) => {
    const timer = window.setInterval(() => {
      if (pendingText.value.length === 0) {
        window.clearInterval(timer)
        resolve()
      }
    }, 40)
  })
}

onUnmounted(stopTyper)

async function ask() {
  const text = question.value.trim()
  if (!text) return
  if (asking.value) return
  question.value = ''
  messages.push({ role: 'user', content: text })
  const reply: { role: 'assistant'; content: string; citations?: string[] } = {
    role: 'assistant',
    content: '',
    citations: []
  }
  messages.push(reply)
  // 注意：reply 是原始对象，直接改它不会触发响应；必须取数组里的 reactive 代理
  const reactiveReply = messages[messages.length - 1]
  asking.value = true
  pendingText.value = ''
  startTyper(reactiveReply)
  await scrollToEnd()
  try {
    const result = await askQuestionStream(text, (delta) => {
      pendingText.value += delta
    })
    await waitForTyperDrain()
    reactiveReply.citations = result.citations
    if (showHistory.value) await loadHistory(1)
  } catch (error) {
    stopTyper()
    pendingText.value = ''
    reactiveReply.content = '回答失败：' + (error as Error).message + '。请稍后重试，或联系管理员检查 AI 配置。'
  } finally {
    stopTyper()
    asking.value = false
    await scrollToEnd()
  }
}

async function scrollToEnd() {
  await nextTick()
  if (chatBody.value) {
    chatBody.value.scrollTop = chatBody.value.scrollHeight
  }
}

onMounted(() => loadHistory(1))
</script>

<style scoped>
.assistant {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-card {
  display: flex;
  flex-direction: column;
}

.chat-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.chat-body {
  height: 420px;
  overflow-y: auto;
  padding: 16px 6px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #f7faf5;
  border-radius: 10px;
  margin-top: 6px;
}

.msg {
  display: flex;
}

.msg.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 76%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
}

.msg.user .bubble {
  background: #2f7d32;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.msg.assistant .bubble {
  background: #ffffff;
  color: #1f3b25;
  border: 1px solid #e3ecdf;
  border-bottom-left-radius: 4px;
  white-space: pre-wrap;
}

.bubble.typing {
  display: flex;
  gap: 5px;
  align-items: center;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #9cc79e;
  animation: blink 1.2s infinite;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}

.dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
  }
  40% {
    opacity: 1;
  }
}

.citations {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e3ecdf;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.cite-icon {
  color: #7a9b7c;
}

.cite-tag {
  font-size: 12px;
  color: #5e6d82;
  background: #f0f6ee;
  border-radius: 4px;
  padding: 2px 8px;
}

.chat-input {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.history-card {
  margin-top: 0;
}

.block-title {
  margin: 0 0 12px;
  font-size: 15px;
  color: #1f3b25;
}

.history-item {
  border: 1px solid #eef1f4;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: box-shadow 0.2s;
}

.history-item:hover {
  box-shadow: 0 3px 10px rgba(31, 59, 37, 0.08);
}

.history-question {
  font-size: 13px;
  font-weight: 600;
  color: #1f3b25;
}

.history-answer {
  font-size: 12px;
  color: #7a8699;
  margin-top: 4px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
