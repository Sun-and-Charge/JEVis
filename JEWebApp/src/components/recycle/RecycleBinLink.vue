<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { RecycleKind } from '../../api/recycle'
import '../../styles/edit.css'
import './recycle.css'

/*
 * "Papierkorb" in the header of the dashboards / analyses overview. Shown to every logged-in user: restoring to the
 * original place needs only the delete right on the item (no create right anywhere), so the create rights cannot
 * tell whether there is something to restore; the page itself has an empty state.
 */

defineProps<{ kind: RecycleKind }>()
const { t } = useI18n()
</script>

<template>
  <RouterLink :to="{ path: '/recycle', query: { kind } }" class="edit-btn rb-link">
    <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" focusable="false">
      <path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
    {{ t('recycle.link') }}
  </RouterLink>
</template>
