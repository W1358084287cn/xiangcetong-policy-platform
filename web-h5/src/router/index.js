import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { public: true },
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/ForgotPassword.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/TabLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'policies',
        name: 'PolicyList',
        component: () => import('@/views/PolicyList.vue'),
        meta: { title: '政策匹配' },
      },
      {
        // 旧直提申报页已下线（所有政策均配 materialSchema，统一走材料分项页）：
        // 带 policyId → 材料页；否则回政策匹配
        path: 'apply',
        redirect: (to) =>
          to.query.policyId
            ? { path: '/apply/materials', query: { policyId: String(to.query.policyId) } }
            : { path: '/policies' },
      },
      {
        path: 'mine',
        name: 'Mine',
        component: () => import('@/views/Mine.vue'),
        meta: { title: '我的' },
      },
      {
        path: 'apply-center',
        name: 'ApplyCenter',
        component: () => import('@/views/ApplyCenter.vue'),
        meta: { title: '申报' },
      },
      {
        path: 'butler',
        name: 'AiButler',
        component: () => import('@/views/AiButler.vue'),
        meta: { title: 'AI 管家' },
      },
    ],
  },
  {
    path: '/mine/favorites',
    name: 'MineFavorites',
    component: () => import('@/views/MineFavorites.vue'),
    meta: { title: '收藏的政策' },
  },
  {
    path: '/mine/applies',
    name: 'MineApplies',
    component: () => import('@/views/MineApplies.vue'),
    meta: { title: '申报进度' },
  },
  {
    path: '/mine/password',
    name: 'ChangePassword',
    component: () => import('@/views/ChangePassword.vue'),
    meta: { title: '修改密码' },
  },
  {
    path: '/policy/:id',
    name: 'PolicyDetail',
    component: () => import('@/views/PolicyDetail.vue'),
    meta: { title: '政策详情' },
  },
  {
    path: '/apply/materials',
    name: 'ApplyMaterials',
    component: () => import('@/views/ApplyMaterials.vue'),
    meta: { title: '申报材料' },
  },
  {
    path: '/apply/detail/:id',
    name: 'ApplyDetail',
    component: () => import('@/views/ApplyDetail.vue'),
    meta: { title: '申报详情' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 未登录跳转登录页
router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router