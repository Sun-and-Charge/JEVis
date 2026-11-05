<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { passwordProblem } from '../passwordRules'

const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()
const current = ref('')
const newPassword = ref('')
const repeat = ref('')
const busy = ref(false)
const error = ref('')
const done = ref(false)

async function submit() {
  const problem = passwordProblem(newPassword.value, repeat.value)
  if (problem) {
    error.value = t(problem)
    return
  }
  busy.value = true
  error.value = ''
  try {
    const outcome = await auth.changePassword(current.value, newPassword.value)
    if (outcome.kind === 'done') {
      done.value = true
      current.value = newPassword.value = repeat.value = ''
      setTimeout(() => router.replace({ name: 'home' }), 1200)
    } else {
      error.value = t('password.wrongCurrent')
    }
  } catch {
    error.value = t('login.error')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="page" aria-labelledby="pw-title">
    <h1 id="pw-title">{{ t('password.title') }}</h1>
    <p v-if="auth.mustChangePassword && !done" class="alert alert-error" role="alert">{{ t('password.mustChange') }}</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="done" class="alert alert-ok" role="status">{{ t('password.changed') }}</p>
    <form v-if="!done" novalidate @submit.prevent="submit">
      <div class="field">
        <label for="current">{{ t('password.current') }}</label>
        <input id="current" v-model="current" type="password" autocomplete="current-password" required />
      </div>
      <div class="field">
        <label for="new">{{ t('forgot.newPassword') }}</label>
        <input id="new" v-model="newPassword" type="password" autocomplete="new-password" required />
      </div>
      <div class="field">
        <label for="repeat">{{ t('forgot.repeat') }}</label>
        <input id="repeat" v-model="repeat" type="password" autocomplete="new-password" required />
      </div>
      <button class="btn btn-primary" type="submit" :disabled="busy || !current || !newPassword">{{ t('password.submit') }}</button>
    </form>
  </section>
</template>

<style scoped>
.page {
  max-width: 420px;
}
</style>
