<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Recipients } from '../../api/modules'

const props = defineProps<{ recipients: Recipients | null }>()
const { t } = useI18n()

/** visible names, plus the number of recipients the user may not read */
const text = computed(() => {
  const r = props.recipients
  if (!r || r.count <= 0) return t('modules.noRecipients')
  const names = r.names ?? []
  const hidden = Math.max(0, r.count - names.length)
  const parts = names.length ? [names.join(', ')] : []
  if (hidden) parts.push(names.length ? '+ ' + t('modules.recipientsHidden', hidden) : t('modules.recipientsHidden', hidden))
  return parts.join(' ')
})
</script>

<template>
  <span>{{ text }}</span>
</template>
