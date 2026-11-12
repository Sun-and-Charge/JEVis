<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useI18n } from 'vue-i18n'

/*
 * Number field of the dashboard editor (E4): shows the common value of the selected widgets ("verschieden" if they
 * differ) and emits each valid number typed; an empty or invalid input changes nothing.
 */

const props = defineProps<{
  id: string
  label: string
  value: number | undefined
  mixed?: boolean
  min?: number
  max?: number
  step?: number | 'any'
  problem?: string | null
  hint?: string
}>()
const emit = defineEmits<{ set: [value: number] }>()
const { t } = useI18n()

function onInput(e: Event) {
  const text = (e.target as HTMLInputElement).value.trim().replace(',', '.')
  if (text === '') return
  const n = Number(text)
  if (Number.isFinite(n)) emit('set', n)
}
const shown = () => (props.mixed || props.value === undefined || props.value === null || !Number.isFinite(props.value) ? '' : String(props.value))
</script>

<template>
  <div class="edit-field">
    <label :for="id">{{ label }}</label>
    <input
      :id="id"
      :value="shown()"
      type="number"
      :min="min"
      :max="max"
      :step="step ?? 1"
      inputmode="decimal"
      :placeholder="mixed ? t('dashboardEdit.mixed') : undefined"
      :aria-invalid="problem ? 'true' : undefined"
      :aria-describedby="problem ? id + '-err' : hint ? id + '-hint' : undefined"
      @input="onInput"
    />
    <p v-if="problem" :id="id + '-err'" class="ae-error">{{ problem }}</p>
    <p v-else-if="hint" :id="id + '-hint'" class="ae-hint">{{ hint }}</p>
  </div>
</template>
