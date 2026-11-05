/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { i18n } from './i18n'
import { router } from './router'
import './styles/main.css'

document.documentElement.lang = i18n.global.locale.value
createApp(App).use(createPinia()).use(i18n).use(router).mount('#app')
