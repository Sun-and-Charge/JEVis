<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatIsoDate } from '../../iso/format'
import { splitTags } from '../../iso/filter'
import { NONCONFORMITY_BADGE, nonconformityState, warningText, type NonconformityRow } from '../../iso/nonconformities'
import IsoAttachments from './IsoAttachments.vue'
import IsoCheckList from './IsoCheckList.vue'

const props = defineProps<{ row: NonconformityRow }>()
const { t, locale } = useI18n()

const n = computed(() => props.row.item)
const sid = (name: string) => `nc-${n.value.id}-${name}`
const state = computed(() => nonconformityState(n.value))
const warnings = computed(() => (n.value.warnings ?? []).map((w) => warningText(w, t)))

const general = computed(() =>
  [
    ['iso.nc.col.description', n.value.description],
    ['iso.nc.col.cause', n.value.cause],
    ['iso.nc.col.immediateMeasures', n.value.immediateMeasures],
    ['iso.nc.col.correctiveActions', n.value.correctiveActions],
    ['iso.nc.col.action', n.value.action],
    ['iso.nc.col.creator', n.value.creator],
    ['iso.nc.col.responsible', n.value.responsible],
    ['iso.nc.col.medium', splitTags(n.value.medium).join(', ')],
    ['iso.nc.col.fields', splitTags(n.value.fields).join(', ')],
    ['iso.nc.col.seu', n.value.seu],
    ['iso.nc.col.createDate', formatIsoDate(n.value.createDate, locale.value)],
    ['iso.nc.col.plannedDate', formatIsoDate(n.value.plannedDate, locale.value)],
    ['iso.nc.col.doneDate', formatIsoDate(n.value.doneDate, locale.value)],
    ['iso.col.plan', props.row.plan.name],
  ] as const,
)
</script>

<template>
  <div class="iso-sections">
    <section class="iso-section" :aria-labelledby="sid('general')">
      <h3 :id="sid('general')">{{ t('iso.section.general') }}</h3>
      <p class="iso-state">
        <span class="mod-badge" :class="'is-' + NONCONFORMITY_BADGE[state]">{{ t('iso.nc.state.' + state) }}</span>
      </p>
      <ul v-if="warnings.length" class="iso-warnings" :aria-label="t('iso.nc.col.warnings')">
        <li v-for="w in warnings" :key="w">{{ w }}</li>
      </ul>
      <dl class="mod-dl">
        <template v-for="[label, value] in general" :key="label">
          <dt>{{ t(label) }}</dt>
          <dd>{{ value || t('modules.none') }}</dd>
        </template>
      </dl>
    </section>

    <section class="iso-section" :aria-labelledby="sid('check')">
      <h3 :id="sid('check')">{{ t('iso.section.checkList') }}</h3>
      <IsoCheckList :check-list="n.checkList" labels="iso.nc.check" />
    </section>

    <section class="iso-section" :aria-labelledby="sid('files')">
      <h3 :id="sid('files')">{{ t('iso.section.attachments') }}</h3>
      <IsoAttachments :item-id="n.id" :count="n.attachments" />
    </section>
  </div>
</template>
