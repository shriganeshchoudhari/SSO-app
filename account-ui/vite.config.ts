import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5100,
    proxy: {
      '/admin': {
        target: 'http://localhost:7070',
        changeOrigin: true
      },
      '/auth': {
        target: 'http://localhost:7070',
        changeOrigin: true
      },
      '/account': {
        target: 'http://localhost:7070',
        changeOrigin: true
      }
    }
  }
})
