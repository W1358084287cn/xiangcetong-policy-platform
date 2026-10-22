import { fetchProfile } from '@/api/villager'
import { isLiveGroupDone } from '@/constants/plantType'

/**
 * 向导七步：地区 → 主体 → 身份 → 种养 → 规模 → 设施意向 → 银行卡
 * 用于首页进度卡与「立即匹配」门禁。
 */
export const PROFILE_STEPS = [
  { key: 'region', label: '所在地区', fields: ['countyName', 'townName', 'villageName'] },
  { key: 'subject', label: '经营主体', fields: ['subjectType', 'laborCount', 'farmYears'] },
  { key: 'identity', label: '身份情况', fields: ['isVeteranFamily', 'isMonitorHousehold'] },
  { key: 'plant', label: '种养类型', fields: ['plantType'] },
  { key: 'scale', label: '经营规模', fields: ['plantArea', 'irrigatedArea', 'hasLandCert'] },
  {
    key: 'facility',
    label: '设施意向',
    fields: ['needMachinery', 'hasGrainInsurance', 'hasManureFacility'],
  },
  { key: 'bank', label: '银行卡', fields: ['bankCard', 'bankName'] },
]

/** 判断字段是否为空（null/空串）；0 视为已填 */
function blank(v) {
  return v == null || String(v).trim() === ''
}

/** 0/1 是否类字段是否已明确选择 */
function ynDone(v) {
  return v === 0 || v === 1 || v === '0' || v === '1'
}

/** 某一步是否已填齐 */
export function isStepDone(profile, stepKey) {
  const step = PROFILE_STEPS.find((s) => s.key === stepKey)
  if (!step || !profile) return false

  if (stepKey === 'subject') {
    return (
      !blank(profile.subjectType) &&
      !blank(profile.laborCount) &&
      Number(profile.laborCount) >= 0 &&
      !blank(profile.farmYears) &&
      Number(profile.farmYears) >= 0
    )
  }
  if (stepKey === 'identity') {
    return ynDone(profile.isVeteranFamily) && ynDone(profile.isMonitorHousehold)
  }
  if (stepKey === 'scale') {
    const a = profile.plantArea
    const areaOk = a !== '' && a != null && !Number.isNaN(Number(a)) && Number(a) >= 0
    const irr = profile.irrigatedArea
    const irrOk = irr !== '' && irr != null && !Number.isNaN(Number(irr)) && Number(irr) >= 0
    const landOk = ynDone(profile.hasLandCert)
    if (!areaOk || !irrOk || !landOk) return false
    // 养殖类须补存栏细分
    if (isLiveGroupDone(profile.plantType)) {
      return (
        !blank(profile.livestockType) &&
        !blank(profile.livestockCount) &&
        Number(profile.livestockCount) >= 0
      )
    }
    return true
  }
  if (stepKey === 'facility') {
    const base =
      ynDone(profile.needMachinery) &&
      ynDone(profile.hasGrainInsurance) &&
      ynDone(profile.hasManureFacility)
    if (!base) return false
    // 有购机意向时须选农机类型
    if (Number(profile.needMachinery) === 1) {
      return !blank(profile.machineryType)
    }
    return true
  }
  if (stepKey === 'bank') {
    return !blank(profile.bankCard) && !blank(profile.bankName)
  }
  return step.fields.every((f) => !blank(profile[f]))
}

/**
 * 返回未完成的步骤 key 列表（按向导顺序）
 * @param {object|null} profile
 * @returns {string[]}
 */
export function missingProfileFields(profile) {
  if (!profile) return PROFILE_STEPS.map((s) => s.key)
  return PROFILE_STEPS.filter((s) => !isStepDone(profile, s.key)).map((s) => s.key)
}

/** 七步是否全部就绪（前端匹配 CTA 门禁） */
export function isProfileReady(profile) {
  return missingProfileFields(profile).length === 0
}

/** 完成度：已完成步数 / 总步数 */
export function profileProgress(profile) {
  const total = PROFILE_STEPS.length
  const done = PROFILE_STEPS.filter((s) => isStepDone(profile, s.key)).length
  return { done, total, percent: total ? Math.round((done / total) * 100) : 0 }
}

/**
 * 拉取资料并校验；失败时当作未齐
 */
export async function fetchAndCheckProfile() {
  try {
    const profile = await fetchProfile()
    const missing = missingProfileFields(profile)
    return {
      ready: missing.length === 0,
      profile,
      missing,
      progress: profileProgress(profile),
    }
  } catch {
    return {
      ready: false,
      profile: null,
      missing: PROFILE_STEPS.map((s) => s.key),
      progress: { done: 0, total: PROFILE_STEPS.length, percent: 0 },
    }
  }
}

/** 缺项中文提示 */
export function missingLabel(missingKeys) {
  const map = Object.fromEntries(PROFILE_STEPS.map((s) => [s.key, s.label]))
  if (!missingKeys?.length) return ''
  return missingKeys.map((k) => map[k] || k).join('、')
}
