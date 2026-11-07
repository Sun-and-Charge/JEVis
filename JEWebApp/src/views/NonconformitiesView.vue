<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadNonconformities } from '../api/iso'
import IsoList from '../components/iso/IsoList.vue'
import NonconformityDetails from '../components/iso/NonconformityDetails.vue'
import { ALL } from '../iso/filter'
import {
  DEFAULT_NONCONFORMITY_COLUMNS,
  DEFAULT_NONCONFORMITY_FILTER,
  filterNonconformities,
  NONCONFORMITY_COLUMNS,
  NONCONFORMITY_DATE_FIELDS,
  nonconformityOptions,
  nonconformityRows,
  nonconformityState,
  type NonconformityFilter,
  type NonconformityRow,
} from '../iso/nonconformities'
import { useLoader } from '../iso/useLoader'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/iso.css'

const { t, locale } = useI18n()
const auth = useAuthStore()
const { data, state, reload } = useLoader(() => loadNonconformities(auth.http))

const filter = reactive<NonconformityFilter>({ ...DEFAULT_NONCONFORMITY_FILTER })
const plans = computed(() => data.value?.plans ?? [])
const all = computed(() => nonconformityRows(data.value))
const rows = computed(() => filterNonconformities(all.value, filter, locale.value))
const options = computed(() => nonconformityOptions(plans.value, filter.plan, locale.value))
const dateFields = computed(() => NONCONFORMITY_DATE_FIELDS.map((value) => ({ value, label: t('iso.nc.col.' + value) })))
const statusOptions = ['all', 'open', 'closed'] as const

watch(plans, (list) => {
  if (list.length === 1) filter.plan = list[0].id
})
watch(
  () => filter.plan,
  () => {
    filter.medium = ALL
    filter.field = ALL
    filter.seu = ALL
  },
)

const rowTitle = (r: NonconformityRow) => [r.item.number, r.item.title].filter(Boolean).join(' – ')
const rowClass = (r: NonconformityRow) => {
  const s = nonconformityState(r.item)
  return s === 'overdue' ? 'is-overdue' : s === 'closed' ? 'is-done' : undefined
}
</script>

<template>
  <section class="iso" aria-labelledby="iso-title">
    <div class="mod-head">
      <h1 id="iso-title">{{ t('modules.nonconformities') }}</h1>
    </div>
    <p class="muted read-only">{{ t('iso.readOnly') }}</p>

    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('iso.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <p v-if="data && !plans.length" class="muted">{{ t('iso.noPlans') }}</p>
    <IsoList
      v-else-if="data"
      v-model:plan="filter.plan"
      v-model:text="filter.text"
      v-model:date-field="filter.dateField"
      v-model:from="filter.from"
      v-model:until="filter.until"
      module="nonconformities"
      :plans="plans"
      :rows="rows"
      :total="all.length"
      :columns="NONCONFORMITY_COLUMNS"
      :default-columns="DEFAULT_NONCONFORMITY_COLUMNS"
      :date-fields="dateFields"
      :row-title="rowTitle"
      :row-class="rowClass"
      :empty-text="t('iso.nc.empty')"
    >
      <template #filters>
        <div class="control">
          <span class="seg-label" aria-hidden="true">{{ t('modules.status') }}</span>
          <div class="mod-seg" role="radiogroup" :aria-label="t('modules.status')">
            <label v-for="s in statusOptions" :key="s">
              <input v-model="filter.status" type="radio" name="nc-status" :value="s" />
              {{ t('iso.nc.filter.' + s) }}
            </label>
          </div>
        </div>
        <div v-if="options.media.length" class="control">
          <label for="nc-medium">{{ t('iso.nc.col.medium') }}</label>
          <select id="nc-medium" v-model="filter.medium">
            <option :value="ALL">{{ t('iso.filter.all') }}</option>
            <option v-for="m in options.media" :key="m" :value="m">{{ m }}</option>
          </select>
        </div>
        <div v-if="options.fields.length" class="control">
          <label for="nc-field">{{ t('iso.nc.col.fields') }}</label>
          <select id="nc-field" v-model="filter.field">
            <option :value="ALL">{{ t('iso.filter.all') }}</option>
            <option v-for="f in options.fields" :key="f" :value="f">{{ f }}</option>
          </select>
        </div>
        <div v-if="options.seus.length" class="control">
          <label for="nc-seu">{{ t('iso.nc.col.seu') }}</label>
          <select id="nc-seu" v-model="filter.seu">
            <option :value="ALL">{{ t('iso.filter.all') }}</option>
            <option v-for="s in options.seus" :key="s" :value="s">{{ s }}</option>
          </select>
        </div>
      </template>
      <template #detail="{ row }">
        <NonconformityDetails :row="row" />
      </template>
    </IsoList>
  </section>
</template>
