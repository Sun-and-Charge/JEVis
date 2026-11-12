<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { deleteAttachment, loadAttachmentFile, loadAttachments, MAX_ATTACHMENT_BYTES, uploadAttachment, type Attachment } from '../../api/iso'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { saveBlob } from '../../iso/download'
import { formatSize } from '../../iso/format'
import { formatLocal } from '../../modules/dates'
import { useAuthStore } from '../../stores/auth'
import ConfirmDialog from '../edit/ConfirmDialog.vue'
import '../../styles/edit.css'
import '../../styles/isoEdit.css'

/*
 * Attachments of an item: listed when the panel opens, downloaded with the Bearer token (blob + object URL).
 * With canAttach (E2): upload by file choice or drag & drop (max 25 MB, checked here and on the server) and
 * delete per file after a question (the file's canDelete if delivered, else canAttach; the server checks again).
 */

const props = defineProps<{ itemId: number; count: number; canAttach?: boolean }>()
const emit = defineEmits<{ changed: [count: number] }>()
const { t, locale } = useI18n()
const auth = useAuthStore()

const files = ref<Attachment[]>([])
const state = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const busy = ref<number | null>(null)
const failed = ref<number | null>(null)

const uploading = ref<string | null>(null)
const message = ref<{ ok: boolean; text: string } | null>(null)
const over = ref(false)
const asking = ref<Attachment | null>(null)
const askError = ref<string | null>(null)
const { busy: deleting, run } = useBusy()
const uid = `iso-att-${Math.random().toString(36).slice(2, 9)}`

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

const mayDelete = (f: Attachment) => (typeof f.canDelete === 'boolean' ? f.canDelete : !!props.canAttach)

function changed() {
  if (state.value === 'ready') emit('changed', files.value.length)
}

async function upload(list: readonly File[]) {
  if (!props.canAttach || uploading.value || !list.length) return
  message.value = null
  const done: string[] = []
  for (const file of list) {
    if (file.size > MAX_ATTACHMENT_BYTES) {
      message.value = { ok: false, text: t('isoEdit.attachments.tooLarge', { name: file.name }) }
      continue
    }
    uploading.value = file.name
    try {
      const saved = await uploadAttachment(auth.http, props.itemId, file)
      files.value = [
        ...files.value,
        { id: saved?.id ?? -Date.now(), name: saved?.name || file.name, size: saved?.size ?? file.size, date: saved?.date ?? new Date().toISOString(), canDelete: saved?.canDelete },
      ]
      if (state.value !== 'error') state.value = 'ready'
      done.push(saved?.name || file.name)
    } catch (e) {
      message.value = { ok: false, text: `${t('isoEdit.attachments.uploadError', { name: file.name })} ${writeErrorText(e, t)}` }
    } finally {
      uploading.value = null
    }
  }
  if (done.length && !message.value) message.value = { ok: true, text: t('isoEdit.attachments.uploaded', { name: done.join(', ') }) }
  if (done.length) changed()
}

function picked(e: Event) {
  const input = e.target as HTMLInputElement
  const list = input.files ? Array.from(input.files) : []
  input.value = ''
  void upload(list)
}

function dropped(e: DragEvent) {
  over.value = false
  const list = e.dataTransfer?.files ? Array.from(e.dataTransfer.files) : []
  void upload(list)
}

function askDelete(f: Attachment) {
  message.value = null
  askError.value = null
  asking.value = f
}

async function remove() {
  const f = asking.value
  if (!f) return
  await run(async () => {
    try {
      await deleteAttachment(auth.http, f.id)
      asking.value = null
      files.value = files.value.filter((x) => x.id !== f.id)
      message.value = { ok: true, text: t('isoEdit.attachments.deleted', { name: f.name }) }
      changed()
    } catch (e) {
      if (writeErrorKind(e) === 'notFound') {
        // already gone (or not an attachment of an ISO item): show the current list
        asking.value = null
        message.value = { ok: false, text: writeErrorText(e, t) }
        void load()
        return
      }
      askError.value = writeErrorText(e, t)
    }
  })
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
    <ul v-if="state !== 'loading' && files.length" class="iso-files">
      <li v-for="f in files" :key="f.id">
        <button type="button" class="btn-link iso-file" :disabled="busy === f.id" :aria-label="t('iso.attachments.download', { name: f.name })" @click="download(f)">
          {{ f.name }}
        </button>
        <span class="muted">{{ [formatSize(f.size, locale), formatLocal(f.date, locale)].filter(Boolean).join(' · ') }}</span>
        <button
          v-if="mayDelete(f)"
          type="button"
          class="edit-btn is-small is-danger iso-file-delete"
          :aria-label="t('isoEdit.attachments.deleteLabel', { name: f.name })"
          :title="t('isoEdit.attachments.deleteLabel', { name: f.name })"
          @click="askDelete(f)"
        >
          {{ t('isoEdit.delete') }}
        </button>
        <span v-if="busy === f.id" class="muted" role="status">{{ t('iso.attachments.downloading') }}</span>
        <span v-if="failed === f.id" class="iso-error" role="alert">{{ t('iso.attachments.downloadError') }}</span>
      </li>
    </ul>

    <div
      v-if="canAttach"
      class="iso-upload"
      :class="{ 'is-over': over }"
      :aria-busy="uploading ? 'true' : undefined"
      @dragenter.prevent="over = true"
      @dragover.prevent="over = true"
      @dragleave.prevent="over = false"
      @drop.prevent="dropped"
    >
      <input :id="uid + '-file'" type="file" multiple class="iso-upload-input" :disabled="!!uploading" :aria-describedby="uid + '-hint'" @change="picked" />
      <label :for="uid + '-file'" class="edit-btn" :aria-disabled="uploading ? 'true' : undefined">{{ t('isoEdit.attachments.upload') }}</label>
      <p :id="uid + '-hint'" class="edit-hint">{{ t('isoEdit.attachments.hint') }}</p>
      <p v-if="uploading" class="muted iso-uploading" role="status">{{ t('isoEdit.attachments.uploading', { name: uploading }) }}</p>
    </div>
    <p v-if="message" class="alert edit-result iso-attach-message" :class="message.ok ? 'alert-ok' : 'alert-error'" :role="message.ok ? 'status' : 'alert'">{{ message.text }}</p>

    <ConfirmDialog
      v-if="asking"
      :title="t('isoEdit.attachments.deleteTitle')"
      :message="t('isoEdit.attachments.deleteQuestion', { name: asking.name })"
      :confirm-label="t('isoEdit.delete')"
      :busy="deleting"
      :error="askError"
      danger
      @confirm="remove"
      @cancel="asking = null"
    />
  </div>
</template>
