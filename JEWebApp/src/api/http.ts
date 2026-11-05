/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

/** Base path of the JEVis REST API (same origin as the app). */
export const API = '/JEWebService/v1'

export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly body: string,
  ) {
    super(`HTTP ${status}${body ? ': ' + body : ''}`)
  }
}

export interface AuthHooks {
  /** Current access token or null. */
  accessToken(): string | null
  /** Tries to get a new access token (refresh token). Resolves to true on success. */
  refresh(): Promise<boolean>
  /** Called when the session is gone (no refresh possible). */
  onUnauthorized(): void
}

type Fetch = typeof fetch

/**
 * Small JSON client for the REST API: adds the Bearer token, and on 401 refreshes the access token once and repeats
 * the request. Concurrent requests share one refresh.
 */
export class HttpClient {
  private refreshing: Promise<boolean> | null = null

  constructor(
    private readonly hooks: AuthHooks,
    private readonly fetchFn: Fetch = (...args) => fetch(...args),
  ) {}

  get<T>(path: string): Promise<T> {
    return this.request<T>('GET', path)
  }

  post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>('POST', path, body)
  }

  async request<T>(method: string, path: string, body?: unknown, retry = true): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' }
    const token = this.hooks.accessToken()
    if (token) headers.Authorization = `Bearer ${token}`
    if (body !== undefined) headers['Content-Type'] = 'application/json'

    const response = await this.fetchFn(API + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'omit',
      cache: 'no-store',
    })

    if (response.status === 401 && retry && token) {
      if (await this.refreshOnce()) {
        return this.request<T>(method, path, body, false)
      }
      this.hooks.onUnauthorized()
    }
    const text = await response.text()
    if (!response.ok) {
      if (response.status === 401 && !retry) this.hooks.onUnauthorized()
      throw new HttpError(response.status, text)
    }
    return (text ? JSON.parse(text) : undefined) as T
  }

  private refreshOnce(): Promise<boolean> {
    if (!this.refreshing) {
      this.refreshing = this.hooks.refresh().finally(() => {
        this.refreshing = null
      })
    }
    return this.refreshing
  }
}
