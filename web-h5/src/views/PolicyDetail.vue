<!--
  政策详情：标题卡（类别/匹配标识/来源与有效期）+ 补贴标准高亮卡 +
  条件匹配卡 + 内容/条件/截止/咨询分区卡；底部固定「去申报」大按钮。
-->
<template>
  <div class="page">
    <van-nav-bar
      title="政策详情"
      left-text="返回"
      left-arrow
      fixed
      placeholder
      class="detail-nav"
      @click-left="goBack"
    >
      <template #right>
        <button
          v-if="detail"
          type="button"
          class="fav-hit"
          :aria-label="detail.favorited ? '取消收藏' : '收藏政策'"
          @click="toggleFavorite"
        >
          <van-icon
            :name="detail.favorited ? 'star' : 'star-o'"
            size="24"
            :color="detail.favorited ? '#f9a825' : '#5a5a5a'"
          />
        </button>
      </template>
    </van-nav-bar>

    <div v-if="loading" class="state-box">
      <van-loading size="28px">加载中...</van-loading>
    </div>

    <van-empty v-else-if="!detail" description="政策不存在或已下架" />

    <template v-else>
      <!-- 标题卡：类别 + 匹配标识 + 来源/有效期元信息 -->
      <div class="card title-card">
        <div class="meta-row">
          <span v-if="categoryLabel" class="cat-tag">{{ categoryLabel }}</span>
          <van-tag v-if="match" :type="levelTag(match.matchLevel)" size="medium">
            {{ match.matchLevelLabel || match.matchLevel }}
          </van-tag>
        </div>
        <h2>{{ detail.title }}</h2>
        <div class="meta-chips">
          <span v-if="detail.sourceOrg" class="chip">
            <van-icon name="cluster-o" />{{ detail.sourceOrg }}
          </span>
          <span v-if="validityText" class="chip warn">
            <van-icon name="clock-o" />{{ validityText }}
          </span>
        </div>
      </div>

      <!-- 补贴标准高亮卡：老人最关心的信息单独成卡 -->
      <div class="card std-card">
        <van-icon name="gold-coin-o" class="std-icon" />
        <div class="std-body">
          <span class="std-label">补贴标准</span>
          <strong class="std-val">{{ detail.subsidyAmountText || detail.standard || '—' }}</strong>
        </div>
      </div>

      <!-- 条件匹配卡：逐项勾选清单 -->
      <div v-if="match" class="card match-box">
        <h4 class="sec-title">
          <van-icon name="certificate" class="sec-icon" />条件匹配结果
        </h4>
        <p class="match-score">{{ match.matchLevelLabel || (match.matchLevel === 'FULL' ? '条件符合' : '条件不符') }}</p>
        <ul v-if="checkRows.length" class="check-list">
          <li v-for="(c, i) in checkRows" :key="i" :class="{ ok: c.hit, bad: !c.hit }">
            <span class="mark">{{ c.hit ? '✓' : '✗' }}</span>
            {{ c.label }}
          </li>
        </ul>
        <ul v-if="match.reasons?.length" class="tips">
          <li v-for="(r, i) in match.reasons" :key="i">{{ r }}</li>
        </ul>
      </div>

      <!-- 政策内容卡 -->
      <div class="card">
        <h4 class="sec-title">
          <van-icon name="description" class="sec-icon" />政策内容
        </h4>
        <p class="sec-text">{{ detail.content || '—' }}</p>
      </div>

      <!-- 申领条件卡 -->
      <div class="card">
        <h4 class="sec-title">
          <van-icon name="todo-list-o" class="sec-icon" />申领条件
        </h4>
        <ul v-if="conditionList.length" class="cond-list">
          <li v-for="(c, i) in conditionList" :key="i">{{ c }}</li>
        </ul>
        <p v-else class="sec-text">{{ detail.conditions || '—' }}</p>
      </div>

      <!-- 申报截止卡（有才显示） -->
      <div v-if="detail.deadlineText" class="card deadline-card">
        <h4 class="sec-title">
          <van-icon name="underway-o" class="sec-icon warn" />申报截止
        </h4>
        <p class="sec-text warn-text">{{ detail.deadlineText }}</p>
      </div>

      <!-- 咨询方式卡 -->
      <div class="card">
        <h4 class="sec-title">
          <van-icon name="service-o" class="sec-icon" />咨询方式
        </h4>
        <p class="sec-text">{{ consultText }}</p>
      </div>

      <div class="footer">
        <van-button
          type="primary"
          block
          round
          class="main-btn"
          :disabled="match?.matchLevel === 'NONE'"
          @click="goApply"
        >
          {{ match?.matchLevel === 'NONE' ? '暂不符合，无法申报' : '去申报' }}
        </van-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { favoritePolicy, fetchPolicyDetail, unfavoritePolicy } from '@/api/policy'
import { navigateToApply } from '@/utils/applyRoute'
import { safeBack } from '@/utils/navBack'

const route = useRoute()
const router = useRouter()

/** 返回匹配列表（无历史时兜底，避免 back 无效） */
function goBack() {
  safeBack(router, '/policies')
}

const detail = ref(null)
const loading = ref(false)
const favLoading = ref(false)

/** 本人匹配结果；未登录或未算分时为 null */
const match = computed(() => detail.value?.myMatch || null)

/** 申领条件列表：优先用结构化 applyConditions */
const conditionList = computed(() => {
  const list = detail.value?.applyConditions
  return Array.isArray(list) ? list : []
})

/** 咨询文案：对象则取 text/phone，否则直接展示字符串或旧电话字段 */
const consultText = computed(() => {
  const c = detail.value?.consultInfo
  if (!c) return detail.value?.consultPhone || '—'
  if (typeof c === 'string') return c
  if (typeof c === 'object') {
    return c.text || c.phone || JSON.stringify(c)
  }
  return String(c)
})

/** SQL WHERE 匹配：展示条件勾选清单 */
const checkRows = computed(() => {
  const checks = match.value?.scoreDetail?.checks
  if (Array.isArray(checks) && checks.length) {
    return checks.map((c) => ({
      label: c.label || '',
      hit: !!c.hit,
    }))
  }
  // 兼容旧四维结构（若后端尚未重启）
  const d = match.value?.scoreDetail
  if (!d || typeof d !== 'object') return []
  const legacy = [
    { key: 'plant', label: '种养类型' },
    { key: 'region', label: '地区' },
    { key: 'area', label: '面积' },
    { key: 'bank', label: '银行卡' },
  ]
  return legacy
    .filter((r) => d[r.key])
    .map((r) => ({
      label: r.label,
      hit: Number(d[r.key]?.score ?? 0) >= 1 || !!d[r.key]?.hit,
    }))
})

/** 匹配等级对应 Vant Tag 颜色 */
function levelTag(level) {
  if (level === 'FULL') return 'success'
  if (level === 'PARTIAL') return 'warning'
  return 'danger'
}

/** 类别码转中文标签（与列表筛选同一口径） */
const categoryLabel = computed(() => {
  const map = { GRAIN: '粮食类', MACHINERY: '农机类', LIVESTOCK: '畜牧类', GENERAL: '综合/其他' }
  return map[detail.value?.category] || ''
})

/** 有效期文案：起止都有则并排，只有一个也如实展示 */
const validityText = computed(() => {
  const d = detail.value
  if (!d) return ''
  const from = d.validFrom ? String(d.validFrom) : ''
  const to = d.validTo ? String(d.validTo) : ''
  if (from && to) return `有效期 ${from} 至 ${to}`
  if (to) return `有效期至 ${to}`
  if (from) return `自 ${from} 起`
  return ''
})

/** 加载政策详情（含收藏态、匹配拆解、材料 Schema） */
async function loadDetail() {
  loading.value = true
  try {
    detail.value = await fetchPolicyDetail(route.params.id)
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

/** 右上角星标：已收藏则取消，未收藏则收藏 */
async function toggleFavorite() {
  if (!detail.value || favLoading.value) return
  favLoading.value = true
  try {
    if (detail.value.favorited) {
      await unfavoritePolicy(detail.value.id)
      detail.value.favorited = false
      showToast('已取消收藏')
    } else {
      await favoritePolicy(detail.value.id)
      detail.value.favorited = true
      showToast('已收藏')
    }
  } catch {
    // toast 已处理
  } finally {
    favLoading.value = false
  }
}

/** 去申报：有材料 Schema 走分项填报表，否则走旧版直提页 */
function goApply() {
  if (!detail.value) return
  navigateToApply(router, {
    id: detail.value.id,
    matchLevel: match.value?.matchLevel,
    materialSchema: detail.value.materialSchema,
  })
}

onMounted(loadDetail)
</script>

<style scoped>
/* ==========================================================================
   Impeccable UI: Golden Wheat & Prairie Theme (Policy Detail)
   ========================================================================== */
.page {
  padding-bottom: 80px;
  min-height: 100vh;
}
.detail-nav {
  z-index: 100;
}
.detail-nav :deep(.van-nav-bar__left) {
  min-width: 72px;
  padding-right: 8px;
}
.fav-hit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  min-height: 44px;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
}
.state-box {
  padding: 60px 0;
  text-align: center;
}
/* 分区白卡：每个信息块独立成卡，页面更充实 */
.card {
  margin: 12px 16px;
  background: var(--xct-card-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: var(--xct-card-border);
  border-radius: var(--xct-radius);
  padding: 16px;
  box-shadow: var(--xct-card-shadow);
}
/* 标题卡：类别标签 + 匹配标识 + 元信息 chips */
.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.cat-tag {
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(101, 163, 13, 0.15);
  color: #4d7c0f;
  font-size: var(--xct-fs-meta, 0.875rem);
  font-weight: 700;
}
.title-card h2 {
  margin: 0;
  font-size: var(--xct-fs-title, 1.25rem);
  color: #451a03;
  line-height: 1.4;
  font-weight: 800;
}
.meta-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(254, 243, 199, 0.5);
  color: #92400e;
  font-size: var(--xct-fs-meta, 0.875rem);
  font-weight: 600;
}
.chip.warn {
  background: #ffedd5;
  color: #ea580c;
  font-weight: 700;
}
/* 补贴标准高亮卡：浅金底 + 金币图标 */
.std-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: linear-gradient(135deg, rgba(254, 243, 199, 0.8) 0%, rgba(255, 255, 255, 0.6) 100%);
  border: 1px solid rgba(217, 119, 6, 0.2);
}
.std-icon {
  font-size: 32px;
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
  font-size: var(--xct-fs-title, 1.25rem);
  color: #78350f;
  line-height: 1.4;
  font-weight: 800;
  word-break: break-all;
}
/* 分区标题 */
.sec-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 10px;
  font-size: var(--xct-fs-body, 1rem);
  color: #78350f;
  font-weight: 800;
}
.sec-icon {
  font-size: 20px;
  color: #d97706;
}
.sec-icon.warn {
  color: #ea580c;
}
.sec-text {
  margin: 0;
  font-size: var(--xct-fs-body, 1rem);
  color: #451a03;
  line-height: 1.7;
  white-space: pre-wrap;
  font-weight: 500;
}
.warn-text {
  color: #ea580c;
  font-weight: 700;
}
/* 条件匹配卡 */
.match-box {
  background: linear-gradient(160deg, rgba(220, 252, 231, 0.7), rgba(255, 255, 255, 0.6) 80%);
  border: 1px solid rgba(101, 163, 13, 0.2);
}
.match-score {
  margin: 0 0 12px;
  font-size: var(--xct-fs-title, 1.25rem);
  font-weight: 800;
  color: #4d7c0f;
}
.check-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.check-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: var(--xct-fs-body, 1rem);
  color: #451a03;
  font-weight: 500;
}
.check-list li.ok {
  color: #4d7c0f;
  font-weight: 600;
}
.check-list li.bad {
  color: #b91c1c;
  font-weight: 600;
}
.check-list .mark {
  font-weight: 800;
  width: 1.2em;
}
.tips,
.cond-list {
  margin: 12px 0 0;
  padding-left: 18px;
  color: #92400e;
  font-size: var(--xct-fs-tip, 0.9375rem);
  line-height: 1.5;
  font-weight: 500;
}
.cond-list {
  margin: 0;
  font-size: var(--xct-fs-body, 1rem);
  line-height: 1.7;
}
/* 截止卡淡橙底 */
.deadline-card {
  background: rgba(255, 237, 213, 0.7);
  border: 1px solid rgba(234, 88, 12, 0.2);
}
.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
  background: rgba(255, 253, 245, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 -4px 24px rgba(217, 119, 6, 0.05);
}
.main-btn {
  height: var(--xct-hit-min, 48px) !important;
  font-size: 1.125rem !important;
  font-weight: 700 !important;
  box-shadow: 0 4px 16px rgba(217, 119, 6, 0.2);
}
</style>
