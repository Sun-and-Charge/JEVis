<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import NumberField from './NumberField.vue'
import { useBlock, type TypeEditorProps } from './shared'
import './types.css'

/*
 * Slider (config2/MinMaxPojo.java: `minMax.minimum` / `maximum`) and PlusMinus (config2/IncrementPojo.java:
 * `minMax.Increment`, default 0.1). Both use the block `minMax`. The server keeps a PlusMinus step at most 10
 * (docs/WEB-API.md, `…/value`), so the input is limited to that.
 */

const props = defineProps<TypeEditorProps>()
const { t } = useI18n()
const tt = (k: string) => t('dashboardEdit.types.' + k)
const uid = useId()
const b = useBlock(props, 'minMax')
const isPlusMinus = computed(() => props.widget.WidgetType === 'PlusMinus')
</script>

<template>
  <fieldset class="ae-section dt-settings">
    <legend>{{ isPlusMinus ? tt('minMax.increment') : tt('minMax.tab') }}</legend>
    <div v-if="isPlusMinus" class="ae-grid">
      <NumberField
        :id="uid + '-increment'"
        :label="tt('minMax.increment')"
        :value="b.num('Increment', 0.1)"
        :min="0"
        :max="10"
        :hint="tt('minMax.incrementHint')"
        @change="b.set('Increment', $event)"
      />
    </div>
    <div v-else class="ae-grid">
      <NumberField :id="uid + '-min'" :label="tt('common.min')" :value="b.num('minimum')" @change="b.set('minimum', $event)" />
      <NumberField :id="uid + '-max'" :label="tt('common.max')" :value="b.num('maximum')" @change="b.set('maximum', $event)" />
    </div>
  </fieldset>
</template>
