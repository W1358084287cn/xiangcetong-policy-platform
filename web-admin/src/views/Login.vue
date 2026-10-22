<!--
  登录页：统一模式（unified）展示角色选择器，其他模式固定使用本端 role。
  左侧为绿色品牌区（平台名 + 端名 + 副标题），右侧为登录表单。
  登录成功后校验返回角色与选择/本端角色一致，再跳转到对应默认页（支持 query.redirect）。
-->
<template>
  <div class="login-page">
    <div class="login-card">
      <!-- 左侧品牌区 -->
      <div class="brand">
        <div class="brand-mark">乡</div>
        <h1 class="brand-name">乡策通</h1>
        <div class="brand-role">{{ appRoleName }}</div>
        <p class="brand-sub">助农扶持政策管理平台</p>
        <div class="brand-points">
          <div class="point"><span class="dot"></span>政策精准匹配到村到户</div>
          <div class="point"><span class="dot"></span>补贴申报审批全流程</div>
          <div class="point"><span class="dot"></span>数据透明可追溯</div>
        </div>
      </div>

      <!-- 右侧登录表单 -->
      <div class="panel">
        <h2 class="panel-title">欢迎登录</h2>
        <p class="panel-sub">{{ isUnified ? '请选择身份并登录' : `请使用「${appRoleName}」账号登录` }}</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <!-- 统一模式：角色选择器 -->
          <el-form-item v-if="isUnified" label="登录身份" prop="selectedRole">
            <el-select
              v-model="form.selectedRole"
              size="large"
              placeholder="请选择登录身份"
              style="width: 100%"
            >
              <el-option
                v-for="opt in ROLE_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              >
                <div style="display: flex; justify-content: space-between; align-items: center">
                  <span>{{ opt.label }}</span>
                  <span style="color: #909399; font-size: 12px">{{ opt.desc }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="账号" prop="username">
            <el-input
              v-model="form.username"
              size="large"
              placeholder="请输入账号"
              :prefix-icon="User"
              clearable
              @keyup.enter="onSubmit"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              placeholder="请输入密码"
              :prefix-icon="Lock"
              @keyup.enter="onSubmit"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="onSubmit"
          >
            登 录
          </el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
// 登录页逻辑：账号密码登录并携带角色（统一模式为选择的角色，否则为本端固定角色）
import { reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { setToken, setUser, clearAuth } from '@/utils/auth'
import {
  APP_ROLE,
  APP_ROLE_NAME,
  isUnifiedMode,
  ROLE_OPTIONS,
  MENU_BY_ROLE,
  SYS_ADMIN,
} from '@/config/role'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)

const appRoleName = APP_ROLE_NAME
const isUnified = isUnifiedMode

/** 实际使用的登录角色：统一模式取选择值，否则取本端固定角色 */
const loginRole = computed(() => {
  return isUnified ? form.selectedRole : APP_ROLE
})

const form = reactive({
  // 演示账号预填（admin 账号在库中按 role 区分，默认选「系统管理端」功能最全）
  username: 'admin',
  password: '***',
  selectedRole: SYS_ADMIN,
})

const rules = {
  ...(isUnified ? { selectedRole: [{ required: true, message: '请选择登录身份', trigger: 'change' }] } : {}),
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/**
 * 提交登录：校验表单后调用 login({username, password, role})，
 * 返回角色与登录角色不符则清除登录态并报错；成功则写 token/user 并跳默认页。
 * 统一模式下所有子路由已预注册，无需 addRoute，登录后直接 push 即可。
 */
async function onSubmit() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  loading.value = true
  try {
    const role = loginRole.value
    const data = await login({
      username: form.username.trim(),
      password: ***,
      role,
    })
    // 该账号不属于所选/本端角色
    if (data?.user?.role !== role) {
      clearAuth()
      ElMessage.error('该账号不属于所选角色')
      return
    }
    setToken(data.token)
    setUser(data.user)
    ElMessage.success('登录成功')
    // 根据实际登录角色获取默认路径并跳转
    const menu = MENU_BY_ROLE[role] || []
    const defaultPath = menu[0]?.path || '/applies'
    const redirect = route.query.redirect || defaultPath
    await router.push(String(redirect))
  } catch (err) {
    // 仅 NavigationDuplicated（重复导航）可忽略，其他错误提示用户
    if (err?.name !== 'NavigationDuplicated') {
      ElMessage.error('登录失败，请重试')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ==========================================================================
   Impeccable UI: Golden Wheat & Prairie Theme (Login)
   ========================================================================== */
@keyframes slideFadeUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
  background: transparent;
}

.login-card {
  position: relative;
  z-index: 1;
  width: 960px;
  max-width: 100%;
  min-height: 560px;
  display: flex;
  border-radius: 24px; /* 极简大圆角 */
  overflow: hidden;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 24px 64px rgba(217, 119, 6, 0.12);
  animation: slideFadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.brand {
  flex: 1 1 45%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 56px 48px;
  color: #ffffff;
  /* 使用金色麦田渐变 */
  background: linear-gradient(135deg, #d97706 0%, #b45309 100%);
  position: relative;
  overflow: hidden;
}

.brand::after {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 60%);
  transform: rotate(30deg);
  pointer-events: none;
}

.brand-mark {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 700;
  border-radius: 16px;
  color: #d97706;
  background: #ffffff;
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
  z-index: 1;
}

.brand-name {
  margin: 28px 0 0;
  font-size: 42px;
  letter-spacing: 4px;
  font-weight: 700;
  z-index: 1;
  text-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.brand-role {
  display: inline-block;
  align-self: flex-start;
  margin-top: 12px;
  padding: 6px 16px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(4px);
  z-index: 1;
}

.brand-sub {
  margin: 16px 0 0;
  font-size: 16px;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.6;
  z-index: 1;
}

.brand-points {
  margin-top: 40px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  z-index: 1;
}

.point {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.95);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fef3c7;
  box-shadow: 0 0 8px #fef3c7;
}

.panel {
  flex: 1 1 55%;
  padding: 64px 64px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.panel-title {
  margin: 0;
  font-size: 32px;
  font-weight: 800;
  color: #451a03;
}

.panel-sub {
  margin: 12px 0 36px;
  font-size: 15px;
  color: #78350f;
}

/* 输入框定制 */
.panel :deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 0 0 1px rgba(217, 119, 6, 0.2) inset;
  padding: 0 16px;
  background: rgba(254, 243, 199, 0.3);
  transition: all 0.3s ease;
}
.panel :deep(.el-input__wrapper:hover),
.panel :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: 0 0 0 1px #d97706 inset, 0 4px 12px rgba(217, 119, 6, 0.08);
}
.panel :deep(.el-input__inner) {
  height: 48px;
  font-size: 15px;
  color: #451a03;
}

.submit-btn {
  width: 100%;
  margin-top: 12px;
  height: 52px;
  font-size: 16px;
  letter-spacing: 4px;
  border-radius: 12px;
  font-weight: 700;
  --el-button-bg-color: #d97706;
  --el-button-border-color: #d97706;
  --el-button-hover-bg-color: #b45309;
  --el-button-hover-border-color: #b45309;
  box-shadow: 0 8px 20px rgba(217, 119, 6, 0.2);
  transition: all 0.3s ease;
}
.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(217, 119, 6, 0.3);
}
.submit-btn:active {
  transform: translateY(0);
}

@media (max-width: 720px) {
  .login-card {
    flex-direction: column;
    min-height: auto;
  }
  .brand {
    padding: 36px 32px;
  }
  .brand-points {
    display: none;
  }
  .brand-name {
    font-size: 32px;
  }
  .panel {
    padding: 40px 32px;
  }
}
</style>