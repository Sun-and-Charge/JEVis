/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import type { HttpClient } from '../api/http'

/**
 * Loads an image of the API with the Bearer token (an <img> cannot send it) and offers it as blob URL with its
 * native size. `path` null: no image. The URL is released when the path changes or the component goes away.
 */
export function useAuthImage(http: Pick<HttpClient, 'getBlob'>, path: Ref<string | null>) {
  const url = ref<string | null>(null)
  const size = ref<{ width: number; height: number } | null>(null)
  const state = ref<'none' | 'loading' | 'ready' | 'error'>('none')
  let seq = 0

  function release() {
    if (url.value && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(url.value)
    url.value = null
    size.value = null
  }

  async function load(p: string | null) {
    const current = ++seq
    release()
    if (!p) {
      state.value = 'none'
      return
    }
    state.value = 'loading'
    try {
      const blob = await http.getBlob(p)
      if (current !== seq) return
      if (typeof URL.createObjectURL !== 'function') throw new Error('no blob URLs')
      const objectUrl = URL.createObjectURL(blob)
      url.value = objectUrl
      state.value = 'ready'
      const img = new Image()
      img.onload = () => {
        if (current === seq) size.value = { width: img.naturalWidth, height: img.naturalHeight }
      }
      img.src = objectUrl
    } catch {
      if (current === seq) state.value = 'error'
    }
  }

  watch(path, load, { immediate: true })
  onBeforeUnmount(() => {
    seq++
    release()
  })
  return { url, size, state }
}
