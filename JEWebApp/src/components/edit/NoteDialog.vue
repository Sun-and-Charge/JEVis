<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTime } from '../../analysis/format'
import { browserTimeZone } from '../../analysis/settings'
import { HttpError } from '../../api/http'
import { createNote, deleteNote, updateNote, type Note } from '../../api/notes'
import { fromDateTimeInput, isoWithOffset, toDateTimeInput, toMinute, useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { chosenTags, customTags, editableTags, tagsToSend, toggleTag, type EditableNote, type TagName } from '../../notes/notes'
import { useAuthStore } from '../../stores/auth'
import EditDialog from './EditDialog.vue'

/*
 * Note dialog (E1): new note on a data row (time to the minute, text, tags 1–13) or change / delete of an existing
 * note (time fixed). 409: on create a note exists at that time; on change / delete the note was changed meanwhile –
 * the current note is taken over from the answer and the caller reloads its list.
 */

const props = defineProps<{
  /** create: data row the note is stored for; edit: unused */
  rowId?: number | null
  rowName?: string
  /** create without a fixed row: the rows to choose from */
  rowChoices?: { id: number; name: string }[]
  /** the note to change; without it a new note is created */
  note?: EditableNote | null
  /** time of a new note (epoch ms, rounded to the minute) */
  timestamp?: number | null
  tagName: TagName
}>()

const emit = defineEmits<{
  close: []
  /** saved: the note of the answer */
  saved: [note: Note | null]
  deleted: [id: string]
  /** 409 / 404: the caller should reload */
  stale: []
}>()

const { t, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()

const version = ref<string | null>(props.note?.version ?? null)
const editing = computed(() => !!props.note)
/* an existing note: change needs canEdit, delete canDelete, both its version (else the server answers 400) */
const mayEdit = computed(() => !props.note || (!!props.note.canEdit && !!version.value))
const mayDelete = computed(() => !!props.note?.canDelete && !!version.value)
const original = ref<string[]>(props.note ? [...props.note.tags] : [])
const text = ref(props.note?.text ?? '')
const tags = ref<string[]>(chosenTags(props.note?.tags))
const when = ref(toDateTimeInput(toMinute(props.timestamp ?? Date.now())))
const chosenRow = ref<number | null>(props.rowId ?? props.rowChoices?.[0]?.id ?? null)
const choose = computed(() => !props.note && !props.rowId && !!props.rowChoices?.length)
const error = ref<string | null>(null)
const askDelete = ref(false)

const chips = computed(() => editableTags(props.tagName))
const custom = computed(() => customTags(original.value))
const title = computed(() => (!editing.value ? t('noteEdit.newTitle') : mayEdit.value ? t('noteEdit.editTitle') : t('noteEdit.deleteTitle')))
const fixedTime = computed(() => (props.note ? formatDateTime(props.note.timestamp, locale.value) : ''))
const uid = `note-dlg-${Math.random().toString(36).slice(2, 9)}`

function toggle(id: string) {
  tags.value = toggleTag(tags.value, id)
}

function takeOver(current: Note | null) {
  if (!current) return
  text.value = current.text ?? ''
  tags.value = chosenTags(current.tags)
  original.value = [...(current.tags ?? [])]
  version.value = current.version ?? null
}

async function save() {
  error.value = null
  if (!mayEdit.value) return
  const body = text.value.trim()
  if (!body) {
    error.value = t('noteEdit.textRequired')
    return
  }
  let ts: number | null = null
  if (!editing.value && chosenRow.value == null) {
    error.value = t('noteEdit.rowRequired')
    return
  }
  if (!editing.value) {
    ts = fromDateTimeInput(when.value)
    if (ts == null) {
      error.value = t('noteEdit.timeRequired')
      return
    }
  }
  await run(async () => {
    try {
      const saved = editing.value
        ? await updateNote(auth.http, props.note!.id, { text: body, tags: tagsToSend(original.value, tags.value), version: version.value! }, browserTimeZone())
        : await createNote(auth.http, chosenRow.value!, { timestamp: isoWithOffset(ts!), text: body, tags: chosenTags(tags.value) }, browserTimeZone())
      emit('saved', saved ?? null)
    } catch (e) {
      handle(e)
    }
  })
}

async function remove() {
  if (!props.note || !mayDelete.value) return
  error.value = null
  await run(async () => {
    try {
      await deleteNote(auth.http, props.note!.id, version.value!, browserTimeZone())
      emit('deleted', props.note!.id)
    } catch (e) {
      askDelete.value = false
      handle(e)
    }
  })
}

function handle(e: unknown) {
  const kind = writeErrorKind(e)
  if (kind === 'conflict') {
    if (editing.value) {
      takeOver(e instanceof HttpError ? e.json<Note>() : null)
      error.value = t('noteEdit.changedMeanwhile')
    } else {
      error.value = t('noteEdit.exists')
    }
    emit('stale')
    return
  }
  if (kind === 'notFound') emit('stale')
  error.value = writeErrorText(e, t)
}
</script>

<template>
  <EditDialog :title="title" :busy="busy" wide @close="emit('close')">
    <form :id="uid + '-form'" novalidate @submit.prevent="save">
      <dl class="edit-facts">
        <template v-if="rowName">
          <dt>{{ t('notes.col.row') }}</dt>
          <dd>{{ rowName }}</dd>
        </template>
        <template v-if="editing">
          <dt>{{ t('notes.col.date') }}</dt>
          <dd>{{ fixedTime }}</dd>
        </template>
      </dl>

      <div v-if="choose" class="edit-field">
        <label :for="uid + '-row'">{{ t('noteEdit.pickRow') }}</label>
        <select :id="uid + '-row'" v-model="chosenRow" class="note-row-select" :disabled="busy" :aria-describedby="uid + '-row-hint'">
          <option v-for="r in rowChoices" :key="r.id" :value="r.id">{{ r.name }}</option>
        </select>
        <p :id="uid + '-row-hint'" class="edit-hint">{{ t('noteEdit.pickRowHint') }}</p>
      </div>

      <div v-if="!editing" class="edit-field">
        <label :for="uid + '-time'">{{ t('noteEdit.time') }}</label>
        <input :id="uid + '-time'" v-model="when" type="datetime-local" step="60" required :disabled="busy" />
      </div>

      <div class="edit-field">
        <label :for="uid + '-text'">{{ t('noteEdit.text') }}</label>
        <textarea :id="uid + '-text'" v-model="text" required maxlength="10000" :disabled="busy" :readonly="!mayEdit" autofocus />
      </div>

      <fieldset class="edit-tags">
        <legend>{{ t('noteEdit.tags') }}</legend>
        <button
          v-for="chip in chips"
          :key="chip.id"
          type="button"
          class="edit-chip"
          :aria-pressed="tags.includes(chip.id)"
          :disabled="busy || !mayEdit"
          @click="toggle(chip.id)"
        >
          {{ chip.name }}
        </button>
      </fieldset>
      <p v-if="custom.length" class="muted edit-hint">{{ t('noteEdit.customTags', { tags: custom.join(', ') }) }}</p>

      <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
      <div v-if="askDelete" class="alert edit-error" role="alertdialog" :aria-label="t('noteEdit.deleteTitle')">
        <p class="edit-message">{{ t('noteEdit.deleteQuestion') }}</p>
        <div class="edit-actions">
          <button type="button" class="edit-btn" :disabled="busy" @click="askDelete = false">{{ t('edit.cancel') }}</button>
          <button type="button" class="btn btn-primary is-danger note-delete-confirm" :disabled="busy" @click="remove">{{ t('noteEdit.deleteConfirm') }}</button>
        </div>
      </div>
    </form>
    <template #footer>
      <button v-if="editing && mayDelete && !askDelete" type="button" class="edit-btn is-danger edit-left note-delete" :disabled="busy" @click="askDelete = true">
        {{ t('noteEdit.delete') }}
      </button>
      <button type="button" class="edit-btn" :disabled="busy" @click="emit('close')">{{ t('edit.cancel') }}</button>
      <button v-if="mayEdit" type="submit" class="btn btn-primary edit-submit" :form="uid + '-form'" :disabled="busy">{{ busy ? t('edit.saving') : t('edit.save') }}</button>
    </template>
  </EditDialog>
</template>
