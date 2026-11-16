<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { matchedRouteKey, onBeforeRouteLeave } from 'vue-router'
import {
  INVOICE_ACCEPT,
  applyParseError,
  applyParsed,
  checkedRows,
  directoryLabel,
  duplicateOf,
  importDirty,
  importSummary,
  importable,
  invoiceErrorText,
  newRows,
  parseProgress,
  parsing as anyParsing,
  preselectDirectory,
  type ImportRow,
  type ImportSummary,
} from '../../accounting/invoiceImport'
import { formatAmount, formatConsumption } from '../../accounting/invoices'
import {
  INVOICE_MEDIA,
  importInvoiceFile,
  loadInvoice,
  loadInvoiceDirectories,
  parseInvoiceFile,
  type InvoiceDetail,
  type InvoiceDirectory,
} from '../../api/invoices'
import { formatLocalDate, formatPeriod } from '../../modules/dates'
import { useNarrow } from '../../modules/useNarrow'
import { useAuthStore } from '../../stores/auth'
import { useNavigationStore } from '../../stores/navigation'
import { useNavPrefsStore } from '../../stores/navPrefs'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import InvoiceDetails from './InvoiceDetails.vue'
import '../../styles/isoEdit.css'

/*
 * "Rechnungen importieren" (E6.1), as the Control Center's InvoiceImportDialog: folder (preselected by the site
 * picker), file chooser / drop zone (PDF, XML, 20 MB each), each file parsed one at a time, then the review (import
 * checkbox, medium, status: new, duplicate with the existing invoice, error; warnings) and the import one at a
 * time with a status per row and a summary. Closing or leaving the page with chosen but not imported rows asks.
 */

const emit = defineEmits<{
  close: []
  /** at least one invoice was imported: reload the list */
  imported: []
}>()

const { t, locale } = useI18n()
const auth = useAuthStore()
const nav = useNavigationStore()
const prefs = useNavPrefsStore()
/* the review has many columns: the side panel shows cards up to very wide screens (the table needs about 1500 px) */
const narrow = useNarrow('(max-width: 1919px)')
const uid = `inv-imp-${Math.random().toString(36).slice(2, 9)}`
const id = (suffix: string) => `${uid}-${suffix}`

/* ---------- folders ---------- */

const dirs = shallowRef<InvoiceDirectory[]>([])
const dirState = ref<'loading' | 'ready' | 'error'>('loading')
const folder = ref<number | ''>('')

async function loadDirs() {
  dirState.value = 'loading'
  try {
    const answer = await loadInvoiceDirectories(auth.http, locale.value)
    dirs.value = Array.isArray(answer) ? answer : []
    if (folder.value === '' || !dirs.value.some((d) => d.id === folder.value)) folder.value = preselectDirectory(dirs.value, prefs.site, nav.data) ?? ''
    dirState.value = 'ready'
  } catch {
    dirState.value = 'error'
  }
}

/* ---------- rows: files, parse ---------- */

const rows = ref<ImportRow[]>([])
let nextKey = 1
let closed = false
let pumping = false
const over = ref(false)
const importing = ref(false)
const importDone = ref(0)
const importTotal = ref(0)
const summary = ref<ImportSummary | null>(null)

const parsingNow = computed(() => anyParsing(rows.value))
const progress = computed(() => parseProgress(rows.value))
const chosen = computed(() => checkedRows(rows.value))
const dirty = computed(() => importDirty(rows.value))

function addFiles(files: File[]) {
  if (!files.length || folder.value === '' || importing.value) return
  summary.value = null
  rows.value.push(
    ...newRows(files, nextKey, (problem, file) => t('invoiceEdit.import.problem.' + problem, { name: file.name })),
  )
  nextKey += files.length
  void pump()
}

/** parses the waiting rows one at a time (files added meanwhile join the queue) */
async function pump() {
  if (pumping) return
  pumping = true
  try {
    for (;;) {
      const row = rows.value.find((r) => r.parse === 'pending')
      if (!row || closed || folder.value === '') break
      row.parse = 'parsing'
      try {
        applyParsed(row, await parseInvoiceFile(auth.http, folder.value, row.file))
      } catch (e) {
        applyParseError(row, invoiceErrorText(e, t))
      }
    }
  } finally {
    pumping = false
  }
}

function picked(e: Event) {
  const input = e.target as HTMLInputElement
  const list = input.files ? Array.from(input.files) : []
  input.value = ''
  addFiles(list)
}

function dropped(e: DragEvent) {
  over.value = false
  addFiles(e.dataTransfer?.files ? Array.from(e.dataTransfer.files) : [])
}

function clearRows() {
  if (importing.value || parsingNow.value) return
  rows.value = []
  summary.value = null
}

/* ---------- import ---------- */

async function runImport() {
  const list = checkedRows(rows.value)
  if (!list.length || importing.value || parsingNow.value || folder.value === '') return
  const dir = folder.value
  importing.value = true
  summary.value = null
  importDone.value = 0
  importTotal.value = list.length
  try {
    for (const row of list) {
      if (closed) break
      row.result = 'importing'
      row.resultMessage = null
      try {
        const answer = await importInvoiceFile(auth.http, dir, row.medium, row.file)
        row.result = 'imported'
        row.invoiceId = typeof answer?.invoiceId === 'number' ? answer.invoiceId : null
        row.importWarnings = Array.isArray(answer?.warnings) ? answer.warnings : []
        row.checked = false
      } catch (e) {
        const existing = duplicateOf(e)
        if (existing !== undefined) {
          row.result = 'skipped'
          if (existing != null) row.existing = existing
          row.checked = false
        } else {
          row.result = 'error'
          row.resultMessage = invoiceErrorText(e, t)
        }
      }
      importDone.value++
    }
  } finally {
    importing.value = false
  }
  summary.value = importSummary(list)
  if (summary.value.imported) emit('imported')
}

/* ---------- the existing invoice of a duplicate ---------- */

const existing = shallowRef<{ id: number; state: 'loading' | 'ready' | 'error'; invoice: InvoiceDetail | null } | null>(null)

async function showExisting(invoiceId: number) {
  existing.value = { id: invoiceId, state: 'loading', invoice: null }
  try {
    const invoice = await loadInvoice(auth.http, invoiceId)
    if (existing.value?.id === invoiceId) existing.value = { id: invoiceId, state: 'ready', invoice }
  } catch {
    if (existing.value?.id === invoiceId) existing.value = { id: invoiceId, state: 'error', invoice: null }
  }
}

/* ---------- guard ---------- */

const leaveAsk = ref<((drop: boolean) => void) | null>(null)
function confirmDrop(): Promise<boolean> {
  if (!dirty.value) return Promise.resolve(true)
  leaveAsk.value?.(false)
  return new Promise((resolve) => {
    leaveAsk.value = (drop: boolean) => {
      leaveAsk.value = null
      resolve(drop)
    }
  })
}

async function requestClose() {
  if (importing.value) return
  if (await confirmDrop()) emit('close')
}

/* only inside a routed page (tests may mount the view without one) */
const routeRecord = inject(matchedRouteKey, null)
if (routeRecord?.value) onBeforeRouteLeave(() => (importing.value ? false : confirmDrop()))

function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value || importing.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  void loadDirs()
})
onBeforeUnmount(() => {
  closed = true
  window.removeEventListener('beforeunload', beforeUnload)
})

/* ---------- display ---------- */

type RowStatus = 'pending' | 'parsing' | 'new' | 'duplicate' | 'error' | 'importing' | 'imported' | 'skipped'
function status(row: ImportRow): RowStatus {
  return row.result === 'none' ? row.parse : row.result
}
const statusText = (row: ImportRow) => t('invoiceEdit.import.status.' + status(row))
const parsed = (row: ImportRow) => row.parsed
const date = (row: ImportRow) => formatLocalDate(row.parsed?.date ?? null, locale.value)
const period = (row: ImportRow) => formatPeriod(row.parsed?.periodStart ?? null, row.parsed?.periodEnd ?? null, locale.value)
const consumption = (row: ImportRow) => formatConsumption(row.parsed?.consumption?.value ?? null, row.parsed?.consumption?.unit ?? null, locale.value)
const money = (row: ImportRow, v: number | null | undefined) => {
  const text = formatAmount(v, locale.value)
  return text && row.parsed?.currency ? `${text} ${row.parsed.currency}` : text
}
const REASON_DE: [string, string][] = [
  ['unit ', 'Einheit '],
  ['line text ', 'Positionstext '],
  ['note ', 'Hinweis '],
  ['seller ', 'Lieferant '],
]
function germanReason(reason: string): string {
  for (const [en, de] of REASON_DE) if (reason.startsWith(en)) return de + reason.slice(en.length)
  return reason
}
const reasons = (row: ImportRow) => {
  const list = Array.isArray(row.parsed?.mediumReasons) ? row.parsed!.mediumReasons!.filter(Boolean) : []
  /* the server names its reasons in English ("unit kWh", "line text 'Strom'"): German labels for the German page */
  const shown = locale.value.startsWith('de') ? list.map(germanReason) : list
  return shown.length ? t('invoiceEdit.import.mediumReasons', { reasons: shown.join(', ') }) : t('invoiceEdit.import.mediumUnknown')
}
const warnings = (row: ImportRow) => [...(Array.isArray(row.parsed?.warnings) ? row.parsed!.warnings! : []), ...row.importWarnings].filter(Boolean)
const message = (row: ImportRow) => (row.result === 'error' ? row.resultMessage : row.parse === 'error' ? row.error : row.result === 'skipped' ? t('invoiceEdit.import.skippedHint') : null)
const hasSub = (row: ImportRow) => !!message(row) || warnings(row).length > 0
const editable = (row: ImportRow) => importable(row) && !importing.value
const rid = (row: ImportRow, suffix: string) => `${uid}-r${row.key}-${suffix}`
const submitLabel = computed(() => t('invoiceEdit.import.submit', { n: chosen.value.length }))
const columns = ['file', 'format', 'number', 'date', 'seller', 'period', 'consumption', 'net', 'gross', 'medium', 'status'] as const
const numeric = new Set(['consumption', 'net', 'gross'])
</script>

<template>
  <EditDialog :title="t('invoiceEdit.import.title')" :busy="importing" wide :described-by="id('hint')" class="inv-import-backdrop" @close="requestClose">
    <p :id="id('hint')" class="inv-import-hint">{{ t('invoiceEdit.import.hint') }}</p>

    <p v-if="dirState === 'loading'" class="muted" role="status">{{ t('invoiceEdit.import.foldersLoading') }}</p>
    <div v-else-if="dirState === 'error'" class="alert alert-error" role="alert">
      {{ t('invoiceEdit.import.foldersError') }}
      <button type="button" class="btn-link retry" @click="loadDirs">{{ t('modules.retry') }}</button>
    </div>
    <p v-else-if="!dirs.length" class="alert alert-error inv-import-nofolder" role="alert">{{ t('invoiceEdit.import.noFolders') }}</p>
    <div v-else class="edit-field inv-import-folder">
      <label :for="id('folder')" class="inv-import-label">{{ t('invoiceEdit.import.folder') }}</label>
      <select :id="id('folder')" v-model="folder" :disabled="rows.length > 0 || importing" :aria-describedby="rows.length ? id('locked') : undefined">
        <option v-if="folder === ''" value="">{{ t('invoiceEdit.import.folderChoose') }}</option>
        <option v-for="d in dirs" :key="d.id" :value="d.id">{{ directoryLabel(d) }}</option>
      </select>
      <p v-if="rows.length" :id="id('locked')" class="edit-hint">{{ t('invoiceEdit.import.folderLocked') }}</p>
    </div>

    <div
      v-if="dirState === 'ready' && dirs.length"
      class="iso-upload inv-drop"
      :class="{ 'is-over': over, 'is-disabled': folder === '' || importing }"
      @dragenter.prevent="over = folder !== ''"
      @dragover.prevent="over = folder !== ''"
      @dragleave.prevent="over = false"
      @drop.prevent="dropped"
    >
      <input
        :id="id('file')"
        type="file"
        multiple
        :accept="INVOICE_ACCEPT"
        class="iso-upload-input inv-file-input"
        :disabled="folder === '' || importing"
        :aria-describedby="id('drop')"
        @change="picked"
      />
      <label :for="id('file')" class="edit-btn" :aria-disabled="folder === '' || importing ? 'true' : undefined">{{ t('invoiceEdit.import.choose') }}</label>
      <p :id="id('drop')" class="edit-hint">
        {{ folder === '' ? t('invoiceEdit.import.chooseFolderFirst') : t('invoiceEdit.import.drop') }}
      </p>
    </div>

    <div v-if="parsingNow" class="inv-import-progress" role="status">
      <span>{{ t('invoiceEdit.import.parsing', { done: progress.done, total: progress.total }) }}</span>
      <progress :value="progress.done" :max="progress.total" :aria-label="t('invoiceEdit.import.parsing', { done: progress.done, total: progress.total })" />
    </div>
    <div v-if="importing" class="inv-import-progress" role="status">
      <span>{{ t('invoiceEdit.import.importing', { done: importDone, total: importTotal }) }}</span>
      <progress :value="importDone" :max="importTotal" />
    </div>
    <p v-if="summary" class="alert edit-result inv-import-summary" :class="summary.failed ? 'alert-error' : 'alert-ok'" role="status">
      {{ t('invoiceEdit.import.summary', { imported: summary.imported, skipped: summary.skipped, failed: summary.failed }) }}
    </p>

    <section v-if="rows.length" class="inv-import-review" :aria-labelledby="id('files')">
      <h3 :id="id('files')" class="inv-import-title">{{ t('invoiceEdit.import.files') }} <span class="muted">({{ rows.length }})</span></h3>

      <ul v-if="narrow" class="mod-cards inv-import-cards">
        <li v-for="row in rows" :key="row.key" class="mod-card inv-import-card" :data-status="status(row)">
          <div class="inv-import-card-head">
            <input
              :id="rid(row, 'check')"
              v-model="row.checked"
              type="checkbox"
              class="inv-import-check"
              :disabled="!editable(row)"
              :aria-label="t('invoiceEdit.import.importRow', { name: row.file.name })"
            />
            <label :for="rid(row, 'check')" class="inv-import-file">{{ row.file.name }}</label>
            <button
              v-if="row.existing != null && (status(row) === 'duplicate' || status(row) === 'skipped')"
              type="button"
              class="mod-badge inv-status inv-existing"
              :class="'inv-st-' + status(row)"
              :aria-label="t('invoiceEdit.import.duplicateLabel', { name: row.file.name })"
              @click="showExisting(row.existing)"
            >
              {{ statusText(row) }} ›
            </button>
            <span v-else class="mod-badge inv-status" :class="'inv-st-' + status(row)">{{ statusText(row) }}</span>
          </div>
          <dl v-if="parsed(row)" class="mod-dl">
            <dt>{{ t('invoiceEdit.import.col.number') }}</dt>
            <dd>{{ parsed(row)!.number || t('modules.none') }}</dd>
            <dt>{{ t('invoiceEdit.import.col.date') }}</dt>
            <dd>{{ date(row) || t('modules.none') }}</dd>
            <dt>{{ t('invoiceEdit.import.col.seller') }}</dt>
            <dd>{{ parsed(row)!.seller || t('modules.none') }}</dd>
            <dt>{{ t('invoiceEdit.import.col.period') }}</dt>
            <dd>{{ period(row) || t('modules.none') }}</dd>
            <dt>{{ t('invoiceEdit.import.col.consumption') }}</dt>
            <dd>{{ consumption(row) || t('modules.none') }}</dd>
            <dt>{{ t('invoiceEdit.import.col.net') }}</dt>
            <dd>{{ money(row, parsed(row)!.net) || t('modules.none') }}</dd>
            <dt>{{ t('invoiceEdit.import.col.gross') }}</dt>
            <dd>{{ money(row, parsed(row)!.gross) || t('modules.none') }}</dd>
            <dt>{{ t('invoiceEdit.import.col.format') }}</dt>
            <dd>{{ parsed(row)!.format || t('modules.none') }}</dd>
          </dl>
          <div v-if="parsed(row)" class="edit-field inv-import-medium">
            <label :for="rid(row, 'medium')" class="inv-import-label">{{ t('invoiceEdit.import.col.medium') }}</label>
            <select :id="rid(row, 'medium')" v-model="row.medium" :disabled="!editable(row)" :title="reasons(row)" :aria-describedby="rid(row, 'reasons')">
              <option v-for="m in INVOICE_MEDIA" :key="m" :value="m">{{ t('invoices.medium.' + m) }}</option>
            </select>
            <p :id="rid(row, 'reasons')" class="edit-hint">{{ reasons(row) }}</p>
          </div>
          <p v-if="message(row)" class="inv-import-msg" :class="{ 'iso-error': status(row) === 'error' }">{{ message(row) }}</p>
          <ul v-if="warnings(row).length" class="edit-warnings inv-import-warnings" :aria-label="t('invoiceEdit.import.warnings')">
            <li v-for="(w, i) in warnings(row)" :key="i">{{ w }}</li>
          </ul>
        </li>
      </ul>

      <div v-else class="mod-table-wrap">
        <table class="mod-table iso-table inv-import-table">
          <caption class="visually-hidden">{{ t('invoiceEdit.import.files') }}</caption>
          <thead>
            <tr>
              <th scope="col" class="inv-import-checkcol">{{ t('invoiceEdit.import.col.import') }}</th>
              <th v-for="c in columns" :key="c" scope="col" :class="{ num: numeric.has(c) }">{{ t('invoiceEdit.import.col.' + c) }}</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="row in rows" :key="row.key">
              <tr class="inv-import-row" :data-status="status(row)" :class="{ 'has-sub': hasSub(row) }">
                <td class="inv-import-checkcol">
                  <input
                    v-model="row.checked"
                    type="checkbox"
                    class="inv-import-check"
                    :disabled="!editable(row)"
                    :aria-label="t('invoiceEdit.import.importRow', { name: row.file.name })"
                  />
                </td>
                <td class="iso-text inv-import-file">{{ row.file.name }}</td>
                <td class="iso-text">{{ parsed(row)?.format }}</td>
                <td class="iso-text">{{ parsed(row)?.number }}</td>
                <td class="nowrap">{{ date(row) }}</td>
                <td class="iso-text">{{ parsed(row)?.seller }}</td>
                <td class="nowrap">{{ period(row) }}</td>
                <td class="num nowrap">{{ consumption(row) }}</td>
                <td class="num nowrap">{{ money(row, parsed(row)?.net) }}</td>
                <td class="num nowrap">{{ money(row, parsed(row)?.gross) }}</td>
                <td>
                  <select
                    v-if="parsed(row)"
                    v-model="row.medium"
                    class="inv-import-medium-select"
                    :disabled="!editable(row)"
                    :title="reasons(row)"
                    :aria-label="t('invoiceEdit.import.mediumLabel', { name: row.file.name })"
                    :aria-describedby="rid(row, 'reasons')"
                  >
                    <option v-for="m in INVOICE_MEDIA" :key="m" :value="m">{{ t('invoices.medium.' + m) }}</option>
                  </select>
                  <span v-if="parsed(row)" :id="rid(row, 'reasons')" class="visually-hidden">{{ reasons(row) }}</span>
                </td>
                <td class="nowrap">
                  <button
                    v-if="row.existing != null && (status(row) === 'duplicate' || status(row) === 'skipped')"
                    type="button"
                    class="mod-badge inv-status inv-existing"
                    :class="'inv-st-' + status(row)"
                    :aria-label="t('invoiceEdit.import.duplicateLabel', { name: row.file.name })"
                    :title="t('invoiceEdit.import.duplicateLabel', { name: row.file.name })"
                    @click="showExisting(row.existing)"
                  >
                    {{ statusText(row) }} ›
                  </button>
                  <span v-else class="mod-badge inv-status" :class="'inv-st-' + status(row)">{{ statusText(row) }}</span>
                </td>
              </tr>
              <tr v-if="hasSub(row)" class="inv-import-sub">
                <td></td>
                <td :colspan="columns.length">
                  <p v-if="message(row)" class="inv-import-msg" :class="{ 'iso-error': status(row) === 'error' }">{{ message(row) }}</p>
                  <ul v-if="warnings(row).length" class="edit-warnings inv-import-warnings" :aria-label="t('invoiceEdit.import.warnings')">
                    <li v-for="(w, i) in warnings(row)" :key="i">{{ w }}</li>
                  </ul>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </section>
    <p v-else-if="dirState === 'ready' && dirs.length" class="muted inv-import-empty">{{ t('invoiceEdit.import.empty') }}</p>

    <template #footer>
      <button type="button" class="edit-btn edit-left inv-import-clear" :disabled="!rows.length || importing || parsingNow" @click="clearRows">
        {{ t('invoiceEdit.import.clear') }}
      </button>
      <button type="button" class="edit-btn inv-import-close" :disabled="importing" @click="requestClose">{{ t('edit.close') }}</button>
      <button type="button" class="btn btn-primary edit-submit" :disabled="!chosen.length || importing || parsingNow || folder === ''" @click="runImport">
        {{ submitLabel }}
      </button>
    </template>
  </EditDialog>

  <EditDialog v-if="existing" :title="t('invoiceEdit.import.existingTitle')" wide class="inv-import-backdrop inv-existing-dialog" @close="existing = null">
    <p v-if="existing.state === 'loading'" class="muted" role="status">{{ t('invoiceEdit.import.existingLoading') }}</p>
    <div v-else-if="existing.state === 'error'" class="alert alert-error" role="alert">
      {{ t('invoiceEdit.import.existingError') }}
      <button type="button" class="btn-link retry" @click="showExisting(existing.id)">{{ t('modules.retry') }}</button>
    </div>
    <InvoiceDetails v-else-if="existing.invoice" :invoice="existing.invoice" />
  </EditDialog>

  <ConfirmDialog
    v-if="leaveAsk"
    :title="t('invoiceEdit.import.leave.title')"
    :message="t('invoiceEdit.import.leave.message')"
    :confirm-label="t('invoiceEdit.import.leave.confirm')"
    danger
    @confirm="leaveAsk?.(true)"
    @cancel="leaveAsk?.(false)"
  />
</template>
