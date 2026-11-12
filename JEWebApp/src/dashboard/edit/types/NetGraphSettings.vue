<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import NumberField from './NumberField.vue'
import { rowObjects, sameJson, syncNetGraphRows, useBlock, type NetGraphRow, type TypeEditorProps } from './shared'
import './types.css'

/*
 * Net graph / radar chart (config2/NetGraphPojo.java + NetGraphTableFactory, block `NetGraph`): values in percent,
 * skin (NetGraphWidget.SKIN), the common maximum (without percent) and per data row minimum/maximum (only used and
 * editable with percent, as the CC). The per-row list follows the widget's rows: new rows get 0/100, removed rows are
 * dropped (the CC does that while its data table changes).
 */

const props = defineProps<TypeEditorProps>()
const { t } = useI18n()
const tt = (k: string) => t('dashboardEdit.types.' + k)
const uid = useId()
const b = useBlock(props, 'NetGraph')
const SKINS = ['SECTOR', 'POLYGON'] as const

const rows = computed(() => rowObjects(props.widget))
const entries = computed(() => syncNetGraphRows(b.cfg.value.netGraphDataRows, rows.value))
const inPercent = computed(() => b.bool('inPercent'))
const nameOf = (id: number) => rows.value.find((r) => r.id === id)?.name ?? `#${id}`

const toJson = (list: NetGraphRow[]) => list.map((e) => ({ min: e.min, max: e.max, id: e.id }))

function write(list: NetGraphRow[]) {
  b.set('netGraphDataRows', toJson(list))
}

/* rows added or removed in the data tab: follow them (only if that changes the stored list) */
watch(
  () => rows.value.map((r) => r.id).join(','),
  () => {
    if (!sameJson(b.cfg.value.netGraphDataRows ?? [], toJson(entries.value))) write(entries.value)
  },
)

function setValue(id: number, key: 'min' | 'max', value: number) {
  write(entries.value.map((e) => (e.id === id ? { ...e, [key]: value } : e)))
}
</script>

<template>
  <fieldset class="ae-section dt-settings">
    <legend>{{ tt('netGraph.tab') }}</legend>
    <label class="ae-check">
      <input type="checkbox" :checked="inPercent" @change="b.set('inPercent', ($event.target as HTMLInputElement).checked)" />
      <span>{{ tt('gauge.inPercent') }}</span>
    </label>
    <div class="ae-grid">
      <div class="edit-field">
        <label :for="uid + '-skin'">{{ tt('netGraph.skin') }}</label>
        <select :id="uid + '-skin'" :value="b.str('skin', 'SECTOR')" @change="b.set('skin', ($event.target as HTMLSelectElement).value)">
          <option v-for="s in SKINS" :key="s" :value="s">{{ tt('netGraph.skins.' + s) }}</option>
        </select>
      </div>
      <NumberField v-if="!inPercent" :id="uid + '-max'" :label="tt('netGraph.max')" :value="b.num('max', 35)" @change="b.set('max', $event)" />
    </div>

    <h4 class="dt-subhead">{{ tt('netGraph.rows') }}</h4>
    <p v-if="!entries.length" class="ae-hint">{{ tt('common.noRows') }}</p>
    <template v-else>
      <p v-if="!inPercent" class="ae-hint">{{ tt('netGraph.rowsHint') }}</p>
      <div class="dt-table-wrap">
        <table class="dt-table">
          <thead>
            <tr>
              <th scope="col">{{ tt('netGraph.row') }}</th>
              <th scope="col">{{ tt('netGraph.rowMin') }}</th>
              <th scope="col">{{ tt('netGraph.rowMax') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="e in entries" :key="e.id">
              <th scope="row">{{ nameOf(e.id) }}</th>
              <td>
                <NumberField
                  :id="`${uid}-r${e.id}-min`"
                  class="dt-cell"
                  :label="tt('netGraph.rowMin') + ' ' + nameOf(e.id)"
                  :value="e.min"
                  :disabled="!inPercent"
                  @change="setValue(e.id, 'min', $event)"
                />
              </td>
              <td>
                <NumberField
                  :id="`${uid}-r${e.id}-max`"
                  class="dt-cell"
                  :label="tt('netGraph.rowMax') + ' ' + nameOf(e.id)"
                  :value="e.max"
                  :disabled="!inPercent"
                  @change="setValue(e.id, 'max', $event)"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </fieldset>
</template>
