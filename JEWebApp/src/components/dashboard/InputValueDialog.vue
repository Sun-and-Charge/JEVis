<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WidgetConfig, WidgetData } from '../../api/dashboard'
import { writeWidgetValue } from '../../api/dashboardEdit'
import { HttpError } from '../../api/http'
import { formatNumber } from '../../analysis/format'
import { browserTimeZone } from '../../analysis/settings'
import { fromDateTimeInput, isoWithOffset, parseUserNumber, toDateTimeInput, useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { useAuthStore } from '../../stores/auth'
import EditDialog from '../edit/EditDialog.vue'

/*
 * Value Editor widget (E4): the manual entry of the Control Center (value and time, default now) through POST
 * /web/dashboards/{id}/widgets/{uuid}/value. Plausibility warnings of the server (409) can be overridden with
 * "Trotzdem speichern" (`confirm`).
 */

const props = defineProps<{ dashboardId: number; widget: WidgetConfig; current: number | null; unit: string | null }>()
const emit = defineEmits<{ close: []; saved: [data: WidgetData] }>()
const { t, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()

const uid = `ive-${Math.random().toString(36).slice(2, 9)}`
const value = ref(props.current != null ? formatNumber(props.current, locale.value, { min: 0, max: 6 }) : '')
const when = ref(toDateTimeInput(Date.now()))
const problem = ref<string | null>(null)
const error = ref<string | null>(null)
const warnings = ref<string[]>([])

function warningsOf(e: unknown): string[] {
  if (!(e instanceof HttpError) || e.status !== 409) return []
  const body = e.json<{ warnings?: unknown }>()
  return Array.isArray(body?.warnings) ? body!.warnings.filter((w): w is string => typeof w === 'string') : []
}

async function submit(confirm = false) {
  problem.value = null
  error.value = null
  const n = parseUserNumber(value.value, locale.value)
  if (n === null) {
    problem.value = t('dashboard.input.number')
    return
  }
  const ms = fromDateTimeInput(when.value)
  await run(async () => {
    try {
      const answer = await writeWidgetValue(
        auth.http,
        props.dashboardId,
        String(props.widget.uuid),
        { value: n, ...(props.widget.rows[0]?.key ? { rowKey: props.widget.rows[0].key } : {}), ...(ms != null ? { timestamp: isoWithOffset(ms) } : {}), confirm, timeZone: browserTimeZone() },
        locale.value,
      )
      emit('saved', answer)
    } catch (e) {
      const list = warningsOf(e)
      if (list.length) {
        warnings.value = list
        return
      }
      warnings.value = []
      error.value = writeErrorKind(e) === 'conflict' ? t('edit.error.conflict') : writeErrorText(e, t)
    }
  })
}
</script>

<template>
  <Teleport to="body">
    <EditDialog :title="t('dashboard.input.title', { name: widget.title || widget.type })" :busy="busy" @close="emit('close')">
      <form :id="uid + '-form'" novalidate @submit.prevent="submit(false)">
        <div class="edit-field">
          <label :for="uid + '-value'">{{ t('dashboard.input.value') }}{{ unit ? ` (${unit})` : '' }}</label>
          <input
            :id="uid + '-value'"
            v-model="value"
            type="text"
            inputmode="decimal"
            autocomplete="off"
            autofocus
            :aria-invalid="problem ? 'true' : undefined"
            :aria-describedby="problem ? uid + '-err' : undefined"
            @input="warnings = []"
          />
          <p v-if="problem" :id="uid + '-err'" class="iso-field-error">{{ problem }}</p>
        </div>
        <div class="edit-field">
          <label :for="uid + '-when'">{{ t('dashboard.input.time') }}</label>
          <input :id="uid + '-when'" v-model="when" type="datetime-local" @input="warnings = []" />
        </div>
        <div v-if="warnings.length" class="alert alert-error ive-warnings" role="alert">
          <ul>
            <li v-for="(w, i) in warnings" :key="i">{{ w }}</li>
          </ul>
        </div>
        <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
      </form>
      <template #footer>
        <button type="button" class="edit-btn" :disabled="busy" @click="emit('close')">{{ t('edit.cancel') }}</button>
        <button v-if="warnings.length" type="button" class="btn btn-primary ive-confirm" :disabled="busy" @click="submit(true)">{{ t('dashboard.input.confirm') }}</button>
        <button v-else type="submit" class="btn btn-primary edit-submit ive-submit" :form="uid + '-form'" :disabled="busy">{{ busy ? t('edit.saving') : t('dashboard.input.save') }}</button>
      </template>
    </EditDialog>
  </Teleport>
</template>
