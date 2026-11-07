<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ActionStatistics } from '../../iso/actions'
import { formatIsoDate, formatMoney, formatWithUnit } from '../../iso/format'

/* Key figures of the filtered actions (like the Control Center's statistics above the action table). */

const props = defineProps<{ stats: ActionStatistics }>()
const { t, locale } = useI18n()

const money = (v: number) => formatMoney(v, locale.value)
const kwh = (v: number) => formatWithUnit(v, 'kWh', locale.value)
const tonnes = (v: number) => formatWithUnit(v, 't', locale.value)

const sinceLabel = computed(() =>
  props.stats.since.from ? t('iso.stats.sinceDate', { date: formatIsoDate(props.stats.since.from, locale.value) }) : t('iso.stats.sinceDone'),
)
</script>

<template>
  <section class="iso-stats" aria-labelledby="iso-stats-title">
    <h2 id="iso-stats-title" class="iso-stats-title">{{ t('iso.stats.title') }}</h2>
    <div class="iso-tiles">
      <dl class="iso-tile">
        <dt>{{ t('iso.stats.investment') }}</dt>
        <dd class="iso-big">{{ money(stats.investment) }}</dd>
      </dl>
      <dl class="iso-tile">
        <dt>{{ t('iso.stats.savings') }}</dt>
        <dd class="iso-big">{{ money(stats.savings) }}</dd>
        <dt class="iso-sub">{{ t('iso.stats.gross') }}</dt>
        <dd>{{ money(stats.savingsGross) }}</dd>
      </dl>
      <dl class="iso-tile">
        <dt>{{ t('iso.stats.consumption') }}</dt>
        <dd class="iso-big">{{ kwh(stats.consumption) }}</dd>
        <dt class="iso-sub">{{ t('iso.stats.gross') }}</dt>
        <dd>{{ kwh(stats.consumptionGross) }}</dd>
      </dl>
      <dl class="iso-tile">
        <dt>{{ sinceLabel }}</dt>
        <dd class="iso-big">{{ money(stats.since.moneyNet) }}</dd>
        <dt class="iso-sub">{{ t('iso.stats.gross') }}</dt>
        <dd>{{ money(stats.since.moneyGross) }}</dd>
        <dt class="iso-sub">{{ t('iso.stats.energyNet') }}</dt>
        <dd>{{ kwh(stats.since.kwhNet) }}</dd>
        <dt class="iso-sub">{{ t('iso.stats.energyGross') }}</dt>
        <dd>{{ kwh(stats.since.kwhGross) }}</dd>
      </dl>
      <dl class="iso-tile">
        <dt>{{ t('iso.stats.co2', { since: sinceLabel }) }}</dt>
        <dd class="iso-big">{{ tonnes(stats.since.co2Net) }}</dd>
        <dt class="iso-sub">{{ t('iso.stats.gross') }}</dt>
        <dd>{{ tonnes(stats.since.co2Gross) }}</dd>
      </dl>
      <dl v-if="stats.statuses.length" class="iso-tile">
        <dt>{{ t('iso.stats.statuses') }}</dt>
        <dd>
          <ul class="iso-status-list">
            <li v-for="s in stats.statuses" :key="s.status">
              <span>{{ s.status }}</span><span class="num-text">{{ s.count }}</span>
            </li>
          </ul>
        </dd>
      </dl>
    </div>

    <div v-if="stats.media.length" class="iso-mini-wrap">
      <table class="iso-mini iso-media">
        <caption>{{ t('iso.stats.perMedium') }}</caption>
        <thead>
          <tr>
            <th scope="col">{{ t('iso.actions.col.medium') }}</th>
            <th scope="col" class="num">{{ t('iso.stats.mediumChange') }}</th>
            <th scope="col" class="num">{{ t('iso.stats.mediumCo2Year') }}</th>
            <th scope="col" class="num">{{ t('iso.stats.mediumSinceNet', { since: sinceLabel }) }}</th>
            <th scope="col" class="num">{{ t('iso.stats.mediumSinceGross') }}</th>
            <th scope="col" class="num">{{ t('iso.stats.mediumCo2Net') }}</th>
            <th scope="col" class="num">{{ t('iso.stats.mediumCo2Gross') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in stats.media" :key="m.id">
            <th scope="row">{{ m.name }}</th>
            <td class="num">{{ kwh(m.diff) }}</td>
            <td class="num">{{ tonnes(m.co2PerYear) }}</td>
            <td class="num">{{ kwh(m.kwhNet) }}</td>
            <td class="num">{{ kwh(m.kwhGross) }}</td>
            <td class="num">{{ tonnes(m.co2Net) }}</td>
            <td class="num">{{ tonnes(m.co2Gross) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
