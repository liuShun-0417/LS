const { defineConfig } = require('@vue/cli-service')
const backend = process.env.VUE_APP_BACKEND_TARGET || 'http://localhost:8080'
module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    port: 8081,
    proxy: {
      '/api': { target: backend, changeOrigin: true, timeout: 240000, proxyTimeout: 240000 },
      '/ws/chat': {
        target: backend, ws: true, changeOrigin: true,
        headers: { Origin: new URL(backend).origin }
      }
    }
  }
})