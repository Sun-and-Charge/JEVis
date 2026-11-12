<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { alphaPercent } from '../../../dashboard/edit/source'
import { toHexInput } from '../../../dashboard/edit/raw'

/*
 * Colour of the dashboard editor (E4): a colour picker with its hex value and, optionally, the opacity in percent.
 * The value is the stored JavaFX colour (`0xRRGGBBAA`) or CSS; the parent converts with raw.ts.
 */

const props = defineProps<{
  id: string
  label: string
  /** stored colour of the (first) widget */
  value: unknown
  mixed?: boolean
  opacity?: boolean
  problem?: string | null
}>()
const emit = defineEmits<{ color: [hex: string]; alpha: [percent: number] }>()
const { t } = useI18n()

const hex = computed(() => toHexInput(props.value, '#ffffff'))
const alpha = computed(() => alphaPercent(props.value))

function onText(e: Event) {
  const v = (e.target as HTMLInputElement).value.trim()
  if (/^#[0-9a-f]{6}$/i.test(v)) emit('color', v.toLowerCase())
}
function onAlpha(e: Event) {
  const n = Number((e.target as HTMLInputElement).value)
  if (Number.isFinite(n) && n >= 0 && n <= 100) emit('alpha', n)
}
</script>

<template>
  <div class="edit-field">
    <label :for="id">{{ label }}</label>
    <div class="ae-color">
      <input :value="hex" type="color" tabindex="-1" :aria-label="label" @input="emit('color', ($event.target as HTMLInputElement).value)" />
      <input
        :id="id"
        :value="mixed ? '' : hex"
        type="text"
        maxlength="7"
        spellcheck="false"
        :placeholder="mixed ? t('dashboardEdit.mixed') : '#rrggbb'"
        :aria-invalid="problem ? 'true' : undefined"
        :aria-describedby="problem ? id + '-err' : undefined"
        @input="onText"
      />
      <template v-if="opacity">
        <input
          :id="id + '-alpha'"
          :value="mixed ? '' : alpha"
          type="number"
          min="0"
          max="100"
          step="1"
          class="dbe-alpha"
          :aria-label="t('dashboardEdit.opacityOf', { name: label })"
          :title="t('dashboardEdit.opacity')"
          @input="onAlpha"
        />
        <span class="dbe-unit" aria-hidden="true">%</span>
      </template>
    </div>
    <p v-if="problem" :id="id + '-err'" class="ae-error">{{ problem }}</p>
  </div>
</template>
