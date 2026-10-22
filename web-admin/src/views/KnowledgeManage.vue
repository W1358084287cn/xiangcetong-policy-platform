<template>
  <div class="knowledge-page">
    <div class="header">
      <h3>知识库管理</h3>
      <div style="display:flex;gap:10px">
        <el-button type="success" @click="handleRebuild" :loading="rebuilding">
          从政策表重建知识库
        </el-button>
        <el-upload
          :before-upload="beforeUpload"
          :http-request="handleUpload"
          :show-file-list="false"
          accept=".pdf,.docx,.doc,.md,.txt"
        >
          <el-button type="primary">+ 上传文档</el-button>
        </el-upload>
      </div>
    </div>
    <p class="desc">支持 PDF、Word (.docx)、Markdown、TXT 格式，最大 20MB。上传后自动解析分块并生成向量。</p>

    <el-table :data="list" stripe v-loading="loading" empty-text="暂无知识文档，请上传">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="文档标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="fileType" label="类型" width="90" />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
          <el-tooltip v-if="row.status === 'FAILED'" :content="row.errorMsg" placement="top">
            <el-icon style="color:#f56c6c;cursor:help;margin-left:4px"><WarningFilled /></el-icon>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="分块数" width="80" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="showChunks(row)">分块</el-button>
          <el-button
            v-if="row.status === 'FAILED'"
            text
            size="small"
            type="warning"
            @click="handleReprocess(row)"
          >重处理</el-button>
          <el-button text size="small" type="danger" @click="handleDel(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchData"
      />
    </div>

    <!-- 分块详情弹窗 -->
    <el-dialog v-model="chunkVisible" title="文档分块详情" width="700px" top="5vh">
      <el-table :data="chunks" stripe max-height="500">
        <el-table-column prop="chunkIndex" label="序号" width="60" />
        <el-table-column prop="chunkText" label="内容" min-width="400" show-overflow-tooltip />
        <el-table-column prop="tokenCount" label="Token" width="70" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import {
  fetchKnowledgeList,
  uploadKnowledge,
  fetchChunkDetail,
  deleteKnowledge,
  reprocessKnowledge,
  rebuildKnowledge,
} from '@/api/ai'

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const rebuilding = ref(false)

const chunkVisible = ref(false)
const chunks = ref([])

onMounted(() => fetchData())

async function fetchData() {
  loading.value = true
  try {
    const result = await fetchKnowledgeList({ page: pageNum.value, size: pageSize })
    list.value = result?.list || []
    total.value = result?.total || 0
  } catch (e) {
    ElMessage.error('加载知识库失败')
  } finally {
    loading.value = false
  }
}

function beforeUpload(file) {
  const allowed = ['pdf', 'docx', 'doc', 'md', 'markdown', 'txt']
  const ext = file.name.split('.').pop().toLowerCase()
  if (!allowed.includes(ext)) {
    ElMessage.warning('仅支持 PDF、Word、Markdown、TXT 格式')
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过 20MB')
    return false
  }
  return true
}

async function handleUpload({ file }) {
  try {
    await uploadKnowledge(file)
    ElMessage.success('文档上传成功，正在处理中...')
    setTimeout(() => fetchData(), 3000)
  } catch (e) {
    ElMessage.error(e?.message || '上传失败')
  }
}

/** 从数据库政策表全量重建知识库 */
async function handleRebuild() {
  try {
    await ElMessageBox.confirm(
      '将从当前所有已上线政策重建知识库，此操作会覆盖已有的"数据库政策同步"文档。是否继续？',
      '确认重建',
      { confirmButtonText: '确认重建', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  rebuilding.value = true
  try {
    const result = await rebuildKnowledge()
    ElMessage.success(`知识库重建完成：同步 ${result.policyCount} 条政策，${result.chunkCount} 个分块`)
    setTimeout(() => fetchData(), 1000)
  } catch (e) {
    ElMessage.error(e?.message || '重建失败')
  } finally {
    rebuilding.value = false
  }
}

async function showChunks(row) {
  try {
    const result = await fetchChunkDetail(row.id)
    chunks.value = result || []
    chunkVisible.value = true
  } catch (e) {
    ElMessage.error('加载分块失败')
  }
}

async function handleReprocess(row) {
  try {
    await reprocessKnowledge(row.id)
    ElMessage.success('已开始重新处理')
    setTimeout(() => fetchData(), 3000)
  } catch (e) {
    ElMessage.error(e?.message || '重新处理失败')
  }
}

async function handleDel(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.title}」吗？所有关联分块也会一并删除。`, '确认删除', {
      type: 'warning',
    })
    await deleteKnowledge(row.id)
    ElMessage.success('已删除')
    fetchData()
  } catch (e) {
    // 取消删除，不做处理
  }
}

function statusType(status) {
  return status === 'READY' ? 'success' : status === 'PROCESSING' ? 'warning' : 'danger'
}
function statusLabel(status) {
  return status === 'READY' ? '就绪' : status === 'PROCESSING' ? '处理中' : '失败'
}
function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style scoped>
.knowledge-page { max-width: 960px; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.header h3 { margin: 0; }
.desc { color: #999; font-size: 13px; margin-bottom: 16px; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>