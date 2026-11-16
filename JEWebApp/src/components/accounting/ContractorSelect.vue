<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadContractors, type ContractorOption, type ContractorRef } from '../../api/contracts'
import { useAuthStore } from '../../stores/auth'
import { matchesTerms, searchTerms } from '../../modules/search'

/*
 * Contractor of a supplier, metering point operator or grid operator (E6.2): a search field over the readable
 * objects of the contractor class (GET /web/contracts/contractors) and a select of the matches; the chosen one can be
 * shown in the contractor tab. A contractor that is not readable stays selectable as "Kein Leserecht".
 */

const props = defineProps<{
  modelValue: number | null
  contractorClass: string
  /** the contractor as loaded (name / readable) */
  loaded: ContractorRef | null
  idBase: string
  disabled?: boolean
  /** the chosen contractor is in the contractor tab (link offered) */
  canShow?: (id: number) => boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [id: number | null]; show: [id: number] }>()
const { t, locale } = useI18n()
const auth = useAuthStore()

const options = shallowRef<ContractorOption[]>([])
const state = ref<'loading' | 'ready' | 'error'>('loading')
const query = ref('')

async function load() {
  state.value = 'loading'
  try {
    const answer = await loadContractors(auth.http, props.contractorClass)
    options.value = (Array.isArray(answer) ? answer : []).filter((o) => o && typeof o.id === 'number')
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}
onMounted(load)

const optionLabel = (o: ContractorOption) => (o.path ? `${o.name} – ${o.path}` : o.name)

/** matches of the search; the chosen one always stays in the list */
const shown = computed(() => {
  const terms = searchTerms(query.value, locale.value)
  return options.value.filter((o) => o.id === props.modelValue || matchesTerms(terms, [o.name, o.path ?? ''], locale.value))
})

/** the chosen contractor is not among the readable ones (not readable or in the recycle bin) */
const hiddenChosen = computed(() => props.modelValue != null && !options.value.some((o) => o.id === props.modelValue))
const hiddenLabel = computed(() => {
  const l = props.loaded
  if (l && l.id === props.modelValue && l.readable && l.name) return l.name
  return t('contracts.contractorHidden')
})

const selectValue = computed({
  get: () => (props.modelValue == null ? '' : String(props.modelValue)),
  set: (v: string) => emit('update:modelValue', v === '' ? null : Number(v)),
})

const showable = computed(() => props.modelValue != null && !hiddenChosen.value && (props.canShow?.(props.modelValue) ?? false))
const countText = computed(() => t('modules.count', { shown: shown.value.length, total: options.value.length }))
</script>

<template>
  <div class="edit-field ctr-contractor-field" role="group" :aria-labelledby="idBase + '-label'">
    <span :id="idBase + '-label'" class="reg-edit-label">{{ t('contracts.contractor') }}</span>
    <div v-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('contractEdit.contractor.error') }}
      <button type="button" class="btn-link retry" @click="load">{{ t('modules.retry') }}</button>
    </div>
    <div class="ctr-contractor-pick">
      <label :for="idBase + '-search'" class="visually-hidden">{{ t('contractEdit.contractor.search') }}</label>
      <input
        :id="idBase + '-search'"
        v-model="query"
        type="search"
        autocomplete="off"
        :placeholder="t('contractEdit.contractor.search')"
        :disabled="disabled || state !== 'ready'"
        :aria-describedby="idBase + '-count'"
      />
      <label :for="idBase" class="visually-hidden">{{ t('contracts.contractor') }}</label>
      <select :id="idBase" v-model="selectValue" :disabled="disabled || state === 'loading'">
        <option value="">{{ t('contractEdit.contractor.none') }}</option>
        <option v-if="hiddenChosen" :value="String(modelValue)">{{ hiddenLabel }}</option>
        <option v-for="o in shown" :key="o.id" :value="String(o.id)">{{ optionLabel(o) }}</option>
      </select>
    </div>
    <p :id="idBase + '-count'" class="edit-hint">
      <span v-if="state === 'loading'" role="status">{{ t('contractEdit.contractor.loading') }}</span>
      <span v-else-if="state === 'ready'">{{ countText }}</span>
      <button v-if="showable" type="button" class="btn-link ctr-contractor-show" @click="emit('show', modelValue!)">
        {{ t('contractEdit.contractor.show') }}
      </button>
    </p>
  </div>
</template>
