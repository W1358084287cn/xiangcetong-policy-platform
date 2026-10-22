/**
 * 村民精细资料选项（与后端 subject_type / livestock 等字段对齐）
 */

/** 经营主体类型 */
export const SUBJECT_OPTIONS = [
  { text: '普通农户', value: 'FARMER' },
  { text: '家庭农场', value: 'FAMILY_FARM' },
  { text: '农民合作社', value: 'COOP' },
  { text: '农业企业', value: 'ENTERPRISE' },
]

/** 主体展示名 */
export function subjectLabel(code) {
  return SUBJECT_OPTIONS.find((o) => o.value === code)?.text || code || ''
}

/** 养殖细分（更细，用于精准匹配存栏类政策） */
export const LIVESTOCK_TYPE_OPTIONS = [
  { text: '生猪', value: '生猪' },
  { text: '肉牛', value: '肉牛' },
  { text: '奶牛', value: '奶牛' },
  { text: '肉羊', value: '肉羊' },
  { text: '蛋鸡', value: '蛋鸡' },
  { text: '肉鸡', value: '肉鸡' },
  { text: '鸭', value: '鸭' },
  { text: '鹅', value: '鹅' },
  { text: '兔', value: '兔' },
  { text: '其他畜禽', value: '其他畜禽' },
]

/** 意向农机类型 */
export const MACHINERY_TYPE_OPTIONS = [
  { text: '拖拉机', value: '拖拉机' },
  { text: '联合收割机', value: '联合收割机' },
  { text: '播种机', value: '播种机' },
  { text: '插秧机', value: '插秧机' },
  { text: '植保无人机', value: '植保无人机' },
  { text: '青贮收获机', value: '青贮收获机' },
  { text: '烘干机', value: '烘干机' },
  { text: '其他农机', value: '其他农机' },
]

/** 是否类单选：0 否 / 1 是 */
export const YES_NO_OPTIONS = [
  { text: '是', value: 1 },
  { text: '否', value: 0 },
]

/** 是否字段展示文案 */
export function ynLabel(v) {
  if (v === 1 || v === '1') return '是'
  if (v === 0 || v === '0') return '否'
  return ''
}
