<!--
  首页滚动字幕（通知公告）。
  管理员在后台发布通知公告后，村民打开首页即可在统计卡下方看到一条左右滚动的公告字幕；
  点按字幕会从底部弹出弹窗，逐条查看每则通知的标题与正文。
  无通知时不渲染任何内容；接口拉取失败时静默处理，不额外打扰首页。
-->
<template>
  <div v-if="notices.length" class="notice-marquee">
    <van-notice-bar
      left-icon="volume-o"
      scrollable
      :delay="1"
      :text="marqueeText"
      class="notice-bar"
      @click="openDetail"
    />

    <!-- 2026-10-17 修复「弹窗被挡住/内容截断」：
         首页入场动画（blurReveal）结束后仍保留 filter/transform（fill-mode: both），
         CSS 规范里非 none 的 filter/transform 祖先会成为 fixed 元素的包含块，
         弹窗的 max-height:80% 因此按 40px 高的字幕条计算、只剩一条缝。
         teleport 到 body 后脱离动画容器，百分比高度与层级恢复正常。 -->
    <van-popup
      v-model:show="showDetail"
      teleport="body"
      position="bottom"
      round
      safe-area-inset-bottom
      class="notice-popup"
    >
      <div class="np-hd">
        <h3>通知公告</h3>
      </div>
      <div class="np-body">
        <article v-for="(n, i) in notices" :key="n.id || i" class="np-item">
          <h4 class="np-title">{{ n.title }}</h4>
          <p class="np-content">{{ n.content }}</p>
        </article>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getActiveNotices } from '@/api/notice'

/** 生效中的通知公告列表 */
const notices = ref([])
/** 是否打开通知明细弹窗 */
const showDetail = ref(false)

/** 把所有通知标题用「　·　」拼成一条，交给字幕横向滚动 */
const marqueeText = computed(() =>
  notices.value.map((n) => n.title).join('　·　')
)

/** 点按字幕：打开明细弹窗 */
function openDetail() {
  showDetail.value = true
}

onMounted(async () => {
  try {
    const data = await getActiveNotices()
    notices.value = Array.isArray(data) ? data : []
  } catch {
    // 静默：公告拉取失败不额外弹错误、不渲染字幕，首页保持整洁
    notices.value = []
  }
})
</script>

<style scoped>
.notice-marquee {
  margin: 16px 16px 0;
}

/* 字幕条：浅绿底 + 深绿字，贴合品牌绿色主题 */
.notice-bar {
  --van-notice-bar-background: var(--xct-green-soft, #e8f5e9);
  --van-notice-bar-text-color: var(--xct-green-deep, #1b5e20);
  --van-notice-bar-font-size: var(--xct-fs-body, 1rem);
  border-radius: var(--xct-radius-sm, 10px);
}

/* 通知明细弹窗：底部圆角，标题 + 正文逐条纵向排列 */
.notice-popup {
  max-height: 80%;
  display: flex;
  flex-direction: column;
}
.np-hd {
  padding: 20px 20px 4px;
}
.np-hd h3 {
  margin: 0;
  font-size: var(--xct-fs-title, 1.25rem);
  color: var(--xct-green-deep, #1b5e20);
}
.np-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 20px 24px;
}
.np-item {
  padding: 16px 0;
  border-bottom: 1px solid var(--xct-line, #eef2ec);
}
.np-item:last-child {
  border-bottom: none;
}
.np-title {
  margin: 0 0 8px;
  font-size: var(--xct-fs-body, 1rem);
  font-weight: 700;
  line-height: 1.4;
  color: var(--xct-color-text, #222);
}
.np-content {
  margin: 0;
  font-size: var(--xct-fs-tip, 0.9375rem);
  line-height: 1.75;
  color: var(--xct-color-muted, #5a5a5a);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
