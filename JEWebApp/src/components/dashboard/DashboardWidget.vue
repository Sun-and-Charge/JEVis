<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WidgetConfig, WidgetData } from '../../api/dashboard'
import type { WidgetContext } from '../../dashboard/context'
import { widgetBox } from '../../dashboard/layout'
import { frameStyle } from '../../dashboard/style'
import { linkTarget, widgetKind, widgetLink } from '../../dashboard/widgets'
import ArrowWidget from './ArrowWidget.vue'
import ChartWidget from './ChartWidget.vue'
import DiagramWidget from './DiagramWidget.vue'
import GaugeWidget from './GaugeWidget.vue'
import ImageWidget from './ImageWidget.vue'
import InputWidget from './InputWidget.vue'
import ShapeWidget from './ShapeWidget.vue'
import TableWidget from './TableWidget.vue'
import TextWidget from './TextWidget.vue'

/** Frame of a widget: position, size and style of the configuration; the content by widget type. */
const props = defineProps<{ widget: WidgetConfig; data: WidgetData | undefined; ctx: WidgetContext; z: number }>()
const emit = defineEmits<{ written: [data: WidgetData] }>()
const { t } = useI18n()

const kind = computed(() => widgetKind(props.widget.type))
const box = computed(() => widgetBox(props.widget, props.ctx.scale))
const style = computed(() => ({
  ...frameStyle(props.widget.style, props.ctx.scale),
  left: `${box.value.left}px`,
  top: `${box.value.top}px`,
  width: `${box.value.width}px`,
  height: `${box.value.height}px`,
  zIndex: String(props.z),
}))

const link = computed(() => {
  const l = widgetLink(props.widget)
  return l ? linkTarget(l, props.ctx.state, props.ctx.shown) : null
})
/** Link widgets show an icon like the Control Center's buttons; the title is the tooltip and the accessible name. */
const linkKind = computed(() => String((props.widget.config as { link?: { kind?: string } } | undefined)?.link?.kind ?? 'DASHBOARD'))
const linkLabel = computed(() => props.widget.title || props.widget.tooltip || t('dashboard.openLink'))
const linkTitle = computed(() => {
  const l = link.value
  if (!l || l.kind === 'route') return props.widget.tooltip || undefined
  return t('dashboard.linkNoAccess')
})

const TEXT = ['title', 'value', 'string', 'timeFrame', 'alarmCount']
const GAUGES = ['gauge', 'linearGauge', 'battery']
</script>

<template>
  <div class="widget" :class="['w-' + kind, { empty: kind === 'empty' }]" :style="style" :title="kind !== 'link' ? widget.tooltip || undefined : undefined" :data-uuid="widget.uuid">
    <TextWidget v-if="TEXT.includes(kind)" :widget="widget" :data="data" :ctx="ctx" />
    <TableWidget v-else-if="kind === 'table'" :widget="widget" :data="data" :ctx="ctx" />
    <ChartWidget v-else-if="kind === 'chart' || kind === 'pie'" :widget="widget" :data="data" :ctx="ctx" />
    <GaugeWidget v-else-if="GAUGES.includes(kind)" :widget="widget" :data="data" :ctx="ctx" />
    <ShapeWidget v-else-if="kind === 'shape'" :widget="widget" :data="data" :ctx="ctx" />
    <ArrowWidget v-else-if="kind === 'arrow'" :widget="widget" />
    <DiagramWidget v-else-if="kind === 'netGraph' || kind === 'sankey'" :widget="widget" :data="data" :ctx="ctx" />
    <InputWidget v-else-if="kind === 'input'" :widget="widget" :data="data" :ctx="ctx" @written="emit('written', $event)" />
    <template v-else-if="kind === 'image'">
      <RouterLink v-if="link?.kind === 'route'" :to="link.to" class="image-link" :aria-label="widget.title || widget.tooltip || t('dashboard.openLink')">
        <ImageWidget :widget="widget" />
      </RouterLink>
      <ImageWidget v-else :widget="widget" />
    </template>
    <template v-else-if="kind === 'link'">
      <RouterLink
        v-if="link?.kind === 'route'"
        :to="link.to"
        class="link"
        :title="linkTitle || linkLabel"
        :aria-label="linkLabel"
      >
        <svg class="link-icon" viewBox="0 0 24 24" aria-hidden="true">
          <path v-if="linkKind === 'ANALYSIS'" d="M4 20h16M7 17v-6M12 17V6M17 17v-9" />
          <path v-else-if="linkKind === 'REPORT'" d="M7 3h7l4 4v14H7zM14 3v4h4M10 12h5M10 16h5" />
          <path v-else d="M4 4h7v7H4zM13 4h7v4h-7zM13 10h7v10h-7zM4 13h7v7H4z" />
        </svg>
      </RouterLink>
      <button v-else type="button" class="link" aria-disabled="true" :title="linkTitle" :aria-label="`${linkLabel} – ${linkTitle}`">
        <svg class="link-icon" viewBox="0 0 24 24" aria-hidden="true">
          <path v-if="linkKind === 'ANALYSIS'" d="M4 20h16M7 17v-6M12 17V6M17 17v-9" />
          <path v-else-if="linkKind === 'REPORT'" d="M7 3h7l4 4v14H7zM14 3v4h4M10 12h5M10 16h5" />
          <path v-else d="M4 4h7v7H4zM13 4h7v4h-7zM13 10h7v10h-7zM4 13h7v7H4z" />
        </svg>
      </button>
    </template>
  </div>
</template>

<style scoped>
.widget {
  position: absolute;
  overflow: hidden;
  line-height: 1.25;
  font-family: inherit;
  display: flex;
}

.widget > :deep(*) {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.empty {
  outline: 1px dashed rgba(127, 127, 127, 0.45);
  outline-offset: -1px;
}

.link {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 0;
  color: inherit;
  background: none;
  border: none;
  cursor: pointer;
}

.link-icon {
  width: min(100%, 2.4em);
  height: min(100%, 2.4em);
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linejoin: round;
  stroke-linecap: round;
}

.link:hover .link-icon,
.link:focus-visible .link-icon {
  stroke-width: 2.4;
}

.link:hover {
  text-decoration-thickness: 2px;
}

button.link[aria-disabled="true"] {
  cursor: not-allowed;
  opacity: 0.55;
}

.image-link {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
