import { showLoadingToast, showToast, closeToast } from 'vant'
import { fetchApplyDetail, saveDraft } from '@/api/apply'
import { fetchPolicyDetail } from '@/api/policy'

/**
 * 解析申报路径：统一走材料分步页（旧直提页已下线；
 * 政策未配 materialSchema 时材料页显示空态兜底）。
 * @param {number|string} policyId
 * @returns {Promise<{ path: string, query: { policyId: string } }>}
 */
export async function resolveApplyRoute(policyId) {
  return { path: '/apply/materials', query: { policyId: String(policyId) } }
}

/**
 * 跳转申报（统一入口）。matchLevel=NONE 时拦截。
 * @param {import('vue-router').Router} router
 * @param {{ id?: number|string, policyId?: number|string, matchLevel?: string, materialSchema?: object|null }} policy
 */
export async function navigateToApply(router, policy) {
  const policyId = policy?.id ?? policy?.policyId
  if (!policyId) {
    showToast('请先选择要申报的政策')
    return false
  }
  if (policy?.matchLevel === 'NONE') {
    showToast('当前条件暂不符合，无法申报')
    return false
  }

  showLoadingToast({ message: '准备申报…', forbidClick: true, duration: 0 })
  try {
    const route = await resolveApplyRoute(policyId, policy)
    await router.push(route)
    return true
  } catch {
    showToast('无法打开申报页，请稍后重试')
    return false
  } finally {
    closeToast()
  }
}

/**
 * 已驳回再申请：读取旧材料写入新草稿（或更新同政策草稿），再进材料页。
 * 同政策若村/镇审中或已通过，后端会拒绝。
 * @param {import('vue-router').Router} router
 * @param {number|string} applyId 被驳回的申报 id
 */
export async function reapplyFromRejected(router, applyId) {
  if (!applyId) {
    showToast('申报记录无效')
    return false
  }
  showLoadingToast({ message: '准备重新申报…', forbidClick: true, duration: 0 })
  try {
    const old = await fetchApplyDetail(applyId)
    if (!old?.policyId) {
      showToast('无法读取原申报政策')
      return false
    }
    if (old.status !== 'REJECTED') {
      showToast('仅已驳回的申报可重新申请')
      return false
    }
    // 检查政策是否仍然存在且上架
    try {
      await fetchPolicyDetail(old.policyId)
    } catch {
      showToast('该政策已失效或不存在，无法重新申报')
      return false
    }
    // 预填旧材料；后端同一政策仅一份 DRAFT，会 upsert
    const materialData =
      old.materialData && typeof old.materialData === 'object' ? old.materialData : {}
    await saveDraft({
      policyId: old.policyId,
      materialData,
    })
    const route = await resolveApplyRoute(old.policyId)
    await router.push(route)
    return true
  } catch (e) {
    // 拦截器已对 API 业务/网络错误弹出 Toast，此处仅做兜底
    const isApiError = e && typeof e === 'object' && 'code' in e && e.code !== 0
    if (!isApiError) {
      showToast('重新申报失败，请稍后重试')
    }
    return false
  } finally {
    closeToast()
  }
}