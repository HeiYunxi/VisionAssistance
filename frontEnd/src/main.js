import'./assets/main.scss'
import { createApp } from 'vue'
import App from './App.vue'

import router from './router/index'
import commonJs from './assets/js/common'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import locale from 'element-plus/dist/locale/zh-cn'
import * as Icons from '@element-plus/icons-vue'


const app = createApp(App)
app.use(router)

for (let i in Icons) {
    app.component(i, Icons[i])
}
app.use(ElementPlus, { locale })

app.config.globalProperties.$baseUrl = 'http://127.0.0.1:8080/'
app.config.globalProperties.$commonJs = commonJs

app.mount('#app')