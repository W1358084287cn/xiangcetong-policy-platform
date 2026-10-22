<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px" @change="loadList">
          <el-option label="已上架" value="ONLINE" />
          <el-option label="已下架" value="OFFLINE" />
        </el-select>
        <el-button type="primary" @click="openForm()">新增政策</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe empty-text="暂无政策">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="类别" width="100">
          <template #default="{ row }">
            {{ categoryLabel(row.category) }}
          </template>
        </el-table-column>
        <el-table-column label="最低面积" width="100">
          <template #default="{ row }">
            {{ row.minArea != null ? `${row.minArea} 亩` : '不限' }}
          </template>
        </el-table-column>
        <el-table-column prop="standard" label="补贴标准" width="120" show-overflow-tooltip />
        <el-table-column prop="consultPhone" label="咨询电话" width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ONLINE' ? '已上架' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openForm(row)">编辑</el-button>
            <el-button
              v-if="row.status === 'OFFLINE'"
              type="success"
              link
              @click="onStatus(row, 'ONLINE')"
            >
              上架
            </el-button>
            <el-button v-else type="warning" link @click="onStatus(row, 'OFFLINE')">下架</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="loadList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑政策' : '新增政策'"
      width="720px"
      top="4vh"
      destroy-on-close
      class="policy-dialog"
    >
      <el-form :model="form" label-width="120px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input v-model="form.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="申领条件" required>
          <el-input v-model="form.conditions" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="补贴标准" required>
          <el-input v-model="form.standard" />
        </el-form-item>
        <el-form-item label="咨询电话" required>
          <el-input v-model="form.consultPhone" />
        </el-form-item>

        <el-divider content-position="left">匹配条件（基础）</el-divider>

        <el-form-item label="政策类别">
          <el-select v-model="form.category" clearable placeholder="不限/请选择" style="width: 100%">
            <el-option label="粮食种植类" value="GRAIN" />
            <el-option label="畜牧养殖类" value="LIVESTOCK" />
            <el-option label="水产养殖类" value="AQUACULTURE" />
            <el-option label="农机装备类" value="MACHINERY" />
            <el-option label="社保优抚类" value="SOCIAL" />
            <el-option label="综合/其他" value="GENERAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用种养">
          <el-select
            v-model="form.targetPlantTypes"
            multiple
            clearable
            placeholder="不选表示不限种养"
            style="width: 100%"
          >
            <el-option v-for="opt in plantOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="最低面积(亩)">
          <el-input-number
            v-model="form.minArea"
            :min="0"
            :precision="2"
            :controls="false"
            placeholder="空=不限"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="要求绑卡">
          <el-switch v-model="form.requireBankCard" />
        </el-form-item>
        <el-form-item label="适用县">
          <el-input v-model="form.countiesText" placeholder="逗号分隔，空=不限；如：正定县" />
        </el-form-item>
        <el-form-item label="适用镇">
          <el-input v-model="form.townsText" placeholder="逗号分隔，空=不限；如：正定镇" />
        </el-form-item>
        <el-form-item label="适用村">
          <el-input v-model="form.villagesText" placeholder="逗号分隔，空=按镇/县匹配" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-date-picker
            v-model="form.validRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="生效日起"
            end-placeholder="生效日止"
            style="width: 100%"
          />
        </el-form-item>

        <el-divider content-position="left">扩展匹配（精细画像）</el-divider>
        <p class="hint">开关关闭或不填 = 不限；打开后村民须满足该条件才会匹配到本政策。</p>

        <el-form-item label="适用主体">
          <el-select
            v-model="form.targetSubjectTypes"
            multiple
            clearable
            placeholder="不选表示不限主体"
            style="width: 100%"
          >
            <el-option v-for="opt in subjectOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="最低存栏">
          <el-input-number
            v-model="form.minLivestock"
            :min="0"
            :precision="0"
            :controls="false"
            placeholder="空=不限"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="退役军人家庭">
          <el-switch v-model="form.requireVeteran" active-text="必须" inactive-text="不限" />
        </el-form-item>
        <el-form-item label="脱贫监测户">
          <el-switch v-model="form.requireMonitor" active-text="必须" inactive-text="不限" />
        </el-form-item>
        <el-form-item label="粪污设施">
          <el-switch v-model="form.requireManureFacility" active-text="必须" inactive-text="不限" />
        </el-form-item>
        <el-form-item label="购机意向">
          <el-switch v-model="form.requireMachineryIntent" active-text="必须" inactive-text="不限" />
        </el-form-item>
        <el-form-item label="粮食保险">
          <el-switch v-model="form.requireGrainInsurance" active-text="必须" inactive-text="不限" />
        </el-form-item>
        <el-form-item label="土地承包证">
          <el-switch v-model="form.requireLandCert" active-text="必须" inactive-text="不限" />
        </el-form-item>

        <el-divider content-position="left">申报材料 Schema</el-divider>
        <p class="hint">
          JSON 对象；支持 materials[] 分组或扁平 fields[]。留空表示该政策走旧版直提页。
        </p>
        <el-form-item label="materialSchema">
          <el-input
            v-model="form.materialSchemaText"
            type="textarea"
            :rows="8"
            placeholder='例如：{"code":"DEMO","title":"材料清单","materials":[{"code":"id_card","title":"身份证","fields":[{"key":"front","label":"正面","type":"photo","required":true}]}]}'
          />
        </el-form-item>

        <el-form-item v-if="!form.id" label="初始状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="下架（草稿）" value="OFFLINE" />
            <el-option label="直接上架" value="ONLINE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchPolicyList,
  fetchPolicyDetail,
  createPolicy,
  updatePolicy,
  updatePolicyStatus,
} from '@/api/policy'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const query = reactive({ status: '', pageNum: 1, pageSize: 10 })

const plantOptions = [
  { label: '粮食种植', value: 'RICE' },
  { label: '茶叶种植', value: 'TEA' },
  { label: '蔬菜种植', value: 'VEGETABLE' },
  { label: '果树种植', value: 'FRUIT' },
  { label: '畜禽养殖', value: 'LIVESTOCK' },
  { label: '水产养殖', value: 'AQUACULTURE' },
  { label: '农机相关', value: 'MACHINERY' },
  { label: '其他', value: 'OTHER' },
]

const subjectOptions = [
  { label: '普通农户', value: 'FARMER' },
  { label: '家庭农场', value: 'FAMILY_FARM' },
  { label: '农民合作社', value: 'COOP' },
  { label: '农业企业', value: 'ENTERPRISE' },
]

const emptyForm = () => ({
  id: null,
  title: '',
  content: '',
  conditions: '',
  standard: '',
  consultPhone: '',
  status: 'OFFLINE',
  category: '',
  targetPlantTypes: [],
  minArea: undefined,
  requireBankCard: false,
  countiesText: '',
  townsText: '',
  villagesText: '',
  validRange: null,
  targetSubjectTypes: [],
  minLivestock: undefined,
  requireVeteran: false,
  requireMonitor: false,
  requireManureFacility: false,
  requireMachineryIntent: false,
  requireGrainInsurance: false,
  requireLandCert: false,
  materialSchemaText: '',
})

const form = reactive(emptyForm())

function categoryLabel(code) {
  const map = { GRAIN: '粮食种植类', MACHINERY: '农机装备类', LIVESTOCK: '畜牧养殖类', AQUACULTURE: '水产养殖类', SOCIAL: '社保优抚类', GENERAL: '综合' }
  return code ? map[code] || code : '—'
}

/** 逗号文本 → 数组 */
function splitNames(text) {
  if (!text || !String(text).trim()) return []
  return String(text)
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

function joinNames(arr) {
  return Array.isArray(arr) && arr.length ? arr.join('，') : ''
}

/** Schema 对象 → 文本框美化 JSON */
function schemaToText(schema) {
  if (!schema || typeof schema !== 'object') return ''
  try {
    return JSON.stringify(schema, null, 2)
  } catch {
    return ''
  }
}

/** 文本框 → Schema 对象；空=null；非法抛错 */
function parseSchemaText(text) {
  const raw = String(text || '').trim()
  if (!raw) return null
  const obj = JSON.parse(raw)
  if (obj === null || typeof obj !== 'object' || Array.isArray(obj)) {
    throw new Error('materialSchema 须为 JSON 对象')
  }
  return obj
}

/** 加载政策分页列表 */
async function loadList() {
  loading.value = true
  try {
    const data = await fetchPolicyList({
      status: query.status || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    })
    list.value = data?.list || []
    total.value = data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开新增/编辑表单（编辑时拉详情以回显结构化字段） */
async function openForm(row) {
  Object.assign(form, emptyForm())
  if (!row) {
    dialogVisible.value = true
    return
  }
  dialogVisible.value = true
  try {
    const d = await fetchPolicyDetail(row.id)
    const region = d.regionCodes || {}
    Object.assign(form, {
      id: d.id,
      title: d.title || '',
      content: d.content || '',
      conditions: d.conditions || '',
      standard: d.standard || '',
      consultPhone: d.consultPhone || '',
      status: d.status || 'OFFLINE',
      category: d.category || '',
      targetPlantTypes: Array.isArray(d.targetPlantTypes) ? [...d.targetPlantTypes] : [],
      minArea: d.minArea != null ? Number(d.minArea) : undefined,
      requireBankCard: !!d.requireBankCard,
      countiesText: joinNames(region.counties),
      townsText: joinNames(region.towns),
      villagesText: joinNames(region.villages),
      validRange: d.validFrom && d.validTo ? [d.validFrom, d.validTo] : null,
      targetSubjectTypes: Array.isArray(d.targetSubjectTypes) ? [...d.targetSubjectTypes] : [],
      minLivestock: d.minLivestock != null ? Number(d.minLivestock) : undefined,
      requireVeteran: !!d.requireVeteran,
      requireMonitor: !!d.requireMonitor,
      requireManureFacility: !!d.requireManureFacility,
      requireMachineryIntent: !!d.requireMachineryIntent,
      requireGrainInsurance: !!d.requireGrainInsurance,
      requireLandCert: !!d.requireLandCert,
      materialSchemaText: schemaToText(d.materialSchema),
    })
  } catch (e) {
    ElMessage.error(e.message || '加载详情失败')
  }
}

/** 组装保存 payload（全量匹配字段，避免后端清空未提交的 v2 列） */
function buildPayload() {
  const range = form.validRange
  let materialSchema = null
  try {
    materialSchema = parseSchemaText(form.materialSchemaText)
  } catch (e) {
    throw e
  }
  return {
    title: form.title,
    content: form.content,
    conditions: form.conditions,
    standard: form.standard,
    consultPhone: form.consultPhone,
    status: form.status,
    category: form.category || null,
    targetPlantTypes: form.targetPlantTypes?.length ? form.targetPlantTypes : [],
    minArea: form.minArea != null && form.minArea !== '' ? form.minArea : null,
    requireBankCard: !!form.requireBankCard,
    regionCodes: {
      counties: splitNames(form.countiesText),
      towns: splitNames(form.townsText),
      villages: splitNames(form.villagesText),
    },
    validFrom: range?.[0] || null,
    validTo: range?.[1] || null,
    targetSubjectTypes: form.targetSubjectTypes?.length ? form.targetSubjectTypes : [],
    minLivestock: form.minLivestock != null && form.minLivestock !== '' ? form.minLivestock : null,
    requireVeteran: !!form.requireVeteran,
    requireMonitor: !!form.requireMonitor,
    requireManureFacility: !!form.requireManureFacility,
    requireMachineryIntent: !!form.requireMachineryIntent,
    requireGrainInsurance: !!form.requireGrainInsurance,
    requireLandCert: !!form.requireLandCert,
    materialSchema,
  }
}

/** 保存政策 */
async function onSave() {
  if (!form.title || !form.content || !form.conditions || !form.standard || !form.consultPhone) {
    ElMessage.warning('请填写完整必填项')
    return
  }
  let payload
  try {
    payload = buildPayload()
  } catch (e) {
    ElMessage.warning(e.message || 'materialSchema JSON 格式不正确')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updatePolicy(form.id, payload)
    } else {
      await createPolicy(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 上下架 */
async function onStatus(row, status) {
  const tip = status === 'ONLINE' ? '确认上架该政策？' : '确认下架该政策？下架后村民端不可见。'
  try {
    await ElMessageBox.confirm(tip, '提示', { type: 'warning' })
    await updatePolicyStatus(row.id, status)
    ElMessage.success('操作成功')
    loadList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

onMounted(loadList)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.hint {
  margin: -4px 0 12px 120px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>