import { useQuery } from '@tanstack/react-query'
import { api } from './client'

export interface ProjectFile {
  type: string
  file: string
  /** Number of data rows (-1 = the file could not be read). */
  rows: number
}

export interface ProjectInfo {
  code: string
  name: string
  description: string
  bundled: boolean
  files: ProjectFile[]
}

export interface ProjectPreview {
  project: ProjectInfo
  /** What the site holds now (what "Replace" would delete). */
  existing: Record<string, number>
}

export type ProjectMode = 'REPLACE' | 'UPSERT'

export interface ProjectStep {
  type: string
  file: string
  created: number
  updated: number
  unchanged: number
  errors: number
  messages: string[]
  millis: number
}

export interface ProjectCheck {
  status: 'PASS' | 'WARNING' | 'ERROR'
  errors: number
  warnings: number
  messages: string[]
}

export interface ProjectResult {
  project: string
  mode: ProjectMode
  dryRun: boolean
  applied: boolean
  deleted: Record<string, number>
  steps: ProjectStep[]
  check: ProjectCheck | null
  durationMs: number
  problems: string[]
}

const BASE = '/api/projects'

export function useProjects() {
  return useQuery<ProjectInfo[]>({ queryKey: ['projects'], queryFn: () => api<ProjectInfo[]>(BASE) })
}

export function useProjectPreview(code: string | null) {
  return useQuery<ProjectPreview>({
    queryKey: ['project-preview', code],
    queryFn: () => api<ProjectPreview>(`${BASE}/${code}`),
    enabled: !!code,
    staleTime: 0,
  })
}

/** Checks (dryRun = true, everything is rolled back) or really loads a data set. A refused load (422) is a normal result. */
export function loadProject(code: string, mode: ProjectMode, dryRun: boolean, confirm: string): Promise<ProjectResult> {
  return api<ProjectResult>(`${BASE}/${code}/load`, {
    method: 'POST',
    body: { mode, dryRun, confirm },
    acceptStatus: [422],
  })
}
