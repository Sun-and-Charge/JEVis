<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadRowTree, type RowTreeNode } from '../../../api/analysis'
import type { PickedRow } from '../../../analysis/editor'
import { useAuthStore } from '../../../stores/auth'
import EditDialog from '../../edit/EditDialog.vue'
import '../../../styles/analysisEdit.css'

/*
 * Row picker of the analysis editor (E3, as the Control Center's TreeSelectionDialog): the tree of GET
 * /web/rows/tree, loaded level by level, and a search by name (from 2 characters). Selectable rows (data classes)
 * get a checkbox and, with more than one attribute, an attribute choice; rows already in the chart are disabled.
 * `single` (E5: data row of a meter / target attribute): radio buttons, one row, emitted as a list of one.
 */

const props = defineProps<{
  /** "objectId|attribute" of the rows already in the chart */
  present: readonly string[]
  /** single-select mode: one row is chosen */
  single?: boolean
  /** dialog title (default: "Datenreihen hinzufügen") */
  title?: string
}>()
const emit = defineEmits<{ close: []; add: [rows: PickedRow[]] }>()
const { t, locale } = useI18n()
const auth = useAuthStore()

const uid = `rp-${Math.random().toString(36).slice(2, 9)}`
const SEARCH_DELAY = 300
const MAX_HITS = 200

type Children = RowTreeNode[] | 'loading' | 'error'
const roots = ref<Children>('loading')
const children = reactive<Record<number, Children>>({})
const expanded = ref<Set<number>>(new Set())

const query = ref('')
const hits = ref<Children | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | null = null
let searchSeq = 0

/** selected rows: key "objectId" -> node and attribute */
const selected = ref<Map<number, PickedRow>>(new Map())

async function loadRoots() {
  roots.value = 'loading'
  try {
    const list = await loadRowTree(auth.http, locale.value)
    roots.value = Array.isArray(list) ? list : []
  } catch {
    roots.value = 'error'
  }
}

async function loadChildren(node: RowTreeNode) {
  children[node.id] = 'loading'
  try {
    const list = await loadRowTree(auth.http, locale.value, node.id)
    children[node.id] = Array.isArray(list) ? list : []
  } catch {
    children[node.id] = 'error'
  }
}

function toggle(node: RowTreeNode) {
  const next = new Set(expanded.value)
  if (next.has(node.id)) next.delete(node.id)
  else {
    next.add(node.id)
    const known = children[node.id]
    if (!Array.isArray(known) && known !== 'loading') void loadChildren(node)
  }
  expanded.value = next
}

watch(query, (q) => {
  if (searchTimer) clearTimeout(searchTimer)
  const text = q.trim()
  if (text.length < 2) {
    searchSeq++
    hits.value = null
    return
  }
  searchTimer = setTimeout(async () => {
    const seq = ++searchSeq
    hits.value = 'loading'
    try {
      const list = await loadRowTree(auth.http, locale.value, null, text)
      if (seq === searchSeq) hits.value = Array.isArray(list) ? list : []
    } catch {
      if (seq === searchSeq) hits.value = 'error'
    }
  }, SEARCH_DELAY)
})

onMounted(loadRoots)
onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

const attributesOf = (node: RowTreeNode) => (node.attributes && node.attributes.length ? node.attributes : ['Value'])
const attributeOf = (node: RowTreeNode) => selected.value.get(node.id)?.attribute ?? attributesOf(node)[0]
const isPresent = (node: RowTreeNode, attribute = attributeOf(node)) => props.present.includes(`${node.id}|${attribute}`)

function choose(node: RowTreeNode, checked: boolean) {
  const next = props.single ? new Map<number, PickedRow>() : new Map(selected.value)
  if (checked) next.set(node.id, { node, attribute: attributeOf(node) })
  else next.delete(node.id)
  selected.value = next
}

function setAttribute(node: RowTreeNode, attribute: string) {
  const next = new Map(selected.value)
  const current = next.get(node.id)
  if (current) next.set(node.id, { node, attribute })
  else if (!isPresent(node, attribute)) {
    if (props.single) next.clear()
    next.set(node.id, { node, attribute })
  }
  selected.value = next
}

const picked = computed(() => [...selected.value.values()].filter((p) => !isPresent(p.node, p.attribute)))

function submit() {
  if (picked.value.length) emit('add', props.single ? picked.value.slice(0, 1) : picked.value)
}

const inputType = computed(() => (props.single ? 'radio' : 'checkbox'))
const dialogTitle = computed(() => props.title || t('analysisEdit.picker.title'))
const submitLabel = computed(() => (props.single ? t('analysisEdit.picker.choose') : t('analysisEdit.picker.add', { n: picked.value.length })))

/* flat list of the shown tree (keyboard and screen readers get a plain list with levels) */
interface Line {
  node: RowTreeNode
  level: number
}
const lines = computed<Line[]>(() => {
  const out: Line[] = []
  const walk = (list: Children, level: number) => {
    if (!Array.isArray(list)) return
    for (const node of list) {
      out.push({ node, level })
      if (expanded.value.has(node.id)) walk(children[node.id] ?? [], level + 1)
    }
  }
  walk(roots.value, 0)
  return out
})
const statusOf = (node: RowTreeNode) => (expanded.value.has(node.id) ? children[node.id] : undefined)
</script>

<template>
  <EditDialog :title="dialogTitle" wide @close="emit('close')">
    <div class="edit-field rp-search">
      <label :for="uid + '-q'">{{ t('analysisEdit.picker.search') }}</label>
      <input :id="uid + '-q'" v-model="query" type="search" autocomplete="off" :aria-describedby="uid + '-qh'" autofocus />
      <p :id="uid + '-qh'" class="edit-hint">{{ t('analysisEdit.picker.searchHint') }}</p>
    </div>

    <div class="rp-scroll">
      <!-- search hits -->
      <template v-if="hits !== null">
        <p v-if="hits === 'loading'" class="rp-status" role="status">{{ t('analysisEdit.picker.loading') }}</p>
        <p v-else-if="hits === 'error'" class="rp-status ae-error" role="alert">{{ t('analysisEdit.picker.error') }}</p>
        <p v-else-if="!hits.length" class="rp-status">{{ t('analysisEdit.picker.noHits') }}</p>
        <ul v-else class="rp-list rp-hits">
          <li v-for="node in hits" :key="'h' + node.id">
            <div class="rp-node">
              <label v-if="node.selectable">
                <input
                  :type="inputType"
                  :name="single ? uid + '-pick' : undefined"
                  :checked="selected.has(node.id) || isPresent(node)"
                  :disabled="isPresent(node)"
                  @change="choose(node, ($event.target as HTMLInputElement).checked)"
                />
                <span>
                  <span class="rp-name">{{ node.name }}</span>
                  <span v-if="node.unit" class="rp-meta"> [{{ node.unit }}]</span>
                  <span v-if="isPresent(node)" class="rp-meta"> – {{ t('analysisEdit.picker.present') }}</span>
                  <span v-if="node.path" class="rp-path">{{ node.path }}</span>
                </span>
              </label>
              <span v-else class="rp-dir">
                {{ node.name }}
                <span v-if="node.path" class="rp-path">{{ node.path }}</span>
              </span>
              <select
                v-if="node.selectable && attributesOf(node).length > 1"
                :value="attributeOf(node)"
                :aria-label="t('analysisEdit.picker.attribute', { name: node.name })"
                @change="setAttribute(node, ($event.target as HTMLSelectElement).value)"
              >
                <option v-for="a in attributesOf(node)" :key="a" :value="a">{{ a }}</option>
              </select>
            </div>
          </li>
        </ul>
        <p v-if="Array.isArray(hits) && hits.length >= MAX_HITS" class="rp-status">{{ t('analysisEdit.picker.tooMany') }}</p>
      </template>

      <!-- tree -->
      <template v-else>
        <p v-if="roots === 'loading'" class="rp-status" role="status">{{ t('analysisEdit.picker.loading') }}</p>
        <p v-else-if="roots === 'error'" class="rp-status ae-error" role="alert">
          {{ t('analysisEdit.picker.error') }}
          <button type="button" class="btn-link" @click="loadRoots">{{ t('analysis.retry') }}</button>
        </p>
        <p v-else-if="!roots.length" class="rp-status">{{ t('analysisEdit.picker.empty') }}</p>
        <ul v-else class="rp-list rp-tree">
          <li v-for="{ node, level } in lines" :key="'n' + node.id + '-' + level" :style="{ paddingLeft: level * 1.1 + 'rem' }">
            <div class="rp-node">
              <button
                v-if="node.hasChildren"
                type="button"
                class="ae-icon-btn rp-expand"
                :aria-expanded="expanded.has(node.id)"
                :aria-label="t(expanded.has(node.id) ? 'analysisEdit.picker.collapse' : 'analysisEdit.picker.expand', { name: node.name })"
                @click="toggle(node)"
              >
                <span :style="{ display: 'inline-block', transform: expanded.has(node.id) ? 'rotate(90deg)' : 'none' }" aria-hidden="true">›</span>
              </button>
              <span v-else class="rp-spacer" aria-hidden="true" />
              <label v-if="node.selectable">
                <input
                  :type="inputType"
                  :name="single ? uid + '-pick' : undefined"
                  :checked="selected.has(node.id) || isPresent(node)"
                  :disabled="isPresent(node)"
                  @change="choose(node, ($event.target as HTMLInputElement).checked)"
                />
                <span>
                  <span class="rp-name">{{ node.name }}</span>
                  <span v-if="node.unit" class="rp-meta"> [{{ node.unit }}]</span>
                  <span v-if="isPresent(node)" class="rp-meta"> – {{ t('analysisEdit.picker.present') }}</span>
                </span>
              </label>
              <span v-else class="rp-dir">{{ node.name }}</span>
              <select
                v-if="node.selectable && attributesOf(node).length > 1"
                :value="attributeOf(node)"
                :aria-label="t('analysisEdit.picker.attribute', { name: node.name })"
                @change="setAttribute(node, ($event.target as HTMLSelectElement).value)"
              >
                <option v-for="a in attributesOf(node)" :key="a" :value="a">{{ a }}</option>
              </select>
            </div>
            <p v-if="statusOf(node) === 'loading'" class="rp-status" role="status" :style="{ paddingLeft: '2rem' }">{{ t('analysisEdit.picker.loading') }}</p>
            <p v-else-if="statusOf(node) === 'error'" class="rp-status ae-error" :style="{ paddingLeft: '2rem' }">{{ t('analysisEdit.picker.error') }}</p>
            <p v-else-if="Array.isArray(statusOf(node)) && !(statusOf(node) as RowTreeNode[]).length" class="rp-status" :style="{ paddingLeft: '2rem' }">
              {{ t('analysisEdit.picker.empty') }}
            </p>
          </li>
        </ul>
      </template>
    </div>
    <p class="rp-status" role="status">{{ t('analysisEdit.picker.selected', { n: picked.length }) }}</p>

    <template #footer>
      <button type="button" class="edit-btn" @click="emit('close')">{{ t('edit.cancel') }}</button>
      <button type="button" class="btn btn-primary edit-submit rp-submit" :disabled="!picked.length" @click="submit">
        {{ submitLabel }}
      </button>
    </template>
  </EditDialog>
</template>
