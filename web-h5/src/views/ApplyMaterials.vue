<!--
  申报材料页：按政策材料 Schema 逐项填写。
  顶部政策卡 + 完成进度条；每项材料一张卡（步骤序号 + 状态标签 + 填写/预览）；
  底部固定「保存草稿 / 提交申报」双按钮。
-->
<template>
  <div class="page">
    <van-nav-bar
      title="申报材料"
      left-text="返回"
      left-arrow
      fixed
      placeholder
      class="mat-nav"
      @click-left="goBack"
    />

    <div v-if="loading" class="state-box">
      <van-loading size="28px">加载中...</van-loading>
    </div>

    <template v-else-if="policy">
      <!-- 政策卡：标题 + 填写说明 + 完成进度条 -->
      <div class="hero">
        <h2>{{ policy.title }}</h2>
        <p>{{ schema?.desc || '请按清单逐项填写，可先保存草稿' }}</p>
        <div class="progress-row">
          <span>完成进度 {{ doneCount }}/{{ materials.length }}</span>
          <van-progress
            :percentage="progressPct"
            stroke-width="8"
            color="#d97706"
            track-color="rgba(254, 243, 199, 0.6)"
          />
        </div>
      </div>

      <!-- 填写引导条：告诉老人从上到下逐项来 -->
      <p class="guide-tip">
        <van-icon name="info-o" />
        共 {{ materials.length }} 项材料，从上往下逐项点「填写」即可
      </p>

      <div class="mat-list">
        <div
          v-for="(mat, idx) in materials"
          :key="mat.code"
          class="mat-card"
          :class="{ done: isMatDone(mat) }"
          @click="openFill(mat)"
        >
          <div class="mat-hd">
            <span class="step-no" aria-hidden="true">{{ idx + 1 }}</span>
            <strong>{{ mat.title || mat.docName }}</strong>
            <van-tag :type="isMatDone(mat) ? 'success' : 'warning'" size="medium">
              {{ isMatDone(mat) ? '已填写' : '待填写' }}
            </van-tag>
          </div>
          <p class="mat-desc">{{ mat.desc || mat.docName || '点击填写' }}</p>
          <div class="mat-actions">
            <van-button type="primary" round class="mat-btn" @click.stop="openFill(mat)">
              填写
            </van-button>
            <van-button
              plain
              round
              class="mat-btn"
              :disabled="!isMatDone(mat)"
              @click.stop="openPreview(mat)"
            >
              预览
            </van-button>
          </div>
        </div>
      </div>

      <div class="footer">
        <van-button round block plain :loading="saving" class="sub-btn" @click="onSaveDraft">
          保存草稿
        </van-button>
        <van-button
          round
          block
          type="primary"
          :loading="submitting"
          class="main-btn"
          :disabled="doneCount < materials.length"
          @click="onSubmit"
        >
          提交申报
        </van-button>
      </div>
    </template>

    <van-empty v-else description="政策不存在或未配置材料" />

    <!-- 填写弹层（teleport 到 body：避免动画容器劫持 fixed 百分比高度导致内容被截断） -->
    <van-popup
      v-model:show="showFill"
      teleport="body"
      position="bottom"
      round
      :style="{ height: '85%' }"
      closeable
    >
      <div v-if="activeMat" class="fill-panel">
        <h3>{{ activeMat.title }}</h3>
        <p class="tip">{{ activeMat.desc }}</p>
        <div v-for="field in activeMat.fields || []" :key="field.key" class="field-block">
          <p class="field-label">
            {{ field.label }}<span v-if="field.required !== false" class="req">*</span>
          </p>
          <template v-if="field.type === 'photo'">
            <van-uploader
              :model-value="photoList(activeMat.code, field.key)"
              :max-count="1"
              :max-size="5 * 1024 * 1024"
              accept="image/jpeg,image/png,image/jpg"
              capture="environment"
              :after-read="(file) => afterPhoto(activeMat.code, field.key, file)"
              @delete="() => setField(activeMat.code, field.key, '')"
              @oversize="() => showToast('单张不超过 5MB')"
            />
          </template>
          <van-field
            v-else
            :model-value="getField(activeMat.code, field.key)"
            :placeholder="'请输入' + field.label"
            @update:model-value="(v) => setField(activeMat.code, field.key, v)"
          />
        </div>
        <van-button type="primary" block round class="main-btn" @click="closeFill">
          完成填写
        </van-button>
      </div>
    </van-popup>

    <!-- 预览弹层 -->
    <van-popup v-model:show="showPreview" teleport="body" position="bottom" round :style="{ height: '70%' }" closeable>
      <div v-if="previewMat" class="fill-panel">
        <h3>预览 · {{ previewMat.title }}</h3>
        <div v-for="field in previewMat.fields || []" :key="field.key" class="preview-row">
          <span class="field-label">{{ field.label }}</span>
          <img
            v-if="field.type === 'photo' && getField(previewMat.code, field.key)"
            :src="absUrl(getField(previewMat.code, field.key))"
            class="preview-img"
            alt=""
          />
          <p v-else>{{ getField(previewMat.code, field.key) || '—' }}</p>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showLoadingToast, showSuccessToast, showToast } from 'vant'
import { fetchPolicyDetail } from '@/api/policy'
import { fetchDraft, saveDraft, submitDraft } from '@/api/apply'
import { uploadFile } from '@/api/file'
import { compressImage } from '@/utils/imageCompress'
import { safeBack } from '@/utils/navBack'

const route = useRoute()
const router = useRouter()

/** 有历史则 back；否则回政策详情或匹配列表 */
function goBack() {
  const pid = route.query.policyId
  safeBack(router, pid ? `/policy/${pid}` : '/policies')
}

const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const policy = ref(null)
const draftId = ref(null)
const materialData = reactive({})
const prefill = ref({})
const showFill = ref(false)
const showPreview = ref(false)
const activeMat = ref(null)
const previewMat = ref(null)

const schema = computed(() => policy.value?.materialSchema || null)

/** 统一为 materials[]；兼容扁平 fields[] */
const materials = computed(() => {
  const s = schema.value
  if (!s) return []
  if (Array.isArray(s.materials) && s.materials.length) return s.materials
  if (Array.isArray(s.fields) && s.fields.length) {
    return [
      {
        code: '_root',
        title: s.title || '申报材料',
        docName: s.docName,
        desc: s.desc,
        fields: s.fields,
      },
    ]
  }
  return []
})

const doneCount = computed(() => materials.value.filter((m) => isMatDone(m)).length)
const progressPct = computed(() => {
  const n = materials.value.length
  if (!n) return 0
  return Math.round((doneCount.value / n) * 100)
})

/** 取出某材料块的对象；没有就建空对象，避免写字段时报错 */
function ensureBlock(code) {
  if (code === '_root') return materialData
  if (!materialData[code] || typeof materialData[code] !== 'object') {
    materialData[code] = {}
  }
  return materialData[code]
}

/** 读某一材料字段的当前值（扁平 schema 用 _root） */
function getField(code, key) {
  if (code === '_root') return materialData[key] || ''
  return materialData[code]?.[key] || ''
}

/** 写入某一材料字段；嵌套材料会先 ensureBlock */
function setField(code, key, val) {
  if (code === '_root') {
    materialData[key] = val
    return
  }
  ensureBlock(code)[key] = val
}

/** 判断单个字段是否已填（非空字符串） */
function isFieldFilled(mat, field) {
  const v = getField(mat.code, field.key)
  return v != null && String(v).trim() !== ''
}

/** 一组材料是否齐：必填字段都有值才算完成 */
function isMatDone(mat) {
  const fields = mat.fields || []
  if (!fields.length) return false
  return fields.every((f) => {
    if (f.required === false) return true
    return isFieldFilled(mat, f)
  })
}

/** 把已存的图片 URL 转成 Vant Uploader 需要的文件列表 */
function photoList(code, key) {
  const url = getField(code, key)
  if (!url) return []
  return [{ url: absUrl(url), isImage: true }]
}

/** 相对路径保持原样交给代理；绝对/blob 直接用 */
function absUrl(url) {
  if (!url) return ''
  if (url.startsWith('http') || url.startsWith('blob:')) return url
  return url
}

/**
 * schema 字段 key（snake_case）→ 后端 buildPrefill 返回 key（camelCase）自动映射。
 * 优先使用 schema 字段上的 prefill 属性；未配置时走此表自动推断。
 */
const AUTO_PREFILL_KEY_MAP = {
  name: 'name',
  id_number: 'idNumber',
  id_front: 'idFront',
  id_back: 'idBack',
  phone: 'phone',
  bank_card: 'bankCard',
  bank_card_number: 'bankCard',
  bank_name: 'bankName',
  county: 'countyName',
  county_name: 'countyName',
  town: 'townName',
  town_name: 'townName',
  village: 'villageName',
  village_name: 'villageName',
  plant_type: 'plantType',
  plant_area: 'plantArea',
  subject_type: 'subjectType',
  labor_count: 'laborCount',
  labor: 'laborCount',
  farm_years: 'farmYears',
  is_veteran: 'isVeteranFamily',
  is_veteran_family: 'isVeteranFamily',
  veteran: 'isVeteranFamily',
  is_monitor: 'isMonitorHousehold',
  is_monitor_household: 'isMonitorHousehold',
  monitor: 'isMonitorHousehold',
  secondary_plant: 'secondaryPlantType',
  secondary_plant_type: 'secondaryPlantType',
  irrigated_area: 'irrigatedArea',
  land_cert: 'hasLandCert',
  has_land_cert: 'hasLandCert',
  livestock_type: 'livestockType',
  livestock_count: 'livestockCount',
  need_machinery: 'needMachinery',
  machinery_type: 'machineryType',
  grain_insurance: 'hasGrainInsurance',
  has_grain_insurance: 'hasGrainInsurance',
  manure_facility: 'hasManureFacility',
  has_manure_facility: 'hasManureFacility',
}

/** 打开填写时：按 schema.prefill（优先）或字段 key 自动推断 从资料建议值填空，已填过的不覆盖 */
function applyPrefill(mat) {
  const pf = prefill.value || {}
  for (const field of mat.fields || []) {
    if (isFieldFilled(mat, field)) continue
    const src = field.prefill || AUTO_PREFILL_KEY_MAP[field.key]
    if (!src) continue
    let val = pf[src]
    if (src === 'bankCard' && typeof val === 'string' && val.includes('*')) continue
    if (val != null && String(val).trim() !== '') {
      setField(mat.code, field.key, String(val))
    }
  }
}

/** 打开底部填写弹层，并尝试预填 */
function openFill(mat) {
  activeMat.value = mat
  applyPrefill(mat)
  showFill.value = true
}

/** 关闭填写弹层（数据已写在 materialData 里） */
function closeFill() {
  showFill.value = false
}

/** 打开只读预览弹层 */
function openPreview(mat) {
  previewMat.value = mat
  showPreview.value = true
}

/** 拍照/选图后先本地压缩，再上传服务器，成功则把 URL 写入对应字段 */
async function afterPhoto(code, key, file) {
  const item = Array.isArray(file) ? file[0] : file
  item.status = 'uploading'
  let toast
  try {
    // 本地压缩：手机原图动辄数 MB，先缩到长边 1600px 再传，显著提速
    const compressed = await compressImage(item.file)
    toast = showLoadingToast({ message: '上传中 0%', forbidClick: true, duration: 0 })
    const res = await uploadFile(compressed, (p) => {
      if (toast) toast.message = `上传中 ${p}%`
    })
    const url = res?.url || res
    setField(code, key, url)
    item.url = absUrl(url)
    item.status = 'done'
  } catch {
    item.status = 'failed'
    showToast('上传失败')
  } finally {
    if (toast) toast.close()
  }
}

/** 深拷贝当前材料数据，避免提交时被界面后续改动影响 */
function snapshotData() {
  return JSON.parse(JSON.stringify(materialData))
}

/** 点「保存草稿」：把当前填写内容 upsert 到后端 DRAFT */
async function onSaveDraft() {
  const policyId = Number(route.query.policyId)
  if (!policyId) return
  saving.value = true
  try {
    const data = await saveDraft({ policyId, materialData: snapshotData() })
    draftId.value = data?.id || draftId.value
    showSuccessToast('草稿已保存')
  } catch {
    // toast 已处理
  } finally {
    saving.value = false
  }
}

/** 点「提交申报」：二次确认后先存草稿再 submit-draft，成功后回「我的」看进度 */
async function onSubmit() {
  if (doneCount.value < materials.value.length) {
    showToast('请先完成全部材料')
    return
  }
  try {
    await showConfirmDialog({
      title: '确认提交',
      message: '材料将送给村里审核，提交后不能再改。确定提交吗？',
      confirmButtonText: '确认提交',
    })
  } catch {
    return
  }
  const policyId = Number(route.query.policyId)
  submitting.value = true
  try {
    let id = draftId.value
    if (!id) {
      const data = await saveDraft({ policyId, materialData: snapshotData() })
      id = data?.id
      draftId.value = id
    } else {
      await saveDraft({ policyId, materialData: snapshotData() })
    }
    await submitDraft(id, { materialData: snapshotData() })
    showSuccessToast('已提交审核')
    router.replace('/mine')
  } catch {
    // toast 已处理
  } finally {
    submitting.value = false
  }
}

/** 用接口返回的草稿/预填把本地表单灌满 */
function hydrateFromDraft(data) {
  if (!data) return
  if (data.id) draftId.value = data.id
  if (data.suggestedPrefill) prefill.value = data.suggestedPrefill
  const md = data.materialData
  if (md && typeof md === 'object') {
    Object.keys(materialData).forEach((k) => delete materialData[k])
    Object.assign(materialData, md)
  }
}

/** 进页：拉政策详情 + 草稿（含预填建议） */
async function load() {
  const policyId = Number(route.query.policyId)
  if (!policyId) {
    showToast('缺少政策')
    return
  }
  loading.value = true
  try {
    const [p, d] = await Promise.all([
      fetchPolicyDetail(policyId),
      fetchDraft(policyId).catch(() => null),
    ])
    policy.value = p
    hydrateFromDraft(d)
  } catch {
    policy.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
/* ==========================================================================
   Impeccable UI: Golden Wheat & Prairie Theme (Apply Materials)
   ========================================================================== */
@keyframes slideFadeUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.page {
  /* 底部双按钮留空，避免误触 */
  padding-bottom: 140px;
  min-height: 100vh;
}
/* 顶栏压过内容区，避免叠层抢点击；「返回」文字加大热区 */
.mat-nav {
  z-index: 100;
}
.mat-nav :deep(.van-nav-bar__left) {
  min-width: 72px;
  padding-right: 8px;
}
.state-box {
  padding: 60px 0;
  text-align: center;
}
/* 顶部政策卡：金色麦田渐变 + 毛玻璃 */
.hero {
  margin: 16px 16px;
  background: linear-gradient(135deg, rgba(254, 243, 199, 0.8) 0%, rgba(255, 255, 255, 0.6) 100%);
  border: 1px solid rgba(217, 119, 6, 0.2);
  border-radius: var(--xct-radius, 20px);
  padding: 24px 20px;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 12px 32px rgba(217, 119, 6, 0.08);
  animation: slideFadeUp 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
}
.hero h2 {
  margin: 0 0 12px;
  font-size: var(--xct-fs-title, 1.25rem);
  color: #78350f;
  font-weight: 800;
}
.hero p {
  margin: 0 0 16px;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: #92400e;
  line-height: 1.6;
  font-weight: 500;
}
.progress-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: var(--xct-fs-body, 1rem);
  color: #451a03;
  font-weight: 700;
}
.progress-row :deep(.van-progress) {
  background: rgba(255, 255, 255, 0.5);
}
/* 填写引导条：浅橙色 */
.guide-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 16px 16px;
  padding: 12px 16px;
  border-radius: var(--xct-radius-sm, 12px);
  background: rgba(255, 237, 213, 0.7);
  color: #ea580c;
  font-size: var(--xct-fs-tip, 0.9375rem);
  font-weight: 600;
  border: 1px solid rgba(234, 88, 12, 0.2);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  animation: slideFadeUp 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
  animation-delay: 0.1s;
}
.mat-list {
  padding: 0 16px;
}
/* 材料卡：毛玻璃底板 */
.mat-card {
  background: var(--xct-card-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: var(--xct-card-border);
  border-radius: var(--xct-radius, 20px);
  padding: 20px;
  margin-bottom: 16px;
  box-shadow: var(--xct-card-shadow);
  animation: slideFadeUp 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
  transition: transform 0.2s, border-color 0.3s;
}

.mat-card:nth-child(1) { animation-delay: 0.15s; }
.mat-card:nth-child(2) { animation-delay: 0.2s; }
.mat-card:nth-child(3) { animation-delay: 0.25s; }
.mat-card:nth-child(4) { animation-delay: 0.3s; }
.mat-card:nth-child(n+5) { animation-delay: 0.35s; }

.mat-card.done {
  border-color: rgba(101, 163, 13, 0.4);
  background: linear-gradient(160deg, rgba(240, 253, 244, 0.8), rgba(255, 255, 255, 0.6) 80%);
}
.mat-card:active {
  transform: scale(0.98);
}

.mat-hd {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}
.step-no {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(254, 243, 199, 0.8);
  color: #d97706;
  font-size: var(--xct-fs-body, 1rem);
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.mat-card.done .step-no {
  background: rgba(101, 163, 13, 0.15);
  color: #4d7c0f;
}
.mat-hd strong {
  flex: 1;
  min-width: 0;
  font-size: var(--xct-fs-body, 1rem);
  color: #451a03;
  font-weight: 800;
}
.mat-desc {
  margin: 10px 0 16px;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: #92400e;
  line-height: 1.6;
  font-weight: 500;
}
.mat-actions {
  display: flex;
  gap: 16px;
}
.mat-btn {
  flex: 1;
  height: var(--xct-hit-min, 52px) !important;
  font-size: var(--xct-fs-body, 1rem) !important;
  font-weight: 700 !important;
  border-radius: 12px !important;
}
.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  gap: 12px;
  padding: 16px 20px calc(16px + env(safe-area-inset-bottom));
  background: rgba(255, 253, 245, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 -4px 24px rgba(217, 119, 6, 0.05);
  z-index: 20;
}
.main-btn,
.sub-btn {
  height: var(--xct-hit-min, 52px) !important;
  font-size: var(--xct-fs-body, 1.05rem) !important;
  font-weight: 700 !important;
}
.main-btn {
  box-shadow: 0 4px 16px rgba(217, 119, 6, 0.2);
}

/* 填写弹层输入框优化 */
.fill-panel {
  padding: 24px 20px 40px;
}
.fill-panel h3 {
  margin: 0 0 12px;
  font-size: var(--xct-fs-title, 1.25rem);
  color: #78350f;
  font-weight: 800;
}
.tip {
  margin: 0 0 20px;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: #92400e;
  line-height: 1.6;
  font-weight: 500;
}
.field-block {
  margin-bottom: 24px;
}
.field-label {
  margin: 0 0 10px;
  font-size: var(--xct-fs-body, 1rem);
  color: #451a03;
  font-weight: 800;
}
.req {
  color: #ea580c;
  margin-left: 4px;
}
.field-block :deep(.van-field) {
  background: rgba(254, 243, 199, 0.3);
  border: 1px solid rgba(217, 119, 6, 0.15);
  border-radius: 12px;
  padding: 12px 16px;
}
.field-block :deep(.van-field__control) {
  color: #451a03;
  font-weight: 600;
}
.preview-row {
  margin-bottom: 20px;
}
.preview-img {
  display: block;
  width: 100%;
  max-height: 240px;
  object-fit: contain;
  border-radius: 12px;
  background: rgba(254, 243, 199, 0.3);
  border: 1px solid rgba(217, 119, 6, 0.15);
}
</style>