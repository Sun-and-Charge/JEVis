<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  loadReview,
  REVIEW_COMMENT_MAX,
  REVIEW_DAYS,
  REVIEW_DEFAULT_DAYS,
  reviewReading,
  type ReadingMeter,
  type ReviewAction,
  type ReviewItem,
} from '../../api/readings'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { useLoader } from '../../iso/useLoader'
import { formatLocal } from '../../modules/dates'
import { useNarrow } from '../../modules/useNarrow'
import { useSitePreselection } from '../../nav/useSitePreselection'
import { exchangeRow, formatRange, formatReading, formatReadingTime, userNote, type ReadingSavedEvent } from '../../readings/readings'
import {
  ALL_SITES,
  commentProblem,
  exchangePrefill,
  issueClass,
  itemsOfSite,
  reviewActions,
  reviewMeter,
  reviewRowClass,
  reviewSites,
} from '../../readings/review'
import { useAuthStore } from '../../stores/auth'
import { useNavigationStore } from '../../stores/navigation'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import MeterExchangeDialog from '../registers/MeterExchangeDialog.vue'
import ReadingPanel from './ReadingPanel.vue'
import '../../styles/readings.css'

/*
 * Review of contradictory readings (E8.5), the third view of the reading page: samples of the last 90 / 365 / 1095
 * days that go backwards on a counter, are far from the expected value or break a limit. Per item: "Korrigieren"
 * (the reading panel with the time fixed, saved with confirm), "Zählerwechsel" (E5 dialog prefilled with the item's
 * time and value as the new meter's first reading), "Bestätigen" (note "Ablesung geprüft") and "Ignorieren" (the
 * value is removed from the row and kept as a note). The list and the navigation badge reload after each action.
 */

const props = defineProps<{
  /** the meters of the reading list (mode, time zone and last readings for the correction panel) */
  meters: readonly ReadingMeter[]
}>()
const emit = defineEmits<{ count: [n: number]; changed: [] }>()

const { t, te, locale } = useI18n()
const auth = useAuthStore()
const nav = useNavigationStore()
const narrow = useNarrow()

const days = ref<number>(REVIEW_DEFAULT_DAYS)
const { data, state, reload } = useLoader(() => loadReview(auth.http, locale.value, days.value))
watch([locale, days], () => reload())
watch(data, (d) => {
  if (d) emit('count', typeof d.count === 'number' ? d.count : d.items.length)
})

const message = ref<{ ok: boolean; text: string } | null>(null)

/* ---- site tabs ("Alle" first when there are several sites) ---- */
const preselect = useSitePreselection()
const selected = ref<string>(ALL_SITES)
const items = computed(() => data.value?.items ?? [])
const sites = computed(() => reviewSites(items.value, locale.value, t('readings.review.noSite')))
const tabs = computed(() => (sites.value.length > 1 ? [{ key: ALL_SITES, name: t('readings.review.allSites'), site: null, count: items.value.length }, ...sites.value] : []))
const current = computed(() => (tabs.value.some((s) => s.key === selected.value) ? selected.value : ALL_SITES))
const list = computed(() => itemsOfSite(items.value, current.value))

watch(sites, (s) => {
  if (!s.length) return
  const key = preselect.pick(s)
  if (key != null) selected.value = key
})

function onTabKey(event: KeyboardEvent, index: number) {
  const all = tabs.value
  let next = -1
  if (event.key === 'ArrowRight') next = (index + 1) % all.length
  else if (event.key === 'ArrowLeft') next = (index - 1 + all.length) % all.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = all.length - 1
  if (next < 0) return
  event.preventDefault()
  selected.value = all[next].key
  document.getElementById(tabId(all[next].key))?.focus()
}

/* ---- after an action: the list, the navigation badge and the reading list ---- */
async function done(text: string) {
  message.value = { ok: true, text }
  void nav.load()
  emit('changed')
  await reload()
}

/* ---- Korrigieren ---- */
const correcting = shallowRef<ReviewItem | null>(null)
const correctMeter = computed(() => (correcting.value ? reviewMeter(correcting.value, props.meters) : null))
const correction = computed(() => {
  const i = correcting.value
  return i ? { timestamp: i.timestamp, value: i.value, previous: i.previous, expected: i.expected } : null
})

function correct(item: ReviewItem) {
  message.value = null
  correcting.value = item
}

async function onCorrected(e: ReadingSavedEvent) {
  correcting.value = null
  const value = formatReading(e.result.value, e.meter, locale.value)
  const time = formatReadingTime(e.result.timestamp, e.meter, locale.value)
  await done(t(e.photoSkipped ? 'readings.savedNoPhoto' : 'readings.review.corrected', { name: e.meter.name, value, time }))
}

/* ---- Zählerwechsel ---- */
const exchanging = shallowRef<ReviewItem | null>(null)
const exchangeMeter = computed(() => (exchanging.value ? exchangeRow(reviewMeter(exchanging.value, props.meters)) : null))
const exchangeInitial = computed(() => (exchanging.value ? exchangePrefill(exchanging.value, locale.value) : null))

function exchange(item: ReviewItem) {
  message.value = null
  exchanging.value = item
}

async function onExchanged(time: string) {
  exchanging.value = null
  await done(t('registerEdit.exchange.saved', { time: formatLocal(time, locale.value) }))
}

/* ---- Bestätigen / Ignorieren ---- */
const deciding = shallowRef<{ item: ReviewItem; action: ReviewAction } | null>(null)
const comment = ref('')
const commentError = ref<string | null>(null)
const decideError = ref<string | null>(null)
const { busy, run } = useBusy()

function decide(item: ReviewItem, action: ReviewAction) {
  message.value = null
  comment.value = ''
  commentError.value = null
  decideError.value = null
  deciding.value = { item, action }
}

function cancelDecide() {
  if (!busy.value) deciding.value = null
}

async function confirmDecide() {
  const d = deciding.value
  if (!d) return
  decideError.value = null
  const problem = commentProblem(comment.value, REVIEW_COMMENT_MAX)
  commentError.value = problem ? t('readings.review.comment.' + problem, { max: REVIEW_COMMENT_MAX }) : null
  if (problem) {
    document.getElementById(id('comment'))?.focus()
    return
  }
  const { item, action } = d
  const text = comment.value.trim()
  await run(async () => {
    try {
      await reviewReading(
        auth.http,
        item.meterId,
        { timestamp: item.timestamp, action, comment: text || null, ...(typeof item.value === 'number' ? { value: item.value } : {}) },
        locale.value,
      )
    } catch (e) {
      const kind = writeErrorKind(e)
      if (kind === 'conflict' || kind === 'notFound') {
        /* the value changed or is gone since it was listed: show the current list */
        deciding.value = null
        message.value = { ok: false, text: t(kind === 'conflict' ? 'readings.review.conflict' : 'readings.review.gone', { name: item.meterName }) }
        await reload()
        return
      }
      decideError.value = writeErrorText(e, t)
      return
    }
    deciding.value = null
    const args = { name: item.meterName, value: valueText(item), time: timeText(item) }
    await done(t(action === 'CONFIRM' ? 'readings.review.confirmed' : 'readings.review.ignored', args))
  })
}

const decideTitle = computed(() => (deciding.value?.action === 'IGNORE' ? t('readings.review.ignoreTitle') : t('readings.review.confirmTitle')))
const decideMessage = computed(() => {
  const d = deciding.value
  if (!d) return ''
  const args = { name: d.item.meterName, value: valueText(d.item), time: timeText(d.item) }
  return d.action === 'IGNORE' ? t('readings.review.ignoreQuestion', args) : t('readings.review.confirmQuestion', args)
})

/* ---- texts ---- */
/* the meter of the reading list for the formatting (decimals, mode, time zone), else one built from the item */
const meterOf = (item: ReviewItem) => reviewMeter(item, props.meters)
const valueText = (item: ReviewItem) => formatReading(item.value, meterOf(item), locale.value)
const timeText = (item: ReviewItem) => formatReadingTime(item.timestamp, meterOf(item), locale.value)
const previousText = (item: ReviewItem) => (item.previous ? formatReading(item.previous.value, meterOf(item), locale.value) : '')
const previousTime = (item: ReviewItem) => (item.previous ? formatReadingTime(item.previous.timestamp, meterOf(item), locale.value) : '')
const rangeText = (item: ReviewItem) => (item.expected ? formatRange(item.expected, meterOf(item), locale.value) : '')
const noteText = (item: ReviewItem) => userNote(item)
const issueText = (issue: string) => (te('readings.review.issue.' + issue) ? t('readings.review.issue.' + issue) : issue)
const daysText = (n: number) => t('readings.review.days.' + n)
const itemKey = (item: ReviewItem) => `${item.meterId}@${item.timestamp}`
const label = (key: string, item: ReviewItem) => t('readings.review.' + key + 'Label', { name: item.meterName, time: timeText(item) })
const showSite = computed(() => current.value === ALL_SITES && sites.value.length > 1)
const emptyText = computed(() => (current.value === ALL_SITES ? t('readings.review.empty', { days: daysText(days.value) }) : t('readings.review.emptySite')))

const tabId = (key: string) => `rd-review-tab-${key}`
const id = (suffix: string) => `rd-review-${suffix}`
</script>

<template>
  <div class="rd-review">
    <p class="muted rd-review-intro">{{ t('readings.review.intro') }}</p>
    <p v-if="message" class="alert edit-result rd-message" :class="message.ok ? 'alert-ok' : 'alert-error'" :role="message.ok ? 'status' : 'alert'">
      {{ message.text }}
    </p>
    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('readings.review.loading') }}</p>
    <div v-else-if="state === 'error' && !data" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <template v-if="data">
      <div v-if="tabs.length" class="reg-tabs" role="tablist" :aria-label="t('registers.groups')">
        <button
          v-for="(s, i) in tabs"
          :id="tabId(s.key)"
          :key="s.key"
          type="button"
          role="tab"
          class="reg-tab"
          :aria-selected="s.key === current"
          :aria-controls="id('panel')"
          :tabindex="s.key === current ? 0 : -1"
          @click="selected = s.key"
          @keydown="onTabKey($event, i)"
        >
          {{ s.name }} <span class="reg-tab-count">{{ s.count }}</span>
        </button>
      </div>

      <div :id="id('panel')" :role="tabs.length ? 'tabpanel' : undefined" :aria-labelledby="tabs.length ? tabId(current) : undefined">
        <fieldset class="mod-filters rd-filters">
          <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
          <div class="control">
            <label :for="id('days')">{{ t('readings.review.period') }}</label>
            <select :id="id('days')" v-model.number="days">
              <option v-for="n in REVIEW_DAYS" :key="n" :value="n">{{ daysText(n) }}</option>
            </select>
          </div>
        </fieldset>

        <div class="iso-toolbar">
          <p class="mod-count muted" role="status">{{ t('readings.review.count', list.length) }}</p>
        </div>
        <p v-if="data.truncated" class="muted rd-review-truncated" role="note">{{ t('readings.review.truncated', { n: data.items.length }) }}</p>

        <p v-if="!list.length" class="muted rd-review-empty">{{ emptyText }}</p>

        <ul v-else-if="narrow" class="mod-cards rd-cards rd-review-cards">
          <li v-for="item in list" :key="itemKey(item)" class="mod-card rd-card rd-review-card" :class="reviewRowClass(item)">
            <div class="rd-card-head">
              <h2>{{ item.meterName }}</h2>
            </div>
            <div class="rd-issues">
              <span v-for="issue in item.issues" :key="issue" class="mod-badge rd-issue" :class="issueClass(issue)">{{ issueText(issue) }}</span>
            </div>
            <dl class="mod-dl">
              <template v-if="showSite && item.site">
                <dt>{{ t('readings.rounds.field.site') }}</dt>
                <dd>{{ item.site.name }}</dd>
              </template>
              <dt>{{ t('readings.review.col.time') }}</dt>
              <dd>{{ timeText(item) }}</dd>
              <dt>{{ t('readings.review.col.value') }}</dt>
              <dd class="rd-review-value">{{ valueText(item) }}</dd>
              <template v-if="item.previous">
                <dt>{{ t('readings.review.col.previous') }}</dt>
                <dd>{{ previousText(item) }} <span class="muted">· {{ previousTime(item) }}</span></dd>
              </template>
              <template v-if="item.expected">
                <dt>{{ t('readings.review.col.expected') }}</dt>
                <dd>{{ rangeText(item) }}</dd>
              </template>
              <template v-if="noteText(item)">
                <dt>{{ t('readings.note.label') }}</dt>
                <dd class="rd-note">{{ noteText(item) }}</dd>
              </template>
            </dl>
            <div class="rd-round-actions rd-review-actions">
              <button v-if="reviewActions(item).correct" type="button" class="btn btn-primary rd-review-correct" :aria-label="label('correct', item)" @click="correct(item)">
                {{ t('readings.review.correct') }}
              </button>
              <button v-if="reviewActions(item).exchange" type="button" class="edit-btn rd-review-exchange" :aria-label="label('exchange', item)" @click="exchange(item)">
                {{ t('registerEdit.exchange.action') }}
              </button>
              <button v-if="reviewActions(item).confirm" type="button" class="edit-btn rd-review-confirm" :aria-label="label('confirm', item)" @click="decide(item, 'CONFIRM')">
                {{ t('readings.review.confirm') }}
              </button>
              <button v-if="reviewActions(item).ignore" type="button" class="edit-btn is-danger rd-review-ignore" :aria-label="label('ignore', item)" @click="decide(item, 'IGNORE')">
                {{ t('readings.review.ignore') }}
              </button>
            </div>
          </li>
        </ul>

        <div v-else class="mod-table-wrap">
          <table class="mod-table rd-table rd-review-table">
            <caption class="visually-hidden">{{ t('readings.views.review') }}</caption>
            <thead>
              <tr>
                <th scope="col">{{ t('readings.col.meter') }}</th>
                <th scope="col">{{ t('readings.review.col.time') }}</th>
                <th scope="col" class="num">{{ t('readings.review.col.value') }}</th>
                <th scope="col" class="num">{{ t('readings.review.col.previous') }}</th>
                <th scope="col">{{ t('readings.review.col.expected') }}</th>
                <th scope="col">{{ t('readings.review.col.issues') }}</th>
                <th scope="col">{{ t('readings.note.label') }}</th>
                <th scope="col"><span class="visually-hidden">{{ t('readings.col.action') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in list" :key="itemKey(item)" class="mod-row rd-row rd-review-row" :class="reviewRowClass(item)">
                <td class="iso-text">
                  <strong class="rd-round-name rd-review-meter">{{ item.meterName }}</strong>
                  <span v-if="showSite && item.site" class="rd-round-sub">{{ item.site.name }}</span>
                </td>
                <td class="nowrap">{{ timeText(item) }}</td>
                <td class="num nowrap rd-review-value">{{ valueText(item) }}</td>
                <td class="num nowrap">
                  {{ previousText(item) }}
                  <span v-if="item.previous" class="rd-round-sub">{{ previousTime(item) }}</span>
                </td>
                <td class="rd-review-range">{{ rangeText(item) }}</td>
                <td>
                  <div class="rd-issues">
                    <span v-for="issue in item.issues" :key="issue" class="mod-badge rd-issue" :class="issueClass(issue)">{{ issueText(issue) }}</span>
                  </div>
                </td>
                <td class="iso-text rd-note">{{ noteText(item) }}</td>
                <td class="rd-action-col">
                  <div class="rd-round-btns">
                    <button v-if="reviewActions(item).correct" type="button" class="edit-btn is-small rd-review-correct" :aria-label="label('correct', item)" @click="correct(item)">
                      {{ t('readings.review.correct') }}
                    </button>
                    <button v-if="reviewActions(item).exchange" type="button" class="edit-btn is-small rd-review-exchange" :aria-label="label('exchange', item)" @click="exchange(item)">
                      {{ t('registerEdit.exchange.action') }}
                    </button>
                    <button v-if="reviewActions(item).confirm" type="button" class="edit-btn is-small rd-review-confirm" :aria-label="label('confirm', item)" @click="decide(item, 'CONFIRM')">
                      {{ t('readings.review.confirm') }}
                    </button>
                    <button
                      v-if="reviewActions(item).ignore"
                      type="button"
                      class="edit-btn is-small is-danger rd-review-ignore"
                      :aria-label="label('ignore', item)"
                      @click="decide(item, 'IGNORE')"
                    >
                      {{ t('readings.review.ignore') }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <ReadingPanel v-if="correcting && correctMeter" :key="itemKey(correcting)" :meter="correctMeter" :correction="correction" @close="correcting = null" @saved="onCorrected" @changed="reload" />
    <MeterExchangeDialog v-if="exchanging && exchangeMeter" :row="exchangeMeter" :initial="exchangeInitial" @close="exchanging = null" @saved="onExchanged" />
    <ConfirmDialog
      v-if="deciding"
      :title="decideTitle"
      :message="decideMessage"
      :confirm-label="deciding.action === 'IGNORE' ? t('readings.review.ignore') : t('readings.review.confirm')"
      :busy="busy"
      :error="decideError"
      :danger="deciding.action === 'IGNORE'"
      @confirm="confirmDecide"
      @cancel="cancelDecide"
    >
      <p v-if="deciding.action === 'IGNORE'" class="alert alert-error rd-review-ignore-note">{{ t('readings.review.ignoreNote') }}</p>
      <div class="edit-field rd-review-comment">
        <label :for="id('comment')">{{ t('readings.review.comment.label') }}</label>
        <textarea
          :id="id('comment')"
          v-model="comment"
          rows="3"
          :maxlength="REVIEW_COMMENT_MAX"
          :placeholder="deciding.action === 'IGNORE' ? t('readings.review.comment.ignorePlaceholder') : t('readings.review.comment.placeholder')"
          :disabled="busy"
          :aria-invalid="commentError ? 'true' : undefined"
          :aria-describedby="commentError ? id('comment-err') : undefined"
          @input="commentError = null"
        ></textarea>
        <p v-if="commentError" :id="id('comment-err')" class="iso-field-error">{{ commentError }}</p>
      </div>
    </ConfirmDialog>
  </div>
</template>
