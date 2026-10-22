<!--
  忘记密码页：输入已注册手机号 + 新密码，重置成功后自动登录跳首页。
-->
<template>
  <div class="page">
    <van-nav-bar
      title="忘记密码"
      left-text="返回"
      left-arrow
      fixed
      placeholder
      @click-left="goBack"
    />

    <div class="form-card">
      <p class="form-tip">输入注册时使用的手机号及姓名验证身份，然后设置新密码</p>

      <van-form @submit="onSubmit">
        <van-cell-group :border="false" class="field-group">
          <van-field
            v-model="form.phone"
            name="phone"
            label="手机号"
            type="tel"
            maxlength="11"
            placeholder="请输入已注册的手机号"
            :rules="[{ required: true, message: '请填写手机号' }]"
          />
          <van-field
            v-model="form.name"
            name="name"
            label="姓名验证"
            placeholder="请输入注册时填写的姓名"
            :rules="[{ required: true, message: '请填写姓名以验证身份' }]"
          />
          <van-field
            v-model="form.newPassword"
            :type="showNew ? 'text' : 'password'"
            name="newPassword"
            label="新密码"
            placeholder="请输入新密码（6~32位）"
            :rules="[{ required: true, message: '请填写新密码' }]"
          >
            <template #right-icon>
              <van-icon
                :name="showNew ? 'eye-o' : 'closed-eye'"
                class="pwd-eye"
                @click="showNew = !showNew"
              />
            </template>
          </van-field>
          <van-field
            v-model="form.confirmPassword"
            :type="showConfirm ? 'text' : 'password'"
            name="confirmPassword"
            label="确认新密码"
            placeholder="请再次输入新密码"
            :rules="confirmPwdRules"
          >
            <template #right-icon>
              <van-icon
                :name="showConfirm ? 'eye-o' : 'closed-eye'"
                class="pwd-eye"
                @click="showConfirm = !showConfirm"
              />
            </template>
          </van-field>
        </van-cell-group>

        <div class="btn-wrap">
          <van-button
            round
            block
            type="primary"
            native-type="submit"
            :loading="loading"
            class="main-btn"
          >
            重置密码并登录
          </van-button>
        </div>
      </van-form>

      <div class="xct-auth-links">
        <span class="extra-text">想起密码了？</span>
        <router-link to="/login" class="xct-auth-link">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { resetPassword } from '@/api/auth'
import { setToken, setUser } from '@/utils/auth'
import { safeBack } from '@/utils/navBack'

const router = useRouter()
const loading = ref(false)
const showNew = ref(false)
const showConfirm = ref(false)

const form = reactive({
  phone: '',
  name: '',
  newPassword: '',
  confirmPassword: '',
})

const confirmPwdRules = [
  { required: true, message: '请再次输入新密码' },
  {
    validator: (val) => val === form.newPassword,
    message: '两次输入的新密码不一致',
  },
]

function goBack() {
  safeBack(router, '/login')
}

async function onSubmit() {
  if (form.newPassword.length < 6 || form.newPassword.length > 32) {
    return
  }
  loading.value = true
  try {
    const data = await resetPassword({
      phone: form.phone.trim(),
      name: form.name.trim(),
      newPassword: ***,
    })
    setToken(data.token)
    setUser(data.user)
    showSuccessToast('密码重置成功')
    router.replace('/home')
  } catch {
    // 错误已在 request 拦截器 Toast
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--xct-bg, #f3f6f1);
}

.form-card {
  padding: 24px 16px;
}

.form-tip {
  margin: 0 0 20px;
  color: #888;
  font-size: 14px;
  text-align: center;
  line-height: 1.6;
}

.field-group {
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}

.pwd-eye {
  color: #aaa;
  cursor: pointer;
}

.btn-wrap {
  margin-top: 24px;
}

.main-btn {
  height: var(--xct-hit-min, 48px);
  font-size: 1.0625rem;
  font-weight: 600;
  border-radius: 23px;
  background: linear-gradient(135deg, #3d9b5f, #1f6b3a);
  border: none;
}

.extra-text {
  display: inline-flex;
  align-items: center;
  color: var(--xct-color-muted, #5a5a5a);
}
</style>