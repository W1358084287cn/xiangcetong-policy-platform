/**
 * 演示地区树：河北省石家庄市正定县 / 藁城区（与河北惠农政策 region_codes 对齐）。
 * 老人点选即可，少打字，降低地区维匹配失败。
 */
export const REGION_TREE = [
  {
    text: '正定县',
    value: '正定县',
    children: [
      {
        text: '正定镇',
        value: '正定镇',
        children: [
          { text: '东村', value: '东村' },
          { text: '西村', value: '西村' },
        ],
      },
      {
        text: '新城铺镇',
        value: '新城铺镇',
        children: [
          { text: '东村', value: '东村' },
          { text: '西村', value: '西村' },
        ],
      },
    ],
  },
  {
    text: '藁城区',
    value: '藁城区',
    children: [
      {
        text: '廉州镇',
        value: '廉州镇',
        children: [
          { text: '东村', value: '东村' },
          { text: '西村', value: '西村' },
        ],
      },
    ],
  },
]

/** 扁平快捷项：一键填县+镇+村 */
export const REGION_QUICK = [
  { label: '正定县 · 正定镇 · 东村', countyName: '正定县', townName: '正定镇', villageName: '东村' },
  { label: '正定县 · 正定镇 · 西村', countyName: '正定县', townName: '正定镇', villageName: '西村' },
  { label: '藁城区 · 廉州镇 · 东村', countyName: '藁城区', townName: '廉州镇', villageName: '东村' },
]

/** van-picker / cascader columns（县 / 镇 / 村） */
export function buildRegionColumns() {
  return REGION_TREE.map((county) => ({
    text: county.text,
    value: county.value,
    children: (county.children || []).map((town) => ({
      text: town.text,
      value: town.value,
      children: (town.children || []).map((v) => ({
        text: v.text,
        value: v.value,
      })),
    })),
  }))
}

/** Cascader options（供 RegionFields 等复用） */
export const REGION_CASCADER_OPTIONS = buildRegionColumns()
