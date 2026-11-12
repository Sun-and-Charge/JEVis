<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RawWidget } from '../raw'
import ColorField from './ColorField.vue'
import NumberField from './NumberField.vue'
import { ensureBlock, toNumber, useBlock, widgetsOf, type TypeEditorProps } from './shared'
import './types.css'

/*
 * Value widget (widget/ValueWidget.java): the CC's tabs "Grenzen" (config2/Limit.java with LimitStaticPane /
 * LimitDynamicPane, block `limit`), "Prozent" (config2/Percent.java + PercentPane, block `percent`) and "Darstellung"
 * (`dimIncompletePeriod`). A dynamic limit refers to another Value widget (`limit.source`, multiplied by the
 * tolerance factors), the percentage to a Value widget as 100 % (`percent.source`, -1 = none).
 */

const props = defineProps<TypeEditorProps>()
const { t } = useI18n()
const tt = (k: string, p?: Record<string, unknown>) => t('dashboardEdit.types.' + k, p ?? {})
const uid = useId()
const limit = useBlock(props, 'limit')
const percent = useBlock(props, 'percent')

type Mode = 'STATIC' | 'DYNAMIC'
/* Limit(control, json): DYNAMIC if `source` > 0; the mode itself is not stored */
const storedMode = (): Mode => ((toNumber(limit.cfg.value.source) ?? -1) > 0 ? 'DYNAMIC' : 'STATIC')
const mode = ref<Mode>(storedMode())
watch(
  () => props.widget,
  () => {
    mode.value = storedMode()
  },
)

function setMode(value: Mode) {
  mode.value = value
  const block = ensureBlock(props.widget, 'limit')
  /* Limit.toJSON writes only the keys of the mode */
  if (value === 'DYNAMIC') {
    block.source = (toNumber(block.source) ?? -1) > 0 ? block.source : -1
    block.lowerLimitOffset = toNumber(block.lowerLimitOffset) ?? 1
    block.upperLimitOffset = toNumber(block.upperLimitOffset) ?? 1
    delete block.lowerLimitValue
    delete block.upperLimitValue
  } else {
    block.lowerLimitValue = toNumber(block.lowerLimitValue) ?? 0
    block.upperLimitValue = toNumber(block.upperLimitValue) ?? 0
    delete block.source
    delete block.lowerLimitOffset
    delete block.upperLimitOffset
  }
}

/* other Value widgets (LimitDynamicPane / PercentPane filter on ValueWidget.WIDGET_ID) */
const valueWidgets = computed(() => widgetsOf(props.source, ['Value'], props.widget.uuid))
const widgetLabel = (w: RawWidget) => `[${w.uuid}] '${typeof w.title === 'string' && w.title ? w.title : tt('value.noTitle')}'`
const missing = (id: number) => id > 0 && !valueWidgets.value.some((w) => w.uuid === id)

const limitSource = computed(() => toNumber(limit.cfg.value.source) ?? -1)
const percentSource = computed(() => toNumber(percent.cfg.value.source) ?? -1)

const sides = ['upper', 'lower'] as const
const valueKey = (side: (typeof sides)[number]) => (mode.value === 'DYNAMIC' ? `${side}LimitOffset` : `${side}LimitValue`)
const enabled = (side: (typeof sides)[number]) => limit.bool(`${side}Limit`)

const dim = computed(() => props.widget.dimIncompletePeriod === true)
</script>

<template>
  <div class="dt-settings">
    <fieldset class="ae-section">
      <legend>{{ tt('value.limitTab') }}</legend>
      <div class="ae-grid">
        <div class="edit-field ae-wide">
          <label :for="uid + '-mode'">{{ tt('value.limitType') }}</label>
          <select :id="uid + '-mode'" :value="mode" @change="setMode(($event.target as HTMLSelectElement).value as Mode)">
            <option value="STATIC">{{ tt('value.static') }}</option>
            <option value="DYNAMIC">{{ tt('value.dynamic') }}</option>
          </select>
        </div>
        <div v-if="mode === 'DYNAMIC'" class="edit-field ae-wide">
          <label :for="uid + '-source'">{{ tt('value.source') }}</label>
          <select :id="uid + '-source'" :value="limitSource > 0 ? limitSource : ''" @change="limit.set('source', Number(($event.target as HTMLSelectElement).value) || -1)">
            <option value="" disabled>{{ tt('value.chooseWidget') }}</option>
            <option v-for="w in valueWidgets" :key="w.uuid" :value="w.uuid">{{ widgetLabel(w) }}</option>
            <option v-if="missing(limitSource)" :value="limitSource">{{ tt('value.missingWidget', { id: limitSource }) }}</option>
          </select>
          <p v-if="!valueWidgets.length" class="ae-hint">{{ tt('value.noValueWidgets') }}</p>
        </div>
      </div>
      <div v-for="side in sides" :key="side" class="dt-limit">
        <label class="ae-check">
          <input type="checkbox" :checked="enabled(side)" @change="limit.set(`${side}Limit`, ($event.target as HTMLInputElement).checked)" />
          <span>{{ tt('value.enable.' + side) }}</span>
        </label>
        <div class="ae-grid">
          <NumberField
            :id="`${uid}-${side}-value`"
            :label="mode === 'DYNAMIC' ? tt('value.offset') : tt('value.limitValue')"
            :value="limit.num(valueKey(side), mode === 'DYNAMIC' ? 1 : 0)"
            :disabled="!enabled(side)"
            :hint="mode === 'DYNAMIC' && side === 'upper' ? tt('value.offsetHint') : undefined"
            @change="limit.set(valueKey(side), $event)"
          />
          <ColorField
            :id="`${uid}-${side}-color`"
            :label="tt('value.color.' + side)"
            :value="limit.get(`${side}LimitColor`, '')"
            :fallback="side === 'upper' ? '#ff0000' : '#008000'"
            :disabled="!enabled(side)"
            @change="limit.set(`${side}LimitColor`, $event)"
          />
        </div>
      </div>
    </fieldset>

    <fieldset class="ae-section">
      <legend>{{ tt('value.percentTab') }}</legend>
      <div class="ae-grid">
        <div class="edit-field ae-wide">
          <label :for="uid + '-psource'">{{ tt('value.source') }}</label>
          <select :id="uid + '-psource'" :value="percentSource > 0 ? percentSource : -1" @change="percent.set('source', Number(($event.target as HTMLSelectElement).value) || -1)">
            <option :value="-1">{{ tt('value.noLink') }}</option>
            <option v-for="w in valueWidgets" :key="w.uuid" :value="w.uuid">{{ widgetLabel(w) }}</option>
            <option v-if="missing(percentSource)" :value="percentSource">{{ tt('value.missingWidget', { id: percentSource }) }}</option>
          </select>
        </div>
        <NumberField
          :id="uid + '-minfrac'"
          :label="tt('value.minFracDigits')"
          :value="percent.num('minFracDigits')"
          :min="0"
          :max="20"
          integer
          @change="percent.set('minFracDigits', $event)"
        />
        <NumberField
          :id="uid + '-maxfrac'"
          :label="tt('value.maxFracDigits')"
          :value="percent.num('maxFracDigits')"
          :min="0"
          :max="20"
          integer
          @change="percent.set('maxFracDigits', $event)"
        />
      </div>
      <label class="ae-check">
        <input type="checkbox" :checked="percent.bool('diff')" @change="percent.set('diff', ($event.target as HTMLInputElement).checked)" />
        <span>{{ tt('value.diff') }}</span>
      </label>
    </fieldset>

    <fieldset class="ae-section">
      <legend>{{ tt('value.displayTab') }}</legend>
      <label class="ae-check">
        <input type="checkbox" :checked="dim" @change="widget.dimIncompletePeriod = ($event.target as HTMLInputElement).checked" />
        <span>{{ tt('value.dimIncomplete') }}</span>
      </label>
    </fieldset>
  </div>
</template>
