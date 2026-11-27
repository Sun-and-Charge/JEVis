<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { checkStatus } from '../../accounting/invoices'
import { recheckInvoice, type Invoice, type InvoiceCheckPart } from '../../api/invoices'
import { useBusy, writeErrorText } from '../../edit/edit'
import { formatNumber } from '../../analysis/format'
import { useAuthStore } from '../../stores/auth'

/*
 * Result of the automatic invoice check (R03, docs/ENERGY-INVOICES.md "Invoice check"): status, the contract the
 * invoice was matched with (meter point = market location), quantity against the measurement and net amount against
 * the contract, each with deviation and tolerance; "Erneut prüfen" with write right on the invoice.
 */

const props = defineProps<{ invoice: Invoice; editable?: boolean }>()
const emit = defineEmits<{ checked: [] }>()
const { t, te, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const error = ref('')
const uid = `inv-chk-${Math.random().toString(36).slice(2, 9)}`

const result = computed(() => props.invoice.check ?? null)
const status = computed(() => checkStatus(props.invoice))
const reason = computed(() => {
  const r = result.value?.reason
  if (!r) return ''
  return te('invoices.check.reason.' + r) ? t('invoices.check.reason.' + r) : r
})
const num = (n: number | null | undefined, digits = 2) => (n == null ? '–' : formatNumber(n, locale.value, { min: 0, max: digits }))
const checkedAt = computed(() => {
  const at = result.value?.checkedAt
  if (!at) return ''
  const ms = Date.parse(at)
  return Number.isNaN(ms) ? at : new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }).format(ms)
})

const parts = computed(() => {
  const list: { key: string; title: string; unit: string; expected: string; part: InvoiceCheckPart }[] = []
  const r = result.value
  if (r?.quantity) list.push({ key: 'quantity', title: t('invoices.check.quantity'), unit: r.quantity.unit ?? '', expected: t('invoices.check.measured'), part: r.quantity })
  if (r?.amount) list.push({ key: 'amount', title: t('invoices.check.amount'), unit: '€', expected: t('invoices.check.contract'), part: r.amount })
  return list
})

async function recheck() {
  error.value = ''
  await run(async () => {
    try {
      await recheckInvoice(auth.http, props.invoice.id)
      emit('checked')
    } catch (e) {
      error.value = writeErrorText(e, t)
    }
  })
}
</script>

<template>
  <section class="iso-section inv-check" :aria-labelledby="uid">
    <h3 :id="uid">{{ t('invoices.check.title') }}</h3>
    <p class="inv-check-status">
      <span class="mod-badge" :class="{ 'is-done': status === 'OK', 'is-overdue': status === 'DEVIATION', 'is-unknown': status === 'NOT_CHECKABLE' || status === 'NONE' }">
        {{ t('invoices.check.status.' + status) }}
      </span>
      <span v-if="reason" class="muted"> {{ reason }}</span>
      <span v-if="result?.partial" class="muted"> · {{ t('invoices.check.partial') }}</span>
    </p>
    <dl v-if="result && (result.contract || checkedAt)" class="mod-dl">
      <template v-if="result.contract">
        <dt>{{ t('invoices.check.contractName') }}</dt>
        <dd>{{ result.contract.name }}</dd>
      </template>
      <template v-if="checkedAt">
        <dt>{{ t('invoices.check.checkedAt') }}</dt>
        <dd>{{ checkedAt }}</dd>
      </template>
    </dl>
    <table v-if="parts.length" class="mod-table inv-check-table">
      <caption class="visually-hidden">{{ t('invoices.check.title') }}</caption>
      <thead>
        <tr>
          <th scope="col"></th>
          <th scope="col" class="num">{{ t('invoices.check.expectedCol') }}</th>
          <th scope="col" class="num">{{ t('invoices.check.invoiced') }}</th>
          <th scope="col" class="num">{{ t('invoices.check.deviation') }}</th>
          <th scope="col" class="num">{{ t('invoices.check.tolerance') }}</th>
          <th scope="col">{{ t('invoices.check.result') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in parts" :key="p.key" :class="'inv-check-' + p.key">
          <th scope="row">{{ p.title }} <span class="muted">({{ p.expected }})</span></th>
          <td class="num nowrap">{{ num(p.part.expected) }} {{ p.unit }}</td>
          <td class="num nowrap">{{ num(p.part.invoiced) }} {{ p.unit }}</td>
          <td class="num nowrap">{{ num(p.part.difference) }} {{ p.unit }}<template v-if="p.part.deviationPercent != null"> ({{ num(p.part.deviationPercent) }} %)</template></td>
          <td class="num nowrap">± {{ num(p.part.tolerancePercent) }} %</td>
          <td>
            <span class="mod-badge" :class="p.part.ok ? 'is-done' : 'is-overdue'">{{ p.part.ok ? t('invoices.check.status.OK') : t('invoices.check.status.DEVIATION') }}</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="status === 'NONE'" class="muted">{{ t('invoices.check.none') }}</p>
    <div v-if="editable && invoice.canCheck" class="edit-actions">
      <button type="button" class="edit-btn inv-recheck" :disabled="busy" @click="recheck">{{ busy ? t('invoices.check.busy') : t('invoices.check.recheck') }}</button>
    </div>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
  </section>
</template>
