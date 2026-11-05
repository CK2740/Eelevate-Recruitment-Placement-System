import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const projectDir = fileURLToPath(new URL(".", import.meta.url));

export default defineConfig({
  plugins: [react()],
  base: "./",
  server: {
    proxy: {
      "/api": {
        target: process.env.BACKEND_URL || "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: resolve(projectDir, "../src/main/resources/static"),
    emptyOutDir: true,
  },
});
