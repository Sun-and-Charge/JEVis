<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadAttachmentFile, loadAttachments, type Attachment } from '../../api/iso'
import { saveBlob } from '../../iso/download'
import { formatSize } from '../../iso/format'
import { formatLocal } from '../../modules/dates'
import { useAuthStore } from '../../stores/auth'

/* Attachments of an item: listed when the panel opens, downloaded with the Bearer token (blob + object URL). */

const props = defineProps<{ itemId: number; count: number }>()
const { t, locale } = useI18n()
const auth = useAuthStore()

const files = ref<Attachment[]>([])
const state = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const busy = ref<number | null>(null)
const failed = ref<number | null>(null)

async function load() {
  if (props.count <= 0) {
    state.value = 'ready'
    return
  }
  state.value = 'loading'
  try {
    files.value = (await loadAttachments(auth.http, props.itemId)) ?? []
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

onMounted(load)

async function download(file: Attachment) {
  busy.value = file.id
  failed.value = null
  try {
    saveBlob(await loadAttachmentFile(auth.http, file.id), file.name)
  } catch {
    failed.value = file.id
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <div class="iso-attachments">
    <p v-if="state === 'loading'" class="muted" role="status">{{ t('iso.attachments.loading') }}</p>
    <p v-else-if="state === 'error'" class="alert alert-error" role="alert">
      {{ t('iso.attachments.error') }}
      <button type="button" class="btn-link" @click="load">{{ t('modules.retry') }}</button>
    </p>
    <p v-else-if="!files.length" class="muted">{{ t('iso.attachments.none') }}</p>
    <ul v-else class="iso-files">
      <li v-for="f in files" :key="f.id">
        <button type="button" class="btn-link iso-file" :disabled="busy === f.id" :aria-label="t('iso.attachments.download', { name: f.name })" @click="download(f)">
          {{ f.name }}
        </button>
        <span class="muted">{{ [formatSize(f.size, locale), formatLocal(f.date, locale)].filter(Boolean).join(' · ') }}</span>
        <span v-if="busy === f.id" class="muted" role="status">{{ t('iso.attachments.downloading') }}</span>
        <span v-if="failed === f.id" class="iso-error" role="alert">{{ t('iso.attachments.downloadError') }}</span>
      </li>
    </ul>
  </div>
</template>
