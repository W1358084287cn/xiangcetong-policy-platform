/**
 * 把 materialData（嵌套 materials 或扁平 fields）展平成可读行，供申报详情只读展示。
 * @param {object|null} materialData
 * @param {string[]} [materialUrls] 旧直提流程的图片列表
 * @param {object|null} [schema] 政策 materialSchema，用于把 code/key 映射成中文名
 * @returns {{ label: string, value: string, isImage: boolean }[]}
 */
export function flattenMaterialRows(materialData, materialUrls = [], schema = null) {
  const rows = []
  const labels = buildSchemaLabels(schema)

  if (materialData && typeof materialData === 'object') {
    for (const [key, val] of Object.entries(materialData)) {
      if (val != null && typeof val === 'object' && !Array.isArray(val)) {
        // 嵌套材料块：户主证 / 义务兵证 等
        for (const [fk, fv] of Object.entries(val)) {
          rows.push(toRow(`${labelOf(labels, key)} · ${labelOf(labels, key, fk)}`, fv))
        }
      } else {
        rows.push(toRow(labelOf(labels, key), val))
      }
    }
  }

  if (Array.isArray(materialUrls)) {
    materialUrls.forEach((url, i) => {
      if (url) rows.push(toRow(`材料照片 ${i + 1}`, url))
    })
  }

  return rows.filter((r) => r.value !== '' && r.value != null)
}

/** 从 materialSchema 提取 code/key → 中文名映射（材料块 title、字段 label） */
function buildSchemaLabels(schema) {
  const map = {}
  if (!schema || typeof schema !== 'object') return map
  for (const m of schema.materials || []) {
    if (m?.code && m?.title) map[m.code] = m.title
    for (const f of m?.fields || []) {
      if (m?.code && f?.key && f?.label) map[`${m.code}.${f.key}`] = f.label
    }
  }
  for (const f of schema.fields || []) {
    if (f?.key && f?.label) map[f.key] = f.label
  }
  return map
}

/** 取展示名：优先 schema 映射，退回内置词典，再退回原 key */
function labelOf(labels, key, subKey) {
  if (subKey != null) return labels[`${key}.${subKey}`] || humanize(subKey)
  return labels[key] || humanize(key)
}

/** 组装一行展示数据，并判断 value 是否为图片地址 */
function toRow(label, val) {
  const value = val == null ? '' : String(val)
  const isImage =
    /^https?:\/\//.test(value) ||
    value.startsWith('/uploads/') ||
    /\.(jpe?g|png|gif|webp)(\?|$)/i.test(value)
  return { label, value, isImage }
}

/** 简单把 camelCase / snake 转成可读中文旁路标签 */
function humanize(key) {
  if (!key || key === '_root') return '材料'
  const map = {
    idCard: '身份证',
    id_card: '身份证',
    id_copy: '户主身份证',
    soldier_id: '义务兵身份证',
    idCardFront: '身份证正面',
    idCardBack: '身份证反面',
    front: '正面',
    back: '反面',
    household: '户口本',
    bankCard: '银行卡',
    bankName: '开户行',
    accountName: '户名',
    bank_confirm: '银行卡确认',
    land_proof: '地块证明',
    landContract: '土地合同',
    contract: '合同照片',
    areaNote: '申报面积',
    invoice: '购机发票',
    invoicePhoto: '发票照片',
    machineModel: '机具型号',
    machine_photo: '机具实拍',
    machine: '机具照片',
    farm_proof: '养殖证明',
    proof: '证明照片',
    photo: '照片',
    name: '姓名',
    phone: '手机号',
  }
  if (map[key]) return map[key]
  // 常见中文 code 直接展示
  if (/[\u4e00-\u9fff]/.test(key)) return key
  return key
}
