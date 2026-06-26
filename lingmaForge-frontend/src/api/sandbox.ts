import { request } from './request'
import type { SandboxStatus } from '@/core/generationCore.mjs'

export interface SandboxRuntimeStatus {
  status: SandboxStatus
  url?: string
  port?: number
}

export const sandboxApi = {
  start(projectId: string) {
    return request<SandboxRuntimeStatus>(`/sandbox/${projectId}/start`, { method: 'POST' })
  },
  stop(projectId: string) {
    return request<void>(`/sandbox/${projectId}/stop`, { method: 'POST' })
  },
  getStatus(projectId: string) {
    return request<SandboxRuntimeStatus>(`/sandbox/${projectId}/status`)
  },
}
