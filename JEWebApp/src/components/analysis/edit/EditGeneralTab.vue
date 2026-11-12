<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { FORCED_INTERVALS, LIMIT_FILLS, formatDates, formatWeekdays, newLimitLine, parseDates, parseWeekdays, type Draft, type Problem } from '../../../analysis/editor'
import { fieldHelpers } from '../../../analysis/editFields'

/* Tab "Allgemein" of the analysis editor (E3): name, layout, forced interval, limit lines, day filter. */

const props = defineProps<{ draft: Draft; problems: readonly Problem[]; uid: string; canRename: boolean }>()
const { t, locale } = useI18n()
const f = fieldHelpers(props.uid, () => props.problems)
const msg = (path: string) => {
  const p = f.problem(path)
  return p ? t(p.key, p.params ?? {}) : ''
}

const intervals = computed(() => {
  const list: string[] = [...FORCED_INTERVALS]
  if (!list.includes(props.draft.forcedInterval)) list.push(props.draft.forcedInterval)
  return list
})
const intervalLabel = (v: string) =>
  !v ? t('analysisEdit.intervals.none') : (FORCED_INTERVALS as readonly string[]).includes(v) ? t('analysisEdit.intervals.' + v) : t('analysisEdit.general.otherInterval', { value: v })

/* ISO weekdays 1 (Monday) .. 7, short names of the locale */
const weekdays = computed(() => {
  const format = new Intl.DateTimeFormat(locale.value, { weekday: 'short' })
  return [1, 2, 3, 4, 5, 6, 7].map((day) => ({ day, label: format.format(new Date(2024, 0, day)) }))
})
const excluded = computed(() => parseWeekdays(props.draft.dayFilter.weekdays))
function toggleWeekday(day: number) {
  const days = excluded.value.includes(day) ? excluded.value.filter((d) => d !== day) : [...excluded.value, day]
  props.draft.dayFilter.weekdays = formatWeekdays(days)
}

const dates = computed(() => parseDates(props.draft.dayFilter.dates))
const newDate = ref('')
function addDate() {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(newDate.value)) return
  props.draft.dayFilter.dates = formatDates([...dates.value, newDate.value])
  newDate.value = ''
}
function removeDate(date: string) {
  props.draft.dayFilter.dates = formatDates(dates.value.filter((d) => d !== date))
}
const dateText = (d: string) => new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium' }).format(new Date(d + 'T00:00:00'))

function addLimit() {
  props.draft.limitLines.push(newLimitLine())
}
function removeLimit(index: number) {
  props.draft.limitLines.splice(index, 1)
}
</script>

<template>
  <div>
    <div class="ae-grid">
      <div v-if="canRename" class="edit-field ae-wide">
        <label :for="f.fid('name')">{{ t('analysisEdit.general.name') }}</label>
        <input
          :id="f.fid('name')"
          v-model="draft.name"
          type="text"
          maxlength="100"
          autocomplete="off"
          :aria-invalid="f.problem('name') ? 'true' : undefined"
          :aria-describedby="f.describedBy('name')"
        />
        <p v-if="f.problem('name')" :id="f.errId('name')" class="ae-error">{{ msg('name') }}</p>
      </div>
      <div v-for="key in ['chartsPerScreen', 'horizontalPies', 'horizontalTables'] as const" :key="key" class="edit-field">
        <label :for="f.fid(key)">{{ t('analysisEdit.general.' + key) }}</label>
        <input
          :id="f.fid(key)"
          v-model.number="draft[key]"
          type="number"
          min="1"
          max="20"
          step="1"
          inputmode="numeric"
          :aria-invalid="f.problem(key) ? 'true' : undefined"
          :aria-describedby="f.describedBy(key)"
        />
        <p v-if="f.problem(key)" :id="f.errId(key)" class="ae-error">{{ msg(key) }}</p>
      </div>
      <div class="edit-field">
        <label :for="f.fid('forcedInterval')">{{ t('analysisEdit.general.forcedInterval') }}</label>
        <select :id="f.fid('forcedInterval')" v-model="draft.forcedInterval">
          <option v-for="v in intervals" :key="v" :value="v">{{ intervalLabel(v) }}</option>
        </select>
      </div>
      <label class="ae-check ae-wide">
        <input v-model="draft.autoSize" type="checkbox" />
        {{ t('analysisEdit.general.autoSize') }}
      </label>
    </div>

    <section class="ae-section" :aria-labelledby="uid + '-limits'">
      <h3 :id="uid + '-limits'">{{ t('analysisEdit.limits.title') }}</h3>
      <p class="ae-hint">{{ t('analysisEdit.limits.hint') }}</p>
      <p v-if="!draft.limitLines.length" class="ae-hint">{{ t('analysisEdit.limits.none') }}</p>
      <ul class="ae-cards">
        <li v-for="(line, i) in draft.limitLines" :key="line.uid" class="ae-card">
          <div class="ae-card-head">
            <span class="ae-swatch" :style="{ background: line.color }" aria-hidden="true" />
            <span class="ae-card-title ae-grow">{{ line.label || t('analysisEdit.limits.line', { n: i + 1 }) }}</span>
            <button type="button" class="ae-icon-btn is-danger" :aria-label="t('analysisEdit.limits.removeLabel', { n: i + 1 })" :title="t('analysisEdit.limits.remove')" @click="removeLimit(i)">✕</button>
          </div>
          <div class="ae-card-body ae-grid">
            <div class="edit-field">
              <label :for="f.fid(`limitLines.${i}.value`)">{{ t('analysisEdit.limits.value') }}</label>
              <input
                :id="f.fid(`limitLines.${i}.value`)"
                v-model.number="line.value"
                type="number"
                step="any"
                inputmode="decimal"
                :aria-invalid="f.problem(`limitLines.${i}.value`) ? 'true' : undefined"
                :aria-describedby="f.describedBy(`limitLines.${i}.value`)"
              />
              <p v-if="f.problem(`limitLines.${i}.value`)" :id="f.errId(`limitLines.${i}.value`)" class="ae-error">{{ msg(`limitLines.${i}.value`) }}</p>
            </div>
            <div class="edit-field">
              <label :for="f.fid(`limitLines.${i}.label`)">{{ t('analysisEdit.limits.label') }}</label>
              <input :id="f.fid(`limitLines.${i}.label`)" v-model="line.label" type="text" maxlength="200" />
            </div>
            <div class="edit-field">
              <label :for="f.fid(`limitLines.${i}.color`)">{{ t('analysisEdit.limits.color') }}</label>
              <div class="ae-color">
                <input :value="line.color.slice(0, 7)" type="color" :aria-label="t('analysisEdit.limits.color')" tabindex="-1" @input="line.color = ($event.target as HTMLInputElement).value" />
                <input
                  :id="f.fid(`limitLines.${i}.color`)"
                  v-model.trim="line.color"
                  type="text"
                  maxlength="9"
                  spellcheck="false"
                  :aria-invalid="f.problem(`limitLines.${i}.color`) ? 'true' : undefined"
                  :aria-describedby="f.describedBy(`limitLines.${i}.color`)"
                />
              </div>
              <p v-if="f.problem(`limitLines.${i}.color`)" :id="f.errId(`limitLines.${i}.color`)" class="ae-error">{{ msg(`limitLines.${i}.color`) }}</p>
            </div>
            <div class="edit-field">
              <label :for="f.fid(`limitLines.${i}.axis`)">{{ t('analysisEdit.limits.axis') }}</label>
              <select :id="f.fid(`limitLines.${i}.axis`)" v-model.number="line.axis">
                <option :value="0">{{ t('analysisEdit.axes.0') }}</option>
                <option :value="1">{{ t('analysisEdit.axes.1') }}</option>
              </select>
            </div>
            <div class="edit-field ae-wide">
              <label :for="f.fid(`limitLines.${i}.fill`)">{{ t('analysisEdit.limits.fill') }}</label>
              <select :id="f.fid(`limitLines.${i}.fill`)" v-model="line.fill">
                <option v-for="fill in LIMIT_FILLS" :key="fill" :value="fill">{{ t('analysisEdit.fills.' + fill) }}</option>
                <option v-if="!(LIMIT_FILLS as readonly string[]).includes(line.fill)" :value="line.fill">{{ line.fill }}</option>
              </select>
            </div>
          </div>
        </li>
      </ul>
      <button type="button" class="edit-btn ae-add ae-add-limit" @click="addLimit">+ {{ t('analysisEdit.limits.add') }}</button>
    </section>

    <section class="ae-section" :aria-labelledby="uid + '-dayfilter'">
      <h3 :id="uid + '-dayfilter'">{{ t('analysisEdit.dayFilter.title') }}</h3>
      <p class="ae-hint">{{ t('analysisEdit.dayFilter.explanation') }}</p>
      <fieldset class="ae-fieldset">
        <legend class="ae-legend">{{ t('analysisEdit.dayFilter.weekdays') }}</legend>
        <div class="ae-weekdays">
          <button
            v-for="w in weekdays"
            :key="w.day"
            type="button"
            class="edit-chip ae-weekday"
            :aria-pressed="excluded.includes(w.day)"
            @click="toggleWeekday(w.day)"
          >
            {{ w.label }}
          </button>
        </div>
      </fieldset>
      <label class="ae-check">
        <input v-model="draft.dayFilter.holidays" type="checkbox" />
        {{ t('analysisEdit.dayFilter.holidays') }}
      </label>
      <div class="edit-field">
        <label :for="f.fid('dayFilter.dates')">{{ t('analysisEdit.dayFilter.dates') }}</label>
        <p v-if="!dates.length" class="ae-hint">{{ t('analysisEdit.dayFilter.noDates') }}</p>
        <ul v-else class="ae-dates">
          <li v-for="d in dates" :key="d">
            {{ dateText(d) }}
            <button type="button" class="ae-icon-btn" :aria-label="t('analysisEdit.dayFilter.removeDate', { date: dateText(d) })" @click="removeDate(d)">✕</button>
          </li>
        </ul>
        <div class="ae-inline">
          <input :id="f.fid('dayFilter.dates')" v-model="newDate" type="date" :aria-describedby="f.describedBy('dayFilter.dates')" @keydown.enter.prevent="addDate" />
          <button type="button" class="edit-btn ae-add-date" :disabled="!newDate" @click="addDate">{{ t('analysisEdit.dayFilter.addDate') }}</button>
        </div>
        <p v-if="f.problem('dayFilter.dates')" :id="f.errId('dayFilter.dates')" class="ae-error">{{ msg('dayFilter.dates') }}</p>
      </div>
      <p class="ae-hint">{{ t('analysisEdit.dayFilter.hint') }}</p>
    </section>
  </div>
</template>
