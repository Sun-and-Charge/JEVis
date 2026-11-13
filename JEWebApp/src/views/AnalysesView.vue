<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import NewAnalysisButton from '../components/analysis/edit/NewAnalysisButton.vue'
import ItemOverview from '../components/nav/ItemOverview.vue'
import RecycleBinLink from '../components/recycle/RecycleBinLink.vue'

/* Overview of all analyses with "Neue Analyse" (E3) in the header. */
</script>

<template>
  <ItemOverview kind="analysis">
    <template #actions>
      <RecycleBinLink kind="analyses" />
      <NewAnalysisButton />
    </template>
  </ItemOverview>
</template>
