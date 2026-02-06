import Vue from 'vue';
import ElementUI from 'element-ui';
import 'element-ui/lib/theme-chalk/index.css';
import App from './App.vue';

// 设置 ElementUI 尺寸为 small，更适合数据密集型应用
Vue.use(ElementUI, { size: 'small' });
Vue.config.productionTip = false;

new Vue({
  render: h => h(App),
}).$mount('#app');