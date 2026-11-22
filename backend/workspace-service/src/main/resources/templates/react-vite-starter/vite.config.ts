import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // previews are opened through the HighVibe workspace (iframe / preview proxy)
    host: true,
    allowedHosts: true,
  },
})
