import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const platformTarget = process.env.VITE_PLATFORM_PROXY_TARGET ?? "http://127.0.0.1:8081";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": {
        target: platformTarget,
        changeOrigin: true,
      },
    },
  },
});
