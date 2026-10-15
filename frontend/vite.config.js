import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// La compilación queda dentro de target/classes/static: Spring Boot la sirve y entra en el jar.
// En desarrollo (npm run dev) Vite atiende en :5173 y reenvía /api a la aplicación en :8080.
export default defineConfig({
  plugins: [react()],
  build: { outDir: '../target/classes/static', emptyOutDir: true },
  server: { proxy: { '/api': 'http://localhost:8080' } },
});
