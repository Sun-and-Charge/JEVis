/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { inject, onBeforeUnmount, onMounted, ref, type Ref } from 'vue'
import { matchedRouteKey, onBeforeRouteLeave } from 'vue-router'

/*
 * Unsaved-changes guard of an editor dialog (as E5's register editor): closing the dialog or leaving the page with
 * unsaved changes asks first ("Verwerfen"), closing the browser tab gets the browser's question. `leaveAsk` is set
 * while the question is open; the dialog shows a ConfirmDialog and calls it with the answer.
 */
export function useLeaveGuard(dirty: Readonly<Ref<boolean>>) {
  const leaveAsk = ref<((drop: boolean) => void) | null>(null)

  function confirmDrop(): Promise<boolean> {
    if (!dirty.value) return Promise.resolve(true)
    leaveAsk.value?.(false)
    return new Promise((resolve) => {
      leaveAsk.value = (drop: boolean) => {
        leaveAsk.value = null
        resolve(drop)
      }
    })
  }

  /* only inside a routed page (tests may mount the view without one) */
  const routeRecord = inject(matchedRouteKey, null)
  if (routeRecord?.value) onBeforeRouteLeave(() => confirmDrop())

  function beforeUnload(e: BeforeUnloadEvent) {
    if (dirty.value) {
      e.preventDefault()
      e.returnValue = ''
    }
  }
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))

  return { leaveAsk, confirmDrop }
}
