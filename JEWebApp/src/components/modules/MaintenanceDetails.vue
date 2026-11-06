<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { MaintenanceEntry } from '../../api/modules'
import { formatLocal } from '../../modules/dates'
import RecipientsText from './RecipientsText.vue'

defineProps<{ entry: MaintenanceEntry }>()
const { t, locale } = useI18n()
</script>

<template>
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
