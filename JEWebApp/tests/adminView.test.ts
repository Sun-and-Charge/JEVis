/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, type Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdminGroup, AdminScope, AdminUser, ApiKey, SiteSharing } from '../src/api/admin'
import SideNav from '../src/components/nav/SideNav.vue'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import AdminApiKeysView from '../src/views/AdminApiKeysView.vue'
import AdminGroupsView from '../src/views/AdminGroupsView.vue'
import AdminSharingView from '../src/views/AdminSharingView.vue'
import AdminUsersView from '../src/views/AdminUsersView.vue'
import { httpError } from './fixtures/edit'

/* E10.1 administration pages with a mocked API; neutral placeholder data. */

const Stub = defineComponent({ render: () => h('div') })
type Fn = ReturnType<typeof vi.fn>
const mounted: VueWrapper[] = []
const SELF = 7

function scope(over: Partial<AdminScope> = {}): AdminScope {
  return {
    sysAdmin: false,
    sites: [{ id: 1, name: 'Musterwerk', className: 'Organization', groupDirectory: { id: 20, name: 'Gruppen' }, userDirectory: { id: 30, name: 'Benutzer' }, canManage: true }],
    ...over,
  }
}

function user(over: Partial<AdminUser> = {}): AdminUser {
  return {
    id: 501,
    account: 'emuster',
    firstName: 'Erika',
    lastName: 'Muster',
    email: 'erika.muster@example.com',
    enabled: true,
    sysAdmin: false,
    mfa: true,
    directory: { id: 30, name: 'Benutzer', site: { id: 1, name: 'Musterwerk' } },
    groups: [
      { id: 40, name: 'Musterwerk – Bearbeiten', rights: ['READ', 'WRITE'] },
      { id: 41, name: 'Zentrale – Ansehen', rights: ['READ'] },
    ],
    canWrite: true,
    version: 'v1',
    ...over,
  }
}

function group(over: Partial<AdminGroup> = {}): AdminGroup {
  return { id: 40, name: 'Musterwerk – Bearbeiten', site: { id: 1, name: 'Musterwerk' }, path: 'Musterwerk › Gruppen', members: 2, canWrite: true, canDelete: true, version: 'g1', ...over }
}

const groups = () => [group(), group({ id: 41, name: 'Zentrale – Ansehen', site: null, canWrite: false, canDelete: false }), group({ id: 42, name: 'Musterwerk – Ansehen', members: 0 })]

function api(routes: Record<string, unknown>, fallback?: (p: string) => unknown) {
  return vi.fn(async (p: string) => {
    for (const [prefix, answer] of Object.entries(routes)) {
      if (p === prefix || p.startsWith(prefix + '?')) {
        if (answer instanceof Error) throw answer
        return typeof answer === 'function' ? (answer as (p: string) => unknown)(p) : answer
      }
    }
    if (fallback) return fallback(p)
    throw new Error('unexpected GET ' + p)
  })
}

async function mountView(component: Component, http: Partial<Record<'get' | 'post' | 'put' | 'delete', Fn>>) {
  const auth = useAuthStore()
  auth.user = { id: SELF, name: 'admin' }
  Object.assign(auth.http, http)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'home', component: Stub }, { path: '/:p(.*)', name: 'other', component: Stub }] })
  await router.push('/')
  const wrapper = mount(component, { global: { plugins: [router, i18n] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

function field(w: VueWrapper, label: string) {
  const l = w.findAll('[role="dialog"] label').find((x) => x.text().replace(/\s*\*$/, '').trim() === label)
  if (!l) throw new Error('no field ' + label)
  return w.find<HTMLInputElement>('#' + l.attributes('for'))
}

const dialog = (w: VueWrapper) => w.find('[role="dialog"]')
const button = (w: VueWrapper, text: string, root = 'body') =>
  w.findAll(root === 'dialog' ? '[role="dialog"] button' : 'button').find((b) => b.text().trim() === text)

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
})
afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
})

describe('navigation group "Verwaltung"', () => {
  async function side(scopeAnswer: unknown) {
    const get = api({ '/web/admin/scope': scopeAnswer, '/web/analyses/directories': [] })
    const auth = useAuthStore()
    Object.assign(auth.http, { get })
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', name: 'home', component: Stub }, ...['adminUsers', 'adminGroups', 'adminSharing', 'adminRights', 'adminApiKeys', 'dashboard'].map((n) => ({ path: '/' + n, name: n, component: Stub }))] })
    await router.push('/')
    const w = mount(SideNav, { props: { rail: false }, global: { plugins: [router, i18n] } })
    mounted.push(w)
    await flushPromises()
    return w
  }

  it('is shown for site managers without API keys', async () => {
    const w = await side(scope())
    const btn = w.findAll('button.group-btn').find((b) => b.text().includes('Verwaltung'))!
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    const items = w.findAll('#nav-group-admin a').map((a) => a.text())
    expect(items).toEqual(['Benutzer', 'Gruppen', 'Freigaben', 'Wer darf was?'])
  })

  it('offers API keys to Sys Admins', async () => {
    const w = await side(scope({ sysAdmin: true }))
    expect(w.findAll('#nav-group-admin a').map((a) => a.text())).toContain('API-Schlüssel')
  })

  it('is hidden without managed sites and on 403', async () => {
    let w = await side(scope({ sites: [{ ...scope().sites[0], canManage: false }] }))
    expect(w.find('#nav-group-admin').exists()).toBe(false)
    w.unmount()
    mounted.pop()
    setActivePinia(createPinia())
    w = await side(httpError(403))
    expect(w.find('#nav-group-admin').exists()).toBe(false)
  })
})

describe('Benutzer', () => {
  it('lists the users and saves changed memberships', async () => {
    const saved = user({ version: 'v2', groups: [{ id: 40, name: 'Musterwerk – Bearbeiten', rights: ['READ', 'WRITE', 'DELETE'] }, { id: 41, name: 'Zentrale – Ansehen', rights: ['READ'] }] })
    const get = api({ '/web/admin/scope': scope(), '/web/admin/users': [user(), user({ id: 502, account: 'mmuster', firstName: 'Max', mfa: false, groups: [] })], '/web/admin/groups': groups() })
    const put = vi.fn(async () => saved)
    const w = await mountView(AdminUsersView, { get, put })
    const rows = w.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('erika.muster@example.com')
    expect(rows[0].text()).toContain('Musterwerk – Bearbeiten, Zentrale – Ansehen')

    await rows[0].find('button').trigger('click')
    await flushPromises()
    expect(dialog(w).text()).toContain('Benutzer „Erika Muster“')
    /* not a Sys Admin: no Sys Admin checkbox */
    expect(w.findAll('[role="dialog"] label').some((l) => l.text() === 'Sys Admin')).toBe(false)
    /* group 41 is not writable: read-only */
    const boxes41 = w.findAll('[role="dialog"] input[aria-label$="Zentrale – Ansehen"]')
    expect(boxes41.every((b) => (b.element as HTMLInputElement).disabled)).toBe(true)
    await w.find('[role="dialog"] input[aria-label="Löschen – Musterwerk – Bearbeiten"]').setValue(true)
    await button(w, 'Speichern', 'dialog')!.trigger('click')
    await flushPromises()
    expect(put).toHaveBeenCalledWith('/web/admin/users/501/groups', {
      version: 'v1',
      groups: [
        { id: 40, rights: ['READ', 'WRITE', 'DELETE'] },
        { id: 41, rights: ['READ'] },
      ],
    })
    expect(dialog(w).text()).toContain('wurde gespeichert')
  })

  it('shows the GRANT refusal and a version conflict', async () => {
    const get = api({ '/web/admin/scope': scope(), '/web/admin/users': [user()], '/web/admin/groups': groups() })
    const put = vi.fn(async () => {
      throw httpError(403, { error: 'GRANT' })
    })
    const w = await mountView(AdminUsersView, { get, put })
    await w.find('tbody tr button').trigger('click')
    await flushPromises()
    await w.find('[role="dialog"] input[aria-label="Löschen – Musterwerk – Bearbeiten"]').setValue(true)
    await button(w, 'Speichern', 'dialog')!.trigger('click')
    await flushPromises()
    expect(w.find('[role="dialog"] [role="alert"]').text()).toBe('Sie können nur Rechte vergeben, die Sie in dieser Gruppe selbst haben.')

    put.mockImplementationOnce(async () => {
      throw httpError(409, user({ version: 'v9', lastName: 'Beispiel' }))
    })
    await field(w, 'Vorname').setValue('Eri')
    await button(w, 'Speichern', 'dialog')!.trigger('click')
    await flushPromises()
    expect(w.find('[role="dialog"] [role="alert"]').text()).toContain('inzwischen geändert')
    expect(field(w, 'Nachname').element.value).toBe('Beispiel')
    expect(field(w, 'Vorname').element.value).toBe('Erika')
  })

  it('creates a user, reports an existing account and clears the passwords', async () => {
    const get = api({ '/web/admin/scope': scope(), '/web/admin/users': [], '/web/admin/groups': [] })
    const post = vi.fn(async () => {
      throw httpError(409, { error: 'ACCOUNT_EXISTS' })
    })
    const w = await mountView(AdminUsersView, { get, post })
    await button(w, 'Neuer Benutzer')!.trigger('click')
    await flushPromises()
    await field(w, 'Benutzername').setValue('emuster')
    await field(w, 'Passwort').setValue('kurz')
    await field(w, 'Passwort wiederholen').setValue('kurz')
    await button(w, 'Anlegen', 'dialog')!.trigger('click')
    await flushPromises()
    expect(post).not.toHaveBeenCalled()
    expect(dialog(w).text()).toContain('mindestens 8 Zeichen')
    expect(field(w, 'Passwort').element.value).toBe('')

    await field(w, 'Passwort').setValue('geheim123')
    await field(w, 'Passwort wiederholen').setValue('geheim123')
    await button(w, 'Anlegen', 'dialog')!.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/admin/users', { directory: 30, account: 'emuster', firstName: '', lastName: '', email: '', enabled: true, password: 'geheim123' })
    expect(dialog(w).text()).toContain('Diesen Benutzernamen gibt es bereits.')
    expect(field(w, 'Passwort').element.value).toBe('')
    expect(field(w, 'Passwort wiederholen').element.value).toBe('')
  })

  it('offers Sys Admin and 2FA reset to Sys Admins, never for oneself', async () => {
    const get = api({ '/web/admin/scope': scope({ sysAdmin: true }), '/web/admin/users': [user(), user({ id: SELF, account: 'admin', firstName: null, lastName: null })], '/web/admin/groups': groups() })
    const post = vi.fn(async () => undefined)
    const w = await mountView(AdminUsersView, { get, post })
    const rowOf = (account: string) => w.findAll('tbody tr').find((r) => r.find('th').text() === account)!
    await rowOf('admin').find('button').trigger('click')
    await flushPromises()
    expect(w.findAll('[role="dialog"] label').some((l) => l.text() === 'Sys Admin')).toBe(false)
    expect(dialog(w).text()).toContain('Ihr eigenes Konto')
    await button(w, 'Schließen', 'dialog')!.trigger('click')
    await flushPromises()

    await rowOf('emuster').find('button').trigger('click')
    await flushPromises()
    expect(w.findAll('[role="dialog"] label').some((l) => l.text() === 'Sys Admin')).toBe(true)
    await button(w, 'Passwort setzen', 'dialog')!.trigger('click')
    await field(w, 'Neues Passwort').setValue('geheim123')
    await field(w, 'Passwort wiederholen').setValue('geheim123')
    const set = w.findAll('[role="dialog"] .adm-subform button').find((b) => b.text() === 'Passwort setzen')!
    await set.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/admin/users/501/password', { password: 'geheim123' })
    expect(dialog(w).text()).toContain('Das Passwort wurde gesetzt.')

    await button(w, '2FA zurücksetzen', 'dialog')!.trigger('click')
    await flushPromises()
    const confirm = w.findAll('[role="dialog"]').at(-1)!
    await confirm.findAll('button').find((b) => b.text() === '2FA zurücksetzen')!.trigger('click')
    await flushPromises()
    expect((post.mock.calls.at(-1) as unknown[] | undefined)?.[0]).toBe('/web/admin/users/501/mfa-reset')
    expect(dialog(w).text()).toContain('wurde zurückgesetzt')
  })
})

describe('Gruppen', () => {
  it('lists the groups and allows deleting only without members', async () => {
    const get = api({
      '/web/admin/scope': scope(),
      '/web/admin/groups': groups(),
      '/web/admin/groups/40/members': [{ user: { id: 501, account: 'emuster', name: 'Erika Muster' }, rights: ['READ', 'WRITE'] }],
      '/web/admin/groups/42/members': [],
      '/web/admin/users': [user(), user({ id: 502, account: 'mmuster', firstName: 'Max', groups: [] })],
    })
    const put = vi.fn(async () => ({}))
    const del = vi.fn(async () => undefined)
    const w = await mountView(AdminGroupsView, { get, put, delete: del })
    expect(w.findAll('tbody tr')).toHaveLength(3)

    const open = async (name: string) => {
      await w.findAll('tbody tr').find((r) => r.find('th').text() === name)!.find('button').trigger('click')
      await flushPromises()
    }
    await open('Musterwerk – Bearbeiten')
    expect((button(w, 'Gruppe löschen', 'dialog')!.element as HTMLButtonElement).disabled).toBe(true)
    /* add a member and save */
    await w.find('[role="dialog"] .adm-add select').setValue('502')
    await button(w, 'Benutzer hinzufügen', 'dialog')!.trigger('click')
    await button(w, 'Speichern', 'dialog')!.trigger('click')
    await flushPromises()
    expect(put).toHaveBeenCalledWith('/web/admin/groups/40/members', {
      version: 'g1',
      members: [
        { user: 501, rights: ['READ', 'WRITE'] },
        { user: 502, rights: ['READ'] },
      ],
    })
    await button(w, 'Schließen', 'dialog')!.trigger('click')
    await flushPromises()

    await open('Musterwerk – Ansehen')
    const delBtn = button(w, 'Gruppe löschen', 'dialog')!
    expect((delBtn.element as HTMLButtonElement).disabled).toBe(false)
    await delBtn.trigger('click')
    await flushPromises()
    const confirm = w.findAll('[role="dialog"]').at(-1)!
    await confirm.findAll('button').find((b) => b.text() === 'Gruppe löschen')!.trigger('click')
    await flushPromises()
    expect(del).toHaveBeenCalledWith('/web/admin/groups/42')
    expect(w.text()).toContain('in den Papierkorb verschoben')
  })
})

describe('Freigaben', () => {
  it('previews and applies the changed presets', async () => {
    const sharing: SiteSharing = {
      groups: { VIEW: { id: 42, name: 'Musterwerk – Ansehen' }, EDIT: null, MANAGE: null },
      users: [{ id: 501, account: 'emuster', name: 'Erika Muster', preset: 'VIEW' }],
    }
    const get = api({ '/web/admin/scope': scope(), '/web/admin/sites/1/sharing': sharing, '/web/admin/users': [user()] })
    const post = vi.fn(async (p: string) =>
      p.endsWith('/preview')
        ? { actions: [{ type: 'CREATE_OBJECT', name: 'Musterwerk – Bearbeiten', className: 'Group', parent: { id: 20, name: 'Gruppen' } }, { type: 'ADD_RELATIONSHIP', text: 'emuster wird Mitglied von „Musterwerk – Bearbeiten“' }], warnings: ['Hinweis'] }
        : { done: 1, failed: [{ text: 'emuster → Musterwerk – Bearbeiten', error: 'keine Berechtigung' }] },
    )
    const w = await mountView(AdminSharingView, { get, post })
    /* one managed site: chosen at once */
    expect(w.findAll('tbody tr')).toHaveLength(1)
    expect((button(w, 'Übernehmen')!.element as HTMLButtonElement).disabled).toBe(true)
    await w.find('tbody select').setValue('EDIT')
    await button(w, 'Vorschau')!.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/admin/sites/1/sharing/preview', { assignments: { '501': 'EDIT' }, includeSubtree: true })
    expect(w.find('.adm-plan').text()).toContain('Anlegen: „Musterwerk – Bearbeiten“ (Group) unter „Gruppen“')
    expect(w.find('.adm-plan').text()).toContain('Hinweis')
    await button(w, 'Übernehmen')!.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenLastCalledWith('/web/admin/sites/1/sharing/apply', { assignments: { '501': 'EDIT' }, includeSubtree: true })
    expect(w.text()).toContain('1 Änderung ausgeführt · 1 fehlgeschlagen')
    expect(w.text()).toContain('keine Berechtigung')
  })
})

describe('API-Schlüssel', () => {
  const key: ApiKey = { id: 'k1', userId: 501, userName: 'emuster', name: 'Export', created: Date.UTC(2026, 9, 1), createdBy: SELF, expires: null, lastUsed: null, revoked: false }

  it('creates a key and shows it once', async () => {
    const get = api({ '/web/admin/scope': scope({ sysAdmin: true }), '/web/admin/apikeys': [key], '/web/admin/users': [user()] })
    const post = vi.fn(async () => ({ ...key, id: 'k2', name: 'Import', key: 'jevis_beispiel_123' }))
    const w = await mountView(AdminApiKeysView, { get, post })
    expect(w.find('tbody').text()).toContain('Export')
    await button(w, 'Neuer API-Schlüssel')!.trigger('click')
    await flushPromises()
    await button(w, 'Erzeugen', 'dialog')!.trigger('click')
    expect(dialog(w).text()).toContain('Bitte wählen Sie einen Benutzer.')
    await w.find('#adm-key-user').setValue('501')
    await w.find('#adm-key-name').setValue('Import')
    await button(w, 'Erzeugen', 'dialog')!.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/admin/apikeys', { userId: 501, name: 'Import', expires: null })
    expect(w.find('.adm-key-text').text()).toBe('jevis_beispiel_123')
    await button(w, 'Schließen', 'dialog')!.trigger('click')
    await flushPromises()
    expect(w.text()).not.toContain('jevis_beispiel_123')
  })

  it('is refused for site managers', async () => {
    const get = api({ '/web/admin/scope': scope() })
    const w = await mountView(AdminApiKeysView, { get })
    expect(w.text()).toContain('nur Sys Admins')
    expect(get).not.toHaveBeenCalledWith('/web/admin/apikeys')
  })
})
