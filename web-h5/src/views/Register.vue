<!--
  注册页：村民自助注册，填写手机号+密码+姓名，成功后自动登录跳首页。
-->
<template>
  <div class="page">
    <van-nav-bar
      title="注册账号"
      left-text="返回"
      left-arrow
      fixed
      placeholder
      @click-left="goBack"
    />

    <div class="form-card">
      <p class="form-tip">注册后即可使用所有功能，请填写真实姓名以便匹配政策</p>

      <van-form @submit="onSubmit">
        <van-cell-group :border="false" class="field-group">
          <van-field
            v-model="form.phone"
            name="phone"
            label="手机号"
            type="tel"
            maxlength="11"
            placeholder="请输入手机号"
            :rules="[{ required: true, message: '请填写手机号' }]"
          />
          <van-field
            v-model="form.name"
            name="name"
            label="姓名"
            maxlength="20"
            placeholder="请输入真实姓名"
            :rules="[{ required: true, message: '请填写姓名' }]"
          />
          <van-field
            v-model="form.password"
            :type="showPwd ? 'text' : 'password'"
            name="password"
            label="密码"
            placeholder="请输入密码（6~32位）"
            :rules="[{ required: true, message: '请填写密码' }]"
          >
            <template #right-icon>
              <van-icon
                :name="showPwd ? 'eye-o' : 'closed-eye'"
                class="pwd-eye"
                @click="showPwd = !showPwd"
              />
            </template>
          </van-field>
          <van-field
            v-model="form.confirmPassword"
            :type="showConfirm ? 'text' : 'password'"
            name="confirmPassword"
            label="确认密码"
            placeholder="请再次输入密码"
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
            注册并登录
          </van-button>
        </div>
      </van-form>

      <div class="xct-auth-links">
        <span class="extra-text">已有账号？</span>
        <router-link to="/login" class="xct-auth-link">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { register } from '@/api/auth'
import { setToken, setUser } from '@/utils/auth'
import { safeBack } from '@/utils/navBack'

const router = useRouter()
const loading = ref(false)
const showPwd = ref(false)
const showConfirm = ref(false)

const form = reactive({
  phone: '',
  name: '',
  password: '',
  confirmPassword: '',
})

const confirmPwdRules = [
  { required: true, message: '请再次输入密码' },
  {
    validator: (val) => val === form.password,
    message: '两次输入的密码不一致',
  },
]

function goBack() {
  safeBack(router, '/login')
}

async function onSubmit() {
  if (form.password.length < 6 || form.password.length > 32) {
    return
  }
  loading.value = true
  try {
    const data = await register({
      phone: form.phone.trim(),
      name: form.name.trim(),
      password: ***,
    })
    setToken(data.token)
    setUser(data.user)
    showSuccessToast('注册成功')
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