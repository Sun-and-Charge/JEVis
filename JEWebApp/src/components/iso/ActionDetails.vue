<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Consumption } from '../../api/iso'
import type { ActionRow } from '../../iso/actions'
import { formatIsoDate, formatMoney, formatWithUnit, npvInputs, npvResults, yearTotals } from '../../iso/format'
import IsoAttachments from './IsoAttachments.vue'
import IsoCheckList from './IsoCheckList.vue'
import IsoItemActions from './IsoItemActions.vue'

const props = defineProps<{ row: ActionRow }>()
const emit = defineEmits<{ edit: []; remove: []; attachments: [count: number] }>()
const { t, locale } = useI18n()

const a = computed(() => props.row.item)
const label = computed(() => [a.value.number, a.value.title].filter(Boolean).join(' – '))
const sid = (name: string) => `act-${a.value.id}-${name}`

const general = computed(() =>
  [
    ['iso.actions.col.description', a.value.description],
    ['iso.actions.col.status', a.value.status],
    ['iso.actions.col.responsible', a.value.responsible],
    ['iso.actions.col.fromUser', a.value.fromUser],
    ['iso.actions.col.distributor', a.value.distributor],
    ['iso.actions.col.field', a.value.field],
    ['iso.actions.col.medium', a.value.mediumName || a.value.medium],
    ['iso.actions.col.seu', a.value.seu],
    ['iso.actions.col.createDate', formatIsoDate(a.value.createDate, locale.value)],
    ['iso.actions.col.plannedDate', formatIsoDate(a.value.plannedDate, locale.value)],
    ['iso.actions.col.doneDate', formatIsoDate(a.value.doneDate, locale.value)],
    ['iso.col.plan', props.row.plan.name],
  ] as const,
)

const notes = computed(() =>
  (
    [
      ['iso.actions.col.note', a.value.note],
      ['iso.actions.notes.correction', a.value.correction],
      ['iso.actions.notes.followAction', a.value.followAction],
      ['iso.actions.notes.alternativeMeasures', a.value.alternativeMeasures],
      ['iso.actions.notes.affectedProcess', a.value.affectedProcessNote],
      ['iso.actions.notes.energyFlow', a.value.energyFlowNote],
      ['iso.actions.notes.evaluation', a.value.evaluationNote],
    ] as const
  ).filter(([, v]) => !!v),
)

function period(from: string | null, until: string | null): string {
  const f = formatIsoDate(from, locale.value)
  const u = formatIsoDate(until, locale.value)
  return f || u ? `${f || '…'} – ${u || '…'}` : ''
}

interface Measure {
  key: 'consumption' | 'enpi'
  value: Consumption
}

const measures = computed<Measure[]>(() => {
  const list: Measure[] = []
  if (a.value.consumption) list.push({ key: 'consumption', value: a.value.consumption })
  if (a.value.enpi) list.push({ key: 'enpi', value: a.value.enpi })
  return list
})

const inputs = computed(() => npvInputs(a.value.npv, locale.value))
const results = computed(() => npvResults(a.value.npv, locale.value))
const years = computed(() => a.value.npv?.yearRows ?? [])
const totals = computed(() => yearTotals(years.value))
const money = (v: number | null | undefined) => formatMoney(v, locale.value)
const none = computed(() => t('modules.none'))
</script>

<template>
  <IsoItemActions :item="a" :label="label" @edit="emit('edit')" @remove="emit('remove')" />
  <div class="iso-sections">
    <section class="iso-section" :aria-labelledby="sid('general')">
      <h3 :id="sid('general')">{{ t('iso.section.general') }}</h3>
      <dl class="mod-dl">
        <template v-for="[label, value] in general" :key="label">
          <dt>{{ t(label) }}</dt>
          <dd>{{ value || none }}</dd>
        </template>
      </dl>
    </section>

    <section class="iso-section" :aria-labelledby="sid('details')">
      <h3 :id="sid('details')">{{ t('iso.section.details') }}</h3>
      <p v-if="a.canWrite && measures.length" class="iso-calc-note">{{ t('isoEdit.calculatedShort') }}</p>
      <p v-if="!measures.length && !notes.length" class="muted">{{ none }}</p>
      <table v-for="m in measures" :key="m.key" class="iso-mini">
        <caption>{{ t('iso.actions.' + m.key) }}<template v-if="m.value.unit"> ({{ m.value.unit }})</template></caption>
        <thead>
          <tr>
            <th scope="col"><span class="visually-hidden">{{ t('iso.actions.measure') }}</span></th>
            <th scope="col" class="num">{{ t('iso.actions.value') }}</th>
            <th scope="col">{{ t('iso.actions.period') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <th scope="row">{{ t('iso.actions.before') }}</th>
            <td class="num">{{ formatWithUnit(m.value.before, m.value.unit, locale) || none }}</td>
            <td>{{ period(m.value.beforeFrom, m.value.beforeUntil) || none }}</td>
          </tr>
          <tr>
            <th scope="row">{{ t('iso.actions.after') }}</th>
            <td class="num">{{ formatWithUnit(m.value.after, m.value.unit, locale) || none }}</td>
            <td>{{ period(m.value.afterFrom, m.value.afterUntil) || none }}</td>
          </tr>
          <tr>
            <th scope="row">{{ t('iso.actions.diff') }}</th>
            <td class="num">{{ formatWithUnit(m.value.diff, m.value.unit, locale) || none }}</td>
            <td></td>
          </tr>
        </tbody>
      </table>
      <dl v-if="notes.length" class="mod-dl">
        <template v-for="[label, value] in notes" :key="label">
          <dt>{{ t(label) }}</dt>
          <dd>{{ value }}</dd>
        </template>
      </dl>
    </section>

    <section class="iso-section" :aria-labelledby="sid('capital')">
      <h3 :id="sid('capital')">{{ t('iso.section.capital') }}</h3>
      <p v-if="a.canWrite" class="iso-calc-note">{{ t('isoEdit.calculatedShort') }}</p>
      <p v-if="!inputs.length && !results.length && !years.length" class="muted">{{ none }}</p>
      <div class="iso-npv">
        <dl v-if="inputs.length" class="mod-dl">
          <template v-for="f in inputs" :key="f.label">
            <dt>{{ t(f.label) }}</dt>
            <dd class="num-text">{{ f.text }}</dd>
          </template>
        </dl>
        <dl v-if="results.length" class="mod-dl">
          <template v-for="f in results" :key="f.label">
            <dt>{{ t(f.label) }}</dt>
            <dd class="num-text">{{ f.text }}</dd>
          </template>
        </dl>
      </div>
      <div v-if="years.length" class="iso-mini-wrap">
        <table class="iso-mini">
          <caption>{{ t('iso.npv.yearTable') }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ t('iso.npv.year') }}</th>
              <th scope="col" class="num">{{ t('iso.npv.deposit') }}</th>
              <th scope="col" class="num">{{ t('iso.npv.cost') }}</th>
              <th scope="col" class="num">{{ t('iso.npv.net') }}</th>
              <th scope="col" class="num">{{ t('iso.npv.discounted') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="y in years" :key="y.year">
              <th scope="row">{{ y.year }}</th>
              <td class="num">{{ money(y.deposit) }}</td>
              <td class="num">{{ money(y.cost) }}</td>
              <td class="num">{{ money(y.net) }}</td>
              <td class="num">{{ money(y.discounted) }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <th scope="row">{{ t('iso.npv.total') }}</th>
              <td class="num">{{ money(totals.deposit) }}</td>
              <td class="num">{{ money(totals.cost) }}</td>
              <td class="num">{{ money(totals.net) }}</td>
              <td class="num">{{ money(totals.discounted) }}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </section>

    <section class="iso-section" :aria-labelledby="sid('check')">
      <h3 :id="sid('check')">{{ t('iso.section.checkList') }}</h3>
      <IsoCheckList :check-list="a.checkList" labels="iso.actions.check" />
    </section>

    <section class="iso-section" :aria-labelledby="sid('files')">
      <h3 :id="sid('files')">{{ t('iso.section.attachments') }}</h3>
      <IsoAttachments :item-id="a.id" :count="a.attachments" :can-attach="!!a.canAttach" @changed="emit('attachments', $event)" />
    </section>
  </div>
</template>
