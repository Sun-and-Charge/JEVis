<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import { echartsLocale } from '../../analysis/echarts'
import { formatDateTime } from '../../analysis/format'
import { HttpError } from '../../api/http'
import { loadRowSamples, type Note, type RowSamples } from '../../api/notes'
import { noteChartOption, noteWindow, numericRowSamples } from '../../notes/chart'
import { noteTime, visibleTags, type TagName } from '../../notes/notes'
import { useAuthStore } from '../../stores/auth'

/*
 * Detail panel of a note: full text, tags, user, data row and site, and a small chart of the data row from one day
 * before to one day after the note with the note time marked. Rows the user may not read get no chart.
 */

const props = defineProps<{ note: Note; tagName: TagName }>()
const { t, locale } = useI18n()
const auth = useAuthStore()

type ChartState = 'noAccess' | 'loading' | 'ready' | 'notFound' | 'error'

const result = shallowRef<RowSamples | null>(null)
const state = ref<ChartState>('loading')
let seq = 0

const ts = computed(() => noteTime(props.note))
const readable = computed(() => !!props.note.row?.readable && ts.value != null)

async function load() {
  const current = ++seq
  result.value = null
  if (!readable.value || !props.note.row || ts.value == null) {
    state.value = 'noAccess'
    return
  }
  state.value = 'loading'
  const { from, to } = noteWindow(ts.value)
  try {
    const answer = await loadRowSamples(auth.http, props.note.row.id, from, to)
    if (current !== seq) return
    result.value = answer
    state.value = 'ready'
  } catch (e) {
    if (current !== seq) return
    state.value = e instanceof HttpError && (e.status === 404 || e.status === 403) ? 'notFound' : 'error'
  }
}

onMounted(load)
watch(() => props.note.id, load)

const samples = computed(() => numericRowSamples(result.value?.samples))
const hasText = computed(() => (result.value?.samples ?? []).some((s) => typeof s?.[1] === 'string'))
const rowLabel = computed(() => props.note.row?.name || '')
const option = computed(() =>
  ts.value != null && samples.value.length
    ? noteChartOption(samples.value, ts.value, rowLabel.value, result.value?.unit ?? null, { locale: locale.value, noteLabel: t('notes.note') })
    : null,
)
const initOptions = computed(() => ({ locale: echartsLocale(locale.value) }))

const tags = computed(() => visibleTags(props.note))
const when = computed(() => (ts.value == null ? props.note.timestamp : formatDateTime(ts.value, locale.value)))
const id = (suffix: string) => `note-${props.note.id}-${suffix}`
</script>

<template>
  <div class="iso-sections note-details">
    <section class="iso-section" :aria-labelledby="id('general')">
      <h3 :id="id('general')">{{ t('notes.note') }}</h3>
      <p class="note-full-text">{{ note.text || t('modules.none') }}</p>
      <dl class="mod-dl">
        <dt>{{ t('notes.col.date') }}</dt>
        <dd>{{ when }}</dd>
        <dt>{{ t('notes.col.tags') }}</dt>
        <dd>
          <span v-if="!tags.length">{{ t('modules.none') }}</span>
          <span v-for="tag in tags" :key="tag" class="note-tag">{{ tagName(tag) }}</span>
        </dd>
        <dt>{{ t('notes.col.user') }}</dt>
        <dd>{{ note.user || t('modules.none') }}</dd>
        <dt>{{ t('notes.col.row') }}</dt>
        <dd :class="{ muted: note.row && !note.row.readable }">{{ note.row?.readable ? note.row.name || '#' + note.row.id : t('notes.noAccess') }}</dd>
        <template v-if="note.row?.readable && note.row.path">
          <dt>{{ t('notes.col.path') }}</dt>
          <dd>{{ note.row.path }}</dd>
        </template>
        <dt>{{ t('notes.col.site') }}</dt>
        <dd>{{ note.site?.name || t('modules.none') }}</dd>
      </dl>
    </section>

    <section class="iso-section note-chart-section" :aria-labelledby="id('chart')">
      <h3 :id="id('chart')">{{ t('notes.detail.chart') }}<span v-if="result?.unit" class="muted"> [{{ result.unit }}]</span></h3>
      <p v-if="state === 'noAccess'" class="muted">{{ t('notes.detail.noAccess') }}</p>
      <p v-else-if="state === 'loading'" class="muted" role="status">{{ t('notes.detail.loading') }}</p>
      <p v-else-if="state === 'notFound'" class="muted">{{ t('notes.detail.notFound') }}</p>
      <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
        {{ t('notes.detail.error') }}
        <button type="button" class="btn-link retry" @click="load">{{ t('modules.retry') }}</button>
      </div>
      <template v-else>
        <p v-if="!option" class="muted">{{ hasText ? t('notes.detail.noNumbers') : t('notes.detail.noData') }}</p>
        <div v-else class="note-chart" role="img" :aria-label="t('notes.detail.chartLabel', { name: rowLabel })">
          <VChart :option="option" :init-options="initOptions" :update-options="{ notMerge: true }" autoresize />
        </div>
        <p v-if="result?.truncated" class="muted note-small">{{ t('notes.detail.truncated') }}</p>
      </template>
    </section>
  </div>
</template>

<style scoped>
.retry {
  margin-left: 0.5rem;
}
</style>
