<!--
  申报进度列表：村民的申报记录卡片流。
  每张卡左侧状态色条 + 状态图标，草稿/驳回给出明确的下一步引导文案。
-->
<template>
  <div class="page">
    <van-nav-bar
      title="申报进度"
      left-text="返回"
      left-arrow
      fixed
      placeholder
      @click-left="goBack"
    />

    <div class="toolbar">
      <span>共 {{ total }} 条 · 最近申报在前</span>
      <van-button plain round class="tool-btn" @click="reload">刷新</van-button>
    </div>

    <div v-if="loading" class="state-box">
      <van-loading size="24px">加载中...</van-loading>
    </div>
    <van-empty v-else-if="!list.length" description="暂无申报记录" />
    <div v-else class="list">
      <div
        v-for="item in list"
        :key="item.id"
        class="card"
        :class="statusClass(item.status)"
      >
        <div class="row" @click="onRecordClick(item)">
          <van-icon :name="statusIcon(item.status)" class="status-icon" />
          <strong>{{ item.policyTitle || '政策' }}</strong>
          <van-tag type="default" size="small" v-if="item.categoryLabel">{{ item.categoryLabel }}</van-tag>
          <van-tag :type="statusTagType(item.status)" size="medium">
            {{ formatApplyStatus(item.status, item.statusLabel) }}
          </van-tag>
        </div>
        <p @click="onRecordClick(item)">申请时间：{{ item.createdAt || '—' }}</p>
        <p v-if="item.status === 'DRAFT'" class="hint" @click="onRecordClick(item)">
          点击继续填写材料
        </p>
        <p v-else-if="item.status === 'REJECTED'" class="hint" @click="onRecordClick(item)">
          已驳回，可重新申报
        </p>
        <p v-else class="hint" @click="onRecordClick(item)">点击查看材料与审批进度</p>

        <!-- 发放信息：已发放显示金额（金额缺省则不显示），待发放给提示 -->
        <div
          v-if="item.status === 'DISBURSED' && item.disburseAmount"
          class="disburse-row"
          @click="onRecordClick(item)"
        >
          <van-icon name="balance-o" class="disburse-icon" />
          <span>已发放</span>
          <strong class="disburse-amount">¥{{ item.disburseAmount }}</strong>
        </div>
        <p v-else-if="item.status === 'PENDING_DISBURSE'" class="disburse-wait" @click="onRecordClick(item)">
          已通过，等待发放
        </p>

        <p v-if="item.firstOpinion" class="opinion">初审意见：{{ item.firstOpinion }}</p>
        <p v-if="item.finalOpinion" class="opinion">终审意见：{{ item.finalOpinion }}</p>

        <div v-if="item.status === 'REJECTED'" class="actions">
          <van-button
            type="primary"
            round
            class="reapply-btn"
            :loading="reapplyingId === item.id"
            @click.stop="onReapply(item)"
          >
            重新申报
          </van-button>
        </div>
      </div>
    </div>

    <div v-if="total > pageSize" class="pager">
      <van-pagination
        v-model="pageNum"
        :total-items="total"
        :items-per-page="pageSize"
        force-ellipses
        @change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { fetchMyApplies } from '@/api/apply'
import { formatApplyStatus } from '@/utils/status'
import { reapplyFromRejected } from '@/utils/applyRoute'
import { safeBack } from '@/utils/navBack'

const router = useRouter()

function goBack() {
  safeBack(router, '/mine')
}

const pageSize = 5
const pageNum = ref(1)
const total = ref(0)
const list = ref([])
const loading = ref(false)
const reapplyingId = ref(null)

/**
 * 状态 Tag 颜色。
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

/** 卡片左侧状态色条对应的修饰类：已发放走绿色 st-ok，待发放走琥珀 st-pending */
function statusClass(status) {
  if (status === 'APPROVED') return 'st-ok'
  if (status === 'DISBURSED') return 'st-ok'
  if (status === 'REJECTED') return 'st-bad'
  if (status === 'DRAFT') return 'st-draft'
  return 'st-pending'
}

/** 卡片标题前的状态图标（Vant 内置）：已发放勾选，待发放进行中 */
function statusIcon(status) {
  if (status === 'APPROVED') return 'checked'
  if (status === 'DISBURSED') return 'checked'
  if (status === 'REJECTED') return 'close'
  if (status === 'DRAFT') return 'edit'
  return 'underway-o'
}

/** 拉取申报列表（每页 5，按申报时间倒序由后端保证） */
async function load() {
  loading.value = true
  try {
    const data = await fetchMyApplies({ pageNum: pageNum.value, pageSize })
    list.value = data?.list || []
    total.value = Number(data?.total ?? list.value.length) || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  load()
}

/** 草稿续填 / 其它进详情 */
function onRecordClick(item) {
  if (!item?.id) return
  if (item.status === 'DRAFT' && item.policyId) {
    router.push({ path: '/apply/materials', query: { policyId: String(item.policyId) } })
    return
  }
  router.push(`/apply/detail/${item.id}`)
}

/** 驳回后再开草稿并进入材料页（二次确认，防误点） */
async function onReapply(item) {
  if (!item?.id || reapplyingId.value) return
  try {
    await showConfirmDialog({
      title: '重新申报',
      message: '将按上次材料开一份新草稿，您可以改完再提交。确定继续吗？',
      confirmButtonText: '确定重新申报',
    })
  } catch {
    return
  }
  reapplyingId.value = item.id
  try {
    await reapplyFromRejected(router, item.id)
  } finally {
    reapplyingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f3f6f1;
  /* 底部分页 + 左下适老钮留白，避免内容贴底被挡 */
  padding-bottom: 120px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px 4px;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: var(--xct-color-muted, #5a5a5a);
  gap: 8px;
}
.tool-btn {
  min-height: 40px !important;
  padding: 0 14px !important;
  font-size: var(--xct-fs-tip, 0.9375rem) !important;
}
.state-box {
  padding: 40px 0;
  text-align: center;
}
.list {
  padding: 8px 16px;
}
/* 申报卡：白卡 + 左侧 4px 状态色条，颜色即状态 */
.card {
  position: relative;
  background: var(--xct-card-bg, #fff);
  border: var(--xct-card-border, 0 solid transparent);
  border-radius: var(--xct-radius, 14px);
  padding: 14px 16px 14px 20px;
  margin-bottom: 12px;
  box-shadow: var(--xct-card-shadow, 0 4px 14px rgba(27, 94, 32, 0.06));
  overflow: hidden;
}
.card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 5px;
  background: #c0c6bd;
}
.card.st-ok::before {
  background: var(--xct-green, #2e8b57);
}
.card.st-bad::before {
  background: var(--xct-red, #c62828);
}
.card.st-draft::before {
  background: #9e9e9e;
}
.card.st-pending::before {
  background: var(--xct-amber, #e65100);
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  cursor: pointer;
}
.status-icon {
  font-size: 20px;
  color: var(--xct-green, #2e8b57);
  flex-shrink: 0;
}
.st-bad .status-icon {
  color: var(--xct-red, #c62828);
}
.st-draft .status-icon {
  color: #757575;
}
.st-pending .status-icon {
  color: var(--xct-amber, #e65100);
}
.row strong {
  flex: 1;
  min-width: 0;
  font-size: var(--xct-fs-body, 1rem);
  color: var(--xct-color-text, #222);
}
.card p {
  margin: 4px 0 0;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: var(--xct-color-muted, #5a5a5a);
  line-height: 1.5;
}
.hint {
  color: #2e7d32 !important;
  font-weight: 600;
}
/* 发放信息行：金额大字突出，适老热区不低于 48 */
.disburse-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 10px 12px;
  min-height: 48px;
  box-sizing: border-box;
  border-radius: var(--xct-radius-sm, 10px);
  background: var(--xct-green-soft, #e8f5e9);
  color: var(--xct-green-deep, #1b5e20);
  font-size: var(--xct-fs-body, 1rem);
  cursor: pointer;
}
.disburse-icon {
  font-size: 20px;
  flex-shrink: 0;
}
.disburse-amount {
  margin-left: auto;
  font-size: var(--xct-fs-title, 1.25rem);
  font-weight: 700;
  color: var(--xct-green-deep, #1b5e20);
}
.disburse-wait {
  margin-top: 10px !important;
  padding: 10px 12px;
  border-radius: var(--xct-radius-sm, 10px);
  background: var(--xct-amber-soft, #fff3e0);
  color: var(--xct-amber, #e65100) !important;
  font-weight: 600;
  cursor: pointer;
}
/* 审批意见用浅底小块衬托，和普通文字拉开层次 */
.opinion {
  margin-top: 8px !important;
  padding: 8px 10px;
  border-radius: var(--xct-radius-sm, 10px);
  background: var(--xct-bg, #f3f6f1);
}
.actions {
  margin-top: 12px;
  display: flex;
  /* 通栏按钮，不被右侧旧浮钮遮挡 */
  justify-content: stretch;
}
.reapply-btn {
  width: 100%;
  height: var(--xct-hit-min, 48px) !important;
  font-size: var(--xct-fs-body, 1rem) !important;
  padding: 0 18px !important;
}
.pager {
  padding: 16px 8px 8px;
}
</style>