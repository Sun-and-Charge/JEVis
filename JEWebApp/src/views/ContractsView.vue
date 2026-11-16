<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, reactive, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CONFIGURATION_FIELDS,
  CONFIGURATIONS_TAB,
  configurationValue,
  contractSites,
  contractTabs,
  contractWhere,
  defaultContractFilter,
  filterConfigurations,
  filterContractObjects,
  hasEntriesWithoutSite,
  type ContractFilter,
} from '../accounting/contracts'
import { anyContractWritable, canCreateIn, canEditObject } from '../accounting/contractEdit'
import { canCreateConfiguration, canEditConfiguration } from '../accounting/configurationEdit'
import { loadContracts, type AccountingConfiguration, type ConfigurationEdit, type ContractGroup, type ContractObject } from '../api/contracts'
import ConfigurationEditDialog from '../components/accounting/ConfigurationEditDialog.vue'
import ContractCard from '../components/accounting/ContractCard.vue'
import ContractCreateDialog from '../components/accounting/ContractCreateDialog.vue'
import ContractEditDialog from '../components/accounting/ContractEditDialog.vue'
import { useLoader } from '../iso/useLoader'
import type { SiteFilter } from '../modules/search'
import { useNarrow } from '../modules/useNarrow'
import { useAuthStore } from '../stores/auth'
import '../styles/modules.css'
import '../styles/iso.css'
import '../styles/notes.css'
import '../styles/accounting.css'

/*
 * Contracts (W4.7, view only) like the Control Center's Accounting Plugin ("Enter data" / "Configuration"): one tab
 * per group (suppliers, metering point operators, grid operators, contractors, dues) and the accounting
 * configurations; search and site filter over all tabs. The cost calculation follows later.
 * E6.2: "Neu" per group and "Bearbeiten" per object (side panel: name, contractor, texts, ranging tables, prices with
 * validity date, delete); the list is reloaded after every write.
 * E6.3: "Neu" and "Bearbeiten" of the accounting configurations (contract fields, template, inputs, delete).
 */

const { t, te, locale } = useI18n()
const auth = useAuthStore()
const narrow = useNarrow()

const { data, state, reload } = useLoader(() => loadContracts(auth.http, locale.value))
watch(locale, () => reload())

const filter = reactive<ContractFilter>(defaultContractFilter())
const tabs = computed(() => contractTabs(data.value))
const sites = computed(() => contractSites(data.value, locale.value))
const withoutSite = computed(() => hasEntriesWithoutSite(data.value))

const shownTabs = computed(() =>
  tabs.value.map((tab) => ({
    id: tab.id,
    group: tab.group,
    total: tab.objects.length + tab.configurations.length,
    objects: filterContractObjects(tab.objects, filter, locale.value),
    configurations: filterConfigurations(tab.configurations, filter, locale.value),
  })),
)
const shownCount = (tab: { objects: unknown[]; configurations: unknown[] }) => tab.objects.length + tab.configurations.length

const activeId = ref<string | null>(null)
const active = computed(() => shownTabs.value.find((tab) => tab.id === activeId.value) ?? shownTabs.value[0] ?? null)
const highlighted = ref<number | null>(null)

/* after a load: keep the tab if it still exists */
watch(tabs, (list) => {
  if (!list.some((tab) => tab.id === activeId.value)) activeId.value = list[0]?.id ?? null
  if (typeof filter.site === 'number' && !sites.value.some((s) => s.id === filter.site)) filter.site = 'all'
})

const siteValue = computed({
  get: () => String(filter.site),
  set: (v: string) => {
    filter.site = (v === 'all' || v === 'none' ? v : Number(v)) as SiteFilter
  },
})

const tabLabel = (id: string) => (te('contracts.kind.' + id) ? t('contracts.kind.' + id) : id)
const tabDomId = (id: string) => `contracts-tab-${id}`
const panelId = 'contracts-panel'

function selectTab(id: string, focus = false) {
  activeId.value = id
  highlighted.value = null
  if (focus) nextTick(() => document.getElementById(tabDomId(id))?.focus())
}

/* arrow keys move between the tabs (WAI-ARIA tabs pattern, automatic activation) */
function onTabKey(e: KeyboardEvent, index: number) {
  const list = shownTabs.value
  let next = -1
  if (e.key === 'ArrowRight') next = (index + 1) % list.length
  else if (e.key === 'ArrowLeft') next = (index - 1 + list.length) % list.length
  else if (e.key === 'Home') next = 0
  else if (e.key === 'End') next = list.length - 1
  if (next < 0) return
  e.preventDefault()
  selectTab(list[next].id, true)
}

/** Contractor ids of the contractor tab (the jump target). */
const contractorIds = computed(() => new Set(tabs.value.find((tab) => tab.id === 'contractors')?.objects.map((o) => o.id) ?? []))

async function openContractor(id: number) {
  if (!contractorIds.value.has(id)) return
  const shown = shownTabs.value.find((tab) => tab.id === 'contractors')
  if (!shown?.objects.some((o) => o.id === id)) Object.assign(filter, defaultContractFilter())
  activeId.value = 'contractors'
  highlighted.value = id
  await nextTick()
  const el = document.getElementById('contract-obj-' + id)
  el?.scrollIntoView?.({ block: 'nearest' })
  el?.focus()
}

const energyName = (code: string) => (te('contracts.energy.' + code) ? t('contracts.energy.' + code) : code)
const configValue = (c: AccountingConfiguration, field: (typeof CONFIGURATION_FIELDS)[number]) => configurationValue(c, field, locale.value, energyName)
const configWhere = (c: AccountingConfiguration) => contractWhere(c, locale.value)

/* ---------- E6.2: editing ---------- */
const configurationCreatable = computed(() => canCreateConfiguration(data.value))
const writable = computed(
  () => anyContractWritable(data.value?.groups) || configurationCreatable.value || (data.value?.configurations ?? []).some(canEditConfiguration),
)
const message = ref<{ ok: boolean; text: string } | null>(null)
const creating = shallowRef<{ group: ContractGroup; kind: string } | null>(null)
/** the edited object: id and group kind; the object itself is taken from the (reloaded) list */
const editing = ref<{ id: number; kind: string } | null>(null)
let lastEdited: ContractObject | null = null

const editObject = computed<ContractObject | null>(() => {
  const e = editing.value
  if (!e) return null
  const found = (data.value?.groups ?? []).flatMap((g) => g.objects ?? []).find((o) => o.id === e.id)
  if (found) lastEdited = found
  return found ?? (lastEdited?.id === e.id ? lastEdited : null)
})

function openCreate(group: ContractGroup | undefined, kind: string) {
  if (!group || !canCreateIn(group)) return
  message.value = null
  creating.value = { group, kind }
}

function openEdit(o: ContractObject, kind: string) {
  if (!canEditObject(o)) return
  message.value = null
  lastEdited = o
  editing.value = { id: o.id, kind }
}

async function onCreated(o: ContractObject | null, name: string) {
  const kind = creating.value?.kind ?? null
  creating.value = null
  const created = { ok: true, text: t('registerEdit.created', { name }) }
  message.value = created
  await reload()
  if (!o || !kind) return
  activeId.value = kind
  highlighted.value = o.id
  /* as the Control Center: the new object opens in the editor to enter its data */
  const listed = (data.value?.groups ?? []).flatMap((g) => g.objects ?? []).find((x) => x.id === o.id)
  openEdit(listed ?? o, kind)
  message.value = created
}

async function onSaved(o: ContractObject | null, name: string) {
  editing.value = null
  message.value = { ok: true, text: t('registerEdit.saved', { name }) }
  await reload()
  if (o) highlighted.value = o.id
}

async function onDeleted(name: string) {
  editing.value = null
  message.value = { ok: true, text: t('registerEdit.deleted', { name }) }
  await reload()
}

const showableContractor = (id: number) => contractorIds.value.has(id)
const configEditable = computed(() => (data.value?.configurations ?? []).some(canEditConfiguration))

async function onEditorContractor(id: number) {
  editing.value = null
  await openContractor(id)
}

/* ---------- E6.3: accounting configurations ---------- */
/** the edited configuration: its id (null while a new one is not yet created) and a stable key of the panel */
const configEditing = ref<{ id: number | null; key: string } | null>(null)
const configTargets = computed(() => data.value?.configurationTargets ?? [])
const listedConfiguration = computed(() => {
  const id = configEditing.value?.id
  return id == null ? null : ((data.value?.configurations ?? []).find((c) => c.id === id) ?? null)
})

function openConfigCreate() {
  if (!configurationCreatable.value) return
  message.value = null
  configEditing.value = { id: null, key: 'cfg-new' }
}

function openConfigEdit(c: AccountingConfiguration) {
  if (!canEditConfiguration(c)) return
  message.value = null
  configEditing.value = { id: c.id, key: 'cfg-' + c.id }
}

async function onConfigSaved(m: ConfigurationEdit, name: string, created: boolean) {
  if (configEditing.value) configEditing.value = { ...configEditing.value, id: m.id }
  message.value = { ok: true, text: t(created ? 'registerEdit.created' : 'registerEdit.saved', { name }) }
  await reload()
  highlighted.value = m.id
}

async function onConfigDeleted(name: string) {
  configEditing.value = null
  message.value = { ok: true, text: t('registerEdit.deleted', { name }) }
  await reload()
}
</script>

<template>
  <section class="iso contracts" aria-labelledby="contracts-title">
    <div class="mod-head">
      <h1 id="contracts-title">{{ t('contracts.title') }}</h1>
    </div>
    <p v-if="writable" class="muted read-only">{{ t('contractEdit.hint') }}</p>
    <p v-else class="muted read-only">{{ t('contracts.readOnly') }}</p>
    <p v-if="message" class="alert edit-result ctr-message" :class="message.ok ? 'alert-ok' : 'alert-error'" :role="message.ok ? 'status' : 'alert'">
      {{ message.text }}
    </p>

    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('contracts.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <p v-if="data && !tabs.length" class="muted">{{ t('contracts.empty') }}</p>

    <template v-else-if="data && active">
      <fieldset class="mod-filters iso-filters">
        <legend class="visually-hidden">{{ t('modules.filters') }}</legend>
        <div class="control search">
          <label for="contracts-search">{{ t('modules.search') }}</label>
          <input id="contracts-search" v-model="filter.text" type="search" :placeholder="t('contracts.searchPlaceholder')" />
        </div>
        <div v-if="sites.length > 1 || (sites.length && withoutSite)" class="control">
          <label for="contracts-site">{{ t('modules.site') }}</label>
          <select id="contracts-site" v-model="siteValue">
            <option value="all">{{ t('modules.allSites') }}</option>
            <option v-for="s in sites" :key="s.id" :value="String(s.id)">{{ s.name }}</option>
            <option v-if="withoutSite" value="none">{{ t('modules.noSite') }}</option>
          </select>
        </div>
      </fieldset>

      <div class="ctr-tabs" role="tablist" :aria-label="t('contracts.tabsLabel')">
        <button
          v-for="(tab, i) in shownTabs"
          :id="tabDomId(tab.id)"
          :key="tab.id"
          type="button"
          role="tab"
          class="ctr-tab"
          :aria-selected="tab.id === active.id"
          :aria-controls="panelId"
          :tabindex="tab.id === active.id ? 0 : -1"
          @click="selectTab(tab.id)"
          @keydown="onTabKey($event, i)"
        >
          {{ tabLabel(tab.id) }}
          <span class="ctr-tab-count">
            <span aria-hidden="true">{{ shownCount(tab) === tab.total ? tab.total : `${shownCount(tab)}/${tab.total}` }}</span>
            <span class="visually-hidden">{{ t('modules.count', { shown: shownCount(tab), total: tab.total }) }}</span>
          </span>
        </button>
      </div>

      <div :id="panelId" class="ctr-panel" role="tabpanel" :aria-labelledby="tabDomId(active.id)" tabindex="0">
        <div class="ctr-panel-head">
          <p class="mod-count muted" role="status">{{ t('modules.count', { shown: shownCount(active), total: active.total }) }}</p>
          <button
            v-if="active.group && canCreateIn(active.group)"
            type="button"
            class="btn btn-primary ctr-new"
            :aria-label="t('contractEdit.newLabel', { group: tabLabel(active.id) })"
            @click="openCreate(active.group, active.id)"
          >
            {{ t('registerEdit.new') }}
          </button>
          <button
            v-else-if="active.id === CONFIGURATIONS_TAB && configurationCreatable"
            type="button"
            class="btn btn-primary ctr-new cfg-new"
            :aria-label="t('configEdit.newLabel')"
            @click="openConfigCreate"
          >
            {{ t('registerEdit.new') }}
          </button>
        </div>
        <p v-if="!active.total && (active.group || active.id === CONFIGURATIONS_TAB)" class="muted">{{ t('contractEdit.emptyGroup') }}</p>
        <p v-else-if="!shownCount(active)" class="muted">{{ t('contracts.noMatch') }}</p>

        <template v-else-if="active.id === CONFIGURATIONS_TAB">
          <p class="alert ctr-hint" role="note">{{ t('contracts.config.hint') }}</p>
          <ul v-if="narrow" class="mod-cards">
            <li v-for="c in active.configurations" :key="c.id" class="mod-card ctr-config-card" :class="{ 'is-highlighted': highlighted === c.id }">
              <p v-if="configWhere(c)" class="ctr-where muted">{{ configWhere(c) }}</p>
              <h2>{{ c.name }}</h2>
              <p v-if="canEditConfiguration(c)" class="ctr-class">
                <button type="button" class="edit-btn is-small cfg-edit" :aria-label="t('registerEdit.editLabel', { name: c.name })" @click="openConfigEdit(c)">
                  {{ t('registerEdit.edit') }}
                </button>
              </p>
              <dl class="mod-dl">
                <template v-for="f in CONFIGURATION_FIELDS" :key="f">
                  <dt>{{ t('contracts.config.' + f) }}</dt>
                  <dd>{{ configValue(c, f) || t('modules.none') }}</dd>
                </template>
              </dl>
            </li>
          </ul>
          <div v-else class="mod-table-wrap">
            <table class="mod-table iso-table ctr-config-table">
              <caption class="visually-hidden">{{ t('contracts.kind.configurations') }}</caption>
              <thead>
                <tr>
                  <th scope="col">{{ t('contracts.config.name') }}</th>
                  <th v-for="f in CONFIGURATION_FIELDS" :key="f" scope="col">{{ t('contracts.config.' + f) }}</th>
                  <th v-if="configEditable" scope="col"><span class="visually-hidden">{{ t('configEdit.actions') }}</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="c in active.configurations" :key="c.id" class="mod-row" :class="{ 'is-highlighted': highlighted === c.id }">
                  <th scope="row" class="iso-text ctr-config-name">
                    <span v-if="configWhere(c)" class="notes-path">{{ configWhere(c) }}</span>
                    <span>{{ c.name }}</span>
                  </th>
                  <td v-for="f in CONFIGURATION_FIELDS" :key="f" :class="{ nowrap: f !== 'marketLocation' }">{{ configValue(c, f) || t('modules.none') }}</td>
                  <td v-if="configEditable" class="nowrap">
                    <button
                      v-if="canEditConfiguration(c)"
                      type="button"
                      class="edit-btn is-small cfg-edit"
                      :aria-label="t('registerEdit.editLabel', { name: c.name })"
                      @click="openConfigEdit(c)"
                    >
                      {{ t('registerEdit.edit') }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>

        <ul v-else class="ctr-list">
          <li v-for="o in active.objects" :key="o.id">
            <ContractCard
              :object="o"
              :contractor-link="!!o.contractor && contractorIds.has(o.contractor.id)"
              :highlighted="highlighted === o.id"
              :editable="canEditObject(o)"
              @open-contractor="openContractor"
              @edit="openEdit($event, active.id)"
            />
          </li>
        </ul>
      </div>
    </template>

    <ContractCreateDialog
      v-if="creating"
      :key="'new-' + creating.kind"
      :group="creating.group"
      :group-label="tabLabel(creating.kind)"
      @close="creating = null"
      @created="onCreated"
    />
    <ContractEditDialog
      v-if="editing && editObject"
      :key="'edit-' + editing.id"
      :object="editObject"
      :group-label="tabLabel(editing.kind)"
      :can-show-contractor="showableContractor"
      @close="editing = null"
      @saved="onSaved"
      @changed="reload"
      @deleted="onDeleted"
      @open-contractor="onEditorContractor"
    />
    <ConfigurationEditDialog
      v-if="configEditing"
      :key="configEditing.key"
      :configuration-id="configEditing.id"
      :targets="configTargets"
      :listed-name="listedConfiguration?.name"
      :can-write="listedConfiguration ? listedConfiguration.canWrite === true : undefined"
      :can-delete="listedConfiguration?.canDelete === true"
      @close="configEditing = null"
      @saved="onConfigSaved"
      @changed="reload"
      @deleted="onConfigDeleted"
    />
  </section>
</template>
