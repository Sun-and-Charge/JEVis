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

  /** The body as JSON (e.g. the current entry of a 409), null if it is empty or not JSON. */
  json<T = unknown>(): T | null {
    if (!this.body) return null
    try {
      return JSON.parse(this.body) as T
    } catch {
      return null
    }
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

  put<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>('PUT', path, body)
  }

  delete<T = void>(path: string): Promise<T> {
    return this.request<T>('DELETE', path)
  }

  /** Multipart upload (attachments): the browser sets the Content-Type with the boundary; same token handling. */
  postForm<T>(path: string, form: FormData): Promise<T> {
    return this.request<T>('POST', path, form)
  }

  async request<T>(method: string, path: string, body?: unknown, retry = true): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' }
    const token = this.hooks.accessToken()
    if (token) headers.Authorization = `Bearer ${token}`
    const form = typeof FormData !== 'undefined' && body instanceof FormData
    if (body !== undefined && !form) headers['Content-Type'] = 'application/json'

    const response = await this.fetchFn(API + path, {
      method,
      headers,
      body: body === undefined ? undefined : form ? (body as FormData) : JSON.stringify(body),
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

  /**
   * Binary download (images) with the Bearer token; an <img src> cannot send the header. Same 401 handling as
   * {@link request}. The browser cache may be used (the server sends `Cache-Control: private`).
   */
  async getBlob(path: string, retry = true): Promise<Blob> {
    const headers: Record<string, string> = {}
    const token = this.hooks.accessToken()
    if (token) headers.Authorization = `Bearer ${token}`
    const response = await this.fetchFn(API + path, { method: 'GET', headers, credentials: 'omit' })
    if (response.status === 401 && retry && token) {
      if (await this.refreshOnce()) return this.getBlob(path, false)
      this.hooks.onUnauthorized()
    }
    if (!response.ok) {
      if (response.status === 401 && !retry) this.hooks.onUnauthorized()
      throw new HttpError(response.status, '')
    }
    return response.blob()
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
