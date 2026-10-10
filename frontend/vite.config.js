import { defineConfig } from "vite";

const backendTarget = process.env.CODELOBBY_BACKEND_URL || "http://localhost:8081";

export default defineConfig({
  server: {
    host: "127.0.0.1",
    port: 5174,
    strictPort: true,
    proxy: {
      "/api": {
        target: backendTarget,
        changeOrigin: true,
      },
    },
  },
});
