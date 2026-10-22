<!--
  申报中心：村民申报补贴的统一入口。
  两个通栏大按钮——「我要申报」先过资料门禁再进政策匹配，「我的申报进度」直达进度列表。
-->
<template>
  <div class="page">
    <!-- 顶栏：品牌 + 页面名 -->
    <header class="topbar">
      <div class="brand-mini">
        <span class="logo-dot" aria-hidden="true" />
        <strong>乡策通</strong>
      </div>
      <div class="meta">
        <van-icon name="edit" />
        <span>申报中心</span>
      </div>
    </header>

    <!-- 页头引导语 -->
    <section class="lead">
      <h1 class="lead-title">要办补贴，点下面按钮</h1>
      <p class="lead-sub">选好政策、填好材料，几步就能提交申报</p>
    </section>

    <!-- 两大入口 -->
    <section class="actions">
      <button type="button" class="big-card primary" @click="onApply">
        <span class="big-icon" aria-hidden="true">
          <van-icon name="coupon-o" size="30" />
        </span>
        <span class="big-body">
          <strong>我要申报</strong>
          <em>先看能领哪些补贴，再填材料提交</em>
        </span>
        <van-icon name="arrow" class="big-arrow" />
      </button>

      <button type="button" class="big-card" @click="onProgress">
        <span class="big-icon blue" aria-hidden="true">
          <van-icon name="orders-o" size="30" />
        </span>
        <span class="big-body">
          <strong>我的申报进度</strong>
          <em>草稿续填 · 驳回可再申</em>
        </span>
        <van-icon name="arrow" class="big-arrow" />
      </button>
    </section>

    <p class="tip">申报后可在进度里查看村、镇两级审批结果</p>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { fetchAndCheckProfile, missingLabel } from '@/utils/profileGate'

const router = useRouter()

/** 我要申报：先过资料门禁（与首页「立即匹配」一致），齐了进政策匹配 */
async function onApply() {
  const gate = await fetchAndCheckProfile()
  if (!gate.ready) {
    showToast(`还差${missingLabel(gate.missing)}，先补齐再申报`)
    router.push({ path: '/mine', query: { edit: '1', from: 'match' } })
    return
  }
  router.push({ path: '/policies' })
}

/** 我的申报进度：直达进度列表 */
function onProgress() {
  router.push('/mine/applies')
}
</script>

<style scoped>
/* ==========================================================================
   Impeccable UI: Golden Wheat & Prairie Theme (Apply Center)
   ========================================================================== */
.page {
  min-height: 100vh;
  padding-bottom: 24px;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: rgba(255, 253, 245, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 4px 24px rgba(217, 119, 6, 0.06);
}
.brand-mini {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #78350f;
  font-size: var(--xct-fs-title, 1.25rem);
}
.logo-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  box-shadow: 0 2px 12px rgba(217, 119, 6, 0.3);
}
.meta {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(254, 243, 199, 0.6);
  border: 1px solid rgba(251, 191, 36, 0.3);
  color: #92400e;
  font-size: var(--xct-fs-meta, 0.875rem);
  font-weight: 600;
}

/* 引导语：居中大字，一眼看懂下一步 */
.lead {
  padding: 32px 20px 16px;
  text-align: center;
}
.lead-title {
  margin: 0;
  font-size: var(--xct-fs-title, 1.25rem);
  font-weight: 800;
  color: #451a03;
}
.lead-sub {
  margin: 8px 0 0;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: #92400e;
  font-weight: 500;
}

/* 两大入口：通栏大卡片，热区放大便于老人点按 */
.actions {
  margin: 16px 16px 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.big-card {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
  min-height: 80px;
  padding: 20px;
  border: var(--xct-card-border);
  border-radius: var(--xct-radius);
  background: var(--xct-card-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: var(--xct-card-shadow);
  text-align: left;
  cursor: pointer;
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.25s;
}
/* 主入口用农绿渐变底，和其它卡片拉开主次 */
.big-card.primary {
  background: linear-gradient(135deg, rgba(220, 252, 231, 0.8) 0%, rgba(255, 255, 255, 0.8) 100%);
  border: 1px solid rgba(101, 163, 13, 0.3);
  box-shadow: 0 12px 32px rgba(101, 163, 13, 0.1);
}
.big-card:active {
  transform: scale(0.97);
  box-shadow: 0 4px 12px rgba(217, 119, 6, 0.04);
}

.big-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: rgba(101, 163, 13, 0.15);
  color: #4d7c0f;
}
.big-icon.blue {
  background: rgba(217, 119, 6, 0.15);
  color: #b45309;
}
.big-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.big-body strong {
  font-size: var(--xct-fs-body, 1rem);
  font-weight: 800;
  color: #451a03;
}
.big-body em {
  font-style: normal;
  font-size: var(--xct-fs-meta, 0.875rem);
  color: #92400e;
}
.big-arrow {
  color: #d97706;
  opacity: 0.6;
  flex-shrink: 0;
}

/* 底部小字提示 */
.tip {
  margin: 24px 20px 0;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: #92400e;
  line-height: 1.5;
  text-align: center;
  font-weight: 500;
}
</style>
