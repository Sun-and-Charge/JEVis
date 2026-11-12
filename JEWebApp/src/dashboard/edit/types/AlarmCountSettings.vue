<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../../stores/auth'
import { searchAlarmObjects, type TreeHit } from '../typeApi'
import { objectId, useBlock, type TypeEditorProps } from './shared'
import './types.css'

/*
 * Alarm Count (widget/AlarmCountWidget.java, block `alarmCount.alarmObject`): all alarms, or only those of one
 * "Alarm Configuration" / "Alarm Directory" (the CC's checkbox and list; 0 = all). The web searches the objects by name
 * (GET /web/rows/tree?q=…, at least 2 characters) instead of listing all of them.
 */

const props = defineProps<TypeEditorProps>()
const { t, locale } = useI18n()
const tt = (k: string, p?: Record<string, unknown>) => t('dashboardEdit.types.' + k, p ?? {})
const uid = useId()
const http = useAuthStore().http
const b = useBlock(props, 'alarmCount')

const stored = computed(() => objectId(b.cfg.value.alarmObject))
const filter = ref(stored.value > 0)
watch(
  () => props.widget,
  () => {
    filter.value = stored.value > 0
    hits.value = []
    query.value = ''
    chosenName.value = ''
  },
)

const query = ref('')
const hits = ref<TreeHit[]>([])
const searching = ref(false)
const searched = ref(false)
const failed = ref(false)
const chosenName = ref('')

function setFilter(on: boolean) {
  filter.value = on
  /* OK of the CC dialog: the selected object only with the checkbox, else 0 */
  if (!on) b.set('alarmObject', 0)
}

async function search() {
  const q = query.value.trim()
  if (q.length < 2) return
  searching.value = true
  failed.value = false
  try {
    hits.value = await searchAlarmObjects(http, q, locale.value)
  } catch {
    hits.value = []
    failed.value = true
  } finally {
    searching.value = false
    searched.value = true
  }
}

function choose(hit: TreeHit) {
  b.set('alarmObject', hit.id)
  chosenName.value = (hit.path ? hit.path + ' / ' : '') + hit.name
}

const currentLabel = computed(() => {
  if (!stored.value) return tt('alarm.noneChosen')
  const hit = hits.value.find((h) => h.id === stored.value)
  if (hit) return (hit.path ? hit.path + ' / ' : '') + hit.name
  return chosenName.value || tt('alarm.objectId', { id: stored.value })
})
</script>

<template>
  <fieldset class="ae-section dt-settings">
    <legend>{{ tt('alarm.tab') }}</legend>
    <label class="ae-check">
      <input type="checkbox" :checked="filter" @change="setFilter(($event.target as HTMLInputElement).checked)" />
      <span>{{ tt('alarm.filter') }}</span>
    </label>
    <template v-if="filter">
      <p class="dt-current">
        <strong>{{ tt('alarm.current') }}:</strong> {{ currentLabel }}
      </p>
      <div class="edit-field">
        <label :for="uid + '-q'">{{ tt('alarm.search') }}</label>
        <div class="ae-inline">
          <input :id="uid + '-q'" v-model="query" type="search" autocomplete="off" :aria-describedby="uid + '-q-hint'" @keydown.enter.prevent="search" />
          <button type="button" class="edit-btn" :disabled="query.trim().length < 2 || searching" @click="search">{{ tt('alarm.searchButton') }}</button>
        </div>
        <p :id="uid + '-q-hint'" class="ae-hint">{{ tt('alarm.searchHint') }}</p>
      </div>
      <p v-if="failed" class="ae-error" role="alert">{{ tt('alarm.searchError') }}</p>
      <p v-else-if="searched && !searching && !hits.length" class="ae-hint" role="status">{{ tt('alarm.noHits') }}</p>
      <ul v-if="hits.length" class="dt-hits" :aria-label="tt('alarm.hits')">
        <li v-for="h in hits" :key="h.id">
          <button type="button" :aria-pressed="h.id === stored ? 'true' : 'false'" @click="choose(h)">
            {{ h.name }} <span class="dt-path">{{ h.path ? h.path + ' · ' : '' }}{{ h.class === 'Alarm Directory' ? tt('alarm.directory') : tt('alarm.configuration') }}</span>
          </button>
        </li>
      </ul>
    </template>
  </fieldset>
</template>
