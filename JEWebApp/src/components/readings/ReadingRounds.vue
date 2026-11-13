<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { deleteRound, loadRoundOptions, loadRounds, type ReadingRound, type RoundOptions } from '../../api/readingRounds'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { useLoader } from '../../iso/useLoader'
import { formatLocalDate } from '../../modules/dates'
import { useNarrow } from '../../modules/useNarrow'
import { defaultRoundFilter, filterRounds, peopleText, roundRowClass, sortRounds, type RoundFilter } from '../../readings/rounds'
import { openRoundSheet } from '../../readings/sheet'
import { useAuthStore } from '../../stores/auth'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import RoundEditor from './RoundEditor.vue'
import RoundStateBadge from './RoundStateBadge.vue'
import '../../styles/readings.css'

/*
 * Reading rounds (E8.2), the second view of the reading page: the rounds the user may read with state, due date,
 * progress and readers; filter "Meine Runden" (default if the user reads any) / "Alle", site and search. Actions:
 * "Ablesen" (the meter list of the round, ?round=<id>), "Laufzettel" (PDF in a new tab), "Bearbeiten", "Löschen"
 * (recycle bin) and "Neue Runde" (create right at the site).
 */

const props = defineProps<{
  /** sites with readable meters (for new rounds) */
  sites: readonly { id: number; name: string }[]
  /** preselected site (the site tab of the meter list) */
  site?: number | null
}>()

const { t, locale } = useI18n()
const auth = useAuthStore()
const router = useRouter()
const narrow = useNarrow()

const { data, state, reload } = useLoader(() => loadRounds(auth.http, locale.value))
watch(locale, () => reload())

const filter = ref<RoundFilter>('all')
let filterSet = false
const siteFilter = ref<number | null>(null)
const text = ref('')
const message = ref<{ ok: boolean; text: string } | null>(null)
const sheetBusy = ref<number | null>(null)

watch(data, (d) => {
  if (d && !filterSet) {
    filterSet = true
    filter.value = defaultRoundFilter(d.rounds)
  }
})

const rounds = computed(() => sortRounds(data.value?.rounds ?? [], locale.value))
const today = computed(() => data.value?.today ?? new Date().toISOString().slice(0, 10))
const mineCount = computed(() => rounds.value.filter((r) => r.mine).length)
const list = computed(() => filterRounds(rounds.value, filter.value, siteFilter.value, text.value, locale.value))
/* sites of the rounds and of the meters, for the site filter */
const allSites = computed(() => {
  const map = new Map<number, string>()
  for (const s of props.sites) map.set(s.id, s.name)
  for (const r of rounds.value) if (r.site) map.set(r.site.id, r.site.name)
  return [...map].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name, locale.value))
})
const showSite = computed(() => allSites.value.length > 1)

/* ---- options (cached per site; the create right of the default site decides "Neue Runde") ---- */
const optionsCache = new Map<number, Promise<RoundOptions>>()
function optionsFor(site: number): Promise<RoundOptions> {
  let p = optionsCache.get(site)
  if (!p) {
    p = loadRoundOptions(auth.http, site, locale.value)
    p.catch(() => optionsCache.delete(site))
    optionsCache.set(site, p)
  }
  return p
}
watch(locale, () => optionsCache.clear())

const newSite = computed(() => siteFilter.value ?? props.site ?? props.sites[0]?.id ?? null)
const canCreate = ref(false)
watch(
  newSite,
  async (site) => {
    canCreate.value = false
    if (site == null) return
    try {
      const o = await optionsFor(site)
      if (site === newSite.value) canCreate.value = !!o.canCreate
    } catch {
      /* without options no "Neue Runde" */
    }
  },
  { immediate: true },
)

/* ---- actions ---- */
const editing = shallowRef<{ round: ReadingRound | null } | null>(null)

function openCreate() {
  message.value = null
  editing.value = { round: null }
}

function openEdit(r: ReadingRound) {
  if (!r.canWrite) return
  message.value = null
  editing.value = { round: r }
}

async function onSaved(round: ReadingRound | null, created: boolean, name: string) {
  editing.value = null
  message.value = { ok: true, text: t(created ? 'registerEdit.created' : 'registerEdit.saved', { name }) }
  /* a new round the user does not read would be hidden by "Meine Runden" */
  if (created && filter.value === 'mine' && !round?.mine) filter.value = 'all'
  await reload()
}

function read(r: ReadingRound) {
  void router.push({ query: { round: String(r.id) } })
}

async function sheet(r: ReadingRound) {
  if (sheetBusy.value != null) return
  sheetBusy.value = r.id
  message.value = null
  try {
    await openRoundSheet(auth.http, r, locale.value, t('readings.rounds.sheetLoading'))
  } catch (e) {
    message.value = { ok: false, text: t('readings.rounds.sheetError', { reason: writeErrorText(e, t) }) }
  } finally {
    sheetBusy.value = null
  }
}

const deleting = shallowRef<ReadingRound | null>(null)
const deleteError = ref<string | null>(null)
const { busy: deleteBusy, run } = useBusy()

function askDelete(r: ReadingRound) {
  if (!r.canDelete) return
  message.value = null
  deleteError.value = null
  deleting.value = r
}

async function confirmDelete() {
  const r = deleting.value
  if (!r) return
  await run(async () => {
    try {
      await deleteRound(auth.http, r.id, r.version)
      deleting.value = null
      message.value = { ok: true, text: t('registerEdit.deleted', { name: r.name }) }
      await reload()
    } catch (e) {
      const kind = writeErrorKind(e)
      if (kind === 'conflict' || kind === 'notFound') {
        deleting.value = null
        message.value = { ok: false, text: kind === 'conflict' ? t('registerEdit.conflictDelete', { name: r.name }) : writeErrorText(e, t) }
        await reload()
        return
      }
      deleteError.value = writeErrorText(e, t)
    }
  })
}

function cancelDelete() {
  if (!deleteBusy.value) deleting.value = null
}

/* ---- texts ---- */
const dueText = (r: ReadingRound) => (r.due ? formatLocalDate(r.due, locale.value) : r.nextDue ? t('readings.rounds.startsOn', { date: formatLocalDate(r.nextDue, locale.value) }) : '')
const periodText = (r: ReadingRound) => t('readings.rounds.period.' + r.period)
const progressText = (r: ReadingRound) => t('readings.rounds.progress', { done: r.done, total: r.total })
const progressPct = (r: ReadingRound) => (r.total > 0 ? Math.round((100 * r.done) / r.total) : 0)
const readersText = (r: ReadingRound) => peopleText(r.readers, t('readings.rounds.unknownPerson')) || t('modules.none')
const emptyText = computed(() =>
  !rounds.value.length ? t('readings.rounds.empty') : filter.value === 'mine' && !text.value.trim() ? t('readings.rounds.noneMine') : t('readings.rounds.noMatch'),
)
const id = (suffix: string) => `rd-rounds-${suffix}`
</script>

<template>
  <div class="rd-rounds">
    <p v-if="message" class="alert edit-result rd-message" :class="message.ok ? 'alert-ok' : 'alert-error'" :role="message.ok ? 'status' : 'alert'">
      {{ message.text }}
    </p>
    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('readings.rounds.loading') }}</p>
    <div v-else-if="state === 'error' && !data" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <template v-if="data">
      <fieldset class="mod-filters rd-filters">
        <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
        <div class="control">
          <span :id="id('filter')" class="rd-filter-label">{{ t('readings.filter.label') }}</span>
          <div class="rd-chips" role="group" :aria-labelledby="id('filter')">
            <button type="button" class="edit-chip rd-chip" :aria-pressed="filter === 'mine'" @click="filter = 'mine'">
              {{ t('readings.rounds.filter.mine') }} <span class="rd-chip-count">{{ mineCount }}</span>
            </button>
            <button type="button" class="edit-chip rd-chip" :aria-pressed="filter === 'all'" @click="filter = 'all'">
              {{ t('readings.filter.all') }} <span class="rd-chip-count">{{ rounds.length }}</span>
            </button>
          </div>
        </div>
        <div v-if="showSite" class="control">
          <label :for="id('site')">{{ t('readings.rounds.field.site') }}</label>
          <select :id="id('site')" v-model="siteFilter">
            <option :value="null">{{ t('readings.rounds.allSites') }}</option>
            <option v-for="s in allSites" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </div>
        <div class="control search">
          <label :for="id('search')">{{ t('modules.search') }}</label>
          <input :id="id('search')" v-model="text" type="search" :placeholder="t('readings.rounds.searchPlaceholder')" />
        </div>
      </fieldset>

      <div class="iso-toolbar">
        <p class="mod-count muted" role="status">{{ t('readings.rounds.count', { shown: list.length, total: rounds.length }) }}</p>
        <div class="reg-actions">
          <button v-if="canCreate" type="button" class="btn btn-primary rd-round-new" @click="openCreate">{{ t('readings.rounds.new') }}</button>
        </div>
      </div>

      <p v-if="!list.length" class="muted rd-rounds-empty">{{ emptyText }}</p>

      <ul v-else-if="narrow" class="mod-cards rd-cards rd-round-cards">
        <li v-for="r in list" :key="r.id" class="mod-card rd-card rd-round-card" :class="roundRowClass(r.state)">
          <div class="rd-card-head">
            <h2>{{ r.name }}</h2>
            <RoundStateBadge :state="r.state" />
          </div>
          <dl class="mod-dl">
            <template v-if="showSite && r.site">
              <dt>{{ t('readings.rounds.field.site') }}</dt>
              <dd>{{ r.site.name }}</dd>
            </template>
            <dt>{{ t('readings.rounds.col.due') }}</dt>
            <dd>{{ dueText(r) }} <span class="muted">· {{ periodText(r) }}</span></dd>
            <dt>{{ t('readings.rounds.col.progress') }}</dt>
            <dd>
              <span class="rd-progress" aria-hidden="true"><span :style="{ width: progressPct(r) + '%' }"></span></span>
              {{ progressText(r) }}
            </dd>
            <dt>{{ t('readings.rounds.col.readers') }}</dt>
            <dd>{{ readersText(r) }}<span v-if="r.mine" class="rd-mine"> · {{ t('readings.rounds.mine') }}</span></dd>
          </dl>
          <div class="rd-round-actions">
            <button type="button" class="btn btn-primary rd-round-read" :aria-label="t('readings.rounds.readLabel', { name: r.name })" @click="read(r)">
              {{ t('readings.read') }}
            </button>
            <button type="button" class="edit-btn rd-round-sheet" :disabled="sheetBusy === r.id" :aria-label="t('readings.rounds.sheetLabel', { name: r.name })" @click="sheet(r)">
              {{ t('readings.rounds.sheet') }}
            </button>
            <button v-if="r.canWrite" type="button" class="edit-btn rd-round-edit" :aria-label="t('registerEdit.editLabel', { name: r.name })" @click="openEdit(r)">
              {{ t('registerEdit.edit') }}
            </button>
            <button v-if="r.canDelete" type="button" class="edit-btn is-danger rd-round-delete" :aria-label="t('registerEdit.deleteLabel', { name: r.name })" @click="askDelete(r)">
              {{ t('registerEdit.delete') }}
            </button>
          </div>
        </li>
      </ul>

      <div v-else class="mod-table-wrap">
        <table class="mod-table rd-table rd-round-table">
          <caption class="visually-hidden">{{ t('readings.views.rounds') }}</caption>
          <thead>
            <tr>
              <th scope="col">{{ t('readings.rounds.col.round') }}</th>
              <th scope="col">{{ t('readings.rounds.col.state') }}</th>
              <th scope="col">{{ t('readings.rounds.col.due') }}</th>
              <th scope="col">{{ t('readings.rounds.col.progress') }}</th>
              <th scope="col">{{ t('readings.rounds.col.readers') }}</th>
              <th scope="col"><span class="visually-hidden">{{ t('readings.col.action') }}</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in list" :key="r.id" class="mod-row rd-row rd-round-row" :class="roundRowClass(r.state)">
              <td class="iso-text">
                <strong class="rd-round-name">{{ r.name }}</strong>
                <span v-if="showSite && r.site" class="rd-round-sub">{{ r.site.name }}</span>
              </td>
              <td><RoundStateBadge :state="r.state" /></td>
              <td class="nowrap">
                {{ dueText(r) }}
                <span class="rd-round-sub">{{ periodText(r) }}</span>
              </td>
              <td class="nowrap">
                <span class="rd-progress" aria-hidden="true"><span :style="{ width: progressPct(r) + '%' }"></span></span>
                {{ progressText(r) }}
              </td>
              <td class="iso-text">
                {{ readersText(r) }}
                <span v-if="r.mine" class="rd-round-sub rd-mine">{{ t('readings.rounds.mine') }}</span>
              </td>
              <td class="rd-action-col">
                <div class="rd-round-btns">
                  <button type="button" class="edit-btn is-small rd-round-read" :aria-label="t('readings.rounds.readLabel', { name: r.name })" @click="read(r)">
                    {{ t('readings.read') }}
                  </button>
                  <button
                    type="button"
                    class="edit-btn is-small rd-round-sheet"
                    :disabled="sheetBusy === r.id"
                    :aria-label="t('readings.rounds.sheetLabel', { name: r.name })"
                    @click="sheet(r)"
                  >
                    {{ t('readings.rounds.sheet') }}
                  </button>
                  <button v-if="r.canWrite" type="button" class="edit-btn is-small rd-round-edit" :aria-label="t('registerEdit.editLabel', { name: r.name })" @click="openEdit(r)">
                    {{ t('registerEdit.edit') }}
                  </button>
                  <button
                    v-if="r.canDelete"
                    type="button"
                    class="edit-btn is-small is-danger rd-round-delete"
                    :aria-label="t('registerEdit.deleteLabel', { name: r.name })"
                    @click="askDelete(r)"
                  >
                    {{ t('registerEdit.delete') }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <RoundEditor
      v-if="editing"
      :round="editing.round"
      :sites="sites"
      :site="newSite"
      :rounds="rounds"
      :today="today"
      :load-options="optionsFor"
      @close="editing = null"
      @saved="onSaved"
      @changed="reload"
    />
    <ConfirmDialog
      v-if="deleting"
      :title="t('readings.rounds.deleteTitle')"
      :message="t('registerEdit.deleteQuestion', { name: deleting.name })"
      :confirm-label="t('registerEdit.delete')"
      :busy="deleteBusy"
      :error="deleteError"
      danger
      @confirm="confirmDelete"
      @cancel="cancelDelete"
    />
  </div>
</template>
