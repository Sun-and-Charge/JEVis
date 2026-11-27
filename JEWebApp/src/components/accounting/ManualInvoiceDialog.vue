<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { directoryLabel, duplicateOf, invoiceErrorText, preselectDirectory } from '../../accounting/invoiceImport'
import { formatAmount, formatConsumption } from '../../accounting/invoices'
import {
  ENERGY_UNITS,
  LINE_UNITS,
  TAX_RATES,
  checkManual,
  effectiveTotals,
  lineNet,
  manualBody,
  manualDirty,
  newLine,
  newManualForm,
  type ManualForm,
} from '../../accounting/manualInvoice'
import { INVOICE_MEDIA, loadInvoiceDirectories, saveManualInvoice, type InvoiceDirectory } from '../../api/invoices'
import { useBusy } from '../../edit/edit'
import { useLeaveGuard } from '../../edit/useLeaveGuard'
import { useAuthStore } from '../../stores/auth'
import { useNavigationStore } from '../../stores/navigation'
import { useNavPrefsStore } from '../../stores/navPrefs'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import '../../styles/edit.css'
import '../../styles/isoEdit.css'
import '../../styles/accounting.css'

/*
 * "Rechnung erfassen" (R01): an invoice without e-invoice file, typed from a paper or PDF invoice, with an optional
 * attachment (PDF or scan). Stored like an imported e-invoice (format MANUAL): it appears in the register, the
 * invoice series and the cost calculation; a second invoice with the same supplier and number is refused (409).
 */

const emit = defineEmits<{ close: []; saved: [id: number] }>()
const { t, locale } = useI18n()
const auth = useAuthStore()
const nav = useNavigationStore()
const prefs = useNavPrefsStore()
const { busy, run } = useBusy()
const uid = `inv-man-${Math.random().toString(36).slice(2, 9)}`
const id = (suffix: string) => `${uid}-${suffix}`

const dirs = shallowRef<InvoiceDirectory[]>([])
const dirState = ref<'loading' | 'ready' | 'error'>('loading')
const form = ref<ManualForm>(newManualForm())
const file = ref<File | null>(null)
const tried = ref(false)
const error = ref('')
const duplicate = ref(false)

async function loadDirs() {
  dirState.value = 'loading'
  try {
    const answer = await loadInvoiceDirectories(auth.http, locale.value)
    dirs.value = Array.isArray(answer) ? answer : []
    if (form.value.directory === '') form.value.directory = preselectDirectory(dirs.value, prefs.site, nav.data) ?? ''
    dirState.value = 'ready'
  } catch {
    dirState.value = 'error'
  }
}
onMounted(loadDirs)

const problems = computed(() => checkManual(form.value))
const shown = (key: string) => (tried.value ? problems.value.get(key) : undefined)
const problemText = (key: string) => {
  const p = shown(key)
  return p ? t('invoiceEdit.manual.problem.' + p.replace(':', '_')) : ''
}
const totals = computed(() => effectiveTotals(form.value))
const dirty = computed(() => manualDirty(form.value) || file.value != null)
const { leaveAsk, confirmDrop } = useLeaveGuard(dirty)

const amount = (n: number | null) => (n == null ? '–' : formatAmount(n, locale.value) + ' €')
const lineAmount = (i: number) => {
  const n = lineNet(form.value.lines[i])
  return n == null ? '' : formatAmount(n, locale.value)
}

function addLine() {
  form.value.lines.push(newLine())
}
function removeLine(i: number) {
  form.value.lines.splice(i, 1)
}
function pickFile(e: Event) {
  const input = e.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
}

async function requestClose() {
  if (busy.value) return
  if (await confirmDrop()) emit('close')
}

async function save() {
  tried.value = true
  error.value = ''
  duplicate.value = false
  if (problems.value.size) return
  await run(async () => {
    try {
      const answer = await saveManualInvoice(auth.http, form.value.directory as number, manualBody(form.value), file.value)
      form.value = newManualForm(form.value.directory)
      file.value = null
      emit('saved', answer.invoiceId)
    } catch (e) {
      if (duplicateOf(e) !== undefined) duplicate.value = true
      else error.value = invoiceErrorText(e, t)
    }
  })
}
</script>

<template>
  <EditDialog :title="t('invoiceEdit.manual.title')" :busy="busy" wide :described-by="id('hint')" class="inv-import-backdrop inv-manual-backdrop" @close="requestClose">
    <p :id="id('hint')" class="inv-import-hint">{{ t('invoiceEdit.manual.hint') }}</p>
    <form :id="id('form')" class="inv-manual-form" novalidate @submit.prevent="save">
      <p v-if="dirState === 'loading'" class="muted" role="status">{{ t('invoiceEdit.import.foldersLoading') }}</p>
      <div v-else-if="dirState === 'error'" class="alert alert-error" role="alert">
        {{ t('invoiceEdit.import.foldersError') }}
        <button type="button" class="btn-link retry" @click="loadDirs">{{ t('modules.retry') }}</button>
      </div>
      <p v-else-if="!dirs.length" class="alert alert-error" role="alert">{{ t('invoiceEdit.import.noFolders') }}</p>

      <h3 class="inv-manual-head">{{ t('invoiceEdit.manual.section.invoice') }}</h3>
      <div class="inv-manual-grid">
        <div class="edit-field">
          <label :for="id('folder')">{{ t('invoiceEdit.import.folder') }} *</label>
          <select :id="id('folder')" v-model="form.directory" :disabled="busy" :aria-invalid="shown('directory') ? 'true' : undefined">
            <option value="">{{ t('invoiceEdit.import.folderChoose') }}</option>
            <option v-for="d in dirs" :key="d.id" :value="d.id">{{ directoryLabel(d) }}</option>
          </select>
          <p v-if="shown('directory')" class="iso-field-error">{{ problemText('directory') }}</p>
        </div>
        <div class="edit-field">
          <label :for="id('medium')">{{ t('invoiceEdit.import.col.medium') }} *</label>
          <select :id="id('medium')" v-model="form.medium" :disabled="busy" :aria-invalid="shown('medium') ? 'true' : undefined">
            <option value="">{{ t('invoiceEdit.manual.choose') }}</option>
            <option v-for="m in INVOICE_MEDIA" :key="m" :value="m">{{ t('invoices.medium.' + m) }}</option>
          </select>
          <p v-if="shown('medium')" class="iso-field-error">{{ problemText('medium') }}</p>
        </div>
        <div class="edit-field">
          <label :for="id('seller')">{{ t('invoiceEdit.import.col.seller') }} *</label>
          <input :id="id('seller')" v-model="form.seller" type="text" maxlength="255" :disabled="busy" :aria-invalid="shown('seller') ? 'true' : undefined" />
          <p v-if="shown('seller')" class="iso-field-error">{{ problemText('seller') }}</p>
        </div>
        <div class="edit-field">
          <label :for="id('vat')">{{ t('invoiceEdit.manual.vat') }}</label>
          <input :id="id('vat')" v-model="form.sellerVatId" type="text" maxlength="40" :disabled="busy" />
        </div>
        <div class="edit-field">
          <label :for="id('number')">{{ t('invoiceEdit.import.col.number') }} *</label>
          <input :id="id('number')" v-model="form.number" type="text" maxlength="255" :disabled="busy" :aria-invalid="shown('number') ? 'true' : undefined" />
          <p v-if="shown('number')" class="iso-field-error">{{ problemText('number') }}</p>
        </div>
        <div class="edit-field">
          <label :for="id('date')">{{ t('invoiceEdit.import.col.date') }} *</label>
          <input :id="id('date')" v-model="form.date" type="date" :disabled="busy" :aria-invalid="shown('date') ? 'true' : undefined" />
          <p v-if="shown('date')" class="iso-field-error">{{ problemText('date') }}</p>
        </div>
        <div class="edit-field">
          <label :for="id('from')">{{ t('invoiceEdit.manual.periodStart') }}</label>
          <input :id="id('from')" v-model="form.periodStart" type="date" :disabled="busy" :aria-invalid="shown('period') ? 'true' : undefined" />
        </div>
        <div class="edit-field">
          <label :for="id('to')">{{ t('invoiceEdit.manual.periodEnd') }}</label>
          <input :id="id('to')" v-model="form.periodEnd" type="date" :disabled="busy" :aria-invalid="shown('period') ? 'true' : undefined" />
          <p v-if="shown('period')" class="iso-field-error">{{ problemText('period') }}</p>
        </div>
        <div class="edit-field inv-manual-wide">
          <label :for="id('meter')">{{ t('invoiceEdit.manual.meterPoint') }}</label>
          <input :id="id('meter')" v-model="form.meterPoint" type="text" maxlength="40" :disabled="busy" :aria-describedby="id('meter-hint')" />
          <p :id="id('meter-hint')" class="edit-hint">{{ t('invoiceEdit.manual.meterPointHint') }}</p>
        </div>
        <label class="inv-manual-check inv-manual-wide">
          <input v-model="form.creditNote" type="checkbox" :disabled="busy" />
          <span>{{ t('invoiceEdit.manual.creditNote') }}</span>
        </label>
      </div>

      <h3 class="inv-manual-head">{{ t('invoiceEdit.manual.section.lines') }}</h3>
      <p class="edit-hint">{{ t('invoiceEdit.manual.linesHint') }}</p>
      <div class="inv-manual-lines" role="group" :aria-label="t('invoiceEdit.manual.section.lines')">
        <div v-for="(line, i) in form.lines" :key="line.uid" class="inv-manual-line">
          <div class="edit-field inv-line-desc">
            <label :for="id(line.uid + '-d')">{{ t('invoiceEdit.manual.line.description') }}</label>
            <input :id="id(line.uid + '-d')" v-model="line.description" type="text" maxlength="255" :disabled="busy" />
            <p v-if="shown(`lines[${i}].description`)" class="iso-field-error">{{ problemText(`lines[${i}].description`) }}</p>
          </div>
          <div class="edit-field">
            <label :for="id(line.uid + '-q')">{{ t('invoiceEdit.manual.line.quantity') }}</label>
            <input :id="id(line.uid + '-q')" v-model="line.quantity" type="text" inputmode="decimal" :disabled="busy" />
            <p v-if="shown(`lines[${i}].quantity`)" class="iso-field-error">{{ problemText(`lines[${i}].quantity`) }}</p>
          </div>
          <div class="edit-field">
            <label :for="id(line.uid + '-u')">{{ t('invoiceEdit.manual.line.unit') }}</label>
            <select :id="id(line.uid + '-u')" v-model="line.unit" :disabled="busy">
              <option value="">–</option>
              <option v-for="u in LINE_UNITS" :key="u" :value="u">{{ t('invoiceEdit.manual.unit.' + u) }}</option>
            </select>
          </div>
          <div class="edit-field">
            <label :for="id(line.uid + '-p')">{{ t('invoiceEdit.manual.line.unitPrice') }}</label>
            <input :id="id(line.uid + '-p')" v-model="line.unitPrice" type="text" inputmode="decimal" :disabled="busy" />
            <p v-if="shown(`lines[${i}].unitPrice`)" class="iso-field-error">{{ problemText(`lines[${i}].unitPrice`) }}</p>
          </div>
          <div class="edit-field">
            <label :for="id(line.uid + '-n')">{{ t('invoiceEdit.manual.line.net') }}</label>
            <input :id="id(line.uid + '-n')" v-model="line.net" type="text" inputmode="decimal" :placeholder="lineAmount(i)" :disabled="busy" />
            <p v-if="shown(`lines[${i}].net`)" class="iso-field-error">{{ problemText(`lines[${i}].net`) }}</p>
          </div>
          <div class="edit-field">
            <label :for="id(line.uid + '-t')">{{ t('invoiceEdit.manual.line.taxRate') }}</label>
            <select :id="id(line.uid + '-t')" v-model="line.taxRate" :disabled="busy">
              <option v-for="r in TAX_RATES" :key="r" :value="r">{{ r }} %</option>
            </select>
          </div>
          <button type="button" class="edit-btn inv-line-remove" :disabled="busy" :aria-label="t('invoiceEdit.manual.removeLine', { n: i + 1 })" @click="removeLine(i)">×</button>
          <span v-if="ENERGY_UNITS.includes(line.unit)" class="mod-badge inv-line-energy">{{ t('invoiceEdit.manual.energyLine') }}</span>
        </div>
      </div>
      <p v-if="shown('lines')" class="iso-field-error">{{ problemText('lines') }}</p>
      <div class="edit-actions">
        <button type="button" class="edit-btn inv-line-add" :disabled="busy || form.lines.length >= 200" @click="addLine">{{ t('invoiceEdit.manual.addLine') }}</button>
      </div>

      <h3 class="inv-manual-head">{{ t('invoiceEdit.manual.section.totals') }}</h3>
      <dl class="mod-dl inv-manual-totals" aria-live="polite">
        <dt>{{ t('invoiceEdit.import.col.net') }}</dt>
        <dd class="inv-total-net">{{ amount(totals.net) }}</dd>
        <dt>{{ t('invoiceEdit.manual.tax') }}</dt>
        <dd class="inv-total-tax">{{ amount(totals.tax) }}</dd>
        <dt>{{ t('invoiceEdit.import.col.gross') }}</dt>
        <dd class="inv-total-gross">{{ amount(totals.gross) }}</dd>
        <dt>{{ t('invoiceEdit.import.col.consumption') }}</dt>
        <dd class="inv-total-consumption">{{ totals.consumption ? formatConsumption(totals.consumption.value, totals.consumption.unit, locale) : t('invoiceEdit.manual.noConsumption') }}</dd>
      </dl>
      <label class="inv-manual-check">
        <input v-model="form.ownTotals" type="checkbox" :disabled="busy" />
        <span>{{ t('invoiceEdit.manual.ownTotals') }}</span>
      </label>
      <div v-if="form.ownTotals" class="inv-manual-grid">
        <div class="edit-field">
          <label :for="id('net')">{{ t('invoiceEdit.import.col.net') }}</label>
          <input :id="id('net')" v-model="form.net" type="text" inputmode="decimal" :disabled="busy" />
        </div>
        <div class="edit-field">
          <label :for="id('tax')">{{ t('invoiceEdit.manual.tax') }}</label>
          <input :id="id('tax')" v-model="form.tax" type="text" inputmode="decimal" :disabled="busy" />
        </div>
        <div class="edit-field">
          <label :for="id('gross')">{{ t('invoiceEdit.import.col.gross') }}</label>
          <input :id="id('gross')" v-model="form.gross" type="text" inputmode="decimal" :disabled="busy" />
        </div>
      </div>
      <p v-if="shown('totals')" class="iso-field-error">{{ problemText('totals') }}</p>
      <label class="inv-manual-check">
        <input v-model="form.ownConsumption" type="checkbox" :disabled="busy" />
        <span>{{ t('invoiceEdit.manual.ownConsumption') }}</span>
      </label>
      <div v-if="form.ownConsumption" class="inv-manual-grid">
        <div class="edit-field">
          <label :for="id('cons')">{{ t('invoiceEdit.import.col.consumption') }}</label>
          <input :id="id('cons')" v-model="form.consumption" type="text" inputmode="decimal" :disabled="busy" />
          <p v-if="shown('consumption')" class="iso-field-error">{{ problemText('consumption') }}</p>
        </div>
        <div class="edit-field">
          <label :for="id('cons-unit')">{{ t('invoiceEdit.manual.line.unit') }}</label>
          <select :id="id('cons-unit')" v-model="form.consumptionUnit" :disabled="busy">
            <option v-for="u in ENERGY_UNITS" :key="u" :value="u">{{ t('invoiceEdit.manual.unit.' + u) }}</option>
          </select>
        </div>
      </div>

      <h3 class="inv-manual-head">{{ t('invoiceEdit.manual.section.file') }}</h3>
      <div class="edit-field">
        <label :for="id('file')">{{ t('invoiceEdit.manual.file') }}</label>
        <input :id="id('file')" type="file" accept=".pdf,.png,.jpg,.jpeg,.tif,.tiff,application/pdf,image/*" :disabled="busy" @change="pickFile" />
        <p class="edit-hint">{{ t('invoiceEdit.manual.fileHint') }}</p>
      </div>

      <p v-if="tried && problems.size" class="alert alert-error" role="alert">{{ t('invoiceEdit.manual.fix') }}</p>
      <p v-if="duplicate" class="alert alert-error inv-manual-duplicate" role="alert">{{ t('invoiceEdit.manual.duplicate') }}</p>
      <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>

    <template #footer>
      <button type="button" class="edit-btn inv-manual-close" :disabled="busy" @click="requestClose">{{ dirty ? t('edit.cancel') : t('edit.close') }}</button>
      <button type="submit" class="btn btn-primary edit-submit" :form="id('form')" :disabled="busy || dirState !== 'ready' || !dirs.length">
        {{ busy ? t('edit.saving') : t('invoiceEdit.manual.save') }}
      </button>
    </template>
  </EditDialog>

  <ConfirmDialog
    v-if="leaveAsk"
    :title="t('registerEdit.leave.title')"
    :message="t('registerEdit.leave.message')"
    :confirm-label="t('registerEdit.leave.confirm')"
    danger
    @confirm="leaveAsk?.(true)"
    @cancel="leaveAsk?.(false)"
  />
</template>

<style scoped>
.inv-manual-head {
  font-size: 0.95rem;
  margin: 1rem 0 0.4rem;
}

.inv-manual-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 14rem), 1fr));
  gap: 0.5rem 0.9rem;
}

.inv-manual-wide {
  grid-column: 1 / -1;
}

.inv-manual-check {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  margin: 0.4rem 0;
}

.inv-manual-lines {
  display: grid;
  gap: 0.6rem;
}

.inv-manual-line {
  display: grid;
  grid-template-columns: minmax(10rem, 2.4fr) repeat(5, minmax(5.5rem, 1fr)) auto;
  gap: 0.4rem;
  align-items: end;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--line);
}

.inv-line-energy {
  grid-column: 1 / -1;
  justify-self: start;
}

.inv-line-remove {
  padding: 0.35rem 0.6rem;
}

@media (max-width: 900px) {
  .inv-manual-line {
    grid-template-columns: 1fr 1fr;
  }

  .inv-line-desc {
    grid-column: 1 / -1;
  }
}
</style>
