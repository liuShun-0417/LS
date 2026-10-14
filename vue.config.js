const { defineConfig } = require('@vue/cli-service')

// vue.config.js：publicPath、开发端口、代理（第 10 课）+ 中文路径兼容修复
module.exports = defineConfig({
  transpileDependencies: true,
  publicPath: '/',
  devServer: {
    port: 8080,
    // 仅在对接真实后端时生效；课堂演示保留 Mock 时不会走到这里
    proxy: {
      '/api': {
        target: 'http://localhost:3000',
        changeOrigin: true,
        pathRewrite: { '^/api': '' }
      }
    }
  },
  chainWebpack: (config) => {
    // 修复：Conflict: Multiple assets emit different content to the same filename index.html
    // 原因：项目路径含中文/空格时，@vue/cli-service 用“绝对路径”排除 public/index.html 的规则失效，
    // 导致 copy-webpack-plugin 把 public/index.html 拷进输出目录，与 html-webpack-plugin 生成的 index.html 冲突。
    // 方案：补一条相对路径的忽略规则 '**/index.html'。
    config.plugin('copy').tap((args) => {
      const copyOptions = args[0]
      const patterns = copyOptions && copyOptions.patterns
      if (patterns && patterns[0]) {
        patterns[0].globOptions = patterns[0].globOptions || {}
        const ignore = patterns[0].globOptions.ignore || []
        if (!ignore.includes('**/index.html')) {
          patterns[0].globOptions.ignore = ignore.concat(['**/index.html'])
        }
      }
      return args
    })
  }
})
