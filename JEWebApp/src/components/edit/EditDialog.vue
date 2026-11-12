<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import '../../styles/edit.css'

/*
 * Modal dialog of the write actions (E1): labelled by its title, focus moves in (first [autofocus] or focusable
 * element) and is kept inside (Tab / Shift+Tab), Esc and the close button cancel (not while a request runs), and
 * the focus returns to the element that opened it.
 */

const props = defineProps<{ title: string; busy?: boolean; describedBy?: string; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()

const uid = `edit-dialog-${Math.random().toString(36).slice(2, 9)}`
const titleId = uid + '-title'
const panel = ref<HTMLElement | null>(null)
let opener: HTMLElement | null = null

const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

function focusables(): HTMLElement[] {
  return panel.value ? Array.from(panel.value.querySelectorAll<HTMLElement>(FOCUSABLE)) : []
}

onMounted(async () => {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  await nextTick()
  const first = panel.value?.querySelector<HTMLElement>('[autofocus]') ?? focusables()[0] ?? panel.value
  first?.focus()
})

onBeforeUnmount(() => {
  if (opener && document.contains(opener)) opener.focus()
})

function close() {
  if (!props.busy) emit('close')
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.stopPropagation()
    e.preventDefault()
    close()
    return
  }
  if (e.key !== 'Tab') return
  const items = focusables()
  if (!items.length) {
    e.preventDefault()
    return
  }
  const first = items[0]
  const last = items[items.length - 1]
  const active = document.activeElement
  if (e.shiftKey && (active === first || !panel.value?.contains(active))) {
    e.preventDefault()
    last.focus()
  } else if (!e.shiftKey && (active === last || !panel.value?.contains(active))) {
    e.preventDefault()
    first.focus()
  }
}
</script>

<template>
  <div class="edit-backdrop" @click.self="close">
    <div
      ref="panel"
      class="edit-dialog"
      :class="{ 'is-wide': wide }"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      :aria-describedby="describedBy"
      :aria-busy="busy ? 'true' : undefined"
      tabindex="-1"
      @keydown="onKeydown"
    >
      <header class="edit-head">
        <h2 :id="titleId">{{ title }}</h2>
        <button type="button" class="edit-close" :aria-label="t('edit.close')" :title="t('edit.close')" :disabled="busy" @click="close">×</button>
      </header>
      <div class="edit-body"><slot /></div>
      <footer v-if="$slots.footer" class="edit-foot"><slot name="footer" /></footer>
    </div>
  </div>
</template>
