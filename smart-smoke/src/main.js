import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import request from './utils/request'
import { reveal, tilt, countTo, initMotion } from './directives/motion'

const app = createApp(App)
app.use(ElementPlus)
app.use(router)
app.directive('reveal', reveal)
app.directive('tilt', tilt)
app.directive('count-to', countTo)
app.config.globalProperties.$http = request
app.mount('#app')
initMotion()
