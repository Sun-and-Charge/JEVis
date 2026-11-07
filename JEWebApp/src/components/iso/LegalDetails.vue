<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatIsoDate } from '../../iso/format'
import { relevanceState, safeLink, type LegalRow } from '../../iso/legal'
import IsoAttachments from './IsoAttachments.vue'

const props = defineProps<{ row: LegalRow }>()
const { t, locale } = useI18n()

const o = computed(() => props.row.item)
const sid = (name: string) => `lg-${o.value.id}-${name}`
const link = computed(() => safeLink(o.value.link))

const general = computed(() =>
  [
    ['iso.legal.col.description', o.value.description],
    ['iso.legal.col.importance', o.value.importance],
    ['iso.legal.col.designation', o.value.designation],
    ['iso.legal.col.category', o.value.category],
    ['iso.legal.col.scope', o.value.scope],
    ['iso.legal.col.issueDate', formatIsoDate(o.value.issueDate, locale.value)],
    ['iso.legal.col.currentVersionDate', formatIsoDate(o.value.currentVersionDate, locale.value)],
    ['iso.legal.col.examinationDate', formatIsoDate(o.value.examinationDate, locale.value)],
    ['iso.col.plan', props.row.plan.name],
  ] as const,
)
</script>

<template>
  <div class="iso-sections">
    <section class="iso-section" :aria-labelledby="sid('general')">
      <h3 :id="sid('general')">{{ t('iso.section.general') }}</h3>
      <dl class="mod-dl">
        <dt>{{ t('iso.legal.col.relevant') }}</dt>
        <dd>{{ t('iso.legal.relevance.' + relevanceState(o)) }}</dd>
        <dt>{{ t('iso.legal.col.link') }}</dt>
        <dd>
          <a v-if="link" :href="link" target="_blank" rel="noopener noreferrer">{{ o.link }}<span class="visually-hidden"> {{ t('iso.externalLink') }}</span></a>
          <template v-else>{{ t('modules.none') }}</template>
        </dd>
        <template v-for="[label, value] in general" :key="label">
          <dt>{{ t(label) }}</dt>
          <dd>{{ value || t('modules.none') }}</dd>
        </template>
      </dl>
    </section>

    <section class="iso-section" :aria-labelledby="sid('files')">
      <h3 :id="sid('files')">{{ t('iso.section.attachments') }}</h3>
      <IsoAttachments :item-id="o.id" :count="o.attachments" />
    </section>
  </div>
</template>
