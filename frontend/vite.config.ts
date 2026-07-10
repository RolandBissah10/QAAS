import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,          // listen on 0.0.0.0 so network devices can reach it
    proxy: {
      "/api": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
    },
  },
});