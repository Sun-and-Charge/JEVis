<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NAME_MAX, nameProblem, objectOf, type Problem } from '../../accounting/contractEdit'
import { contractWhere } from '../../accounting/contracts'
import { createContractObject, type ContractCreateTarget, type ContractGroup, type ContractObject } from '../../api/contracts'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { useLeaveGuard } from '../../edit/useLeaveGuard'
import { serverMessages } from '../../iso/edit'
import { useAuthStore } from '../../stores/auth'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import EditDialog from '../edit/EditDialog.vue'
import '../../styles/isoEdit.css'

/*
 * "Neu" in a contract group (E6.2), as the Control Center's "New" dialog: class (with label), directory (if the
 * group has several) and name. The page then opens the new object in the editor.
 */

const props = defineProps<{ group: ContractGroup; groupLabel: string }>()
const emit = defineEmits<{ close: []; created: [object: ContractObject | null, name: string] }>()

const { t, locale } = useI18n()
const auth = useAuthStore()
const { busy, run } = useBusy()
const uid = `ctr-new-${Math.random().toString(36).slice(2, 9)}`
const id = (suffix: string) => `${uid}-${suffix}`

const classes = computed(() => props.group.classes ?? [])
const targets = computed(() => props.group.createTargets ?? [])
const cls = ref(classes.value[0]?.class ?? '')
const parent = ref<number | ''>(targets.value.length === 1 ? targets.value[0].id : '')
const name = ref('')
const nameError = ref<Problem | null>(null)
const parentError = ref(false)
const error = ref<string | null>(null)
const messages = ref<string[]>([])

const dirty = computed(() => !!name.value.trim())
const { leaveAsk, confirmDrop } = useLeaveGuard(dirty)

async function requestClose() {
  if (busy.value) return
  if (await confirmDrop()) emit('close')
}

const targetLabel = (d: ContractCreateTarget) => {
  const w = contractWhere(d, locale.value)
  return w ? `${d.name} – ${w}` : d.name
}

async function save() {
  error.value = null
  messages.value = []
  nameError.value = nameProblem(name.value)
  parentError.value = parent.value === ''
  if (parentError.value || nameError.value) {
    await nextTick()
    document.getElementById(id(parentError.value ? 'parent' : 'name'))?.focus()
    return
  }
  const newName = name.value.trim()
  await run(async () => {
    try {
      const answer = await createContractObject(auth.http, { parentId: parent.value as number, class: cls.value, name: newName }, locale.value)
      const o = answer && typeof answer === 'object' && typeof answer.id === 'number' ? objectOf(answer, answer.id) : null
      emit('created', o, o?.name || newName)
    } catch (e) {
      if (writeErrorKind(e) === 'invalid') {
        const list = serverMessages(e)
        if (list.length) {
          messages.value = list
          return
        }
      }
      error.value = writeErrorText(e, t)
    }
  })
}
</script>

<template>
  <EditDialog :title="t('contractEdit.newTitle', { group: groupLabel })" :busy="busy" @close="requestClose">
    <form :id="id('form')" novalidate @submit.prevent="save">
      <div v-if="classes.length > 1" class="edit-field">
        <label :for="id('class')">{{ t('contractEdit.class') }}</label>
        <select :id="id('class')" v-model="cls" :disabled="busy">
          <option v-for="c in classes" :key="c.class" :value="c.class">{{ c.label || c.class }}</option>
        </select>
      </div>
      <dl v-else class="edit-facts">
        <dt>{{ t('contractEdit.class') }}</dt>
        <dd>{{ classes[0]?.label || classes[0]?.class }}</dd>
      </dl>

      <div v-if="targets.length > 1" class="edit-field">
        <label :for="id('parent')">{{ t('contractEdit.directory') }}<span class="iso-req" aria-hidden="true"> *</span></label>
        <select
          :id="id('parent')"
          v-model="parent"
          :disabled="busy"
          required
          aria-required="true"
          :aria-invalid="parentError ? 'true' : undefined"
          :aria-describedby="parentError ? id('parent-err') : undefined"
          @change="parentError = false"
        >
          <option value="" disabled>{{ t('registerEdit.choose') }}</option>
          <option v-for="d in targets" :key="d.id" :value="d.id">{{ targetLabel(d) }}</option>
        </select>
        <p v-if="parentError" :id="id('parent-err')" class="iso-field-error">{{ t('contractEdit.problem.directory') }}</p>
      </div>
      <dl v-else-if="targets.length === 1" class="edit-facts">
        <dt>{{ t('contractEdit.directory') }}</dt>
        <dd>{{ targetLabel(targets[0]) }}</dd>
      </dl>

      <div class="edit-field">
        <label :for="id('name')">{{ t('registers.col.name') }}<span class="iso-req" aria-hidden="true"> *</span></label>
        <input
          :id="id('name')"
          v-model="name"
          type="text"
          :maxlength="NAME_MAX + 20"
          autocomplete="off"
          required
          aria-required="true"
          autofocus
          :disabled="busy"
          :aria-invalid="nameError ? 'true' : undefined"
          :aria-describedby="nameError ? id('name-err') : undefined"
          @input="nameError = null"
        />
        <p v-if="nameError" :id="id('name-err')" class="iso-field-error">{{ t(nameError.key, nameError.params ?? {}) }}</p>
      </div>
      <p class="edit-hint">{{ t('contractEdit.newHint') }}</p>

      <div v-if="messages.length" class="alert alert-error iso-messages" role="alert">
        <strong>{{ t('registerEdit.invalidTitle') }}</strong>
        <ul class="edit-warnings">
          <li v-for="(m, i) in messages" :key="i">{{ m }}</li>
        </ul>
      </div>
      <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="edit-btn" :disabled="busy" @click="requestClose">{{ t('edit.cancel') }}</button>
      <button type="submit" class="btn btn-primary edit-submit" :form="id('form')" :disabled="busy || !cls">
        {{ busy ? t('edit.saving') : t('registerEdit.create') }}
      </button>
    </template>
  </EditDialog>
  <ConfirmDialog
    v-if="leaveAsk"
    :title="t('registerEdit.leave.title')"
    :message="t('registerEdit.leave.message')"
    :confirm-label="t('registerEdit.leave.confirm')"
    danger
    @confirm="leaveAsk?.(true)"
    @cancel="leaveAsk?.(false)"
  />
</template>
