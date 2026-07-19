import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from "./router/index";
import 'ant-design-vue/dist/reset.css';
import './assets/css/admin-responsive.css';
const app = createApp(App);
import pinia from './stores/index';
app.use(pinia).use(router).mount('#app');
