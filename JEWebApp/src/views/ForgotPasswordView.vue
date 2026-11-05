<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AuthError, requestPasswordReset, resetPassword } from '../api/auth'
import AuthCard from '../components/AuthCard.vue'
import { passwordProblem } from '../passwordRules'

const { t } = useI18n()
const step = ref<'request' | 'reset' | 'done'>('request')
const login = ref('')
const code = ref('')
const newPassword = ref('')
const repeat = ref('')
const busy = ref(false)
const error = ref('')
const info = ref('')

async function request() {
  busy.value = true
  error.value = ''
  try {
    await requestPasswordReset(login.value.trim())
    info.value = t('forgot.sent')
    step.value = 'reset'
  } catch (e) {
    error.value = e instanceof AuthError && e.status === 429 ? t('login.locked') : t('login.error')
  } finally {
    busy.value = false
  }
}

async function reset() {
  const problem = passwordProblem(newPassword.value, repeat.value)
  if (problem) {
    error.value = t(problem)
    return
  }
  busy.value = true
  error.value = ''
  try {
    await resetPassword(login.value.trim(), code.value.trim(), newPassword.value)
    step.value = 'done'
  } catch (e) {
    error.value = e instanceof AuthError && e.status === 400 && e.message ? e.message : t('login.error')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AuthCard :title="t('forgot.title')">
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

    <form v-if="step === 'request'" novalidate @submit.prevent="request">
      <p class="muted">{{ t('forgot.text') }}</p>
      <div class="field">
        <label for="login">{{ t('forgot.login') }}</label>
        <input id="login" v-model="login" type="text" autocomplete="username" autocapitalize="none" required autofocus />
      </div>
      <button class="btn btn-primary wide" type="submit" :disabled="busy || !login.trim()">{{ t('forgot.send') }}</button>
    </form>

    <form v-else-if="step === 'reset'" novalidate @submit.prevent="reset">
      <p class="alert alert-ok" role="status">{{ info }}</p>
      <div class="field">
        <label for="code">{{ t('forgot.code') }}</label>
        <input id="code" v-model="code" type="text" autocomplete="one-time-code" required />
      </div>
      <div class="field">
        <label for="new">{{ t('forgot.newPassword') }}</label>
        <input id="new" v-model="newPassword" type="password" autocomplete="new-password" required />
      </div>
      <div class="field">
        <label for="repeat">{{ t('forgot.repeat') }}</label>
        <input id="repeat" v-model="repeat" type="password" autocomplete="new-password" required />
      </div>
      <button class="btn btn-primary wide" type="submit" :disabled="busy || !code || !newPassword">{{ t('forgot.reset') }}</button>
    </form>

    <p v-else class="alert alert-ok" role="status">{{ t('forgot.done') }}</p>

    <p class="below"><RouterLink :to="{ name: 'login' }">{{ t('forgot.toLogin') }}</RouterLink></p>
  </AuthCard>
</template>

<style scoped>
.wide {
  width: 100%;
}

.below {
  margin: 1rem 0 0;
  text-align: center;
}
</style>
