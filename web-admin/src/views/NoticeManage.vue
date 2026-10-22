<!--
  通知管理页（仅 SYS_ADMIN 系统管理端注册路由与菜单）
  提供通知的列表、新建、编辑、上线/下线快捷切换、删除。
  status：ONLINE / OFFLINE；sort 数值越大越靠前。
-->
<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="openForm()">新建通知</el-button>
      </div>

      <!-- 通知列表 -->
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无通知">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ONLINE' ? '已上线' : '已下线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="90" />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openForm(row)">编辑</el-button>
            <el-button
              v-if="row.status === 'OFFLINE'"
              type="success"
              link
              @click="onToggle(row, 'ONLINE')"
            >
              上线
            </el-button>
            <el-button v-else type="warning" link @click="onToggle(row, 'OFFLINE')">下线</el-button>
            <el-button type="danger" link @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 新建 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑通知' : '新建通知'"
      width="560px"
      top="8vh"
      destroy-on-close
    >
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题" required>
          <el-input
            v-model="form.title"
            maxlength="128"
            show-word-limit
            placeholder="请输入通知标题"
          />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            maxlength="2048"
            show-word-limit
            placeholder="请输入通知内容"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            active-value="ONLINE"
            inactive-value="OFFLINE"
            active-text="上线"
            inactive-text="下线"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="form.sort"
            :min="0"
            :step="1"
            :precision="0"
            controls-position="right"
            style="width: 180px"
          />
          <span class="sort-hint">数值越大越靠前</span>
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
// 通知管理页：列表 + 新建/编辑 + 上下线 + 删除
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchNoticeList, createNotice, updateNotice, deleteNotice } from '@/api/notice'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const dialogVisible = ref(false)

const query = reactive({ page: 1, size: 10 })

/** 空表单（新增/编辑共用） */
const emptyForm = () => ({ id: null, title: '', content: '', status: 'ONLINE', sort: 0 })
const form = reactive(emptyForm())

/** 加载通知分页列表 */
async function loadList() {
  loading.value = true
  try {
    const data = await fetchNoticeList({ page: query.page, size: query.size })
    list.value = data?.list || []
    total.value = data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开新建/编辑表单 */
function openForm(row) {
  Object.assign(form, emptyForm())
  if (row) {
    Object.assign(form, {
      id: row.id,
      title: row.title || '',
      content: row.content || '',
      status: row.status || 'OFFLINE',
      sort: row.sort != null ? Number(row.sort) : 0,
    })
  }
  dialogVisible.value = true
}

/** 保存（新增/编辑） */
async function onSave() {
  if (!form.title || !form.title.trim()) {
    ElMessage.warning('请填写通知标题')
    return
  }
  const payload = {
    title: form.title.trim(),
    content: form.content,
    status: form.status,
    sort: form.sort,
  }
  saving.value = true
  try {
    if (form.id) {
      await updateNotice(form.id, payload)
    } else {
      await createNotice(payload)
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

/** 上线/下线快捷切换（PUT 仅传 status） */
async function onToggle(row, status) {
  const tip = status === 'ONLINE' ? '确认上线该通知？' : '确认下线该通知？'
  try {
    await ElMessageBox.confirm(tip, '提示', { type: 'warning' })
    await updateNotice(row.id, { status })
    ElMessage.success('操作成功')
    loadList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

/** 删除通知 */
async function onDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除该通知？删除后不可恢复。', '删除确认', { type: 'warning' })
    await deleteNotice(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
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
.sort-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
</style>
