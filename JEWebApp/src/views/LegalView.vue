<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadLegal } from '../api/iso'
import IsoList from '../components/iso/IsoList.vue'
import LegalDetails from '../components/iso/LegalDetails.vue'
import { ALL } from '../iso/filter'
import { DEFAULT_LEGAL_COLUMNS, DEFAULT_LEGAL_FILTER, filterLegal, LEGAL_COLUMNS, LEGAL_DATE_FIELDS, legalOptions, legalRows, type LegalFilter, type LegalRow } from '../iso/legal'
import { useLoader } from '../iso/useLoader'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/iso.css'

const { t, locale } = useI18n()
const auth = useAuthStore()
const { data, state, reload } = useLoader(() => loadLegal(auth.http))

const filter = reactive<LegalFilter>({ ...DEFAULT_LEGAL_FILTER })
const plans = computed(() => data.value?.plans ?? [])
const all = computed(() => legalRows(data.value))
const rows = computed(() => filterLegal(all.value, filter, locale.value))
const options = computed(() => legalOptions(plans.value, filter.plan, locale.value))
const dateFields = computed(() => LEGAL_DATE_FIELDS.map((value) => ({ value, label: t('iso.legal.col.' + value) })))
const relevanceOptions = ['all', 'relevant', 'notRelevant'] as const

watch(plans, (list) => {
  if (list.length === 1) filter.plan = list[0].id
})
watch(
  () => filter.plan,
  () => {
    filter.category = ALL
    filter.scope = ALL
  },
)

const rowTitle = (r: LegalRow) => [r.item.nr != null ? String(r.item.nr) : '', r.item.title].filter(Boolean).join(' – ')
const rowClass = (r: LegalRow) => (r.item.relevant === false ? 'is-done' : undefined)
</script>

<template>
  <section class="iso" aria-labelledby="iso-title">
    <div class="mod-head">
      <h1 id="iso-title">{{ t('modules.legal') }}</h1>
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
      module="legal"
      :plans="plans"
      :rows="rows"
      :total="all.length"
      :columns="LEGAL_COLUMNS"
      :default-columns="DEFAULT_LEGAL_COLUMNS"
      :date-fields="dateFields"
      :row-title="rowTitle"
      :row-class="rowClass"
      :empty-text="t('iso.legal.empty')"
    >
      <template #filters>
        <div v-if="options.categories.length" class="control">
          <label for="legal-category">{{ t('iso.legal.col.category') }}</label>
          <select id="legal-category" v-model="filter.category">
            <option :value="ALL">{{ t('iso.filter.all') }}</option>
            <option v-for="c in options.categories" :key="c" :value="c">{{ c }}</option>
          </select>
        </div>
        <div v-if="options.scopes.length" class="control">
          <label for="legal-scope">{{ t('iso.legal.col.scope') }}</label>
          <select id="legal-scope" v-model="filter.scope">
            <option :value="ALL">{{ t('iso.filter.all') }}</option>
            <option v-for="s in options.scopes" :key="s" :value="s">{{ s }}</option>
          </select>
        </div>
        <div class="control">
          <span class="seg-label" aria-hidden="true">{{ t('iso.legal.col.relevant') }}</span>
          <div class="mod-seg" role="radiogroup" :aria-label="t('iso.legal.col.relevant')">
            <label v-for="r in relevanceOptions" :key="r">
              <input v-model="filter.relevance" type="radio" name="legal-relevance" :value="r" />
              {{ t('iso.legal.filter.' + r) }}
            </label>
          </div>
        </div>
      </template>
      <template #detail="{ row }">
        <LegalDetails :row="row" />
      </template>
    </IsoList>
  </section>
</template>
