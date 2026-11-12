/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch, type ComputedRef, type Ref, type ShallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRouter } from 'vue-router'
import {
  copyAnalysis,
  deleteAnalysis,
  loadAnalysis,
  loadPreviewData,
  updateAnalysis,
  type AnalysisConfig,
  type AnalysisData,
  type AnalysisDataRequest,
} from '../api/analysis'
import { useBusy, writeErrorKind, writeErrorText } from '../edit/edit'
import { serverMessages } from '../iso/edit'
import { useAuthStore } from '../stores/auth'
import { useNavigationStore } from '../stores/navigation'
import { conflictConfig, draftConfig, draftKey, nameProblem, nameTakenId, previewKey, toDefinition, toDraft, validateDraft, type Draft } from './editor'

/*
 * Edit mode of the analysis view (E3): the draft of the side panel, the debounced live preview (POST
 * /web/analyses/preview/data, an older request is aborted), "Speichern" (PUT, rename), "Speichern unter" (copy),
 * overwriting an analysis whose name is taken, "Löschen", conflicts (409) and the unsaved-changes guard.
 */

/** Delay of the live preview after the last change. */
export const PREVIEW_DELAY = 600

export interface SaveAsState {
  name: string
  directoryId: number | null
}

/** A name that exists in the target directory: offer to overwrite that analysis (PUT on it). */
export interface OverwriteState {
  id: number
  name: string
}

export interface AnalysisEditingOptions {
  id: () => number
  /** the saved analysis shown by the view */
  config: ShallowRef<AnalysisConfig | null>
  /** data of the saved analysis */
  data: ShallowRef<AnalysisData | null>
  /** local name of the analysis */
  title: ComputedRef<string>
  /** data request of the toolbar settings */
  request: () => AnalysisDataRequest
  /** compare key of the toolbar settings (a change loads a new preview) */
  settingsKey: ComputedRef<string>
  /** a saved / reloaded analysis replaces the shown one (the view loads its data) */
  replaceConfig: (config: AnalysisConfig) => void
}

export function useAnalysisEditing(options: AnalysisEditingOptions) {
  const { t, locale } = useI18n()
  const auth = useAuthStore()
  const nav = useNavigationStore()
  const router = useRouter()
  const { busy, run } = useBusy()

  const editing = ref(false)
  const draft = ref<Draft | null>(null) as Ref<Draft | null>
  const initialKey = ref('')
  const error = ref<string | null>(null)
  const messages = ref<string[]>([])
  const result = ref<{ ok: boolean; text: string } | null>(null)

  const canWrite = computed(() => !!options.config.value?.canWrite && !!options.config.value?.version)
  const canDelete = computed(() => !!options.config.value?.canDelete && !!options.config.value?.version)
  const canSaveAs = computed(() => nav.creatableDirectories.length > 0)
  /** "Bearbeiten": with write right, or with a directory to save a copy in */
  const canEdit = computed(() => canWrite.value || canSaveAs.value)

  const problems = computed(() => (draft.value ? validateDraft(draft.value, { checkName: canWrite.value }) : []))
  const dirty = computed(() => !!draft.value && draftKey(draft.value) !== initialKey.value)

  /* ---------------------------------------------------------------- preview */

  const previewData = shallowRef<AnalysisData | null>(null)
  const previewState = ref<'idle' | 'waiting' | 'loading' | 'error' | 'invalid'>('idle')
  const previewMessages = ref<string[]>([])
  let previewTimer: ReturnType<typeof setTimeout> | null = null
  let previewAbort: AbortController | null = null
  let previewSeq = 0

  /** configuration drawn by the view while editing */
  const shownConfig = computed<AnalysisConfig | null>(() => {
    const base = options.config.value
    if (!base || !editing.value || !draft.value) return base
    return draftConfig(draft.value, base, previewData.value === options.data.value)
  })
  const shownData = computed(() => (editing.value ? previewData.value : options.data.value))

  function cancelPreview() {
    if (previewTimer) clearTimeout(previewTimer)
    previewTimer = null
    previewAbort?.abort()
    previewAbort = null
    previewSeq++
  }

  function schedulePreview() {
    if (!editing.value || !draft.value) return
    cancelPreview()
    previewState.value = 'waiting'
    previewTimer = setTimeout(() => void runPreview(), PREVIEW_DELAY)
  }

  async function runPreview() {
    previewTimer = null
    if (!editing.value || !draft.value) return
    if (problems.value.some((p) => p.path !== 'name')) {
      previewState.value = 'invalid'
      return
    }
    const seq = ++previewSeq
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null
    previewAbort = controller
    previewState.value = 'loading'
    try {
      const answer = await loadPreviewData(auth.http, toDefinition(draft.value), options.request(), controller?.signal, options.id())
      if (seq !== previewSeq) return
      previewData.value = answer
      previewMessages.value = []
      previewState.value = 'idle'
    } catch (e) {
      if (seq !== previewSeq) return
      previewMessages.value = writeErrorKind(e) === 'invalid' ? serverMessages(e) : []
      previewState.value = 'error'
    } finally {
      if (previewAbort === controller) previewAbort = null
    }
  }

  const previewWatchKey = computed(() => (editing.value && draft.value ? previewKey(toDefinition(draft.value)) : null))
  watch(previewWatchKey, (key, old) => {
    if (key !== null && old !== null && key !== old) schedulePreview()
  })
  watch(options.settingsKey, () => {
    if (editing.value) schedulePreview()
  })

  /* ---------------------------------------------------------------- enter / leave */

  function start() {
    const config = options.config.value
    // only the analysis of the route (a stale one may still be shown while the next one loads)
    if (!config || config.id !== options.id() || !canEdit.value) return
    result.value = null
    error.value = null
    messages.value = []
    draft.value = toDraft(config, options.title.value)
    initialKey.value = draftKey(draft.value)
    previewData.value = options.data.value
    previewMessages.value = []
    previewState.value = 'idle'
    editing.value = true
  }

  function stop() {
    cancelPreview()
    editing.value = false
    draft.value = null
    error.value = null
    messages.value = []
    previewMessages.value = []
    previewState.value = 'idle'
  }

  /* question before unsaved changes are dropped (button, route change); resolves true to drop them */
  const leaveAsk = ref<((drop: boolean) => void) | null>(null)
  function confirmDrop(): Promise<boolean> {
    if (!editing.value || !dirty.value) return Promise.resolve(true)
    leaveAsk.value?.(false)
    return new Promise((resolve) => {
      leaveAsk.value = (drop: boolean) => {
        leaveAsk.value = null
        resolve(drop)
      }
    })
  }

  async function discard() {
    if (await confirmDrop()) stop()
  }

  onBeforeRouteLeave(async () => {
    if (!(await confirmDrop())) return false
    stop()
    return true
  })
  onBeforeRouteUpdate(async (to, from) => {
    if (String(to.params.id) === String(from.params.id)) return true
    if (!(await confirmDrop())) return false
    stop()
    return true
  })

  function beforeUnload(e: BeforeUnloadEvent) {
    if (editing.value && dirty.value) {
      e.preventDefault()
      e.returnValue = ''
    }
  }
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', beforeUnload)
    cancelPreview()
  })

  /* ---------------------------------------------------------------- save / save as / overwrite / delete */

  const conflict = shallowRef<AnalysisConfig | null>(null)
  const conflictOpen = ref(false)
  const saveAs = ref<SaveAsState | null>(null)
  const saveAsError = ref<string | null>(null)
  const overwrite = ref<OverwriteState | null>(null)
  const deleting = ref(false)
  const deleteError = ref<string | null>(null)

  function clearErrors() {
    error.value = null
    messages.value = []
    result.value = null
  }

  /** first problem of the draft as text (save is refused) */
  function blockedByProblems(): boolean {
    if (!problems.value.length) return false
    error.value = t('analysisEdit.fixProblems', problems.value.length)
    return true
  }

  function handleWriteError(e: unknown, target: 'panel' | 'saveAs') {
    const kind = writeErrorKind(e)
    if (kind === 'invalid') {
      const list = serverMessages(e)
      if (list.length) {
        messages.value = list
        if (target === 'saveAs') saveAsError.value = t('analysisEdit.invalidSee')
        return
      }
    }
    const text = writeErrorText(e, t, 'analysisEdit.conflict')
    if (target === 'saveAs') saveAsError.value = text
    else error.value = text
  }

  /** after a write: the navigation follows (names, new / deleted analyses) */
  function reloadNavigation() {
    void nav.load()
  }

  async function goTo(id: number, text: string) {
    stop()
    await router.push({ name: 'analysis', params: { id }, query: router.currentRoute.value.query })
    result.value = { ok: true, text }
  }

  async function save() {
    clearErrors()
    const config = options.config.value
    if (!draft.value || !config || !canWrite.value) return
    if (config.id !== options.id()) {
      // never write a draft into another analysis than the one of the route
      stop()
      return
    }
    if (blockedByProblems()) return
    const newName = draft.value.name.trim()
    const rename = newName !== options.title.value.trim() ? newName : undefined
    const definition = toDefinition(draft.value)
    await run(async () => {
      try {
        const saved = await updateAnalysis(auth.http, locale.value, config.id, config.version!, definition, rename)
        if (rename !== undefined) reloadNavigation()
        stop()
        if (saved && typeof saved === 'object' && Array.isArray(saved.charts)) options.replaceConfig(saved)
        else {
          const fresh = await loadAnalysis(auth.http, config.id)
          options.replaceConfig(fresh)
        }
        result.value = { ok: true, text: t('analysisEdit.saved') }
      } catch (e) {
        const taken = nameTakenId(e)
        if (taken !== null && rename !== undefined) {
          overwrite.value = { id: taken, name: newName }
          return
        }
        if (writeErrorKind(e) === 'conflict') {
          conflict.value = conflictConfig(e, config.id)
          conflictOpen.value = true
          return
        }
        handleWriteError(e, 'panel')
      }
    })
  }

  function openSaveAs() {
    clearErrors()
    saveAsError.value = null
    if (!draft.value) return
    if (blockedByProblems()) return
    const directories = nav.creatableDirectories
    const own = options.config.value?.directoryId
    const directoryId = directories.find((d) => d.id === own)?.id ?? directories[0]?.id ?? null
    saveAs.value = { name: t('analysisEdit.copyName', { name: draft.value.name.trim() || options.title.value }), directoryId }
  }

  async function submitSaveAs(target: SaveAsState) {
    saveAsError.value = null
    const config = options.config.value
    if (!draft.value || !config || target.directoryId == null) return
    const problem = nameProblem(target.name)
    if (problem) {
      saveAsError.value = t(problem)
      return
    }
    const name = target.name.trim()
    const definition = toDefinition(draft.value)
    await run(async () => {
      try {
        const created = await copyAnalysis(auth.http, locale.value, config.id, target.directoryId!, name, definition)
        saveAs.value = null
        reloadNavigation()
        await goTo(created.id, t('analysisEdit.copied', { name }))
      } catch (e) {
        const taken = nameTakenId(e)
        if (taken !== null) {
          overwrite.value = { id: taken, name }
          return
        }
        handleWriteError(e, 'saveAs')
      }
    })
  }

  /** Overwrites the analysis with the taken name with the draft (PUT with its current version), then opens it. */
  async function confirmOverwrite() {
    const target = overwrite.value
    if (!target || !draft.value) return
    const definition = toDefinition(draft.value)
    await run(async () => {
      try {
        const existing = await loadAnalysis(auth.http, target.id)
        if (!existing.version) throw new Error('no version')
        await updateAnalysis(auth.http, locale.value, target.id, existing.version, definition)
        overwrite.value = null
        saveAs.value = null
        reloadNavigation()
        await goTo(target.id, t('analysisEdit.overwritten', { name: target.name }))
      } catch (e) {
        overwrite.value = null
        const text = writeErrorText(e, t, 'analysisEdit.conflict')
        if (saveAs.value) saveAsError.value = text
        else error.value = text
      }
    })
  }

  function cancelOverwrite() {
    if (!busy.value) overwrite.value = null
  }

  /** 409: take over the current analysis (the draft is dropped) and stay in edit mode. */
  async function conflictReload() {
    const config = options.config.value
    if (!config) return
    conflictOpen.value = false
    await run(async () => {
      try {
        const current = conflict.value ?? (await loadAnalysis(auth.http, config.id))
        conflict.value = null
        stop()
        options.replaceConfig(current)
        start()
        schedulePreview()
        result.value = { ok: false, text: t('analysisEdit.reloaded') }
      } catch (e) {
        error.value = writeErrorText(e, t)
      }
    })
  }

  function conflictSaveAs() {
    conflictOpen.value = false
    conflict.value = null
    openSaveAs()
  }

  function askDelete() {
    clearErrors()
    deleteError.value = null
    deleting.value = true
  }

  async function confirmDelete() {
    const config = options.config.value
    if (!config?.version) return
    await run(async () => {
      try {
        await deleteAnalysis(auth.http, config.id, config.version!)
        deleting.value = false
        stop()
        reloadNavigation()
        await router.push({ name: 'home' })
      } catch (e) {
        if (writeErrorKind(e) === 'conflict') {
          deleting.value = false
          conflict.value = conflictConfig(e, config.id)
          conflictOpen.value = true
          return
        }
        deleteError.value = writeErrorText(e, t, 'analysisEdit.conflict')
      }
    })
  }

  function cancelDelete() {
    if (!busy.value) deleting.value = false
  }

  /** E3: a filter text typed in a table chart is part of the analysis (as in the Control Center). */
  function setFilterText(chartId: number, column: string, text: string) {
    const chart = draft.value?.charts.find((c) => c.id === chartId)
    if (!chart) return
    const next = { ...chart.filterTexts }
    if (text) next[column] = text
    else delete next[column]
    chart.filterTexts = next
  }

  return {
    editing,
    draft,
    dirty,
    problems,
    busy,
    error,
    messages,
    result,
    canWrite,
    canDelete,
    canSaveAs,
    canEdit,
    shownConfig,
    shownData,
    previewState,
    previewMessages,
    leaveAsk,
    conflict,
    conflictOpen,
    saveAs,
    saveAsError,
    overwrite,
    deleting,
    deleteError,
    start,
    discard,
    save,
    openSaveAs,
    submitSaveAs,
    confirmOverwrite,
    cancelOverwrite,
    conflictReload,
    conflictSaveAs,
    askDelete,
    confirmDelete,
    cancelDelete,
    setFilterText,
    schedulePreview,
  }
}
