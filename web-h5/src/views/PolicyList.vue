<!--
  政策匹配列表：分类选项卡 + 搜索栏 + 分页（每页 5 条）。
  搜索栏仿抖音风格，顶部固定不随滚动消失。
-->
<template>
  <div class="page">
    <van-nav-bar title="政策匹配" fixed placeholder />

    <!-- 搜索栏：实时搜索（输入/删除即搜），也可点搜索按钮确认 -->
    <div class="search-bar">
      <van-search
        v-model="keyword"
        shape="round"
        placeholder="输入关键词实时搜索"
        show-action
        action-text="搜索"
        @search="onSearch"
        @clear="onClear"
        @cancel="onClear"
      />
    </div>

    <!-- 分类选项卡：非搜索模式时显示 -->
    <div v-if="!isSearchMode" class="sticky-header">
      <van-tabs
        v-model:active="activeTab"
        swipeable
        sticky
        :offset-top="100"
        :ellipsis="false"
      >
        <van-tab
          v-for="tab in tabs"
          :key="tab.value"
          :title="tab.text"
          :name="tab.value"
        />
      </van-tabs>
    </div>

    <!-- 资料不全引导 -->
    <van-notice-bar
      v-if="needProfile"
      left-icon="info-o"
      mode="link"
      text="还差个人资料，点这里去完善后再匹配"
      @click="$router.push({ path: '/mine', query: { edit: '1', from: 'match' } })"
    />
    <van-notice-bar
      v-else-if="expiringOnly"
      left-icon="clock-o"
      color="#e65100"
      background="#fff3e0"
      text="仅显示 90 天内即将截止的匹配政策"
    />

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <!-- 加载中 -->
      <div v-if="loading" class="state-box">
        <van-loading size="28px">
          {{ isSearchMode ? '搜索中...' : '正在按您的条件匹配政策...' }}
        </van-loading>
      </div>

      <van-empty v-else-if="needProfile && !isSearchMode" description="完善资料后自动为您匹配政策" />
      <van-empty v-else-if="!loading && !currentList.length" :description="isSearchMode ? '未找到包含该关键词的政策' : '暂无符合条件的政策'" />

      <div v-else class="list">
        <!-- 搜索模式时显示结果数和关键词 -->
        <div v-if="isSearchMode && currentList.length" class="search-summary">
          <van-icon name="search" />
          <span>「{{ searchKeyword }}」共 {{ totalCount }} 条结果</span>
        </div>

        <div
          v-for="item in displayList"
          :key="item.id"
          class="policy-card"
          :class="{ disabled: item.matchLevel === 'NONE' }"
          @click="goDetail(item)"
        >
          <div class="meta-row">
            <span v-if="categoryLabel(item)" class="cat-tag">{{ categoryLabel(item) }}</span>
            <van-tag v-if="!isSearchMode" :type="levelTag(item.matchLevel)" size="medium" class="level-tag">
              {{ item.matchLevelLabel || item.matchLevel }}
            </van-tag>
          </div>
          <h3 class="card-title" v-html="highlight(item.title)"></h3>

          <div class="std-box">
            <van-icon name="gold-coin-o" class="std-icon" />
            <div class="std-body">
              <span class="std-label">补贴标准</span>
              <strong class="std-val">{{ item.standard || '详见政策内容' }}</strong>
            </div>
          </div>

          <p v-if="item.validTo" class="valid-line">
            <van-icon name="clock-o" />
            申报有效期至 {{ formatValid(item.validTo) }}
          </p>
          <p v-for="(tip, idx) in tipLines(item)" :key="idx" class="reason">{{ tip }}</p>
          <div class="actions">
            <van-button
              type="primary"
              round
              class="apply-btn"
              :disabled="isSearchMode ? false : (item.matchLevel === 'NONE')"
              @click.stop="goApply(item)"
            >
              {{ isSearchMode ? '去申报' : (item.matchLevel === 'NONE' ? '暂不符合' : '去申报') }}
            </van-button>
          </div>
        </div>

        <!-- 分页控制器：搜索结果或分类选项卡超过 5 条时显示 -->
        <div v-if="totalPages > 1" class="pager">
          <van-button
            size="small"
            plain
            :disabled="activePage <= 1"
            @click="goPage(activePage - 1)"
          >
            上一页
          </van-button>
          <span class="page-info">{{ activePage }} / {{ totalPages }}</span>
          <van-button
            size="small"
            plain
            :disabled="activePage >= totalPages"
            @click="goPage(activePage + 1)"
          >
            下一页
          </van-button>
        </div>
      </div>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchMatchedPolicies, searchPolicies } from '@/api/policy'
import { navigateToApply } from '@/utils/applyRoute'

const router = useRouter()
const route = useRoute()

const list = ref([])
const loading = ref(false)
const refreshing = ref(false)
const needProfile = ref(false)
const activeTab = ref('')
const pageSize = 5

const keyword = ref('')
const isSearchMode = ref(false)
const searchKeyword = ref('')
const currentPage = ref(1)
const searchTotal = ref(0)
const tabPage = ref(1)

const tabs = [
  { text: '全部政策', value: '' },
  { text: '粮食种植', value: 'GRAIN' },
  { text: '畜牧养殖', value: 'LIVESTOCK' },
  { text: '水产养殖', value: 'AQUACULTURE' },
  { text: '农机装备', value: 'MACHINERY' },
  { text: '社保优抚', value: 'SOCIAL' },
  { text: '综合/其他', value: 'GENERAL' },
]

// 2026-10-17 修复：改为响应式。原先在 setup 里读一次 const，从首页带
// ?expiring=1 跳转时组件被复用、不会重跑 setup，导致「即将到期」过滤失效、
// 出现不该出现的政策。
const expiringOnly = computed(
  () => route.query.expiring === '1' || route.query.expiring === 'true'
)

watch(expiringOnly, () => {
  tabPage.value = 1
  reload()
})

const currentList = computed(() => {
  if (isSearchMode.value) return list.value
  if (!activeTab.value) return list.value
  return list.value.filter((p) => p.category === activeTab.value)
})

const displayList = computed(() => {
  if (isSearchMode.value) return list.value
  const start = (tabPage.value - 1) * pageSize
  return currentList.value.slice(start, start + pageSize)
})

const totalCount = computed(() => {
  if (isSearchMode.value) return searchTotal.value
  return currentList.value.length
})

const totalPages = computed(() => Math.ceil(totalCount.value / pageSize) || 1)

const activePage = computed(() => {
  return isSearchMode.value ? currentPage.value : tabPage.value
})

watch(activeTab, () => { tabPage.value = 1 })

function levelTag(level) {
  if (level === 'FULL') return 'success'
  if (level === 'HIGH') return 'primary'
  if (level === 'MEDIUM') return 'warning'
  if (level === 'LOW') return 'warning'
  return 'danger'
}

function categoryLabel(item) {
  const hit = tabs.find((o) => o.value && o.value === item?.category)
  return hit?.text || ''
}

function formatValid(v) {
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

function tipLines(item) {
  const reasons = item?.reasons || item?.scoreDetail?.tips || []
  return reasons.slice(0, 2)
}

/** 搜索结果中高亮关键词 */
function highlight(text) {
  if (!isSearchMode.value || !searchKeyword.value || !text) return text
  const kw = searchKeyword.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const re = new RegExp(`(${kw})`, 'gi')
  return text.replace(re, '<em class="hl">$1</em>')
}

/** 防抖定时器：输入时实时搜索（老人也可点搜索按钮立即搜） */
let searchTimer = null

function onSearch(val) {
  const kw = (val || keyword.value).trim()
  if (!kw) return
  clearTimeout(searchTimer)
  searchKeyword.value = kw
  isSearchMode.value = true
  currentPage.value = 1
  tabPage.value = 1
  doSearch()
}

function onClear() {
  clearTimeout(searchTimer)
  keyword.value = ''
  searchKeyword.value = ''
  isSearchMode.value = false
  currentPage.value = 1
  tabPage.value = 1
  reload()
}

/** 输入即搜：300ms 防抖，删除文字也实时搜，清空回到原样 */
watch(keyword, (newVal) => {
  clearTimeout(searchTimer)
  if (!newVal.trim()) {
    onClear()
    return
  }
  searchTimer = setTimeout(() => {
    searchKeyword.value = newVal.trim()
    isSearchMode.value = true
    currentPage.value = 1
    tabPage.value = 1
    doSearch()
  }, 300)
})

onUnmounted(() => {
  clearTimeout(searchTimer)
})

function goPage(p) {
  if (isSearchMode.value) {
    currentPage.value = p
    doSearch()
  } else {
    tabPage.value = p
  }
  // 2026-10-17 修复：翻页后平滑滚回列表顶部，否则新页内容出现在视口上方、
  // 用户看到的还是上一页滚到一半的位置，像「点了没反应」。
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function doSearch() {
  loading.value = true
  try {
    const data = await searchPolicies(searchKeyword.value, currentPage.value, pageSize)
    list.value = data.list || []
    searchTotal.value = data.total || 0
  } catch (e) {
    list.value = []
    searchTotal.value = 0
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function isExpiringSoon(p) {
  if (!p?.validTo) return false
  const end = new Date(p.validTo)
  if (Number.isNaN(end.getTime())) return false
  const days = (end - Date.now()) / (24 * 3600 * 1000)
  return days >= 0 && days <= 90
}

async function reload(keepPage = false) {
  loading.value = true
  needProfile.value = false
  try {
    const data = await fetchMatchedPolicies({
      plantType: undefined,
      category: undefined,
      includeNone: undefined,
      pageNum: 1,
      pageSize: 200,
    })
    let rows = data?.list || []
    if (expiringOnly.value) {
      rows = rows.filter(isExpiringSoon)
    }
    // 2026-10-17 修复：条件不符（NONE）的政策统一排到最后，避免穿插在
    // 符合条件的政策中间干扰阅读。同档位内保持后端原顺序（稳定排序）。
    const levelRank = { FULL: 0, HIGH: 1, MEDIUM: 2, LOW: 3, NONE: 4 }
    rows = rows
      .map((row, idx) => ({ row, idx }))
      .sort(
        (a, b) =>
          (levelRank[a.row.matchLevel] ?? 3) - (levelRank[b.row.matchLevel] ?? 3) ||
          a.idx - b.idx
      )
      .map((o) => o.row)
    list.value = rows
    // 2026-10-17 修复：下拉刷新（keepPage=true）不再重置页码 ——
    // 原先翻到第 N 页后往回一滑触发下拉刷新就被拽回第 1 页。
    if (!keepPage) tabPage.value = 1
  } catch (e) {
    list.value = []
    if (e?.code === 42202) {
      needProfile.value = true
    }
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function onRefresh() {
  if (isSearchMode.value) {
    doSearch()
  } else {
    reload(true)
  }
}

function goDetail(item) {
  router.push(`/policy/${item.id}`)
}

function goApply(item) {
  navigateToApply(router, item)
}

onMounted(() => {
  if (route.query.category) {
    activeTab.value = route.query.category
  }
  reload()
})
</script>

<style scoped>
/* ==========================================================================
   Impeccable UI: Golden Wheat & Prairie Theme (List Reveal)
   ========================================================================== */
@keyframes slideFadeUp {
  from { opacity: 0; filter: blur(8px); transform: translateY(20px); }
  to { opacity: 1; filter: blur(0); transform: translateY(0); }
}

.page {
  padding-bottom: 16px;
}

/* 搜索栏固定在导航栏下方 */
.search-bar {
  position: sticky;
  top: 46px;
  z-index: 11;
  background: rgba(255, 253, 245, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  padding: 4px;
}

.search-bar :deep(.van-search) {
  background: transparent;
}

.search-bar :deep(.van-search__content) {
  background: rgba(255, 255, 255, 0.8); 
  border-radius: var(--xct-radius-sm);
  border: 1px solid rgba(217, 119, 6, 0.2);
}

.sticky-header :deep(.van-tabs__wrap) {
  position: sticky;
  top: 100px;
  z-index: 10;
  /* 2026-10-17：吸顶时给选项卡加不透明毛玻璃底，防止卡片标题从标签栏下透出叠字 */
  background: rgba(255, 253, 245, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.sticky-header :deep(.van-tabs__nav) {
  background: transparent;
}

.sticky-header :deep(.van-tab) {
  font-size: 14px;
  padding: 0 12px;
  flex: none;
  font-weight: 600;
}

.state-box {
  padding: 60px 0;
  text-align: center;
}

.search-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0 4px;
  font-size: 14px;
  color: #92400e;
  font-weight: 600;
}

.list {
  padding: 12px 16px;
}

.policy-card {
  background: var(--xct-card-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: var(--xct-card-border);
  border-radius: var(--xct-radius); 
  padding: 20px;
  margin-bottom: 16px;
  box-shadow: var(--xct-card-shadow);
  animation: slideFadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.25s;
  will-change: transform;
}

/* 交错动画延迟 */
.policy-card:nth-child(1) { animation-delay: 0.05s; }
.policy-card:nth-child(2) { animation-delay: 0.1s; }
.policy-card:nth-child(3) { animation-delay: 0.15s; }
.policy-card:nth-child(4) { animation-delay: 0.2s; }
.policy-card:nth-child(5) { animation-delay: 0.25s; }

.policy-card:active {
  transform: scale(0.98);
  box-shadow: 0 4px 12px rgba(217, 119, 6, 0.04);
}

.policy-card.disabled {
  opacity: 0.75;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.cat-tag {
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(101, 163, 13, 0.15);
  color: #4d7c0f;
  font-size: var(--xct-fs-meta, 0.875rem);
  font-weight: 700;
}

.level-tag {
  flex-shrink: 0;
  border-radius: 8px; 
}

.card-title {
  margin: 0;
  font-size: var(--xct-fs-title, 1.25rem);
  color: #451a03;
  line-height: 1.4;
  font-weight: 800;
}

.card-title :deep(.hl) {
  color: #b45309;
  background: #fef3c7;
  padding: 0 4px;
  border-radius: 4px;
}

.std-box {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 16px;
  border-radius: var(--xct-radius-sm);
  background: rgba(254, 243, 199, 0.4); 
  border: 1px solid rgba(217, 119, 6, 0.1);
}

.std-icon {
  font-size: 28px;
  color: #d97706;
  flex-shrink: 0;
}

.std-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.std-label {
  font-size: var(--xct-fs-meta, 0.875rem);
  color: #92400e;
  font-weight: 600;
}

.std-val {
  font-size: var(--xct-fs-body, 1rem);
  color: #78350f;
  line-height: 1.4;
  font-weight: 800;
  word-break: break-all;
}

.valid-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px 0 0;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: #ea580c;
  font-weight: 700;
}

.reason {
  margin: 8px 0 0;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: #92400e;
  line-height: 1.5;
  font-weight: 500;
}

.actions {
  margin-top: 16px;
  text-align: right;
}

.apply-btn {
  min-width: 120px;
  height: var(--xct-hit-min, 48px) !important;
  font-size: var(--xct-fs-body, 1rem) !important;
  padding: 0 24px !important;
  font-weight: 700 !important;
  box-shadow: 0 4px 16px rgba(217, 119, 6, 0.2);
}

/* 分页控制器 */
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 24px 0;
}

.page-info {
  font-size: 15px;
  font-weight: 700;
  color: #92400e;
}

.pager :deep(.van-button) {
  color: #d97706;
  border-color: #d97706;
  min-width: 80px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(8px);
}
</style>