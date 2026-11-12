<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { HttpError } from '../../../api/http'
import { objectImagePath } from '../../../api/dashboard'
import { useAuthStore } from '../../../stores/auth'
import { useAuthImage } from '../../authImage'
import { uploadImage } from '../typeApi'
import { isObject, objectId, useBlock, type Block, type TypeEditorProps } from './shared'
import TargetSelect from './TargetSelect.vue'
import { useNavTargets } from './targets'
import './types.css'

/*
 * Image (config2/ImageConfig.java, block `image`): the image file (`objectID`, a "File" child of the dashboard; the CC
 * writes the id as a string) uploaded with POST /web/dashboards/{id}/images — only for a saved dashboard — and the
 * optional link opened on click: none, a dashboard (`dashboardLinker.dashboardObject`) or an analysis
 * (`analysisLinker.graphAnalysisObject`). ImageConfig.toJSON writes only the link of the active type and only with a
 * target (> 0).
 */

const props = defineProps<TypeEditorProps>()
const { t } = useI18n()
const tt = (k: string, p?: Record<string, unknown>) => t('dashboardEdit.types.' + k, p ?? {})
const uid = useId()
const http = useAuthStore().http
const b = useBlock(props, 'image')

const imageId = computed(() => objectId(b.cfg.value.objectID))
const preview = useAuthImage(
  http,
  computed(() => (imageId.value ? objectImagePath(imageId.value) : null)),
)

const uploading = ref(false)
const uploadError = ref('')
const uploadedName = ref('')
watch(
  () => props.widget,
  () => {
    uploadError.value = ''
    uploadedName.value = ''
  },
)

async function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || props.dashboardId == null) return
  uploading.value = true
  uploadError.value = ''
  try {
    const answer = await uploadImage(http, props.dashboardId, file)
    /* ImageConfig.toJSON: objectID.toString() */
    b.set('objectID', String(answer.objectId))
    uploadedName.value = answer.name
  } catch (e) {
    uploadError.value =
      e instanceof HttpError && e.status === 413 ? tt('image.tooLarge') : e instanceof HttpError && e.status === 403 ? tt('image.forbidden') : e instanceof HttpError && e.status === 400 ? tt('image.noImage') : tt('image.uploadError')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

/* link: ImageConfig.getLinkType — a dashboard link wins if both are set */
type LinkType = 'NONE' | 'DASHBOARD' | 'ANALYSIS'
const linkOf = (key: string, field: string) => {
  const node = b.cfg.value[key]
  return isObject(node) ? objectId(node[field]) : 0
}
const dashboardTarget = computed(() => linkOf('dashboardLinker', 'dashboardObject'))
const analysisTarget = computed(() => linkOf('analysisLinker', 'graphAnalysisObject'))
const storedType = (): LinkType => (dashboardTarget.value > 0 ? 'DASHBOARD' : analysisTarget.value > 0 ? 'ANALYSIS' : 'NONE')
const linkType = ref<LinkType>(storedType())
watch(
  () => props.widget,
  () => {
    linkType.value = storedType()
  },
)

const nav = useNavTargets()

function setLinkType(type: LinkType) {
  linkType.value = type
  const block = b.block()
  /* only the link of the chosen type is written */
  if (type !== 'DASHBOARD') delete block.dashboardLinker
  if (type !== 'ANALYSIS') delete block.analysisLinker
}

function setTarget(id: number) {
  const block = b.block()
  const key = linkType.value === 'DASHBOARD' ? 'dashboardLinker' : 'analysisLinker'
  const field = linkType.value === 'DASHBOARD' ? 'dashboardObject' : 'graphAnalysisObject'
  if (id > 0) {
    const node: Block = isObject(block[key]) ? (block[key] as Block) : {}
    node[field] = id
    block[key] = node
  } else {
    delete block[key]
  }
}
</script>

<template>
  <fieldset class="ae-section dt-settings">
    <legend>{{ tt('image.tab') }}</legend>
    <img v-if="preview.url.value" class="dt-preview" :src="preview.url.value" :alt="tt('image.preview')" />
    <p v-else-if="imageId && preview.state.value === 'error'" class="ae-hint">{{ tt('image.previewError', { id: imageId }) }}</p>
    <p v-else-if="!imageId" class="ae-hint">{{ tt('image.none') }}</p>
    <p v-if="uploadedName" class="ae-hint" role="status">{{ tt('image.uploaded', { name: uploadedName }) }}</p>

    <div v-if="dashboardId != null" class="edit-field">
      <label :for="uid + '-file'">{{ imageId ? tt('image.replace') : tt('image.file') }}</label>
      <div class="dt-file">
        <input
          :id="uid + '-file'"
          type="file"
          accept="image/png,image/jpeg,image/gif,image/bmp,image/webp,image/svg+xml,.svg"
          :disabled="uploading"
          :aria-describedby="uid + '-file-hint'"
          @change="onFile"
        />
      </div>
      <p :id="uid + '-file-hint'" class="ae-hint">{{ uploading ? tt('image.uploading') : tt('image.fileHint') }}</p>
      <p v-if="uploadError" class="ae-error" role="alert">{{ uploadError }}</p>
    </div>
    <p v-else class="ae-hint">{{ tt('image.saveFirst') }}</p>

    <div class="ae-grid">
      <div class="edit-field ae-wide">
        <label :for="uid + '-linktype'">{{ tt('image.link') }}</label>
        <select :id="uid + '-linktype'" :value="linkType" @change="setLinkType(($event.target as HTMLSelectElement).value as LinkType)">
          <option value="NONE">{{ tt('image.linkTypes.NONE') }}</option>
          <option value="DASHBOARD">{{ tt('image.linkTypes.DASHBOARD') }}</option>
          <option value="ANALYSIS">{{ tt('image.linkTypes.ANALYSIS') }}</option>
        </select>
      </div>
      <TargetSelect
        v-if="linkType !== 'NONE'"
        :id="uid + '-target'"
        :label="tt('image.linkTarget')"
        :targets="linkType === 'DASHBOARD' ? nav.dashboards.value : nav.analyses.value"
        :value="linkType === 'DASHBOARD' ? dashboardTarget : analysisTarget"
        :loading="nav.loading.value"
        @change="setTarget"
      />
    </div>
  </fieldset>
</template>
