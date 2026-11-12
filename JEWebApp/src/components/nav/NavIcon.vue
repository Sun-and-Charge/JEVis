<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
/* Line icons of the navigation (inline SVG, 24 × 24 grid, drawn with the text colour); always decorative. */

import type { NavIconName } from '../../nav/menu'

defineProps<{ name: NavIconName; filled?: boolean }>()
</script>

<template>
  <svg class="nav-icon" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" focusable="false">
    <g fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
      <template v-if="name === 'home'">
        <path d="M3 11 12 4l9 7" />
        <path d="M5.5 9.5V20h13V9.5" />
        <path d="M10 20v-5h4v5" />
      </template>
      <template v-else-if="name === 'dashboard'">
        <rect x="3.5" y="3.5" width="7" height="9" rx="1" />
        <rect x="13.5" y="3.5" width="7" height="5" rx="1" />
        <rect x="13.5" y="11.5" width="7" height="9" rx="1" />
        <rect x="3.5" y="15.5" width="7" height="5" rx="1" />
      </template>
      <template v-else-if="name === 'analysis'">
        <path d="M4 4v16h16" />
        <path d="m7 15 4-5 3 3 5-6" />
      </template>
      <template v-else-if="name === 'iso'">
        <path d="M12 3 5 6v5c0 4.5 3 8 7 10 4-2 7-5.5 7-10V6z" />
        <path d="m9 12 2 2 4-4" />
      </template>
      <template v-else-if="name === 'operations'">
        <circle cx="12" cy="12" r="3" />
        <path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1" />
      </template>
      <template v-else-if="name === 'assets'">
        <path d="M13 2 4 14h7l-1 8 9-12h-7z" />
      </template>
      <template v-else-if="name === 'finance'">
        <path d="M17.5 6.5A7 7 0 1 0 17.5 17.5" />
        <path d="M4 10h9M4 14h9" />
      </template>
      <template v-else-if="name === 'reports'">
        <path d="M14 3H6.5A1.5 1.5 0 0 0 5 4.5v15A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V8z" />
        <path d="M14 3v5h5M8.5 13h7M8.5 17h5" />
      </template>
      <template v-else-if="name === 'collapse'">
        <path d="m11 17-5-5 5-5M18 17l-5-5 5-5" />
      </template>
      <template v-else-if="name === 'expand'">
        <path d="m13 17 5-5-5-5M6 17l5-5-5-5" />
      </template>
      <template v-else-if="name === 'search'">
        <circle cx="11" cy="11" r="6.5" />
        <path d="m20 20-4.4-4.4" />
      </template>
      <template v-else-if="name === 'star'">
        <path :fill="filled ? 'currentColor' : 'none'" d="m12 3.5 2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z" />
      </template>
      <template v-else-if="name === 'chevron'">
        <path d="m9 6 6 6-6 6" />
      </template>
      <template v-else-if="name === 'user'">
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7" />
      </template>
      <template v-else-if="name === 'site'">
        <path d="M12 21s-6.5-6-6.5-11a6.5 6.5 0 0 1 13 0c0 5-6.5 11-6.5 11z" />
        <circle cx="12" cy="10" r="2.3" />
      </template>
    </g>
  </svg>
</template>

<style scoped>
.nav-icon {
  flex: none;
  display: block;
}
</style>
