<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WidgetConfig, WidgetData } from '../../api/dashboard'
import { formatNumber } from '../../analysis/format'
import type { WidgetContext } from '../../dashboard/context'
import { widgetDecimals, widgetRows } from '../../dashboard/values'

/** Table widget: name, value and unit per row (like the Control Center). */
const props = defineProps<{ widget: WidgetConfig; data: WidgetData | undefined; ctx: WidgetContext }>()
const { t } = useI18n()

const lines = computed(() =>
  widgetRows(props.widget, props.data).map((row) => {
    const readable = row.config.readable !== false
    let value = ''
    let state: string | null = null
    if (row.state === 'NO_PERMISSION') state = t('analysis.state.noPermission')
    else if (row.state === 'ERROR') state = t('analysis.state.error')
    else if (row.value != null) value = formatNumber(row.value, props.ctx.locale, widgetDecimals(props.widget, row.config))
    else if (row.text != null) value = row.text
    return {
      key: row.config.key,
      name: readable && row.config.name ? row.config.name : t('analysis.state.noAccess'),
      value,
      state,
      unit: readable ? row.unit : '',
      readable,
    }
  }),
)
</script>

<template>
  <div class="scroll">
    <table>
      <caption v-if="widget.title" class="visually-hidden">{{ widget.title }}</caption>
      <thead>
        <tr>
          <th scope="col">{{ t('dashboard.table.name') }}</th>
          <th scope="col" class="num">{{ t('dashboard.table.value') }}</th>
          <th scope="col">{{ t('dashboard.table.unit') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="line in lines" :key="line.key" :class="{ off: !line.readable }">
          <th scope="row">{{ line.name }}</th>
          <td class="num">
            <span v-if="line.state" class="state">{{ line.state }}</span>
            <template v-else>{{ line.value }}</template>
          </td>
          <td>{{ line.unit }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.scroll {
  width: 100%;
  height: 100%;
  overflow: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 1em;
}

th,
td {
  padding: 0.2em 0.5em;
  text-align: left;
  border-bottom: 1px solid rgba(127, 127, 127, 0.25);
  white-space: nowrap;
}

thead th {
  font-weight: 600;
  font-size: 0.9em;
  opacity: 0.8;
}

tbody th {
  font-weight: inherit;
  white-space: normal;
}

.num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.off th,
.state {
  color: #8a93a3;
}
</style>
