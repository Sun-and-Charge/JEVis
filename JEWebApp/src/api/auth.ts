/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { API } from './http'

/** Answer of /auth/login, /auth/login/mfa and /auth/refresh (JsonLoginResult on the server). */
export interface LoginResult {
  status: 'SUCCESS' | 'MFA_REQUIRED' | 'MFA_ENROLLMENT_REQUIRED' | 'FAILURE'
  accessToken?: string
  refreshToken?: string
  mfaToken?: string
  expiresIn?: number
  userId?: number
  userName?: string
  mustChangePassword?: boolean
  message?: string
}

/** Thrown for answers that are not a login result (rate limit, server not ready, ...). */
export class AuthError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message)
  }
}

async function postJson(path: string, body: unknown, token?: string | null): Promise<{ status: number; json: any; text: string }> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(API + path, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    credentials: 'omit',
    cache: 'no-store',
  })
  const text = await response.text()
  let json: any = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = null
  }
  return { status: response.status, json, text }
}

/** Login answers come as JSON also for a failed login (401); anything else is an error. */
async function loginCall(path: string, body: unknown): Promise<LoginResult> {
  const { status, json, text } = await postJson(path, body)
  if (json && typeof json.status === 'string') return json as LoginResult
  throw new AuthError(status, text || `HTTP ${status}`)
}

export function login(username: string, password: string, remember: boolean): Promise<LoginResult> {
  return loginCall('/auth/login', { username, password, remember })
}

export function loginMfa(mfaToken: string, code: string, remember: boolean): Promise<LoginResult> {
  return loginCall('/auth/login/mfa', { mfaToken, code, remember })
}

export function refresh(refreshToken: string): Promise<LoginResult> {
  return loginCall('/auth/refresh', { refreshToken })
}

export async function logout(accessToken: string | null, refreshToken: string | null): Promise<void> {
  try {
    await postJson('/auth/logout', refreshToken ? { refreshToken } : {}, accessToken)
  } catch {
    /* the local session is cleared anyway */
  }
}

/** Step 1 of "forgot password": the server mails a code if the account exists (same answer otherwise). */
export async function requestPasswordReset(login: string): Promise<void> {
  const { status, text } = await postJson('/auth/password/forgot', { login })
  if (status !== 200) throw new AuthError(status, text)
}

/** Step 2: set the new password with the mailed code. */
export async function resetPassword(login: string, code: string, newPassword: string): Promise<void> {
  const { status, text } = await postJson('/auth/password/reset', { login, code, newPassword })
  if (status !== 200) throw new AuthError(status, text)
}

/**
 * Change the own password (needs a valid access token). On success the server issues new tokens, on a wrong current
 * password it answers with status FAILURE.
 */
export async function changePassword(accessToken: string, oldPassword: string, newPassword: string, remember: boolean): Promise<LoginResult> {
  const { status, json, text } = await postJson('/auth/password', { oldPassword, newPassword, remember }, accessToken)
  if (json && typeof json.status === 'string') return json as LoginResult
  throw new AuthError(status, text || `HTTP ${status}`)
}
