export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  query?: Record<string, string | number | boolean | undefined>
}

const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'

function buildUrl(path: string, query?: RequestOptions['query']) {
  const url = new URL(`${baseUrl}${path}`, window.location.origin)
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined) url.searchParams.set(key, String(value))
  })
  return `${url.pathname}${url.search}`
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (!headers.has('Content-Type') && options.body !== undefined) headers.set('Content-Type', 'application/json')

  const response = await fetch(buildUrl(path, options.query), {
    ...options,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`)

  const text = await response.text()
  if (!text) return undefined as T

  const payload = JSON.parse(text)
  if (typeof payload === 'object' && payload && 'code' in payload) {
    if (payload.code === 0) return payload.data as T
    throw new Error(payload.message || '请求失败')
  }

  return payload as T
}
