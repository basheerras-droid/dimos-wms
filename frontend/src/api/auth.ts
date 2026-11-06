import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from './client'

/** Where this browser is registered as a workstation (or office mode), as the server sees it. */
export interface WorkstationInfo {
  /** OFFICE (not registered), REGISTERED, DISABLED (workstation switched off) or IP_MISMATCH (wrong network address). */
  mode: 'OFFICE' | 'REGISTERED' | 'DISABLED' | 'IP_MISMATCH'
  /** This browser has a registration cookie that the server no longer accepts. */
  staleCookie: boolean
  code: string | null
  name: string | null
  type: string | null
  groupCode: string | null
  locationCode: string | null
  /** The workstation cuts down the user's permissions. */
  restricted: boolean
}

export interface Me {
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
  mustChangePassword: boolean
  workstation: WorkstationInfo
}

const ME_KEY = ['me'] as const

/** The logged-in user, or null when not logged in (401). */
export function useMe() {
  return useQuery<Me | null>({
    queryKey: ME_KEY,
    queryFn: async () => {
      try {
        return await api<Me>('/api/auth/me')
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null
        throw e
      }
    },
    retry: false,
    staleTime: 60_000,
  })
}

/** True if the logged-in user has the permission (e.g. MASTERDATA_EDIT). The server checks again on every call. */
export function useCan(permission: string): boolean {
  const me = useMe()
  return me.data?.permissions?.includes(permission) ?? false
}

export function useLogin() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { username: string; password: string }) =>
      api<Me>('/api/auth/login', { method: 'POST', body: v }),
    onSuccess: (me) => qc.setQueryData(ME_KEY, me),
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('/api/auth/logout', { method: 'POST' }),
    onSettled: () => {
      qc.setQueryData(ME_KEY, null)
      qc.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' })
    },
  })
}

export function useChangePassword() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { currentPassword: string; newPassword: string }) =>
      api<Me>('/api/auth/change-password', { method: 'POST', body: v }),
    onSuccess: (me) => qc.setQueryData(ME_KEY, me),
  })
}

/** "Unregister this browser" (super administrators): back to office mode, whatever a workstation group allows. */
export function useUnregisterBrowser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('/api/workstation/unregister', { method: 'POST' }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ME_KEY }),
  })
}
