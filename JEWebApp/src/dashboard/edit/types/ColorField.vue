<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { ref, watch } from 'vue'
import { toHexInput } from '../raw'
import { fxFromInput } from './shared'

/*
 * Colour of a type setting: colour picker plus the hex value (as the E3 row colour). The source keeps JavaFX
 * `0xRRGGBBAA`; the alpha of the stored value stays.
 */

const props = defineProps<{ id: string; label: string; value: unknown; fallback?: string; disabled?: boolean }>()
const emit = defineEmits<{ change: [fx: string] }>()

const hex = () => toHexInput(props.value, props.fallback ?? '#000000')
const text = ref(hex())
watch(
  () => props.value,
  () => {
    text.value = hex()
  },
)

function apply(value: string) {
  const v = value.trim()
  const normalized = /^#?[0-9a-f]{6}$/i.test(v) ? (v.startsWith('#') ? v : '#' + v).toLowerCase() : null
  if (!normalized) return false
  const fx = fxFromInput(normalized, props.value)
  if (fx) emit('change', fx)
  return !!fx
}

function onText(event: Event) {
  const value = (event.target as HTMLInputElement).value
  text.value = value
  if (!apply(value)) text.value = hex()
}
</script>

<template>
  <div class="edit-field">
    <label :for="id">{{ label }}</label>
    <div class="ae-color">
      <input
        :value="hex()"
        type="color"
        tabindex="-1"
        :aria-label="label"
        :disabled="disabled"
        @input="apply(($event.target as HTMLInputElement).value)"
      />
      <input :id="id" v-model="text" type="text" maxlength="7" autocomplete="off" spellcheck="false" :disabled="disabled" @change="onText" />
    </div>
  </div>
</template>
