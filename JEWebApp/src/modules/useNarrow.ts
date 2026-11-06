/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/** Phone width (same breakpoint as the app shell): lists are shown as cards instead of tables. */
export const NARROW_QUERY = '(max-width: 800px)'

/** True while the media query matches; false where matchMedia is missing (tests). */
export function useNarrow(query = NARROW_QUERY): Ref<boolean> {
  const narrow = ref(false)
  let mql: MediaQueryList | null = null
  const update = () => {
    narrow.value = !!mql?.matches
  }
  onMounted(() => {
    if (typeof window.matchMedia !== 'function') return
    mql = window.matchMedia(query)
    update()
    mql.addEventListener?.('change', update)
  })
  onBeforeUnmount(() => mql?.removeEventListener?.('change', update))
  return narrow
}
