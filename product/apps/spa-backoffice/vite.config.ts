import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const platformTarget = process.env.VITE_PLATFORM_PROXY_TARGET ?? "http://127.0.0.1:8081";
const keycloakTarget = process.env.VITE_KEYCLOAK_PROXY_TARGET ?? "http://127.0.0.1:18080";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api/": {
        target: platformTarget,
        changeOrigin: true,
      },
      "/realms": {
        target: keycloakTarget,
        changeOrigin: true,
      },
    },
  },
});
