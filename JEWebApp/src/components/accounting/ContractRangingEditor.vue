<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { addRangingRow, RANGING_MAX, removeRangingRow, type RangingField, type RangingFormRow, type RangingProblem } from '../../accounting/contractEdit'

/*
 * Table editor of a ranging attribute (E6.2, grid operator "Demand Charge"), as the Control Center's
 * RangingValueEditor: rows von / bis / Wert, add and remove, "unbegrenzt" for the end of the last row (1e9).
 * The checks (numbers, von < bis, sorted, no overlap, 1–50 rows) are done by the dialog on save and shown here.
 */

const props = defineProps<{
  rows: RangingFormRow[]
  problems: RangingProblem[]
  label: string
  unit?: string | null
  idBase: string
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:rows': [rows: RangingFormRow[]] }>()
const { t } = useI18n()

const cellId = (row: number, field: RangingField) => `${props.idBase}-r${row}-${field}`
const errId = (row: number) => `${props.idBase}-r${row}-err`
const general = computed(() => props.problems.filter((p) => p.row == null))
const rowProblems = (row: number) => props.problems.filter((p) => p.row === row)
const invalid = (row: number, field: RangingField) => props.problems.some((p) => p.row === row && p.field === field)

function set(row: number, field: RangingField, e: Event) {
  const next = props.rows.map((r) => ({ ...r }))
  next[row][field] = (e.target as HTMLInputElement).value
  emit('update:rows', next)
}

function setUnlimited(row: number, e: Event) {
  const next = props.rows.map((r) => ({ ...r }))
  next[row].unlimited = (e.target as HTMLInputElement).checked
  emit('update:rows', next)
}

async function add() {
  const next = addRangingRow(props.rows)
  emit('update:rows', next)
  await nextTick()
  const row = next.length - 1
  document.getElementById(cellId(row, next[row].from ? 'value' : 'from'))?.focus()
}

async function remove(row: number) {
  const next = removeRangingRow(props.rows, row)
  emit('update:rows', next)
  await nextTick()
  document.getElementById(next.length ? cellId(Math.min(row, next.length - 1), 'from') : props.idBase + '-add')?.focus()
}

const valueLabel = computed(() => (props.unit ? `${t('contracts.ranging.value')} [${props.unit}]` : t('contracts.ranging.value')))
</script>

<template>
  <div class="ctr-rng" role="group" :aria-labelledby="idBase + '-label'">
    <span :id="idBase + '-label'" class="reg-edit-label">{{ label }}</span>
    <div class="iso-mini-wrap">
      <table class="iso-mini ctr-rng-table">
        <thead>
          <tr>
            <th scope="col">{{ t('contracts.ranging.from') }}</th>
            <th scope="col">{{ t('contracts.ranging.to') }}</th>
            <th scope="col">{{ valueLabel }}</th>
            <th scope="col"><span class="visually-hidden">{{ t('contractEdit.ranging.actions') }}</span></th>
          </tr>
        </thead>
        <tbody>
          <template v-for="(r, i) in rows" :key="i">
            <tr :class="{ 'has-problem': rowProblems(i).length }">
              <td>
                <input
                  :id="cellId(i, 'from')"
                  type="text"
                  inputmode="decimal"
                  autocomplete="off"
                  :value="r.from"
                  :disabled="disabled"
                  :aria-label="t('contractEdit.ranging.cell', { field: t('contracts.ranging.from'), row: i + 1 })"
                  :aria-invalid="invalid(i, 'from') ? 'true' : undefined"
                  :aria-describedby="rowProblems(i).length ? errId(i) : undefined"
                  @input="set(i, 'from', $event)"
                />
              </td>
              <td>
                <input
                  v-if="!r.unlimited"
                  :id="cellId(i, 'to')"
                  type="text"
                  inputmode="decimal"
                  autocomplete="off"
                  :value="r.to"
                  :disabled="disabled"
                  :aria-label="t('contractEdit.ranging.cell', { field: t('contracts.ranging.to'), row: i + 1 })"
                  :aria-invalid="invalid(i, 'to') ? 'true' : undefined"
                  :aria-describedby="rowProblems(i).length ? errId(i) : undefined"
                  @input="set(i, 'to', $event)"
                />
                <span v-else class="ctr-rng-open muted">{{ t('contractEdit.ranging.unlimited') }}</span>
                <label v-if="i === rows.length - 1" class="ctr-rng-unlimited">
                  <input type="checkbox" :checked="r.unlimited" :disabled="disabled" @change="setUnlimited(i, $event)" />
                  {{ t('contractEdit.ranging.unlimited') }}
                </label>
              </td>
              <td>
                <input
                  :id="cellId(i, 'value')"
                  type="text"
                  inputmode="decimal"
                  autocomplete="off"
                  :value="r.value"
                  :disabled="disabled"
                  :aria-label="t('contractEdit.ranging.cell', { field: t('contracts.ranging.value'), row: i + 1 })"
                  :aria-invalid="invalid(i, 'value') ? 'true' : undefined"
                  :aria-describedby="rowProblems(i).length ? errId(i) : undefined"
                  @input="set(i, 'value', $event)"
                />
              </td>
              <td class="ctr-rng-actions">
                <button
                  type="button"
                  class="edit-btn is-small is-danger ctr-rng-remove"
                  :disabled="disabled"
                  :aria-label="t('contractEdit.ranging.removeLabel', { row: i + 1 })"
                  :title="t('contractEdit.ranging.removeLabel', { row: i + 1 })"
                  @click="remove(i)"
                >
                  ×
                </button>
              </td>
            </tr>
            <tr v-if="rowProblems(i).length" class="ctr-rng-problem-row">
              <td colspan="4">
                <p :id="errId(i)" class="iso-field-error">
                  {{ t('contractEdit.ranging.rowProblem', { row: i + 1 }) }}
                  <template v-for="(p, k) in rowProblems(i)" :key="k">{{ ' ' + t(p.key, p.params ?? {}) }}</template>
                </p>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
    <p v-for="(p, k) in general" :key="k" class="iso-field-error">{{ t(p.key, p.params ?? {}) }}</p>
    <button :id="idBase + '-add'" type="button" class="edit-btn is-small ctr-rng-add" :disabled="disabled || rows.length >= RANGING_MAX" @click="add">
      + {{ t('contractEdit.ranging.add') }}
    </button>
  </div>
</template>
