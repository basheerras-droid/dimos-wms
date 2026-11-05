/** Small fetch wrapper: same-origin cookies, CSRF header for writes, JSON in and out. */

export class ApiError extends Error {
  status: number
  code: string | undefined
  constructor(status: number, code?: string) {
    super(code ?? `HTTP ${status}`)
    this.status = status
    this.code = code
  }
}

function xsrfToken(): string | undefined {
  const match = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return match ? decodeURIComponent(match.substring('XSRF-TOKEN='.length)) : undefined
}

export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    // The server hands out the token as a cookie on any earlier response (e.g. the first /api/auth/me call).
    const token = xsrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (!response.ok) {
    let code: string | undefined
    try {
      code = ((await response.json()) as { code?: string }).code
    } catch {
      // body was empty or not JSON
    }
    throw new ApiError(response.status, code)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
