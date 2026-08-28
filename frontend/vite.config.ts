import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  base: './',
  plugins: [react()],
  server: {
    port: Number(process.env.VITE_DEV_PORT ?? 35173),
    proxy: {
      '/api': process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:38080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test-setup.ts',
  },
})
