<template>
  <div class="chat-logs-page">
    <!-- 列表页：按用户分组 -->
    <div v-if="!currentUser">
      <div class="header">
        <h3>AI 对话日志</h3>
        <span class="desc">按用户分组查看对话记录，点击用户可查看详情</span>
      </div>

      <div class="search-bar">
        <el-input
          v-model="searchPhone"
          placeholder="搜索手机号"
          clearable
          style="width: 240px"
          @keyup.enter="searchUsers"
          @clear="searchUsers"
        />
        <el-button type="primary" @click="searchUsers">搜索</el-button>
      </div>

      <el-table :data="userList" stripe v-loading="loading" empty-text="暂无对话记录" @row-click="openDetail">
        <el-table-column prop="phone" label="手机号" width="160" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="chatCount" label="对话次数" width="100" align="center" />
        <el-table-column label="最后对话时间" width="180">
          <template #default="{ row }">{{ row.lastChatTime }}</template>
        </el-table-column>
        <el-table-column label="Token 消耗" min-width="140" align="center">
          <template #default="{ row }">
            <span>{{ row.totalTokens }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default>
            <el-button type="primary" link size="small">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="userPage"
          :page-size="userPageSize"
          :total="userTotal"
          layout="total, prev, pager, next"
          background
          @current-change="loadUsers"
        />
      </div>
    </div>

    <!-- 详情页：某个用户的对话记录 -->
    <div v-else>
      <div class="detail-header">
        <el-button type="primary" link @click="backToList">
          <el-icon><ArrowLeft /></el-icon> 返回用户列表
        </el-button>
        <span class="user-info">
          <el-icon><User /></el-icon>
          {{ currentUser.name }}（{{ currentUser.phone }}）
        </span>
      </div>

      <el-table :data="detailList" stripe v-loading="detailLoading" empty-text="暂无对话记录">
        <el-table-column label="问题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="question-text">{{ row.question }}</span>
          </template>
        </el-table-column>
        <el-table-column label="AI 回答" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="answer-text">{{ row.answer }}</span>
          </template>
        </el-table-column>
        <el-table-column label="知识库" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.fromKnowledge ? 'success' : 'info'" size="small">
              {{ row.fromKnowledge ? '命中' : '未命中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="模型" width="130" />
        <el-table-column label="Token" width="140" align="center">
          <template #default="{ row }">
            <span>输入{{ row.promptTokens }}+输出{{ row.completionTokens }}</span>
          </template>
        </el-table-column>
        <el-table-column label="提问时间" width="170">
          <template #default="{ row }">{{ row.createdAt }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="detailPage"
          :page-size="detailPageSize"
          :total="detailTotal"
          layout="total, prev, pager, next"
          background
          @current-change="loadDetail"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ArrowLeft, User } from '@element-plus/icons-vue'
import { fetchChatUsers, fetchChatLogsByUser } from '@/api/ai'

const loading = ref(false)
const detailLoading = ref(false)
const searchPhone = ref('')

// 用户列表
const userList = ref([])
const userTotal = ref(0)
const userPage = ref(1)
const userPageSize = 10

// 详情
const currentUser = ref(null)
const detailList = ref([])
const detailTotal = ref(0)
const detailPage = ref(1)
const detailPageSize = 20

onMounted(() => loadUsers())

async function loadUsers() {
  loading.value = true
  try {
    const result = await fetchChatUsers({
      page: userPage.value,
      size: userPageSize,
      phone: searchPhone.value,
    })
    userList.value = result?.list || []
    userTotal.value = result?.total || 0
  } catch (e) {
    // Axios 拦截器已统一弹错误提示，此处仅容错
  } finally {
    loading.value = false
  }
}

function searchUsers() {
  userPage.value = 1
  loadUsers()
}

function openDetail(row) {
  currentUser.value = row
  detailPage.value = 1
  loadDetail()
}

async function loadDetail() {
  if (!currentUser.value) return
  detailLoading.value = true
  try {
    const result = await fetchChatLogsByUser(currentUser.value.userId, {
      page: detailPage.value,
      size: detailPageSize,
    })
    detailList.value = result?.list || []
    detailTotal.value = result?.total || 0
  } catch (e) {
    // Axios 拦截器已统一弹错误提示，此处仅容错
  } finally {
    detailLoading.value = false
  }
}

function backToList() {
  currentUser.value = null
  detailList.value = []
}
</script>

<style scoped>
.chat-logs-page { padding: 0; }
.header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; }
.header h3 { margin: 0; }
.desc { color: #999; font-size: 13px; }
.search-bar { display: flex; gap: 12px; margin-bottom: 16px; }
.question-text { color: #303133; }
.answer-text { color: #606266; }
.na { color: #c0c4cc; }
.pagination-wrapper { display: flex; justify-content: center; margin-top: 20px; }

.detail-header {
  display: flex; align-items: center; gap: 16px;
  margin-bottom: 16px; padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}
.user-info { font-size: 15px; font-weight: 500; color: #303133; display: flex; align-items: center; gap: 6px; }
</style>