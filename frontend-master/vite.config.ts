import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/produtos': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/pedidos': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('error', () => {
            // Silencia ECONNRESET quando o backend REST (:8080) opera via HTTP polling sem endpoint /ws
          });
        },
      },
    },
  },
});
