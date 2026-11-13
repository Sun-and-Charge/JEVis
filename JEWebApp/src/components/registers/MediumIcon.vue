<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'

/* Small line icon of a meter medium (decorative: the label next to it names the medium). */

const props = defineProps<{ medium: string | null | undefined }>()

const PATHS: Record<string, string> = {
  ELECTRICITY: 'M13 2 4 14h7l-1 8 9-12h-7z',
  GAS: 'M12 2c1 4 6 6 6 12a6 6 0 0 1-12 0c0-3 2-5 3-7 0 2 1 3 2 3 0-3 0-5 1-8z',
  WATER: 'M12 3s6 7 6 11a6 6 0 0 1-12 0c0-4 6-11 6-11z',
  HEAT: 'M10 4a2 2 0 0 1 4 0v9a4 4 0 1 1-4 0zM12 9v6',
  AIR: 'M3 9h11a3 3 0 1 0-3-3M3 15h15a3 3 0 1 1-3 3M3 12h7',
  COMPRESSED_AIR: 'M12 21a8 8 0 1 1 0-16 8 8 0 0 1 0 16zM12 13l3-3M8 13h0M16 13h0',
  NITROGEN: 'M4 4h16v16H4zM8 16V8l8 8V8',
}

const path = computed(() => PATHS[props.medium ?? ''] ?? 'M5 5h14v14H5zM9 12h6')
</script>

<template>
  <svg class="reg-medium-icon" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" focusable="false">
    <path :d="path" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
  </svg>
</template>
