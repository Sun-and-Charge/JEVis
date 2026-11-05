<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import LanguageSwitch from './LanguageSwitch.vue'

defineProps<{ title: string }>()
</script>

<template>
  <div class="auth-page">
    <header class="auth-top">
      <img src="/suc_logo_dark.png" alt="Sun and Charge" class="logo" />
      <LanguageSwitch />
    </header>
    <main class="auth-main">
      <section class="card" aria-labelledby="auth-title">
        <h1 id="auth-title">{{ title }}</h1>
        <slot />
      </section>
    </main>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, var(--blueberry) 0 220px, var(--bg) 220px);
}

.auth-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.25rem;
  color: #fff;
}

.logo {
  height: 40px;
}

.auth-main {
  flex: 1;
  display: flex;
  justify-content: center;
  padding: 1.5rem 1rem 3rem;
}

.card {
  width: 100%;
  max-width: 400px;
  align-self: flex-start;
  background: var(--surface);
  border-radius: 10px;
  border-top: 4px solid var(--sun);
  box-shadow: 0 6px 24px rgba(26, 42, 69, 0.15);
  padding: 1.75rem 1.5rem;
}
</style>
