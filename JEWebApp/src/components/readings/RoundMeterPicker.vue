<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ROUND_LIMITS, type RoundMeter, type RoundOptionMeter } from '../../api/readingRounds'
import { moveItem, searchMeters } from '../../readings/rounds'
import MediumIcon from '../registers/MediumIcon.vue'

/*
 * Meters of a reading round in reading order (E8.2): numbered list, reorder by drag and drop (mouse / touch-capable
 * browsers) and by the up / down buttons (keyboard; the focus stays on the moved meter, the new position is
 * announced), remove; below it the site's meters not yet in the round (search, "add", "add all"). Automatic meters
 * are marked (they count as read and are not on the route sheet); meters missing from the site's list are marked
 * as not available.
 */

const props = defineProps<{
  meters: readonly RoundOptionMeter[]
  /** meters of the round as loaded (names of meters missing from the options) */
  known?: readonly RoundMeter[]
  modelValue: readonly number[]
  idPrefix: string
  loading?: boolean
  error?: string | null
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [ids: number[]] }>()
const { t, locale } = useI18n()

const text = ref('')
const status = ref('')
const dragFrom = ref<number | null>(null)
const dragOver = ref<number | null>(null)

const byId = computed(() => new Map(props.meters.map((m) => [m.id, m])))
const knownById = computed(() => new Map((props.known ?? []).map((m) => [m.id, m])))
const items = computed(() =>
  props.modelValue.map((id) => {
    const m = byId.value.get(id)
    const k = knownById.value.get(id)
    return {
      id,
      name: m?.name ?? k?.name ?? t('readings.rounds.meters.unknown', { id }),
      location: m?.location ?? k?.location ?? null,
      medium: m?.medium ?? null,
      automatic: m?.automatic ?? k?.automatic ?? false,
      /* not in the site's readable meters (the server refuses it) – only known once the options are loaded */
      unavailable: !props.loading && !m,
    }
  }),
)
const matches = computed(() => searchMeters(props.meters, props.modelValue, text.value, locale.value))
const noneText = computed(() =>
  !props.meters.length ? t('readings.rounds.meters.noneAtSite') : text.value.trim() ? t('readings.rounds.meters.noMatch') : t('readings.rounds.meters.allAdded'),
)
const full = computed(() => props.modelValue.length >= ROUND_LIMITS.meters)
const listId = computed(() => props.idPrefix + '-list')
const errId = computed(() => props.idPrefix + '-err')
const btnId = (kind: string, id: number) => `${props.idPrefix}-${kind}-${id}`

function update(ids: number[]) {
  emit('update:modelValue', ids)
}

async function move(index: number, to: number, focusKind?: 'up' | 'down') {
  if (props.disabled || to < 0 || to >= props.modelValue.length || to === index) return
  const item = items.value[index]
  update(moveItem(props.modelValue, index, to))
  status.value = t('readings.rounds.meters.moved', { name: item.name, pos: to + 1, total: props.modelValue.length })
  if (!focusKind) return
  await nextTick()
  /* keep the focus on the moved meter; at the top / bottom the other button */
  const own = document.getElementById(btnId(focusKind, item.id)) as HTMLButtonElement | null
  const other = document.getElementById(btnId(focusKind === 'up' ? 'down' : 'up', item.id)) as HTMLButtonElement | null
  ;(own && !own.disabled ? own : other)?.focus()
}

async function remove(index: number) {
  if (props.disabled) return
  const item = items.value[index]
  const next = props.modelValue.filter((_, i) => i !== index)
  update(next)
  status.value = t('readings.rounds.meters.removed', { name: item.name })
  await nextTick()
  const neighbour = next[Math.min(index, next.length - 1)]
  const target = neighbour != null ? document.getElementById(btnId('remove', neighbour)) : document.getElementById(props.idPrefix + '-search')
  target?.focus()
}

function add(m: RoundOptionMeter) {
  if (props.disabled || full.value) return
  update([...props.modelValue, m.id])
  status.value = t('readings.rounds.meters.added', { name: m.name, pos: props.modelValue.length + 1 })
}

function addAll() {
  if (props.disabled) return
  const room = ROUND_LIMITS.meters - props.modelValue.length
  const add = matches.value.slice(0, Math.max(0, room)).map((m) => m.id)
  if (!add.length) return
  update([...props.modelValue, ...add])
  status.value = t('readings.rounds.meters.addedAll', add.length)
}

/* ---- drag and drop ---- */
function onDragStart(e: DragEvent, index: number) {
  if (props.disabled) return
  dragFrom.value = index
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    /* Firefox only starts a drag with data */
    e.dataTransfer.setData('text/plain', String(props.modelValue[index]))
  }
}

function onDragOver(e: DragEvent, index: number) {
  if (dragFrom.value == null) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  dragOver.value = index
}

function onDrop(e: DragEvent, index: number) {
  e.preventDefault()
  const from = dragFrom.value
  dragFrom.value = null
  dragOver.value = null
  if (from != null) void move(from, index)
}

function onDragEnd() {
  dragFrom.value = null
  dragOver.value = null
}
</script>

<template>
  <div class="rd-meters">
    <p v-if="!modelValue.length" class="muted rd-order-empty">{{ t('readings.rounds.meters.empty') }}</p>
    <ol v-else :id="listId" class="rd-order" :aria-describedby="error ? errId : undefined">
      <li
        v-for="(m, i) in items"
        :key="m.id"
        class="rd-order-item"
        :class="{ 'is-dragging': dragFrom === i, 'is-over': dragOver === i && dragFrom !== i, 'is-unavailable': m.unavailable }"
        :draggable="!disabled"
        @dragstart="onDragStart($event, i)"
        @dragover="onDragOver($event, i)"
        @drop="onDrop($event, i)"
        @dragend="onDragEnd"
      >
        <span class="rd-order-handle" aria-hidden="true" :title="t('readings.rounds.meters.drag')">⠿</span>
        <span class="rd-order-pos">{{ i + 1 }}</span>
        <span class="rd-order-icon"><MediumIcon :medium="m.medium" /></span>
        <span class="rd-order-name">
          <strong>{{ m.name }}</strong>
          <span v-if="m.location" class="rd-order-loc">{{ m.location }}</span>
          <span v-if="m.automatic" class="mod-badge rd-tag">{{ t('readings.rounds.meters.automatic') }}</span>
          <span v-if="m.unavailable" class="mod-badge rd-tag is-unavailable">{{ t('readings.rounds.meters.unavailable') }}</span>
        </span>
        <span class="rd-order-actions">
          <button
            :id="btnId('up', m.id)"
            type="button"
            class="edit-btn is-small rd-order-btn"
            :disabled="disabled || i === 0"
            :aria-label="t('readings.rounds.meters.up', { name: m.name })"
            :title="t('readings.rounds.meters.up', { name: m.name })"
            @click="move(i, i - 1, 'up')"
          >
            ↑
          </button>
          <button
            :id="btnId('down', m.id)"
            type="button"
            class="edit-btn is-small rd-order-btn"
            :disabled="disabled || i === items.length - 1"
            :aria-label="t('readings.rounds.meters.down', { name: m.name })"
            :title="t('readings.rounds.meters.down', { name: m.name })"
            @click="move(i, i + 1, 'down')"
          >
            ↓
          </button>
          <button
            :id="btnId('remove', m.id)"
            type="button"
            class="edit-btn is-small is-danger rd-order-btn"
            :disabled="disabled"
            :aria-label="t('readings.rounds.meters.remove', { name: m.name })"
            :title="t('readings.rounds.meters.remove', { name: m.name })"
            @click="remove(i)"
          >
            ×
          </button>
        </span>
      </li>
    </ol>
    <p v-if="error" :id="errId" class="iso-field-error">{{ error }}</p>

    <div class="edit-field rd-meter-add">
      <label :for="idPrefix + '-search'">{{ t('readings.rounds.meters.add') }}</label>
      <p v-if="loading" class="muted" role="status">{{ t('readings.rounds.meters.loading') }}</p>
      <template v-else>
        <div class="rd-meter-add-row">
          <input
            :id="idPrefix + '-search'"
            v-model="text"
            type="search"
            autocomplete="off"
            :placeholder="t('readings.rounds.meters.search')"
            :disabled="disabled"
            @keydown.enter.prevent
          />
          <button type="button" class="edit-btn rd-meter-addall" :disabled="disabled || full || !matches.length" @click="addAll">
            {{ t('readings.rounds.meters.addAll', { n: matches.length }) }}
          </button>
        </div>
        <ul v-if="matches.length" class="rd-meter-matches" :aria-label="t('readings.rounds.meters.matches')">
          <li v-for="m in matches" :key="m.id">
            <button type="button" class="rd-meter-match" :disabled="disabled || full" :aria-label="t('readings.rounds.meters.addOne', { name: m.name })" @click="add(m)">
              <span aria-hidden="true">+</span>
              <MediumIcon :medium="m.medium" />
              <span class="rd-order-name">
                <strong>{{ m.name }}</strong>
                <span v-if="m.location" class="rd-order-loc">{{ m.location }}</span>
                <span v-if="m.automatic" class="mod-badge rd-tag">{{ t('readings.rounds.meters.automatic') }}</span>
              </span>
            </button>
          </li>
        </ul>
        <p v-else class="muted rd-meter-none">{{ noneText }}</p>
        <p v-if="full" class="edit-hint">{{ t('readings.rounds.meters.full', { max: ROUND_LIMITS.meters }) }}</p>
      </template>
    </div>
    <p class="visually-hidden" role="status">{{ status }}</p>
  </div>
</template>
