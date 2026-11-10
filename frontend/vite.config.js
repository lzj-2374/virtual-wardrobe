import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    open: true,
    proxy: {
      // 非 Mock 模式下，将 /api 代理到后端 Spring Boot 服务
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 若后端接口本身带 /api 前缀则不需要 rewrite
        // rewrite: (p) => p.replace(/^\/api/, '')
      }
    }
  }
})
