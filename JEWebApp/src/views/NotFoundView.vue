<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <section>
    <h1>{{ t('notFound.title') }}</h1>
    <RouterLink :to="{ name: 'home' }">{{ t('notFound.home') }}</RouterLink>
  </section>
</template>
