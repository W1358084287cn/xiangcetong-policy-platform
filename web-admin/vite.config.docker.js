import { defineConfig, mergeConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 容器化部署专用构建配置：
// 管理端挂载在 /admin/ 子路径下，必须显式指定 base，
// 否则 CLI 的 --base 参数在 rolldown-vite 下不生效，
// 会把构建机的工作目录写进产物（历史 BUG：/WorkBuddyAI/.../admin/）。
export default defineConfig({
  plugins: [vue()],
  base: '/admin/',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: 'dist-unified',
    emptyOutDir: true,
  },
})
