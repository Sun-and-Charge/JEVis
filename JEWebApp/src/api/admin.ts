/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import type { HttpClient } from './http'

/*
 * Administration of users, groups and rights (E10.1, docs/WEB-API.md "E10.1 users, groups, rights"): all under
 * /web/admin. The server checks every right; the answers mark what the caller may change (canWrite, canDelete,
 * canManage, sysAdmin) and the UI only hides or disables accordingly. Writes take the `version` of the read state and
 * answer 409 with the current state when it changed meanwhile.
 */

export type Right = 'READ' | 'WRITE' | 'EXECUTE' | 'CREATE' | 'DELETE'
export type Preset = 'VIEW' | 'EDIT' | 'MANAGE'
/** version of a read state, sent back with a write (opaque to the app) */
export type Version = string | number | null

export interface AdminRef {
  id: number
  name: string
}

export interface AdminSite {
  id: number
  name: string
  className: string
  /** directory new groups go into, null if none writable */
  groupDirectory: AdminRef | number | null
  /** directory new users go into, null if none writable */
  userDirectory: AdminRef | number | null
  /** the caller may write the site's group directory (site manager, "Verwalten") */
  canManage: boolean
}

export interface AdminScope {
  sysAdmin: boolean
  sites: AdminSite[]
}

export interface UserGroup {
  id: number
  name: string
  rights: Right[]
  /** the caller may change this membership (absent: assume yes when the user is writable) */
  canWrite?: boolean
  site?: AdminRef | string | null
}

export interface AdminUser {
  id: number
  account: string
  firstName: string | null
  lastName: string | null
  email: string | null
  enabled: boolean
  sysAdmin: boolean
  mfa: boolean
  directory: { id: number; name: string; site: AdminRef | string | null } | null
  groups: UserGroup[]
  canWrite: boolean
  version: Version
}

export interface NewUser {
  directory: number
  account: string
  firstName: string
  lastName: string
  email: string
  enabled: boolean
  password: string
}

export interface UserChange {
  version: Version
  firstName: string
  lastName: string
  email: string
  enabled: boolean
  sysAdmin?: boolean
}

export interface Membership {
  id: number
  rights: Right[]
}

/** answer of PUT users/{id}/groups: the user with the memberships now stored, groups not writable reported unchanged */
export interface UserGroupsResult extends Partial<AdminUser> {
  unchanged?: (AdminRef | number)[]
}

export interface AdminGroup {
  id: number
  name: string
  site: AdminRef | string | null
  path: string | null
  /** number of members */
  members: number
  canWrite: boolean
  canDelete: boolean
  version: Version
}

export interface GroupMember {
  user: { id: number; account: string; name: string | null }
  rights: Right[]
}

export interface SiteSharing {
  groups: Record<Preset, AdminRef | null>
  users: { id: number; account: string; name: string | null; preset: Preset | 'NONE' | null }[]
}

export interface SharingRequest {
  /** user id → preset; null removes the user from the sharing groups; users not listed stay unchanged */
  assignments: Record<string, Preset | null>
  includeSubtree: boolean
}

export type PlanActionType = 'CREATE_OBJECT' | 'ADD_RELATIONSHIP' | 'REMOVE_RELATIONSHIP'

/** One planned change (RightsPlan.Action); the server may add a ready-made `text`. */
export interface PlanAction {
  type: PlanActionType
  category?: string | null
  text?: string | null
  /** created object */
  name?: string | null
  className?: string | null
  parent?: AdminRef | string | null
  from?: AdminRef | string | null
  to?: AdminRef | string | null
  relType?: number | null
}

export interface SharingPlan {
  actions: PlanAction[]
  warnings: string[]
}

export interface SharingResult {
  done: PlanAction[] | number
  failed: { action?: PlanAction | null; text?: string | null; error?: string | null }[]
  warnings?: string[]
}

/* "Wer darf was?" (same answers as /permissions/object/{id} and /permissions/user/{id}) */
export interface PermissionSource {
  groupId: number
  groupName: string | null
  roles: AdminRef[]
}

export interface EffectiveRights {
  userId: number
  userName: string | null
  sysAdmin: boolean
  enabled: boolean
  publicObject: boolean
  ownObject: boolean
  read: boolean
  write: boolean
  create: boolean
  delete: boolean
  execute: boolean
  readSources: PermissionSource[]
  writeSources: PermissionSource[]
  createSources: PermissionSource[]
  deleteSources: PermissionSource[]
  executeSources: PermissionSource[]
  createViaExecuteClasses?: string[]
}

export interface ObjectRights {
  objectId: number
  objectName: string | null
  jevisClass: string | null
  publicObject: boolean
  /** false: only the caller's own row */
  complete: boolean
  ownerGroups: AdminRef[]
  users: EffectiveRights[]
}

export interface GroupRights {
  groupId: number
  groupName: string | null
  read: boolean
  write: boolean
  create: boolean
  delete: boolean
  execute: boolean
  roles: AdminRef[]
}

export interface UserRights {
  userId: number
  userName: string | null
  sysAdmin: boolean
  enabled: boolean
  groups: GroupRights[]
  roles: { roleId: number; roleName: string | null; groups: GroupRights[] }[]
  objectId?: number | null
  objectName?: string | null
  objectRights?: EffectiveRights | null
}

export interface ApiKey {
  id: string
  /** only in the answer of the creation, shown once */
  key?: string | null
  userId: number
  userName: string | null
  name: string
  created: number | null
  createdBy: number | null
  expires: number | null
  lastUsed: number | null
  revoked: boolean | null
}

export interface NewApiKey {
  userId: number
  name: string
  /** epoch ms, null: no expiry */
  expires: number | null
}

const BASE = '/web/admin'

function query(params: Record<string, string | number | null | undefined>): string {
  const parts = Object.entries(params)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
  return parts.length ? '?' + parts.join('&') : ''
}

export function loadScope(http: Pick<HttpClient, 'get'>): Promise<AdminScope> {
  return http.get<AdminScope>(`${BASE}/scope`)
}

/* ---------- users ---------- */

export function usersPath(q?: string | null, site?: number | null): string {
  return `${BASE}/users` + query({ q: q?.trim() || null, site: site ?? null })
}

export function listUsers(http: Pick<HttpClient, 'get'>, q?: string | null, site?: number | null): Promise<AdminUser[]> {
  return http.get<AdminUser[]>(usersPath(q, site))
}

export function createUser(http: Pick<HttpClient, 'post'>, user: NewUser): Promise<AdminUser> {
  return http.post<AdminUser>(`${BASE}/users`, user)
}

export function updateUser(http: Pick<HttpClient, 'put'>, id: number, change: UserChange): Promise<AdminUser> {
  return http.put<AdminUser>(`${BASE}/users/${id}`, change)
}

export function setUserPassword(http: Pick<HttpClient, 'post'>, id: number, password: string): Promise<void> {
  return http.post<void>(`${BASE}/users/${id}/password`, { password })
}

export function resetUserMfa(http: Pick<HttpClient, 'post'>, id: number): Promise<void> {
  return http.post<void>(`${BASE}/users/${id}/mfa-reset`)
}

export function setUserGroups(http: Pick<HttpClient, 'put'>, id: number, version: Version, groups: Membership[]): Promise<UserGroupsResult> {
  return http.put<UserGroupsResult>(`${BASE}/users/${id}/groups`, { version, groups })
}

/* ---------- groups ---------- */

export function groupsPath(q?: string | null, site?: number | null): string {
  return `${BASE}/groups` + query({ q: q?.trim() || null, site: site ?? null })
}

export function listGroups(http: Pick<HttpClient, 'get'>, q?: string | null, site?: number | null): Promise<AdminGroup[]> {
  return http.get<AdminGroup[]>(groupsPath(q, site))
}

export function createGroup(http: Pick<HttpClient, 'post'>, directory: number, name: string): Promise<AdminGroup> {
  return http.post<AdminGroup>(`${BASE}/groups`, { directory, name })
}

export function renameGroup(http: Pick<HttpClient, 'put'>, id: number, version: Version, name: string): Promise<AdminGroup> {
  return http.put<AdminGroup>(`${BASE}/groups/${id}`, { version, name })
}

export function deleteGroup(http: Pick<HttpClient, 'delete'>, id: number): Promise<void> {
  return http.delete(`${BASE}/groups/${id}`)
}

export function loadMembers(http: Pick<HttpClient, 'get'>, id: number): Promise<GroupMember[]> {
  return http.get<GroupMember[]>(`${BASE}/groups/${id}/members`)
}

export function setMembers(http: Pick<HttpClient, 'put'>, id: number, version: Version, members: { user: number; rights: Right[] }[]): Promise<unknown> {
  return http.put<unknown>(`${BASE}/groups/${id}/members`, { version, members })
}

/* ---------- site sharing ---------- */

export function loadSharing(http: Pick<HttpClient, 'get'>, site: number): Promise<SiteSharing> {
  return http.get<SiteSharing>(`${BASE}/sites/${site}/sharing`)
}

export function previewSharing(http: Pick<HttpClient, 'post'>, site: number, request: SharingRequest): Promise<SharingPlan> {
  return http.post<SharingPlan>(`${BASE}/sites/${site}/sharing/preview`, request)
}

export function applySharing(http: Pick<HttpClient, 'post'>, site: number, request: SharingRequest): Promise<SharingResult> {
  return http.post<SharingResult>(`${BASE}/sites/${site}/sharing/apply`, request)
}

/* ---------- who may do what ---------- */

export function loadObjectRights(http: Pick<HttpClient, 'get'>, object: number): Promise<ObjectRights> {
  return http.get<ObjectRights>(`${BASE}/rights?object=${object}`)
}

export function loadUserRights(http: Pick<HttpClient, 'get'>, user: number): Promise<UserRights> {
  return http.get<UserRights>(`${BASE}/rights?user=${user}`)
}

/* ---------- API keys ---------- */

export function listApiKeys(http: Pick<HttpClient, 'get'>): Promise<ApiKey[]> {
  return http.get<ApiKey[]>(`${BASE}/apikeys`)
}

export function createApiKey(http: Pick<HttpClient, 'post'>, key: NewApiKey): Promise<ApiKey> {
  return http.post<ApiKey>(`${BASE}/apikeys`, key)
}

export function deleteApiKey(http: Pick<HttpClient, 'delete'>, id: string): Promise<void> {
  return http.delete(`${BASE}/apikeys/${encodeURIComponent(id)}`)
}
