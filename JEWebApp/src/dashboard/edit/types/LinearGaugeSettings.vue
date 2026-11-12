<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useId } from 'vue'
import { useI18n } from 'vue-i18n'
import ColorField from './ColorField.vue'
import NumberField from './NumberField.vue'
import { useBlock, type TypeEditorProps } from './shared'
import './types.css'

/*
 * Linear gauge (config2/LinearGaugePojo.java, block `linearGaugeDesign`), fields in the order of the CC tab; the step
 * of the major ticks is shown only with major ticks (as the CC).
 */

const props = defineProps<TypeEditorProps>()
const { t } = useI18n()
const tt = (k: string) => t('dashboardEdit.types.' + k)
const uid = useId()
const b = useBlock(props, 'linearGaugeDesign')
const checks = ['showTitle', 'showUnit', 'showValue', 'inPercent'] as const
const ticks = ['showMajorTick', 'showMediumTick', 'showMinorTick'] as const
</script>

<template>
  <fieldset class="ae-section dt-settings">
    <legend>{{ tt('gauge.tab') }}</legend>
    <label v-for="key in checks" :key="key" class="ae-check">
      <input type="checkbox" :checked="b.bool(key, key !== 'inPercent')" @change="b.set(key, ($event.target as HTMLInputElement).checked)" />
      <span>{{ tt('gauge.' + key) }}</span>
    </label>
    <div class="ae-grid">
      <NumberField :id="uid + '-min'" :label="tt('common.min')" :value="b.num('minimum')" @change="b.set('minimum', $event)" />
      <NumberField :id="uid + '-max'" :label="tt('common.max')" :value="b.num('maximum')" @change="b.set('maximum', $event)" />
      <NumberField
        v-if="b.bool('showMajorTick', true)"
        :id="uid + '-step'"
        :label="tt('linearGauge.majorTickStep')"
        :value="b.num('majorTickStep', 5)"
        @change="b.set('majorTickStep', $event)"
      />
      <ColorField :id="uid + '-color'" :label="tt('gauge.valueIndicatorColor')" :value="b.get('color', '')" fallback="#00008b" @change="b.set('color', $event)" />
    </div>
    <label v-for="key in ticks" :key="key" class="ae-check">
      <input type="checkbox" :checked="b.bool(key, true)" @change="b.set(key, ($event.target as HTMLInputElement).checked)" />
      <span>{{ tt('linearGauge.' + key) }}</span>
    </label>
    <div class="ae-grid">
      <ColorField :id="uid + '-border'" :label="tt('linearGauge.borderColor')" :value="b.get('colorBorder', '')" fallback="#000000" @change="b.set('colorBorder', $event)" />
    </div>
  </fieldset>
</template>
