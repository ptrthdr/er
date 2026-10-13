import { defineConfig } from "vite";
export default defineConfig({build: {
  lib: {entry: "src/execution-editor.js", formats: ["es"], fileName: "execution-editor", cssFileName: "style"},
  rollupOptions: {external: id => id === "codemirror" || id.startsWith("@codemirror/")}
}});
