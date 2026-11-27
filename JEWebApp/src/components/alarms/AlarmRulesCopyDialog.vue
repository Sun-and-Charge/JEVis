<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { copyRowAlarmRules, type CopiedRow, type RuleCopyResult } from '../../api/alarmSettings'
import type { PickedRow } from '../../analysis/editor'
import { useBusy, writeErrorText } from '../../edit/edit'
import { useAuthStore } from '../../stores/auth'
import RowPickerDialog from '../analysis/edit/RowPickerDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import '../../styles/edit.css'
import '../../styles/alarmSettings.css'

/*
 * Copies the stored alarm rules of a row to other rows (R16, docs/WEB-API.md): the user picks the rows, chooses
 * whether their own rules stay (default) or are replaced, and sees which rows were changed and which were skipped.
 */

const props = defineProps<{ rowId: number; rowName: string; ruleCount: number }>()
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()

const uid = `arc-${Math.random().toString(36).slice(2, 9)}`
const targets = ref<{ id: number; name: string }[]>([])
const replace = ref(false)
const picking = ref(false)
const result = ref<RuleCopyResult | null>(null)
const error = ref('')

function added(rows: PickedRow[]) {
  picking.value = false
  for (const p of rows) {
    if (!targets.value.some((x) => x.id === p.node.id)) targets.value.push({ id: p.node.id, name: p.node.name })
  }
}

const remove = (id: number) => (targets.value = targets.value.filter((x) => x.id !== id))

const rowText = (row: CopiedRow | null, id: number) =>
  row ? [row.rawName, row.name].filter(Boolean).join(' / ') : t('alarmSettings.copy.unknownRow', { id })

const canSubmit = computed(() => targets.value.length > 0 && !busy.value && !result.value)

async function submit() {
  error.value = ''
  await run(async () => {
    try {
      result.value = await copyRowAlarmRules(auth.http, props.rowId, targets.value.map((x) => x.id), replace.value)
    } catch (e) {
      error.value = writeErrorText(e, t)
    }
  })
}
</script>

<template>
  <EditDialog :title="t('alarmSettings.copy.title', { name: rowName })" :busy="busy" wide class="alm-edit-backdrop alm-copy-backdrop" @close="emit('close')">
    <p class="muted">{{ t('alarmSettings.copy.intro', ruleCount) }}</p>

    <template v-if="!result">
      <h3 class="alm-copy-head">{{ t('alarmSettings.copy.targets') }}</h3>
      <p v-if="!targets.length" class="muted">{{ t('alarmSettings.copy.none') }}</p>
      <ul v-else class="alm-copy-list">
        <li v-for="x in targets" :key="x.id">
          <span>{{ x.name }}</span>
          <button type="button" class="edit-btn alm-copy-remove" :aria-label="t('alarmSettings.copy.remove', { name: x.name })" :disabled="busy" @click="remove(x.id)">×</button>
        </li>
      </ul>
      <div class="edit-actions">
        <button type="button" class="edit-btn alm-copy-pick" :disabled="busy" @click="picking = true">{{ t('alarmSettings.copy.choose') }}</button>
      </div>
      <label class="alm-copy-replace">
        <input :id="uid + '-replace'" v-model="replace" type="checkbox" :disabled="busy" />
        <span>{{ t('alarmSettings.copy.replace') }}</span>
      </label>
      <p class="edit-hint">{{ replace ? t('alarmSettings.copy.replaceHint') : t('alarmSettings.copy.addHint') }}</p>
    </template>

    <template v-else>
      <h3 class="alm-copy-head">{{ t('alarmSettings.copy.changed', result.changed.length) }}</h3>
      <ul v-if="result.changed.length" class="alm-copy-list alm-copy-changed">
        <li v-for="r in result.changed" :key="r.id">{{ rowText(r, r.id) }}</li>
      </ul>
      <template v-if="result.skipped.length">
        <h3 class="alm-copy-head">{{ t('alarmSettings.copy.skipped', result.skipped.length) }}</h3>
        <ul class="alm-copy-list alm-copy-skipped">
          <li v-for="s in result.skipped" :key="s.id">
            {{ rowText(s.row, s.id) }} – {{ t('alarmSettings.copy.reason.' + s.reason) }}
          </li>
        </ul>
      </template>
    </template>
    <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>

    <template #footer>
      <button type="button" class="edit-btn alm-copy-close" :disabled="busy" @click="emit('close')">{{ result ? t('edit.close') : t('edit.cancel') }}</button>
      <button v-if="!result" type="button" class="btn btn-primary alm-copy-submit" :disabled="!canSubmit" @click="submit">
        {{ busy ? t('alarmSettings.copy.busy') : t('alarmSettings.copy.submit') }}
      </button>
    </template>
  </EditDialog>

  <RowPickerDialog v-if="picking" :present="[]" :title="t('alarmSettings.copy.choose')" @close="picking = false" @add="added" />
</template>

<style scoped>
.alm-copy-head {
  font-size: 0.95rem;
  margin: 0.9rem 0 0.4rem;
}

.alm-copy-list {
  margin: 0 0 0.6rem;
  padding-left: 1.1rem;
}

.alm-copy-list li {
  margin: 0.15rem 0;
}

.alm-copy-list .alm-copy-remove {
  margin-left: 0.5rem;
  padding: 0 0.45rem;
}

.alm-copy-replace {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  margin-top: 0.6rem;
}
</style>
