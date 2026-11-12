<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTime } from '../analysis/format'
import { browserTimeZone } from '../analysis/settings'
import { loadNotes, noteTargetId, type Note } from '../api/notes'
import NoteDialog from '../components/edit/NoteDialog.vue'
import NoteDetails from '../components/notes/NoteDetails.vue'
import { saveBlob } from '../iso/download'
import { sortRows, type ColumnContext, type SortDirection } from '../iso/columns'
import { useLoader } from '../iso/useLoader'
import type { SiteFilter } from '../modules/search'
import { useNarrow } from '../modules/useNarrow'
import {
  DEFAULT_NOTE_SORT,
  checkNoteRange,
  defaultNoteFilter,
  defaultNoteRange,
  filterNotes,
  i18nTagName,
  noteTime,
  noteColumns,
  noteSites,
  notesCsv,
  notesCsvName,
  rowFullName,
  tagNamer,
  tagOptions,
  toggleTag,
  visibleTags,
  type NoteFilter,
} from '../notes/notes'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/iso.css'
import '../styles/notes.css'
import '../styles/edit.css'

/*
 * Notes (W4.4; writing E1) like the Control Center's Notes Plugin: notes of the data rows in a time frame, search
 * with the plugin's toggles, tag and site filter, sortable table (cards on phone width), CSV export and a detail
 * panel with the curve of the data row around the note.
 */

const { t, te, locale } = useI18n()
const auth = useAuthStore()
const narrow = useNarrow()

const range = reactive(defaultNoteRange())
const rangeCheck = computed(() => checkNoteRange(range.from, range.to))

/* the request of the last load (shown time frame, CSV name) */
let requested = { ...range }
const { data, state, reload } = useLoader(() => {
  requested = { ...range }
  return loadNotes(auth.http, { from: range.from, to: range.to, timeZone: browserTimeZone(), lang: locale.value })
})

/* a valid new time frame or language loads again; an invalid one keeps the shown notes */
watch([() => range.from, () => range.to, locale], () => {
  if (rangeCheck.value === 'ok') reload()
})

function resetRange() {
  Object.assign(range, defaultNoteRange())
}

const filter = reactive<NoteFilter>(defaultNoteFilter())
const sortId = ref(DEFAULT_NOTE_SORT.id)
const sortDir = ref<SortDirection>(DEFAULT_NOTE_SORT.dir)
const selected = ref<string | null>(null)

const tagName = computed(() => tagNamer(data.value?.tags, i18nTagName(t, te)))
const noAccess = computed(() => t('notes.noAccess'))
const columns = computed(() =>
  noteColumns({ noAccess: noAccess.value, tagName: tagName.value, formatTime: (ms) => formatDateTime(ms, locale.value) }),
)
const ctx = computed<ColumnContext>(() => ({ locale: locale.value, t: t as ColumnContext['t'] }))

const all = computed<Note[]>(() => data.value?.notes ?? [])
const rows = computed(() => filterNotes(all.value, filter, tagName.value, locale.value))
const sorted = computed(() =>
  sortRows(
    rows.value,
    columns.value.find((c) => c.id === sortId.value),
    sortDir.value,
    ctx.value,
  ),
)
const tags = computed(() => tagOptions(data.value?.tags ?? [], all.value, filter.tags, tagName.value))
const sites = computed(() => noteSites(all.value, locale.value))
const hasNotesWithoutSite = computed(() => all.value.some((n) => !n.site))
const noField = computed(() => !filter.inText && !filter.inUser && !filter.inRow && !filter.inTag)

/* tags or sites that are gone after a new load leave the filter */
watch(data, () => {
  const known = new Set(tags.value.map((x) => x.id))
  filter.tags = filter.tags.filter((id) => known.has(id))
  if (typeof filter.site === 'number' && !sites.value.some((s) => s.id === filter.site)) filter.site = 'all'
  if (selected.value && !all.value.some((n) => n.id === selected.value)) selected.value = null
})

const siteValue = computed({
  get: () => String(filter.site),
  set: (v: string) => {
    filter.site = (v === 'all' || v === 'none' ? v : Number(v)) as SiteFilter
  },
})

function sortBy(id: string) {
  if (sortId.value === id) sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  else {
    sortId.value = id
    sortDir.value = id === 'date' ? 'desc' : 'asc'
  }
}

function ariaSort(id: string): 'ascending' | 'descending' | 'none' {
  if (sortId.value !== id) return 'none'
  return sortDir.value === 'asc' ? 'ascending' : 'descending'
}

function toggle(id: string) {
  selected.value = selected.value === id ? null : id
}

function toggleTagFilter(id: string) {
  filter.tags = toggleTag(filter.tags, id)
}

function exportCsv() {
  const csv = notesCsv(
    sorted.value,
    {
      date: t('notes.col.date'),
      site: t('notes.col.site'),
      path: t('notes.col.path'),
      row: t('notes.col.row'),
      text: t('notes.col.text'),
      tags: t('notes.col.tags'),
      user: t('notes.col.user'),
    },
    { noAccess: noAccess.value, tagName: tagName.value },
    locale.value,
  )
  saveBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), notesCsvName(t('notes.title'), requested.from, requested.to))
}

const cell = (id: string, n: Note) => columns.value.find((c) => c.id === id)?.text(n, ctx.value) ?? ''
const rowText = (n: Note) => rowFullName(n, noAccess.value)
const showDetails = (n: Note) => t('modules.showDetails', { title: cell('date', n) })
const domId = (n: Note, suffix: string) => `note-${n.id}-${suffix}`
const tableColumns = ['date', 'site', 'row', 'text', 'tags', 'user'] as const

/* ---- E1: new note, change / delete ---- */

type NoteDialogState = { kind: 'new'; rowId: number | null; rowName: string } | { kind: 'edit'; note: Note }
const dialog = ref<NoteDialogState | null>(null)
const message = ref<string | null>(null)

/** Data rows of the loaded notes the user may add notes to (one per target row). */
const rowChoices = computed(() => {
  const seen = new Map<number, string>()
  for (const n of all.value) {
    if (!n.row?.canAddNote || !n.row.readable) continue
    const id = noteTargetId(n.row)
    if (id != null && !seen.has(id)) seen.set(id, rowFullName(n, noAccess.value))
  }
  return [...seen.entries()].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name, locale.value))
})
const canWriteAny = computed(() => rowChoices.value.length > 0 || all.value.some((n) => n.canEdit || n.canDelete))

function newNote() {
  message.value = null
  dialog.value = { kind: 'new', rowId: null, rowName: '' }
}

function addToRow(n: Note) {
  message.value = null
  dialog.value = { kind: 'new', rowId: noteTargetId(n.row), rowName: rowFullName(n, noAccess.value) }
}

function editNote(n: Note) {
  message.value = null
  dialog.value = { kind: 'edit', note: n }
}

const editable = (n: Note) => ({
  id: n.id,
  timestamp: noteTime(n) ?? Date.parse(n.timestamp),
  text: n.text ?? '',
  tags: n.tags ?? [],
  version: n.version ?? null,
  canEdit: !!n.canEdit,
  canDelete: !!n.canDelete,
})

function done(text: string) {
  dialog.value = null
  message.value = text
  void reload()
}
</script>

<template>
  <section class="iso notes" aria-labelledby="notes-title">
    <div class="mod-head">
      <h1 id="notes-title">{{ t('notes.title') }}</h1>
    </div>
    <p class="muted read-only">{{ canWriteAny ? t('noteEdit.hint') : t('notes.readOnly') }}</p>
    <p v-if="message" class="alert alert-ok edit-result notes-message" role="status">{{ message }}</p>

    <fieldset class="mod-filters iso-filters notes-range">
      <legend class="visually-hidden">{{ t('notes.range.label') }}</legend>
      <div class="control">
        <label for="notes-from">{{ t('notes.range.from') }}</label>
        <input id="notes-from" v-model="range.from" type="date" :max="range.to || undefined" :aria-invalid="rangeCheck !== 'ok'" aria-describedby="notes-range-msg" />
      </div>
      <div class="control">
        <label for="notes-to">{{ t('notes.range.to') }}</label>
        <input id="notes-to" v-model="range.to" type="date" :min="range.from || undefined" :aria-invalid="rangeCheck !== 'ok'" aria-describedby="notes-range-msg" />
      </div>
      <div class="control">
        <button type="button" class="notes-btn notes-reset" @click="resetRange">{{ t('notes.range.reset') }}</button>
      </div>
      <p id="notes-range-msg" class="notes-range-msg" :class="{ 'iso-error': rangeCheck !== 'ok' }" role="status">
        <template v-if="rangeCheck !== 'ok'">{{ t('notes.range.' + rangeCheck) }}</template>
      </p>
    </fieldset>

    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('notes.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <template v-if="data">
      <fieldset class="mod-filters iso-filters">
        <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
        <div class="control search">
          <label for="notes-search">{{ t('modules.search') }}</label>
          <input id="notes-search" v-model="filter.text" type="search" :placeholder="t('notes.searchPlaceholder')" />
        </div>
        <div class="control">
          <span id="notes-in" class="seg-label">{{ t('notes.searchIn') }}</span>
          <div class="mod-seg notes-in" role="group" aria-labelledby="notes-in">
            <label><input v-model="filter.inText" type="checkbox" />{{ t('notes.in.text') }}</label>
            <label><input v-model="filter.inUser" type="checkbox" />{{ t('notes.in.user') }}</label>
            <label><input v-model="filter.inRow" type="checkbox" />{{ t('notes.in.row') }}</label>
            <label><input v-model="filter.inTag" type="checkbox" />{{ t('notes.in.tag') }}</label>
          </div>
        </div>
        <div v-if="sites.length > 1 || (sites.length && hasNotesWithoutSite)" class="control">
          <label for="notes-site">{{ t('modules.site') }}</label>
          <select id="notes-site" v-model="siteValue">
            <option value="all">{{ t('modules.allSites') }}</option>
            <option v-for="s in sites" :key="s.id" :value="String(s.id)">{{ s.name }}</option>
            <option v-if="hasNotesWithoutSite" value="none">{{ t('modules.noSite') }}</option>
          </select>
        </div>
      </fieldset>

      <div v-if="tags.length" class="notes-tags" role="group" :aria-label="t('notes.tagFilter')">
        <span class="seg-label" aria-hidden="true">{{ t('notes.tagFilter') }}</span>
        <button type="button" class="notes-chip" :aria-pressed="!filter.tags.length" @click="filter.tags = []">{{ t('notes.clearTags') }}</button>
        <button v-for="tag in tags" :key="tag.id" type="button" class="notes-chip" :aria-pressed="filter.tags.includes(tag.id)" @click="toggleTagFilter(tag.id)">
          {{ tag.name }} <span class="notes-chip-count">{{ tag.count }}</span>
        </button>
      </div>

      <div v-if="data.truncated" class="alert notes-truncated" role="note">{{ t('notes.truncated', { n: all.length }) }}</div>

      <div class="iso-toolbar">
        <p class="mod-count muted" role="status">{{ t('modules.count', { shown: rows.length, total: all.length }) }}</p>
        <button type="button" class="notes-btn" :disabled="!rows.length" :aria-label="t('notes.export.csvLabel')" @click="exportCsv">
          {{ t('notes.export.csv') }}
        </button>
        <button v-if="rowChoices.length" type="button" class="notes-btn notes-new" @click="newNote">{{ t('noteEdit.add') }}</button>
      </div>

      <p v-if="!all.length" class="muted">{{ t('notes.empty') }}</p>
      <p v-else-if="!rows.length" class="muted">{{ noField && filter.text.trim() ? t('notes.noField') : t('notes.noMatch') }}</p>

      <ul v-else-if="narrow" class="mod-cards">
        <li v-for="n in sorted" :key="n.id" class="mod-card notes-card">
          <h2>{{ cell('date', n) }}</h2>
          <p class="notes-text">{{ n.text || t('modules.none') }}</p>
          <dl class="mod-dl">
            <dt>{{ t('notes.col.row') }}</dt>
            <dd :class="{ muted: n.row && !n.row.readable }">{{ rowText(n) || t('modules.none') }}</dd>
            <template v-if="n.site">
              <dt>{{ t('notes.col.site') }}</dt>
              <dd>{{ n.site.name }}</dd>
            </template>
            <template v-if="visibleTags(n).length">
              <dt>{{ t('notes.col.tags') }}</dt>
              <dd><span v-for="tag in visibleTags(n)" :key="tag" class="note-tag">{{ tagName(tag) }}</span></dd>
            </template>
            <template v-if="n.user">
              <dt>{{ t('notes.col.user') }}</dt>
              <dd>{{ n.user }}</dd>
            </template>
          </dl>
          <button
            type="button"
            class="mod-toggle"
            :aria-expanded="selected === n.id"
            :aria-controls="domId(n, 'c')"
            :aria-label="showDetails(n)"
            @click="toggle(n.id)"
          >
            <span class="chevron" aria-hidden="true">›</span>{{ t('modules.details') }}
          </button>
          <div v-if="selected === n.id" :id="domId(n, 'c')" class="mod-details">
            <NoteDetails :note="n" :tag-name="tagName" @edit="editNote(n)" @add="addToRow(n)" />
          </div>
        </li>
      </ul>

      <div v-else class="mod-table-wrap">
        <table class="mod-table iso-table notes-table">
          <caption class="visually-hidden">{{ t('notes.title') }}</caption>
          <thead>
            <tr>
              <th scope="col" class="iso-toggle-col"><span class="visually-hidden">{{ t('modules.details') }}</span></th>
              <th v-for="c in tableColumns" :key="c" scope="col" :aria-sort="ariaSort(c)">
                <button type="button" class="iso-sort" @click="sortBy(c)">
                  {{ t('notes.col.' + c) }}<span class="iso-sort-mark" aria-hidden="true">{{ sortId === c ? (sortDir === 'asc' ? '▲' : '▼') : '' }}</span>
                </button>
              </th>
            </tr>
          </thead>
          <tbody>
            <template v-for="n in sorted" :key="n.id">
              <tr class="mod-row notes-row" :class="{ 'is-selected': selected === n.id }" @click="toggle(n.id)">
                <td class="iso-toggle-col">
                  <button
                    type="button"
                    class="mod-toggle"
                    :aria-expanded="selected === n.id"
                    :aria-controls="domId(n, 'd')"
                    :aria-label="showDetails(n)"
                    :title="showDetails(n)"
                    @click.stop="toggle(n.id)"
                  >
                    <span class="chevron" aria-hidden="true">›</span>
                  </button>
                </td>
                <td class="nowrap">{{ cell('date', n) }}</td>
                <td class="iso-text">{{ n.site?.name ?? '' }}</td>
                <td class="iso-text notes-row-name" :class="{ muted: n.row && !n.row.readable }">
                  <template v-if="n.row?.readable">
                    <span class="notes-path">{{ n.row.path }}</span>
                    <span>{{ n.row.name || '#' + n.row.id }}</span>
                  </template>
                  <template v-else>{{ rowText(n) }}</template>
                </td>
                <td class="iso-text"><span class="notes-text notes-clamp">{{ n.text }}</span></td>
                <td><span v-for="tag in visibleTags(n)" :key="tag" class="note-tag">{{ tagName(tag) }}</span></td>
                <td class="iso-text">{{ n.user ?? '' }}</td>
              </tr>
              <tr v-if="selected === n.id" :id="domId(n, 'd')" class="mod-detail">
                <td :colspan="tableColumns.length + 1"><NoteDetails :note="n" :tag-name="tagName" @edit="editNote(n)" @add="addToRow(n)" /></td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>

    <NoteDialog
      v-if="dialog?.kind === 'new'"
      :row-id="dialog.rowId"
      :row-name="dialog.rowName"
      :row-choices="rowChoices"
      :tag-name="tagName"
      @close="dialog = null"
      @saved="done(t('noteEdit.saved'))"
      @stale="reload"
    />
    <NoteDialog
      v-else-if="dialog?.kind === 'edit'"
      :note="editable(dialog.note)"
      :row-name="rowText(dialog.note)"
      :tag-name="tagName"
      @close="dialog = null"
      @saved="done(t('noteEdit.saved'))"
      @deleted="done(t('noteEdit.deleted'))"
      @stale="reload"
    />
  </section>
</template>
