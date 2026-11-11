<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { attributeText, contractWhere, hasHistory, isRanging, rangingRows, sampleDate } from '../../accounting/contracts'
import type { ContractAttribute, ContractObject } from '../../api/contracts'
import AttributeHistory from './AttributeHistory.vue'

/*
 * One contract object: name, class, site / path, the linked contractor (a button that jumps to it when it is in the
 * contractor tab) and the attributes with their "valid from" date; ranging values as small table. Attributes with
 * more than one sample get a "Verlauf" toggle that loads the price history.
 */

const props = defineProps<{ object: ContractObject; contractorLink: boolean; highlighted?: boolean }>()
const emit = defineEmits<{ openContractor: [id: number] }>()
const { t, locale } = useI18n()

const where = computed(() => contractWhere(props.object, locale.value))
const openEnd = (from: string) => t('contracts.openEnd', { from })
const text = (a: ContractAttribute) => attributeText(a, locale.value, openEnd)
const rows = (a: ContractAttribute) => (isRanging(a.value) ? rangingRows(a.value, a.unit, locale.value) : [])
const since = (a: ContractAttribute) => (a.value == null || a.value === '' ? '' : sampleDate(a.timestamp, locale.value))

const open = reactive<Record<string, boolean>>({})
const toggle = (a: ContractAttribute) => {
  open[a.name] = !open[a.name]
}

const contractor = computed(() => props.object.contractor)
const contractorName = computed(() => {
  const c = contractor.value
  if (!c) return ''
  return c.readable && c.name ? c.name : t('contracts.contractorHidden')
})
const domId = (suffix: string) => `contract-${props.object.id}-${suffix}`
const attrId = (index: number) => domId('h' + index)
</script>

<template>
  <article :id="'contract-obj-' + object.id" class="mod-card ctr-card" :class="{ 'is-highlighted': highlighted }" tabindex="-1" :aria-labelledby="domId('title')">
    <p v-if="where" class="ctr-where muted">{{ where }}</p>
    <h2 :id="domId('title')" class="ctr-title">{{ object.name }}</h2>
    <p class="ctr-class"><span class="mod-badge">{{ object.classLabel || object.className }}</span></p>

    <dl class="ctr-attrs">
      <template v-if="contractor">
        <dt>{{ t('contracts.contractor') }}</dt>
        <dd>
          <button
            v-if="contractorLink && contractor.readable && contractor.name"
            type="button"
            class="btn-link ctr-contractor"
            :aria-label="t('contracts.showContractor', { name: contractorName })"
            @click="emit('openContractor', contractor.id)"
          >
            {{ contractorName }}
          </button>
          <span v-else :class="{ muted: !contractor.readable || !contractor.name }">{{ contractorName }}</span>
        </dd>
      </template>

      <template v-for="(a, i) in object.attributes" :key="a.name">
        <dt :id="domId('l' + i)">{{ a.label || a.name }}</dt>
        <dd class="ctr-value" :class="{ 'is-text': a.type === 'text' }">
          <div class="ctr-value-line">
            <template v-if="isRanging(a.value) && rows(a).length">
              <div class="iso-mini-wrap ctr-ranging-wrap">
                <table class="iso-mini ctr-ranging" :aria-labelledby="domId('l' + i)">
                  <thead>
                    <tr>
                      <th scope="col" class="num">{{ t('contracts.ranging.from') }}</th>
                      <th scope="col" class="num">{{ t('contracts.ranging.to') }}</th>
                      <th scope="col" class="num">{{ t('contracts.ranging.value') }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(r, k) in rows(a)" :key="k">
                      <td class="num">{{ r.from || t('modules.none') }}</td>
                      <td class="num">{{ r.to || t('contracts.ranging.open') }}</td>
                      <td class="num">{{ r.value || t('modules.none') }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>
            <span v-else class="ctr-text">{{ text(a) || t('modules.none') }}</span>
            <span v-if="since(a)" class="muted ctr-since">{{ t('contracts.validFrom', { date: since(a) }) }}</span>
            <button
              v-if="hasHistory(a)"
              type="button"
              class="notes-btn ctr-history-btn"
              :aria-expanded="!!open[a.name]"
              :aria-controls="attrId(i)"
              :aria-label="t('contracts.history.toggleLabel', { name: a.label || a.name })"
              @click="toggle(a)"
            >
              <span class="chevron" aria-hidden="true">›</span> {{ t('contracts.history.toggle') }}
            </button>
          </div>
          <AttributeHistory v-if="open[a.name]" :object-id="object.id" :attribute="a" :dom-id="attrId(i)" />
        </dd>
      </template>
    </dl>
    <p v-if="!object.attributes?.length && !contractor" class="muted">{{ t('contracts.noAttributes') }}</p>
  </article>
</template>
