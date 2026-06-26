import { request } from './request'

export interface CreateGenerationRequest {
  prompt: string
  projectId?: string
  templateId?: string
}

export interface CreateGenerationResponse {
  taskId: string
}

export interface GenerationResult {
  taskId: string
  status: 'running' | 'completed' | 'failed'
  filesCount?: number
  buildTime?: number
  url?: string
}

export const generationApi = {
  create(data: CreateGenerationRequest) {
    return request<CreateGenerationResponse>('/generation/create', { method: 'POST', body: data })
  },
  stop(taskId: string) {
    return request<void>(`/generation/${taskId}/stop`, { method: 'DELETE' })
  },
  getStatus(taskId: string) {
    return request<GenerationResult>(`/generation/${taskId}/status`)
  },
  iterate(projectId: string, prompt: string) {
    return request<CreateGenerationResponse>('/generation/iterate', {
      method: 'POST',
      body: { projectId, prompt },
    })
  },
}
