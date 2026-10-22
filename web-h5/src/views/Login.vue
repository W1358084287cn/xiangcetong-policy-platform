<!--
  登录页：乡策通村民端入口。
  绿色渐变 Hero 品牌区 + 白卡表单；登录成功后按 redirect 跳回来源页。
-->
<template>
  <div class="login-page">
    <!-- 品牌 Hero：农绿渐变 + 应用名 + slogan -->
    <header class="hero">
      <div class="hero-pattern" aria-hidden="true" />
      <div class="hero-inner">
        <span class="logo-badge" aria-hidden="true">乡</span>
        <h1>乡策通</h1>
        <p class="slogan">助农扶持政策管理平台 · 村民端</p>
        <p class="sub">让政策惠及每一位农民，让补贴精准到每一户</p>
      </div>
      <div class="hero-curve" aria-hidden="true" />
    </header>

    <!-- 登录表单白卡（压住 Hero 下缘，形成层次） -->
    <main class="form-card">
      <h2 class="form-title">手机号登录</h2>
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
            v-model="form.password"
            :type="showPwd ? 'text' : 'password'"
            name="password"
            label="密码"
            placeholder="请输入密码"
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
            登录
          </van-button>
        </div>
      </van-form>

      <!-- 注册 / 忘记密码入口：撑到 ≥48px 触控高，老人好按 -->
      <div class="xct-auth-links">
        <router-link to="/register" class="xct-auth-link">注册账号</router-link>
        <span class="xct-auth-sep">|</span>
        <router-link to="/forgot-password" class="xct-auth-link">忘记密码</router-link>
      </div>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { login } from '@/api/auth'
import { setToken, setUser } from '@/utils/auth'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const showPwd = ref(false)

const form = reactive({
  // 演示账号预填：张三峰（正定镇东村村民）
  phone: '15632489079',
  password: '***',
})

/** 提交登录并跳转首页 */
async function onSubmit() {
  loading.value = true
  try {
    const data = await login({
      phone: form.phone.trim(),
      password: ***,
    })
    setToken(data.token)
    setUser(data.user)
    showSuccessToast('登录成功')
    const redirect = route.query.redirect || '/home'
    router.replace(String(redirect))
  } catch {
    // 错误已在 request 拦截器 Toast
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 整页浅绿底，Hero 与表单卡上下衔接 */
.login-page {
  min-height: 100vh;
  padding-bottom: 32px;
  background: var(--xct-bg, #f3f6f1);
}

/* 品牌 Hero：农绿渐变 + 点状纹理 + 底部弧形收口 */
.hero {
  position: relative;
  padding: 52px 20px 72px;
  background: linear-gradient(165deg, #3d9b5f 0%, #1f6b3a 48%, #14532d 100%);
  color: #fff;
  overflow: hidden;
  text-align: center;
}
.hero-pattern {
  position: absolute;
  inset: 0;
  opacity: 0.14;
  background-image: radial-gradient(#fff 1.2px, transparent 1.2px);
  background-size: 18px 18px;
  pointer-events: none;
}
.hero-inner {
  position: relative;
  z-index: 1;
}
.logo-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-bottom: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.16);
  border: 2px solid rgba(255, 255, 255, 0.45);
  font-size: 30px;
  font-weight: 800;
  color: #fff;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.18);
}
.hero h1 {
  margin: 0;
  font-family: 'Source Han Serif SC', 'Noto Serif SC', 'Songti SC', Georgia, serif;
  font-size: 2.5rem;
  letter-spacing: 0.12em;
  font-weight: 800;
  color: #fff;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}
.slogan {
  margin: 12px 0 0;
  font-size: var(--xct-fs-body, 1rem);
  font-weight: 600;
  color: rgba(255, 255, 255, 0.95);
}
.sub {
  margin: 8px 0 0;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: rgba(255, 255, 255, 0.85);
}
.hero-curve {
  position: absolute;
  left: -10%;
  right: -10%;
  bottom: -1px;
  height: 40px;
  background: var(--xct-bg, #f3f6f1);
  border-radius: 50% 50% 0 0 / 100% 100% 0 0;
}

/* 表单白卡：上提压住 Hero 弧形，形成卡片层次 */
.form-card {
  position: relative;
  z-index: 2;
  margin: -32px 16px 0;
  padding: 20px 16px 16px;
  background: var(--xct-card-bg, #fff);
  border: var(--xct-card-border, 0 solid transparent);
  border-radius: var(--xct-radius, 14px);
  box-shadow: var(--xct-card-shadow-lg, 0 6px 18px rgba(27, 94, 32, 0.1));
}
.form-title {
  margin: 0 0 12px;
  font-size: var(--xct-fs-title, 1.25rem);
  color: var(--xct-green-deep, #1b5e20);
  text-align: center;
}
/* 去掉默认分组底色，字段之间只留细分隔线 */
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
  font-size: 1.125rem !important;
}
</style>
