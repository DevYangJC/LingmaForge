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

  let text = await response.text()
  if (!text) return undefined as T

  // 解决 JS 解析长整型大整数（如 Snowflake ID）精度丢失问题，将 16 位及以上的长整数转换为字符串
  text = text.replace(/:\s*(-?\d{16,})/g, ':"$1"')

  const payload = JSON.parse(text)
  console.log(`[Request Debug] Path: ${path}`, 'Payload:', payload)
  if (typeof payload === 'object' && payload && ('code' in payload || 'success' in payload)) {
    const isSuccess = payload.success === true || payload.code === 200 || payload.code === 0
    console.log(`[Request Debug] Path: ${path}`, 'isSuccess:', isSuccess)
    if (isSuccess) return payload.data as T
    throw new Error(payload.message || '请求失败')
  }

  return payload as T
}
