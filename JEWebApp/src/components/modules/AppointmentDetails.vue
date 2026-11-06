<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Appointment } from '../../api/modules'
import { formatLocal } from '../../modules/dates'
import RecipientsText from './RecipientsText.vue'

defineProps<{ entry: Appointment }>()
const { t, locale } = useI18n()
</script>

<template>
  <dl class="mod-dl">
    <dt>{{ t('modules.description') }}</dt>
    <dd>{{ entry.description || t('modules.none') }}</dd>
    <template v-if="entry.nextDue">
      <dt>{{ t('appointments.nextDue') }}</dt>
      <dd>{{ formatLocal(entry.nextDue, locale) }}</dd>
    </template>
    <template v-if="entry.lastDone">
      <dt>{{ t('appointments.lastDone') }}</dt>
      <dd>{{ formatLocal(entry.lastDone, locale) }}</dd>
    </template>
    <dt>{{ t('modules.recipients') }}</dt>
    <dd><RecipientsText :recipients="entry.recipients" /></dd>
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
