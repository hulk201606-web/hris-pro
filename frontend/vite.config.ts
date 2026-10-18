import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 智云 HR 前端构建配置
// - SPA 单工程（管理端 + 员工自助端同一应用，路由分域）
// - 开发环境通过 /api 代理到网关，生产由网关/Ingress 统一路由
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    sourcemap: false,
    chunkSizeWarningLimit: 1200,
  },
});
