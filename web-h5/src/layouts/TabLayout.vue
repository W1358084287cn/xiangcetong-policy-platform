<!--
  主框架布局：上方路由内容 + 底部 4 Tab（首页/匹配/申报/我的）。
  激活 Tab 由路由路径推导；底栏随字号档自动抬高，避免遮挡内容。
-->
<template>
  <div class="tab-layout">
    <div class="tab-body">
      <router-view v-slot="{ Component }">
        <keep-alive>
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </div>
    <!-- AI 对话悬浮按钮 -->
    <AiChatFloating />
    <!-- 底部：首页 / 匹配 / 申报 / AI / 我的 -->
    <van-tabbar v-model="active" route safe-area-inset-bottom>
      <van-tabbar-item replace to="/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/policies" icon="orders-o">匹配</van-tabbar-item>
      <van-tabbar-item replace to="/apply-center" icon="edit">申报</van-tabbar-item>
      <van-tabbar-item replace to="/butler" icon="chat-o">AI 管家</van-tabbar-item>
      <van-tabbar-item replace to="/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AiChatFloating from '@/components/AiChatFloating.vue'

defineOptions({ name: 'TabLayout' })

const route = useRoute()
const active = ref(0)

watch(
  () => route.path,
  (path) => {
    // 索引：首页=0 匹配=1 申报=2 AI管家=3 我的=4
    if (path.startsWith('/policies')) active.value = 1
    else if (path.startsWith('/apply') || path.startsWith('/mine/applies')) active.value = 2
    else if (path.startsWith('/butler')) active.value = 3
    else if (path.startsWith('/mine')) active.value = 4
    else active.value = 0
  },
  { immediate: true }
)
</script>

<style scoped>
.tab-layout {
  min-height: 100vh;
  background: var(--xct-bg, #f3f6f1);
  /* 随字号档抬高 Tab 高度，避免内容被挡住 */
  padding-bottom: calc(var(--van-tabbar-height, 56px) + env(safe-area-inset-bottom));
}
.tab-body {
  min-height: calc(100vh - var(--van-tabbar-height, 56px));
}
/* 底栏顶部分隔：柔和阴影托起，和内容区拉开层次 */
.tab-layout :deep(.van-tabbar) {
  box-shadow: 0 -2px 12px rgba(27, 94, 32, 0.08);
  border-top: 1px solid var(--xct-line, #eef2ec);
}
/* 激活项加粗，老人更容易辨认当前在哪 */
.tab-layout :deep(.van-tabbar-item--active) {
  font-weight: 700;
}
</style>