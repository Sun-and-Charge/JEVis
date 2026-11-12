<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useId } from 'vue'
import { useI18n } from 'vue-i18n'
import ColorField from './ColorField.vue'
import NumberField from './NumberField.vue'
import { useBlock, type TypeEditorProps } from './shared'
import './types.css'

/*
 * Shape (config2/ShapePojo.java, block `shapeDesign`): form (ShapeWidget.SHAPE), minimum value and colour, maximum value
 * and colour, step distance (`increment`).
 */

const props = defineProps<TypeEditorProps>()
const { t } = useI18n()
const tt = (k: string) => t('dashboardEdit.types.' + k)
const uid = useId()
const b = useBlock(props, 'shapeDesign')
const SHAPES = ['ELLIPSE', 'RECTANGLE'] as const
</script>

<template>
  <fieldset class="ae-section dt-settings">
    <legend>{{ tt('shape.tab') }}</legend>
    <div class="ae-grid">
      <div class="edit-field ae-wide">
        <label :for="uid + '-shape'">{{ tt('shape.shape') }}</label>
        <select :id="uid + '-shape'" :value="b.str('shape', 'RECTANGLE')" @change="b.set('shape', ($event.target as HTMLSelectElement).value)">
          <option v-for="s in SHAPES" :key="s" :value="s">{{ tt('shape.shapes.' + s) }}</option>
          <option v-if="!(SHAPES as readonly string[]).includes(b.str('shape', 'RECTANGLE'))" :value="b.str('shape')">{{ b.str('shape') }}</option>
        </select>
      </div>
      <NumberField :id="uid + '-min'" :label="tt('common.min')" :value="b.num('minValue')" @change="b.set('minValue', $event)" />
      <ColorField :id="uid + '-mincolor'" :label="tt('shape.minColor')" :value="b.get('minColor', '')" fallback="#008000" @change="b.set('minColor', $event)" />
      <NumberField :id="uid + '-max'" :label="tt('common.max')" :value="b.num('maxValue', 100)" @change="b.set('maxValue', $event)" />
      <ColorField :id="uid + '-maxcolor'" :label="tt('shape.maxColor')" :value="b.get('maxColor', '')" fallback="#ff0000" @change="b.set('maxColor', $event)" />
      <NumberField :id="uid + '-step'" :label="tt('shape.stepDistance')" :value="b.num('increment')" @change="b.set('increment', $event)" />
    </div>
  </fieldset>
</template>
