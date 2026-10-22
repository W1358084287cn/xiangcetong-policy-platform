<!--
  申报详情：单条申报的状态横幅 + 三级审批时间线（村初审→镇终审）+ 已交材料只读；
  草稿可续填、驳回可重新申报。
-->
<template>
  <div class="page">
    <van-nav-bar
      title="申报详情"
      left-arrow
      fixed
      placeholder
      left-text="返回"
      class="detail-nav"
      @click-left="goBack"
    />

    <div v-if="loading" class="state-box">
      <van-loading size="28px">加载中...</van-loading>
    </div>

    <van-empty v-else-if="!detail" description="申报记录不存在" />

    <template v-else>
      <!-- 状态横幅卡：左侧状态图标 + 标题 + 状态标签 -->
      <div class="card status-card" :class="statusClass(detail.status)">
        <div class="hd">
          <van-icon :name="statusIcon(detail.status)" class="status-icon" />
          <h2>{{ detail.policyTitle || '补贴申报' }}</h2>
          <van-tag type="default" size="small" v-if="detail.categoryLabel">{{ detail.categoryLabel }}</van-tag>
          <van-tag :type="statusTagType(detail.status)" size="medium">
            {{ formatApplyStatus(detail.status, detail.statusLabel) }}
          </van-tag>
        </div>
        <p class="meta">申请时间：{{ detail.createdAt || '—' }}</p>
        <p v-if="detail.updatedAt" class="meta">更新时间：{{ detail.updatedAt }}</p>
        <!-- 已发放时横幅卡突出显示发放金额 -->
        <p v-if="detail.status === 'DISBURSED' && detail.disburseAmount" class="meta disburse-meta">
          发放金额 <strong>¥{{ detail.disburseAmount }}</strong>
        </p>
      </div>

      <!-- 审批时间线 -->
      <div class="card">
        <h3 class="card-title">
          <van-icon name="underway-o" class="title-icon" />审批进度
        </h3>
        <div class="timeline">
          <div class="tl-item done">
            <span class="dot" />
            <div>
              <strong>已提交</strong>
              <p>{{ detail.createdAt || '—' }}</p>
            </div>
          </div>
          <div
            class="tl-item"
            :class="{
              done: villagePassed,
              fail: villageFailed,
              pending: detail.status === 'PENDING_VILLAGE',
            }"
          >
            <span class="dot" />
            <div>
              <strong>村级初审</strong>
              <p v-if="villageFailed">已驳回：{{ detail.firstOpinion }}</p>
              <p v-else-if="detail.firstOpinion">意见：{{ detail.firstOpinion }}</p>
              <p v-else-if="detail.status === 'PENDING_VILLAGE'">等待村委会审核</p>
              <p v-else-if="villagePassed">已通过</p>
              <p v-else>—</p>
            </div>
          </div>
          <div
            class="tl-item"
            :class="{
              done: townPassed,
              fail: detail.status === 'REJECTED' && detail.finalOpinion,
              pending: detail.status === 'PENDING_TOWN',
            }"
          >
            <span class="dot" />
            <div>
              <strong>镇级终审</strong>
              <p v-if="detail.finalOpinion">意见：{{ detail.finalOpinion }}</p>
              <p v-else-if="detail.status === 'PENDING_TOWN'">等待镇级终审</p>
              <p v-else-if="townPassed">已通过</p>
              <p v-else-if="detail.status === 'REJECTED' && !detail.finalOpinion">—</p>
              <p v-else>—</p>
            </div>
          </div>
          <!-- 发放节点：终审通过后进入发放阶段，已发放显示金额与时间，待发放显示等待，未通过前灰色未开始 -->
          <div
            class="tl-item"
            :class="{
              done: detail.status === 'DISBURSED',
              pending: detail.status === 'PENDING_DISBURSE',
            }"
          >
            <span class="dot" />
            <div>
              <strong>发放</strong>
              <p v-if="detail.status === 'DISBURSED'">
                已发放
                <span v-if="detail.disburseAmount" class="disburse-amount">¥{{ detail.disburseAmount }}</span>
                <span v-if="detail.disburseAt" class="disburse-time">{{ detail.disburseAt }}</span>
              </p>
              <p v-else-if="detail.status === 'PENDING_DISBURSE'">
                等待发放
                <span v-if="detail.disburseAmount" class="disburse-amount">拟定 ¥{{ detail.disburseAmount }}</span>
                <span v-if="detail.disburseAt" class="disburse-time">拟定 {{ detail.disburseAt }}</span>
              </p>
              <p v-else>未开始</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 咨询电话：有值才显示，整行可点击拨号（适老热区 ≥48） -->
      <div v-if="detail.consultPhone" class="card consult-card">
        <h3 class="card-title">
          <van-icon name="phone-o" class="title-icon" />咨询电话
        </h3>
        <a :href="`tel:${detail.consultPhone}`" class="consult-link">
          <van-icon name="phone-o" class="consult-icon" />
          <span class="consult-text">遇到问题请咨询：{{ detail.consultPhone }}</span>
        </a>
      </div>

      <!-- 已交材料只读 -->
      <div class="card">
        <h3 class="card-title">
          <van-icon name="description" class="title-icon" />已交材料
        </h3>
        <van-empty v-if="!rows.length" description="暂无材料信息" />
        <div v-for="(row, i) in rows" :key="i" class="mat-row">
          <p class="mat-label">{{ row.label }}</p>
          <img
            v-if="row.isImage"
            :src="absUrl(row.value)"
            class="mat-img"
            alt=""
            @click="preview(row.value)"
          />
          <p v-else class="mat-val">{{ row.value }}</p>
        </div>
      </div>

      <div v-if="detail.status === 'DRAFT'" class="footer">
        <van-button type="primary" block round class="main-btn" @click="continueDraft">
          继续填写材料
        </van-button>
      </div>
      <div v-else-if="detail.status === 'REJECTED'" class="footer">
        <van-button
          type="primary"
          block
          round
          class="main-btn"
          :loading="reapplying"
          @click="onReapply"
        >
          重新申报
        </van-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showImagePreview, showToast } from 'vant'
import { fetchApplyDetail } from '@/api/apply'
import { formatApplyStatus } from '@/utils/status'
import { flattenMaterialRows } from '@/utils/materialDisplay'
import { reapplyFromRejected } from '@/utils/applyRoute'
import { safeBack } from '@/utils/navBack'

const route = useRoute()
const router = useRouter()

function goBack() {
  safeBack(router, '/mine/applies')
}

const loading = ref(false)
const detail = ref(null)
const reapplying = ref(false)

/** 已交材料展平成只读行（文案 + 图片），code 按政策 Schema 映射成中文名 */
const rows = computed(() =>
  flattenMaterialRows(
    detail.value?.materialData,
    detail.value?.materialUrls || [],
    detail.value?.materialSchema
  )
)

/**
 * 村审已通过（含后续镇审中/通过/镇驳回，以及发放阶段的待发放、已发放）。
 * 发放阶段说明镇、村两级均已通过，故一并视为村审通过。
 */
const villagePassed = computed(() => {
  const s = detail.value?.status
  return (
    s === 'PENDING_TOWN' ||
    s === 'APPROVED' ||
    s === 'PENDING_DISBURSE' ||
    s === 'DISBURSED' ||
    (s === 'REJECTED' && !!detail.value?.finalOpinion)
  )
})

/** 村审直接驳回（有初审意见且无终审意见） */
const villageFailed = computed(() => {
  const d = detail.value
  return d?.status === 'REJECTED' && !!d.firstOpinion && !d.finalOpinion
})

/** 镇级终审已通过（含后续发放阶段：待发放、已发放） */
const townPassed = computed(() => {
  const s = detail.value?.status
  return s === 'APPROVED' || s === 'PENDING_DISBURSE' || s === 'DISBURSED'
})

/**
 * 申报状态对应 Vant Tag 颜色类型。
 * 2026-10 新增发放阶段：待发放 PENDING_DISBURSE→蓝(primary)、已发放 DISBURSED→绿(success)；
 * APPROVED（已通过，历史）仍为绿色 success。
 */
function statusTagType(status) {
  if (status === 'APPROVED') return 'success'
  if (status === 'DISBURSED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'PENDING_TOWN') return 'primary'
  if (status === 'PENDING_DISBURSE') return 'primary'
  if (status === 'DRAFT') return 'default'
  return 'warning'
}

/** 状态横幅卡修饰类（左色条 + 图标色）：已发放绿、待发放琥珀 */
function statusClass(status) {
  if (status === 'APPROVED') return 'st-ok'
  if (status === 'DISBURSED') return 'st-ok'
  if (status === 'REJECTED') return 'st-bad'
  if (status === 'DRAFT') return 'st-draft'
  return 'st-pending'
}

/** 状态横幅图标（Vant 内置）：已发放勾选，待发放进行中 */
function statusIcon(status) {
  if (status === 'APPROVED') return 'checked'
  if (status === 'DISBURSED') return 'checked'
  if (status === 'REJECTED') return 'close'
  if (status === 'DRAFT') return 'edit'
  return 'underway-o'
}

/** 材料图片地址：已是绝对/blob 则原样返回 */
function absUrl(url) {
  if (!url) return ''
  if (url.startsWith('http') || url.startsWith('blob:')) return url
  return url
}

/** 点击材料图放大预览 */
function preview(url) {
  showImagePreview([absUrl(url)])
}

/** 草稿继续填写：跳转材料分步页 */
function continueDraft() {
  if (detail.value?.policyId) {
    router.push({ path: '/apply/materials', query: { policyId: String(detail.value.policyId) } })
  }
}

/** 已驳回：预填旧材料开新草稿并进入申报 */
async function onReapply() {
  if (!detail.value?.id || reapplying.value) return
  reapplying.value = true
  try {
    const ok = await reapplyFromRejected(router, detail.value.id)
    if (!ok) showToast('重新申报失败，请稍后重试')
  } finally {
    reapplying.value = false
  }
}

/** 按路由 id 拉取申报详情 */
async function load() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    detail.value = await fetchApplyDetail(id)
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
/* ==========================================================================
   Impeccable & UI-UX Pro Max Theme: Organic Biophilic (Apply Detail)
   ========================================================================== */
@keyframes slideFadeUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.page {
  padding-bottom: 88px;
  background: var(--xct-bg, #f3f6f1);
  min-height: 100vh;
}
.detail-nav {
  z-index: 100;
}
.detail-nav :deep(.van-nav-bar__left) {
  min-width: 72px;
  padding-right: 8px;
}
.state-box {
  padding: 60px 0;
  text-align: center;
}
.card {
  margin: 16px 16px;
  padding: 20px;
  background: var(--xct-card-bg, #fff);
  border: var(--xct-card-border, 1px solid rgba(21, 128, 61, 0.05));
  border-radius: var(--xct-radius, 20px);
  box-shadow: var(--xct-card-shadow, 0 8px 24px rgba(21, 128, 61, 0.04));
  animation: slideFadeUp 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.card:nth-child(1) { animation-delay: 0.1s; }
.card:nth-child(2) { animation-delay: 0.15s; }
.card:nth-child(3) { animation-delay: 0.2s; }
.card:nth-child(4) { animation-delay: 0.25s; }

/* 状态横幅卡：左侧状态色条 */
.status-card {
  position: relative;
  padding-left: 24px;
  overflow: hidden;
}
.status-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  background: #cbd5e1;
}
.status-card.st-ok::before {
  background: var(--xct-green, #15803d);
}
.status-card.st-bad::before {
  background: var(--xct-red, #b91c1c);
}
.status-card.st-draft::before {
  background: #94a3b8;
}
.status-card.st-pending::before {
  background: var(--xct-amber, #b45309);
}
.hd {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.status-icon {
  font-size: 26px;
  margin-top: 2px;
  color: var(--xct-green, #15803d);
  flex-shrink: 0;
}
.st-bad .status-icon {
  color: var(--xct-red, #b91c1c);
}
.st-draft .status-icon {
  color: #94a3b8;
}
.st-pending .status-icon {
  color: var(--xct-amber, #b45309);
}
.card h2 {
  margin: 0;
  font-size: var(--xct-fs-title, 1.25rem);
  color: var(--xct-green-deep, #166534);
  line-height: 1.4;
  flex: 1;
  font-weight: 700;
}
/* 卡片小标题：图标 + 粗体 */
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 16px;
  font-size: var(--xct-fs-body, 1rem);
  color: var(--xct-color-text, #1e293b);
  font-weight: 700;
}
.title-icon {
  font-size: 22px;
  color: var(--xct-green, #15803d);
}
.meta {
  margin: 10px 0 0;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: var(--xct-color-muted, #64748b);
  font-weight: 500;
}
/* 横幅卡内的发放金额：大字突出 */
.disburse-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed rgba(21, 128, 61, 0.1);
}
.disburse-meta strong {
  font-size: var(--xct-fs-title, 1.35rem);
  font-weight: 700;
  color: var(--xct-green-deep, #166534);
}
/* 时间线：圆点之间用竖线串联，进度感更强 */
.timeline {
  display: flex;
  flex-direction: column;
}
.tl-item {
  position: relative;
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding-bottom: 24px;
  color: var(--xct-color-muted, #64748b);
}
.tl-item:last-child {
  padding-bottom: 0;
}
/* 圆点右侧的竖向连接线（最后一项不画） */
.tl-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 6px;
  top: 20px;
  bottom: 0;
  width: 2px;
  background: var(--xct-line, rgba(21, 128, 61, 0.1));
}
.tl-item .dot {
  position: relative;
  z-index: 1;
  width: 14px;
  height: 14px;
  margin-top: 4px;
  border-radius: 50%;
  background: #cbd5e1;
  flex-shrink: 0;
}
.tl-item.done {
  color: var(--xct-color-text, #1e293b);
}
.tl-item.done .dot {
  background: var(--xct-green, #15803d);
  box-shadow: 0 0 0 4px #dcfce7;
}
.tl-item.done:not(:last-child)::before {
  background: var(--xct-green, #15803d);
}
.tl-item.pending {
  color: var(--xct-amber, #b45309);
}
.tl-item.pending .dot {
  background: #f59e0b;
  box-shadow: 0 0 0 4px #fef3c7;
}
.tl-item.fail {
  color: var(--xct-red, #b91c1c);
}
.tl-item.fail .dot {
  background: var(--xct-red, #b91c1c);
  box-shadow: 0 0 0 4px #fee2e2;
}
.tl-item strong {
  display: block;
  font-size: var(--xct-fs-body, 1rem);
  margin-bottom: 6px;
  font-weight: 600;
}
.tl-item p {
  margin: 0;
  font-size: var(--xct-fs-tip, 0.9375rem);
  line-height: 1.5;
}
/* 发放节点金额大字突出，时间用次色 */
.tl-item .disburse-amount {
  font-size: var(--xct-fs-title, 1.25rem);
  font-weight: 700;
  color: var(--xct-green-deep, #166534);
}
.tl-item .disburse-time {
  margin-left: 10px;
  color: var(--xct-color-muted, #64748b);
  font-size: var(--xct-fs-tip, 0.9375rem);
}
/* 咨询电话：整行可点，热区 ≥48，绿底突出适老 */
.consult-link {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 52px;
  padding: 12px 16px;
  box-sizing: border-box;
  border-radius: var(--xct-radius-sm, 14px);
  background: #f0fdf4;
  color: var(--xct-green-deep, #166534);
  text-decoration: none;
  border: 1px solid rgba(21, 128, 61, 0.08);
}
.consult-icon {
  font-size: 24px;
  flex-shrink: 0;
}
.consult-text {
  font-size: var(--xct-fs-body, 1rem);
  font-weight: 600;
  line-height: 1.5;
}
.mat-row {
  margin-bottom: 16px;
}
.mat-label {
  margin: 0 0 8px;
  font-size: var(--xct-fs-body, 1rem);
  font-weight: 600;
  color: #1e293b;
}
.mat-val {
  margin: 0;
  font-size: var(--xct-fs-body, 1rem);
  color: var(--xct-color-muted, #475569);
  line-height: 1.6;
  word-break: break-all;
}
.mat-img {
  display: block;
  width: 100%;
  max-height: 240px;
  object-fit: contain;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid rgba(0,0,0,0.05);
}
.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16px 20px calc(16px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 -4px 24px rgba(0, 0, 0, 0.04);
  z-index: 10;
}
.main-btn {
  height: var(--xct-hit-min, 52px) !important;
  font-size: var(--xct-fs-body, 1.05rem) !important;
  font-weight: 600 !important;
  box-shadow: 0 4px 12px rgba(21, 128, 61, 0.2);
}
</style>