<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import type { WidgetConfig } from '../../api/dashboard'
import { DEFAULT_FONT_SIZE } from '../../dashboard/style'

/** Arrow widget: an arrow (or line) in the font color in one of four directions (no text, like the Control Center). */
const props = defineProps<{ widget: WidgetConfig }>()

const W = computed(() => Math.max(1, props.widget.width || 1))
const H = computed(() => Math.max(1, props.widget.height || 1))
const fs = computed(() => props.widget.style?.fontSize || DEFAULT_FONT_SIZE)
const orientation = computed(() => String(props.widget.config?.orientation ?? 'LEFT_RIGHT').toUpperCase())
const line = computed(() => String(props.widget.config?.shape ?? 'ARROW').toUpperCase() === 'LINE')
const vertical = computed(() => orientation.value === 'TOP_BOTTOM' || orientation.value === 'BOTTOM_TOP')

/** polygon of an arrow pointing to the right in a box of length × thickness, then rotated */
const shape = computed(() => {
  const len = vertical.value ? H.value : W.value
  const thick = vertical.value ? W.value : H.value
  const shaft = Math.max(2, Math.min(thick * 0.3, fs.value))
  const head = line.value ? 0 : Math.min(len * 0.4, Math.max(thick * 0.8, shaft * 2.5))
  const headHalf = Math.min(thick / 2, shaft * 1.6)
  const mid = thick / 2
  const pts = line.value
    ? [[0, mid - shaft / 2], [len, mid - shaft / 2], [len, mid + shaft / 2], [0, mid + shaft / 2]]
    : [[0, mid - shaft / 2], [len - head, mid - shaft / 2], [len - head, mid - headHalf], [len, mid], [len - head, mid + headHalf], [len - head, mid + shaft / 2], [0, mid + shaft / 2]]
  const map = (x: number, y: number): [number, number] => {
    switch (orientation.value) {
      case 'RIGHT_LEFT':
        return [len - x, y]
      case 'TOP_BOTTOM':
        return [y, x]
      case 'BOTTOM_TOP':
        return [y, len - x]
      default:
        return [x, y]
    }
  }
  return pts.map(([x, y]) => map(x, y).join(',')).join(' ')
})
</script>

<template>
  <svg class="svg" :viewBox="`0 0 ${W} ${H}`"preserveAspectRatio="none" role="img" :aria-label="widget.title || undefined">
    <polygon :points="shape" fill="currentColor" />
  </svg>
</template>

<style scoped>
.svg {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
