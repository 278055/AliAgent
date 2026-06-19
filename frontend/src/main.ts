import { createApp } from 'vue'
import App from './App.vue'
import './assets/main.css'

const app = createApp(App)

// Apply saved theme
const theme = localStorage.getItem('theme') || 'light'
document.documentElement.setAttribute('data-theme', theme)

app.mount('#app')
