<template>
  <div class="page">
    <!-- 统计卡片：村管 / 镇管显示，系统管理端不显示 -->
    <div v-if="!sysAdmin" class="stat-cards">
      <el-card shadow="never" class="stat-card">
        <div class="stat-num">{{ stats.pendingCount }}</div>
        <div class="stat-label">待审批</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-num">{{ stats.todayCount }}</div>
        <div class="stat-label">今日审批</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-num">{{ stats.monthCount }}</div>
        <div class="stat-label">本月审批</div>
      </el-card>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <!-- 村管 / 镇管：分类导航（替代原状态下拉），按 tab 参数拉取 -->
        <el-tabs
          v-if="!sysAdmin"
          v-model="activeTab"
          class="tabs"
          @tab-change="onTabChange"
        >
          <el-tab-pane
            v-for="t in tabs"
            :key="t.name"
            :label="t.label"
            :name="t.name"
          />
        </el-tabs>

        <!-- 系统管理端：保留原状态下拉全量筛选 -->
        <template v-if="sysAdmin">
          <el-select
            v-model="query.status"
            clearable
            placeholder="全部状态"
            style="width: 180px"
            @change="onSearch"
          >
            <el-option
              v-for="(label, key) in APPLY_STATUS_MAP"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
          <el-input
            v-model="query.villageCode"
            clearable
            placeholder="村编码筛选"
            style="width: 160px"
            @keyup.enter="onSearch"
          />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </template>
      </div>

      <!-- 加载 / 空 / 表格：点击整行即打开详情操作，无需精确点「详情」按钮 -->
      <el-table
        v-loading="loading"
        :data="list"
        stripe
        empty-text="暂无申请数据（点击整行可查看详情并操作）"
        :row-style="{ cursor: 'pointer' }"
        @row-click="(row) => openDetail(row)"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="applicantName" label="申请人" width="100" />
        <el-table-column prop="applicantPhone" label="手机号" width="120" />
        <el-table-column prop="policyTitle" label="政策" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ formatApplyStatus(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发放金额" width="120">
          <template #default="{ row }">
            <span v-if="row.status === 'DISBURSED'" class="disburse-val">¥{{ formatAmount(row.disburseAmount) }}</span>
            <span v-else-if="row.disburseAmount != null" class="disburse-proposed">拟定 ¥{{ formatAmount(row.disburseAmount) }}</span>
            <span v-else class="dash">—</span>
          </template>
        </el-table-column>
        <el-table-column label="发放时间" width="170">
          <template #default="{ row }">
            <span v-if="row.disburseAt">{{ row.disburseAt }}</span>
            <span v-else class="dash">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="villageCode" label="村" width="100" />
        <el-table-column prop="townCode" label="镇" width="100" />
        <el-table-column prop="createdAt" label="申报时间" width="170" />
        <!-- 操作列：按角色/状态显示可用操作按钮 -->
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canVillageAuditRow(row)"
              type="success"
              size="small"
              link
              @click.stop="openDetail(row)"
            >初审</el-button>
            <el-button
              v-else-if="canTownAuditRow(row)"
              type="warning"
              size="small"
              link
              @click.stop="openDetail(row)"
            >终审</el-button>
            <el-button
              v-else-if="canDisburseRow(row)"
              type="warning"
              size="small"
              link
              @click.stop="openDetail(row)"
            >发放</el-button>
            <el-button
              v-else
              type="primary"
              size="small"
              link
              @click.stop="openDetail(row)"
            >查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 详情抽屉：含审批（按角色）与镇管发放 -->
    <el-drawer v-model="drawerVisible" title="申请详情" size="480px" destroy-on-close>
      <template v-if="current">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="申请人">{{ current.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ current.applicantPhone }}</el-descriptions-item>
          <el-descriptions-item label="政策">{{ current.policyTitle }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ formatApplyStatus(current.status) }}
          </el-descriptions-item>
          <el-descriptions-item label="村 / 镇">
            {{ current.villageCode }} / {{ current.townCode }}
          </el-descriptions-item>
          <el-descriptions-item label="申报时间">{{ current.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="发放金额">
            {{ current.disburseAmount ? `¥${formatAmount(current.disburseAmount)}` : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="发放时间">
            {{ current.disburseAt || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="初审意见">
            {{ current.firstOpinion || '—' }}
            <span v-if="current.firstAt" class="muted">（{{ current.firstAt }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="终审意见">
            {{ current.finalOpinion || '—' }}
            <span v-if="current.finalAt" class="muted">（{{ current.finalAt }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="材料图片">
            <div v-if="materialList.length" class="materials">
              <el-image
                v-for="(url, i) in materialList"
                :key="i"
                :src="url"
                :preview-src-list="materialList"
                :initial-index="i"
                fit="cover"
                preview-teleported
                class="thumb"
              />
            </div>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="materialDataRows.length" label="结构化材料">
            <div class="mat-data">
              <div v-for="(row, i) in materialDataRows" :key="i" class="mat-data-row">
                <span class="mat-key">{{ row.label }}</span>
                <!-- 照片字段直接显示缩略图，点击放大预览 -->
                <el-image
                  v-if="row.isImg"
                  :src="row.value"
                  :preview-src-list="[row.value]"
                  fit="cover"
                  preview-teleported
                  class="thumb"
                />
                <a
                  v-else-if="row.isUrl"
                  :href="row.value"
                  target="_blank"
                  rel="noopener"
                  class="mat-val"
                >{{ row.value }}</a>
                <span v-else class="mat-val">{{ row.value }}</span>
              </div>
            </div>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 镇管：待发放时显示发放区 -->
        <div v-if="canDisburse" class="audit-box">
          <div class="disburse-title">发放补贴</div>
          <el-input-number
            v-model="disburseAmount"
            :min="0.01"
            :precision="2"
            :step="100"
            :controls="false"
            placeholder="请输入发放金额"
            style="width: 100%"
          />
          <el-date-picker
            v-model="disburseAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="发放时间（选填）"
            :disabled-date="disabledDisburseDate"
            style="width: 100%; margin-top: 12px"
          />
          <div class="audit-btns">
            <el-button type="warning" size="large" :loading="disbursing" @click="doDisburse">
              确认发放
            </el-button>
          </div>
        </div>

        <!-- 村管：初审按钮 + 拟定发放金额/时间 -->
        <div v-if="canVillageAudit" class="audit-box">
          <div class="disburse-title">拟定发放信息（必填，镇管可见可改）</div>
          <el-input-number
            v-model="villageDisburseAmount"
            :min="0.01"
            :precision="2"
            :step="100"
            :controls="false"
            placeholder="发放金额（元）"
            style="width: 100%"
          />
          <el-date-picker
            v-model="villageDisburseAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="发放时间"
            :disabled-date="disabledDisburseDate"
            style="width: 100%; margin-top: 12px"
          />
          <div class="audit-label" style="margin-top: 12px">审批意见 <span class="req">*</span></div>
          <el-input
            v-model="opinion"
            type="textarea"
            :rows="3"
            placeholder="请填写审批意见（必填）"
          />
          <div class="audit-btns">
            <el-button type="success" size="large" :loading="auditing" @click="doAudit('PASS')">
              初审通过
            </el-button>
            <el-button type="danger" size="large" :loading="auditing" @click="doAudit('REJECT')">
              初审驳回
            </el-button>
          </div>
        </div>

        <!-- 镇管：仅终审按钮 -->
        <div v-if="canTownAudit" class="audit-box">
          <div class="audit-label">审批意见 <span class="req">*</span></div>
          <el-input
            v-model="opinion"
            type="textarea"
            :rows="3"
            placeholder="请填写审批意见（必填）"
          />
          <div class="audit-btns">
            <el-button type="success" size="large" :loading="auditing" @click="doAudit('PASS')">
              终审通过
            </el-button>
            <el-button type="danger" size="large" :loading="auditing" @click="doAudit('REJECT')">
              终审驳回
            </el-button>
          </div>
        </div>

        <!-- SYS_ADMIN 只读提示 -->
        <el-alert
          v-if="sysAdmin"
          type="info"
          :closable="false"
          show-icon
          title="系统管理员仅可查看，不可审批"
          style="margin-top: 16px"
        />
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
/**
 * 申请审批列表重构：
 * 1. 村管/镇管用分类导航（el-tabs）替代原状态下拉，切换传 tab 参数，修复「审批后单据消失」的 bug。
 * 2. 顶部统计卡片（今日审批/本月审批/待审批），村管/镇管显示。
 * 3. 镇管对待发放单据提供发放操作（金额输入 + 二次确认）。
 * 4. 状态映射与发放金额/时间展示扩展。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchApplyList, fetchApplyStats, villageAudit, townAudit, disburse } from '@/api/apply'
import { isSysAdmin, isVillageAdmin, isTownAdmin } from '@/utils/auth'
import { APPLY_STATUS_MAP, formatApplyStatus } from '@/utils/status'

const loading = ref(false)
const auditing = ref(false)
const disbursing = ref(false)
const list = ref([])
const total = ref(0)
const drawerVisible = ref(false)
const current = ref(null)
const opinion = ref('')
const disburseAmount = ref(null)
/** 镇管发放时间（选填，村管拟定值预填） */
const disburseAt = ref(null)
/** 村管初审时输入的拟定金额和时间 */
const villageDisburseAmount = ref(null)
const villageDisburseAt = ref(null)

const sysAdmin = computed(() => isSysAdmin())
const villageAdmin = computed(() => isVillageAdmin())
const townAdmin = computed(() => isTownAdmin())

/** 统计卡片数据 */
const stats = reactive({ pendingCount: 0, todayCount: 0, monthCount: 0 })

/** 村管分类导航：审核中=村审已过、镇审中；已通过=镇审及之后（待发放/已发放/历史已通过） */
const villageTabs = [
  { name: 'pending', label: '待审批' },
  { name: 'reviewing', label: '审核中' },
  { name: 'approved', label: '已通过' },
  { name: 'rejected', label: '已驳回' },
]

/** 镇管分类导航 */
const townTabs = [
  { name: 'pending', label: '待审批' },
  { name: 'disbursing', label: '待发放' },
  { name: 'disbursed', label: '已发放' },
  { name: 'rejected', label: '已驳回' },
]

/** 当前端分类导航（系统管理端为空，走下拉） */
const tabs = computed(() => {
  if (villageAdmin.value) return villageTabs
  if (townAdmin.value) return townTabs
  return []
})

const activeTab = ref('pending')

const query = reactive({
  status: '',
  villageCode: '',
  pageNum: 1,
  pageSize: 10,
})

/** 材料 URL 列表（兼容字符串 JSON） */
const materialList = computed(() => {
  const urls = current.value?.materialUrls
  if (!urls) return []
  if (Array.isArray(urls)) return urls
  if (typeof urls === 'string') {
    try {
      const parsed = JSON.parse(urls)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return urls ? [urls] : []
    }
  }
  return []
})

/** 结构化 materialData 只读展平（code 按政策 Schema 映射成中文名） */
const materialDataRows = computed(() => {
  const data = current.value?.materialData
  if (!data || typeof data !== 'object') return []
  const schema = current.value?.materialSchema
  const labels = {}
  for (const m of schema?.materials || []) {
    if (m?.code && m?.title) labels[m.code] = m.title
    for (const f of m?.fields || []) {
      if (m?.code && f?.key && f?.label) labels[`${m.code}.${f.key}`] = f.label
    }
  }
  for (const f of schema?.fields || []) {
    if (f?.key && f?.label) labels[f.key] = f.label
  }
  const rows = []
  const walk = (obj, prefix, prefixLabel) => {
    Object.entries(obj).forEach(([k, v]) => {
      const path = prefix ? `${prefix}.${k}` : k
      const zh = labels[path] || humanizeKey(k)
      const display = prefixLabel ? `${prefixLabel} · ${zh}` : zh
      if (v && typeof v === 'object' && !Array.isArray(v)) {
        walk(v, path, display)
      } else if (v != null && String(v).trim() !== '') {
        const s = String(v)
        const isUrl = s.startsWith('/uploads') || s.startsWith('http')
        // 图片链接直接渲染缩略图（照片字段直显，修复显示成链接文本的 bug）
        rows.push({
          label: display,
          value: s,
          isUrl,
          isImg: isUrl && /\.(png|jpe?g|gif|webp|bmp)(\?.*)?$/i.test(s),
        })
      }
    })
  }
  walk(data, '', '')
  return rows
})

/** 简单把 camelCase / snake 转成可读中文，用于无 Schema 时的兜底展示 */
function humanizeKey(key) {
  if (!key || key === '_root') return '材料'
  const map = {
    id_card: '身份证', idCard: '身份证',
    front: '正面', back: '反面',
    household: '户口本',
    bank_card: '银行卡信息', bank_confirm: '银行卡确认',
    bankCard: '银行卡', bankName: '开户行', accountName: '户名',
    bank_card_number: '银行卡号', bank_name: '开户行',
    land_proof: '地块证明', contract: '合同照片', areaNote: '申报面积',
    invoice: '购机发票', machineModel: '机具型号',
    machine_photo: '机具实拍', machine: '机具',
    farm_proof: '养殖证明', proof: '证明照片',
    photo: '照片', name: '姓名', phone: '手机号',
    id_number: '身份证号', id_copy: '户主身份证', soldier_id: '义务兵身份证',
    idCardFront: '身份证正面', idCardBack: '身份证反面',
    id_front: '正面', id_back: '反面',
    landContract: '土地合同', invoicePhoto: '发票照片',
    cert_file: '证明文件', cert_number: '证件编号',
    eligibility_proof: '资格证明',
  }
  if (map[key]) return map[key]
  if (/[\u4e00-\u9fff]/.test(key)) return key
  return key
}

/** 村管且待村审时可初审 */
const canVillageAudit = computed(
  () => villageAdmin.value && current.value?.status === 'PENDING_VILLAGE'
)

/** 镇管且待镇审时可终审 */
const canTownAudit = computed(
  () => townAdmin.value && current.value?.status === 'PENDING_TOWN'
)

/** 镇管且待发放（含历史「已通过」未发放的旧单据）时可发放 */
const canDisburse = computed(
  () =>
    townAdmin.value &&
    (current.value?.status === 'PENDING_DISBURSE' || current.value?.status === 'APPROVED')
)

/** 表格行级：当前村管且单据待村审 */
function canVillageAuditRow(row) {
  return villageAdmin.value && row.status === 'PENDING_VILLAGE'
}

/** 表格行级：当前镇管且单据待镇审 */
function canTownAuditRow(row) {
  return townAdmin.value && row.status === 'PENDING_TOWN'
}

/** 表格行级：当前镇管且单据待发放或历史已通过未发放 */
function canDisburseRow(row) {
  return townAdmin.value && (row.status === 'PENDING_DISBURSE' || row.status === 'APPROVED')
}

function statusTagType(status) {
  if (status === 'APPROVED' || status === 'DISBURSED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'PENDING_TOWN' || status === 'PENDING_DISBURSE') return 'warning'
  return 'info'
}

/** 发放时间选择器：禁用 < 申报时间 和 > 政策截止日期 */
function disabledDisburseDate(time) {
  const d = time.getTime()
  const apply = current.value
  if (apply?.createdAt) {
    const min = new Date(apply.createdAt).getTime()
    if (d < min) return true
  }
  if (apply?.policyValidTo) {
    const max = new Date(apply.policyValidTo + 'T23:59:59').getTime()
    if (d > max) return true
  }
  return false
}

/** 金额格式化：字符串/数字 → 两位小数 */
function formatAmount(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

/** 拉取申请分页列表：系统管理端走 status/villageCode，村管/镇管走 tab */
async function loadList() {
  loading.value = true
  try {
    const params = {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    }
    if (sysAdmin.value) {
      if (query.status) params.status = query.status
      if (query.villageCode) params.villageCode = query.villageCode.trim()
    } else {
      params.tab = activeTab.value
    }
    const data = await fetchApplyList(params)
    list.value = data?.list || []
    total.value = data?.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 拉取统计（系统管理端不调用） */
async function loadStats() {
  if (sysAdmin.value) return
  try {
    const data = await fetchApplyStats()
    stats.pendingCount = data?.pendingCount ?? 0
    stats.todayCount = data?.todayCount ?? 0
    stats.monthCount = data?.monthCount ?? 0
  } catch {
    // 统计失败不阻断主流程
  }
}

function onSearch() {
  query.pageNum = 1
  loadList()
}

function onReset() {
  query.status = ''
  query.villageCode = ''
  query.pageNum = 1
  loadList()
}

/** 切换分类导航：回到第一页并刷新 */
function onTabChange() {
  query.pageNum = 1
  loadList()
}

/** 打开详情抽屉 */
function openDetail(row) {
  current.value = { ...row }
  opinion.value = ''
  // 镇管发放区：预填当前已有金额和时间（村管初审时可能已拟定）
  disburseAmount.value = row.disburseAmount != null ? Number(row.disburseAmount) : null
  disburseAt.value = row.disburseAt || null
  villageDisburseAmount.value = null
  villageDisburseAt.value = null
  drawerVisible.value = true
}

/**
 * 提交审批：村管走 village-audit，镇管走 town-audit
 * @param {'PASS'|'REJECT'} action
 */
async function doAudit(action) {
  // 通过 / 驳回都必须填写审批意见
  if (!opinion.value.trim()) {
    ElMessage.warning('请填写审批意见')
    return
  }
  const actionLabel = action === 'PASS' ? '通过' : '驳回'
  try {
    await ElMessageBox.confirm(`确认${actionLabel}该申请？`, '审批确认', { type: 'warning' })
  } catch {
    return
  }
  auditing.value = true
  try {
    const body = { action, opinion: opinion.value.trim() || undefined }
    // 村管初审：附上拟定发放金额和时间（金额必填）
    if (villageAdmin.value) {
      const amt = villageDisburseAmount.value
      if (amt == null || amt <= 0) {
        ElMessage.warning('请输入拟定发放金额')
        auditing.value = false
        return
      }
      body.disburseAmount = Number(amt.toFixed(2))
      if (villageDisburseAt.value) {
        body.disburseAt = villageDisburseAt.value
      }
    }
    const id = current.value.id
    const data = villageAdmin.value
      ? await villageAudit(id, body)
      : await townAudit(id, body)
    ElMessage.success('审批成功')
    // 同步抽屉内状态
    current.value = { ...current.value, ...data }
    drawerVisible.value = false
    loadList()
    loadStats()
  } catch {
    // 拦截器已提示
  } finally {
    auditing.value = false
  }
}

/** 镇管发放补贴：二次确认后提交，成功后刷新列表与统计 */
async function doDisburse() {
  const amount = disburseAmount.value
  if (amount == null || amount <= 0) {
    ElMessage.warning('请输入正确的发放金额')
    return
  }
  const pay = Number(amount.toFixed(2))
  try {
    await ElMessageBox.confirm(`确认发放 ¥${pay.toFixed(2)}？`, '发放确认', { type: 'warning' })
  } catch {
    return
  }
  disbursing.value = true
  try {
    const body = { amount: pay }
    if (disburseAt.value) {
      body.disburseAt = disburseAt.value
    }
    await disburse(current.value.id, body)
    ElMessage.success('发放成功')
    drawerVisible.value = false
    loadList()
    loadStats()
  } catch {
    // 拦截器已提示
  } finally {
    disbursing.value = false
  }
}

onMounted(() => {
  loadList()
  loadStats()
})
</script>

<style scoped>
/* ==========================================================================
   Impeccable & React-bits (Vue Port) Animations 
   ========================================================================== */
@keyframes slideFadeUp {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

.stat-cards {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  animation: slideFadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}
.stat-card {
  flex: 1;
  text-align: center;
  transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.3s ease;
  will-change: transform, box-shadow;
}
.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.08);
}
.toolbar {
  animation: slideFadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.1s both;
}
:deep(.el-table) {
  animation: slideFadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.2s both;
}
.pager {
  animation: slideFadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.3s both;
}
.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #1b5e20;
  line-height: 1.2;
}
.stat-label {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}
.tabs {
  width: 100%;
}
.mat-data {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 100%;
}
.mat-data-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
}
.mat-key {
  color: #909399;
}
.mat-val {
  word-break: break-all;
  color: #303133;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.audit-box {
  margin-top: 20px;
}
.audit-label {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.req {
  color: #f56c6c;
  margin-left: 2px;
}
.disburse-title {
  margin-bottom: 12px;
  font-weight: 600;
  color: #303133;
}
.audit-btns {
  margin-top: 12px;
  display: flex;
  gap: 12px;
}
.materials {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.thumb {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}
.dash {
  color: #c0c4cc;
}
.disburse-val {
  color: #1b5e20;
  font-weight: 600;
}
.disburse-proposed {
  color: #e65100;
  font-size: 12px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>