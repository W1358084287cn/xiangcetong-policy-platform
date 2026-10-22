<!--
  我的：村民个人信息卡 + 常用功能入口（资料/收藏/申报）+ 退出登录；
  edit=1 时切换为整表资料编辑模式。
-->
<template>
  <div class="page">
    <van-nav-bar
      :title="profileMode ? '个人资料' : '我的'"
      fixed
      placeholder
      :left-arrow="profileMode"
      @click-left="onProfileBack"
    />

    <!-- 整表资料：一次看全、想改哪项改哪项（不再分步） -->
    <div v-if="profileMode" class="section">
      <p class="edit-tip">以下资料可随时修改，改完点底部「保存资料」即可</p>
      <ProfileForm
        v-model:editing="editing"
        :wizard="false"
        @saved="onProfileSaved"
      />
    </div>

    <!-- 模块入口 -->
    <template v-else>
      <!-- 用户信息卡：大头像 + 姓名 + 身份标签 + 手机号 -->
      <div class="user-card">
        <div class="avatar" aria-hidden="true">
          <van-icon name="contact" size="30" />
        </div>
        <div class="user-meta">
          <div class="name-row">
            <strong>{{ displayName }}</strong>
            <span class="role-tag">村民</span>
          </div>
          <p v-if="displayPhone">手机号：{{ displayPhone }}</p>
          <p v-else>村民端 · 资料与申报入口</p>
        </div>
        <van-icon name="arrow" class="uc-arrow" @click="goProfile" />
      </div>

      <h3 class="group-title">常用功能</h3>
      <div class="mod-grid">
        <button type="button" class="mod-card" @click="goProfile">
          <span class="mod-icon profile" aria-hidden="true">
            <van-icon name="contact" size="26" />
          </span>
          <span class="mod-body">
            <strong>个人资料</strong>
            <em>整页编辑 · 想改哪项改哪项</em>
          </span>
          <van-icon name="arrow" color="#5a5a5a" />
        </button>

        <button type="button" class="mod-card" @click="$router.push('/mine/favorites')">
          <span class="mod-icon fav" aria-hidden="true">
            <van-icon name="star-o" size="26" />
          </span>
          <span class="mod-body">
            <strong>收藏的政策</strong>
            <em>查看已收藏条目</em>
          </span>
          <van-icon name="arrow" color="#5a5a5a" />
        </button>

        <button type="button" class="mod-card" @click="$router.push('/mine/applies')">
          <span class="mod-icon apply" aria-hidden="true">
            <van-icon name="orders-o" size="26" />
          </span>
          <span class="mod-body">
            <strong>申报进度</strong>
            <em>草稿续填 · 驳回可再申</em>
          </span>
          <van-icon name="arrow" color="#5a5a5a" />
        </button>

        <button type="button" class="mod-card" @click="$router.push('/mine/password')">
          <span class="mod-icon pwd" aria-hidden="true">
            <van-icon name="lock" size="26" />
          </span>
          <span class="mod-body">
            <strong>修改密码</strong>
            <em>定期更换更安全</em>
          </span>
          <van-icon name="arrow" color="#5a5a5a" />
        </button>
      </div>

      <div class="btn-wrap">
        <van-button round block class="main-btn" @click="onLogout">退出登录</van-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { clearAuth, getUser } from '@/utils/auth'
import ProfileForm from '@/components/ProfileForm.vue'

const route = useRoute()
const router = useRouter()
/** 进入资料页即处于可编辑 */
const editing = ref(true)

/**
 * 资料编辑页：edit=1 / wizard=1（兼容旧链接）/ profile=1
 * 一律整表编辑，不再走分步向导。
 */
const profileMode = computed(() => {
  const q = route.query
  return (
    q.edit === '1' ||
    q.edit === 'true' ||
    q.wizard === '1' ||
    q.wizard === 'true' ||
    q.profile === '1' ||
    q.profile === 'true'
  )
})

/** 是否从首页匹配入口跳来（保存后进匹配列表） */
const fromMatch = computed(() => route.query.from === 'match')

const displayName = computed(() => getUser()?.name || '村民用户')
/** 登录缓存里的手机号（没有则不显示该行） */
const displayPhone = computed(() => getUser()?.phone || '')

watch(
  profileMode,
  (v) => {
    if (v) editing.value = true
  },
  { immediate: true }
)

/** 进入整表资料 */
function goProfile() {
  router.push({ path: '/mine', query: { edit: '1' } })
}

function onProfileBack() {
  router.replace({ path: '/mine' })
}

/** 保存成功：匹配入口则去政策列表，否则留在本页继续可改 */
function onProfileSaved() {
  editing.value = true
  if (fromMatch.value) {
    router.replace({ path: '/policies' })
  }
}

async function onLogout() {
  try {
    await showConfirmDialog({ title: '提示', message: '确定退出登录？' })
    clearAuth()
    router.replace('/login')
  } catch {
    // 取消
  }
}
</script>

<style scoped>
/* ==========================================================================
   Impeccable UI: Golden Wheat & Prairie Theme (Staggered Reveal)
   ========================================================================== */
@keyframes slideFadeUp {
  from { opacity: 0; filter: blur(8px); transform: translateY(20px); }
  to { opacity: 1; filter: blur(0); transform: translateY(0); }
}

.page {
  padding-bottom: 24px;
  min-height: 100vh;
  /* background comes from global App.vue / style.css */
}
.section {
  margin-top: 8px;
  padding-bottom: 80px;
}
.edit-tip {
  margin: 0 16px 12px;
  padding: 12px 16px;
  background: rgba(254, 243, 199, 0.6);
  border-radius: 12px;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: #92400e;
  line-height: 1.5;
  font-weight: 600;
  border: 1px solid rgba(251, 191, 36, 0.3);
}

/* 用户信息卡：麦田金渐变 + 高级毛玻璃阴影 */
.user-card {
  margin: 16px 16px 12px;
  padding: 24px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  background: linear-gradient(135deg, rgba(254, 243, 199, 0.8) 0%, rgba(255, 255, 255, 0.8) 100%);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: var(--xct-card-border);
  border-radius: var(--xct-radius);
  box-shadow: var(--xct-card-shadow);
  animation: slideFadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.avatar {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 8px 16px rgba(217, 119, 6, 0.25);
}

.user-meta {
  flex: 1;
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name-row strong {
  font-size: var(--xct-fs-title, 1.25rem);
  color: #451a03;
  font-weight: 800;
}

.role-tag {
  padding: 4px 12px;
  border-radius: 999px;
  background: #65a30d;
  color: #fff;
  font-size: var(--xct-fs-meta, 0.875rem);
  font-weight: 600;
  flex-shrink: 0;
}

.user-meta p {
  margin: 8px 0 0;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: #92400e;
  font-weight: 500;
}

.uc-arrow {
  color: #d97706;
  opacity: 0.6;
  padding: 8px;
  flex-shrink: 0;
}

/* 入口分组标题 */
.group-title {
  margin: 24px 16px 12px;
  font-size: var(--xct-fs-body, 1rem);
  font-weight: 800;
  color: #78350f;
}

.mod-grid {
  margin: 0 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 模块卡片交错入场动画 */
.mod-card {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
  min-height: var(--xct-hit-min, 48px);
  padding: 20px;
  border: var(--xct-card-border);
  border-radius: var(--xct-radius);
  background: var(--xct-card-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: var(--xct-card-shadow);
  text-align: left;
  cursor: pointer;
  animation: slideFadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
  transition: transform 0.25s, box-shadow 0.25s;
}

.mod-card:nth-child(1) { animation-delay: 0.1s; }
.mod-card:nth-child(2) { animation-delay: 0.15s; }
.mod-card:nth-child(3) { animation-delay: 0.2s; }
.mod-card:nth-child(4) { animation-delay: 0.25s; }

.mod-card:active {
  transform: scale(0.98);
  box-shadow: 0 4px 12px rgba(217, 119, 6, 0.04);
}

.mod-icon {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.mod-icon.profile { background: #fef3c7; color: #d97706; }
.mod-icon.fav { background: #ffedd5; color: #b45309; }
.mod-icon.apply { background: #dcfce7; color: #4d7c0f; }
.mod-icon.pwd { background: #fee2e2; color: #b91c1c; }

.mod-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mod-body strong {
  font-size: var(--xct-fs-body, 1rem);
  color: #451a03;
  font-weight: 800;
}

.mod-body em {
  font-style: normal;
  font-size: var(--xct-fs-meta, 0.875rem);
  color: #92400e;
}

.btn-wrap {
  margin: 32px 16px 16px;
  animation: slideFadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
  animation-delay: 0.35s;
}

.main-btn {
  height: var(--xct-hit-min, 52px) !important;
  font-size: var(--xct-fs-body, 1rem) !important;
  color: #b91c1c !important;
  background: var(--xct-card-bg) !important;
  backdrop-filter: blur(12px) !important;
  border: 1px solid #fca5a5 !important;
  border-radius: var(--xct-radius-sm) !important;
  font-weight: 700 !important;
}
</style>
