<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { MaintenanceEntry, MaintenanceStatus } from '../../api/modules'
import { formatLocal } from '../../modules/dates'
import RecipientsText from './RecipientsText.vue'
import '../../styles/edit.css'

defineProps<{ entry: MaintenanceEntry; busy?: boolean }>()
/* E1: status change (the view sends it) */
const emit = defineEmits<{ status: [status: MaintenanceStatus] }>()
const STATUSES: readonly MaintenanceStatus[] = ['PLANNED', 'IN_PROGRESS', 'DONE']
const { t, locale } = useI18n()
</script>

<template>
  <div v-if="entry.canWrite && entry.version && entry.status" class="edit-actions mt-status" role="group" :aria-label="t('moduleEdit.statusLabel', { title: entry.title })">
    <span class="muted">{{ t('moduleEdit.status') }}:</span>
    <button
      v-for="s in STATUSES"
      :key="s"
      type="button"
      class="edit-btn mt-status-btn"
      :aria-pressed="entry.status === s"
      :disabled="busy || entry.status === s"
      @click="emit('status', s)"
    >
      {{ t('moduleEdit.statusOption.' + s) }}
    </button>
  </div>
  <dl class="mod-dl">
    <dt>{{ t('modules.description') }}</dt>
    <dd>{{ entry.description || t('modules.none') }}</dd>
    <dt>{{ t('modules.recipients') }}</dt>
    <dd><RecipientsText :recipients="entry.recipients" /></dd>
    <template v-if="entry.informRequested">
      <dt>{{ t('maintenance.informRequested') }}</dt>
      <dd>{{ formatLocal(entry.informRequested, locale) }}</dd>
    </template>
    <template v-if="entry.lastMail">
      <dt>{{ t('modules.lastMail') }}</dt>
      <dd>{{ formatLocal(entry.lastMail, locale) }}</dd>
    </template>
    <template v-if="entry.doneAt">
      <dt>{{ t('maintenance.doneAt') }}</dt>
      <dd>{{ formatLocal(entry.doneAt, locale) }}</dd>
    </template>
    <dt>{{ t('modules.created') }}</dt>
    <dd>
      {{
        entry.created && entry.createdBy
          ? t('modules.createdBy', { date: formatLocal(entry.created, locale), user: entry.createdBy })
          : formatLocal(entry.created, locale) || entry.createdBy || t('modules.none')
      }}
    </dd>
    <template v-if="entry.directory">
      <dt>{{ t('modules.directory') }}</dt>
      <dd>{{ entry.directory.name }}</dd>
    </template>
  </dl>
</template>
