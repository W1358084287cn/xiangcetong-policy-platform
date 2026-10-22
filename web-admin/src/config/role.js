/**
 * 三角色前端裁剪统一配置
 * 说明：一套代码通过 Vite mode 产出多个管理端：
 *   - 村管端 5174（village）
 *   - 镇管端 5175（town）
 *   - 系统管理端 5176（sys）
 *   - 统一登录端 5177（unified，登录时选择角色）
 * 当前端角色与端名由 .env.* 注入 VITE_APP_ROLE / VITE_APP_ROLE_NAME。
 * 本文件是「路由裁剪、侧边菜单、登录跳转、角色校验」读取当前端身份的唯一入口。
 */

/** 本端角色编码（由构建 mode 注入，缺省回退村管） */
export const APP_ROLE = import.meta.env.VITE_APP_ROLE || 'VILLAGE_ADMIN'

/** 本端显示名（如 村管端 / 镇管端 / 系统管理端 / 管理端） */
export const APP_ROLE_NAME = import.meta.env.VITE_APP_ROLE_NAME || '村管端'

/** 角色编码常量 */
export const SYS_ADMIN = 'SYS_ADMIN'
export const TOWN_ADMIN = 'TOWN_ADMIN'
export const VILLAGE_ADMIN = 'VILLAGE_ADMIN'
export const UNIFIED = 'UNIFIED'

/** 是否为统一登录模式（登录页展示角色选择器） */
export const isUnifiedMode = APP_ROLE === UNIFIED

/** 角色选项列表（用于统一登录页的角色选择器） */
export const ROLE_OPTIONS = [
  { value: VILLAGE_ADMIN, label: '村管端', desc: '村级申报审批' },
  { value: TOWN_ADMIN, label: '镇管端', desc: '镇级申报审批' },
  { value: SYS_ADMIN, label: '系统管理端', desc: '政策/用户/日志/通知' },
]

/**
 * 各端菜单与页面标题
 * 村管/镇管只有「申请审批」；系统管理端为政策/用户/日志/通知（无审批菜单）。
 * icon 为字符串键，在 AdminLayout 中映射为具体图标组件。
 */
export const MENU_BY_ROLE = {
  [VILLAGE_ADMIN]: [{ path: '/applies', title: '申请审批', icon: 'Document' }],
  [TOWN_ADMIN]: [{ path: '/applies', title: '申请审批', icon: 'Document' }],
  [SYS_ADMIN]: [
    { path: '/policies', title: '政策管理', icon: 'Notebook' },
    { path: '/users', title: '用户管理', icon: 'User' },
    { path: '/logs', title: '操作日志', icon: 'List' },
    { path: '/notices', title: '通知管理', icon: 'Bell' },
    { path: '/knowledge', title: '知识库管理', icon: 'FolderOpened' },
    { path: '/chat-logs', title: 'AI 对话日志', icon: 'ChatDotRound' },
    { path: '/ai-config', title: 'AI 设置', icon: 'Setting' },
  ],
}

/** 本端菜单（渲染左侧菜单用） */
export const CURRENT_MENU = MENU_BY_ROLE[APP_ROLE] || []

/** 本端登录后默认页 */
export const DEFAULT_PATH = CURRENT_MENU[0]?.path || '/applies'

/** 本端允许访问的路径集合（越权访问时重定向到默认页） */
export const ALLOWED_PATHS = new Set(CURRENT_MENU.map((m) => m.path))

/** 是否系统管理端 */
export const isSysMode = APP_ROLE === SYS_ADMIN
/** 是否村级管理端 */
export const isVillageMode = APP_ROLE === VILLAGE_ADMIN
/** 是否镇级管理端 */
export const isTownMode = APP_ROLE === TOWN_ADMIN