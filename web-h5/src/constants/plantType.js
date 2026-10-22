/**
 * 种养类型统一字典（河北粮油为主，细分作物/畜禽）。
 * filter 对应匹配接口 plantType 查询码；group 区分种植/养殖/农机。
 */
export const PLANT_TYPE_OPTIONS = [
  { text: '小麦种植', value: '小麦种植', filter: 'RICE', group: 'plant' },
  { text: '玉米种植', value: '玉米种植', filter: 'RICE', group: 'plant' },
  { text: '水稻种植', value: '水稻种植', filter: 'RICE', group: 'plant' },
  { text: '大豆种植', value: '大豆种植', filter: 'RICE', group: 'plant' },
  { text: '谷子种植', value: '谷子种植', filter: 'OTHER', group: 'plant' },
  { text: '高粱种植', value: '高粱种植', filter: 'OTHER', group: 'plant' },
  { text: '花生种植', value: '花生种植', filter: 'OTHER', group: 'plant' },
  { text: '棉花种植', value: '棉花种植', filter: 'OTHER', group: 'plant' },
  { text: '马铃薯种植', value: '马铃薯种植', filter: 'OTHER', group: 'plant' },
  { text: '甘薯种植', value: '甘薯种植', filter: 'OTHER', group: 'plant' },
  { text: '大豆玉米带状复合', value: '大豆玉米带状复合', filter: 'RICE', group: 'plant' },
  { text: '蔬菜种植', value: '蔬菜种植', filter: 'VEGETABLE', group: 'plant' },
  { text: '设施蔬菜', value: '设施蔬菜', filter: 'VEGETABLE', group: 'plant' },
  { text: '果树种植', value: '果树种植', filter: 'FRUIT', group: 'plant' },
  { text: '苹果种植', value: '苹果种植', filter: 'FRUIT', group: 'plant' },
  { text: '梨树种植', value: '梨树种植', filter: 'FRUIT', group: 'plant' },
  { text: '葡萄种植', value: '葡萄种植', filter: 'FRUIT', group: 'plant' },
  { text: '茶叶种植', value: '茶叶种植', filter: 'TEA', group: 'plant' },
  { text: '中药材种植', value: '中药材种植', filter: 'OTHER', group: 'plant' },
  { text: '油菜种植', value: '油菜种植', filter: 'OTHER', group: 'plant' },
  { text: '芝麻种植', value: '芝麻种植', filter: 'OTHER', group: 'plant' },
  { text: '畜禽养殖', value: '畜禽养殖', filter: 'LIVESTOCK', group: 'live' },
  { text: '生猪养殖', value: '生猪养殖', filter: 'LIVESTOCK', group: 'live' },
  { text: '肉牛养殖', value: '肉牛养殖', filter: 'LIVESTOCK', group: 'live' },
  { text: '奶牛养殖', value: '奶牛养殖', filter: 'LIVESTOCK', group: 'live' },
  { text: '肉羊养殖', value: '肉羊养殖', filter: 'LIVESTOCK', group: 'live' },
  { text: '蛋鸡养殖', value: '蛋鸡养殖', filter: 'LIVESTOCK', group: 'live' },
  { text: '肉鸡养殖', value: '肉鸡养殖', filter: 'LIVESTOCK', group: 'live' },
  { text: '鸭养殖', value: '鸭养殖', filter: 'LIVESTOCK', group: 'live' },
  { text: '兔养殖', value: '兔养殖', filter: 'LIVESTOCK', group: 'live' },
  { text: '水产养殖', value: '水产养殖', filter: 'AQUACULTURE', group: 'live' },
  { text: '农机作业服务', value: '农机作业', filter: 'MACHINERY', group: 'mach' },
  { text: '其他', value: '其他', filter: 'OTHER', group: 'other' },
]

/** 「我的」选择器用 columns */
export const PLANT_PICKER_COLUMNS = PLANT_TYPE_OPTIONS.map(({ text, value }) => ({ text, value }))

/** 兼营可选：全部 +「无」 */
export const SECONDARY_PLANT_OPTIONS = [
  { text: '无兼营', value: '' },
  ...PLANT_PICKER_COLUMNS,
]

/** 种植类 */
export const PLANT_GUIDE_OPTS = PLANT_TYPE_OPTIONS.filter((o) => o.group === 'plant').map(
  ({ text, value }) => ({ text, value })
)

/** 养殖类 */
export const LIVE_GUIDE_OPTS = PLANT_TYPE_OPTIONS.filter((o) => o.group === 'live').map(
  ({ text, value }) => ({ text, value })
)

/** 匹配列表筛选 */
export const PLANT_FILTER_OPTIONS = (() => {
  const seen = new Set()
  const opts = [{ text: '全部政策', value: '' }]
  for (const o of PLANT_TYPE_OPTIONS) {
    if (seen.has(o.filter)) continue
    seen.add(o.filter)
    const labelMap = {
      RICE: '粮食种植（麦/玉/稻等）',
      TEA: '茶叶种植',
      VEGETABLE: '蔬菜种植',
      FRUIT: '果树种植',
      LIVESTOCK: '畜禽养殖',
      AQUACULTURE: '水产养殖',
      MACHINERY: '农机相关',
      OTHER: '其他',
    }
    opts.push({ text: labelMap[o.filter] || o.text, value: o.filter })
  }
  return opts
})()

const PLANT_VALUES = new Set(PLANT_TYPE_OPTIONS.filter((o) => o.group === 'plant').map((o) => o.value))
const LIVE_VALUES = new Set(PLANT_TYPE_OPTIONS.filter((o) => o.group === 'live').map((o) => o.value))

/** 是否算种植类已填 */
export function isPlantGroupDone(plantType) {
  const pt = plantType || ''
  return PLANT_VALUES.has(pt) || /稻|粮|麦|玉|豆|谷|花|茶|菜|果|棉|薯|药|高粱/.test(pt)
}

/** 是否算养殖类已填 */
export function isLiveGroupDone(plantType) {
  const pt = plantType || ''
  return LIVE_VALUES.has(pt) || /畜|禽|养殖|水产|猪|牛|羊|鸡|鸭/.test(pt)
}