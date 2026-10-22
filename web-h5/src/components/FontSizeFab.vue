<template>
  <!--
    适老设置浮动按钮（左下）：默认只显示「适老」一颗；展开后纵向排列字号/高对比。
    支持拖动（触摸 + 鼠标），避免遮挡内容。
  -->
  <div
    class="a11y-root"
    :class="{ open }"
    :style="{ left: posX + 'px', bottom: posY + 'px' }"
    role="group"
    aria-label="适老设置"
    @mousedown="onDragStart"
    @mouseup="onDragEnd"
    @mouseleave="onDragEnd"
    @touchstart="onDragStart"
    @touchend="onDragEnd"
  >
    <div v-if="open" class="a11y-mask" aria-hidden="true" @click.stop="open = false" />
    <div class="a11y-fabs">
      <div v-if="open" class="a11y-panel">
        <button type="button" class="chip font-chip" aria-label="调节字号" @click.stop="onFont">
          <span class="mark">A|a</span>
          <span>{{ fontLabel }}</span>
        </button>
        <button
          type="button"
          class="chip contrast-chip"
          :class="{ on: contrastOn }"
          :aria-pressed="contrastOn"
          aria-label="高对比主题"
          @click.stop="onContrast"
        >
          {{ contrastOn ? '高对比已开' : '高对比' }}
        </button>
      </div>
      <button
        type="button"
        class="chip toggle-chip"
        :aria-expanded="open"
        :aria-label="open ? '收起适老设置' : '打开适老设置'"
        @click.stop="open = !open"
        @mousedown.stop
      >
        <span class="mark">Aa</span>
        <span>{{ open ? '收起' : '适老' }}</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { showToast } from 'vant'
import {
  FONT_LEVELS,
  applyContrast,
  applyFontLevel,
  loadContrastHigh,
  loadFontLevel,
  nextFontLevel,
} from '@/utils/a11yPrefs'

const idx = ref(0)
const contrastOn = ref(false)
const open = ref(false)
const fontLabel = computed(() => FONT_LEVELS[idx.value].label)

// ===== 拖动状态 =====
const posX = ref(12)
const posY = ref(100)

let dragging = false
let dragStartX = 0
let dragStartY = 0
let dragStartPosX = 0
let dragStartPosY = 0

function onFont() {
  const next = nextFontLevel(FONT_LEVELS[idx.value].key)
  idx.value = FONT_LEVELS.findIndex((l) => l.key === next.key)
  applyFontLevel(next)
  showToast(`字号：${next.label}`)
  open.value = false
}

function onContrast() {
  contrastOn.value = !contrastOn.value
  applyContrast(contrastOn.value)
  showToast(contrastOn.value ? '已开启高对比' : '已关闭高对比')
  open.value = false
}

function onDragStart(e) {
  // 如果点击的是展开面板里的按钮，不启动拖动
  if (e.target.closest('.a11y-panel')) return
  e.preventDefault()
  dragging = true
  const point = e.touches ? e.touches[0] : e
  dragStartX = point.clientX
  dragStartY = point.clientY
  dragStartPosX = posX.value
  dragStartPosY = posY.value
}

function onDragMove(e) {
  if (!dragging) return
  e.preventDefault()
  const point = e.touches ? e.touches[0] : e
  const dx = point.clientX - dragStartX
  const dy = point.clientY - dragStartY

  // bottom 值 = 起始 bottom - 向下移动量（屏幕坐标 Y 越大，bottom 越小）
  const newLeft = Math.max(0, Math.min(dragStartPosX + dx, window.innerWidth - 80))
  const newBottom = Math.max(60, Math.min(dragStartPosY - dy, window.innerHeight - 200))

  posX.value = newLeft
  posY.value = newBottom
}

function onDragEnd() {
  dragging = false
}

onMounted(() => {
  const level = loadFontLevel()
  idx.value = FONT_LEVELS.findIndex((l) => l.key === level.key)
  applyFontLevel(level)
  contrastOn.value = loadContrastHigh()
  applyContrast(contrastOn.value)

  // 全局监听移动
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
.a11y-root {
  position: fixed;
  z-index: 200;
  user-select: none;
  touch-action: none;
}
.a11y-mask {
  position: fixed;
  inset: 0;
  background: transparent;
}
.a11y-fabs {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}
.a11y-panel {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}
.chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: var(--xct-hit-min, 48px);
  padding: 10px 16px;
  border: none;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.2);
  white-space: nowrap;
  flex-shrink: 0;
}
.toggle-chip {
  background: linear-gradient(135deg, #ffd54f, #ffb300);
  color: #4e342e;
}
.font-chip {
  background: #fff8e1;
  color: #4e342e;
  border: 2px solid #ffb300;
}
.contrast-chip {
  background: #fff;
  color: #111;
  border: 2px solid #111;
}
.contrast-chip.on {
  background: #000;
  color: #ffeb3b;
  border-color: #ffeb3b;
}
.mark {
  font-family: Georgia, 'Times New Roman', serif;
  letter-spacing: 0.02em;
  font-size: 17px;
}
</style>