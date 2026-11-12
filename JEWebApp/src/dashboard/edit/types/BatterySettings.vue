<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useId } from 'vue'
import { useI18n } from 'vue-i18n'
import ColorField from './ColorField.vue'
import NumberField from './NumberField.vue'
import { useBlock, type TypeEditorProps } from './shared'
import './types.css'

/* Battery (config2/BatteryPojo.java, block `BatteryDesign`): show value, minimum, maximum, colour of the value. */

const props = defineProps<TypeEditorProps>()
const { t } = useI18n()
const tt = (k: string) => t('dashboardEdit.types.' + k)
const uid = useId()
const b = useBlock(props, 'BatteryDesign')
</script>

<template>
  <fieldset class="ae-section dt-settings">
    <legend>{{ tt('names.battery') }}</legend>
    <label class="ae-check">
      <input type="checkbox" :checked="b.bool('showValue', true)" @change="b.set('showValue', ($event.target as HTMLInputElement).checked)" />
      <span>{{ tt('gauge.showValue') }}</span>
    </label>
    <div class="ae-grid">
      <NumberField :id="uid + '-min'" :label="tt('common.min')" :value="b.num('minimum')" @change="b.set('minimum', $event)" />
      <NumberField :id="uid + '-max'" :label="tt('common.max')" :value="b.num('maximum')" @change="b.set('maximum', $event)" />
      <ColorField :id="uid + '-color'" :label="tt('gauge.valueIndicatorColor')" :value="b.get('color', '')" fallback="#008000" @change="b.set('color', $event)" />
    </div>
  </fieldset>
</template>
