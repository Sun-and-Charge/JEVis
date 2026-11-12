<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { WIDGET_TYPES } from '../../../dashboard/edit/typeEditors'
import type { DashboardEditing } from '../../../dashboard/edit/useDashboardEditing'
import EditDialog from '../../edit/EditDialog.vue'
import { useTypeLabel } from './typeLabel'

/*
 * Tools above the canvas (E4): "Widget hinzufügen" (all widget types with the Control Center's defaults), undo /
 * redo, duplicate, layer up / down and delete of the selection – the same as the keyboard shortcuts of the canvas.
 */

const props = defineProps<{ ed: DashboardEditing }>()
const emit = defineEmits<{ add: [type: string] }>()
const { t } = useI18n()
const typeLabel = useTypeLabel()

const palette = ref(false)
const types = computed(() => [...WIDGET_TYPES].sort((a, b) => typeLabel(a.id).localeCompare(typeLabel(b.id))))
const none = computed(() => !props.ed.selection.value.size)

function add(type: string) {
  palette.value = false
  emit('add', type)
}
</script>

<template>
  <div class="dbe-toolbar" role="toolbar" :aria-label="t('dashboardEdit.toolbar.label')">
    <button type="button" class="btn btn-primary dbe-add" @click="palette = true"><span aria-hidden="true">+</span> {{ t('dashboardEdit.toolbar.add') }}</button>
    <span class="dbe-sep" aria-hidden="true" />
    <button type="button" class="edit-btn is-small dbe-undo" :disabled="!ed.canUndo.value" :title="t('dashboardEdit.toolbar.undoKey')" @click="ed.undo()">↶ {{ t('dashboardEdit.toolbar.undo') }}</button>
    <button type="button" class="edit-btn is-small dbe-redo" :disabled="!ed.canRedo.value" :title="t('dashboardEdit.toolbar.redoKey')" @click="ed.redo()">↷ {{ t('dashboardEdit.toolbar.redo') }}</button>
    <span class="dbe-sep" aria-hidden="true" />
    <button type="button" class="edit-btn is-small dbe-duplicate" :disabled="none" :title="t('dashboardEdit.toolbar.duplicateKey')" @click="ed.duplicateSelected()">{{ t('dashboardEdit.toolbar.duplicate') }}</button>
    <button type="button" class="edit-btn is-small dbe-layer-up" :disabled="none" @click="ed.layer(1)">{{ t('dashboardEdit.toolbar.layerUp') }}</button>
    <button type="button" class="edit-btn is-small dbe-layer-down" :disabled="none" @click="ed.layer(-1)">{{ t('dashboardEdit.toolbar.layerDown') }}</button>
    <button type="button" class="edit-btn is-small is-danger dbe-remove" :disabled="none" :title="t('dashboardEdit.toolbar.deleteKey')" @click="ed.deleteSelected()">{{ t('dashboardEdit.toolbar.delete') }}</button>
    <span class="dbe-count" role="status">{{ none ? '' : t('dashboardEdit.toolbar.selected', { n: ed.selection.value.size }) }}</span>

    <EditDialog v-if="palette" :title="t('dashboardEdit.toolbar.addTitle')" wide @close="palette = false">
      <p class="edit-message">{{ t('dashboardEdit.toolbar.addHint') }}</p>
      <p v-if="!types.length" class="ae-hint">{{ t('dashboardEdit.toolbar.noTypes') }}</p>
      <ul class="dbe-palette">
        <li v-for="(type, i) in types" :key="type.id">
          <button type="button" class="dbe-type-btn" :data-type="type.id" :autofocus="i === 0" @click="add(type.id)">{{ typeLabel(type.id) }}</button>
        </li>
      </ul>
      <template #footer>
        <button type="button" class="edit-btn" @click="palette = false">{{ t('edit.cancel') }}</button>
      </template>
    </EditDialog>
  </div>
</template>
