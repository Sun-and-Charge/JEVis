<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { ReadingMeter } from '../../api/readings'
import { formatDate } from '../../analysis/format'
import { formatRange, formatReading, formatReadingTime } from '../../readings/readings'

/*
 * "Ableseliste drucken" (E8.1): the filtered meters as a list to fill in by hand (name, location, serial number,
 * last reading and date, expected range, empty boxes for reading, date and initials). Hidden on screen; the page's
 * print CSS shows only this sheet (A4 portrait).
 */

defineProps<{ title: string; subtitle: string; meters: readonly ReadingMeter[] }>()
const { t, locale } = useI18n()
const printed = formatDate(Date.now(), locale.value)
</script>

<template>
  <div class="rd-print" aria-hidden="true">
    <header class="rd-print-head">
      <h1>{{ title }}</h1>
      <p>{{ subtitle }}</p>
      <p>{{ t('readings.print.printed', { date: printed }) }}</p>
    </header>
    <table class="rd-print-table">
      <thead>
        <tr>
          <th scope="col" class="rd-p-meter">{{ t('readings.col.meter') }}</th>
          <th scope="col" class="rd-p-last">{{ t('readings.col.last') }}</th>
          <th scope="col" class="rd-p-exp">{{ t('readings.print.expected') }}</th>
          <th scope="col" class="rd-p-box rd-p-reading">{{ t('readings.reading') }}</th>
          <th scope="col" class="rd-p-box rd-p-date">{{ t('readings.col.date') }}</th>
          <th scope="col" class="rd-p-box rd-p-init">{{ t('readings.print.initials') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="m in meters" :key="m.id">
          <td class="rd-p-meter">
            <strong>{{ m.name }}</strong>
            <span v-if="m.location">{{ m.location }}</span>
            <span v-if="m.serialNumber">{{ t('readings.col.serial') }}: {{ m.serialNumber }}</span>
          </td>
          <td class="rd-p-last">
            <template v-if="m.readings[0]">
              {{ formatReading(m.readings[0].value, m, locale) }}
              <span>{{ formatReadingTime(m.readings[0].timestamp, m, locale) }}</span>
            </template>
            <template v-else>–</template>
          </td>
          <td class="rd-p-exp">{{ m.expected ? formatRange(m.expected, m, locale) : '–' }}</td>
          <td class="rd-p-box rd-p-reading"></td>
          <td class="rd-p-box rd-p-date"></td>
          <td class="rd-p-box rd-p-init"></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
