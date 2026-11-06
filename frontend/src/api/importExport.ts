import { useQuery } from '@tanstack/react-query'
import { api, apiDownload, apiUpload } from './client'
import { MD } from './masterdata'

export interface ImportColumn {
  name: string
  required: boolean
  example: string
  help: string
}

/** One importable table, in the order the tables have to be loaded. */
export interface ImportEntity {
  key: string
  title: string
  columns: ImportColumn[]
}

export type ImportStatus = 'CREATE' | 'UPDATE' | 'ADD' | 'UNCHANGED' | 'ERROR'

export interface ImportRowResult {
  row: number
  key: string
  status: ImportStatus
  message: string | null
}

export interface ImportResult {
  entity: string
  dryRun: boolean
  applied: boolean
  created: number
  updated: number
  added: number
  unchanged: number
  errors: number
  truncated: boolean
  fileErrors: string[]
  rows: ImportRowResult[]
}

export function useImportEntities() {
  return useQuery<ImportEntity[]>({
    queryKey: ['md-import-types'],
    queryFn: () => api<ImportEntity[]>(`${MD}/import/types`),
    staleTime: 5 * 60_000,
  })
}

/** Checks a file (dryRun = true, nothing is changed) or applies it (dryRun = false; all rows or none). */
export function sendImport(entity: string, file: File, dryRun: boolean): Promise<ImportResult> {
  const form = new FormData()
  form.append('file', file)
  // 422 = "apply refused": the body is a normal result that lists the wrong rows
  return apiUpload<ImportResult>(`${MD}/import/${entity}?dryRun=${dryRun}`, form, [422])
}

export const downloadTemplate = (entity: string, format: 'csv' | 'xlsx') =>
  apiDownload(`${MD}/import/${entity}/template?format=${format}`)

export const downloadExport = (entity: string, format: 'csv' | 'xlsx') =>
  apiDownload(`${MD}/export/${entity}?format=${format}`)

// ---- configuration check ---------------------------------------------------------------------------------

export interface ValidationItem {
  severity: 'WARNING' | 'ERROR'
  /** location, location-link, location-type, hu-type, equipment - or null for a message about the whole plant */
  entityType: string | null
  entityId: number | null
  code: string | null
  message: string
}

export interface ValidationRule {
  code: string
  title: string
  status: 'PASS' | 'WARNING' | 'ERROR'
  count: number
  truncated: boolean
  items: ValidationItem[]
}

export interface ValidationReport {
  status: 'PASS' | 'WARNING' | 'ERROR'
  checkedAt: string
  durationMs: number
  counts: Record<string, number>
  rules: ValidationRule[]
}

export function useValidationReport() {
  return useQuery<ValidationReport>({
    queryKey: ['md-validation'],
    queryFn: () => api<ValidationReport>(`${MD}/validation`),
    staleTime: 0,
    refetchOnWindowFocus: false,
  })
}
