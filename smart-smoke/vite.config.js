import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    // 允许樱花 FRP 节点携带公网 Host 访问开发服务器。
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      // 兼容原烟感模块尚未统一加 /api 前缀的接口。
      '/latest': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/history': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/device/status': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  }
})
