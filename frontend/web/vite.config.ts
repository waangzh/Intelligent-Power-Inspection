import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: 'http://112.124.49.152:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://112.124.49.152:8080',
        ws: true,
      },
    },
  },
})
