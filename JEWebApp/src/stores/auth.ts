/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '../api/auth'
import type { LoginResult } from '../api/auth'
import { HttpClient } from '../api/http'
import { TokenStorage } from '../auth/tokenStorage'

export interface CurrentUser {
  id: number
  name: string
}

/** Result of a login step for the login form. */
export type LoginOutcome =
  | { kind: 'done'; mustChangePassword: boolean }
  | { kind: 'mfa' }
  | { kind: 'mfaEnrollment' }
  | { kind: 'failed'; message: string }

export const useAuthStore = defineStore('auth', () => {
  const tokens = new TokenStorage(window.sessionStorage, window.localStorage)
  const user = ref<CurrentUser | null>(null)
  const mustChangePassword = ref(false)
  const remember = ref(false)
  /** set when the session ended without the user logging out (token expired or revoked) */
  const sessionExpired = ref(false)
  let mfaToken: string | null = null

  const isLoggedIn = computed(() => user.value !== null)

  const http = new HttpClient({
    accessToken: () => tokens.getAccessToken(),
    refresh: refreshAccessToken,
    onUnauthorized: () => endSession(true),
  })

  function apply(result: LoginResult): void {
    tokens.save(result.accessToken!, result.refreshToken ?? (remember.value ? tokens.getRefreshToken() : null))
    user.value = { id: result.userId!, name: result.userName ?? '' }
    mustChangePassword.value = !!result.mustChangePassword
    sessionExpired.value = false
  }

  function outcome(result: LoginResult): LoginOutcome {
    switch (result.status) {
      case 'SUCCESS':
        apply(result)
        return { kind: 'done', mustChangePassword: !!result.mustChangePassword }
      case 'MFA_REQUIRED':
        mfaToken = result.mfaToken ?? null
        return { kind: 'mfa' }
      case 'MFA_ENROLLMENT_REQUIRED':
        return { kind: 'mfaEnrollment' }
      default:
        return { kind: 'failed', message: result.message ?? '' }
    }
  }

  async function login(username: string, password: string, rememberMe: boolean): Promise<LoginOutcome> {
    remember.value = rememberMe
    return outcome(await authApi.login(username, password, rememberMe))
  }

  async function completeMfa(code: string): Promise<LoginOutcome> {
    if (!mfaToken) return { kind: 'failed', message: '' }
    return outcome(await authApi.loginMfa(mfaToken, code, remember.value))
  }

  async function refreshAccessToken(): Promise<boolean> {
    const refreshToken = tokens.getRefreshToken()
    if (!refreshToken) return false
    try {
      const result = await authApi.refresh(refreshToken)
      if (result.status !== 'SUCCESS' || !result.accessToken) return false
      tokens.updateAccessToken(result.accessToken)
      if (result.userId) user.value = { id: result.userId, name: result.userName ?? user.value?.name ?? '' }
      return true
    } catch {
      return false
    }
  }

  /** After a page load: continue the session of this tab or of "remember me". */
  async function restore(): Promise<boolean> {
    const stored = tokens.load()
    remember.value = !!stored.refreshToken
    if (stored.refreshToken && (await refreshAccessToken())) return true
    if (stored.accessToken) {
      try {
        const me = await http.get<{ id: number; name: string }>('/auth/me')
        user.value = { id: me.id, name: me.name }
        return true
      } catch {
        /* token expired */
      }
    }
    tokens.clear()
    return false
  }

  async function changePassword(oldPassword: string, newPassword: string): Promise<LoginOutcome> {
    const token = tokens.getAccessToken()
    if (!token) return { kind: 'failed', message: '' }
    const result = await authApi.changePassword(token, oldPassword, newPassword, remember.value)
    return outcome(result)
  }

  function endSession(expired: boolean): void {
    tokens.clear()
    user.value = null
    mfaToken = null
    mustChangePassword.value = false
    sessionExpired.value = expired
  }

  async function logout(): Promise<void> {
    await authApi.logout(tokens.getAccessToken(), tokens.getRefreshToken())
    endSession(false)
  }

  return {
    user,
    isLoggedIn,
    mustChangePassword,
    sessionExpired,
    http,
    login,
    completeMfa,
    restore,
    changePassword,
    logout,
  }
})
