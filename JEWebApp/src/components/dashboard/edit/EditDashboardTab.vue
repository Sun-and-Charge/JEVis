<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DashboardTimeFrameOption } from '../../../api/dashboard'
import { cssToFx, fxAlpha, toHexInput, ZOOM_FIT_HEIGHT, ZOOM_FIT_SCREEN, ZOOM_FIT_WIDTH } from '../../../dashboard/edit/raw'
import { BACKGROUND_MODES, SOURCE_DEFAULTS, timeFrameChoices, ZOOM_MODES, type Problem } from '../../../dashboard/edit/source'
import type { DashboardEditing } from '../../../dashboard/edit/useDashboardEditing'
import DbeColor from './DbeColor.vue'
import DbeNumber from './DbeNumber.vue'

/*
 * Tab "Dashboard" of the dashboard editor (E4): name, size, background colour / mode / image, time frame, zoom,
 * update rate and grid (as the Control Center's dashboard settings).
 */

const props = defineProps<{ ed: DashboardEditing; uid: string; timeFrames: readonly DashboardTimeFrameOption[] }>()
const { t, te } = useI18n()

const s = computed(() => props.ed.source.value!)
const problemOf = (path: string) => {
  const p = props.ed.problems.value.find((x: Problem) => x.path === path)
  return p ? t(p.key, p.params ?? {}) : null
}
const id = (k: string) => `${props.uid}-db-${k}`

const num = (v: unknown, fallback: number) => (typeof v === 'number' && Number.isFinite(v) ? v : fallback)
function set(key: string, value: unknown) {
  ;(s.value as Record<string, unknown>)[key] = value
}

/* background */
function setColor(hex: string) {
  const fx = cssToFx(hex, fxAlpha(s.value['Dash Board Color']))
  if (fx) set('Dash Board Color', fx)
}
function setAlpha(percent: number) {
  const fx = cssToFx(toHexInput(s.value['Dash Board Color'], '#f4f4f4'), percent / 100)
  if (fx) set('Dash Board Color', fx)
}
const mode = computed(() => (typeof s.value.backgroundMode === 'string' ? s.value.backgroundMode.toLowerCase() : 'default'))

const file = ref<HTMLInputElement | null>(null)
function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  if (f) void props.ed.uploadBackgroundImage(f)
  input.value = ''
}

/* time frame ("Data Period") */
const choices = computed(() => timeFrameChoices(props.timeFrames))
const period = computed(() => String(s.value['Data Period'] ?? SOURCE_DEFAULTS.dataPeriod))
const periodLabel = (c: { id: string; name: string | null }) =>
  c.name ?? (te(`dashboard.timeFrames.${c.id}`) ? t(`dashboard.timeFrames.${c.id}`) : te(`dashboardEdit.timeFrames.${c.id}`) ? t(`dashboardEdit.timeFrames.${c.id}`) : c.id)

/* zoom: a fit mode or a factor */
const zoom = computed(() => num(s.value['Zoom Factor'], SOURCE_DEFAULTS.zoom))
const isFit = computed(() => [ZOOM_FIT_WIDTH, ZOOM_FIT_HEIGHT, ZOOM_FIT_SCREEN].includes(zoom.value))
const zoomMode = computed(() => (isFit.value ? String(zoom.value) : 'factor'))
function setZoomMode(value: string) {
  set('Zoom Factor', value === 'factor' ? 1 : Number(value))
}
</script>

<template>
  <div>
    <div v-if="ed.canWrite.value" class="edit-field">
      <label :for="id('name')">{{ t('dashboardEdit.dashboard.name') }}</label>
      <input
        :id="id('name')"
        v-model="ed.name.value"
        type="text"
        maxlength="100"
        autocomplete="off"
        :aria-invalid="problemOf('name') ? 'true' : undefined"
        :aria-describedby="problemOf('name') ? id('name') + '-err' : undefined"
      />
      <p v-if="problemOf('name')" :id="id('name') + '-err'" class="ae-error">{{ problemOf('name') }}</p>
    </div>

    <section class="ae-section">
      <h3>{{ t('dashboardEdit.dashboard.size') }}</h3>
      <div class="ae-grid">
        <DbeNumber :id="id('width')" :label="t('dashboardEdit.dashboard.width')" :value="num(s.width, SOURCE_DEFAULTS.width)" :min="100" :max="20000" :problem="problemOf('dashboard.width')" @set="set('width', $event)" />
        <DbeNumber :id="id('height')" :label="t('dashboardEdit.dashboard.height')" :value="num(s.height, SOURCE_DEFAULTS.height)" :min="100" :max="20000" :problem="problemOf('dashboard.height')" @set="set('height', $event)" />
        <div class="edit-field">
          <label :for="id('zoom')">{{ t('dashboardEdit.dashboard.zoom') }}</label>
          <select :id="id('zoom')" :value="zoomMode" @change="setZoomMode(($event.target as HTMLSelectElement).value)">
            <option v-for="z in ZOOM_MODES" :key="z.value" :value="String(z.value)">{{ t('dashboardEdit.zoom.' + z.key) }}</option>
            <option value="factor">{{ t('dashboardEdit.zoom.factor') }}</option>
          </select>
        </div>
        <DbeNumber
          v-if="!isFit"
          :id="id('zoomFactor')"
          :label="t('dashboardEdit.dashboard.zoomFactor')"
          :value="zoom"
          :min="0.05"
          :max="20"
          step="any"
          :problem="problemOf('dashboard.zoom')"
          @set="set('Zoom Factor', $event)"
        />
      </div>
    </section>

    <section class="ae-section">
      <h3>{{ t('dashboardEdit.dashboard.background') }}</h3>
      <DbeColor :id="id('color')" :label="t('dashboardEdit.dashboard.color')" :value="s['Dash Board Color'] ?? SOURCE_DEFAULTS.color" opacity :problem="problemOf('dashboard.color')" @color="setColor" @alpha="setAlpha" />
      <div class="edit-field">
        <label :for="id('mode')">{{ t('dashboardEdit.dashboard.mode') }}</label>
        <select :id="id('mode')" :value="mode" @change="set('backgroundMode', ($event.target as HTMLSelectElement).value)">
          <option v-for="m in BACKGROUND_MODES" :key="m" :value="m">{{ t('dashboardEdit.backgroundModes.' + m) }}</option>
        </select>
      </div>
      <div v-if="ed.canWrite.value" class="edit-field">
        <span class="dbe-label" :id="id('image') + '-label'">{{ t('dashboardEdit.dashboard.image') }}</span>
        <input ref="file" :id="id('image')" type="file" accept="image/png,image/jpeg,image/gif,image/bmp,image/webp,image/svg+xml" class="visually-hidden dbe-file" @change="onFile" />
        <button type="button" class="edit-btn is-small dbe-upload" :disabled="ed.uploading.value" :aria-describedby="id('image') + '-hint'" @click="file?.click()">
          {{ ed.uploading.value ? t('dashboardEdit.background.uploading') : t('dashboardEdit.background.choose') }}
        </button>
        <p :id="id('image') + '-hint'" class="ae-hint">{{ t('dashboardEdit.background.hint') }}</p>
        <p v-if="ed.uploadMessage.value" class="ae-hint dbe-upload-result" :class="{ 'ae-error': !ed.uploadMessage.value.ok }" role="status">{{ ed.uploadMessage.value.text }}</p>
      </div>
    </section>

    <section class="ae-section">
      <h3>{{ t('dashboardEdit.dashboard.data') }}</h3>
      <div class="ae-grid">
        <div class="edit-field">
          <label :for="id('period')">{{ t('dashboardEdit.dashboard.timeFrame') }}</label>
          <select :id="id('period')" :value="period" @change="set('Data Period', ($event.target as HTMLSelectElement).value)">
            <option v-if="!choices.some((c) => c.id === period)" :value="period">{{ t('dashboardEdit.stored', { value: period }) }}</option>
            <option v-for="c in choices" :key="c.id" :value="c.id">{{ periodLabel(c) }}</option>
          </select>
        </div>
        <DbeNumber
          :id="id('updateRate')"
          :label="t('dashboardEdit.dashboard.updateRate')"
          :value="num(s['Update Rate'], SOURCE_DEFAULTS.updateRate)"
          :min="1"
          :max="86400"
          :problem="problemOf('dashboard.updateRate')"
          @set="set('Update Rate', Math.round($event))"
        />
      </div>
    </section>

    <section class="ae-section">
      <h3>{{ t('dashboardEdit.dashboard.grid') }}</h3>
      <div class="ae-grid">
        <DbeNumber :id="id('gridX')" :label="t('dashboardEdit.dashboard.gridX')" :value="num(s['X Axis Grid Interval'], SOURCE_DEFAULTS.grid)" :min="1" :max="1000" step="any" :problem="problemOf('dashboard.gridX')" @set="set('X Axis Grid Interval', $event)" />
        <DbeNumber :id="id('gridY')" :label="t('dashboardEdit.dashboard.gridY')" :value="num(s['Y Axis Grid Interval'], SOURCE_DEFAULTS.grid)" :min="1" :max="1000" step="any" :problem="problemOf('dashboard.gridY')" @set="set('Y Axis Grid Interval', $event)" />
      </div>
      <label class="ae-check">
        <input type="checkbox" :checked="s['Show Grid'] === true" @change="set('Show Grid', ($event.target as HTMLInputElement).checked)" />
        {{ t('dashboardEdit.dashboard.showGrid') }}
      </label>
      <p class="ae-hint">{{ t('dashboardEdit.dashboard.gridHint') }}</p>
    </section>
  </div>
</template>
