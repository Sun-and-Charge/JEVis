<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { AuthError } from '../api/auth'
import AuthCard from '../components/AuthCard.vue'
import { useAuthStore, type LoginOutcome } from '../stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const step = ref<'credentials' | 'mfa' | 'enrollment'>('credentials')
const username = ref('')
const password = ref('')
const remember = ref(false)
const code = ref('')
const busy = ref(false)
const error = ref(auth.sessionExpired ? t('login.expired') : '')
const codeInput = ref<HTMLInputElement | null>(null)

function errorText(e: unknown): string {
  if (e instanceof AuthError) {
    if (e.status === 429) return t('login.locked')
    if (e.status === 503) return t('login.unavailable')
  }
  return t('login.error')
}

async function handle(outcome: LoginOutcome, failedText: string) {
  if (outcome.kind === 'done') {
    password.value = ''
    const target = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/'
    await router.replace(outcome.mustChangePassword ? { name: 'password' } : target)
  } else if (outcome.kind === 'mfa') {
    step.value = 'mfa'
    error.value = ''
    await nextTick()
    codeInput.value?.focus()
  } else if (outcome.kind === 'mfaEnrollment') {
    step.value = 'enrollment'
  } else {
    error.value = failedText
  }
}

async function submitCredentials() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await handle(await auth.login(username.value.trim(), password.value, remember.value), t('login.failed'))
  } catch (e) {
    error.value = errorText(e)
  } finally {
    busy.value = false
  }
}

async function submitCode() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await handle(await auth.completeMfa(code.value.replace(/\s/g, '')), t('login.mfaFailed'))
  } catch (e) {
    error.value = errorText(e)
  } finally {
    busy.value = false
  }
}

function back() {
  step.value = 'credentials'
  code.value = ''
  error.value = ''
}
</script>

<template>
  <AuthCard :title="step === 'mfa' ? t('login.mfaTitle') : t('login.title')">
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

    <form v-if="step === 'credentials'" novalidate @submit.prevent="submitCredentials">
      <div class="field">
        <label for="username">{{ t('login.username') }}</label>
        <input id="username" v-model="username" type="text" autocomplete="username" autocapitalize="none" spellcheck="false" required autofocus />
      </div>
      <div class="field">
        <label for="password">{{ t('login.password') }}</label>
        <input id="password" v-model="password" type="password" autocomplete="current-password" required />
      </div>
      <div class="field">
        <label class="check"><input v-model="remember" type="checkbox" /> {{ t('login.remember') }}</label>
      </div>
      <button class="btn btn-primary wide" type="submit" :disabled="busy || !username || !password">{{ t('login.submit') }}</button>
      <p class="below"><RouterLink :to="{ name: 'forgot' }">{{ t('login.forgot') }}</RouterLink></p>
    </form>

    <form v-else-if="step === 'mfa'" novalidate @submit.prevent="submitCode">
      <p class="muted">{{ t('login.mfaText') }}</p>
      <div class="field">
        <label for="code">{{ t('login.mfaCode') }}</label>
        <input id="code" ref="codeInput" v-model="code" type="text" inputmode="numeric" autocomplete="one-time-code" required />
      </div>
      <button class="btn btn-primary wide" type="submit" :disabled="busy || !code">{{ t('login.mfaSubmit') }}</button>
      <p class="below"><button type="button" class="btn-link" @click="back">{{ t('login.back') }}</button></p>
    </form>

    <div v-else>
      <p class="alert alert-error" role="alert">{{ t('login.mfaEnrollment') }}</p>
      <button type="button" class="btn-link" @click="back">{{ t('login.back') }}</button>
    </div>
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
