<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DashboardTimeFrameOption } from '../../../api/dashboard'
import type { RawWidget } from '../../../dashboard/edit/raw'
import {
  commonValue,
  FONT_POSTURES,
  FONT_WEIGHTS,
  ownTimeFrame,
  setAll,
  setAlphaAll,
  setColorAll,
  setOwnTimeFrame,
  timeFrameChoices,
  titlePosition,
  TITLE_POSITIONS,
} from '../../../dashboard/edit/source'
import type { DashboardEditing } from '../../../dashboard/edit/useDashboardEditing'
import DbeColor from './DbeColor.vue'
import DbeNumber from './DbeNumber.vue'
import { typeHasRows, useTypeLabel } from './typeLabel'

/*
 * Tab "Widget" of the dashboard editor (E4): the common settings of one or several selected widgets (as the Control
 * Center's GenericConfigNode). With several widgets a field shows "verschieden" when they differ; a change applies
 * to all of them.
 */

const props = defineProps<{ ed: DashboardEditing; uid: string; timeFrames: readonly DashboardTimeFrameOption[] }>()
const { t, te } = useI18n()
const typeLabel = useTypeLabel()

const widgets = computed(() => props.ed.selectedWidgets.value)
const id = (k: string) => `${props.uid}-w-${k}`
const c = <T,>(get: (w: RawWidget) => T) => commonValue(widgets.value, get)
const set = (key: string, value: unknown) => setAll(widgets.value, key, value)
const numOr = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : undefined)

/** problem of a field of any selected widget */
function problemOf(key: string): string | null {
  for (const w of widgets.value) {
    const p = props.ed.problems.value.find((x) => x.path === `widget.${w.uuid}.${key}`)
    if (p) return t(p.key, p.params ?? {})
  }
  return null
}

const title = computed(() => c((w) => (typeof w.title === 'string' ? w.title : '')))
const tooltip = computed(() => c((w) => (typeof w.tooltip === 'string' ? w.tooltip : '')))
const types = computed(() => [...new Set(widgets.value.map((w) => typeLabel(w.WidgetType)))].join(', '))

function selectValue(common: { value: unknown; mixed: boolean }): string {
  return common.mixed ? '__mixed' : String(common.value ?? '')
}
function onSelect(key: string, value: string) {
  if (value !== '__mixed') set(key, value)
}
function onCheck(key: string, e: Event) {
  set(key, (e.target as HTMLInputElement).checked)
}

const position = computed(() => c((w) => titlePosition(w['Title Position'])))
const posLabel = (p: string) => (te('dashboardEdit.positions.' + p) ? t('dashboardEdit.positions.' + p) : p)

/* own time frame: only for widgets with data rows (the CC's DataModelWidget) */
const withData = computed(() => widgets.value.length > 0 && widgets.value.every((w) => typeHasRows(w.WidgetType, !!w.dataHandler)))
const ownFrame = computed(() => c(ownTimeFrame))
const choices = computed(() => timeFrameChoices(props.timeFrames))
const frameLabel = (x: { id: string; name: string | null }) =>
  x.name ?? (te(`dashboard.timeFrames.${x.id}`) ? t(`dashboard.timeFrames.${x.id}`) : te(`dashboardEdit.timeFrames.${x.id}`) ? t(`dashboardEdit.timeFrames.${x.id}`) : x.id)
function setFrame(value: string) {
  if (value === '__mixed') return
  for (const w of widgets.value) setOwnTimeFrame(w, value)
}

const checkState = (common: { value: unknown; mixed: boolean }) => ({ checked: common.value === true, indeterminate: common.mixed })
</script>

<template>
  <div>
    <p v-if="!widgets.length" class="ae-hint dbe-none">{{ t('dashboardEdit.widget.none') }}</p>
    <template v-else>
      <p class="ae-hint dbe-selected" role="status">
        {{ widgets.length === 1 ? t('dashboardEdit.widget.one', { type: types }) : t('dashboardEdit.widget.several', { n: widgets.length, types }) }}
      </p>

      <div class="edit-field">
        <label :for="id('title')">{{ t('dashboardEdit.widget.title') }}</label>
        <input :id="id('title')" :value="title.value ?? ''" type="text" maxlength="10000" :placeholder="title.mixed ? t('dashboardEdit.mixed') : undefined" @input="set('title', ($event.target as HTMLInputElement).value)" />
      </div>
      <div class="edit-field">
        <label :for="id('tooltip')">{{ t('dashboardEdit.widget.tooltip') }}</label>
        <input :id="id('tooltip')" :value="tooltip.value ?? ''" type="text" maxlength="10000" :placeholder="tooltip.mixed ? t('dashboardEdit.mixed') : undefined" @input="set('tooltip', ($event.target as HTMLInputElement).value)" />
      </div>

      <section class="ae-section">
        <h3>{{ t('dashboardEdit.widget.layout') }}</h3>
        <div class="ae-grid">
          <DbeNumber :id="id('x')" :label="t('dashboardEdit.widget.x')" v-bind="c((w) => numOr(w.xPos))" step="any" :problem="problemOf('xPos')" @set="set('xPos', $event)" />
          <DbeNumber :id="id('y')" :label="t('dashboardEdit.widget.y')" v-bind="c((w) => numOr(w.yPos))" step="any" :problem="problemOf('yPos')" @set="set('yPos', $event)" />
          <DbeNumber :id="id('width')" :label="t('dashboardEdit.widget.width')" v-bind="c((w) => numOr(w.width))" :min="10" :max="20000" step="any" :problem="problemOf('width')" @set="set('width', $event)" />
          <DbeNumber :id="id('height')" :label="t('dashboardEdit.widget.height')" v-bind="c((w) => numOr(w.height))" :min="10" :max="20000" step="any" :problem="problemOf('height')" @set="set('height', $event)" />
          <DbeNumber :id="id('layer')" :label="t('dashboardEdit.widget.layer')" v-bind="c((w) => numOr(w.layer) ?? 0)" :problem="problemOf('layer')" @set="set('layer', Math.round($event))" />
        </div>
      </section>

      <section class="ae-section">
        <h3>{{ t('dashboardEdit.widget.colors') }}</h3>
        <DbeColor
          :id="id('bg')"
          :label="t('dashboardEdit.widget.background')"
          :value="widgets[0]['Background Color']"
          :mixed="c((w) => w['Background Color']).mixed"
          opacity
          :problem="problemOf('Background Color')"
          @color="setColorAll(widgets, 'Background Color', $event)"
          @alpha="setAlphaAll(widgets, 'Background Color', $event)"
        />
        <DbeColor
          :id="id('font')"
          :label="t('dashboardEdit.widget.fontColor')"
          :value="widgets[0]['Font Color']"
          :mixed="c((w) => w['Font Color']).mixed"
          :problem="problemOf('Font Color')"
          @color="setColorAll(widgets, 'Font Color', $event)"
        />
      </section>

      <section class="ae-section">
        <h3>{{ t('dashboardEdit.widget.font') }}</h3>
        <div class="ae-grid">
          <DbeNumber :id="id('fontSize')" :label="t('dashboardEdit.widget.fontSize')" v-bind="c((w) => numOr(w.fontSize))" :min="1" :max="400" step="any" :problem="problemOf('fontSize')" @set="set('fontSize', $event)" />
          <div class="edit-field">
            <label :for="id('fontWeight')">{{ t('dashboardEdit.widget.fontWeight') }}</label>
            <select :id="id('fontWeight')" :value="selectValue(c((w) => w.fontWeight ?? 'NORMAL'))" @change="onSelect('fontWeight', ($event.target as HTMLSelectElement).value)">
              <option v-if="c((w) => w.fontWeight).mixed" value="__mixed">{{ t('dashboardEdit.mixed') }}</option>
              <option v-for="fw in FONT_WEIGHTS" :key="fw" :value="fw">{{ t('dashboardEdit.fontWeights.' + fw) }}</option>
            </select>
          </div>
          <div class="edit-field">
            <label :for="id('fontPosture')">{{ t('dashboardEdit.widget.fontPosture') }}</label>
            <select :id="id('fontPosture')" :value="selectValue(c((w) => w.fontPosture ?? 'REGULAR'))" @change="onSelect('fontPosture', ($event.target as HTMLSelectElement).value)">
              <option v-if="c((w) => w.fontPosture ?? 'REGULAR').mixed" value="__mixed">{{ t('dashboardEdit.mixed') }}</option>
              <option v-for="fp in FONT_POSTURES" :key="fp" :value="fp">{{ t('dashboardEdit.fontPostures.' + fp) }}</option>
            </select>
          </div>
          <div class="edit-field">
            <label :for="id('position')">{{ t('dashboardEdit.widget.titlePosition') }}</label>
            <select :id="id('position')" :value="selectValue(position)" @change="onSelect('Title Position', ($event.target as HTMLSelectElement).value)">
              <option v-if="position.mixed" value="__mixed">{{ t('dashboardEdit.mixed') }}</option>
              <option v-if="!position.mixed && !(TITLE_POSITIONS as readonly string[]).includes(String(position.value))" :value="position.value">{{ position.value }}</option>
              <option v-for="p in TITLE_POSITIONS" :key="p" :value="p">{{ posLabel(p) }}</option>
            </select>
          </div>
        </div>
        <label class="ae-check">
          <input type="checkbox" v-bind="checkState(c((w) => w.fontUnderlined === true))" @change="onCheck('fontUnderlined', $event)" />
          {{ t('dashboardEdit.widget.underline') }}
        </label>
      </section>

      <section class="ae-section">
        <h3>{{ t('dashboardEdit.widget.border') }}</h3>
        <div class="ae-grid">
          <DbeNumber :id="id('borderSize')" :label="t('dashboardEdit.widget.borderSize')" v-bind="c((w) => numOr(w.borderSize) ?? 0)" :min="0" :max="100" step="any" :problem="problemOf('borderSize')" @set="set('borderSize', $event)" />
          <DbeNumber :id="id('cornerRadius')" :label="t('dashboardEdit.widget.cornerRadius')" v-bind="c((w) => numOr(w.cornerRadius) ?? 0)" :min="0" :max="10000" step="any" :problem="problemOf('cornerRadius')" @set="set('cornerRadius', $event)" />
        </div>
        <DbeColor
          :id="id('borderColor')"
          :label="t('dashboardEdit.widget.borderColor')"
          :value="widgets[0].borderColor"
          :mixed="c((w) => w.borderColor).mixed"
          :problem="problemOf('borderColor')"
          @color="setColorAll(widgets, 'borderColor', $event)"
        />
        <label class="ae-check">
          <input type="checkbox" v-bind="checkState(c((w) => w.shadow === true))" @change="onCheck('shadow', $event)" />
          {{ t('dashboardEdit.widget.shadow') }}
        </label>
      </section>

      <section class="ae-section">
        <h3>{{ t('dashboardEdit.widget.values') }}</h3>
        <div class="ae-grid">
          <DbeNumber :id="id('decimals')" :label="t('dashboardEdit.widget.decimals')" v-bind="c((w) => numOr(w.decimals))" :min="0" :max="20" :problem="problemOf('decimals')" @set="set('decimals', Math.round($event))" />
        </div>
        <label class="ae-check">
          <input type="checkbox" class="dbe-decimals-auto" v-bind="checkState(c((w) => w.decimalsAuto === true))" @change="onCheck('decimalsAuto', $event)" />
          {{ t('dashboardEdit.widget.decimalsAuto') }}
        </label>
        <label class="ae-check">
          <input type="checkbox" v-bind="checkState(c((w) => w.showValue !== false))" @change="onCheck('showValue', $event)" />
          {{ t('dashboardEdit.widget.showValue') }}
        </label>
      </section>

      <section v-if="withData" class="ae-section">
        <h3>{{ t('dashboardEdit.widget.timeFrameTitle') }}</h3>
        <div class="edit-field">
          <label :for="id('timeFrame')">{{ t('dashboardEdit.widget.timeFrame') }}</label>
          <select :id="id('timeFrame')" class="dbe-own-frame" :value="ownFrame.mixed ? '__mixed' : ownFrame.value ?? ''" @change="setFrame(($event.target as HTMLSelectElement).value)">
            <option v-if="ownFrame.mixed" value="__mixed">{{ t('dashboardEdit.mixed') }}</option>
            <option value="">{{ t('dashboardEdit.widget.timeFrameNone') }}</option>
            <option v-if="!ownFrame.mixed && ownFrame.value && !choices.some((x) => x.id === ownFrame.value)" :value="ownFrame.value">
              {{ t('dashboardEdit.stored', { value: ownFrame.value }) }}
            </option>
            <option v-for="x in choices" :key="x.id" :value="x.id">{{ frameLabel(x) }}</option>
          </select>
        </div>
        <label class="ae-check">
          <input type="checkbox" v-bind="checkState(c((w) => w.fixedTimeframe === true))" @change="onCheck('fixedTimeframe', $event)" />
          {{ t('dashboardEdit.widget.fixedTimeFrame') }}
        </label>
        <p class="ae-hint">{{ t('dashboardEdit.widget.fixedTimeFrameHint') }}</p>
      </section>
    </template>
  </div>
</template>
