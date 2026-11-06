import { useQuery } from '@tanstack/react-query'

/** What the server reports at /actuator/info (open without login). */
interface InfoResponse {
  build?: { version?: string; time?: string; dirty?: string }
  git?: { branch?: string; commit?: { id?: string; time?: string } }
}

export interface BuildInfo {
  version: string
  /** Build time as "2026-11-06 19:05" in the viewer's time zone, or '' when unknown. */
  time: string
  commit: string
  /** The build was made from a working tree with uncommitted changes. */
  dirty: boolean
}

const pad = (n: number) => String(n).padStart(2, '0')

export function formatBuildTime(iso: string | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Version, build time and git commit of the running server. Falls back to the front-end version if unreachable. */
export function useBuildInfo() {
  return useQuery<BuildInfo>({
    queryKey: ['build-info'],
    queryFn: async () => {
      try {
        const response = await fetch('/actuator/info', { headers: { Accept: 'application/json' } })
        if (!response.ok) throw new Error(String(response.status))
        const info = (await response.json()) as InfoResponse
        return {
          version: info.build?.version ?? __APP_VERSION__,
          time: formatBuildTime(info.build?.time),
          commit: info.git?.commit?.id ?? '',
          dirty: info.build?.dirty === 'true',
        }
      } catch {
        return { version: __APP_VERSION__, time: '', commit: '', dirty: false }
      }
    },
    staleTime: Infinity,
    retry: false,
  })
}
