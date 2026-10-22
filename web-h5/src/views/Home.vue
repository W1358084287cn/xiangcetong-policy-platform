<template>
  <div class="home" @scroll="handleScroll">
    <!-- Topbar: Glassmorphism -->
    <header class="topbar" :class="{ 'scrolled': isScrolled }">
      <div class="brand">
        <div class="logo-orb" aria-hidden="true"></div>
        <span class="brand-text">乡策通</span>
      </div>
      <div class="location-pill">
        <van-icon name="location-o" />
        <span>{{ townLabel }}</span>
      </div>
    </header>

    <main class="content-wrapper">
      <!-- Hero Section -->
      <section class="hero stagger-item">
        <h1 class="hero-title">
          风吹麦浪，<br/>
          <span class="text-gradient">惠农万家</span>
        </h1>
        <p class="hero-subtitle">智能匹配，让补贴精准到户</p>
      </section>

      <!-- Bento Grid: Main Actions & Stats -->
      <section class="bento-grid stagger-item" style="--stagger-idx: 1;">
        <button class="bento-card match-card" @click="onMatchClick">
          <div class="match-bg-pattern"></div>
          <div class="match-content">
            <div class="icon-ring">
              <van-icon name="coupon-o" size="28" />
            </div>
            <div class="text">
              <h2>一键匹配补贴</h2>
              <p>基于您的资料智能推荐</p>
            </div>
          </div>
          <van-icon name="arrow" class="match-arrow" />
        </button>

        <div class="stats-row">
          <div class="bento-card stat-card" @click="onMatchClick">
            <span class="stat-num">{{ stats.matched }}</span>
            <span class="stat-label">适用政策</span>
          </div>
          <div class="bento-card stat-card expiring" @click="goExpiring">
            <span class="stat-num warn-num">{{ stats.expiring }}</span>
            <span class="stat-label">即将到期</span>
          </div>
        </div>
      </section>

      <NoticeMarquee class="stagger-item notice-spacing" style="--stagger-idx: 2;" />

      <!-- Quick Actions -->
      <section class="quick-actions stagger-item" style="--stagger-idx: 3;">
        <h3 class="section-title">常用功能</h3>
        <div class="action-grid">
          <button class="action-btn" @click="goApplies">
            <div class="action-icon wheat">
              <van-icon name="orders-o" size="24" />
            </div>
            <span>申报进度</span>
          </button>
          <button class="action-btn" @click="goWizard">
            <div class="action-icon grass">
              <van-icon name="contact" size="24" />
            </div>
            <span>完善资料</span>
          </button>
        </div>
      </section>

      <!-- Profile Progress -->
      <section class="profile-card stagger-item" style="--stagger-idx: 4;" @click="goWizard">
        <div class="profile-header">
          <div class="profile-title">
            <h3>资料完善度</h3>
            <span class="pct-text">{{ progress.percent }}%</span>
          </div>
          <van-progress 
            :percentage="progress.percent" 
            :show-pivot="false"
            color="linear-gradient(to right, #f59e0b, #65a30d)" 
            track-color="#fef3c7" 
            stroke-width="6" 
          />
        </div>
        <div class="profile-footer">
          <span v-if="progressReady" class="status-text success">资料已完善，可精准匹配</span>
          <span v-else class="status-text warning">还差 {{ progress.total - progress.done }} 项未填，建议补齐</span>
          <van-icon name="arrow" />
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { fetchMatchedPolicies } from '@/api/policy'
import NoticeMarquee from '@/components/NoticeMarquee.vue'
import { getUser } from '@/utils/auth'
import {
  fetchAndCheckProfile,
  missingLabel,
} from '@/utils/profileGate'

const router = useRouter()
const stats = reactive({ matched: '—', expiring: '—' })
const townLabel = ref('本镇')
const profile = ref(null)
const progress = reactive({ done: 0, total: 7, percent: 0 })
const isScrolled = ref(false)

const progressReady = computed(() => progress.done >= progress.total)

const handleScroll = (e) => {
  isScrolled.value = e.target.scrollTop > 10
}

function goWizard() {
  router.push({ path: '/mine', query: { edit: '1' } })
}

function goApplies() {
  router.push('/mine/applies')
}

async function onMatchClick() {
  const gate = await fetchAndCheckProfile()
  applyGate(gate)
  if (!gate.ready) {
    showToast(`还差${missingLabel(gate.missing)}，先补齐再匹配`)
    router.push({ path: '/mine', query: { edit: '1', from: 'match' } })
    return
  }
  router.push({ path: '/policies' })
}

async function goExpiring() {
  const gate = await fetchAndCheckProfile()
  applyGate(gate)
  if (!gate.ready) {
    showToast(`还差${missingLabel(gate.missing)}，先补齐再匹配`)
    router.push({ path: '/mine', query: { edit: '1', from: 'match' } })
    return
  }
  router.push({ path: '/policies', query: { expiring: '1' } })
}

function applyGate(gate) {
  profile.value = gate.profile
  progress.done = gate.progress.done
  progress.total = gate.progress.total
  progress.percent = gate.progress.percent
  townLabel.value = gate.profile?.townName || getUser()?.townCode || '本镇'
}

async function loadProfileCard() {
  const gate = await fetchAndCheckProfile()
  applyGate(gate)
}

async function loadStats() {
  try {
    const data = await fetchMatchedPolicies({ pageNum: 1, pageSize: 50 })
    const list = data?.list || []
    stats.matched = String(list.length)
    const soon = list.filter((p) => {
      if (!p.validTo) return false
      const end = new Date(p.validTo)
      const days = (end - Date.now()) / (24 * 3600 * 1000)
      return days >= 0 && days <= 90
    })
    stats.expiring = String(soon.length)
  } catch {
    stats.matched = '—'
    stats.expiring = '—'
  }
}

onMounted(() => {
  loadProfileCard()
  loadStats()
})
</script>

<style scoped>
/* ==========================================================================
   Impeccable UI: Golden Wheat & Prairie Theme
   ========================================================================== */

@keyframes blurReveal {
  0% { opacity: 0; filter: blur(12px); transform: translateY(20px); }
  100% { opacity: 1; filter: blur(0); transform: translateY(0); }
}

@keyframes breeze {
  0% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(-10px, 15px) scale(1.05); }
  100% { transform: translate(0, 0) scale(1); }
}

.home {
  height: 100vh;
  overflow-y: auto;
  overflow-x: hidden;
  /* Soft gradient from warm sunlight to fresh grass */
  background: linear-gradient(170deg, #fffbeb 0%, #fef3c7 40%, #f0fdf4 100%);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  color: #451a03;
  position: relative;
}

/* Immersive Atmospheric Lighting */
.atmosphere {
  position: fixed;
  pointer-events: none;
  z-index: 0;
  border-radius: 50%;
  filter: blur(80px);
  animation: breeze 12s ease-in-out infinite;
}

.glow-sun {
  top: -10%;
  right: -20%;
  width: 300px;
  height: 300px;
  background: rgba(251, 191, 36, 0.4);
}

.glow-grass {
  bottom: 0%;
  left: -20%;
  width: 400px;
  height: 400px;
  background: rgba(134, 239, 172, 0.25);
  animation-delay: -6s;
}

.content-wrapper {
  position: relative;
  z-index: 1;
  padding: 80px 16px 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stagger-item {
  animation: blurReveal 0.8s cubic-bezier(0.16, 1, 0.3, 1) both;
  animation-delay: calc(var(--stagger-idx, 0) * 0.1s);
}

/* Glassmorphism Topbar */
.topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  z-index: 50;
  background: transparent;
  transition: all 0.3s ease;
}

.topbar.scrolled {
  background: rgba(255, 253, 245, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 4px 24px rgba(217, 119, 6, 0.06);
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.logo-orb {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  /* Golden wheat orb */
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  box-shadow: 0 2px 12px rgba(217, 119, 6, 0.3);
}

.brand-text {
  font-size: 1.125rem;
  font-weight: 800;
  color: #78350f;
  letter-spacing: 0.5px;
}

.location-pill {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: rgba(254, 243, 199, 0.6);
  border: 1px solid rgba(251, 191, 36, 0.3);
  border-radius: 99px;
  font-size: 0.875rem;
  font-weight: 600;
  color: #92400e;
  transition: background 0.3s;
}
.topbar.scrolled .location-pill {
  background: rgba(254, 243, 199, 0.9);
}

/* Hero Section */
.hero {
  padding: 12px 4px;
}

.hero-title {
  font-size: 2.25rem;
  line-height: 1.25;
  font-weight: 800;
  margin: 0;
  color: #451a03;
  letter-spacing: -0.5px;
}

.text-gradient {
  /* Rich amber to lively green */
  background: linear-gradient(135deg, #d97706 0%, #65a30d 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-subtitle {
  margin: 8px 0 0;
  font-size: 1rem;
  color: #92400e;
  font-weight: 500;
}

/* Bento Grid */
.bento-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bento-card {
  border: none;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 24px;
  box-shadow: 0 8px 32px rgba(217, 119, 6, 0.06);
  text-align: left;
  cursor: pointer;
  transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.4s;
  will-change: transform;
}

.bento-card:active {
  transform: scale(0.96);
}

.match-card {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px;
  /* Immersive golden wheat gradient */
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 60%, #b45309 100%);
  border: none;
  color: #fff;
  box-shadow: 0 12px 32px rgba(217, 119, 6, 0.25);
}

.match-bg-pattern {
  position: absolute;
  top: -50%;
  right: -20%;
  width: 240px;
  height: 240px;
  background: radial-gradient(circle, rgba(255,255,255,0.2) 0%, transparent 70%);
  border-radius: 50%;
  pointer-events: none;
}

.match-content {
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.icon-ring {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(8px);
  box-shadow: inset 0 2px 4px rgba(255, 255, 255, 0.3);
}

.match-content h2 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 800;
  letter-spacing: 0.5px;
  text-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.match-content p {
  margin: 4px 0 0;
  font-size: 0.875rem;
  color: rgba(255, 255, 255, 0.95);
}

.match-arrow {
  font-size: 1.25rem;
  opacity: 0.9;
  position: relative;
  z-index: 1;
}

.stats-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.stat-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}

.stat-num {
  font-size: 1.875rem;
  font-weight: 800;
  color: #78350f;
  line-height: 1;
}

.warn-num {
  color: #ea580c;
}

.stat-label {
  font-size: 0.875rem;
  color: #92400e;
  font-weight: 600;
}

.expiring {
  background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%);
  border: 1px solid rgba(253, 186, 116, 0.4);
}

/* Quick Actions */
.section-title {
  font-size: 1.125rem;
  font-weight: 800;
  margin: 0 0 12px 4px;
  color: #78350f;
}

.action-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 20px;
  box-shadow: 0 8px 24px rgba(217, 119, 6, 0.04);
  cursor: pointer;
  transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.action-btn:active {
  transform: scale(0.96);
}

.action-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-icon.wheat { background: #fef3c7; color: #d97706; }
.action-icon.grass { background: #dcfce7; color: #15803d; }

.action-btn span {
  font-size: 1rem;
  font-weight: 700;
  color: #78350f;
}

/* Profile Card */
.profile-card {
  padding: 20px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 24px;
  box-shadow: 0 8px 32px rgba(217, 119, 6, 0.05);
  cursor: pointer;
  transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.profile-card:active {
  transform: scale(0.98);
}

.profile-header {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.profile-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.profile-title h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 800;
  color: #78350f;
}

.pct-text {
  font-weight: 800;
  color: #d97706;
  font-size: 1rem;
}

.profile-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 16px;
  border-top: 1px solid rgba(217, 119, 6, 0.1);
}

.status-text {
  font-size: 0.875rem;
  font-weight: 600;
}
.status-text.success { color: #65a30d; }
.status-text.warning { color: #ea580c; }

.profile-footer .van-icon {
  color: #d97706;
  opacity: 0.6;
}

.notice-spacing {
  margin: 4px 0;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(217, 119, 6, 0.05);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(8px);
}
</style>
