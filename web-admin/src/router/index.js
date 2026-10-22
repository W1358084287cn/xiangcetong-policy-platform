/**
 * 管理端路由：一套代码按 Vite mode 固定角色裁剪，或统一模式动态裁剪
 * 村管/镇管端仅「申请审批 /applies」；系统管理端为政策/用户/日志/通知。
 * 统一模式（unified）始终注册全部子路由，登录后由 beforeEach 守卫按角色控制访问权限，
 * 避免 addRoute 不持久导致页面刷新后路由丢失的死循环问题。
 */
import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getRole, clearAuth } from '@/utils/auth'
import {
  APP_ROLE,
  ALLOWED_PATHS,
  DEFAULT_PATH,
  isUnifiedMode,
  MENU_BY_ROLE,
} from '@/config/role'

/** 所有可用子路由定义 */
const allChildren = {
  policies: {
    path: 'policies',
    name: 'PolicyManage',
    component: () => import('@/views/PolicyManage.vue'),
    meta: { title: '政策管理', role: 'SYS_ADMIN' },
  },
  users: {
    path: 'users',
    name: 'UserManage',
    component: () => import('@/views/UserManage.vue'),
    meta: { title: '用户管理', role: 'SYS_ADMIN' },
  },
  logs: {
    path: 'logs',
    name: 'LogList',
    component: () => import('@/views/LogList.vue'),
    meta: { title: '操作日志', role: 'SYS_ADMIN' },
  },
  notices: {
    path: 'notices',
    name: 'NoticeManage',
    component: () => import('@/views/NoticeManage.vue'),
    meta: { title: '通知管理', role: 'SYS_ADMIN' },
  },
  knowledge: {
    path: 'knowledge',
    name: 'KnowledgeManage',
    component: () => import('@/views/KnowledgeManage.vue'),
    meta: { title: '知识库管理', role: 'SYS_ADMIN' },
  },
  'ai-config': {
    path: 'ai-config',
    name: 'AiConfig',
    component: () => import('@/views/AiConfig.vue'),
    meta: { title: 'AI 设置', role: 'SYS_ADMIN' },
  },
  'chat-logs': {
    path: 'chat-logs',
    name: 'ChatLogs',
    component: () => import('@/views/ChatLogs.vue'),
    meta: { title: 'AI 对话日志', role: 'SYS_ADMIN' },
  },
  applies: {
    path: 'applies',
    name: 'ApplyList',
    component: () => import('@/views/ApplyList.vue'),
    meta: { title: '申请审批', role: 'APPLY' },
  },
}

/**
 * 根据角色获取子路由列表
 * @param {string} role - 角色编码
 * @returns {Array} 子路由数组
 */
function getChildrenByRole(role) {
  if (role === 'SYS_ADMIN') {
    return [allChildren.policies, allChildren.users, allChildren.logs, allChildren.notices, allChildren.knowledge, allChildren['ai-config'], allChildren['chat-logs']]
  }
  // VILLAGE_ADMIN 和 TOWN_ADMIN 都只有申请审批
  return [allChildren.applies]
}

/** 当前模式的子路由：统一模式预注册全部，非统一模式按构建时角色裁剪 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    name: 'AdminLayout',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: DEFAULT_PATH,
    children: isUnifiedMode
      ? [allChildren.applies, allChildren.policies, allChildren.users, allChildren.logs, allChildren.notices, allChildren.knowledge, allChildren['ai-config'], allChildren['chat-logs']]
      : getChildrenByRole(APP_ROLE),
  },
  // 未匹配路径 → 默认页
  { path: '/:pathMatch(.*)*', redirect: DEFAULT_PATH },
]

const router = createRouter({
  // 2026-10-18 修复：必须带上构建 base。
  // 管理端挂在 /admin/ 子路径下，若 history 用默认 '/'，
  // 首次访问 /admin/ 虽能正常渲染，但路由一跳转就会把地址栏改写成 /login，
  // 此时刷新会掉到根路径（村民端 H5），管理端"刷新即失联"。
  // vite.config.js 的 base 为 '/'，vite.config.docker.js 的 base 为 '/admin/'，
  // 用 import.meta.env.BASE_URL 可同时兼容本地开发与容器部署。
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

/**
 * 路由守卫：
 * - 未登录去登录
 * - 统一模式：按角色控制页面访问权限（已注册全部路由，按 role 过滤）
 * - 其他模式：登录态角色与本端角色不符则清登录态；越权路径回默认页
 */
router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 统一模式：按实际登录角色限制可访问页面
  if (isUnifiedMode) {
    const role = getRole()
    const menu = MENU_BY_ROLE[role] || []
    const allowedPaths = new Set(menu.map((m) => m.path))
    // 未匹配到角色菜单的路径，重定向到该角色默认页
    if (!allowedPaths.has(to.path)) {
      return { path: menu[0]?.path || '/applies' }
    }
    return true
  }
  // 换端/串端（localStorage 中的角色与本端不符）→ 清除登录态
  if (getRole() !== APP_ROLE) {
    clearAuth()
    return { path: '/login' }
  }
  // 本端未配置的路径（含越权访问）→ 重定向默认页
  if (!ALLOWED_PATHS.has(to.path)) {
    return { path: DEFAULT_PATH }
  }
  return true
})

export default router