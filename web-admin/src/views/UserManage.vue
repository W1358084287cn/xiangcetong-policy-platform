<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="query.role" clearable placeholder="全部角色" style="width: 160px" @change="loadList">
          <el-option label="村民" value="VILLAGER" />
          <el-option label="村级管理员" value="VILLAGE_ADMIN" />
          <el-option label="镇级管理员" value="TOWN_ADMIN" />
          <el-option label="系统管理员" value="SYS_ADMIN" />
        </el-select>
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe empty-text="暂无用户">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">{{ formatRole(row.role) }}</template>
        </el-table-column>
        <el-table-column prop="countyCode" label="县" width="100" />
        <el-table-column prop="townCode" label="镇" width="100" />
        <el-table-column prop="villageCode" label="村" width="100" />
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确定删除该用户？" confirm-button-text="删除" @confirm="onDelete(row)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
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

    <!-- 新增用户弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增用户" width="480px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="手机号" required>
          <el-input v-model="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="form.password" type="password" show-password placeholder="如 Demo@123" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="村民" value="VILLAGER" />
            <el-option label="村级管理员" value="VILLAGE_ADMIN" />
            <el-option label="镇级管理员" value="TOWN_ADMIN" />
            <el-option label="系统管理员" value="SYS_ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="县编码">
          <el-input v-model="form.countyCode" placeholder="示例县" />
        </el-form-item>
        <el-form-item label="镇编码">
          <el-input v-model="form.townCode" placeholder="示例镇" />
        </el-form-item>
        <el-form-item label="村编码">
          <el-input v-model="form.villageCode" placeholder="东村 / 西村（村管/村民必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchUserList, createUser, deleteUser } from '@/api/user'
import { formatRole } from '@/utils/status'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const query = reactive({ role: '', pageNum: 1, pageSize: 10 })
const form = reactive({
  phone: '',
  name: '',
  password: '***',
  role: 'VILLAGER',
  countyCode: '示例县',
  townCode: '示例镇',
  villageCode: '',
})

/** 加载用户列表 */
async function loadList() {
  loading.value = true
  try {
    const data = await fetchUserList({
      role: query.role || undefined,
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

/** 打开新增表单 */
function openCreate() {
  Object.assign(form, {
    phone: '',
    name: '',
    password: '***',
    role: 'VILLAGER',
    countyCode: '示例县',
    townCode: '示例镇',
    villageCode: '',
  })
  dialogVisible.value = true
}

/** 创建用户 */
async function onSave() {
  if (!form.phone || !form.name || !form.password || !form.role) {
    ElMessage.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    await createUser({ ...form })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    saving.value = false
  }
}

/** 删除用户 */
async function onDelete(row) {
  try {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
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
</style>