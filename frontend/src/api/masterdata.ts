import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'

export const MD = '/api/masterdata'

export interface Page<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface Versioned {
  id: number
  version: number
}

export type ListParams = Record<string, string | number | boolean | undefined>

function query(params: ListParams): string {
  const usp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '') usp.set(k, String(v))
  }
  const s = usp.toString()
  return s ? `?${s}` : ''
}

/** One page of a master-data list (server does the paging, sorting, search and filters). */
export function useMasterList<T>(path: string, params: ListParams) {
  return useQuery<Page<T>>({
    queryKey: ['md', path, params],
    queryFn: () => api<Page<T>>(`${path}${query(params)}`),
    placeholderData: keepPreviousData,
  })
}

/** All records of a small list (areas, types, ...), for drop-downs. */
export function useOptions<T>(path: string) {
  return useQuery<Page<T>>({
    queryKey: ['md-options', path],
    queryFn: () => api<Page<T>>(`${path}?size=200&sort=code`),
    staleTime: 30_000,
  })
}

export function useSaveRecord<T extends Versioned>(path: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { id?: number; body: Record<string, unknown> }) =>
      v.id === undefined
        ? api<T>(path, { method: 'POST', body: v.body })
        : api<T>(`${path}/${v.id}`, { method: 'PUT', body: v.body }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['md', path] })
      void qc.invalidateQueries({ queryKey: ['md-options', path] })
    },
  })
}

export function useDeleteRecord(path: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`${path}/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['md', path] })
      void qc.invalidateQueries({ queryKey: ['md-options', path] })
    },
  })
}

/** Reads one record fresh from the server (used by "reload" after someone else changed it). */
export function fetchRecord<T>(path: string, id: number) {
  return api<T>(`${path}/${id}`)
}
