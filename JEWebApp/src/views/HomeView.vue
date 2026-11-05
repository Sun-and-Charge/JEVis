<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useNavigationStore } from '../stores/navigation'

const { t } = useI18n()
const nav = useNavigationStore()
</script>

<template>
  <section aria-labelledby="home-title">
    <h1 id="home-title">{{ t('home.title') }}</h1>
    <p>{{ t('home.text') }}</p>
    <p v-if="nav.data" class="muted">{{ t('home.count', nav.counts) }}</p>
  </section>
</template>
