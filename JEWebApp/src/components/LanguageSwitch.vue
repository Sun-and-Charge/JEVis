<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { LOCALES, setLocale, type Locale } from '../i18n'

const { t, locale } = useI18n()
</script>

<template>
  <div class="lang" role="group" :aria-label="t('header.language')">
    <button
      v-for="l in LOCALES"
      :key="l"
      type="button"
      class="lang-btn"
      :class="{ active: locale === l }"
      :aria-pressed="locale === l"
      @click="setLocale(l as Locale)"
    >
      {{ l.toUpperCase() }}
    </button>
  </div>
</template>

<style scoped>
.lang {
  display: inline-flex;
  gap: 2px;
}

.lang-btn {
  font: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  padding: 0.25rem 0.5rem;
  border: 1px solid currentColor;
  border-radius: 4px;
  background: transparent;
  color: inherit;
  opacity: 0.7;
  cursor: pointer;
}

.lang-btn.active {
  opacity: 1;
  background: var(--sun);
  color: var(--blueberry);
  border-color: var(--sun);
}
</style>
