<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { catalog, recentEntries, type CatalogEntry } from '../../nav/catalog'
import { groupOfRoute, visibleModules } from '../../nav/menu'
import { paletteKey, paletteSearch, type PaletteItem } from '../../nav/palette'
import { useNavigationStore } from '../../stores/navigation'
import { useNavPrefsStore } from '../../stores/navPrefs'
import NavIcon from './NavIcon.vue'

/*
 * Search (Strg+K, "/"): a modal dialog with a combobox over pages, modules, dashboards and analyses of the
 * navigation data. ↑/↓ move through the hits (the focus stays in the input, aria-activedescendant marks the hit),
 * Enter or a click opens, Esc or a click beside the dialog closes; the focus returns to where it was.
 */

const emit = defineEmits<{ close: [] }>()
const { t, locale } = useI18n()
const nav = useNavigationStore()
const prefs = useNavPrefsStore()
const router = useRouter()

const query = ref('')
const active = ref(0)
const input = ref<HTMLInputElement | null>(null)
const list = ref<HTMLElement | null>(null)
let opener: HTMLElement | null = null

const entryItem = (e: CatalogEntry): PaletteItem => ({
  id: e.key,
  kind: e.kind,
  label: e.name,
  detail: e.sitePath ?? t('overview.other'),
  route: { name: e.kind, params: { id: e.id } },
})

const dashboards = computed(() => catalog(nav.data, 'dashboard', locale.value))
const analyses = computed(() => catalog(nav.data, 'analysis', locale.value))

const pages = computed<PaletteItem[]>(() => {
  const list: PaletteItem[] = [{ id: 'p:home', kind: 'page', label: t('nav.start'), detail: null, route: { name: 'home' } }]
  if (dashboards.value.length) list.push({ id: 'p:dashboards', kind: 'page', label: t('nav.dashboards'), detail: null, route: { name: 'dashboards' } })
  if (analyses.value.length || nav.creatableDirectories.length) list.push({ id: 'p:analyses', kind: 'page', label: t('nav.analyses'), detail: null, route: { name: 'analyses' } })
  return list
})

const modules = computed<PaletteItem[]>(() =>
  visibleModules(nav.modules).map((m) => {
    const group = groupOfRoute(m.route)
    return { id: 'm:' + m.key, kind: 'module', label: t('modules.' + m.key), detail: group ? t('nav.groups.' + group) : null, route: { name: m.route } }
  }),
)

const items = computed(() => [...pages.value, ...modules.value, ...dashboards.value.map(entryItem), ...analyses.value.map(entryItem)])
/* without a search: recently opened, then the pages and modules */
const initial = computed(() => [...recentEntries([...dashboards.value, ...analyses.value], prefs.recent).map(entryItem), ...pages.value, ...modules.value])
const results = computed(() => paletteSearch(items.value, query.value, locale.value, initial.value))

watch(results, (list) => {
  active.value = list.length ? 0 : -1
})

const optionId = (i: number) => 'palette-opt-' + i
const kindLabel = (item: PaletteItem) => t('palette.kind.' + item.kind)
const icon = (item: PaletteItem) => (item.kind === 'dashboard' ? 'dashboard' : item.kind === 'analysis' ? 'analysis' : item.kind === 'page' ? 'home' : 'chevron')

onMounted(async () => {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  await nextTick()
  input.value?.focus()
})

onBeforeUnmount(() => {
  if (opener && document.contains(opener)) opener.focus()
})

function close() {
  emit('close')
}

async function open(item: PaletteItem | undefined) {
  if (!item) return
  emit('close')
  await router.push(item.route)
}

function scrollToActive() {
  const el = list.value?.querySelector<HTMLElement>('#' + optionId(active.value))
  if (el && typeof el.scrollIntoView === 'function') el.scrollIntoView({ block: 'nearest' })
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Tab') {
    /* the input is the only stop in the dialog */
    e.preventDefault()
    return
  }
  const action = paletteKey(e.key, active.value, results.value.length)
  if (!action) return
  e.preventDefault()
  e.stopPropagation()
  if (action.type === 'close') close()
  else if (action.type === 'open') void open(results.value[action.index])
  else {
    active.value = action.index
    void nextTick(scrollToActive)
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="pal-backdrop" @click.self="close">
      <div class="pal" role="dialog" aria-modal="true" aria-labelledby="palette-title" @keydown="onKeydown">
        <h2 id="palette-title" class="visually-hidden">{{ t('palette.title') }}</h2>
        <div class="pal-head">
          <NavIcon name="search" />
          <input
            ref="input"
            v-model="query"
            type="text"
            class="pal-input"
            role="combobox"
            aria-autocomplete="list"
            :aria-expanded="results.length > 0"
            aria-controls="palette-list"
            :aria-activedescendant="active >= 0 && results.length ? optionId(active) : undefined"
            :aria-label="t('palette.label')"
            :placeholder="t('palette.placeholder')"
            autocomplete="off"
            spellcheck="false"
          />
          <kbd class="pal-esc" aria-hidden="true">Esc</kbd>
        </div>
        <ul v-show="results.length" id="palette-list" ref="list" class="pal-list" role="listbox" :aria-label="t('palette.results')">
          <li
            v-for="(r, i) in results"
            :id="optionId(i)"
            :key="r.id"
            role="option"
            class="pal-option"
            :class="{ active: i === active }"
            :aria-selected="i === active"
            @click="open(r)"
            @mousemove="active = i"
          >
            <span class="pal-icon"><NavIcon :name="icon(r)" /></span>
            <span class="pal-text">
              <span class="pal-label">{{ r.label }}</span>
              <span v-if="r.detail" class="pal-detail">{{ r.detail }}</span>
            </span>
            <span class="pal-kind">{{ kindLabel(r) }}</span>
          </li>
        </ul>
        <p v-if="!results.length" class="pal-empty">{{ t('palette.empty') }}</p>
        <p class="visually-hidden" role="status" aria-live="polite">{{ t('palette.count', results.length) }}</p>
        <p class="pal-hint" aria-hidden="true">{{ t('palette.hint') }}</p>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.pal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 10vh 1rem 1rem;
  background: rgba(26, 42, 69, 0.45);
}

.pal {
  display: flex;
  flex-direction: column;
  width: min(640px, 100%);
  max-height: 75vh;
  background: var(--surface);
  color: var(--ink);
  border-radius: var(--radius);
  border-top: 3px solid var(--sun);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.28);
  overflow: hidden;
}

.pal-head {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.6rem 0.9rem;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
}

.pal-input {
  flex: 1;
  min-width: 0;
  padding: 0.45rem 0;
  font: inherit;
  font-size: 1.05rem;
  color: var(--ink);
  background: transparent;
  border: none;
}

.pal-input:focus-visible {
  outline: none;
}

.pal-head:focus-within {
  box-shadow: inset 0 -2px 0 var(--sun);
}

kbd {
  font: inherit;
  font-size: 0.75rem;
  padding: 0.1rem 0.4rem;
  border: 1px solid var(--line);
  border-radius: 4px;
  color: var(--muted);
}

.pal-list {
  list-style: none;
  margin: 0;
  padding: 0.35rem;
  overflow-y: auto;
}

.pal-option {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.5rem 0.6rem;
  border-radius: 4px;
  cursor: pointer;
}

.pal-option.active {
  background: var(--blueberry);
  color: #fff;
}

.pal-icon {
  display: inline-flex;
  color: var(--muted);
}

.pal-option.active .pal-icon {
  color: var(--sun);
}

.pal-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.pal-label {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pal-detail {
  font-size: 0.82rem;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pal-option.active .pal-detail {
  color: rgba(255, 255, 255, 0.8);
}

.pal-kind {
  font-size: 0.75rem;
  color: var(--muted);
  white-space: nowrap;
}

.pal-option.active .pal-kind {
  color: rgba(255, 255, 255, 0.8);
}

.pal-empty {
  margin: 0;
  padding: 1rem;
  color: var(--muted);
}

.pal-hint {
  margin: 0;
  padding: 0.45rem 0.9rem;
  font-size: 0.78rem;
  color: var(--muted);
  border-top: 1px solid var(--line);
  background: var(--bg);
}

@media (max-width: 800px) {
  .pal-backdrop {
    padding: 0.5rem;
  }

  .pal {
    max-height: calc(100vh - 1rem);
  }

  .pal-hint {
    display: none;
  }
}
</style>
