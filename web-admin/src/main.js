import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)
app.use(router)
// 使用 Element Plus 中文语言包，分页/弹窗等组件文案为中文
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
