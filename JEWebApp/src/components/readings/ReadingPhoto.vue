<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { readingPhotoPath } from '../../api/readings'
import { useAuthImage } from '../../dashboard/authImage'
import { useAuthStore } from '../../stores/auth'

/* The photo of a past reading: a small camera button; the image is loaded (with the Bearer token) when opened. */

const props = defineProps<{ meterId: number; timestamp: string; label: string }>()
const { t } = useI18n()
const auth = useAuthStore()

const open = ref(false)
const path = computed(() => (open.value ? readingPhotoPath(props.meterId, props.timestamp) : null))
const { url, state } = useAuthImage(auth.http, path)
const uid = `rd-photo-${Math.random().toString(36).slice(2, 9)}`
</script>

<template>
  <span class="rd-photo">
    <button
      type="button"
      class="rd-photo-btn"
      :aria-expanded="open"
      :aria-controls="uid"
      :aria-label="t('readings.photo.show', { time: label })"
      :title="t('readings.photo.show', { time: label })"
      @click="open = !open"
    >
      <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" focusable="false">
        <path d="M4 8h3l2-3h6l2 3h3v11H4z M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
      </svg>
    </button>
    <span v-if="open" :id="uid" class="rd-photo-view">
      <img v-if="url" :src="url" :alt="t('readings.photo.alt', { time: label })" class="rd-photo-img" />
      <span v-else-if="state === 'loading'" class="muted" role="status">{{ t('registers.file.loading') }}</span>
      <span v-else-if="state === 'error'" class="muted">{{ t('readings.photo.loadError') }}</span>
    </span>
  </span>
</template>
