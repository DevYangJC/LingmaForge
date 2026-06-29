import { request } from './request'
import type { FileNode } from '@/core/generationCore.mjs'

export interface ProjectResponse {
  id: number | string
  name: string
  description?: string
  framework: string
  status: 'draft' | 'generating' | 'ready' | 'error'
  lastBuildStatus: 'PENDING' | 'SUCCESS' | 'FAILED'
  sandboxUrl?: string
  createdAt: string
  updatedAt: string
}

export const projectApi = {
  list() {
    return request<ProjectResponse[]>('/projects')
  },
  create(data: { name: string; description?: string; framework: string }) {
    return request<ProjectResponse>('/projects', { method: 'POST', body: data })
  },
  getFileTree(projectId: string | number) {
    return request<FileNode[]>(`/projects/${projectId}/tree`)
  },
  getFileContent(projectId: string | number, path: string) {
    return request<string>(`/projects/${projectId}/file`, { query: { path } })
  },
  saveFile(projectId: string | number, path: string, content: string) {
    return request<void>(`/projects/${projectId}/file`, { method: 'PUT', body: { path, content } })
  },
}
