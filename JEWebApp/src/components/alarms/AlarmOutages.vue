<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AlarmOutage } from '../../api/alarms'
import { formatLocal } from '../../modules/dates'

/*
 * Data outages and transmission faults (docs/ALARMS-OUTAGE.md) above the limit alarms: the open ones first, then the
 * ones that ended in the time frame with their result (late delivery, gap, no longer monitored).
 */

const props = defineProps<{ outages: readonly AlarmOutage[] }>()
const { t, locale } = useI18n()

const open = computed(() => props.outages.filter((o) => o.open))
const ended = computed(() => props.outages.filter((o) => !o.open))

const time = (iso: string | null) => (iso ? formatLocal(iso.slice(0, 16), locale.value) : '')

function objectName(o: AlarmOutage): string {
  if (!o.object) return t('modules.none')
  if (!o.object.readable) return t('alarms.noAccess', { id: o.object.id })
  return [o.object.path, o.object.name].filter(Boolean).join(' / ')
}

function duration(minutes: number): string {
  if (minutes % 1440 === 0) return t('alarmOutages.days', minutes / 1440)
  if (minutes % 60 === 0) return t('alarmOutages.hours', minutes / 60)
  return t('alarmOutages.minutes', minutes)
}

function result(o: AlarmOutage): string {
  if (!o.resumed) return t('alarmOutages.result.unmonitored')
  return o.backfilled ? t('alarmOutages.result.late') : t('alarmOutages.result.gap')
}

const kindName = (o: AlarmOutage) => t(o.kind === 'SOURCE' ? 'alarmOutages.kind.source' : 'alarmOutages.kind.row')
</script>

<template>
  <section class="alm-outages" aria-labelledby="alm-outages-title">
    <h2 id="alm-outages-title">{{ t('alarmOutages.title') }}</h2>
    <p class="muted alm-outages-hint">{{ t('alarmOutages.hint') }}</p>
    <ul class="alm-outage-list">
      <li v-for="o in [...open, ...ended]" :key="o.id" class="alm-outage" :class="o.open ? 'is-open' : 'is-ended'">
        <div class="alm-outage-head">
          <span class="mod-badge" :class="o.open ? 'is-overdue' : 'is-done'">{{ o.open ? t('alarmOutages.open') : t('alarmOutages.ended') }}</span>
          <strong>{{ objectName(o) }}</strong>
          <span class="muted">· {{ kindName(o) }}</span>
        </div>
        <dl class="mod-dl">
          <dt>{{ t('alarmOutages.since') }}</dt>
          <dd>{{ o.since ? time(o.since) : t('alarmOutages.never') }}</dd>
          <dt>{{ t('alarmOutages.allowed') }}</dt>
          <dd>{{ duration(o.minutes) }}</dd>
          <template v-if="o.cause">
            <dt>{{ t('alarmOutages.cause') }}</dt>
            <dd>{{ o.cause.name ?? t('alarms.noAccess', { id: o.cause.id }) }}</dd>
          </template>
          <template v-if="!o.open">
            <dt>{{ t('alarmOutages.resumed') }}</dt>
            <dd>{{ o.resumed ? time(o.resumed) : t('modules.none') }} – {{ result(o) }}</dd>
          </template>
          <template v-if="o.site">
            <dt>{{ t('alarms.col.site') }}</dt>
            <dd>{{ o.site.name }}</dd>
          </template>
          <dt>{{ t('alarmOutages.configuration') }}</dt>
          <dd>{{ o.configuration.name }}</dd>
        </dl>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.alm-outages {
  margin: 0 0 1.25rem;
}

.alm-outages h2 {
  font-size: 1.05rem;
  margin: 0 0 0.25rem;
}

.alm-outages-hint {
  font-size: 0.85rem;
  margin: 0 0 0.75rem;
}

.alm-outage-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.5rem;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 22rem), 1fr));
}

.alm-outage {
  background: var(--surface);
  border: 1px solid var(--line);
  border-left: 4px solid var(--line);
  border-radius: var(--radius);
  padding: 0.6rem 0.8rem;
}

.alm-outage.is-open {
  border-left-color: #c62828;
}

.alm-outage-head {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  align-items: baseline;
  margin-bottom: 0.35rem;
}
</style>
