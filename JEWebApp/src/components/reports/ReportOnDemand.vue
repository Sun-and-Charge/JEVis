<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTime } from '../../analysis/format'
import { HttpError } from '../../api/http'
import { loadOnDemand, loadOnDemandFile, requestOnDemand, type OnDemandRequest, type OnDemandState, type ReportFormat } from '../../api/reports'
import { saveBlob } from '../../iso/download'
import { canAddRequest, formats, hasOpen, onDemandFileName, periodLabel } from '../../reports/onDemand'
import { useAuthStore } from '../../stores/auth'

/*
 * R04 report on demand: the user picks a period of the report's schedule, JEReport renders the report within a
 * minute (OnDemandRunner polls every 30 s) and the files can be downloaded here; optionally they are mailed to the
 * user. The list is reloaded every 10 s while a request is open.
 */

const props = defineProps<{ reportId: number; reportName: string }>()
const { t, locale } = useI18n()
const auth = useAuthStore()

const POLL_MS = 10000

const state = shallowRef<OnDemandState | null>(null)
const loadState = ref<'loading' | 'ready' | 'error'>('loading')
const selected = ref<string | null>(null)
const mail = ref(false)
const sending = ref(false)
const sendError = ref<string | null>(null)
const sent = ref(false)
let seq = 0
let timer: ReturnType<typeof setTimeout> | null = null

function stopPolling() {
  if (timer) clearTimeout(timer)
  timer = null
}

function schedulePoll() {
  stopPolling()
  if (state.value && hasOpen(state.value.requests)) timer = setTimeout(() => load(true), POLL_MS)
}

async function load(quiet = false) {
  const current = ++seq
  if (!quiet) loadState.value = 'loading'
  try {
    const answer = await loadOnDemand(auth.http, props.reportId)
    if (current !== seq) return
    state.value = answer
    if (!selected.value || !answer.periods.some((p) => p.start === selected.value)) {
      /* the last complete period, else the running one */
      selected.value = (answer.periods.find((p) => !p.current) ?? answer.periods[0])?.start ?? null
    }
    loadState.value = 'ready'
  } catch {
    if (current === seq && !quiet) loadState.value = 'error'
  }
  if (current === seq) schedulePoll()
}

watch(
  () => props.reportId,
  () => {
    state.value = null
    selected.value = null
    mail.value = false
    sendError.value = null
    sent.value = false
    load()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  seq++
  stopPolling()
})

const label = (start: string) => (state.value ? periodLabel(start, state.value.schedule, state.value.timeZone, locale.value, (k, v) => t(k, v ?? {})) : start)
const time = (iso: string | null | undefined) => {
  const ms = iso ? Date.parse(iso) : NaN
  return Number.isNaN(ms) ? '' : formatDateTime(ms, locale.value)
}
const requests = computed(() => state.value?.requests ?? [])
const mayAdd = computed(() => canAddRequest(requests.value))

async function send() {
  if (!state.value || !selected.value || sending.value) return
  sending.value = true
  sendError.value = null
  sent.value = false
  try {
    await requestOnDemand(auth.http, props.reportId, { start: selected.value, mail: mail.value })
    sent.value = true
    await load(true)
  } catch (e) {
    const status = e instanceof HttpError ? e.status : 0
    const body = e instanceof HttpError ? e.body : ''
    sendError.value =
      status === 429 ? 'tooMany' : status === 403 ? 'rights' : status === 400 && /e-mail/i.test(body) ? 'noMail' : status === 400 ? 'invalid' : 'error'
  } finally {
    sending.value = false
  }
}

const busy = ref<string | null>(null)
const downloadError = ref<string | null>(null)

async function download(r: OnDemandRequest, format: ReportFormat) {
  busy.value = r.id + format
  downloadError.value = null
  try {
    const blob = await loadOnDemandFile(auth.http, props.reportId, r.id, format)
    saveBlob(blob, onDemandFileName(props.reportName, label(r.start), format))
  } catch (e) {
    downloadError.value = e instanceof HttpError && e.status === 404 ? 'notFound' : 'error'
  } finally {
    busy.value = null
  }
}

const domId = (suffix: string) => `report-${props.reportId}-od-${suffix}`
</script>

<template>
  <section class="rep-od" :aria-labelledby="domId('title')">
    <h3 :id="domId('title')" class="rep-od-title">{{ t('reports.onDemand.title') }}</h3>

    <p v-if="loadState === 'loading'" class="muted" role="status">{{ t('reports.onDemand.loading') }}</p>
    <div v-else-if="loadState === 'error'" class="alert alert-error" role="alert">
      {{ t('reports.onDemand.error') }}
      <button type="button" class="btn-link retry" @click="load()">{{ t('modules.retry') }}</button>
    </div>

    <template v-else-if="state">
      <p v-if="!state.canRequest" class="muted rep-hint">{{ t('reports.onDemand.not.' + (state.reason ?? 'rights')) }}</p>
      <form v-else class="rep-od-form" @submit.prevent="send">
        <div class="control">
          <label :for="domId('period')">{{ t('reports.onDemand.period') }}</label>
          <select :id="domId('period')" v-model="selected" class="rep-od-period">
            <option v-for="p in state.periods" :key="p.start" :value="p.start">
              {{ label(p.start) }}{{ p.current ? ' ' + t('reports.onDemand.running') : '' }}
            </option>
          </select>
        </div>
        <label class="iso-edit-check">
          <input v-model="mail" type="checkbox" class="rep-od-mail" />
          <span>{{ t('reports.onDemand.mail') }}</span>
        </label>
        <div class="rep-actions">
          <button type="submit" class="btn-primary rep-od-send" :disabled="sending || !selected || !mayAdd">
            {{ sending ? t('reports.onDemand.sending') : t('reports.onDemand.send') }}
          </button>
        </div>
        <p v-if="!mayAdd" class="muted rep-hint">{{ t('reports.onDemand.tooMany') }}</p>
        <p v-if="sent && !sendError" class="muted rep-hint" role="status">{{ t('reports.onDemand.sent') }}</p>
        <p v-if="sendError" class="iso-error rep-hint" role="alert">{{ t('reports.onDemand.sendError.' + sendError) }}</p>
      </form>

      <p v-if="!requests.length" class="muted rep-hint">{{ t('reports.onDemand.none') }}</p>
      <ul v-else class="rep-od-list" :aria-label="t('reports.onDemand.listLabel')">
        <li v-for="r in requests" :key="r.id" class="rep-od-item" :data-status="r.status">
          <div class="rep-od-head">
            <strong>{{ label(r.start) }}</strong>
            <span class="mod-badge rep-od-status" :class="'is-' + r.status.toLowerCase()">{{ t('reports.onDemand.status.' + r.status) }}</span>
          </div>
          <p class="muted rep-od-meta">
            {{ t('reports.onDemand.requested', { time: time(r.requestedAt), name: r.mine ? t('reports.onDemand.me') : r.requestedByName || '–' }) }}
            <template v-if="r.mail"> · {{ t('reports.onDemand.mailed') }}</template>
          </p>
          <p v-if="r.status === 'FAILED' && r.message" class="iso-error rep-od-message">{{ r.message }}</p>
          <div v-if="formats(r).length" class="rep-actions">
            <button
              v-for="f in formats(r)"
              :key="f"
              type="button"
              class="notes-btn rep-download"
              :data-format="f"
              :disabled="busy != null"
              @click="download(r, f)"
            >
              {{ t('reports.download.' + f) }}
            </button>
          </div>
        </li>
      </ul>
      <p v-if="downloadError" class="iso-error rep-hint" role="alert">{{ t('reports.download.' + downloadError) }}</p>
    </template>
  </section>
</template>
