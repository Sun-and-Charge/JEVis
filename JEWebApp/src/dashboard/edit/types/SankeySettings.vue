<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import NumberField from './NumberField.vue'
import { isObject, rowObjects, sameJson, sankeyMaxLevel, syncSankeyRows, toNumber, useBlock, type SankeyRow, type TypeEditorProps } from './shared'
import './types.css'

/*
 * Sankey (config2/SankeyPojo.java + SankeyTableFactory, block `Sankey`): show values, show percentages (refer to the
 * parent or the root), automatic gap or a fixed gap, error tolerance, the horizontal offset of the inner levels
 * (-800…800, IntegerSpinnerValueFactory) and per data row the rows it flows into plus the vertical order. The row
 * list follows the widget's rows (stored order first, new rows at the end, removed rows dropped).
 */

const props = defineProps<TypeEditorProps>()
const { t } = useI18n()
const tt = (k: string, p?: Record<string, unknown>) => t('dashboardEdit.types.' + k, p ?? {})
const uid = useId()
const b = useBlock(props, 'Sankey')
const OFFSET_MIN = -800
const OFFSET_MAX = 800

const rows = computed(() => rowObjects(props.widget))
const entries = computed(() => syncSankeyRows(b.cfg.value.sankeyDataRows, rows.value))
const nameOf = (id: number) => rows.value.find((r) => r.id === id)?.name ?? `#${id}`
const toJson = (list: SankeyRow[]) => list.map((e) => ({ id: e.id, children: [...e.children] }))

function write(list: SankeyRow[]) {
  b.set('sankeyDataRows', toJson(list))
}

watch(
  () => rows.value.map((r) => r.id).join(','),
  () => {
    if (!sameJson(b.cfg.value.sankeyDataRows ?? [], toJson(entries.value))) write(entries.value)
  },
)

function toggleChild(id: number, child: number, on: boolean) {
  write(
    entries.value.map((e) => {
      if (e.id !== id) return e
      const children = on ? [...e.children.filter((c) => c !== child), child] : e.children.filter((c) => c !== child)
      return { ...e, children }
    }),
  )
}

function move(index: number, delta: -1 | 1) {
  const list = entries.value.slice()
  const target = index + delta
  if (target < 0 || target >= list.length) return
  ;[list[index], list[target]] = [list[target], list[index]]
  write(list)
}

/* offsets of the levels 1 … maxLevel-1 (the first and last level cannot be moved) */
const maxLevel = computed(() => sankeyMaxLevel(entries.value))
const levels = computed(() => Array.from({ length: Math.max(0, maxLevel.value - 1) }, (_, i) => i + 1))
const storedOffsets = computed(() => {
  const map = new Map<number, number>()
  const list = b.cfg.value.offsets
  for (const o of Array.isArray(list) ? list : []) {
    if (!isObject(o)) continue
    const level = toNumber(o.level)
    if (level !== null) map.set(Math.trunc(level), Math.trunc(toNumber(o.offset) ?? 0))
  }
  return map
})
const offsetOf = (level: number) => storedOffsets.value.get(level) ?? 0

/* GaugeDesignTab.commitChanges: the offsets are written for every level shown */
function setOffset(level: number, value: number) {
  b.set(
    'offsets',
    levels.value.map((l) => ({ level: l, offset: l === level ? value : offsetOf(l) })),
  )
}

const refersTo = computed(() => (b.str('refersTo', 'PARENT') === 'ROOT' ? 'ROOT' : 'PARENT'))
const showPercent = computed(() => b.bool('showPercent', true))
const autoGap = computed(() => b.bool('autoGap'))
</script>

<template>
  <fieldset class="ae-section dt-settings">
    <legend>{{ tt('sankey.tab') }}</legend>
    <label class="ae-check">
      <input type="checkbox" :checked="b.bool('showValue', true)" @change="b.set('showValue', ($event.target as HTMLInputElement).checked)" />
      <span>{{ tt('sankey.showValue') }}</span>
    </label>
    <label class="ae-check">
      <input type="checkbox" :checked="showPercent" @change="b.set('showPercent', ($event.target as HTMLInputElement).checked)" />
      <span>{{ tt('sankey.showPercent') }}</span>
    </label>
    <div class="ae-grid">
      <div v-if="showPercent" class="edit-field ae-wide">
        <label :for="uid + '-refers'">{{ tt('sankey.refersTo') }}</label>
        <select :id="uid + '-refers'" :value="refersTo" @change="b.set('refersTo', ($event.target as HTMLSelectElement).value)">
          <option value="PARENT">{{ tt('sankey.parent') }}</option>
          <option value="ROOT">{{ tt('sankey.root') }}</option>
        </select>
      </div>
    </div>
    <label class="ae-check">
      <input type="checkbox" :checked="autoGap" @change="b.set('autoGap', ($event.target as HTMLInputElement).checked)" />
      <span>{{ tt('sankey.autoGap') }}</span>
    </label>
    <div class="ae-grid">
      <NumberField v-if="!autoGap" :id="uid + '-gap'" :label="tt('sankey.gap')" :value="b.num('gap')" integer @change="b.set('gap', $event)" />
      <NumberField :id="uid + '-tolerance'" :label="tt('sankey.errorTolerance')" :value="b.num('errorTolerance')" @change="b.set('errorTolerance', $event)" />
    </div>

    <template v-if="levels.length">
      <h4 class="dt-subhead">{{ tt('sankey.offsets') }}</h4>
      <p class="ae-hint">{{ tt('sankey.offsetHint') }}</p>
      <div class="ae-grid">
        <NumberField
          v-for="level in levels"
          :id="`${uid}-offset-${level}`"
          :key="level"
          :label="tt('sankey.levelOffset', { level: level + 1 })"
          :value="offsetOf(level)"
          :min="OFFSET_MIN"
          :max="OFFSET_MAX"
          integer
          @change="setOffset(level, $event)"
        />
      </div>
    </template>

    <h4 class="dt-subhead">{{ tt('sankey.rows') }}</h4>
    <p v-if="!entries.length" class="ae-hint">{{ tt('common.noRows') }}</p>
    <div v-else class="dt-table-wrap">
      <table class="dt-table">
        <thead>
          <tr>
            <th scope="col">{{ tt('sankey.objectName') }}</th>
            <th scope="col">{{ tt('sankey.id') }}</th>
            <th scope="col">{{ tt('sankey.flowsInto') }}</th>
            <th scope="col">{{ tt('sankey.move') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(e, i) in entries" :key="e.id">
            <th scope="row">{{ nameOf(e.id) }}</th>
            <td class="dt-num">{{ e.id }}</td>
            <td>
              <details class="dt-flows">
                <summary>{{ e.children.length ? e.children.map(nameOf).join(', ') : tt('sankey.none') }}</summary>
                <ul>
                  <li v-for="other in entries.filter((o) => o.id !== e.id)" :key="other.id">
                    <label class="ae-check">
                      <input
                        type="checkbox"
                        :checked="e.children.includes(other.id)"
                        @change="toggleChild(e.id, other.id, ($event.target as HTMLInputElement).checked)"
                      />
                      <span>{{ nameOf(other.id) }}</span>
                    </label>
                  </li>
                </ul>
              </details>
            </td>
            <td>
              <div class="dt-actions">
                <button type="button" class="ae-icon-btn" :disabled="i === 0" :aria-label="tt('sankey.up', { name: nameOf(e.id) })" @click="move(i, -1)">↑</button>
                <button type="button" class="ae-icon-btn" :disabled="i === entries.length - 1" :aria-label="tt('sankey.down', { name: nameOf(e.id) })" @click="move(i, 1)">↓</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </fieldset>
</template>
