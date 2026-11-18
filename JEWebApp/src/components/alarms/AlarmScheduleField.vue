<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { WEEKDAYS } from '../../api/alarmSettings'
import type { ScheduleForm } from '../../alarms/settings'

/*
 * Silent time ("Ruhezeit") or standby time ("Bereitschaft") of an alarm rule (E7.2): off, or weekdays as chips plus
 * one time range "von"–"bis" (also across midnight). A stored schedule the web cannot show that way is shown read-only
 * and kept unchanged.
 */

const props = defineProps<{
  modelValue: ScheduleForm
  label: string
  hint: string
  idBase: string
  disabled?: boolean
  error?: string | null
}>()
const emit = defineEmits<{ 'update:modelValue': [value: ScheduleForm] }>()
const { t } = useI18n()

const on = computed(() => props.modelValue.mode === 'simple')
const id = (suffix: string) => `${props.idBase}-${suffix}`
const errId = computed(() => id('err'))

function update(patch: Partial<ScheduleForm>) {
  emit('update:modelValue', { ...props.modelValue, ...patch })
}

function toggle(e: Event) {
  const checked = (e.target as HTMLInputElement).checked
  update(checked ? { mode: 'simple', days: props.modelValue.days.length ? props.modelValue.days : ['MON', 'TUE', 'WED', 'THU', 'FRI'], from: props.modelValue.from || '22:00', to: props.modelValue.to || '06:00' } : { mode: 'none' })
}

function toggleDay(day: string) {
  const days = props.modelValue.days.includes(day) ? props.modelValue.days.filter((d) => d !== day) : [...props.modelValue.days, day]
  update({ days: WEEKDAYS.filter((d) => days.includes(d)) })
}
</script>

<template>
  <div class="edit-field alm-schedule">
    <template v-if="modelValue.mode === 'fixed'">
      <span class="reg-edit-label">{{ label }}</span>
      <p class="alm-fixed">
        {{ t('alarmSettings.schedule.fixed') }}
        <code v-if="modelValue.text">{{ modelValue.text }}</code>
      </p>
    </template>
    <template v-else>
      <label class="iso-edit-check">
        <input type="checkbox" :checked="on" :disabled="disabled" :aria-describedby="id('hint')" @change="toggle" />
        <span>{{ label }}</span>
      </label>
      <p :id="id('hint')" class="edit-hint">{{ hint }}</p>
      <template v-if="on">
        <div class="alm-days" role="group" :aria-label="t('alarmSettings.schedule.days', { label })">
          <button
            v-for="d in WEEKDAYS"
            :key="d"
            type="button"
            class="edit-chip alm-day"
            :aria-pressed="modelValue.days.includes(d)"
            :title="t('alarmSettings.day.' + d)"
            :disabled="disabled"
            @click="toggleDay(d)"
          >
            {{ t('alarmSettings.dayShort.' + d) }}
          </button>
        </div>
        <div class="alm-times">
          <div>
            <label :for="id('from')">{{ t('alarmSettings.schedule.from') }}</label>
            <input
              :id="id('from')"
              type="time"
              :value="modelValue.from"
              :disabled="disabled"
              :aria-invalid="error ? 'true' : undefined"
              :aria-describedby="error ? errId : undefined"
              @input="update({ from: ($event.target as HTMLInputElement).value })"
            />
          </div>
          <div>
            <label :for="id('to')">{{ t('alarmSettings.schedule.to') }}</label>
            <input
              :id="id('to')"
              type="time"
              :value="modelValue.to"
              :disabled="disabled"
              :aria-invalid="error ? 'true' : undefined"
              :aria-describedby="error ? errId : undefined"
              @input="update({ to: ($event.target as HTMLInputElement).value })"
            />
          </div>
        </div>
        <p class="edit-hint">{{ t('alarmSettings.schedule.midnight') }}</p>
      </template>
    </template>
    <p v-if="error" :id="errId" class="iso-field-error">{{ error }}</p>
  </div>
</template>
