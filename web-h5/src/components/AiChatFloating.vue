<!--
  AI 悬浮按钮 → 点击跳转到 AI 管家全屏页 /butler
  支持触摸 + 鼠标拖动，避免遮挡内容
-->
<template>
  <div
    class="ai-fab-root"
    :style="{ right: posRight + 'px', bottom: posBottom + 'px' }"
    @mousedown="onDragStart"
    @mouseup="onDragEnd"
    @mouseleave="onDragEnd"
    @touchstart="onDragStart"
    @touchend="onDragEnd"
  >
    <div class="ai-fab" @click.stop="onFabClick">
      <van-icon name="chat-o" size="24" />
      <span class="ai-fab-label">AI</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// ===== 拖动状态 =====
const posRight = ref(16)
const posBottom = ref(80)

let dragging = false
let hasMoved = false
let dragStartX = 0
let dragStartY = 0
let dragStartRight = 0
let dragStartBottom = 0
const DRAG_THRESHOLD = 4

function onFabClick() {
  if (hasMoved) {
    hasMoved = false
    return
  }
  router.push('/butler')
}

function onDragStart(e) {
  // 2026-10-17 修复：此处不能 preventDefault —— 触摸设备上阻止 touchstart 默认行为
  // 会连带吞掉后续合成的 click 事件，导致「点击悬浮球无反应」。
  // 防误触滚动已由 .ai-fab-root 的 touch-action: none 保证。
  dragging = true
  hasMoved = false
  const point = e.touches ? e.touches[0] : e
  dragStartX = point.clientX
  dragStartY = point.clientY
  dragStartRight = posRight.value
  dragStartBottom = posBottom.value
}

function onDragMove(e) {
  if (!dragging) return
  e.preventDefault()
  const point = e.touches ? e.touches[0] : e
  const dx = point.clientX - dragStartX
  const dy = point.clientY - dragStartY

  if (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
    hasMoved = true
  }

  // right 值 = 起始 right + 向左移动量（屏幕坐标 X 越大，right 越小）
  const newRight = Math.max(0, Math.min(dragStartRight - dx, window.innerWidth - 80))
  // bottom 值 = 起始 bottom - 向下移动量（屏幕坐标 Y 越大，bottom 越小）
  const newBottom = Math.max(60, Math.min(dragStartBottom - dy, window.innerHeight - 200))

  posRight.value = newRight
  posBottom.value = newBottom
}

function onDragEnd() {
  dragging = false
}

onMounted(() => {
  window.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', onDragEnd)
  window.addEventListener('touchmove', onDragMove, { passive: false })
  window.addEventListener('touchend', onDragEnd)
})

onUnmounted(() => {
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragEnd)
  window.removeEventListener('touchmove', onDragMove)
  window.removeEventListener('touchend', onDragEnd)
})
</script>

<style scoped>
.ai-fab-root {
  position: fixed;
  z-index: 999;
  user-select: none;
  touch-action: none;
}

.ai-fab {
  width: 56px;
  height: 56px;
  border-radius: 28px;
  background: linear-gradient(135deg, #1B5E20, #388E3C);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(27, 94, 32, 0.35);
  cursor: pointer;
  transition: transform 0.2s;
}

.ai-fab:active {
  transform: scale(0.92);
}

.ai-fab-label {
  font-size: 10px;
  font-weight: 700;
  margin-top: -2px;
}
</style>