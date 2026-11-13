<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { MAX_REGISTER_FILE_BYTES, uploadRegisterFile, type FileValue, type UploadedFile } from '../../api/registers'
import { writeErrorText } from '../../edit/edit'
import { useAuthStore } from '../../stores/auth'
import RegisterFile from './RegisterFile.vue'

/*
 * A file attribute in the attribute form (E5): the current file (image preview, download) and, with canUpload, an
 * upload that writes a new file sample right away (max 25 MB; the old file stays in the history). Not part of the
 * form's save.
 */

const props = defineProps<{
  objectId: number
  attribute: string
  label: string
  value: FileValue | null
  /** shown after an upload, so the preview is loaded again */
  version?: string | null
  canUpload?: boolean
  fieldId: string
}>()
const emit = defineEmits<{ uploaded: [file: UploadedFile] }>()
const { t } = useI18n()
const auth = useAuthStore()

const uploading = ref<string | null>(null)
const message = ref<{ ok: boolean; text: string } | null>(null)

async function upload(file: File | undefined) {
  if (!file || !props.canUpload || uploading.value) return
  message.value = null
  if (file.size > MAX_REGISTER_FILE_BYTES) {
    message.value = { ok: false, text: t('registerEdit.file.tooLarge', { name: file.name }) }
    return
  }
  uploading.value = file.name
  try {
    const saved = await uploadRegisterFile(auth.http, props.objectId, props.attribute, file)
    const result: UploadedFile = {
      attribute: saved?.attribute || props.attribute,
      file: saved?.file || file.name,
      image: saved?.image === true,
      version: saved?.version ?? null,
    }
    message.value = { ok: true, text: t('registerEdit.file.uploaded', { name: result.file }) }
    emit('uploaded', result)
  } catch (e) {
    message.value = { ok: false, text: `${t('registerEdit.file.uploadError', { name: file.name })} ${writeErrorText(e, t)}` }
  } finally {
    uploading.value = null
  }
}

function picked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  void upload(file)
}
</script>

<template>
  <div class="reg-file-field" :aria-busy="uploading ? 'true' : undefined">
    <RegisterFile v-if="value" :key="(version ?? '') + value.file" :object-id="objectId" :attribute="attribute" :label="label" :value="value" />
    <p v-else class="muted reg-file-none">{{ t('registerEdit.file.none') }}</p>
    <div v-if="canUpload" class="reg-file-upload">
      <input :id="fieldId" type="file" class="iso-upload-input" :disabled="!!uploading" :aria-describedby="fieldId + '-hint'" @change="picked" />
      <label :for="fieldId" class="edit-btn is-small" :aria-disabled="uploading ? 'true' : undefined">
        {{ value ? t('registerEdit.file.replace') : t('registerEdit.file.upload') }}
      </label>
      <span :id="fieldId + '-hint'" class="edit-hint">{{ t('registerEdit.file.hint') }}</span>
    </div>
    <p v-if="uploading" class="muted" role="status">{{ t('registerEdit.file.uploading', { name: uploading }) }}</p>
    <p v-if="message" class="reg-file-message" :class="message.ok ? 'is-ok' : 'iso-field-error'" :role="message.ok ? 'status' : 'alert'">{{ message.text }}</p>
  </div>
</template>
