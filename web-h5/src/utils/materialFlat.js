/**
 * 把结构化 materialData 展平成只读行（嵌套对象递归；图片 URL 标 isUrl）
 * @param {object|null|undefined} data
 * @returns {{ label: string, value: string, isUrl: boolean }[]}
 */
export function flattenMaterialData(data) {
  if (!data || typeof data !== 'object') return []
  const rows = []
  const walk = (obj, prefix) => {
    Object.entries(obj).forEach(([k, v]) => {
      const label = prefix ? `${prefix} · ${k}` : k
      if (v && typeof v === 'object' && !Array.isArray(v)) {
        walk(v, label)
      } else if (v != null && String(v).trim() !== '') {
        const s = String(v)
        rows.push({
          label,
          value: s,
          isUrl: s.startsWith('/uploads') || s.startsWith('http'),
        })
      }
    })
  }
  walk(data, '')
  return rows
}

/** 材料图片列表：兼容数组 / JSON 字符串 / 单字符串 */
export function normalizeMaterialUrls(urls) {
  if (!urls) return []
  if (Array.isArray(urls)) return urls.filter(Boolean)
  if (typeof urls === 'string') {
    try {
      const parsed = JSON.parse(urls)
      if (Array.isArray(parsed)) return parsed.filter(Boolean)
    } catch {
      /* 单 URL */
    }
    return urls.trim() ? [urls] : []
  }
  return []
}
