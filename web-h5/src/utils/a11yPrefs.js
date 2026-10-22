/**
 * 适老偏好：字号三档 + 高对比主题。
 * 读写 localStorage，并同步到 documentElement（data-font / data-contrast）。
 */

export const FONT_LEVELS = [
  { key: 'normal', label: '标准', size: '16px' },
  { key: 'large', label: '大字', size: '18px' },
  { key: 'xlarge', label: '特大', size: '20px' },
]

export const FONT_STORAGE_KEY = 'xct-font-level'
export const CONTRAST_STORAGE_KEY = 'xct-contrast'

/** 读取已存字号档；非法值回落标准 */
export function loadFontLevel() {
  const saved = typeof localStorage !== 'undefined' ? localStorage.getItem(FONT_STORAGE_KEY) : null
  const found = FONT_LEVELS.find((l) => l.key === saved)
  return found || FONT_LEVELS[0]
}

/** 按 key 取字号档；找不到回落标准 */
export function getFontLevelByKey(key) {
  return FONT_LEVELS.find((l) => l.key === key) || FONT_LEVELS[0]
}

/** 下一档字号（循环） */
export function nextFontLevel(currentKey) {
  const i = FONT_LEVELS.findIndex((l) => l.key === currentKey)
  const idx = i >= 0 ? (i + 1) % FONT_LEVELS.length : 0
  return FONT_LEVELS[idx]
}

/**
 * 应用字号到 DOM（可在无 document 的单测中跳过）
 * @param {{ key: string, size: string }} level
 * @param {Document} [doc]
 */
export function applyFontLevel(level, doc = typeof document !== 'undefined' ? document : null) {
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(FONT_STORAGE_KEY, level.key)
  }
  if (!doc?.documentElement) return level
  doc.documentElement.dataset.font = level.key
  doc.documentElement.style.fontSize = level.size
  return level
}

/** 是否开启高对比 */
export function loadContrastHigh() {
  if (typeof localStorage === 'undefined') return false
  return localStorage.getItem(CONTRAST_STORAGE_KEY) === 'high'
}

/**
 * 开关高对比并写回存储
 * @param {boolean} on
 * @param {Document} [doc]
 */
export function applyContrast(on, doc = typeof document !== 'undefined' ? document : null) {
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(CONTRAST_STORAGE_KEY, on ? 'high' : 'normal')
  }
  if (!doc?.documentElement) return on
  if (on) doc.documentElement.dataset.contrast = 'high'
  else delete doc.documentElement.dataset.contrast
  return on
}

/** 切换高对比，返回切换后是否开启 */
export function toggleContrast(doc = typeof document !== 'undefined' ? document : null) {
  return applyContrast(!loadContrastHigh(), doc)
}
