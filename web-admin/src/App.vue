<template>
  <div class="app-container">
    <!-- Global Atmospheric background elements -->
    <div class="atmosphere glow-sun"></div>
    <div class="atmosphere glow-grass"></div>
    <router-view />
  </div>
</template>

<script setup>
// 管理端根组件：只负责挂载路由页面
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  position: relative;
  overflow-x: hidden;
}
/* Immersive Atmospheric Lighting */
@keyframes breeze {
  0% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(-10px, 15px) scale(1.05); }
  100% { transform: translate(0, 0) scale(1); }
}
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
</style>
