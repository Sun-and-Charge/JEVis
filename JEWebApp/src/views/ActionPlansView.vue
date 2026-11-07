<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadActionPlans } from '../api/iso'
import ActionDetails from '../components/iso/ActionDetails.vue'
import ActionStats from '../components/iso/ActionStats.vue'
import IsoList from '../components/iso/IsoList.vue'
import { ACTION_COLUMNS, ACTION_DATE_FIELDS, actionOptions, actionRows, actionStatistics, DEFAULT_ACTION_COLUMNS, DEFAULT_ACTION_FILTER, filterActions, type ActionFilter, type ActionRow } from '../iso/actions'
import { ALL } from '../iso/filter'
import { useLoader } from '../iso/useLoader'
import { formatLocalDate } from '../modules/dates'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/iso.css'

const { t, locale } = useI18n()
const auth = useAuthStore()
const { data, state, reload } = useLoader(() => loadActionPlans(auth.http))

const filter = reactive<ActionFilter>({ ...DEFAULT_ACTION_FILTER })
const plans = computed(() => data.value?.plans ?? [])
const all = computed(() => actionRows(data.value))
const rows = computed(() => filterActions(all.value, filter, locale.value))
const options = computed(() => actionOptions(plans.value, filter.plan, locale.value))
const stats = computed(() => actionStatistics(rows.value, data.value?.today ?? new Date().toISOString().slice(0, 10), filter.from || null, options.value.statuses))
const dateFields = computed(() => ACTION_DATE_FIELDS.map((value) => ({ value, label: t('iso.actions.col.' + value) })))

/* a single plan is selected right away; tag filters of another plan would hide everything */
watch(plans, (list) => {
  if (list.length === 1) filter.plan = list[0].id
})
watch(
  () => filter.plan,
  () => {
    filter.status = ALL
    filter.medium = ALL
    filter.field = ALL
    filter.seu = ALL
  },
)

const rowTitle = (r: ActionRow) => [r.item.number, r.item.title].filter(Boolean).join(' – ')
</script>

<template>
  <section class="iso" aria-labelledby="iso-title">
    <div class="mod-head">
      <h1 id="iso-title">{{ t('modules.actionPlans') }}</h1>
      <span v-if="data?.today" class="muted">{{ t('appointments.today', { date: formatLocalDate(data.today, locale) }) }}</span>
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
      module="actions"
      :plans="plans"
      :rows="rows"
      :total="all.length"
      :columns="ACTION_COLUMNS"
      :default-columns="DEFAULT_ACTION_COLUMNS"
      :date-fields="dateFields"
      :row-title="rowTitle"
      :empty-text="t('iso.actions.empty')"
    >
      <template #filters>
        <div class="control">
          <label for="actions-status">{{ t('iso.actions.col.status') }}</label>
          <select id="actions-status" v-model="filter.status">
            <option :value="ALL">{{ t('iso.filter.all') }}</option>
            <option v-for="s in options.statuses" :key="s" :value="s">{{ s }}</option>
          </select>
        </div>
        <div v-if="options.media.length" class="control">
          <label for="actions-medium">{{ t('iso.actions.col.medium') }}</label>
          <select id="actions-medium" v-model="filter.medium">
            <option :value="ALL">{{ t('iso.filter.all') }}</option>
            <option v-for="m in options.media" :key="m.id" :value="m.id">{{ m.name }}</option>
          </select>
        </div>
        <div v-if="options.fields.length" class="control">
          <label for="actions-field">{{ t('iso.actions.col.field') }}</label>
          <select id="actions-field" v-model="filter.field">
            <option :value="ALL">{{ t('iso.filter.all') }}</option>
            <option v-for="f in options.fields" :key="f" :value="f">{{ f }}</option>
          </select>
        </div>
        <div v-if="options.seus.length" class="control">
          <label for="actions-seu">{{ t('iso.actions.col.seu') }}</label>
          <select id="actions-seu" v-model="filter.seu">
            <option :value="ALL">{{ t('iso.filter.all') }}</option>
            <option v-for="s in options.seus" :key="s" :value="s">{{ s }}</option>
          </select>
        </div>
      </template>
      <template #stats>
        <ActionStats :stats="stats" />
      </template>
      <template #detail="{ row }">
        <ActionDetails :row="row" />
      </template>
    </IsoList>
  </section>
</template>
