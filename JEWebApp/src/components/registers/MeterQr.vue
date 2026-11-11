<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { meterQrPath } from '../../api/registers'
import { useAuthImage } from '../../dashboard/authImage'
import { useAuthStore } from '../../stores/auth'

/* QR code of a meter (SVG of the server, loaded with the Bearer token into an object URL, released on unmount). */

const props = defineProps<{ meterId: number; name: string }>()
const { t } = useI18n()
const auth = useAuthStore()
const { url, state } = useAuthImage(
  auth.http,
  computed(() => meterQrPath(props.meterId)),
)
</script>

<template>
  <div class="reg-qr">
    <img v-if="url" :src="url" :alt="t('registers.qr.alt', { name })" class="reg-qr-img" width="160" height="160" />
    <p v-else-if="state === 'error'" class="muted">{{ t('registers.qr.error') }}</p>
    <p v-else class="muted" role="status">{{ t('registers.qr.loading') }}</p>
  </div>
</template>
