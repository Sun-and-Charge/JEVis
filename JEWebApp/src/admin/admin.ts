/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { HttpError } from '../api/http'
import type { AdminRef, AdminScope, AdminSite, AdminUser, EffectiveRights, Membership, PlanAction, Preset, Right, SiteSharing, Version } from '../api/admin'
import { matchesTerms, searchTerms } from '../modules/search'
import { passwordProblem } from '../passwordRules'

/*
 * Pure helpers of the administration pages (E10.1): rights and presets, membership changes, the user form and its
 * checks, the site sharing assignments and the error codes of the server.
 */

/** All rights in display order (LESEN, SCHREIBEN, AUSFÜHREN, ERSTELLEN, LÖSCHEN). */
export const RIGHTS: readonly Right[] = ['READ', 'WRITE', 'EXECUTE', 'CREATE', 'DELETE']
export const PRESETS: readonly Preset[] = ['VIEW', 'EDIT', 'MANAGE']

const PRESET_RIGHTS: Record<Preset, readonly Right[]> = {
  VIEW: ['READ'],
  EDIT: ['READ', 'WRITE', 'EXECUTE', 'CREATE'],
  MANAGE: RIGHTS,
}

/** The known rights of a list, without duplicates, in display order. */
export function normalizeRights(rights: readonly unknown[] | null | undefined): Right[] {
  const set = new Set((rights ?? []).filter((r): r is Right => typeof r === 'string' && (RIGHTS as readonly string[]).includes(r)))
  return RIGHTS.filter((r) => set.has(r))
}

export function presetRights(preset: Preset): Right[] {
  return [...PRESET_RIGHTS[preset]]
}

/** The preset with exactly these rights, null for none, 'CUSTOM' for another combination. */
export function presetOfRights(rights: readonly Right[]): Preset | null | 'CUSTOM' {
  const norm = normalizeRights(rights)
  if (!norm.length) return null
  for (const p of PRESETS) {
    const want = PRESET_RIGHTS[p]
    if (want.length === norm.length && want.every((r) => norm.includes(r))) return p
  }
  return 'CUSTOM'
}

/** Rights with one right switched on or off. */
export function toggleRight(rights: readonly Right[], right: Right, on: boolean): Right[] {
  const set = new Set(normalizeRights(rights))
  if (on) set.add(right)
  else set.delete(right)
  return RIGHTS.filter((r) => set.has(r))
}

export function sameRights(a: readonly Right[], b: readonly Right[]): boolean {
  const x = normalizeRights(a)
  const y = normalizeRights(b)
  return x.length === y.length && x.every((r, i) => r === y[i])
}

/** Name of a reference that the server sends as object, plain name or id. */
export function refName(ref: AdminRef | string | number | null | undefined): string {
  if (ref == null) return ''
  if (typeof ref === 'string') return ref
  if (typeof ref === 'number') return String(ref)
  return ref.name ?? String(ref.id)
}

/** Id of a reference that the server sends as object or id; null for names and nothing. */
export function refId(ref: AdminRef | string | number | null | undefined): number | null {
  if (typeof ref === 'number') return ref
  if (ref && typeof ref === 'object' && typeof ref.id === 'number') return ref.id
  return null
}

/** Display name of a user: "Vorname Nachname", else the account. */
export function userName(u: Pick<AdminUser, 'account' | 'firstName' | 'lastName'>): string {
  const full = [u.firstName, u.lastName].filter((s) => s && s.trim()).join(' ').trim()
  return full || u.account
}

/* ---------- memberships ---------- */

export interface MembershipDiff {
  added: Membership[]
  removed: Membership[]
  changed: { id: number; before: Right[]; after: Right[] }[]
}

/** The memberships that `after` adds, removes or changes against `before` (a membership without rights is none). */
export function membershipDiff(before: readonly Membership[], after: readonly Membership[]): MembershipDiff {
  const b = new Map(before.filter((m) => normalizeRights(m.rights).length).map((m) => [m.id, normalizeRights(m.rights)]))
  const a = new Map(after.filter((m) => normalizeRights(m.rights).length).map((m) => [m.id, normalizeRights(m.rights)]))
  const diff: MembershipDiff = { added: [], removed: [], changed: [] }
  for (const [id, rights] of a) {
    const old = b.get(id)
    if (!old) diff.added.push({ id, rights })
    else if (!sameRights(old, rights)) diff.changed.push({ id, before: old, after: rights })
  }
  for (const [id, rights] of b) if (!a.has(id)) diff.removed.push({ id, rights })
  return diff
}

export function isEmptyDiff(d: MembershipDiff): boolean {
  return !d.added.length && !d.removed.length && !d.changed.length
}

/** The memberships to send: only those with rights, rights normalized. */
export function membershipsToSend(list: readonly Membership[]): Membership[] {
  return list.map((m) => ({ id: m.id, rights: normalizeRights(m.rights) })).filter((m) => m.rights.length)
}

/* ---------- users ---------- */

export interface UserForm {
  directory: number | null
  account: string
  firstName: string
  lastName: string
  email: string
  enabled: boolean
  sysAdmin: boolean
  password: string
  repeat: string
}

export function emptyUserForm(directory: number | null = null): UserForm {
  return { directory, account: '', firstName: '', lastName: '', email: '', enabled: true, sysAdmin: false, password: '', repeat: '' }
}

export function userFormOf(u: AdminUser): UserForm {
  return {
    directory: u.directory?.id ?? null,
    account: u.account,
    firstName: u.firstName ?? '',
    lastName: u.lastName ?? '',
    email: u.email ?? '',
    enabled: u.enabled,
    sysAdmin: u.sysAdmin,
    password: '',
    repeat: '',
  }
}

/** Same data (the password fields are not compared: they are a separate action). */
export function sameUserForm(a: UserForm, b: UserForm): boolean {
  return (
    a.directory === b.directory &&
    a.account.trim() === b.account.trim() &&
    a.firstName.trim() === b.firstName.trim() &&
    a.lastName.trim() === b.lastName.trim() &&
    a.email.trim() === b.email.trim() &&
    a.enabled === b.enabled &&
    a.sysAdmin === b.sysAdmin
  )
}

export interface FormProblem {
  field: string
  /** i18n key */
  key: string
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const ACCOUNT = /^[^\s/\\]+$/

/** Checks of the user data; `isNew` also checks directory, account and the initial password. */
export function userFormProblems(form: UserForm, isNew: boolean): FormProblem[] {
  const problems: FormProblem[] = []
  if (isNew) {
    if (form.directory == null) problems.push({ field: 'directory', key: 'admin.problem.directory' })
    const account = form.account.trim()
    if (!account) problems.push({ field: 'account', key: 'admin.problem.account' })
    else if (!ACCOUNT.test(account)) problems.push({ field: 'account', key: 'admin.problem.accountChars' })
  }
  const email = form.email.trim()
  if (email && !EMAIL.test(email)) problems.push({ field: 'email', key: 'admin.problem.email' })
  if (isNew) {
    const p = passwordProblem(form.password, form.repeat)
    if (p) problems.push({ field: p === 'password.mismatch' ? 'repeat' : 'password', key: p })
  }
  return problems
}

/** Problem of the "Passwort setzen" sub-form (i18n key) or null. */
export function newPasswordProblem(password: string, repeat: string): string | null {
  return passwordProblem(password, repeat)
}

/** Users matching all search words (account, names, e-mail, groups). */
export function filterUsers(users: readonly AdminUser[], text: string, locale: string): AdminUser[] {
  const terms = searchTerms(text, locale)
  if (!terms.length) return [...users]
  return users.filter((u) => matchesTerms(terms, [u.account, u.firstName, u.lastName, u.email, ...u.groups.map((g) => g.name)], locale))
}

export function sortByName<T>(list: readonly T[], name: (x: T) => string, locale: string): T[] {
  return [...list].sort((a, b) => name(a).localeCompare(name(b), locale, { sensitivity: 'base' }))
}

/* ---------- scope ---------- */

/** The administration is offered to Sys Admins and to managers of at least one site. */
export function canAdminister(scope: AdminScope | null | undefined): boolean {
  return !!scope && (scope.sysAdmin === true || (scope.sites ?? []).some((s) => s.canManage))
}

/** Sites with a writable user directory (target of "Neuer Benutzer"). */
export function userDirectories(scope: AdminScope | null | undefined): { site: AdminSite; directory: number; name: string }[] {
  return (scope?.sites ?? [])
    .map((site) => ({ site, directory: refId(site.userDirectory), name: refName(site.userDirectory) }))
    .filter((d): d is { site: AdminSite; directory: number; name: string } => d.directory != null)
}

/** Sites with a writable group directory (target of "Neue Gruppe"). */
export function groupDirectories(scope: AdminScope | null | undefined): { site: AdminSite; directory: number; name: string }[] {
  return (scope?.sites ?? [])
    .map((site) => ({ site, directory: refId(site.groupDirectory), name: refName(site.groupDirectory) }))
    .filter((d): d is { site: AdminSite; directory: number; name: string } => d.directory != null)
}

/* ---------- site sharing ---------- */

export type PresetChoice = Preset | null

export interface SharingRow {
  id: number
  account: string
  name: string
  /** the access the user has now (via the sharing groups) */
  current: PresetChoice
  /** the wanted access */
  wanted: PresetChoice
}

export function presetChoice(value: unknown): PresetChoice {
  return value === 'VIEW' || value === 'EDIT' || value === 'MANAGE' ? value : null
}

export function sharingRows(sharing: SiteSharing | null | undefined): SharingRow[] {
  return (sharing?.users ?? []).map((u) => {
    const current = presetChoice(u.preset)
    return { id: u.id, account: u.account, name: u.name || u.account, current, wanted: current }
  })
}

/** The changed users as assignments of the request (null removes the access). */
export function changedAssignments(rows: readonly SharingRow[]): Record<string, PresetChoice> {
  const out: Record<string, PresetChoice> = {}
  for (const r of rows) if (r.wanted !== r.current) out[String(r.id)] = r.wanted
  return out
}

/** Text of a planned change: the server's text, else composed from the action. */
export function planActionText(a: PlanAction, t: (key: string, values?: Record<string, unknown>) => string): string {
  if (a.text) return a.text
  switch (a.type) {
    case 'CREATE_OBJECT':
      return t('admin.sharing.action.create', { name: a.name ?? '', className: a.className ?? '', parent: refName(a.parent) })
    case 'ADD_RELATIONSHIP':
      return t('admin.sharing.action.add', { from: refName(a.from), to: refName(a.to), kind: relationLabel(a, t) })
    case 'REMOVE_RELATIONSHIP':
      return t('admin.sharing.action.remove', { from: refName(a.from), to: refName(a.to), kind: relationLabel(a, t) })
    default:
      return String(a.type)
  }
}

function relationLabel(a: PlanAction, t: (key: string) => string): string {
  if (a.category) {
    const key = 'admin.sharing.category.' + a.category
    const label = t(key)
    if (label !== key) return label
    return a.category
  }
  return a.relType != null ? String(a.relType) : ''
}

/* ---------- who may do what ---------- */

export interface RightReason {
  right: Right
  granted: boolean
  /** the groups (with covering roles) the right comes from */
  sources: { group: string; roles: string[] }[]
}

const SOURCE_FIELD: Record<Right, keyof EffectiveRights> = {
  READ: 'readSources',
  WRITE: 'writeSources',
  EXECUTE: 'executeSources',
  CREATE: 'createSources',
  DELETE: 'deleteSources',
}
const FLAG_FIELD: Record<Right, keyof EffectiveRights> = { READ: 'read', WRITE: 'write', EXECUTE: 'execute', CREATE: 'create', DELETE: 'delete' }

/** The five rights of a user on an object with the groups they come from. */
export function rightReasons(r: EffectiveRights): RightReason[] {
  return RIGHTS.map((right) => {
    const sources = (r[SOURCE_FIELD[right]] as EffectiveRights['readSources'] | undefined) ?? []
    return {
      right,
      granted: r[FLAG_FIELD[right]] === true,
      sources: sources.map((s) => ({ group: s.groupName ?? String(s.groupId), roles: (s.roles ?? []).map((x) => refName(x)) })),
    }
  })
}

/** Rights of a flags object (group rights) as list. */
export function flagRights(r: { read?: boolean; write?: boolean; execute?: boolean; create?: boolean; delete?: boolean }): Right[] {
  return RIGHTS.filter((right) => r[FLAG_FIELD[right] as 'read'] === true)
}

/* ---------- errors ---------- */

/** Error code of a server answer: JSON `{error|code}` or a plain text that starts with an upper-case code. */
export function errorCode(e: unknown): string | null {
  if (!(e instanceof HttpError)) return null
  const json = e.json<Record<string, unknown>>()
  if (json && typeof json === 'object' && !Array.isArray(json)) {
    for (const k of ['error', 'code', 'reason']) {
      const v = json[k]
      if (typeof v === 'string' && /^[A-Z][A-Z_]+$/.test(v)) return v
    }
  }
  const m = /^\s*([A-Z][A-Z_]{2,})\b/.exec(e.body ?? '')
  return m ? m[1] : null
}

/** The current state in the body of a 409 (version conflict), null if there is none. */
export function conflictState<T extends { id: number; version?: Version }>(e: unknown): T | null {
  if (!(e instanceof HttpError) || e.status !== 409) return null
  const json = e.json<Record<string, unknown>>()
  if (!json || typeof json !== 'object') return null
  const candidate = (typeof json.id === 'number' ? json : json.current) as T | undefined
  return candidate && typeof candidate === 'object' && typeof candidate.id === 'number' ? candidate : null
}

/** Message key of a known error code of the administration, or null. */
export function adminErrorKey(e: unknown): string | null {
  const code = errorCode(e)
  if (!code) return null
  const known = ['ACCOUNT_EXISTS', 'SELF', 'GRANT', 'HAS_MEMBERS', 'PASSWORD_POLICY']
  return known.includes(code) ? 'admin.error.' + code : null
}

/* ---------- API keys ---------- */

/** Fields of the "Neuer API-Schlüssel" form with a problem: user, name, expires (date not in the future). */
export function apiKeyProblems(form: { userId: number | null; name: string; expires: string }, today: Date = new Date()): string[] {
  const problems: string[] = []
  if (form.userId == null) problems.push('user')
  if (!form.name.trim()) problems.push('name')
  if (form.expires) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(form.expires)
    const day = m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : null
    const start = new Date(today.getFullYear(), today.getMonth(), today.getDate())
    if (!day || Number.isNaN(day.getTime()) || day.getTime() < start.getTime()) problems.push('expires')
  }
  return problems
}
