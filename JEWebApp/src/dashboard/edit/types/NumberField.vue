<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { clamp, parseInput } from './shared'

/*
 * Number of a type setting. Like the CC's text fields an invalid entry keeps the old value; values outside
 * `min`/`max` are clamped, `integer` rounds.
 */

const props = defineProps<{
  id: string
  label: string
  value: number
  min?: number
  max?: number
  step?: number | 'any'
  integer?: boolean
  disabled?: boolean
  hint?: string
}>()
const emit = defineEmits<{ change: [value: number] }>()

function onChange(event: Event) {
  const input = event.target as HTMLInputElement
  let n = parseInput(input.value)
  if (n === null) {
    input.value = String(props.value)
    return
  }
  if (props.integer) n = Math.round(n)
  n = clamp(n, props.min ?? -Infinity, props.max ?? Infinity)
  input.value = String(n)
  if (n !== props.value) emit('change', n)
}
</script>

<template>
  <div class="edit-field">
    <label :for="id">{{ label }}</label>
    <input
      :id="id"
      :value="value"
      type="number"
      :min="min"
      :max="max"
      :step="step ?? (integer ? 1 : 'any')"
      :disabled="disabled"
      :aria-describedby="hint ? id + '-hint' : undefined"
      @change="onChange"
    />
    <p v-if="hint" :id="id + '-hint'" class="ae-hint">{{ hint }}</p>
  </div>
</template>
