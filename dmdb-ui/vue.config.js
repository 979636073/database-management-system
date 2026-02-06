const MonacoWebpackPlugin = require('monaco-editor-webpack-plugin');

module.exports = {
  // 关闭保存时的代码格式检查
  lintOnSave: false,
  configureWebpack: {
    plugins: [
      new MonacoWebpackPlugin({
        // 按需加载语言，减少打包体积
        languages: ['sql', 'json', 'javascript', 'xml'] 
      })
    ]
  }
}