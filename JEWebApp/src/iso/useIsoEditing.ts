/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { computed, nextTick, ref, shallowRef, type ShallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { HttpError } from '../api/http'
import { deleteIsoItem, type IsoItem, type IsoKind } from '../api/iso'
import { useBusy, writeErrorKind, writeErrorText } from '../edit/edit'
import { useAuthStore } from '../stores/auth'
import { anyWritable, dropItem, itemLabel, putItem, type IsoPlan } from './edit'
import type { PlanFilter } from './filter'

/*
 * State of the write actions of an ISO view (E2): the item dialog (new / edit), the delete question and the result
 * message. The list is updated in place from the answers; it is reloaded only when an answer has no item.
 */

export interface EditTarget {
  plan: IsoPlan
  /** null: new item */
  item: IsoItem | null
  /** new item: the plans to choose from */
  plans: IsoPlan[]
}

export interface DeleteTarget {
  plan: IsoPlan
  item: IsoItem
}

export interface ListHandle {
  expand: (id: number) => unknown
}

export function useIsoEditing<L extends { plans: readonly object[] }>(
  kind: IsoKind,
  data: ShallowRef<L | null>,
  reload: () => Promise<void>,
  selectedPlan: () => PlanFilter,
  list: () => ListHandle | null,
) {
  const { t } = useI18n()
  const auth = useAuthStore()
  const { busy: deleteBusy, run } = useBusy()

  const editing = shallowRef<EditTarget | null>(null)
  const deleting = shallowRef<DeleteTarget | null>(null)
  const deleteError = ref<string | null>(null)
  const message = ref<{ ok: boolean; text: string } | null>(null)

  const plans = computed(() => (data.value?.plans ?? []) as IsoPlan[])
  const creatable = computed(() => plans.value.filter((p) => !!p.canCreate))
  const writable = computed(() => anyWritable(kind, plans.value))

  function openCreate() {
    const selected = selectedPlan()
    const plan = creatable.value.find((p) => p.id === selected) ?? creatable.value[0]
    if (!plan) return
    message.value = null
    editing.value = { plan, item: null, plans: selected === 'all' ? creatable.value : [plan] }
  }

  function openEdit(plan: IsoPlan, item: IsoItem) {
    message.value = null
    editing.value = { plan, item, plans: [plan] }
  }

  function update(planId: number, item: IsoItem) {
    if (data.value) data.value = putItem(data.value, kind, planId, item)
  }

  async function onSaved(item: IsoItem | null, planId: number, created: boolean) {
    editing.value = null
    if (!item) {
      await reload()
      message.value = { ok: true, text: t(created ? 'isoEdit.createdShort' : 'isoEdit.savedShort') }
      return
    }
    update(planId, item)
    message.value = { ok: true, text: t(created ? 'isoEdit.created' : 'isoEdit.saved', { title: itemLabel(kind, item) }) }
    if (created) {
      await nextTick()
      await list()?.expand(item.id)
    }
  }

  /** 409 / 404 in the dialog: take over the current item or reload the list. */
  function onStale(item: IsoItem | null, planId: number) {
    if (item) update(planId, item)
    else void reload()
  }

  function askDelete(plan: IsoPlan, item: IsoItem) {
    message.value = null
    deleteError.value = null
    deleting.value = { plan, item }
  }

  async function confirmDelete() {
    const target = deleting.value
    if (!target || !target.item.version) return
    await run(async () => {
      try {
        await deleteIsoItem(auth.http, kind, target.item.id, target.item.version!)
        deleting.value = null
        if (data.value) data.value = dropItem(data.value, kind, target.item.id)
        message.value = { ok: true, text: t('isoEdit.deleted', { title: itemLabel(kind, target.item) }) }
      } catch (e) {
        const errorKind = writeErrorKind(e)
        if (errorKind === 'conflict') {
          deleting.value = null
          const current = e instanceof HttpError ? e.json<IsoItem>() : null
          if (current && typeof current === 'object' && current.id === target.item.id) update(target.plan.id, current)
          else void reload()
          message.value = { ok: false, text: t('isoEdit.conflictDelete') }
          return
        }
        if (errorKind === 'notFound') {
          deleting.value = null
          message.value = { ok: false, text: writeErrorText(e, t) }
          void reload()
          return
        }
        deleteError.value = writeErrorText(e, t)
      }
    })
  }

  function cancelDelete() {
    if (!deleteBusy.value) deleting.value = null
  }

  /** Attachments were added / removed in the detail panel: the count of the list follows. */
  function setAttachments(planId: number, item: IsoItem, count: number) {
    if (item.attachments !== count) update(planId, { ...item, attachments: count })
  }

  const deleteLabel = computed(() => (deleting.value ? itemLabel(kind, deleting.value.item) : ''))

  return {
    editing,
    deleting,
    deleteError,
    deleteBusy,
    deleteLabel,
    message,
    creatable,
    writable,
    openCreate,
    openEdit,
    onSaved,
    onStale,
    askDelete,
    confirmDelete,
    cancelDelete,
    setAttachments,
  }
}
