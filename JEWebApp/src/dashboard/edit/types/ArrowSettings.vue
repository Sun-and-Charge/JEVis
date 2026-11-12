<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { useBlock, type TypeEditorProps } from './shared'
import './types.css'

/* Arrow (config2/ArrowConfig.java, block `arrow`): orientation (ArrowWidget.ARROW_ORIENTATION) and shape (ARROW/LINE). */

const props = defineProps<TypeEditorProps>()
const { t } = useI18n()
const tt = (k: string) => t('dashboardEdit.types.' + k)
const uid = useId()
const b = useBlock(props, 'arrow')
const ORIENTATIONS = ['LEFT_RIGHT', 'RIGHT_LEFT', 'TOP_BOTTOM', 'BOTTOM_TOP'] as const
const SHAPES = ['ARROW', 'LINE'] as const
</script>

<template>
  <fieldset class="ae-section dt-settings">
    <legend>{{ tt('arrow.tab') }}</legend>
    <div class="ae-grid">
      <div class="edit-field">
        <label :for="uid + '-orientation'">{{ tt('arrow.orientation') }}</label>
        <select :id="uid + '-orientation'" :value="b.str('orientation', 'LEFT_RIGHT')" @change="b.set('orientation', ($event.target as HTMLSelectElement).value)">
          <option v-for="o in ORIENTATIONS" :key="o" :value="o">{{ tt('arrow.orientations.' + o) }}</option>
        </select>
      </div>
      <div class="edit-field">
        <label :for="uid + '-shape'">{{ tt('arrow.shape') }}</label>
        <select :id="uid + '-shape'" :value="b.str('shape', 'ARROW')" @change="b.set('shape', ($event.target as HTMLSelectElement).value)">
          <option v-for="s in SHAPES" :key="s" :value="s">{{ tt('arrow.shapes.' + s) }}</option>
        </select>
      </div>
    </div>
  </fieldset>
</template>
