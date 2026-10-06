import { defineConfig } from "vite";
import { cpSync, readdirSync, statSync } from "node:fs";
import { dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));

/** 收集所有页面作为构建入口：前台、运营后台、小程序原型三套页面都在同一个工程里 */
function collectHtml(dir, out = []) {
  for (const name of readdirSync(dir)) {
    if (name === "node_modules" || name === "dist" || name === "assets") continue;
    const full = resolve(dir, name);
    if (statSync(full).isDirectory()) {
      collectHtml(full, out);
    } else if (name.endsWith(".html")) {
      out.push(full);
    }
  }
  return out;
}

const input = {};
for (const file of collectHtml(root)) {
  input[relative(root, file).replace(/\\/g, "/")] = file;
}

/**
 * 页面用 <script src="assets/js/x.js"> 这种「非模块」引用，Vite 不会把它们打进产物，
 * 也不会复制 assets/img、assets/js。这里在构建结束后把 assets 原样拷进 dist。
 */
function copyStaticAssets() {
  return {
    name: "baiyishop-copy-static-assets",
    apply: "build",
    closeBundle() {
      const from = resolve(root, "assets");
      const to = resolve(root, "dist", "assets");
      for (const name of readdirSync(from)) {
        cpSync(resolve(from, name), resolve(to, name), { recursive: true, force: true });
      }
    }
  };
}

export default defineConfig({
  root,
  plugins: [copyStaticAssets()],
  server: {
    port: 5173,
    // 前端统一以 /api 前缀请求，由开发服务器转发到网关（http://localhost:8080），无需后端开 CORS
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
    rollupOptions: { input }
  }
});
