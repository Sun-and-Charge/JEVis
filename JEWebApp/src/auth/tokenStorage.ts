/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

/**
 * Where the login tokens are kept in the browser.
 *
 * - The access token is held in memory. Without "remember me" it is also kept in sessionStorage, so a reload of the
 *   tab stays logged in; it is gone when the tab is closed.
 * - With "remember me" the server issues a refresh token (valid for days). It is kept in localStorage and used to get
 *   a new access token after a reload or when the access token expired. The access token is not persisted then.
 *
 * The tokens are never written into the page or a URL.
 */
export interface StoredTokens {
  accessToken: string | null
  refreshToken: string | null
}

const ACCESS_KEY = 'jevis.accessToken'
const REFRESH_KEY = 'jevis.refreshToken'

export class TokenStorage {
  private accessToken: string | null = null

  constructor(
    private readonly session: Storage,
    private readonly local: Storage,
  ) {}

  /** Tokens after a page load: the persisted refresh token, or the access token of this tab. */
  load(): StoredTokens {
    const refreshToken = safeGet(this.local, REFRESH_KEY)
    this.accessToken = refreshToken ? null : safeGet(this.session, ACCESS_KEY)
    return { accessToken: this.accessToken, refreshToken }
  }

  save(accessToken: string, refreshToken: string | null): void {
    this.accessToken = accessToken
    if (refreshToken) {
      safeSet(this.local, REFRESH_KEY, refreshToken)
      safeRemove(this.session, ACCESS_KEY)
    } else {
      safeSet(this.session, ACCESS_KEY, accessToken)
    }
  }

  /** New access token from a refresh, the refresh token stays. */
  updateAccessToken(accessToken: string): void {
    this.accessToken = accessToken
    if (!safeGet(this.local, REFRESH_KEY)) {
      safeSet(this.session, ACCESS_KEY, accessToken)
    }
  }

  getAccessToken(): string | null {
    return this.accessToken
  }

  getRefreshToken(): string | null {
    return safeGet(this.local, REFRESH_KEY)
  }

  clear(): void {
    this.accessToken = null
    safeRemove(this.session, ACCESS_KEY)
    safeRemove(this.local, REFRESH_KEY)
  }
}

/* storage can be unavailable (private mode, blocked site data): the app then works for the current page only */
function safeGet(storage: Storage, key: string): string | null {
  try {
    return storage.getItem(key)
  } catch {
    return null
  }
}

function safeSet(storage: Storage, key: string, value: string): void {
  try {
    storage.setItem(key, value)
  } catch {
    /* keep it in memory only */
  }
}

function safeRemove(storage: Storage, key: string): void {
  try {
    storage.removeItem(key)
  } catch {
    /* nothing to remove */
  }
}
