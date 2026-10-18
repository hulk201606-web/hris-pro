import { createApp } from 'vue';
import { createPinia } from 'pinia';
import Antd from 'ant-design-vue';
import App from './App.vue';
import router from './router';
import { vPerm } from './directives/perm';
import 'ant-design-vue/dist/reset.css';
import './styles/design-tokens.css';
import './styles/global.css';

const app = createApp(App);

app.use(createPinia());
app.use(router);
app.use(Antd);
app.directive('perm', vPerm);

app.mount('#app');
