/** Small fetch wrapper: same-origin cookies, CSRF header for writes, JSON in and out. */

export class ApiError extends Error {
  status: number
  code: string | undefined
  /** Problems shown next to a form field, from server messages of the form "field: message". */
  fieldErrors: Record<string, string>
  /** Server messages that do not belong to one field. */
  otherDetails: string[]

  constructor(status: number, code?: string, message?: string, details: string[] = []) {
    super(message ?? code ?? `HTTP ${status}`)
    this.status = status
    this.code = code
    this.fieldErrors = {}
    this.otherDetails = []
    for (const d of details) {
      const m = /^([A-Za-z][A-Za-z0-9_.]*): (.*)$/.exec(d)
      if (m) this.fieldErrors[m[1]] = m[2]
      else this.otherDetails.push(d)
    }
  }

  /** The record was changed by someone else while it was open (HTTP 409 STALE_VERSION). */
  get isStale() {
    return this.status === 409 && this.code === 'STALE_VERSION'
  }
}

function xsrfToken(): string | undefined {
  const match = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return match ? decodeURIComponent(match.substring('XSRF-TOKEN='.length)) : undefined
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as { code?: string; message?: string; details?: string[] }
    return new ApiError(response.status, body.code, body.message, body.details ?? [])
  } catch {
    return new ApiError(response.status) // body was empty or not JSON
  }
}

function writeHeaders(headers: Record<string, string>, method: string) {
  if (method !== 'GET') {
    // The server hands out the token as a cookie on any earlier response (e.g. the first /api/auth/me call).
    const token = xsrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }
}

export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  writeHeaders(headers, method)

  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (!response.ok) throw await toApiError(response)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

/** Sends a file (multipart). The result may be a 422 "import refused" body, which is still JSON the caller wants. */
export async function apiUpload<T>(path: string, form: FormData, acceptStatus: number[] = []): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  writeHeaders(headers, 'POST')
  const response = await fetch(path, { method: 'POST', headers, credentials: 'same-origin', body: form })
  if (!response.ok && !acceptStatus.includes(response.status)) throw await toApiError(response)
  return (await response.json()) as T
}

/** Downloads a file the server generates (export, template) and hands it to the browser. */
export async function apiDownload(path: string): Promise<void> {
  const response = await fetch(path, { credentials: 'same-origin' })
  if (!response.ok) throw await toApiError(response)
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? 'download'
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
