import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

export default defineConfig({
  base: process.env.VITE_BASE_URL || (process.env.GITHUB_ACTIONS || process.env.DEPLOY_TARGET === 'gh-pages' ? '/memoryvault/' : '/'),
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
  },
})
