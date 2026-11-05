import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from './client'

export interface Me {
  username: string
  displayName: string
  roles: string[]
  mustChangePassword: boolean
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
