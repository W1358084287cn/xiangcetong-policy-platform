<template>
  <div class="ai-config-page">
    <h3>AI 设置</h3>
    <p class="desc">配置大模型 API 和向量化服务参数，更改后即时生效。</p>

    <el-card class="config-card" shadow="never">
      <template #header><span>大模型配置</span></template>
      <el-form :model="form" label-width="140px" :rules="rules" ref="formRef">
        <el-form-item label="API 地址" prop="apiUrl">
          <el-input v-model="form.apiUrl" placeholder="https://api.deepseek.com/v1" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="sk-..." />
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="deepseek-chat" />
        </el-form-item>
        <el-form-item label="最大 Token">
          <el-input-number v-model="form.maxTokens" :min="256" :max="8192" :step="256" />
        </el-form-item>
        <el-form-item label="温度参数">
          <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="config-card" shadow="never">
      <template #header><span>向量化配置（Embedding）</span></template>
      <el-form :model="form" label-width="140px">
        <el-form-item label="Embedding 地址" prop="embeddingUrl">
          <el-input v-model="form.embeddingUrl" placeholder="https://api.siliconflow.cn/v1" />
        </el-form-item>
        <el-form-item label="Embedding Key" prop="embeddingKey">
          <el-input v-model="form.embeddingKey" type="password" show-password placeholder="sk-..." />
        </el-form-item>
        <el-form-item label="Embedding 模型" prop="embeddingModel">
          <el-input v-model="form.embeddingModel" placeholder="BAAI/bge-large-zh-v1.5" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="config-card" shadow="never">
      <template #header><span>DeepSeek 开发平台</span></template>
      <el-form :model="form" label-width="140px">
        <el-form-item label="平台链接">
          <el-input v-model="form.platformUrl" placeholder="https://platform.deepseek.com" />
        </el-form-item>
      </el-form>
      <div style="display:flex;gap:12px;margin-left:140px">
        <el-button type="success" @click="handleJumpPlatform">前往 API 开发平台</el-button>
        <el-button @click="handleSavePlatformUrl" :loading="savingPlatformUrl">保存链接</el-button>
      </div>
    </el-card>

    <el-card class="config-card" shadow="never">
      <template #header><span>余额查询</span></template>
      <div class="balance-section">
        <el-button type="primary" @click="handleQueryBalance" :loading="queryingBalance">
          查询 DeepSeek 账户余额
        </el-button>
        <el-descriptions v-if="balanceData" :column="2" border style="margin-top:16px">
          <el-descriptions-item label="账户状态">
            <el-tag :type="balanceData.isAvailable ? 'success' : 'danger'">
              {{ balanceData.isAvailable ? '可用' : '不可用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="币种">{{ balanceData.currency }}</el-descriptions-item>
          <el-descriptions-item label="总余额">
            <span class="balance-highlight">¥{{ balanceData.totalBalance }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="赠金余额">¥{{ balanceData.grantedBalance }}</el-descriptions-item>
          <el-descriptions-item label="充值余额">
            <span class="balance-topped">¥{{ balanceData.toppedUpBalance }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="今日调用次数">
            <el-tag type="info">{{ balanceData.todayCalls || 0 }} 次</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="今日消耗 Token">
            <span>{{ formatNumber(balanceData.todayTokens) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="今日预估费用" :span="2">
            <span class="balance-today-cost">¥{{ (balanceData.todayCost || 0).toFixed(4) }}</span>
            <span style="color:#909399;font-size:12px;margin-left:8px">
              基于 DeepSeek 标准定价估算
            </span>
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="balanceError" class="balance-error">
          <el-alert :title="balanceError" type="error" show-icon :closable="false" />
        </div>
      </div>
    </el-card>

    <div class="actions">
      <el-button type="primary" @click="handleSave" :loading="saving">保存配置</el-button>
      <el-button @click="handleTest" :loading="testing">测试连接</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAiConfig, updateAiConfig, testAiConnection, queryBalance, updatePlatformUrl } from '@/api/ai'

const formRef = ref(null)
const saving = ref(false)
const testing = ref(false)
const savingPlatformUrl = ref(false)
const queryingBalance = ref(false)
const balanceData = ref(null)
const balanceError = ref('')

const form = reactive({
  apiUrl: 'https://api.deepseek.com/v1',
  apiKey: '',
  modelName: 'deepseek-chat',
  embeddingUrl: 'https://api.siliconflow.cn/v1',
  embeddingKey: '',
  embeddingModel: 'BAAI/bge-large-zh-v1.5',
  maxTokens: 2048,
  temperature: 0.7,
  platformUrl: 'https://platform.deepseek.com',
})

const rules = {
  apiUrl: [{ required: true, message: '请输入 API 地址', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  embeddingUrl: [{ required: true, message: '请输入 Embedding 地址', trigger: 'blur' }],
  embeddingKey: [{ required: true, message: '请输入 Embedding Key', trigger: 'blur' }],
  embeddingModel: [{ required: true, message: '请输入 Embedding 模型', trigger: 'blur' }],
}

onMounted(async () => {
  try {
    const configData = await fetchAiConfig()
    if (configData) {
      form.apiUrl = configData.apiUrl || form.apiUrl
      form.apiKey = *** || ''
      form.modelName = configData.modelName || form.modelName
      form.embeddingUrl = configData.embeddingUrl || form.embeddingUrl
      form.embeddingKey = configData.embeddingKey || ''
      form.embeddingModel = configData.embeddingModel || form.embeddingModel
      form.maxTokens = *** || form.maxTokens
      form.temperature = configData.temperature !== null ? configData.temperature : form.temperature
      form.platformUrl = configData.platformUrl || form.platformUrl
    }
  } catch (e) {
    // 配置未初始化时静默处理
  }
})

async function handleSave() {
  saving.value = true
  try {
    await updateAiConfig({ ...form })
    ElMessage.success('AI 配置已保存')
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  testing.value = true
  try {
    await testAiConnection()
    ElMessage.success('连接正常！大模型 API 可用')
  } catch (e) {
    ElMessage.error(e?.message || '连接失败，请检查配置')
  } finally {
    testing.value = false
  }
}

/** 跳转到 DeepSeek 开发平台 */
function handleJumpPlatform() {
  const url = form.platformUrl || 'https://platform.deepseek.com'
  window.open(url, '_blank')
}

/** 保存平台链接（独立保存，不影响其他配置） */
async function handleSavePlatformUrl() {
  savingPlatformUrl.value = true
  try {
    await updatePlatformUrl(form.platformUrl)
    ElMessage.success('开发平台链接已保存')
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    savingPlatformUrl.value = false
  }
}

/** 查询 DeepSeek 账户余额 */
async function handleQueryBalance() {
  queryingBalance.value = true
  balanceError.value = ''
  balanceData.value = null
  try {
    const data = await queryBalance()
    balanceData.value = data
    ElMessage.success('余额查询成功')
  } catch (e) {
    balanceError.value = e?.message || '余额查询失败'
    ElMessage.error(balanceError.value)
  } finally {
    queryingBalance.value = false
  }
}

/** 数字千分位格式化 */
function formatNumber(num) {
  if (num == null) return '0'
  return Number(num).toLocaleString()
}
</script>

<style scoped>
.ai-config-page { max-width: 720px; }
.desc { color: #999; font-size: 13px; margin-bottom: 20px; }
.config-card { margin-bottom: 20px; }
.actions { display: flex; gap: 12px; margin-top: 8px; }
.balance-section { margin-bottom: 4px; }
.balance-highlight { font-size: 18px; font-weight: 700; color: #1b5e20; }
.balance-topped { color: #409eff; font-weight: 600; }
.balance-error { margin-top: 12px; }
</style>