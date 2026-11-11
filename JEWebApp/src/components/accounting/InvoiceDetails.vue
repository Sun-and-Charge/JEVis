<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NUMERIC_ITEM_KEYS,
  amountWithCurrency,
  formatConsumption,
  invoiceDate,
  invoiceFileName,
  invoiceMedium,
  invoicePeriod,
  isPdfFile,
  itemCell,
  itemColumns,
} from '../../accounting/invoices'
import { HttpError } from '../../api/http'
import { loadInvoice, loadInvoiceFile, type Invoice, type InvoiceDetail } from '../../api/invoices'
import { saveBlob } from '../../iso/download'
import { useAuthStore } from '../../stores/auth'

/*
 * Detail panel of an energy invoice: all fields, the line items (GET /web/invoices/{id}) and the original file
 * (PDF: inline preview through an object URL like the reports, revoked when closed; XML: download only).
 */

const props = defineProps<{ invoice: Invoice }>()
const { t, te, locale } = useI18n()
const auth = useAuthStore()

type LoadState = 'none' | 'loading' | 'ready' | 'error' | 'notFound'
const errorState = (e: unknown): LoadState => (e instanceof HttpError && e.status === 404 ? 'notFound' : 'error')

/* ---------- detail with line items ---------- */

const detail = shallowRef<InvoiceDetail | null>(null)
const state = ref<LoadState>('loading')
let seq = 0

async function load() {
  const current = ++seq
  state.value = 'loading'
  try {
    const answer = await loadInvoice(auth.http, props.invoice.id)
    if (current !== seq) return
    detail.value = answer
    state.value = 'ready'
  } catch (e) {
    if (current === seq) state.value = errorState(e)
  }
}

onMounted(load)

/** the fields of the list until the detail is there */
const inv = computed<Invoice & Partial<InvoiceDetail>>(() => ({ ...props.invoice, ...(detail.value ?? {}) }))
const items = computed(() => (Array.isArray(detail.value?.items) ? detail.value!.items : []))
const columns = computed(() => itemColumns(items.value))
const itemLabel = (key: string) => (te('invoices.item.' + key) ? t('invoices.item.' + key) : key)
const cell = (item: Record<string, unknown>, key: string) => itemCell(item, key, locale.value)

const medium = computed(() => t('invoices.medium.' + invoiceMedium(inv.value)))
const money = (v: number | null | undefined) => amountWithCurrency(v, inv.value.currency, locale.value) || t('modules.none')
const where = computed(() => [inv.value.site?.name, inv.value.path].filter(Boolean).join(' / '))

/* ---------- file ---------- */

const hasFile = computed(() => !!props.invoice.file)
const pdf = computed(() => isPdfFile(props.invoice.file))
const fileName = computed(() => invoiceFileName(props.invoice))

const previewUrl = ref<string | null>(null)
const previewState = ref<LoadState>('none')
let previewSeq = 0

function releasePreview() {
  if (previewUrl.value && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = null
}

async function showPreview() {
  const current = ++previewSeq
  releasePreview()
  previewState.value = 'loading'
  try {
    let blob = await loadInvoiceFile(auth.http, props.invoice.id, true)
    if (current !== previewSeq) return
    if (typeof URL.createObjectURL !== 'function') throw new Error('no blob URLs')
    if (blob.type !== 'application/pdf') blob = new Blob([blob], { type: 'application/pdf' })
    previewUrl.value = URL.createObjectURL(blob)
    previewState.value = 'ready'
  } catch (e) {
    if (current === previewSeq) previewState.value = errorState(e)
  }
}

function hidePreview() {
  previewSeq++
  releasePreview()
  previewState.value = 'none'
}

const busy = ref(false)
const downloadError = ref<'error' | 'notFound' | null>(null)

async function download() {
  busy.value = true
  downloadError.value = null
  try {
    const blob = await loadInvoiceFile(auth.http, props.invoice.id)
    saveBlob(blob, fileName.value)
  } catch (e) {
    downloadError.value = errorState(e) === 'notFound' ? 'notFound' : 'error'
  } finally {
    busy.value = false
  }
}

onBeforeUnmount(() => {
  seq++
  previewSeq++
  releasePreview()
})

const id = (suffix: string) => `invoice-${props.invoice.id}-${suffix}`
</script>

<template>
  <div class="iso-sections inv-details">
    <section class="iso-section" :aria-labelledby="id('general')">
      <h3 :id="id('general')">
        {{ t('invoices.detail.title', { number: inv.number || '#' + inv.id }) }}
        <span v-if="inv.credit" class="mod-badge inv-credit">{{ t('invoices.credit') }}</span>
      </h3>
      <dl class="mod-dl">
        <dt>{{ t('invoices.col.number') }}</dt>
        <dd>{{ inv.number || t('modules.none') }}</dd>
        <dt>{{ t('invoices.col.date') }}</dt>
        <dd>{{ invoiceDate(inv, locale) || t('modules.none') }}</dd>
        <dt>{{ t('invoices.col.period') }}</dt>
        <dd>{{ invoicePeriod(inv, locale) || t('modules.none') }}</dd>
        <dt>{{ t('invoices.col.medium') }}</dt>
        <dd>{{ medium }}</dd>
        <dt>{{ t('invoices.col.seller') }}</dt>
        <dd>{{ inv.seller || t('modules.none') }}</dd>
        <template v-if="inv.sellerVatId">
          <dt>{{ t('invoices.col.sellerVatId') }}</dt>
          <dd>{{ inv.sellerVatId }}</dd>
        </template>
        <dt>{{ t('invoices.col.buyer') }}</dt>
        <dd>{{ inv.buyer || t('modules.none') }}</dd>
        <dt>{{ t('invoices.col.meterPoint') }}</dt>
        <dd class="inv-mono">{{ inv.meterPoint || t('modules.none') }}</dd>
        <dt>{{ t('invoices.col.consumption') }}</dt>
        <dd :class="{ 'is-negative': (inv.consumption ?? 0) < 0 }">{{ formatConsumption(inv.consumption, inv.consumptionUnit, locale) || t('modules.none') }}</dd>
        <dt>{{ t('invoices.col.net') }}</dt>
        <dd :class="{ 'is-negative': (inv.net ?? 0) < 0 }">{{ money(inv.net) }}</dd>
        <dt>{{ t('invoices.col.tax') }}</dt>
        <dd :class="{ 'is-negative': (inv.tax ?? 0) < 0 }">{{ money(inv.tax) }}</dd>
        <dt>{{ t('invoices.col.gross') }}</dt>
        <dd :class="{ 'is-negative': (inv.gross ?? 0) < 0 }">{{ money(inv.gross) }}</dd>
        <template v-if="inv.format">
          <dt>{{ t('invoices.col.format') }}</dt>
          <dd>{{ inv.format }}</dd>
        </template>
        <template v-if="where">
          <dt>{{ t('invoices.col.where') }}</dt>
          <dd>{{ where }}</dd>
        </template>
      </dl>
    </section>

    <section class="iso-section inv-file" :aria-labelledby="id('file')">
      <h3 :id="id('file')">{{ t('invoices.file.title') }}</h3>
      <p v-if="!hasFile" class="muted">{{ t('invoices.file.none') }}</p>
      <template v-else>
        <p class="inv-file-name">{{ invoice.file }}</p>
        <div class="rep-actions">
          <button v-if="pdf && previewState === 'none'" type="button" class="notes-btn inv-show" @click="showPreview">{{ t('invoices.file.show') }}</button>
          <button v-else-if="pdf" type="button" class="notes-btn inv-hide" :aria-controls="id('preview')" @click="hidePreview">{{ t('invoices.file.hide') }}</button>
          <button
            type="button"
            class="notes-btn inv-download"
            :disabled="busy"
            :aria-label="t('invoices.file.downloadLabel', { name: fileName })"
            @click="download"
          >
            {{ t('invoices.file.download') }}
          </button>
        </div>
        <p v-if="busy" class="muted rep-hint" role="status">{{ t('invoices.file.busy') }}</p>
        <p v-if="downloadError" class="iso-error rep-hint" role="alert">{{ t('invoices.file.' + downloadError) }}</p>
      </template>
    </section>

    <section v-if="pdf && previewState !== 'none'" :id="id('preview')" class="iso-section inv-preview" :aria-label="t('invoices.file.previewTitle')">
      <p v-if="previewState === 'loading'" class="muted" role="status">{{ t('invoices.file.previewLoading') }}</p>
      <div v-else-if="previewState === 'notFound' || previewState === 'error'" class="alert alert-error" role="alert">
        {{ t('invoices.file.' + (previewState === 'notFound' ? 'notFound' : 'previewError')) }}
        <button type="button" class="btn-link retry" @click="showPreview">{{ t('modules.retry') }}</button>
      </div>
      <template v-else-if="previewState === 'ready' && previewUrl">
        <object :key="previewUrl" :data="previewUrl" type="application/pdf" class="rep-pdf" :title="t('invoices.file.previewLabel', { name: fileName })">
          <p class="rep-pdf-fallback">
            {{ t('invoices.file.unsupported') }}
            <a :href="previewUrl" target="_blank" rel="noopener">{{ t('invoices.file.openTab') }}</a>
          </p>
        </object>
        <p class="rep-hint">
          <a :href="previewUrl" target="_blank" rel="noopener" class="inv-open">{{ t('invoices.file.openTab') }}</a>
        </p>
      </template>
    </section>

    <section class="iso-section inv-items-section" :aria-labelledby="id('items')">
      <h3 :id="id('items')">{{ t('invoices.detail.items') }}</h3>
      <p v-if="state === 'loading'" class="muted" role="status">{{ t('invoices.detail.loading') }}</p>
      <p v-else-if="state === 'notFound'" class="alert alert-error" role="alert">{{ t('invoices.detail.notFound') }}</p>
      <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
        {{ t('invoices.detail.error') }}
        <button type="button" class="btn-link retry" @click="load">{{ t('modules.retry') }}</button>
      </div>
      <p v-else-if="!items.length || !columns.length" class="muted">{{ t('invoices.detail.noItems') }}</p>
      <div v-else class="iso-mini-wrap">
        <table class="iso-mini inv-items">
          <caption class="visually-hidden">{{ t('invoices.detail.items') }}</caption>
          <thead>
            <tr>
              <th v-for="c in columns" :key="c" scope="col" :class="{ num: NUMERIC_ITEM_KEYS.has(c) }">{{ itemLabel(c) }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, i) in items" :key="i">
              <td v-for="c in columns" :key="c" :class="{ num: NUMERIC_ITEM_KEYS.has(c), nowrap: NUMERIC_ITEM_KEYS.has(c) || c === 'period' }">{{ cell(item, c) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
