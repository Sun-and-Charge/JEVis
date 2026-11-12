<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import NewDashboardButton from '../components/dashboard/edit/NewDashboardButton.vue'
import ItemOverview from '../components/nav/ItemOverview.vue'

/* Overview of all dashboards with "Neues Dashboard" (E4) in the header. */
</script>

<template>
  <ItemOverview kind="dashboard">
    <template #actions>
      <NewDashboardButton />
    </template>
  </ItemOverview>
</template>
