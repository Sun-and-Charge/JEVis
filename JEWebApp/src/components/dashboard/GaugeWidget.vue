<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import type { WidgetConfig, WidgetData } from '../../api/dashboard'
import { formatNumber } from '../../analysis/format'
import type { WidgetContext } from '../../dashboard/context'
import { gaugeConfig, gaugeOption } from '../../dashboard/options'
import { cssColor, DEFAULT_FONT_SIZE } from '../../dashboard/style'
import { fraction, num, toPercent, widgetDecimals, widgetRows } from '../../dashboard/values'
import { widgetKind } from '../../dashboard/widgets'
import WidgetChart from './WidgetChart.vue'

/** Gauge (ECharts), LinearGauge and Battery (SVG). */
const props = defineProps<{ widget: WidgetConfig; data: WidgetData | undefined; ctx: WidgetContext }>()

const kind = computed(() => widgetKind(props.widget.type))
const row = computed(() => widgetRows(props.widget, props.data)[0])
const value = computed(() => row.value?.value ?? null)
const decimals = computed(() => widgetDecimals(props.widget, row.value?.config))
const fontColor = computed(() => cssColor(props.widget.style?.fontColor) ?? '#1a2a45')
const fontSize = computed(() => (props.widget.style?.fontSize || DEFAULT_FONT_SIZE) * props.ctx.scale)
const c = computed(() => props.widget.config ?? {})

const gauge = computed(() =>
  kind.value === 'gauge'
    ? gaugeOption(gaugeConfig(c.value), {
        title: props.widget.title ?? '',
        value: value.value,
        unit: row.value?.unit ?? '',
        decimals: decimals.value,
        color: fontColor.value,
        fontSize: fontSize.value,
        locale: props.ctx.locale,
      })
    : null,
)

/* ---- LinearGauge and Battery: SVG in the native size of the widget ---- */
const W = computed(() => Math.max(1, props.widget.width || 1))
const H = computed(() => Math.max(1, props.widget.height || 1))
const fs = computed(() => props.widget.style?.fontSize || DEFAULT_FONT_SIZE)
const minimum = computed(() => num(c.value.minimum, 0))
const maximum = computed(() => {
  const m = num(c.value.maximum, 100)
  return m > minimum.value ? m : minimum.value + 1
})
const inPercent = computed(() => !!c.value.inPercent)
const level = computed(() => fraction(value.value, minimum.value, maximum.value))
const barColor = computed(() => cssColor(c.value.color) ?? '#00538a')
const show = (key: string) => c.value[key] !== false

const valueText = computed(() => {
  if (value.value == null) return '–'
  if (inPercent.value || kind.value === 'battery') {
    const p = kind.value === 'battery' ? level.value * 100 : toPercent(value.value, minimum.value, maximum.value)
    return p == null ? '–' : formatNumber(p, props.ctx.locale, kind.value === 'battery' ? { min: 0, max: 0 } : decimals.value) + ' %'
  }
  const unit = show('showUnit') && row.value?.unit ? ' ' + row.value.unit : ''
  return formatNumber(value.value, props.ctx.locale, decimals.value) + unit
})

const horizontal = computed(() => W.value >= H.value)
const linear = computed(() => {
  const pad = fs.value * 0.8
  const titleH = show('showTitle') && props.widget.title ? fs.value * 1.6 : 0
  const valueH = show('showValue') ? fs.value * 1.6 : 0
  const tickH = fs.value * 1.6
  const lo = inPercent.value ? 0 : minimum.value
  const hi = inPercent.value ? 100 : maximum.value
  const step = num(c.value.majorTickStep, 0)
  const span = hi - lo
  const count = step > 0 && span / step <= 50 ? Math.floor(span / step + 1e-9) : 4
  const tickStep = step > 0 && span / step <= 50 ? step : span / 4
  const ticks = Array.from({ length: count + 1 }, (_, i) => lo + i * tickStep)
  const tickDecimals = Number.isInteger(tickStep) ? { min: 0, max: 0 } : { min: 0, max: 2 }
  const label = (v: number) => formatNumber(v, props.ctx.locale, tickDecimals)
  if (horizontal.value) {
    const x0 = pad
    const x1 = W.value - pad
    const thick = Math.max(4, Math.min(H.value - titleH - valueH - tickH - pad * 2, fs.value * 1.5))
    const y = titleH + pad
    return {
      bar: { x: x0, y, width: x1 - x0, height: thick },
      fill: { x: x0, y, width: (x1 - x0) * level.value, height: thick },
      ticks: ticks.map((v) => {
        const x = x0 + ((v - lo) / (span || 1)) * (x1 - x0)
        return { x1: x, y1: y + thick, x2: x, y2: y + thick + fs.value * 0.4, tx: x, ty: y + thick + fs.value * 1.4, anchor: 'middle', text: label(v) }
      }),
      title: { x: W.value / 2, y: fs.value * 1.2 },
      value: { x: W.value / 2, y: y + thick + tickH + fs.value * 1.3 },
    }
  }
  const y0 = titleH + pad
  const y1 = H.value - valueH - pad
  const thick = Math.max(4, Math.min(W.value * 0.3, fs.value * 1.5))
  const x = W.value / 2 - thick
  const len = Math.max(1, y1 - y0)
  return {
    bar: { x, y: y0, width: thick, height: len },
    fill: { x, y: y0 + len * (1 - level.value), width: thick, height: len * level.value },
    ticks: ticks.map((v) => {
      const ty = y1 - ((v - lo) / (span || 1)) * len
      return { x1: x + thick, y1: ty, x2: x + thick + fs.value * 0.4, y2: ty, tx: x + thick + fs.value * 0.6, ty: ty + fs.value * 0.35, anchor: 'start', text: label(v) }
    }),
    title: { x: W.value / 2, y: fs.value * 1.2 },
    value: { x: W.value / 2, y: H.value - pad },
  }
})

const battery = computed(() => {
  const pad = Math.min(W.value, H.value) * 0.08
  const horizontalBattery = W.value >= H.value
  if (horizontalBattery) {
    const capW = Math.max(3, W.value * 0.05)
    const body = { x: pad, y: pad, width: W.value - pad * 2 - capW, height: H.value - pad * 2 }
    const inset = Math.max(2, body.height * 0.08)
    const inner = body.width - inset * 2
    return {
      body,
      cap: { x: body.x + body.width, y: H.value / 2 - body.height * 0.2, width: capW, height: body.height * 0.4 },
      fill: { x: body.x + inset, y: body.y + inset, width: inner * level.value, height: body.height - inset * 2 },
    }
  }
  const capH = Math.max(3, H.value * 0.05)
  const body = { x: pad, y: pad + capH, width: W.value - pad * 2, height: H.value - pad * 2 - capH }
  const inset = Math.max(2, body.width * 0.08)
  const inner = body.height - inset * 2
  return {
    body,
    cap: { x: W.value / 2 - body.width * 0.2, y: pad, width: body.width * 0.4, height: capH },
    fill: { x: body.x + inset, y: body.y + inset + inner * (1 - level.value), width: body.width - inset * 2, height: inner * level.value },
  }
})
</script>

<template>
  <WidgetChart v-if="kind === 'gauge'" :option="gauge" :locale="ctx.locale" :label="`${widget.title ?? ''} ${valueText}`" />
  <svg v-else-if="kind === 'linearGauge'" class="svg" :viewBox="`0 0 ${W} ${H}`" role="img" :aria-label="`${widget.title ?? ''} ${valueText}`">
    <text v-if="show('showTitle') && widget.title" :x="linear.title.x" :y="linear.title.y" text-anchor="middle" :font-size="fs" fill="currentColor">{{ widget.title }}</text>
    <rect v-bind="linear.bar" :fill="'#e3e7ed'" :stroke="c.borderColor ? String(c.borderColor) : 'none'" />
    <rect v-bind="linear.fill" :fill="barColor" />
    <g :font-size="fs * 0.8" fill="currentColor" stroke="currentColor" stroke-width="1">
      <g v-for="(tick, i) in linear.ticks" :key="i">
        <line :x1="tick.x1" :y1="tick.y1" :x2="tick.x2" :y2="tick.y2" />
        <text :x="tick.tx" :y="tick.ty" :text-anchor="tick.anchor" stroke="none">{{ tick.text }}</text>
      </g>
    </g>
    <text v-if="show('showValue')" :x="linear.value.x" :y="linear.value.y" text-anchor="middle" :font-size="fs * 1.1" font-weight="600" fill="currentColor">{{ valueText }}</text>
  </svg>
  <svg v-else class="svg" :viewBox="`0 0 ${W} ${H}`" role="img" :aria-label="`${widget.title ?? ''} ${valueText}`">
    <rect v-bind="battery.body" rx="4" fill="none" stroke="currentColor" stroke-width="2" />
    <rect v-bind="battery.cap" rx="1" fill="currentColor" />
    <rect v-bind="battery.fill" rx="2" :fill="barColor" />
    <text v-if="show('showValue')" :x="W / 2" :y="H / 2 + fs * 0.4" text-anchor="middle" :font-size="fs * 1.1" font-weight="600" fill="currentColor">{{ valueText }}</text>
  </svg>
</template>

<style scoped>
.svg {
  display: block;
  width: 100%;
  height: 100%;
  font-family: inherit;
}
</style>
