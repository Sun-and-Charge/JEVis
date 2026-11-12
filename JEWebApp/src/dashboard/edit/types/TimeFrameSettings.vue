<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { widgetRows, type RawWidget } from '../raw'
import { isDataWidget, isObject, toNumber, typeLabelKey, useBlock, widgetsOf, type Block, type TypeEditorProps } from './shared'
import './types.css'

/*
 * Time Frame (config2/TimeFramePojo.java + TimeFrameTableView, block `"Time Frame"`): date format and the widget whose
 * interval is shown (`selectedWidget`: `id` = uuid, `start` PERIODE_FROM|NONE, `end` LAST_TS|PERIODE_UNTIL|NONE,
 * `isCount` = number of samples). Candidates are the other widgets with data rows (DataModelWidget). The CC disables
 * the table if the Time Frame widget has rows of its own. Start/end and the sample count exclude each other
 * (TimeFrameWidgetObject listeners).
 */

const props = defineProps<TypeEditorProps>()
const { t } = useI18n()
const tt = (k: string) => t('dashboardEdit.types.' + k)
const uid = useId()
const b = useBlock(props, 'Time Frame')

const STARTS = ['NONE', 'PERIODE_FROM'] as const
const ENDS = ['NONE', 'LAST_TS', 'PERIODE_UNTIL'] as const

const candidates = computed(() => widgetsOf(props.source, (type) => type !== 'Time Frame' && isDataWidget(type), props.widget.uuid))
const ownRows = computed(() => widgetRows(props.widget).length > 0)

const selected = computed<Block>(() => (isObject(b.cfg.value.selectedWidget) ? b.cfg.value.selectedWidget : {}))
const selectedId = computed(() => toNumber(selected.value.id) ?? -1)
const start = computed(() => (typeof selected.value.start === 'string' && selected.value.start ? selected.value.start : 'NONE'))
const end = computed(() => (typeof selected.value.end === 'string' && selected.value.end ? selected.value.end : 'NONE'))
const count = computed(() => selected.value.isCount === true)

function selection(): Block {
  const block = b.block()
  if (!isObject(block.selectedWidget)) block.selectedWidget = { start: '', end: '', id: -1, isCount: false }
  return block.selectedWidget as Block
}

function select(id: number) {
  const s = selection()
  if (toNumber(s.id) === id) return
  s.id = id
  /* a newly selected TimeFrameWidgetObject starts with NONE / NONE / no count */
  s.start = id > 0 ? 'NONE' : ''
  s.end = id > 0 ? 'NONE' : ''
  s.isCount = false
}

function setStart(value: string) {
  const s = selection()
  s.start = value
  if (value !== 'NONE') s.isCount = false
}
function setEnd(value: string) {
  const s = selection()
  s.end = value
  if (value !== 'NONE') s.isCount = false
}
function setCount(value: boolean) {
  const s = selection()
  s.isCount = value
  if (value) {
    s.start = 'NONE'
    s.end = 'NONE'
  }
}

const title = (w: RawWidget) => (typeof w.title === 'string' ? w.title : '')
const typeName = (type: string) => {
  const key = typeLabelKey(type)
  return key ? t(key) : type
}
const missing = computed(() => selectedId.value > 0 && !candidates.value.some((w) => w.uuid === selectedId.value))
</script>

<template>
  <fieldset class="ae-section dt-settings">
    <legend>{{ tt('timeFrame.tab') }}</legend>
    <div class="ae-grid">
      <div class="edit-field ae-wide">
        <label :for="uid + '-format'">{{ tt('timeFrame.format') }}</label>
        <input
          :id="uid + '-format'"
          :value="b.str('format', 'yyyy.MM.dd HH:mm')"
          type="text"
          maxlength="100"
          autocomplete="off"
          spellcheck="false"
          :aria-describedby="uid + '-format-hint'"
          @change="b.set('format', ($event.target as HTMLInputElement).value)"
        />
        <p :id="uid + '-format-hint'" class="ae-hint">{{ tt('timeFrame.formatHint') }}</p>
      </div>
    </div>
    <p v-if="ownRows" class="ae-hint">{{ tt('timeFrame.ownRows') }}</p>
    <p v-else-if="!candidates.length" class="ae-hint">{{ tt('timeFrame.noWidgets') }}</p>
    <div v-if="candidates.length" class="dt-table-wrap">
      <table class="dt-table">
        <caption class="dt-sr">{{ tt('timeFrame.tableCaption') }}</caption>
        <thead>
          <tr>
            <th scope="col">{{ tt('timeFrame.selected') }}</th>
            <th scope="col">{{ tt('timeFrame.id') }}</th>
            <th scope="col">{{ tt('timeFrame.type') }}</th>
            <th scope="col">{{ tt('timeFrame.title') }}</th>
            <th scope="col">{{ tt('timeFrame.start') }}</th>
            <th scope="col">{{ tt('timeFrame.end') }}</th>
            <th scope="col">{{ tt('timeFrame.count') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="w in candidates" :key="w.uuid" :class="{ 'is-selected': w.uuid === selectedId }">
            <td>
              <input
                :id="`${uid}-sel-${w.uuid}`"
                type="radio"
                :name="uid + '-selected'"
                :checked="w.uuid === selectedId"
                :disabled="ownRows"
                :aria-label="tt('timeFrame.selectWidget') + ' ' + w.uuid"
                @change="select(w.uuid)"
              />
            </td>
            <td class="dt-num">{{ w.uuid }}</td>
            <td>{{ typeName(w.WidgetType) }}</td>
            <td>{{ title(w) }}</td>
            <td>
              <select
                v-if="w.uuid === selectedId"
                :value="start"
                :disabled="ownRows"
                :aria-label="tt('timeFrame.start')"
                @change="setStart(($event.target as HTMLSelectElement).value)"
              >
                <option v-for="s in STARTS" :key="s" :value="s">{{ tt('timeFrame.starts.' + s) }}</option>
              </select>
            </td>
            <td>
              <select v-if="w.uuid === selectedId" :value="end" :disabled="ownRows" :aria-label="tt('timeFrame.end')" @change="setEnd(($event.target as HTMLSelectElement).value)">
                <option v-for="e in ENDS" :key="e" :value="e">{{ tt('timeFrame.ends.' + e) }}</option>
              </select>
            </td>
            <td>
              <input
                v-if="w.uuid === selectedId"
                type="checkbox"
                :checked="count"
                :disabled="ownRows"
                :aria-label="tt('timeFrame.count')"
                @change="setCount(($event.target as HTMLInputElement).checked)"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="missing" class="ae-hint">{{ tt('timeFrame.missing') }} {{ selectedId }}</p>
    <button v-if="selectedId > 0 && !ownRows" type="button" class="edit-btn dt-add" @click="select(-1)">{{ tt('timeFrame.clear') }}</button>
  </fieldset>
</template>
