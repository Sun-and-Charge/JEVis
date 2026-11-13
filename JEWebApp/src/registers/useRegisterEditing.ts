/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { computed, ref, shallowRef, type ComputedRef, type ShallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { deleteRegisterObject, type EditableRegisterKind, type RegisterGroup, type RegisterKind, type RegisterList, type RegisterRow } from '../api/registers'
import { useBusy, writeErrorKind, writeErrorText } from '../edit/edit'
import { useAuthStore } from '../stores/auth'
import { anyRegisterWritable, latestVersion } from './edit'

/*
 * State of the write actions of the meters and equipment pages (E5): the object dialog (new / edit), the delete
 * question and the result message. The list is reloaded after every write (the answers carry new versions and
 * the server computes paths, media and last values).
 */

export interface RegisterEditTarget {
  group: RegisterGroup
  /** null: new object in the group */
  row: RegisterRow | null
}

export function useRegisterEditing(
  kind: () => RegisterKind,
  data: ShallowRef<RegisterList | null>,
  reload: () => Promise<void>,
  /** a created object is shown expanded */
  expand: (id: number) => void,
) {
  const { t } = useI18n()
  const auth = useAuthStore()
  const { busy: deleteBusy, run } = useBusy()

  const editing = shallowRef<RegisterEditTarget | null>(null)
  const deleting = shallowRef<RegisterRow | null>(null)
  const deleteError = ref<string | null>(null)
  const message = ref<{ ok: boolean; text: string } | null>(null)

  const editable: ComputedRef<EditableRegisterKind | null> = computed(() => {
    const k = kind()
    return k === 'meters' || k === 'equipment' ? k : null
  })
  const writable = computed(() => !!editable.value && anyRegisterWritable(data.value?.groups ?? []))

  function openCreate(group: RegisterGroup) {
    if (!editable.value || !group.canCreate) return
    message.value = null
    editing.value = { group, row: null }
  }

  function openEdit(group: RegisterGroup, row: RegisterRow) {
    if (!editable.value || !row.canWrite) return
    message.value = null
    editing.value = { group, row }
  }

  function close() {
    editing.value = null
  }

  async function onSaved(row: RegisterRow | null, created: boolean, name: string) {
    editing.value = null
    message.value = { ok: true, text: t(created ? 'registerEdit.created' : 'registerEdit.saved', { name }) }
    await reload()
    if (created && row) expand(row.id)
  }

  function askDelete(row: RegisterRow) {
    if (!editable.value || !row.canDelete) return
    message.value = null
    deleteError.value = null
    deleting.value = row
  }

  async function confirmDelete() {
    const row = deleting.value
    if (!row) return
    await run(async () => {
      try {
        /* an object without any sample has no version; the server then expects an empty one */
        await deleteRegisterObject(auth.http, row.id, latestVersion(row) ?? '')
        deleting.value = null
        message.value = { ok: true, text: t('registerEdit.deleted', { name: row.name }) }
        await reload()
      } catch (e) {
        const errorKind = writeErrorKind(e)
        if (errorKind === 'conflict' || errorKind === 'notFound') {
          deleting.value = null
          message.value = { ok: false, text: errorKind === 'conflict' ? t('registerEdit.conflictDelete', { name: row.name }) : writeErrorText(e, t) }
          await reload()
          return
        }
        deleteError.value = writeErrorText(e, t)
      }
    })
  }

  function cancelDelete() {
    if (!deleteBusy.value) deleting.value = null
  }

  return { editable, writable, editing, deleting, deleteError, deleteBusy, message, openCreate, openEdit, close, onSaved, askDelete, confirmDelete, cancelDelete }
}
