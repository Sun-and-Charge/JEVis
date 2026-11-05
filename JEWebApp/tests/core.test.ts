/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it, vi } from 'vitest'
import { HttpClient, HttpError } from '../src/api/http'
import { buildSiteTree, itemCount, localName, type NavSite } from '../src/api/navigation'
import { TokenStorage } from '../src/auth/tokenStorage'
import { initialLocale } from '../src/i18n'
import { passwordProblem } from '../src/passwordRules'

class MemoryStorage implements Storage {
  private map = new Map<string, string>()
  get length() {
    return this.map.size
  }
  clear() {
    this.map.clear()
  }
  getItem(key: string) {
    return this.map.has(key) ? this.map.get(key)! : null
  }
  key(i: number) {
    return [...this.map.keys()][i] ?? null
  }
  removeItem(key: string) {
    this.map.delete(key)
  }
  setItem(key: string, value: string) {
    this.map.set(key, value)
  }
}

describe('TokenStorage', () => {
  it('keeps the access token of this tab without "remember me"', () => {
    const session = new MemoryStorage()
    const local = new MemoryStorage()
    new TokenStorage(session, local).save('acc-1', null)
    expect(local.length).toBe(0)
    const reloaded = new TokenStorage(session, local).load()
    expect(reloaded).toEqual({ accessToken: 'acc-1', refreshToken: null })
  })

  it('persists only the refresh token with "remember me"', () => {
    const session = new MemoryStorage()
    const local = new MemoryStorage()
    const storage = new TokenStorage(session, local)
    storage.save('acc-1', 'ref-1')
    expect(session.length).toBe(0)
    expect(storage.getAccessToken()).toBe('acc-1')
    expect(new TokenStorage(session, local).load()).toEqual({ accessToken: null, refreshToken: 'ref-1' })
  })

  it('clear removes everything', () => {
    const session = new MemoryStorage()
    const local = new MemoryStorage()
    const storage = new TokenStorage(session, local)
    storage.save('acc', 'ref')
    storage.clear()
    expect(storage.getAccessToken()).toBeNull()
    expect(session.length + local.length).toBe(0)
  })

  it('works when the storage is blocked', () => {
    const blocked = {
      getItem: () => {
        throw new Error('blocked')
      },
      setItem: () => {
        throw new Error('blocked')
      },
      removeItem: () => {
        throw new Error('blocked')
      },
    } as unknown as Storage
    const storage = new TokenStorage(blocked, blocked)
    storage.save('acc', 'ref')
    expect(storage.getAccessToken()).toBe('acc')
    expect(storage.load()).toEqual({ accessToken: null, refreshToken: null })
  })
})

function response(status: number, body = ''): Response {
  return new Response(body, { status })
}

describe('HttpClient', () => {
  it('sends the Bearer token and parses JSON', async () => {
    const fetchFn = vi.fn().mockResolvedValue(response(200, '{"a":1}'))
    const client = new HttpClient({ accessToken: () => 'tok', refresh: async () => false, onUnauthorized: () => {} }, fetchFn)
    expect(await client.get('/x')).toEqual({ a: 1 })
    expect(fetchFn.mock.calls[0][0]).toBe('/JEWebService/v1/x')
    expect(fetchFn.mock.calls[0][1].headers.Authorization).toBe('Bearer tok')
  })

  it('refreshes once on 401 and repeats the request', async () => {
    let token = 'old'
    const fetchFn = vi
      .fn()
      .mockResolvedValueOnce(response(401))
      .mockResolvedValueOnce(response(200, '{"ok":true}'))
    const refresh = vi.fn(async () => {
      token = 'new'
      return true
    })
    const client = new HttpClient({ accessToken: () => token, refresh, onUnauthorized: () => {} }, fetchFn)
    expect(await client.get('/x')).toEqual({ ok: true })
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(fetchFn.mock.calls[1][1].headers.Authorization).toBe('Bearer new')
  })

  it('shares one refresh between parallel requests', async () => {
    let token = 'old'
    const fetchFn = vi.fn(async (_url: string, init: RequestInit) =>
      (init.headers as Record<string, string>).Authorization === 'Bearer new' ? response(200, '1') : response(401),
    )
    const refresh = vi.fn(async () => {
      await new Promise((r) => setTimeout(r, 5))
      token = 'new'
      return true
    })
    const client = new HttpClient({ accessToken: () => token, refresh, onUnauthorized: () => {} }, fetchFn as unknown as typeof fetch)
    await Promise.all([client.get('/a'), client.get('/b'), client.get('/c')])
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('ends the session when the refresh fails', async () => {
    const onUnauthorized = vi.fn()
    const fetchFn = vi.fn().mockResolvedValue(response(401, 'expired'))
    const client = new HttpClient({ accessToken: () => 'tok', refresh: async () => false, onUnauthorized }, fetchFn)
    await expect(client.get('/x')).rejects.toBeInstanceOf(HttpError)
    expect(onUnauthorized).toHaveBeenCalled()
  })
})

function site(id: number, parentId: number | null, analyses = 0, dashboards = 0): NavSite {
  const items = (n: number, base: number) => Array.from({ length: n }, (_, i) => ({ id: base + i, name: `item ${base + i}`, names: {} }))
  return { id, name: `site ${id}`, names: {}, className: 'Building', parentId, analyses: items(analyses, id * 100), dashboards: items(dashboards, id * 1000) }
}

describe('navigation', () => {
  it('nests sites by parent and counts items including sub-sites', () => {
    const tree = buildSiteTree([site(1, null, 1), site(2, 1, 0, 2), site(3, 2, 1), site(4, null)])
    expect(tree.map((n) => n.site.id)).toEqual([1, 4])
    expect(tree[0].children[0].site.id).toBe(2)
    expect(itemCount(tree[0])).toBe(4)
    expect(itemCount(tree[1])).toBe(0)
  })

  it('sites with an unknown parent become roots', () => {
    expect(buildSiteTree([site(5, 99)]).map((n) => n.site.id)).toEqual([5])
  })

  it('uses the localized name if there is one', () => {
    const item = { id: 1, name: 'Overview', names: { de: 'Übersicht' } }
    expect(localName(item, 'de')).toBe('Übersicht')
    expect(localName(item, 'en')).toBe('Overview')
  })
})

describe('locale and password rules', () => {
  it('prefers the stored language, then the browser, then German', () => {
    expect(initialLocale('en', ['de-DE'])).toBe('en')
    expect(initialLocale(null, ['fr-FR', 'en-US'])).toBe('en')
    expect(initialLocale('xx', ['fr'])).toBe('de')
  })

  it('checks length and repetition', () => {
    expect(passwordProblem('short', 'short')).toBe('password.tooShort')
    expect(passwordProblem('longenough', 'different')).toBe('password.mismatch')
    expect(passwordProblem('longenough', 'longenough')).toBeNull()
  })
})
