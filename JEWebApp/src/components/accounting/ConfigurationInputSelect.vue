<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { inputOptionLabel, inputTitle } from '../../accounting/configurationEdit'
import type { ConfigurationInput } from '../../api/contracts'
import { matchesTerms, searchTerms } from '../../modules/search'

/*
 * One input selector of an accounting configuration (E6.3), as the Control Center's input pane: label = the
 * template's variable names, the object class, a search field and a select of the offered objects. "nicht
 * zugeordnet" is allowed (warning badge); a chosen object the user cannot read stays as "Kein Leserecht".
 */

const props = defineProps<{
  input: ConfigurationInput
  modelValue: number | null
  idBase: string
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [id: number | null] }>()
const { t, locale } = useI18n()

const query = ref('')
const title = computed(() => inputTitle(props.input))
const options = computed(() => (props.input.options ?? []).filter((o) => o && typeof o.id === 'number'))
const hidden = computed(() => t('configEdit.hidden'))
const label = (o: (typeof options.value)[number]) => inputOptionLabel(o, hidden.value)

/** matches of the search; the chosen one always stays in the list */
const shown = computed(() => {
  const terms = searchTerms(query.value, locale.value)
  return options.value.filter((o) => o.id === props.modelValue || matchesTerms(terms, [o.name ?? '', o.path ?? ''], locale.value))
})

/** the chosen object is not offered (not readable or gone) */
const missingChosen = computed(() => props.modelValue != null && !options.value.some((o) => o.id === props.modelValue))
const unassigned = computed(() => props.modelValue == null)

const selectValue = computed({
  get: () => (props.modelValue == null ? '' : String(props.modelValue)),
  set: (v: string) => emit('update:modelValue', v === '' ? null : Number(v)),
})

const countText = computed(() => t('modules.count', { shown: shown.value.length, total: options.value.length }))
</script>

<template>
  <div class="edit-field ctr-input-field" role="group" :aria-labelledby="idBase + '-label'">
    <div class="ctr-input-head">
      <span :id="idBase + '-label'" class="reg-edit-label ctr-input-title">{{ title }}</span>
      <span v-if="unassigned" class="mod-badge is-dueSoon ctr-unassigned">{{ t('configEdit.unassigned') }}</span>
    </div>
    <p v-if="input.objectClass" class="edit-hint ctr-input-class">{{ t('configEdit.objectClass', { name: input.objectClass }) }}</p>
    <div class="ctr-contractor-pick">
      <label :for="idBase + '-search'" class="visually-hidden">{{ t('configEdit.inputSearch') }}</label>
      <input
        :id="idBase + '-search'"
        v-model="query"
        type="search"
        autocomplete="off"
        :placeholder="t('configEdit.inputSearch')"
        :disabled="disabled || !options.length"
        :aria-describedby="idBase + '-count'"
      />
      <label :for="idBase" class="visually-hidden">{{ t('configEdit.inputLabel', { name: title }) }}</label>
      <select :id="idBase" v-model="selectValue" :disabled="disabled">
        <option value="">{{ t('configEdit.inputNone') }}</option>
        <option v-if="missingChosen" :value="String(modelValue)">{{ hidden }}</option>
        <option v-for="o in shown" :key="o.id" :value="String(o.id)">{{ label(o) }}</option>
      </select>
    </div>
    <p :id="idBase + '-count'" class="edit-hint">{{ countText }}</p>
  </div>
</template>
