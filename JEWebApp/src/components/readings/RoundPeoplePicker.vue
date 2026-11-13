<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RoundOptionPerson, RoundPerson } from '../../api/readingRounds'
import { searchPeople, usersWithoutEmail } from '../../readings/rounds'

/*
 * Searchable multi-select of users and groups (readers, supervisor of a reading round, E8.2): the chosen people as
 * chips (groups marked, users without e-mail address marked and listed in a warning), a search field and the matching
 * people as buttons below it (shown while the picker has the focus or a search text). People the user may not read
 * keep their id ("#41").
 */

const props = defineProps<{
  people: readonly RoundOptionPerson[]
  /** names of the round as loaded (people missing from the options) */
  known?: readonly RoundPerson[]
  modelValue: readonly number[]
  inputId: string
  label: string
  hint?: string
  error?: string | null
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [ids: number[]] }>()
const { t, locale } = useI18n()

const text = ref('')
const open = ref(false)
const root = ref<HTMLElement | null>(null)
const status = ref('')

const byId = computed(() => new Map(props.people.map((p) => [p.id, p])))
const knownById = computed(() => new Map((props.known ?? []).map((p) => [p.id, p])))
const chosen = computed(() =>
  props.modelValue.map((id) => {
    const p = byId.value.get(id)
    const k = knownById.value.get(id)
    return {
      id,
      name: p?.name ?? k?.name ?? `#${id}`,
      group: (p?.kind ?? k?.kind) === 'GROUP',
      noEmail: !!p && p.kind === 'USER' && !p.email,
    }
  }),
)
const matches = computed(() => searchPeople(props.people, props.modelValue, text.value, locale.value))
const noEmail = computed(() => usersWithoutEmail(props.people, props.modelValue))
const listShown = computed(() => !props.disabled && (open.value || text.value.trim() !== ''))
const listId = computed(() => props.inputId + '-matches')
const hintId = computed(() => props.inputId + '-hint')
const errId = computed(() => props.inputId + '-err')
const warnId = computed(() => props.inputId + '-warn')
const describedBy = computed(() => [props.hint ? hintId.value : '', props.error ? errId.value : '', noEmail.value.length ? warnId.value : ''].filter(Boolean).join(' ') || undefined)

function add(p: RoundOptionPerson) {
  emit('update:modelValue', [...props.modelValue, p.id])
  status.value = t('readings.rounds.people.added', { name: p.name })
  text.value = ''
  void nextTick(() => document.getElementById(props.inputId)?.focus())
}

function remove(id: number, name: string) {
  emit(
    'update:modelValue',
    props.modelValue.filter((x) => x !== id),
  )
  status.value = t('readings.rounds.people.removed', { name })
  void nextTick(() => document.getElementById(props.inputId)?.focus())
}

function onFocusOut(e: FocusEvent) {
  const next = e.relatedTarget as Node | null
  if (!next || !root.value?.contains(next)) open.value = false
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    /* Enter adds the first match instead of submitting the form */
    e.preventDefault()
    if (matches.value[0] && text.value.trim()) add(matches.value[0])
  } else if (e.key === 'Escape' && (text.value || open.value)) {
    e.stopPropagation()
    text.value = ''
    open.value = false
  } else if (e.key === 'ArrowDown' && listShown.value) {
    e.preventDefault()
    root.value?.querySelector<HTMLElement>('.rd-people-match')?.focus()
  }
}
</script>

<template>
  <div ref="root" class="edit-field rd-people" @focusin="open = true" @focusout="onFocusOut">
    <label :for="inputId">{{ label }}</label>
    <ul v-if="chosen.length" class="rd-people-chosen" :aria-label="t('readings.rounds.people.chosen', { label })">
      <li v-for="p in chosen" :key="p.id" class="rd-person" :class="{ 'is-group': p.group, 'is-noEmail': p.noEmail }">
        <span v-if="p.group" class="rd-person-kind">{{ t('readings.rounds.people.group') }}</span>
        <span class="rd-person-name">{{ p.name }}</span>
        <span v-if="p.noEmail" class="rd-person-warn" :title="t('readings.rounds.people.noEmailOne')" aria-hidden="true">!</span>
        <button
          type="button"
          class="rd-person-remove"
          :disabled="disabled"
          :aria-label="t('readings.rounds.people.remove', { name: p.name })"
          :title="t('readings.rounds.people.remove', { name: p.name })"
          @click="remove(p.id, p.name)"
        >
          ×
        </button>
      </li>
    </ul>
    <input
      :id="inputId"
      v-model="text"
      type="search"
      autocomplete="off"
      :placeholder="t('readings.rounds.people.search')"
      :disabled="disabled"
      :aria-controls="listShown ? listId : undefined"
      :aria-expanded="listShown ? 'true' : 'false'"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      @keydown="onKey"
    />
    <ul v-if="listShown" :id="listId" class="rd-people-matches" :aria-label="t('readings.rounds.people.matches')">
      <li v-for="p in matches" :key="p.id">
        <button type="button" class="rd-people-match" @click="add(p)">
          <span aria-hidden="true">+</span>
          <span class="rd-person-name">{{ p.name }}</span>
          <span v-if="p.kind === 'GROUP'" class="rd-person-kind">{{ t('readings.rounds.people.group') }}</span>
          <span v-else-if="!p.email" class="rd-person-mail">{{ t('readings.rounds.people.noEmailShort') }}</span>
        </button>
      </li>
      <li v-if="!matches.length" class="muted rd-people-none">{{ t('readings.rounds.people.none') }}</li>
    </ul>
    <p v-if="hint" :id="hintId" class="edit-hint">{{ hint }}</p>
    <p v-if="noEmail.length" :id="warnId" class="rd-people-warning">
      {{ t('readings.rounds.people.noEmail', { names: noEmail.map((p) => p.name).join(', ') }) }}
    </p>
    <p v-if="error" :id="errId" class="iso-field-error">{{ error }}</p>
    <p class="visually-hidden" role="status">{{ status }}</p>
  </div>
</template>
