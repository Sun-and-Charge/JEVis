/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it, vi } from 'vitest'
import {
  applySharing,
  createApiKey,
  createUser,
  deleteApiKey,
  deleteGroup,
  groupsPath,
  loadObjectRights,
  loadUserRights,
  previewSharing,
  setMembers,
  setUserGroups,
  updateUser,
  usersPath,
  type AdminScope,
  type AdminUser,
  type EffectiveRights,
} from '../src/api/admin'
import {
  adminErrorKey,
  apiKeyProblems,
  canAdminister,
  changedAssignments,
  conflictState,
  emptyUserForm,
  errorCode,
  filterUsers,
  flagRights,
  groupDirectories,
  isEmptyDiff,
  membershipDiff,
  membershipsToSend,
  normalizeRights,
  planActionText,
  presetOfRights,
  presetRights,
  refName,
  rightReasons,
  sameUserForm,
  sharingRows,
  toggleRight,
  userDirectories,
  userFormOf,
  userFormProblems,
  userName,
} from '../src/admin/admin'
import { adminPages, groupOfRoute } from '../src/nav/menu'
import { httpError } from './fixtures/edit'

/* E10.1 administration: api paths and pure helpers; neutral placeholder data. */

const t = (key: string, v?: Record<string, unknown>) => `${key.split('.').pop()}:${JSON.stringify(v ?? {})}`

function user(over: Partial<AdminUser> = {}): AdminUser {
  return {
    id: 501,
    account: 'emuster',
    firstName: 'Erika',
    lastName: 'Muster',
    email: 'erika.muster@example.com',
    enabled: true,
    sysAdmin: false,
    mfa: false,
    directory: { id: 30, name: 'Benutzer', site: { id: 1, name: 'Musterwerk' } },
    groups: [{ id: 40, name: 'Musterwerk – Bearbeiten', rights: ['READ', 'WRITE'] }],
    canWrite: true,
    version: 'v1',
    ...over,
  }
}

const scope: AdminScope = {
  sysAdmin: false,
  sites: [
    { id: 1, name: 'Musterwerk', className: 'Organization', groupDirectory: { id: 20, name: 'Gruppen' }, userDirectory: { id: 30, name: 'Benutzer' }, canManage: true },
    { id: 2, name: 'Außenlager', className: 'Building', groupDirectory: null, userDirectory: null, canManage: false },
  ],
}

describe('admin api', () => {
  it('uses the paths of the contract', async () => {
    expect(usersPath()).toBe('/web/admin/users')
    expect(usersPath(' muster ', 7)).toBe('/web/admin/users?q=muster&site=7')
    expect(groupsPath(null, 3)).toBe('/web/admin/groups?site=3')
    const post = vi.fn(async () => ({}))
    const put = vi.fn(async () => ({}))
    const get = vi.fn(async () => ({}))
    const del = vi.fn(async () => undefined)
    await createUser({ post } as never, { directory: 30, account: 'a', firstName: '', lastName: '', email: '', enabled: true, password: 'geheim123' })
    await updateUser({ put } as never, 5, { version: 'v1', firstName: 'E', lastName: 'M', email: '', enabled: true })
    await setUserGroups({ put } as never, 5, 'v1', [{ id: 40, rights: ['READ'] }])
    await setMembers({ put } as never, 40, 'g1', [{ user: 5, rights: ['READ'] }])
    await deleteGroup({ delete: del } as never, 40)
    await previewSharing({ post } as never, 1, { assignments: { '5': 'VIEW' }, includeSubtree: true })
    await applySharing({ post } as never, 1, { assignments: { '5': null }, includeSubtree: false })
    await loadObjectRights({ get } as never, 9)
    await loadUserRights({ get } as never, 5)
    await createApiKey({ post } as never, { userId: 5, name: 'Export', expires: null })
    await deleteApiKey({ delete: del } as never, 'k/1')
    expect(post.mock.calls.map((c) => (c as unknown[])[0])).toEqual([
      '/web/admin/users',
      '/web/admin/sites/1/sharing/preview',
      '/web/admin/sites/1/sharing/apply',
      '/web/admin/apikeys',
    ])
    expect(put.mock.calls).toEqual([
      ['/web/admin/users/5', { version: 'v1', firstName: 'E', lastName: 'M', email: '', enabled: true }],
      ['/web/admin/users/5/groups', { version: 'v1', groups: [{ id: 40, rights: ['READ'] }] }],
      ['/web/admin/groups/40/members', { version: 'g1', members: [{ user: 5, rights: ['READ'] }] }],
    ])
    expect(get.mock.calls.map((c) => (c as unknown[])[0])).toEqual(['/web/admin/rights?object=9', '/web/admin/rights?user=5'])
    expect(del.mock.calls.map((c) => (c as unknown[])[0])).toEqual(['/web/admin/groups/40', '/web/admin/apikeys/k%2F1'])
  })
})

describe('rights and presets', () => {
  it('normalizes, toggles and maps presets', () => {
    expect(normalizeRights(['DELETE', 'READ', 'READ', 'FOO', 3])).toEqual(['READ', 'DELETE'])
    expect(toggleRight(['READ'], 'CREATE', true)).toEqual(['READ', 'CREATE'])
    expect(toggleRight(['READ', 'CREATE'], 'READ', false)).toEqual(['CREATE'])
    expect(presetRights('EDIT')).toEqual(['READ', 'WRITE', 'EXECUTE', 'CREATE'])
    expect(presetOfRights(['READ'])).toBe('VIEW')
    expect(presetOfRights(['DELETE', 'READ', 'WRITE', 'EXECUTE', 'CREATE'])).toBe('MANAGE')
    expect(presetOfRights([])).toBeNull()
    expect(presetOfRights(['READ', 'DELETE'])).toBe('CUSTOM')
    expect(flagRights({ read: true, write: false, delete: true })).toEqual(['READ', 'DELETE'])
  })

  it('computes the membership changes', () => {
    const before = [
      { id: 1, rights: ['READ' as const] },
      { id: 2, rights: ['READ' as const, 'WRITE' as const] },
      { id: 3, rights: ['READ' as const] },
    ]
    const after = [
      { id: 1, rights: ['READ' as const] },
      { id: 2, rights: ['READ' as const] },
      { id: 3, rights: [] },
      { id: 4, rights: ['READ' as const] },
    ]
    const d = membershipDiff(before, after)
    expect(d.added).toEqual([{ id: 4, rights: ['READ'] }])
    expect(d.removed).toEqual([{ id: 3, rights: ['READ'] }])
    expect(d.changed).toEqual([{ id: 2, before: ['READ', 'WRITE'], after: ['READ'] }])
    expect(isEmptyDiff(membershipDiff(before, [...before].reverse()))).toBe(true)
    expect(membershipsToSend(after)).toEqual([
      { id: 1, rights: ['READ'] },
      { id: 2, rights: ['READ'] },
      { id: 4, rights: ['READ'] },
    ])
  })
})

describe('user form', () => {
  it('checks a new user', () => {
    const f = emptyUserForm()
    expect(userFormProblems(f, true).map((p) => p.field)).toEqual(['directory', 'account', 'password'])
    const ok = { ...f, directory: 30, account: 'emuster', email: 'erika.muster@example.com', password: 'geheim123', repeat: 'geheim123' }
    expect(userFormProblems(ok, true)).toEqual([])
    expect(userFormProblems({ ...ok, account: 'e muster' }, true)[0].key).toBe('admin.problem.accountChars')
    expect(userFormProblems({ ...ok, repeat: 'anders123' }, true)).toEqual([{ field: 'repeat', key: 'password.mismatch' }])
    expect(userFormProblems({ ...ok, email: 'kein-at' }, false)).toEqual([{ field: 'email', key: 'admin.problem.email' }])
  })

  it('reads a user and compares without the password fields', () => {
    const f = userFormOf(user())
    expect(f).toMatchObject({ directory: 30, account: 'emuster', firstName: 'Erika', enabled: true, password: '' })
    expect(sameUserForm(f, { ...f, password: 'x', lastName: 'Muster ' })).toBe(true)
    expect(sameUserForm(f, { ...f, enabled: false })).toBe(false)
    expect(userName(user())).toBe('Erika Muster')
    expect(userName(user({ firstName: null, lastName: ' ' }))).toBe('emuster')
  })

  it('searches account, names, e-mail and groups', () => {
    const list = [user(), user({ id: 502, account: 'mmuster', firstName: 'Max', email: null, groups: [] })]
    expect(filterUsers(list, 'bearbeiten', 'de').map((u) => u.id)).toEqual([501])
    expect(filterUsers(list, 'max', 'de').map((u) => u.id)).toEqual([502])
    expect(filterUsers(list, '', 'de')).toHaveLength(2)
  })
})

describe('scope', () => {
  it('offers the administration to Sys Admins and site managers only', () => {
    expect(canAdminister(null)).toBe(false)
    expect(canAdminister(scope)).toBe(true)
    expect(canAdminister({ sysAdmin: false, sites: [scope.sites[1]] })).toBe(false)
    expect(canAdminister({ sysAdmin: true, sites: [] })).toBe(true)
    expect(userDirectories(scope).map((d) => d.directory)).toEqual([30])
    expect(groupDirectories({ sysAdmin: false, sites: [{ ...scope.sites[0], groupDirectory: 21 }] }).map((d) => d.directory)).toEqual([21])
    expect(adminPages(false, true)).toEqual([])
    expect(adminPages(true, false)).toEqual(['adminUsers', 'adminGroups', 'adminSharing', 'adminRights'])
    expect(adminPages(true, true)).toContain('adminApiKeys')
    expect(groupOfRoute('adminGroups')).toBe('admin')
    expect(refName({ id: 1, name: 'Musterwerk' })).toBe('Musterwerk')
    expect(refName(null)).toBe('')
  })
})

describe('site sharing', () => {
  it('sends only the changed users', () => {
    const rows = sharingRows({
      groups: { VIEW: { id: 1, name: 'Musterwerk – Ansehen' }, EDIT: null, MANAGE: null },
      users: [
        { id: 5, account: 'emuster', name: 'Erika Muster', preset: 'VIEW' },
        { id: 6, account: 'mmuster', name: null, preset: 'NONE' },
      ],
    })
    expect(rows.map((r) => [r.name, r.current])).toEqual([
      ['Erika Muster', 'VIEW'],
      ['mmuster', null],
    ])
    expect(changedAssignments(rows)).toEqual({})
    rows[0].wanted = null
    rows[1].wanted = 'EDIT'
    expect(changedAssignments(rows)).toEqual({ '5': null, '6': 'EDIT' })
  })

  it('describes planned changes', () => {
    expect(planActionText({ type: 'ADD_RELATIONSHIP', text: 'fertig' }, t)).toBe('fertig')
    expect(planActionText({ type: 'CREATE_OBJECT', name: 'Musterwerk – Ansehen', className: 'Group', parent: 'Gruppen' }, t)).toBe(
      'create:{"name":"Musterwerk – Ansehen","className":"Group","parent":"Gruppen"}',
    )
    expect(planActionText({ type: 'REMOVE_RELATIONSHIP', from: { id: 5, name: 'emuster' }, to: { id: 1, name: 'G' }, category: 'MEMBERSHIP' }, t)).toContain('"from":"emuster"')
  })
})

describe('who may do what', () => {
  it('lists the five rights with their groups', () => {
    const r: EffectiveRights = {
      userId: 5,
      userName: 'emuster',
      sysAdmin: false,
      enabled: true,
      publicObject: false,
      ownObject: false,
      read: true,
      write: false,
      create: false,
      delete: false,
      execute: false,
      readSources: [{ groupId: 40, groupName: 'Musterwerk – Ansehen', roles: [{ id: 70, name: 'Leser' }] }],
      writeSources: [],
      createSources: [],
      deleteSources: [],
      executeSources: [],
    }
    const reasons = rightReasons(r)
    expect(reasons.map((x) => x.granted)).toEqual([true, false, false, false, false])
    expect(reasons[0].sources).toEqual([{ group: 'Musterwerk – Ansehen', roles: ['Leser'] }])
  })
})

describe('errors', () => {
  it('reads the error codes of JSON and text answers', () => {
    expect(errorCode(httpError(409, { error: 'ACCOUNT_EXISTS' }))).toBe('ACCOUNT_EXISTS')
    expect(errorCode(httpError(403, 'GRANT: DELETE on group 40'))).toBe('GRANT')
    expect(errorCode(httpError(400, 'nicht erlaubt'))).toBeNull()
    expect(errorCode(new Error('x'))).toBeNull()
    expect(adminErrorKey(httpError(400, { code: 'SELF' }))).toBe('admin.error.SELF')
    expect(adminErrorKey(httpError(400, { code: 'OTHER' }))).toBeNull()
  })

  it('takes the current state of a version conflict', () => {
    expect(conflictState<AdminUser>(httpError(409, user({ version: 'v2' })))?.version).toBe('v2')
    expect(conflictState<AdminUser>(httpError(409, { current: user({ version: 'v3' }) }))?.version).toBe('v3')
    expect(conflictState<AdminUser>(httpError(409, { error: 'ACCOUNT_EXISTS' }))).toBeNull()
    expect(conflictState<AdminUser>(httpError(400, user()))).toBeNull()
  })

  it('checks the api key form', () => {
    const today = new Date(2026, 10, 27)
    expect(apiKeyProblems({ userId: null, name: ' ', expires: '' }, today)).toEqual(['user', 'name'])
    expect(apiKeyProblems({ userId: 5, name: 'Export', expires: '2026-11-26' }, today)).toEqual(['expires'])
    expect(apiKeyProblems({ userId: 5, name: 'Export', expires: '2026-11-27' }, today)).toEqual([])
  })
})
