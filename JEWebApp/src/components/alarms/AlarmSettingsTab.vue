<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { filterAlarmConfigs, recipientCount, scopeNeedsObjects } from '../../alarms/settings'
import { loadAlarmSettings, type AlarmConfigSettings } from '../../api/alarmSettings'
import { useLoader } from '../../iso/useLoader'
import { useAuthStore } from '../../stores/auth'
import AlarmConfigDialog from './AlarmConfigDialog.vue'
import '../../styles/alarmSettings.css'

/*
 * Tab "Einstellungen" of the alarms page (E7.1): the alarm configurations as cards (name, site, active, scope with the
 * number of rows, period, number of mail recipients, a warning for several notifications), a search, "Neu" (when a
 * directory allows it) and "Bearbeiten" / "Anzeigen" in a side panel.
 */

const { t, te, locale } = useI18n()
const auth = useAuthStore()

const { data, state, reload } = useLoader(() => loadAlarmSettings(auth.http, locale.value))
watch(locale, () => reload())

const text = ref('')
const all = computed<AlarmConfigSettings[]>(() => (Array.isArray(data.value?.configurations) ? data.value!.configurations : []))
const shown = computed(() => filterAlarmConfigs(all.value, text.value, 'all', locale.value))
const targets = computed(() => (Array.isArray(data.value?.targets) ? data.value!.targets : []))
const message = ref<{ ok: boolean; text: string } | null>(null)

/** the open panel: a configuration, 'new' or none; `key` remounts the panel for another configuration */
const editing = shallowRef<{ config: AlarmConfigSettings | null; key: string } | null>(null)

function openEdit(c: AlarmConfigSettings) {
  message.value = null
  editing.value = { config: c, key: 'c' + c.id }
}

function openNew() {
  message.value = null
  editing.value = { config: null, key: 'new' }
}

function onSaved(c: AlarmConfigSettings, created: boolean) {
  message.value = { ok: true, text: t(created ? 'registerEdit.created' : 'registerEdit.saved', { name: c.name }) }
  void reload()
}

function onDeleted(name: string) {
  editing.value = null
  message.value = { ok: true, text: t('registerEdit.deleted', { name }) }
  void reload()
}

const periodName = (p: string) => (te('alarmSettings.period.' + p) ? t('alarmSettings.period.' + p) : p)
const scopeText = (c: AlarmConfigSettings) => {
  const name = te('alarmSettings.scope.' + c.scope) ? t('alarmSettings.scope.' + c.scope) : c.scope
  return scopeNeedsObjects(c.scope) ? `${name} (${t('alarmSettings.rowCount', c.objects.length)})` : name
}
const recipientsText = (c: AlarmConfigSettings) => {
  if (c.multipleNotifications) return t('alarmSettings.list.multiple')
  if (!c.notification) return t('alarmSettings.list.noNotification')
  const n = t('alarmSettings.recipientCount', recipientCount(c))
  return c.notification.enabled ? n : `${n} – ${t('alarmSettings.list.mailOff')}`
}
const editLabel = (c: AlarmConfigSettings) => t(c.canWrite ? 'registerEdit.editLabel' : 'alarmSettings.showLabel', { name: c.name })
</script>

<template>
  <div class="alm-settings">
    <p class="muted alm-settings-hint">{{ t('alarmSettings.hint.tab') }}</p>
    <p v-if="message" class="alert edit-result" :class="message.ok ? 'alert-ok' : 'alert-error'" role="status">{{ message.text }}</p>

    <p v-if="state === 'loading' && !data" class="muted" role="status">{{ t('alarmSettings.loading') }}</p>
    <div v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('modules.loadError') }}
      <button type="button" class="btn-link retry" @click="reload">{{ t('modules.retry') }}</button>
    </div>

    <template v-if="data">
      <div class="alm-cfg-head">
        <div class="control search">
          <label for="alm-cfg-search" class="visually-hidden">{{ t('modules.search') }}</label>
          <input id="alm-cfg-search" v-model="text" type="search" :placeholder="t('alarmSettings.searchPlaceholder')" />
        </div>
        <p class="mod-count muted" role="status">{{ t('modules.count', { shown: shown.length, total: all.length }) }}</p>
        <button v-if="targets.length" type="button" class="btn btn-primary alm-cfg-new" @click="openNew">{{ t('registerEdit.new') }}</button>
      </div>

      <p v-if="!all.length" class="muted">{{ t('alarmSettings.empty') }}</p>
      <p v-else-if="!shown.length" class="muted">{{ t('alarmSettings.noMatch') }}</p>
      <ul v-else class="alm-cfg-list">
        <li v-for="c in shown" :key="c.id" class="alm-cfg-card" :class="{ 'is-inactive': !c.enabled }">
          <h2>{{ c.name }}</h2>
          <p v-if="c.site || c.path" class="alm-cfg-path">{{ [c.site?.name, c.path].filter(Boolean).join(' · ') }}</p>
          <div class="alm-badges">
            <span class="mod-badge" :class="c.enabled ? 'is-done' : 'is-unknown'">{{ c.enabled ? t('alarmSettings.list.active') : t('alarmSettings.list.inactive') }}</span>
            <span v-if="c.notification?.enabled && !c.multipleNotifications" class="mod-badge">{{ t('alarmSettings.list.mailOn') }}</span>
          </div>
          <dl class="mod-dl">
            <dt>{{ t('alarmSettings.field.scope') }}</dt>
            <dd>{{ scopeText(c) }}</dd>
            <dt>{{ t('alarmSettings.field.period') }}</dt>
            <dd>{{ periodName(c.period) }}</dd>
            <dt>{{ t('alarmSettings.list.recipients') }}</dt>
            <dd>{{ recipientsText(c) }}</dd>
          </dl>
          <p v-if="c.multipleNotifications" class="alm-warning" role="note">{{ t('alarmSettings.multipleNotifications') }}</p>
          <div class="edit-actions">
            <button type="button" class="edit-btn alm-cfg-edit" :aria-label="editLabel(c)" @click="openEdit(c)">
              {{ c.canWrite ? t('registerEdit.edit') : t('alarmSettings.show') }}
            </button>
          </div>
        </li>
      </ul>
    </template>

    <AlarmConfigDialog
      v-if="editing"
      :key="editing.key"
      :config="editing.config"
      :targets="targets"
      :people="data?.people"
      @close="editing = null"
      @saved="onSaved"
      @changed="reload"
      @deleted="onDeleted"
    />
  </div>
</template>
