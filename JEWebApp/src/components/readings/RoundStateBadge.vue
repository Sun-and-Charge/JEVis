<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { roundStateClass } from '../../readings/rounds'

/* State of a reading round: Erledigt green, Offen neutral, Verspätet amber, Eskaliert red, Nicht gestartet / Inaktiv grey. */

const props = defineProps<{ state: string }>()
const { t, te } = useI18n()

const cls = computed(() => roundStateClass(props.state))
const label = computed(() => (te('readings.rounds.state.' + props.state) ? t('readings.rounds.state.' + props.state) : props.state))
</script>

<template>
  <span class="mod-badge rd-round-state" :class="cls">{{ label }}</span>
</template>
