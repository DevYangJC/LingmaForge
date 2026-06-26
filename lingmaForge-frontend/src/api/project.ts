import { request } from './request'
import type { FileNode } from '@/core/generationCore.mjs'

export const projectApi = {
  getFileTree(projectId: string) {
    return request<FileNode[]>(`/project/${projectId}/tree`)
  },
  getFileContent(projectId: string, path: string) {
    return request<string>(`/project/${projectId}/file`, { query: { path } })
  },
  saveFile(projectId: string, path: string, content: string) {
    return request<void>(`/project/${projectId}/file`, { method: 'PUT', body: { path, content } })
  },
}
