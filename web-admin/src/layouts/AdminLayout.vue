<template>
  <el-container class="admin-layout">
    <!-- 左侧菜单：按本端角色动态显示 -->
    <el-aside width="220px" class="aside">
      <div class="logo">乡策通 · {{ appRoleName }}</div>
      <el-menu
        :default-active="activeMenu"
        router
      >
        <el-menu-item v-for="item in menu" :key="item.path" :index="item.path">
          <el-icon><component :is="iconMap[item.icon]" /></el-icon>
          <!-- 「申请审批」菜单红点：待审批数量 > 0 时显示，> 99 显示 99+ -->
          <el-badge
            v-if="item.path === '/applies'"
            :value="pendingCount"
            :max="99"
            :hidden="pendingCount <= 0"
            class="menu-badge"
          >
            <span>{{ item.title }}</span>
          </el-badge>
          <span v-else>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏：姓名 / 角色 / 退出 -->
      <el-header class="header">
        <span class="page-title">{{ pageTitle }}</span>
        <div class="user-box">
          <span>{{ user?.name || '—' }}</span>
          <el-tag size="small" type="success" effect="plain">{{ roleLabel }}</el-tag>
          <el-button type="danger" link @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
// 管理端主布局：左侧菜单与顶栏，菜单按本端角色或登录后角色渲染；
// 「申请审批」菜单加待审批数量红点，仅村管/镇管请求统计，每 30s 刷新一次。
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Document, Notebook, User, List, Bell, FolderOpened, Setting, ChatDotRound } from '@element-plus/icons-vue'
import { getUser, getRole, clearAuth } from '@/utils/auth'
import { formatRole } from '@/utils/status'
import { fetchApplyStats } from '@/api/apply'
import { CURRENT_MENU, APP_ROLE_NAME, isSysMode, isUnifiedMode, MENU_BY_ROLE } from '@/config/role'

const route = useRoute()
const router = useRouter()

/** 待审批数量（红点数字），系统管理端不请求 */
const pendingCount = ref(0)
let statsTimer = null

/** 当前实际使用的角色：统一模式取登录后的角色，否则取构建时角色 */
const currentRole = computed(() => {
  if (isUnifiedMode) {
    return getRole() || 'VILLAGE_ADMIN'
  }
  return import.meta.env.VITE_APP_ROLE || 'VILLAGE_ADMIN'
})

/** 是否为系统管理员（用于决定是否请求统计） */
const isSys = computed(() => currentRole.value === 'SYS_ADMIN')

/** 菜单列表：统一模式动态获取，否则用构建时固定菜单 */
const menu = computed(() => {
  if (isUnifiedMode) {
    return MENU_BY_ROLE[currentRole.value] || []
  }
  return CURRENT_MENU
})

/** 端名显示：统一模式显示对应角色名，否则显示构建时端名 */
const appRoleName = computed(() => {
  if (isUnifiedMode) {
    const roleNames = {
      VILLAGE_ADMIN: '村管端',
      TOWN_ADMIN: '镇管端',
      SYS_ADMIN: '系统管理端',
    }
    return roleNames[currentRole.value] || '管理端'
  }
  return APP_ROLE_NAME
})

/** 拉取待审批统计；系统管理端直接跳过 */
async function loadStats() {
  if (isSys.value) return
  try {
    const data = await fetchApplyStats()
    pendingCount.value = data?.pendingCount ?? 0
  } catch {
    // 统计失败不阻断布局渲染
  }
}

onMounted(() => {
  loadStats()
  // 村管/镇管每 30 秒刷新一次待审批数量
  if (!isSys.value) {
    statsTimer = setInterval(loadStats, 30000)
  }
})

onBeforeUnmount(() => {
  if (statsTimer) {
    clearInterval(statsTimer)
    statsTimer = null
  }
})

/** 菜单项 icon 字符串键 → 图标组件 */
const iconMap = { Document, Notebook, User, List, Bell, FolderOpened, Setting, ChatDotRound }

const user = computed(() => getUser())
const roleLabel = computed(() => formatRole(user.value?.role))
const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta.title || '乡策通')

/** 退出登录并回登录页 */
function onLogout() {
  clearAuth()
  router.replace('/login')
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}
.aside {
  background: rgba(255, 255, 255, 0.75); /* 毛玻璃侧边栏 */
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-right: 1px solid rgba(217, 119, 6, 0.15);
  box-shadow: 2px 0 12px rgba(217, 119, 6, 0.05);
  z-index: 10;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-weight: 800;
  font-size: 18px;
  color: #78350f; /* 麦田深棕 */
  letter-spacing: 1px;
  border-bottom: 1px solid rgba(217, 119, 6, 0.1);
  background: transparent;
}

/* 侧边栏菜单定制 */
.aside :deep(.el-menu) {
  border-right: none;
  background-color: transparent !important;
  padding: 12px 8px;
}
.aside :deep(.el-menu-item) {
  height: 48px;
  line-height: 48px;
  border-radius: 12px;
  margin-bottom: 6px;
  color: #78350f !important;
  font-weight: 600;
}
.aside :deep(.el-menu-item:hover) {
  background-color: rgba(254, 243, 199, 0.5) !important;
}
.aside :deep(.el-menu-item.is-active) {
  background-color: #d97706 !important;
  color: #ffffff !important;
  box-shadow: 0 4px 12px rgba(217, 119, 6, 0.2);
}
.aside :deep(.el-menu-item.is-active i) {
  color: #ffffff !important;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(217, 119, 6, 0.15);
  height: 60px;
  box-shadow: 0 2px 12px rgba(217, 119, 6, 0.05);
  z-index: 9;
}
.page-title {
  font-size: 18px;
  font-weight: 800;
  color: #451a03;
}
.user-box {
  display: flex;
  align-items: center;
  gap: 16px;
  color: #78350f;
  font-weight: 600;
}
.menu-badge {
  margin-left: 6px;
}
.menu-badge :deep(.el-badge__content) {
  background-color: #ea580c;
  border: none;
}
.main {
  background: transparent;
  min-height: calc(100vh - 60px);
  padding: 24px;
}
</style>