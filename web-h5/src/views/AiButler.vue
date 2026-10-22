<!--
  AI 智能管家 — 豆包式布局重构
  左栏：对话历史（桌面永久可见 / 移动端汉堡菜单呼出）
  右栏：消息聊天区
  配色：首页麦田暖色系（amber/gold/wheat）
  悬浮按钮：适老 + AI 按钮均支持拖动
-->
<template>
  <div class="butler-page">
    <!-- 移动端遮罩 -->
    <div v-if="sidebarOpen" class="sidebar-overlay" @click="sidebarOpen = false"></div>

    <!-- 左栏：对话历史 -->
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-inner">
        <div class="sidebar-hd">
          <h3>对话记录</h3>
          <button class="btn-new-chat" @click="onNewChat">
            <van-icon name="plus" size="18" />
            <span>新建对话</span>
          </button>
        </div>

        <div v-if="convLoading" class="sidebar-loading">加载中...</div>
        <div v-else-if="conversations.length === 0" class="sidebar-empty">
          <van-icon name="chat-o" size="40" color="#ccc" />
          <p>暂无对话</p>
          <button class="btn-start" @click="onNewChat">开始新对话</button>
        </div>
        <div v-else class="sidebar-list">
          <div
            v-for="c in conversations"
            :key="c.id"
            class="conv-row"
            :class="{ active: c.id === conversationId }"
            @click="onSwitchConv(c)"
          >
            <div class="conv-icon">
              <van-icon v-if="c.pinned" name="star" color="#d97706" size="14" />
              <van-icon v-else name="chat-o" color="#aaa" size="14" />
            </div>
            <div class="conv-info">
              <div class="conv-title" v-if="renamingId !== c.id">{{ c.title }}</div>
              <input
                v-else
                v-model="renameText"
                class="conv-rename-input"
                @blur="onRenameBlur(c)"
                @keyup.enter="onRenameBlur(c)"
                @click.stop
              />
              <div class="conv-meta">
                <span>{{ formatTime(c.updatedAt) }}</span>
                <span class="conv-count">{{ c.msgCount || 0 }}条</span>
              </div>
            </div>
            <div class="conv-actions" @click.stop>
              <van-icon name="wap-nav" size="14" @click="onStartRename(c)" />
              <van-icon
                :name="c.pinned ? 'star' : 'star-o'"
                size="14"
                :color="c.pinned ? '#d97706' : '#aaa'"
                @click="onPinConv(c)"
              />
              <van-icon name="delete-o" size="14" color="#e74c3c" @click="onDeleteConv(c)" />
            </div>
          </div>
        </div>

        <!-- 侧栏底部快捷入口 -->
        <div class="sidebar-ft">
          <div class="sidebar-link" @click="$router.push('/home')">
            <van-icon name="home-o" size="16" />
            <span>返回首页</span>
          </div>
          <div class="sidebar-link" @click="$router.push('/mine')">
            <van-icon name="user-o" size="16" />
            <span>个人资料</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右栏：聊天主区 -->
    <div class="main">
      <!-- 顶栏（玻璃拟态） -->
      <header class="topbar">
        <div class="topbar-left">
          <van-icon
            name="wap-nav"
            size="22"
            class="menu-btn"
            @click="sidebarOpen = !sidebarOpen"
          />
          <span class="topbar-title">{{ currentTitle }}</span>
        </div>
        <van-icon name="add-o" size="22" class="new-btn" @click="onNewChat" />
      </header>

      <!-- 消息区 -->
      <div class="body" ref="bodyRef">
        <!-- 欢迎卡片 -->
        <div v-if="showWelcome" class="welcome-area">
          <div class="welcome-card">
            <div class="wc-avatar">🤖</div>
            <h2 class="wc-greeting">{{ welcome.greeting || '你好！' }}</h2>
            <p class="wc-sub">我是您的AI管家，可以帮您匹配政策、申报补贴、查询进度</p>

            <!-- 数据摘要 -->
            <div class="wc-summary" v-if="welcome.summary">
              <div class="wc-stat" @click="$router.push('/policies')">
                <span class="wc-num green">{{ welcome.summary.matchedPolicies || 0 }}</span>
                <span class="wc-label">可匹配政策</span>
              </div>
              <div class="wc-stat" @click="$router.push('/mine/applies')">
                <span class="wc-num orange">{{ welcome.summary.pendingApplies || 0 }}</span>
                <span class="wc-label">待处理申报</span>
              </div>
              <div class="wc-stat" @click="$router.push('/mine')">
                <span class="wc-num amber">{{ welcome.summary.profileCompleteness || 0 }}%</span>
                <span class="wc-label">资料完善度</span>
              </div>
            </div>

            <!-- 快捷入口 -->
            <div class="wc-links" v-if="welcome.quickLinks && welcome.quickLinks.length">
              <div
                v-for="l in welcome.quickLinks"
                :key="l.path"
                class="wc-link"
                @click="$router.push(l.path)"
              >
                <span class="wc-link-icon">{{ l.icon }}</span>
                <span class="wc-link-label">{{ l.label }}</span>
                <van-icon name="arrow" size="12" color="#aaa" />
              </div>
            </div>

            <!-- 建议词 -->
            <div class="wc-suggest" v-if="welcome.quickSuggestions && welcome.quickSuggestions.length">
              <span
                v-for="s in welcome.quickSuggestions"
                :key="s"
                class="suggest-chip"
                @click="onQuickSend(s)"
              >{{ s }}</span>
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, i) in messages" :key="i" class="msg-block">
          <!-- 用户消息 -->
          <div v-if="msg.role === 'USER'" class="msg-row user">
            <div class="msg-bubble user-bubble">{{ msg.content }}</div>
          </div>

          <!-- AI 动作提示 -->
          <div v-if="msg.action" class="msg-row">
            <div class="action-tip">
              <van-loading v-if="msg.action === 'loading'" size="14" color="#d97706" />
              <span>{{ actionLabel(msg.action) }}</span>
            </div>
          </div>

          <!-- AI 文本 -->
          <div v-if="msg.role === 'AI' && msg.content" class="msg-row ai">
            <div class="msg-bubble ai-bubble" v-html="formatMsgContent(msg.content)"></div>
          </div>

          <!-- ===== 政策列表卡片（内联渲染，解决导航返回后点击失效 BUG） ===== -->
          <div v-if="msg.card && msg.card.type === 'POLICY_LIST'" class="msg-row">
            <div class="data-card policy-list-card">
              <div class="card-header">
                <span class="card-header-icon">📋</span>
                <span>匹配政策</span>
                <span v-if="cardSummary(msg)" class="card-header-summary">
                  共 {{ cardSummary(msg).total }} 项 · 🟢{{ cardSummary(msg).hit }} · 🟡{{ cardSummary(msg).partial }} · ⚪{{ cardSummary(msg).other }}
                </span>
              </div>
              <div
                v-for="p in visibleCardPolicies(msg)"
                :key="p.id"
                class="policy-item"
                @click="p.navigateTo ? onCardNavigate(p.navigateTo) : onApplyPolicy(p)"
              >
                <div class="policy-info">
                  <span class="policy-emoji">{{ policyMatchEmoji(p.matchLevel) }}</span>
                  <span class="policy-title">{{ p.title }}</span>
                  <span class="policy-level" :class="p.matchLevel === '命中' ? 'hit' : 'partial'">{{ p.matchLevel }}</span>
                </div>
                <span v-if="p.deadlineText" class="policy-deadline">📅 {{ p.deadlineText }}</span>
                <div class="policy-actions">
                  <van-button v-if="p.navigateTo" size="mini" color="#d97706" round @click.stop="onCardNavigate(p.navigateTo)">查看详情</van-button>
                  <van-button v-if="p.matchLevel !== '不匹配'" size="mini" plain type="success" round style="margin-left:6px" @click.stop="onApplyPolicy(p)">立即申报</van-button>
                </div>
              </div>
              <!-- 2026-10-18 防刷屏：相关项优先展示，其余折叠，按需展开 -->
              <button v-if="hiddenCardCount(msg) > 0" class="card-fold-btn" @click="toggleCardExpand(msg)">
                {{ msg._cardExpanded ? '收起 ▲' : '展开其余 ' + hiddenCardCount(msg) + ' 项 ▼' }}
              </button>
            </div>
          </div>

          <!-- ===== 用户资料卡片 ===== -->
          <div v-else-if="msg.card && msg.card.type === 'USER_PROFILE' && msg.card.profile" class="msg-row">
            <div class="data-card profile-card">
              <div class="card-header">
                <span class="card-header-icon">👤</span>
                <span>您的资料</span>
              </div>
              <div class="profile-grid">
                <div class="pf-item"><span class="pf-label">姓名</span><span>{{ msg.card.profile.name || '-' }}</span></div>
                <div class="pf-item"><span class="pf-label">种植类型</span><span>{{ msg.card.profile.plantType || '-' }}</span></div>
                <div class="pf-item"><span class="pf-label">种植面积</span><span>{{ msg.card.profile.plantArea ? msg.card.profile.plantArea + '亩' : '-' }}</span></div>
                <div class="pf-item"><span class="pf-label">经营主体</span><span>{{ msg.card.profile.subjectType || '-' }}</span></div>
              </div>
              <div v-if="msg.card.completeness !== undefined" class="profile-completeness">
                <span class="pc-label">资料完善度 {{ msg.card.completeness }}%</span>
                <div class="pc-bar"><div class="pc-fill" :style="{ width: msg.card.completeness + '%' }"></div></div>
              </div>
              <van-button v-if="msg.card.navigateTo" size="small" color="#d97706" round class="card-nav-btn" @click="onCardNavigate(msg.card.navigateTo)">完善资料 →</van-button>
            </div>
          </div>

          <!-- ===== 申报表单卡片 ===== -->
          <div v-else-if="msg.card && msg.card.type === 'APPLY_FORM'" class="msg-row">
            <div class="data-card apply-form-card">
              <div class="card-header">
                <span class="card-header-icon">{{ msg.card.readyToSubmit ? '✅' : '📝' }}</span>
                <span>{{ msg.card.readyToSubmit ? '确认提交' : '申报材料' }}</span>
              </div>
              <div class="apply-form-body">
                <p class="af-policy">政策：{{ msg.card.policyTitle || '' }}</p>
                <van-button v-if="msg.card.navigateTo" size="small" color="#d97706" round class="card-nav-btn" @click="onCardNavigate(msg.card.navigateTo)">{{ msg.card.navigateLabel || '前往填写 →' }}</van-button>
              </div>
            </div>
          </div>

          <!-- ===== 申报列表卡片 ===== -->
          <div v-else-if="msg.card && msg.card.type === 'APPLY_LIST'" class="msg-row">
            <div class="data-card apply-list-card">
              <div class="card-header">
                <span class="card-header-icon">📂</span>
                <span>我的申报</span>
              </div>
              <div
                v-for="a in msg.card.applies"
                :key="a.id"
                class="apply-item"
                @click="a.navigateTo && onCardNavigate(a.navigateTo)"
              >
                <span class="ai-policy">{{ a.policy_title }}</span>
                <span class="ai-status">{{ a.statusLabel || a.status }}</span>
                <van-icon v-if="a.navigateTo" name="arrow" size="14" color="#aaa" />
              </div>
            </div>
          </div>

          <!-- 兜底导航按钮 -->
          <div v-if="msg.card && msg.card.navigateTo && !['POLICY_LIST','USER_PROFILE','APPLY_LIST'].includes(msg.card.type)" class="msg-row">
            <button class="nav-btn" @click="onCardNavigate(msg.card.navigateTo)">
              {{ msg.card.navigateLabel || '点击跳转 →' }}
            </button>
          </div>
        </div>

        <!-- SSE 快捷建议条 -->
        <div v-if="quickSuggestions.length" class="msg-row quick-row">
          <div class="quick-inner">
            <span class="quick-hint">{{ quickLabel }}</span>
            <div class="suggest-chips inline">
              <span
                v-for="s in quickSuggestions"
                :key="s"
                class="suggest-chip"
                @click="onQuickSend(s)"
              >{{ s }}</span>
            </div>
            <button
              v-if="quickNavigateTo"
              class="nav-btn"
              @click="onCardNavigate(quickNavigateTo)"
            >{{ quickNavigateLabel }}</button>
          </div>
        </div>

        <!-- 等待中 -->
        <div v-if="waiting" class="msg-row">
          <div class="typing-dots"><span></span><span></span><span></span></div>
        </div>

        <div class="body-spacer"></div>
      </div>

      <!-- 底部快捷栏 -->
      <div class="quick-bar">
        <span class="quick-chip" @click="onQuickSend('我的资料')">📋 我的资料</span>
        <span class="quick-chip" @click="onQuickSend('匹配政策')">🎯 匹配政策</span>
        <span class="quick-chip" @click="$router.push('/mine/applies')">📂 我的申报</span>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <div class="input-row">
          <van-icon
            name="service-o"
            size="22"
            class="voice-btn"
            :class="{ recording: voiceActive }"
            @touchstart.prevent="startVoice"
            @touchend.prevent="stopVoice"
          />
          <input
            ref="inputRef"
            v-model="inputText"
            class="msg-input"
            placeholder="输入您的问题..."
            @keyup.enter="onSend"
          />
          <button
            class="send-btn"
            :class="{ active: inputText.trim() && !waiting }"
            :disabled="!inputText.trim() || waiting"
            @click="onSend"
          >
            <van-icon name="arrow-up" size="20" />
          </button>
        </div>
      </div>
    </div>

    <!-- 语音浮层 -->
    <van-overlay :show="voiceActive" class="voice-overlay">
      <div class="voice-popup">
        <div class="voice-wave">🎤</div>
        <p>正在聆听...</p>
      </div>
    </van-overlay>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import {
  agentWelcome, agentChatStream,
  listConversations, getConversation, createConversation,
  renameConversation, togglePinConversation, deleteConversation
} from '@/api/ai'

const router = useRouter()

// ===== 状态 =====
const bodyRef = ref(null)
const inputRef = ref(null)
const inputText = ref('')
const waiting = ref(false)
const convLoading = ref(false)
const voiceActive = ref(false)
const sidebarOpen = ref(false)

const conversationId = ref(null)
const conversations = ref([])
const messages = ref([])
const welcome = ref({ greeting: '你好！', summary: null, quickSuggestions: [], quickLinks: [] })

const quickSuggestions = ref([])
const quickLabel = ref('')
const quickNavigateTo = ref(null)
const quickNavigateLabel = ref('')

const renamingId = ref(null)
const renameText = ref('')

// ===== 计算属性 =====
const showWelcome = computed(() => messages.value.length === 0 && !waiting.value)
const currentTitle = computed(() => {
  const conv = conversations.value.find(c => c.id === conversationId.value)
  return conv ? conv.title : 'AI 智能管家'
})

const SESSION_KEY = 'xct_butler_conv_id'

// ===== 初始化 =====
onMounted(async () => {
  await loadConversations()
  await loadWelcome()
  // 自动恢复上次对话
  const savedConvId = sessionStorage.getItem(SESSION_KEY)
  if (savedConvId) {
    const conv = conversations.value.find(c => String(c.id) === savedConvId)
    if (conv) await onSwitchConv(conv)
  }
})

async function loadWelcome() {
  try {
    const data = await agentWelcome()
    if (data && data.greeting) welcome.value = data
  } catch (e) { /* 忽略 */ }
}

async function loadConversations() {
  convLoading.value = true
  try {
    const list = await listConversations()
    conversations.value = Array.isArray(list) ? list : []
  } catch (e) { /* 忽略 */ }
  finally { convLoading.value = false }
}

// ===== 对话操作 =====
async function onSwitchConv(conv) {
  sidebarOpen.value = false
  conversationId.value = conv.id
  sessionStorage.setItem(SESSION_KEY, conv.id)
  messages.value = []
  waiting.value = false
  clearQuickSuggestions()
  try {
    const detail = await getConversation(conv.id)
    if (detail && detail.messages) {
      messages.value = (detail.messages || []).flatMap(m => {
        const items = []
        if (m.question) items.push({ role: 'USER', content: m.question, action: null, card: null })
        if (m.answer) {
          items.push({ role: 'AI', content: m.answer || '', action: null, card: parseCardData(m.cardData) })
        }
        return items
      })
    }
  } catch (e) { showToast('加载对话失败') }
  scrollToBottom()
}

async function onNewChat() {
  try {
    const conv = await createConversation()
    if (conv && conv.id) {
      conversations.value.unshift(conv)
      conversationId.value = conv.id
      sessionStorage.setItem(SESSION_KEY, conv.id)
      messages.value = []
      waiting.value = false
      sidebarOpen.value = false
      clearQuickSuggestions()
      await loadWelcome()
    }
  } catch (e) { showToast('创建对话失败') }
  scrollToBottom()
}

async function onDeleteConv(conv) {
  try {
    await showConfirmDialog({ title: '确认删除', message: '删除后消息不可恢复' })
    await deleteConversation(conv.id)
    conversations.value = conversations.value.filter(c => c.id !== conv.id)
    if (conversationId.value === conv.id) {
      conversationId.value = null
      sessionStorage.removeItem(SESSION_KEY)
      messages.value = []
      clearQuickSuggestions()
    }
  } catch (e) { /* 取消 */ }
}

async function onPinConv(conv) {
  try {
    await togglePinConversation(conv.id)
    await loadConversations()
  } catch (e) { /* 忽略 */ }
}

function onStartRename(conv) {
  renamingId.value = conv.id
  renameText.value = conv.title
  nextTick(() => {
    const inp = document.querySelector('.conv-rename-input')
    if (inp) inp.focus()
  })
}

async function onRenameBlur(conv) {
  const newTitle = renameText.value.trim()
  renamingId.value = null
  if (newTitle && newTitle !== conv.title) {
    try {
      await renameConversation(conv.id, newTitle)
      conv.title = newTitle
    } catch (e) { showToast('改名失败') }
  }
}

// ===== 消息 =====
function formatMsgContent(content) {
  if (!content) return ''
  return content.replace(/\\n/g, '<br>').replace(/\n/g, '<br>')
}

async function onSend() {
  const text = inputText.value.trim()
  if (!text || waiting.value) return
  inputText.value = ''
  waiting.value = true
  clearQuickSuggestions()

  messages.value.push({ role: 'USER', content: text })
  scrollToBottom()

  if (!conversationId.value) {
    try {
      const conv = await createConversation()
      if (conv && conv.id) {
        conversationId.value = conv.id
        sessionStorage.setItem(SESSION_KEY, conv.id)
        conversations.value.unshift(conv)
      }
    } catch (e) {
      showToast('创建对话失败，请稍后再试')
      waiting.value = false
      return
    }
  }

  try {
    const response = await agentChatStream({ conversationId: conversationId.value, message: text })
    await handleSSE(response)
  } catch (e) {
    messages.value.push({ role: 'AI', content: '网络连接失败，请稍后再试' })
    waiting.value = false
  }

  await loadConversations()
}

function onQuickSend(text) { inputText.value = text; onSend() }

function onCardNavigate(path) {
  if (!path) return
  if (typeof path === 'string') router.push(path)
}

function onApplyPolicy(policy) {
  if (policy.applyNavigateTo) { router.push(policy.applyNavigateTo); return }
  if (policy.id) { router.push('/apply/materials?policyId=' + policy.id); return }
  inputText.value = '帮我申报' + policy.title
  onSend()
}

// ===== SSE 处理 =====
async function handleSSE(response) {
  if (!response.ok) {
    let errMsg = '服务器异常'
    try { const err = await response.json(); errMsg = err.message || errMsg } catch (e) { /* */ }
    throw new Error(errMsg)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentAiIdx = -1
  let currentAction = null

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''

    for (const eventBlock of events) {
      if (!eventBlock.trim()) continue
      const lines = eventBlock.split('\n')
      let eventType = 'token', dataStr = ''

      for (const line of lines) {
        if (line.startsWith('event:')) eventType = line.slice(6).trim()
        else if (line.startsWith('data:')) {
          if (dataStr) dataStr += '\n'
          dataStr += line.slice(5)
        }
      }
      if (!dataStr) continue

      let data = null
      try { data = JSON.parse(dataStr) } catch (e) { /* 纯文本 */ }

      if (eventType === 'action') {
        currentAction = data ? data.agentAction : null
        messages.value.push({ role: 'AI', content: '', action: currentAction, card: null })
        currentAiIdx = messages.value.length - 1
      } else if (eventType === 'token') {
        const text = data && data.answer ? data.answer : (data && typeof data === 'object' ? '' : dataStr)
        if (currentAiIdx < 0) {
          messages.value.push({ role: 'AI', content: text, action: currentAction, card: null })
          currentAiIdx = messages.value.length - 1
        } else {
          messages.value[currentAiIdx].content += text
        }
      } else if (eventType === 'card') {
        if (currentAiIdx >= 0 && data) {
          messages.value[currentAiIdx].card = data
          messages.value[currentAiIdx].action = currentAction
        }
      } else if (eventType === 'quick') {
        if (data) {
          quickSuggestions.value = data.suggestions || []
          quickLabel.value = data.label || ''
          quickNavigateTo.value = data.navigateTo || null
          quickNavigateLabel.value = data.navigateLabel || ''
        }
      } else if (eventType === 'done') {
        waiting.value = false
        if (currentAiIdx >= 0) {
          messages.value[currentAiIdx].action = null
          const card = messages.value[currentAiIdx].card
          if (card && card.autoNavigate && card.navigateTo) {
            const path = card.navigateTo
            setTimeout(() => router.push(path), 800)
          }
        }
        currentAiIdx = -1; currentAction = null
      } else if (eventType === 'error') {
        waiting.value = false
        messages.value.push({ role: 'AI', content: (data && data.message) || 'AI 暂时不可用' })
      }
    }
    scrollToBottom()
  }
  waiting.value = false
}

function clearQuickSuggestions() {
  quickSuggestions.value = []
  quickLabel.value = ''
  quickNavigateTo.value = null
  quickNavigateLabel.value = ''
}

// ===== 语音 =====
let recognition = null
function startVoice() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRecognition) { showToast('当前浏览器不支持语音输入'); return }
  voiceActive.value = true
  recognition = new SpeechRecognition()
  recognition.lang = 'zh-CN'; recognition.interimResults = false; recognition.continuous = false
  recognition.onresult = (e) => { inputText.value = e.results[0][0].transcript }
  recognition.onerror = () => { showToast('语音识别失败'); voiceActive.value = false }
  recognition.onend = () => { voiceActive.value = false }
  recognition.start()
}
function stopVoice() { if (recognition) recognition.stop(); voiceActive.value = false }

// ===== 工具 =====
function scrollToBottom() {
  nextTick(() => {
    if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
  })
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t), now = new Date(), diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN')
}

function actionLabel(action) {
  const map = {
    VIEW_PROFILE: '正在查看您的资料...', MATCH_POLICY: '正在为您匹配政策...',
    START_APPLY: '正在准备申报材料...', FILL_FIELD: '正在填写申报信息...',
    SUBMIT_APPLY: '正在提交申报...', MY_APPLIES: '正在查询申报进度...',
    SEARCH_KB: '正在搜索相关知识...', FREE_CHAT: '正在思考...',
    CANCEL_ACTION: '已取消操作', POLICY_DETAIL: '正在查找政策详情...',
    MODIFY_FIELD: '正在修改信息...', APPLY_STATUS: '正在查询状态...',
  }
  return map[action] || '处理中...'
}

function parseCardData(cardData) {
  if (!cardData) return null
  try { return typeof cardData === 'string' ? JSON.parse(cardData) : cardData }
  catch (e) { return null }
}

function policyMatchEmoji(level) {
  if (level === '命中') return '🟢'
  if (level === '部分匹配') return '🟡'
  return '⚪'
}

// ===== 2026-10-18 政策卡片防刷屏：相关项优先 + 折叠 =====
// 与后端 AgentService.POLICY_CARD_PREVIEW_LIMIT 保持一致。
// 后端保证命中/部分匹配已排在前面，这里再按等级白名单兜底一次，
// 老历史卡片（无 summary）同样生效。
const POLICY_CARD_PREVIEW_LIMIT = 20

function cardSummary(msg) {
  const policies = msg?.card?.policies
  if (msg?.card?.summary) return msg.card.summary
  if (!Array.isArray(policies) || !policies.length) return null
  const hit = policies.filter(p => p.matchLevel === '命中').length
  const partial = policies.filter(p => p.matchLevel === '部分匹配').length
  return { total: policies.length, hit, partial, other: policies.length - hit - partial }
}

function isRelevantPolicy(p) {
  return p?.matchLevel === '命中' || p?.matchLevel === '部分匹配'
}

function visibleCardPolicies(msg) {
  const all = msg?.card?.policies
  if (!Array.isArray(all)) return []
  if (msg._cardExpanded) return all
  const relevant = all.filter(isRelevantPolicy)
  const rest = all.filter(p => !isRelevantPolicy(p))
  // 相关项优先展示（超出上限的也折叠），其余全部折叠
  return relevant.slice(0, POLICY_CARD_PREVIEW_LIMIT).concat(rest.slice(0, 0))
}

function hiddenCardCount(msg) {
  const all = msg?.card?.policies
  if (!Array.isArray(all)) return 0
  if (msg._cardExpanded) return 0
  const relevant = all.filter(isRelevantPolicy).length
  return Math.max(0, all.length - Math.min(relevant, POLICY_CARD_PREVIEW_LIMIT))
}

function toggleCardExpand(msg) {
  msg._cardExpanded = !msg._cardExpanded
}
</script>

<style scoped>
/* ===== 全局布局 ===== */
.butler-page {
  display: grid;
  grid-template-columns: 280px 1fr;
  height: calc(100vh - var(--van-tabbar-height, 56px));
  background: linear-gradient(170deg, #fffbeb 0%, #fef3c7 40%, #f0fdf4 100%);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  overflow: hidden;
  position: relative;
}

/* ===== 侧栏遮罩（移动端） ===== */
.sidebar-overlay {
  display: none;
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.35);
  z-index: 90;
}

/* ===== 左栏：对话历史侧栏 ===== */
.sidebar {
  display: flex;
  flex-direction: column;
  background: rgba(255,253,245,0.95);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-right: 1px solid rgba(217,119,6,0.12);
  overflow: hidden;
  z-index: 100;
}
.sidebar-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.sidebar-hd {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px; border-bottom: 1px solid rgba(217,119,6,0.1);
  flex-shrink: 0;
}
.sidebar-hd h3 { margin: 0; font-size: 17px; font-weight: 700; color: #78350f; }

.btn-new-chat {
  display: flex; align-items: center; gap: 4px;
  padding: 7px 14px; border: none; border-radius: 20px;
  background: linear-gradient(135deg, #d97706, #b45309);
  color: #fff; font-size: 13px; font-weight: 600; cursor: pointer;
  transition: opacity 0.2s;
}
.btn-new-chat:active { opacity: 0.85; }

.sidebar-loading, .sidebar-empty {
  flex: 1; display: flex; flex-direction: column; align-items: center;
  justify-content: center; color: #999; font-size: 14px; gap: 12px;
}
.btn-start {
  padding: 8px 20px; border: 2px solid #d97706; border-radius: 20px;
  background: transparent; color: #d97706; font-size: 14px; font-weight: 600; cursor: pointer;
}
.btn-start:active { background: #fef3c7; }

.sidebar-list {
  flex: 1; overflow-y: auto; padding: 8px 0;
}

.conv-row {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 16px; cursor: pointer; transition: background 0.15s;
  position: relative;
}
.conv-row:hover { background: rgba(217,119,6,0.06); }
.conv-row.active { background: rgba(254,243,199,0.7); border-right: 3px solid #d97706; }

.conv-icon { flex-shrink: 0; width: 28px; text-align: center; }
.conv-info { flex: 1; min-width: 0; }
.conv-title { font-size: 14px; font-weight: 500; color: #451a03; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-meta { display: flex; gap: 8px; font-size: 11px; color: #999; margin-top: 2px; }
.conv-count { background: #fef3c7; border-radius: 8px; padding: 0 6px; font-size: 10px; color: #78350f; }

.conv-actions { display: flex; gap: 10px; opacity: 0; transition: opacity 0.2s; flex-shrink: 0; }
.conv-row:hover .conv-actions { opacity: 1; }
.conv-actions .van-icon { padding: 2px; cursor: pointer; }

.conv-rename-input {
  width: 100%; border: 1px solid #d97706; border-radius: 4px;
  padding: 2px 6px; font-size: 13px; outline: none; background: #fff;
}

/* 侧栏底部 */
.sidebar-ft {
  border-top: 1px solid rgba(217,119,6,0.1);
  padding: 12px 16px; display: flex; gap: 20px; flex-shrink: 0;
}
.sidebar-link {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; color: #78350f; cursor: pointer; transition: color 0.2s;
}
.sidebar-link:active { color: #d97706; }

/* ===== 右栏：聊天主区 ===== */
.main {
  display: flex; flex-direction: column;
  overflow: hidden; min-width: 0;
}

/* 顶栏（玻璃拟态） */
.topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 16px;
  background: rgba(255,253,245,0.85);
  backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(217,119,6,0.1);
  flex-shrink: 0; z-index: 50;
}
.topbar-left { display: flex; align-items: center; gap: 10px; }
.menu-btn { color: #78350f; cursor: pointer; flex-shrink: 0; display: none; }
.topbar-title { font-size: 16px; font-weight: 700; color: #451a03; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.new-btn { color: #78350f; cursor: pointer; }

/* 消息区 */
.body {
  flex: 1; overflow-y: auto; padding: 16px;
  display: flex; flex-direction: column; gap: 14px;
  scroll-behavior: smooth;
}
.body-spacer { height: 24px; flex-shrink: 0; }

/* ===== 欢迎卡片 ===== */
.welcome-area { display: flex; justify-content: center; padding-top: 10px; }
.welcome-card {
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(255,255,255,0.6);
  border-radius: 20px; padding: 28px 24px;
  width: 100%; max-width: 420px;
  box-shadow: 0 8px 32px rgba(217,119,6,0.06);
  text-align: center;
}
.wc-avatar { font-size: 48px; margin-bottom: 8px; }
.wc-greeting { font-size: 22px; font-weight: 700; color: #451a03; margin: 0 0 6px; }
.wc-sub { font-size: 14px; color: #78350f; margin: 0 0 20px; line-height: 1.4; }

.wc-summary { display: flex; gap: 10px; margin-bottom: 16px; }
.wc-stat {
  flex: 1; text-align: center;
  background: rgba(254,243,199,0.5); border-radius: 14px;
  padding: 14px 8px; cursor: pointer; transition: transform 0.15s;
}
.wc-stat:active { transform: scale(0.96); }
.wc-num { font-size: 26px; font-weight: 800; display: block; }
.wc-num.green { color: #65a30d; }
.wc-num.orange { color: #d97706; }
.wc-num.amber { color: #b45309; }
.wc-label { font-size: 11px; color: #78350f; margin-top: 2px; }

.wc-links { margin-bottom: 14px; text-align: left; }
.wc-link {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; background: rgba(254,243,199,0.4);
  border-radius: 12px; margin-bottom: 6px; cursor: pointer;
  transition: background 0.15s;
}
.wc-link:active { background: rgba(254,243,199,0.8); }
.wc-link-icon { font-size: 18px; }
.wc-link-label { flex: 1; font-size: 14px; font-weight: 500; color: #451a03; }

.wc-suggest { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }

/* ===== 消息气泡 ===== */
.msg-block { display: flex; flex-direction: column; }
.msg-row { display: flex; max-width: 85%; }
.msg-row.user { align-self: flex-end; flex-direction: row-reverse; }
.msg-row.ai { align-self: flex-start; }

.msg-bubble { padding: 12px 16px; border-radius: 18px; font-size: 15px; line-height: 1.55; word-break: break-word; white-space: pre-wrap; }
.user-bubble {
  background: linear-gradient(135deg, #d97706, #b45309);
  color: #fff; border-bottom-right-radius: 4px;
}
.ai-bubble {
  background: #fff; color: #451a03;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(217,119,6,0.06);
}

/* 动作提示 */
.action-tip {
  display: flex; align-items: center; gap: 8px;
  font-size: 13px; color: #d97706; background: #fef3c7;
  padding: 8px 14px; border-radius: 20px; align-self: flex-start;
}

/* 打字动画 */
.typing-dots {
  display: flex; gap: 5px; padding: 14px 18px;
  background: #fff; border-radius: 18px; border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(217,119,6,0.06);
}
.typing-dots span {
  width: 9px; height: 9px; background: #d97706; border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out both;
}
.typing-dots span:nth-child(1) { animation-delay: 0s; }
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.3; }
  40% { transform: scale(1); opacity: 1; }
}

/* 建议词 */
.suggest-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.suggest-chips.inline { display: inline-flex; }
.suggest-chip {
  background: rgba(254,243,199,0.7); color: #78350f;
  font-size: 13px; padding: 7px 15px; border-radius: 18px;
  cursor: pointer; transition: all 0.2s; white-space: nowrap;
  border: 1px solid rgba(217,119,6,0.15);
}
.suggest-chip:active { background: #d97706; color: #fff; }

/* ===== 底部快捷栏 ===== */
.quick-bar {
  display: flex; gap: 8px; padding: 8px 16px;
  background: rgba(255,253,245,0.8); border-top: 1px solid rgba(217,119,6,0.08);
  overflow-x: auto; flex-shrink: 0;
}
.quick-chip {
  flex-shrink: 0; font-size: 13px; padding: 7px 15px;
  background: rgba(255,255,255,0.7); border: 1px solid rgba(217,119,6,0.15);
  border-radius: 16px; cursor: pointer; white-space: nowrap;
  color: #78350f; transition: all 0.2s;
}
.quick-chip:active { background: #fef3c7; border-color: #d97706; }

/* ===== SSE 快捷建议 ===== */
.quick-row { align-self: flex-start; }
.quick-inner { display: flex; flex-direction: column; gap: 8px; }
.quick-hint { font-size: 12px; color: #999; }

/* 导航按钮 */
.nav-btn {
  padding: 8px 18px; border: none; border-radius: 20px;
  background: linear-gradient(135deg, #d97706, #b45309);
  color: #fff; font-size: 14px; font-weight: 600;
  cursor: pointer; transition: opacity 0.2s; align-self: flex-start;
}
.nav-btn:active { opacity: 0.85; }

/* ===== 输入区 ===== */
.input-area {
  padding: 8px 16px calc(8px + env(safe-area-inset-bottom));
  background: rgba(255,255,255,0.9);
  backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
  border-top: 1px solid rgba(217,119,6,0.1); flex-shrink: 0;
}
.input-row {
  display: flex; align-items: center; gap: 10px;
  background: rgba(254,243,199,0.3); border-radius: 24px;
  padding: 6px 8px 6px 16px;
  border: 1px solid rgba(217,119,6,0.12);
}
.msg-input {
  flex: 1; border: none; background: transparent;
  font-size: 15px; outline: none; color: #451a03; min-width: 0; line-height: 28px;
}
.msg-input::placeholder { color: #bbb; }
.voice-btn { color: #78350f; flex-shrink: 0; cursor: pointer; transition: color 0.2s; }
.voice-btn.recording { color: #e74c3c; animation: voicePulse 1s infinite; }

.send-btn {
  width: 36px; height: 36px; border: none; border-radius: 50%;
  background: #ccc; color: #fff;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; cursor: pointer; transition: all 0.2s;
}
.send-btn.active { background: linear-gradient(135deg, #d97706, #b45309); }
.send-btn:disabled { cursor: not-allowed; }

/* ===== 语音浮层 ===== */
.voice-overlay { display: flex; align-items: center; justify-content: center; }
.voice-popup { text-align: center; color: #fff; }
.voice-wave { font-size: 64px; animation: voicePulse 1.5s ease-in-out infinite; }
@keyframes voicePulse { 0%, 100% { transform: scale(1); opacity: 0.8; } 50% { transform: scale(1.2); opacity: 1; } }
.voice-popup p { font-size: 16px; margin-top: 12px; }

/* ===== 数据卡片通用 ===== */
.data-card {
  background: #fff; border-radius: 16px; padding: 16px;
  box-shadow: 0 4px 16px rgba(217,119,6,0.06);
  margin: 2px 0; width: 100%; max-width: 350px; align-self: flex-start;
  border: 1px solid rgba(217,119,6,0.08);
}
.card-header { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; margin-bottom: 12px; color: #451a03; }
.card-header-summary { margin-left: auto; font-size: 11px; font-weight: 400; color: #92400e; background: rgba(254,243,199,0.6); border-radius: 10px; padding: 2px 8px; white-space: nowrap; }
.card-fold-btn { display: block; width: 100%; margin-top: 10px; padding: 7px 0; border: none; border-radius: 10px; background: rgba(254,243,199,0.55); color: #92400e; font-size: 12px; cursor: pointer; }
.card-fold-btn:active { background: rgba(254,215,170,0.8); }

/* 政策列表卡片 */
.policy-item { padding: 10px 0; border-bottom: 1px solid rgba(217,119,6,0.06); cursor: pointer; }
.policy-item:last-child { border-bottom: none; }
.policy-info { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.policy-emoji { flex-shrink: 0; }
.policy-title { font-size: 14px; font-weight: 500; flex: 1; color: #451a03; }
.policy-level { font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.policy-level.hit { background: #dcfce7; color: #65a30d; }
.policy-level.partial { background: #fef3c7; color: #d97706; }
.policy-deadline { font-size: 12px; color: #999; margin: 4px 0; }
.policy-actions { display: flex; justify-content: flex-end; padding-top: 6px; }

/* 资料卡片 */
.profile-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.pf-item { font-size: 13px; padding: 8px; background: rgba(254,243,199,0.3); border-radius: 10px; }
.pf-label { color: #999; display: block; font-size: 11px; margin-bottom: 2px; }
.profile-completeness { margin-top: 10px; }
.pc-label { font-size: 12px; color: #78350f; }
.pc-bar { height: 6px; background: rgba(217,119,6,0.15); border-radius: 3px; margin-top: 4px; overflow: hidden; }
.pc-fill { height: 100%; background: linear-gradient(90deg, #d97706, #65a30d); border-radius: 3px; transition: width 0.5s; }
.card-nav-btn { margin-top: 10px; width: 100%; }

/* 申报表单卡片 */
.af-policy { font-size: 14px; font-weight: 600; margin: 0 0 10px; color: #451a03; }

/* 申报列表卡片 */
.apply-item {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 0; border-bottom: 1px solid rgba(217,119,6,0.06); cursor: pointer;
}
.apply-item:last-child { border-bottom: none; }
.ai-policy { flex: 1; font-size: 14px; font-weight: 500; color: #451a03; }
.ai-status { font-size: 12px; padding: 2px 8px; background: #fef3c7; border-radius: 10px; color: #78350f; }

/* ===== 响应式：移动端 ===== */
@media (max-width: 768px) {
  .butler-page {
    grid-template-columns: 1fr;
  }

  .menu-btn { display: block; }

  .sidebar {
    position: fixed; left: 0; top: 0; bottom: 0;
    width: 280px; transform: translateX(-100%);
    transition: transform 0.3s ease; z-index: 100;
    box-shadow: 4px 0 24px rgba(0,0,0,0.15);
  }
  .sidebar.open { transform: translateX(0); }

  .sidebar-overlay { display: block; }

  .msg-row { max-width: 90%; }
  .data-card { max-width: 100%; }
}

/* iPad / 中等屏幕 */
@media (min-width: 769px) and (max-width: 1024px) {
  .butler-page {
    grid-template-columns: 240px 1fr;
  }
}
</style>