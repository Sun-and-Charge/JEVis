<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import ColorField from './ColorField.vue'
import NumberField from './NumberField.vue'
import { isObject, toNumber, useBlock, type Block, type TypeEditorProps } from './shared'
import './types.css'

/*
 * Gauge (config2/GaugePojo.java, block `gaugeDesign`): show title/unit/value, values in percent, minimum/maximum and
 * the sections (GaugeSectionTableView: start, end, colour). Sections are chained as in the CC: the start of a section
 * is the end of the one before; with sections, minimum/maximum follow the smallest start / largest end.
 */

const props = defineProps<TypeEditorProps>()
const { t } = useI18n()
const tt = (k: string) => t('dashboardEdit.types.' + k)
const uid = useId()
const b = useBlock(props, 'gaugeDesign')

/** GaugeSectionPojo default colour Color.DARKBLUE */
const SECTION_COLOR = '0x00008bff'

const sections = computed<Block[]>(() => {
  const list = b.cfg.value.sections
  return Array.isArray(list) ? list.filter(isObject) : []
})
const hasSections = computed(() => sections.value.length > 0)

function sectionList(): Block[] {
  const block = b.block()
  if (!Array.isArray(block.sections)) block.sections = []
  return block.sections as Block[]
}

/** GaugeDesignTab.commitChanges: with sections minimum/maximum are taken from them */
function syncMinMax() {
  const list = sectionList()
  if (!list.length) return
  b.set('minimum', Math.min(...list.map((s) => toNumber(s.start) ?? 0)))
  b.set('maximum', Math.max(...list.map((s) => toNumber(s.end) ?? 0)))
}

function addSection() {
  const list = sectionList()
  const last = list[list.length - 1]
  /* new GaugeSectionPojo(lastEnd) / new GaugeSectionPojo(): start bound to the last end, end 0, DARKBLUE */
  const start = last ? toNumber(last.end) ?? 0 : 0
  list.push({ end: 0, start, color: SECTION_COLOR })
  syncMinMax()
}

function removeSection(index: number) {
  const list = sectionList()
  list.splice(index, 1)
  /* the CC chains the sections again on loading: start = end of the section before */
  if (index > 0 && index < list.length) list[index].start = list[index - 1].end
  if (list.length) syncMinMax()
}

function setStart(index: number, value: number) {
  const list = sectionList()
  list[index].start = value
  /* bound bidirectionally to the end of the section before */
  if (index > 0) list[index - 1].end = value
  syncMinMax()
}

function setEnd(index: number, value: number) {
  const list = sectionList()
  list[index].end = value
  if (index + 1 < list.length) list[index + 1].start = value
  syncMinMax()
}

function setColor(index: number, fx: string) {
  sectionList()[index].color = fx
}

const checks = ['showTitle', 'showUnit', 'showValue', 'inPercent'] as const
</script>

<template>
  <fieldset class="ae-section dt-settings">
    <legend>{{ tt('gauge.tab') }}</legend>
    <label v-for="key in checks" :key="key" class="ae-check">
      <input type="checkbox" :checked="b.bool(key, key !== 'inPercent')" @change="b.set(key, ($event.target as HTMLInputElement).checked)" />
      <span>{{ tt('gauge.' + key) }}</span>
    </label>
    <div class="ae-grid">
      <NumberField
        :id="uid + '-min'"
        :label="tt('common.min')"
        :value="b.num('minimum')"
        :disabled="hasSections"
        :hint="hasSections ? tt('gauge.fromSections') : undefined"
        @change="b.set('minimum', $event)"
      />
      <NumberField
        :id="uid + '-max'"
        :label="tt('common.max')"
        :value="b.num('maximum')"
        :disabled="hasSections"
        @change="b.set('maximum', $event)"
      />
    </div>

    <h4 class="dt-subhead">{{ tt('gauge.sections') }}</h4>
    <table v-if="hasSections" class="dt-table">
      <thead>
        <tr>
          <th scope="col">{{ tt('gauge.start') }}</th>
          <th scope="col">{{ tt('gauge.end') }}</th>
          <th scope="col">{{ tt('gauge.color') }}</th>
          <th scope="col"><span class="dt-sr">{{ tt('common.actions') }}</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(s, i) in sections" :key="i">
          <td>
            <NumberField
              :id="`${uid}-s${i}-start`"
              class="dt-cell"
              :label="tt('gauge.startOf') + ' ' + (i + 1)"
              :value="toNumber(s.start) ?? 0"
              @change="setStart(i, $event)"
            />
          </td>
          <td>
            <NumberField :id="`${uid}-s${i}-end`" class="dt-cell" :label="tt('gauge.endOf') + ' ' + (i + 1)" :value="toNumber(s.end) ?? 0" @change="setEnd(i, $event)" />
          </td>
          <td>
            <ColorField :id="`${uid}-s${i}-color`" class="dt-cell" :label="tt('gauge.colorOf') + ' ' + (i + 1)" :value="s.color" fallback="#00008b" @change="setColor(i, $event)" />
          </td>
          <td>
            <button type="button" class="ae-icon-btn is-danger" :aria-label="tt('gauge.removeSection') + ' ' + (i + 1)" :title="tt('gauge.removeSection')" @click="removeSection(i)">✕</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="ae-hint">{{ tt('gauge.noSections') }}</p>
    <button type="button" class="edit-btn dt-add" @click="addSection">{{ tt('gauge.addSection') }}</button>
  </fieldset>
</template>
