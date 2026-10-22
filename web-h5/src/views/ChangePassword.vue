<!--
  修改密码：旧密码 + 新密码 + 确认新密码。
  前端校验必填、新密码 6~32 位、两次一致；成功后清除登录态跳登录页。
-->
<template>
  <div class="page">
    <van-nav-bar
      title="修改密码"
      left-text="返回"
      left-arrow
      fixed
      placeholder
      @click-left="goBack"
    />

    <div class="form-card">
      <p class="form-tip">新密码要 6 到 32 位，两次填一样就行</p>

      <van-form @submit="onSubmit">
        <van-cell-group :border="false" class="field-group">
          <van-field
            v-model="form.oldPassword"
            :type="showOld ? 'text' : 'password'"
            name="oldPassword"
            label="旧密码"
            placeholder="请输入旧密码"
            :rules="[{ required: true, message: '请填写旧密码' }]"
          >
            <template #right-icon>
              <van-icon
                :name="showOld ? 'eye-o' : 'closed-eye'"
                class="pwd-eye"
                @click="showOld = !showOld"
              />
            </template>
          </van-field>

          <van-field
            v-model="form.newPassword"
            :type="showNew ? 'text' : 'password'"
            name="newPassword"
            label="新密码"
            placeholder="请输入新密码（6~32 位）"
            :rules="newPwdRules"
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
            placeholder="请再输入一遍新密码"
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
            确认修改
          </van-button>
        </div>
      </van-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { changePassword } from '@/api/auth'
import { clearAuth } from '@/utils/auth'
import { safeBack } from '@/utils/navBack'

const router = useRouter()
const loading = ref(false)
const showOld = ref(false)
const showNew = ref(false)
const showConfirm = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

/** 新密码：必填 + 6~32 位 */
const newPwdRules = [
  { required: true, message: '请填写新密码' },
  {
    validator: (v) => v.length >= 6 && v.length <= 32,
    message: '新密码需 6~32 位',
  },
]

/** 确认新密码：必填 + 与新密码一致 */
const confirmPwdRules = [
  { required: true, message: '请再填写一次新密码' },
  {
    validator: (v) => v === form.newPassword,
    message: '两次输入的新密码不一致',
  },
]

function goBack() {
  safeBack(router, '/mine')
}

/** 提交修改；成功后清登录态回登录页 */
async function onSubmit() {
  loading.value = true
  try {
    await changePassword({
      oldPassword: ***,
      newPassword: ***,
    })
    showSuccessToast('修改成功，请重新登录')
    clearAuth()
    router.replace('/login')
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
  padding-bottom: 24px;
}
.form-card {
  margin: 12px 16px 0;
  padding: 16px;
  background: var(--xct-card-bg, #fff);
  border: var(--xct-card-border, 0 solid transparent);
  border-radius: var(--xct-radius, 14px);
  box-shadow: var(--xct-card-shadow, 0 4px 14px rgba(27, 94, 32, 0.06));
}
.form-tip {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: var(--xct-radius-sm, 10px);
  background: var(--xct-green-soft, #e8f5e9);
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: var(--xct-green-deep, #1b5e20);
  line-height: 1.45;
}
.field-group {
  background: transparent;
}
.field-group :deep(.van-cell) {
  padding-left: 4px;
  padding-right: 4px;
}
.pwd-eye {
  font-size: 22px;
  padding: 8px;
  color: var(--xct-color-muted, #5a5a5a);
}
.btn-wrap {
  margin-top: 24px;
}
.main-btn {
  height: var(--xct-hit-min, 48px) !important;
  font-size: var(--xct-fs-body, 1rem) !important;
}
</style>
