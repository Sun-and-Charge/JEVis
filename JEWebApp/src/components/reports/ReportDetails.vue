<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTime } from '../../analysis/format'
import { HttpError } from '../../api/http'
import { loadReportFile, loadReportFiles, type ReportFormat, type ReportOutput, type ReportSummary } from '../../api/reports'
import { saveBlob } from '../../iso/download'
import { hasOutput, outputFileName, outputLabel, outputTs, scheduleLabel, sortOutputs } from '../../reports/reports'
import { useAuthStore } from '../../stores/auth'

/*
 * The selected report: schedule and state, the outputs (newest first) with PDF / Excel download and an inline PDF
 * preview. The files are loaded with the Bearer token as blob and shown / saved through object URLs that are
 * revoked when the output changes or the panel goes away.
 */

const props = defineProps<{ report: ReportSummary; headingId?: string }>()
const { t, locale } = useI18n()
const auth = useAuthStore()

type LoadState = 'none' | 'loading' | 'ready' | 'error' | 'notFound'

const formatTime = (ms: number) => formatDateTime(ms, locale.value)
const schedule = computed(() => scheduleLabel(props.report.schedule, (k) => t(k)))
const where = computed(() => [props.report.site?.name, props.report.path].filter(Boolean).join(' / '))
const errorState = (e: unknown): LoadState => (e instanceof HttpError && e.status === 404 ? 'notFound' : 'error')

/* ---------- outputs ---------- */

const outputs = shallowRef<ReportOutput[]>([])
const filesState = ref<LoadState>('none')
const selectedTs = ref<string | null>(null)
let filesSeq = 0

async function loadFiles() {
  const current = ++filesSeq
  outputs.value = []
  selectedTs.value = null
  if (!hasOutput(props.report)) {
    filesState.value = 'none'
    return
  }
  filesState.value = 'loading'
  try {
    const answer = await loadReportFiles(auth.http, props.report.id)
    if (current !== filesSeq) return
    outputs.value = sortOutputs(answer.files ?? [])
    selectedTs.value = outputs.value[0]?.timestamp ?? null
    filesState.value = outputs.value.length ? 'ready' : 'none'
  } catch (e) {
    if (current === filesSeq) filesState.value = errorState(e)
  }
}

watch(() => props.report.id, loadFiles, { immediate: true })

const output = computed(() => outputs.value.find((o) => o.timestamp === selectedTs.value) ?? null)

/* ---------- PDF preview ---------- */

const previewUrl = ref<string | null>(null)
const previewState = ref<LoadState>('none')
let previewSeq = 0

function releasePreview() {
  if (previewUrl.value && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = null
}

async function loadPreview() {
  const current = ++previewSeq
  releasePreview()
  const o = output.value
  if (!o) {
    previewState.value = 'none'
    return
  }
  previewState.value = 'loading'
  try {
    let blob = await loadReportFile(auth.http, props.report.id, 'pdf', o.timestamp, true)
    if (current !== previewSeq) return
    if (typeof URL.createObjectURL !== 'function') throw new Error('no blob URLs')
    if (blob.type !== 'application/pdf') blob = new Blob([blob], { type: 'application/pdf' })
    previewUrl.value = URL.createObjectURL(blob)
    previewState.value = 'ready'
  } catch (e) {
    if (current === previewSeq) previewState.value = errorState(e)
  }
}

watch(output, loadPreview, { immediate: true })

onBeforeUnmount(() => {
  filesSeq++
  previewSeq++
  releasePreview()
})

/* ---------- downloads ---------- */

const busy = ref<ReportFormat | null>(null)
const downloadError = ref<'error' | 'notFound' | null>(null)

async function download(format: ReportFormat) {
  const o = output.value
  const ts = o ? outputTs(o, format) : null
  if (!o || !ts) return
  busy.value = format
  downloadError.value = null
  try {
    const blob = await loadReportFile(auth.http, props.report.id, format, ts)
    saveBlob(blob, outputFileName(o, format, props.report.name))
  } catch (e) {
    downloadError.value = errorState(e) === 'notFound' ? 'notFound' : 'error'
  } finally {
    busy.value = null
  }
}

watch(output, () => {
  downloadError.value = null
})

const label = (o: ReportOutput) => outputLabel(o, formatTime)
const domId = (suffix: string) => `report-${props.report.id}-${suffix}`
</script>

<template>
  <article class="rep-detail" :aria-labelledby="headingId ?? domId('title')">
    <p v-if="where" class="rep-where muted">{{ where }}</p>
    <h2 :id="headingId ?? domId('title')" class="rep-title" tabindex="-1">{{ report.name }}</h2>

    <dl class="mod-dl rep-meta">
      <dt>{{ t('reports.detail.schedule') }}</dt>
      <dd>{{ schedule || t('modules.none') }}</dd>
      <dt>{{ t('reports.detail.state') }}</dt>
      <dd>
        <span class="mod-badge rep-state" :class="report.enabled ? 'is-enabled' : 'is-disabled'">
          {{ t(report.enabled ? 'reports.enabled' : 'reports.disabled') }}
        </span>
      </dd>
      <dt>{{ t('reports.detail.outputs') }}</dt>
      <dd>{{ report.count }}</dd>
    </dl>

    <p v-if="filesState === 'none'" class="muted rep-none">{{ t('reports.detail.noOutput') }}</p>
    <p v-else-if="filesState === 'loading'" class="muted" role="status">{{ t('reports.detail.loading') }}</p>
    <div v-else-if="filesState === 'notFound'" class="alert alert-error" role="alert">
      {{ t('reports.detail.notFound') }}
      <button type="button" class="btn-link retry" @click="loadFiles">{{ t('modules.retry') }}</button>
    </div>
    <div v-else-if="filesState === 'error'" class="alert alert-error" role="alert">
      {{ t('reports.detail.error') }}
      <button type="button" class="btn-link retry" @click="loadFiles">{{ t('modules.retry') }}</button>
    </div>

    <template v-else-if="output">
      <div class="rep-output">
        <div class="control rep-output-select">
          <label :for="domId('output')">{{ t('reports.detail.output') }}</label>
          <select :id="domId('output')" v-model="selectedTs">
            <option v-for="o in outputs" :key="o.timestamp" :value="o.timestamp">{{ label(o) }}</option>
          </select>
        </div>
        <div class="rep-actions">
          <button
            type="button"
            class="notes-btn rep-download"
            data-format="pdf"
            :disabled="busy != null"
            :aria-label="t('reports.download.pdfLabel', { name: output.name || report.name })"
            @click="download('pdf')"
          >
            {{ t('reports.download.pdf') }}
          </button>
          <button
            type="button"
            class="notes-btn rep-download"
            data-format="xlsx"
            :disabled="busy != null || !output.xlsx"
            :title="output.xlsx ? undefined : t('reports.download.noXlsx')"
            :aria-label="t('reports.download.xlsxLabel', { name: output.name || report.name })"
            :aria-describedby="output.xlsx ? undefined : domId('noxlsx')"
            @click="download('xlsx')"
          >
            {{ t('reports.download.xlsx') }}
          </button>
        </div>
        <p v-if="!output.xlsx" :id="domId('noxlsx')" class="muted rep-hint">{{ t('reports.download.noXlsx') }}</p>
        <p v-if="busy" class="muted rep-hint" role="status">{{ t('reports.download.busy') }}</p>
        <p v-if="downloadError" class="iso-error rep-hint" role="alert">{{ t('reports.download.' + downloadError) }}</p>
      </div>

      <section class="rep-preview" :aria-label="t('reports.preview.title')">
        <p v-if="previewState === 'loading'" class="muted" role="status">{{ t('reports.preview.loading') }}</p>
        <div v-else-if="previewState === 'notFound' || previewState === 'error'" class="alert alert-error" role="alert">
          {{ t('reports.preview.' + previewState) }}
          <button type="button" class="btn-link retry" @click="loadPreview">{{ t('modules.retry') }}</button>
        </div>
        <template v-else-if="previewState === 'ready' && previewUrl">
          <object :key="previewUrl" :data="previewUrl" type="application/pdf" class="rep-pdf" :title="t('reports.preview.label', { name: output.name || report.name })">
            <p class="rep-pdf-fallback">
              {{ t('reports.preview.unsupported') }}
              <a :href="previewUrl" target="_blank" rel="noopener">{{ t('reports.preview.open') }}</a>
            </p>
          </object>
          <p class="rep-hint">
            <a :href="previewUrl" target="_blank" rel="noopener" class="rep-open">{{ t('reports.preview.open') }}</a>
          </p>
        </template>
      </section>
    </template>
  </article>
</template>
