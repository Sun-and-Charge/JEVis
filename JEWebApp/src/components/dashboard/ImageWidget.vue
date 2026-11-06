<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed } from 'vue'
import { objectImagePath, type WidgetConfig } from '../../api/dashboard'
import { useAuthImage } from '../../dashboard/authImage'
import { useAuthStore } from '../../stores/auth'

/** Image widget: the image of an object, loaded with the token (a link is handled by the frame). */
const props = defineProps<{ widget: WidgetConfig }>()
const auth = useAuthStore()

const path = computed(() => {
  const id = props.widget.config?.objectId
  return typeof id === 'number' && id > 0 ? objectImagePath(id) : null
})
const image = useAuthImage(auth.http, path)
</script>

<template>
  <div class="image">
    <img v-if="image.url.value" :src="image.url.value" :alt="widget.title || widget.tooltip || ''" />
  </div>
</template>

<style scoped>
.image {
  width: 100%;
  height: 100%;
}

img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
</style>
