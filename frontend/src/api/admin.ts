import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { ListParams, Page } from './masterdata'

const ADMIN = '/api/admin'

function query(params: ListParams): string {
  const usp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '') usp.set(k, String(v))
  }
  const s = usp.toString()
  return s ? `?${s}` : ''
}

// ---- users -----------------------------------------------------------------------------------------------

export interface ViaRole {
  role: string
  /** null = the user has the role directly; otherwise the code of the group it comes through. */
  group: string | null
}

export interface EffectivePermission {
  code: string
  module: string
  description: string
  via: ViaRole[]
}

export interface AdminUser {
  id: number
  username: string
  displayName: string
  email: string | null
  enabled: boolean
  roles: string[]
  hasPassword: boolean
  mustChangePassword: boolean
  locked: boolean
  lockedUntil: string | null
  failedAttempts: number
  lastLoginAt: string | null
  version: number
  updatedAt: string
  updatedBy: string
  /** Only when one user is read: the groups, all roles (including the groups') and the effective permissions. */
  groups?: { code: string; enabled: boolean }[] | null
  effectiveRoles?: string[] | null
  effectivePermissions?: EffectivePermission[] | null
}

export interface Permission {
  code: string
  module: string
  description: string
}

export interface RoleInfo {
  id: number
  code: string
  name: string
  description: string | null
  /** Built-in roles are locked. */
  builtin: boolean
  permissions: string[]
  userCount: number
  groupCount: number
  version: number
}

export interface RoleInput {
  code: string
  name: string
  description: string | null
  permissions: string[]
  version?: number
}

export interface UserInput {
  username?: string
  displayName: string
  email: string | null
  roles: string[]
  enabled?: boolean
  version?: number
}

export function useAdminUsers(params: ListParams) {
  return useQuery<Page<AdminUser>>({
    queryKey: ['admin-users', params],
    queryFn: () => api<Page<AdminUser>>(`${ADMIN}/users${query(params)}`),
    placeholderData: keepPreviousData,
  })
}

export function useRoles() {
  return useQuery<RoleInfo[]>({
    queryKey: ['admin-roles'],
    queryFn: () => api<RoleInfo[]>(`${ADMIN}/roles`),
    staleTime: 30_000,
  })
}

/** The fixed list of permissions of the product, with the module each belongs to. */
export function usePermissions() {
  return useQuery<Permission[]>({
    queryKey: ['admin-permissions'],
    queryFn: () => api<Permission[]>(`${ADMIN}/permissions`),
    staleTime: 5 * 60_000,
  })
}

export function useSaveRole() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { id?: number; body: RoleInput }) =>
      v.id === undefined
        ? api<RoleInfo>(`${ADMIN}/roles`, { method: 'POST', body: v.body })
        : api<RoleInfo>(`${ADMIN}/roles/${v.id}`, { method: 'PUT', body: v.body }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['admin-roles'] }),
  })
}

export function useDeleteRole() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`${ADMIN}/roles/${id}`, { method: 'DELETE' }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['admin-roles'] }),
  })
}

export const fetchUser = (id: number) => api<AdminUser>(`${ADMIN}/users/${id}`)

export interface CreatedUser {
  user: AdminUser
  temporaryPassword: string
}

/** Creates (no id) or updates a user. Creating answers with the temporary password, which is shown only once. */
export function useSaveUser() {
  const qc = useQueryClient()
  return useMutation<AdminUser | CreatedUser, Error, { id?: number; body: UserInput }>({
    mutationFn: (v) =>
      v.id === undefined
        ? api<CreatedUser>(`${ADMIN}/users`, { method: 'POST', body: v.body })
        : api<AdminUser>(`${ADMIN}/users/${v.id}`, { method: 'PUT', body: v.body }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })
}

export function useResetPassword() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) =>
      api<{ temporaryPassword: string }>(`${ADMIN}/users/${id}/reset-password`, { method: 'POST' }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })
}

export function useUnlockUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<AdminUser>(`${ADMIN}/users/${id}/unlock`, { method: 'POST' }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })
}

// ---- audit log -------------------------------------------------------------------------------------------

export interface AuditRow {
  id: number
  occurredAt: string
  actor: string
  action: string
  entityType: string | null
  entityId: string | null
  beforeValue: string | null
  afterValue: string | null
  detail: string | null
  remoteAddr: string | null
  correlationId: string | null
  siteId: number | null
}

export function useAuditLog(params: ListParams) {
  return useQuery<Page<AuditRow>>({
    queryKey: ['admin-audit', params],
    queryFn: () => api<Page<AuditRow>>(`${ADMIN}/audit${query(params)}`),
    placeholderData: keepPreviousData,
  })
}

export function useAuditFilters() {
  return useQuery<{ actions: string[]; entityTypes: string[] }>({
    queryKey: ['admin-audit-filters'],
    queryFn: () => api<{ actions: string[]; entityTypes: string[] }>(`${ADMIN}/audit/filters`),
    staleTime: 60_000,
  })
}

// ---- user groups -----------------------------------------------------------------------------------------

export interface GroupMember {
  id: number
  username: string
  displayName: string
}

export interface AdminGroup {
  id: number
  code: string
  name: string
  description: string | null
  externalRef: string | null
  enabled: boolean
  roles: string[]
  memberCount: number
  /** Only when one group is read. */
  members: GroupMember[] | null
  version: number
  updatedAt: string
  updatedBy: string
}

export interface GroupInput {
  code: string
  name: string
  description: string | null
  externalRef: string | null
  enabled: boolean
  roles: string[]
  memberIds: number[]
  version?: number
}

export function useAdminGroups(params: ListParams) {
  return useQuery<Page<AdminGroup>>({
    queryKey: ['admin-groups', params],
    queryFn: () => api<Page<AdminGroup>>(`${ADMIN}/groups${query(params)}`),
    placeholderData: keepPreviousData,
  })
}

export const fetchGroup = (id: number) => api<AdminGroup>(`${ADMIN}/groups/${id}`)

export function useSaveGroup() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { id?: number; body: GroupInput }) =>
      v.id === undefined
        ? api<AdminGroup>(`${ADMIN}/groups`, { method: 'POST', body: v.body })
        : api<AdminGroup>(`${ADMIN}/groups/${v.id}`, { method: 'PUT', body: v.body }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['admin-groups'] })
      void qc.invalidateQueries({ queryKey: ['admin-user'] })
      void qc.invalidateQueries({ queryKey: ['admin-roles'] })
    },
  })
}

export function useDeleteGroup() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`${ADMIN}/groups/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['admin-groups'] })
      void qc.invalidateQueries({ queryKey: ['admin-roles'] })
    },
  })
}
