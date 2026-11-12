/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch, type ComputedRef, type Ref, type ShallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRouter } from 'vue-router'
import { loadDashboard, type DashboardConfig, type DashboardData, type DashboardDataRequest } from '../../api/dashboard'
import { copyDashboard, deleteDashboard, loadSource, previewDashboard, updateDashboard, uploadBackground } from '../../api/dashboardEdit'
import { nameProblem, nameTakenId } from '../../analysis/editor'
import { useBusy, writeErrorKind, writeErrorText } from '../../edit/edit'
import { serverMessages } from '../../iso/edit'
import { useAuthStore } from '../../stores/auth'
import { useNavigationStore } from '../../stores/navigation'
import { changeLayer, deleteWidgets, duplicateWidgets, gridOf, nudge, placeInView } from './canvas'
import { SourceHistory } from './history'
import { nextUuid, type RawDashboard, type RawWidget } from './raw'
import { editableSource, overlaySource, previewKey, sourceKey, validateSource } from './source'
import { createWidget } from './widgetDefaults'

/*
 * Edit mode of the dashboard view (E4): the source loaded from GET …/source, the canvas operations with undo / redo,
 * the debounced preview (POST /web/dashboards/preview, an older request is aborted; positions and sizes are drawn
 * from the source at once), "Speichern" (PUT, rename), "Speichern unter" (copy), overwriting a dashboard whose name
 * is taken, "Löschen", the background upload, conflicts (409) and the unsaved-changes guard.
 */

/** Delay of the preview after the last change. */
export const PREVIEW_DELAY = 600
/** Largest background image accepted by the server. */
export const MAX_UPLOAD = 10 * 1024 * 1024

export interface SaveAsState {
  name: string
  directoryId: number | null
}

export interface OverwriteState {
  id: number
  name: string
}

export interface DashboardEditingOptions {
  id: () => number
  /** the saved dashboard shown by the view */
  config: ShallowRef<DashboardConfig | null>
  /** its data */
  data: ShallowRef<DashboardData | null>
  /** local name of the dashboard */
  title: ComputedRef<string>
  /** data request of the time frame bar */
  request: () => DashboardDataRequest
  /** compare key of the time frame bar (a change loads a new preview) */
  settingsKey: ComputedRef<string>
  /** a saved / reloaded dashboard replaces the shown one (the view loads its data) */
  replaceConfig: (config: DashboardConfig) => void
}

const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v)) as T

export function useDashboardEditing(options: DashboardEditingOptions) {
  const { t, locale } = useI18n()
  const auth = useAuthStore()
  const nav = useNavigationStore()
  const router = useRouter()
  const { busy, run } = useBusy()

  const editing = ref(false)
  const starting = ref(false)
  const source = ref<RawDashboard | null>(null) as Ref<RawDashboard | null>
  /** version of the loaded source (sent with PUT / DELETE) */
  const version = ref<string>('')
  const name = ref('')
  const initialKey = ref('')
  const initialName = ref('')
  const error = ref<string | null>(null)
  const messages = ref<string[]>([])
  const result = ref<{ ok: boolean; text: string } | null>(null)

  const canWrite = computed(() => !!options.config.value?.canWrite && !!options.config.value?.version)
  const canDelete = computed(() => !!options.config.value?.canDelete && !!options.config.value?.version)
  const canSaveAs = computed(() => nav.creatableDashboardDirectories.length > 0)
  /** "Bearbeiten": with write right, or with a directory to save a copy in */
  const canEdit = computed(() => canWrite.value || canSaveAs.value)

  const currentKey = computed(() => (source.value ? sourceKey(source.value) : ''))
  /** the source as loaded: values the Control Center stored outside the ranges may stay unchanged */
  const loadedSource = ref<RawDashboard | null>(null) as Ref<RawDashboard | null>
  const problems = computed(() => (source.value ? validateSource(source.value, canWrite.value ? name.value : null, loadedSource.value) : []))
  const dirty = computed(() => editing.value && !!source.value && (currentKey.value !== initialKey.value || name.value.trim() !== initialName.value.trim()))

  /* ---------------------------------------------------------------- selection and undo / redo */

  const selection = ref<Set<number>>(new Set())
  const widgets = computed<RawWidget[]>(() => source.value?.Widget ?? [])
  const selectedWidgets = computed(() => widgets.value.filter((w) => selection.value.has(w.uuid)))
  let history = new SourceHistory('')
  /** bumped on every history change (canUndo / canRedo are not reactive by themselves) */
  const historyTick = ref(0)
  const canUndo = computed(() => historyTick.value >= 0 && editing.value && history.canUndo)
  const canRedo = computed(() => historyTick.value >= 0 && editing.value && history.canRedo)
  let gesture = false

  function setSelection(next: Iterable<number>) {
    selection.value = new Set(next)
  }

  /** Records the current source as an undo step (`group`: steps of the same group close together are merged). */
  function record(group: string | null = null) {
    if (!source.value) return
    if (history.record(sourceKey(source.value), group)) historyTick.value++
  }

  // changes of the side panel (and the type settings) are recorded as they happen
  watch(currentKey, (key) => {
    if (!editing.value || !source.value || gesture || key === history.current) return
    record('panel')
  })

  function applyState(state: string | null) {
    if (state === null) return
    source.value = JSON.parse(state) as RawDashboard
    const present = new Set(source.value.Widget.map((w) => w.uuid))
    setSelection([...selection.value].filter((u) => present.has(u)))
    historyTick.value++
  }

  const undo = () => applyState(history.undo())
  const redo = () => applyState(history.redo())

  /** Dragging: one undo step for the whole gesture. */
  function beginGesture() {
    gesture = true
  }
  function endGesture() {
    gesture = false
    record(null)
  }

  const grid = (fine = false) => gridOf(source.value ?? {}, fine)

  function nudgeSelection(key: string, fine: boolean): boolean {
    if (!source.value || !nudge(source.value.Widget, selection.value, key, fine, grid())) return false
    record('nudge:' + [...selection.value].sort().join(','))
    return true
  }

  function deleteSelected() {
    if (!source.value || !selection.value.size) return
    deleteWidgets(source.value, selection.value)
    setSelection([])
    record(null)
  }

  function duplicateSelected() {
    if (!source.value || !selection.value.size) return
    const added = duplicateWidgets(source.value, selection.value, grid())
    setSelection(added)
    record(null)
  }

  function layer(delta: 1 | -1) {
    if (source.value && changeLayer(source.value.Widget, selection.value, delta)) record(null)
  }

  /** "Widget hinzufügen": a widget of the type with the CC defaults, in the visible area (dashboard pixels). */
  function addWidget(type: string, view: { left: number; top: number }): RawWidget | null {
    if (!source.value) return null
    const pos = placeInView(view, grid())
    const widget = createWidget(type, nextUuid(source.value), pos.x, pos.y, {
      locale: locale.value,
      grid: { x: source.value['X Axis Grid Interval'], y: source.value['Y Axis Grid Interval'] },
    })
    source.value.Widget.push(widget)
    setSelection([widget.uuid])
    record(null)
    return source.value.Widget[source.value.Widget.length - 1]
  }

  function selectAll() {
    setSelection(widgets.value.map((w) => w.uuid))
  }

  /* ---------------------------------------------------------------- preview */

  const previewConfig = shallowRef<DashboardConfig | null>(null)
  const previewData = shallowRef<DashboardData | null>(null)
  const previewState = ref<'idle' | 'waiting' | 'loading' | 'error' | 'invalid'>('idle')
  const previewMessages = ref<string[]>([])
  /** a background was uploaded while editing (drawn before the preview knows it) */
  const backgroundVersion = ref(0)
  let previewTimer: ReturnType<typeof setTimeout> | null = null
  let previewAbort: AbortController | null = null
  let previewSeq = 0

  /** configuration drawn by the view while editing */
  const shownConfig = computed<DashboardConfig | null>(() => {
    const base = previewConfig.value ?? options.config.value
    if (!base || !editing.value || !source.value) return options.config.value
    const shown = overlaySource(base, source.value)
    if (backgroundVersion.value && shown.background) shown.background = { ...shown.background, image: true }
    return shown
  })
  const shownData = computed(() => (editing.value ? previewData.value : options.data.value))

  function cancelPreview() {
    if (previewTimer) clearTimeout(previewTimer)
    previewTimer = null
    previewAbort?.abort()
    previewAbort = null
    previewSeq++
  }

  function schedulePreview(delay = PREVIEW_DELAY) {
    if (!editing.value || !source.value) return
    cancelPreview()
    previewState.value = 'waiting'
    previewTimer = setTimeout(() => void runPreview(), delay)
  }

  async function runPreview() {
    previewTimer = null
    if (!editing.value || !source.value) return
    if (problems.value.some((p) => p.path !== 'name')) {
      previewState.value = 'invalid'
      return
    }
    const seq = ++previewSeq
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null
    previewAbort = controller
    previewState.value = 'loading'
    try {
      const answer = await previewDashboard(auth.http, clone(source.value), options.request(), controller?.signal, options.id())
      if (seq !== previewSeq) return
      if (answer?.dashboard && Array.isArray(answer.dashboard.widgets)) previewConfig.value = answer.dashboard
      previewData.value = answer?.data ?? null
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

  const previewWatchKey = computed(() => (editing.value && source.value ? previewKey(source.value) : null))
  watch(previewWatchKey, (key, old) => {
    if (key !== null && old !== null && key !== old) schedulePreview()
  })
  watch(options.settingsKey, () => {
    if (editing.value) schedulePreview()
  })

  /* ---------------------------------------------------------------- enter / leave */

  let startSeq = 0

  /** Loads the source and switches to edit mode; only for the dashboard of the route. */
  async function start(): Promise<void> {
    const config = options.config.value
    // only the dashboard of the route (a stale one may still be shown while the next one loads)
    if (!config || config.id !== options.id() || !canEdit.value || editing.value || starting.value) return
    const seq = ++startSeq
    starting.value = true
    result.value = null
    error.value = null
    messages.value = []
    try {
      const loaded = await loadSource(auth.http, config.id)
      if (seq !== startSeq || options.config.value?.id !== config.id || config.id !== options.id()) return
      source.value = editableSource(loaded?.source)
      loadedSource.value = editableSource(loaded?.source)
      version.value = loaded?.version ?? config.version ?? ''
      name.value = options.title.value
      initialName.value = name.value
      initialKey.value = sourceKey(source.value)
      history = new SourceHistory(initialKey.value)
      historyTick.value++
      setSelection([])
      previewConfig.value = null
      previewData.value = options.data.value
      previewMessages.value = []
      previewState.value = 'idle'
      backgroundVersion.value = 0
      editing.value = true
    } catch (e) {
      if (seq === startSeq) result.value = { ok: false, text: writeErrorText(e, t, 'dashboardEdit.conflict') }
    } finally {
      if (seq === startSeq) starting.value = false
    }
  }

  function stop() {
    cancelPreview()
    startSeq++
    starting.value = false
    editing.value = false
    source.value = null
    setSelection([])
    previewConfig.value = null
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
    if (dirty.value) {
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

  function blockedByProblems(): boolean {
    if (!problems.value.length) return false
    error.value = t('dashboardEdit.fixProblems', problems.value.length)
    return true
  }

  function handleWriteError(e: unknown, target: 'panel' | 'saveAs') {
    if (writeErrorKind(e) === 'invalid') {
      const list = serverMessages(e)
      if (list.length) {
        messages.value = list
        if (target === 'saveAs') saveAsError.value = t('dashboardEdit.invalidSee')
        return
      }
    }
    const text = writeErrorText(e, t, 'dashboardEdit.conflict')
    if (target === 'saveAs') saveAsError.value = text
    else error.value = text
  }

  /** after a write: the navigation follows (names, new / deleted dashboards) */
  function reloadNavigation() {
    void nav.load()
  }

  async function goTo(id: number, text: string) {
    stop()
    await router.push({ name: 'dashboard', params: { id }, query: router.currentRoute.value.query })
    result.value = { ok: true, text }
  }

  async function save() {
    clearErrors()
    const config = options.config.value
    if (!source.value || !config || !canWrite.value) return
    if (config.id !== options.id()) {
      // never write a draft into another dashboard than the one of the route
      stop()
      return
    }
    if (blockedByProblems()) return
    const newName = name.value.trim()
    const rename = newName !== options.title.value.trim() ? newName : undefined
    const body = clone(source.value)
    await run(async () => {
      try {
        const saved = await updateDashboard(auth.http, locale.value, config.id, version.value || config.version!, body, rename)
        if (rename !== undefined) reloadNavigation()
        stop()
        if (saved && typeof saved === 'object' && Array.isArray(saved.widgets)) options.replaceConfig(saved)
        else options.replaceConfig(await loadDashboard(auth.http, config.id))
        result.value = { ok: true, text: t('dashboardEdit.saved') }
      } catch (e) {
        const taken = nameTakenId(e)
        if (taken !== null && rename !== undefined) {
          overwrite.value = { id: taken, name: newName }
          return
        }
        if (writeErrorKind(e) === 'conflict') {
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
    if (!source.value) return
    if (problems.value.some((p) => p.path !== 'name')) {
      blockedByProblems()
      return
    }
    const directories = nav.creatableDashboardDirectories
    const own = options.config.value?.directoryId
    const directoryId = directories.find((d) => d.id === own)?.id ?? directories[0]?.id ?? null
    saveAs.value = { name: t('dashboardEdit.copyName', { name: name.value.trim() || options.title.value }), directoryId }
  }

  async function submitSaveAs(target: SaveAsState) {
    saveAsError.value = null
    const config = options.config.value
    if (!source.value || !config || target.directoryId == null) return
    const problem = nameProblem(target.name)
    if (problem) {
      saveAsError.value = t(problem)
      return
    }
    const newName = target.name.trim()
    const body = clone(source.value)
    await run(async () => {
      try {
        const created = await copyDashboard(auth.http, locale.value, config.id, target.directoryId!, newName, body)
        saveAs.value = null
        reloadNavigation()
        await goTo(created.id, t('dashboardEdit.copied', { name: newName }))
      } catch (e) {
        const taken = nameTakenId(e)
        if (taken !== null) {
          overwrite.value = { id: taken, name: newName }
          return
        }
        handleWriteError(e, 'saveAs')
      }
    })
  }

  /** Overwrites the dashboard with the taken name with the source (PUT with its current version), then opens it. */
  async function confirmOverwrite() {
    const target = overwrite.value
    if (!target || !source.value) return
    const body = clone(source.value)
    await run(async () => {
      try {
        const existing = await loadDashboard(auth.http, target.id)
        if (!existing.version) throw new Error('no version')
        await updateDashboard(auth.http, locale.value, target.id, existing.version, body)
        overwrite.value = null
        saveAs.value = null
        reloadNavigation()
        await goTo(target.id, t('dashboardEdit.overwritten', { name: target.name }))
      } catch (e) {
        overwrite.value = null
        const text = writeErrorText(e, t, 'dashboardEdit.conflict')
        if (saveAs.value) saveAsError.value = text
        else error.value = text
      }
    })
  }

  function cancelOverwrite() {
    if (!busy.value) overwrite.value = null
  }

  /** 409: take over the current dashboard (the changes are dropped) and stay in edit mode. */
  async function conflictReload() {
    const config = options.config.value
    if (!config) return
    conflictOpen.value = false
    await run(async () => {
      try {
        const current = await loadDashboard(auth.http, config.id)
        stop()
        options.replaceConfig(current)
      } catch (e) {
        error.value = writeErrorText(e, t)
        return
      }
    })
    if (!editing.value && options.config.value?.id === config.id) {
      await start()
      result.value = { ok: false, text: t('dashboardEdit.reloaded') }
    }
  }

  function conflictSaveAs() {
    conflictOpen.value = false
    openSaveAs()
  }

  function askDelete() {
    clearErrors()
    deleteError.value = null
    deleting.value = true
  }

  async function confirmDelete() {
    const config = options.config.value
    const v = version.value || config?.version
    if (!config || !v) return
    await run(async () => {
      try {
        await deleteDashboard(auth.http, config.id, v)
        deleting.value = false
        stop()
        reloadNavigation()
        await router.push({ name: 'home' })
      } catch (e) {
        if (writeErrorKind(e) === 'conflict') {
          deleting.value = false
          conflictOpen.value = true
          return
        }
        deleteError.value = writeErrorText(e, t, 'dashboardEdit.conflict')
      }
    })
  }

  function cancelDelete() {
    if (!busy.value) deleting.value = false
  }

  /* ---------------------------------------------------------------- background image */

  const uploading = ref(false)
  const uploadMessage = ref<{ ok: boolean; text: string } | null>(null)

  async function uploadBackgroundImage(file: File) {
    const config = options.config.value
    uploadMessage.value = null
    if (!config || !canWrite.value || uploading.value) return
    if (file.size > MAX_UPLOAD) {
      uploadMessage.value = { ok: false, text: t('dashboardEdit.background.tooLarge') }
      return
    }
    uploading.value = true
    try {
      await uploadBackground(auth.http, config.id, file)
      backgroundVersion.value++
      uploadMessage.value = { ok: true, text: t('dashboardEdit.background.uploaded') }
      schedulePreview(0)
    } catch (e) {
      uploadMessage.value = { ok: false, text: writeErrorText(e, t) }
    } finally {
      uploading.value = false
    }
  }

  return {
    editing,
    starting,
    source,
    name,
    version,
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
    backgroundVersion,
    leaveAsk,
    conflictOpen,
    saveAs,
    saveAsError,
    overwrite,
    deleting,
    deleteError,
    uploading,
    uploadMessage,
    selection,
    selectedWidgets,
    canUndo,
    canRedo,
    start,
    stop,
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
    schedulePreview,
    setSelection,
    selectAll,
    record,
    undo,
    redo,
    beginGesture,
    endGesture,
    grid,
    nudgeSelection,
    deleteSelected,
    duplicateSelected,
    layer,
    addWidget,
    uploadBackgroundImage,
  }
}

export type DashboardEditing = ReturnType<typeof useDashboardEditing>
