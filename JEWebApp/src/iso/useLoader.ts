/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { onMounted, ref, shallowRef, type Ref, type ShallowRef } from 'vue'

export interface Loader<T> {
  data: ShallowRef<T | null>
  state: Ref<'loading' | 'ready' | 'error'>
  reload: () => Promise<void>
}

/** Loads once on mount; reload() repeats it, answers of older calls are ignored. */
export function useLoader<T>(load: () => Promise<T>): Loader<T> {
  const data = shallowRef<T | null>(null)
  const state = ref<'loading' | 'ready' | 'error'>('loading')
  let seq = 0
  async function reload() {
    const current = ++seq
    state.value = 'loading'
    try {
      const result = await load()
      if (current !== seq) return
      data.value = result
      state.value = 'ready'
    } catch {
      if (current !== seq) return
      state.value = 'error'
    }
  }
  onMounted(reload)
  return { data, state, reload }
}
