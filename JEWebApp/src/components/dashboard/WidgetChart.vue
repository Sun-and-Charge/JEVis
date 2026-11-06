<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import type { EChartsOption } from 'echarts'
import { computed, onMounted, ref } from 'vue'
import VChart from 'vue-echarts'
import { echartsLocale } from '../../analysis/echarts'

/** ECharts canvas filling its widget. */
const props = defineProps<{ option: EChartsOption | null; locale: string; label?: string }>()

// ECharts measures text widths when drawing: start once Montserrat is loaded
const fontsReady = ref(typeof document === 'undefined' || !document.fonts || document.fonts.status === 'loaded')
onMounted(() => {
  if (!fontsReady.value) document.fonts.ready.finally(() => (fontsReady.value = true))
})
const initOptions = computed(() => ({ locale: echartsLocale(props.locale) }))
</script>

<template>
  <div class="wchart" role="img" :aria-label="label || undefined">
    <VChart v-if="fontsReady && option" :option="option" :init-options="initOptions" :update-options="{ notMerge: true }" autoresize />
  </div>
</template>

<style scoped>
.wchart {
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
}
</style>
