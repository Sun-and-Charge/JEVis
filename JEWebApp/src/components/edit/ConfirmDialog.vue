<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import EditDialog from './EditDialog.vue'

/* Yes/no question before a write action (E1); the confirm button is disabled while the request runs. */

defineProps<{ title: string; message: string; confirmLabel: string; busy?: boolean; danger?: boolean; error?: string | null }>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()
const { t } = useI18n()
const msgId = `confirm-msg-${Math.random().toString(36).slice(2, 9)}`
</script>

<template>
  <EditDialog :title="title" :busy="busy" :described-by="msgId" @close="emit('cancel')">
    <p :id="msgId" class="edit-message">{{ message }}</p>
    <slot />
    <p v-if="error" class="alert alert-error edit-error" role="alert">{{ error }}</p>
    <template #footer>
      <button type="button" class="edit-btn" :disabled="busy" @click="emit('cancel')">{{ t('edit.cancel') }}</button>
      <button type="button" class="btn btn-primary edit-submit" :class="{ 'is-danger': danger }" :disabled="busy" autofocus @click="emit('confirm')">
        {{ busy ? t('edit.saving') : confirmLabel }}
      </button>
    </template>
  </EditDialog>
</template>
