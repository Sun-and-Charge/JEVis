<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadRegisterFile, registerFilePath, type FileValue } from '../../api/registers'
import { useAuthImage } from '../../dashboard/authImage'
import { saveBlob } from '../../iso/download'
import { useAuthStore } from '../../stores/auth'

/* A file attribute: images as thumbnail (loaded with the Bearer token), every file with a download button. */

const props = defineProps<{ objectId: number; attribute: string; label: string; value: FileValue }>()
const { t } = useI18n()
const auth = useAuthStore()

const imagePath = computed(() => (props.value.image ? registerFilePath(props.objectId, props.attribute, true) : null))
const { url, state } = useAuthImage(auth.http, imagePath)

const busy = ref(false)
const failed = ref(false)

async function download() {
  busy.value = true
  failed.value = false
  try {
    saveBlob(await loadRegisterFile(auth.http, props.objectId, props.attribute), props.value.file)
  } catch {
    failed.value = true
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="reg-file">
    <img v-if="url" :src="url" :alt="t('registers.file.preview', { name: label })" class="reg-thumb" />
    <span v-else-if="state === 'loading'" class="muted" role="status">{{ t('registers.file.loading') }}</span>
    <span v-else-if="state === 'error'" class="muted">{{ t('registers.file.previewError') }}</span>
    <button type="button" class="btn-link iso-file" :disabled="busy" :aria-label="t('registers.file.download', { name: value.file })" @click="download">
      {{ value.file }}
    </button>
    <span v-if="busy" class="muted" role="status">{{ t('iso.attachments.downloading') }}</span>
    <span v-if="failed" class="iso-error" role="alert">{{ t('iso.attachments.downloadError') }}</span>
  </div>
</template>
