import type { SSECompleteData, SSEMessage } from '@/core/generationCore.mjs'

export interface SSEHandlers {
  onMessage: (message: SSEMessage) => void
  onComplete: (data: SSECompleteData) => void
  onError: (error: string) => void
  onReconnecting?: (attempt: number) => void
}

export class GenerationSSEClient {
  private eventSource: EventSource | null = null
  private reconnectAttempt = 0
  private manuallyClosed = false

  constructor(
    private readonly taskId: string,
    private readonly handlers: SSEHandlers,
    private readonly maxReconnectAttempts = 5,
  ) {}

  connect() {
    this.manuallyClosed = false
    this.eventSource = new EventSource(`/api/stream/generation/${this.taskId}`)

    this.eventSource.addEventListener('message', (event) => {
      const message = JSON.parse((event as MessageEvent).data) as SSEMessage
      this.reconnectAttempt = 0
      this.handlers.onMessage(message)
    })

    this.eventSource.addEventListener('complete', (event) => {
      const data = JSON.parse((event as MessageEvent).data) as SSECompleteData
      this.handlers.onComplete(data)
      this.close()
    })

    this.eventSource.addEventListener('error', () => {
      if (this.manuallyClosed) return
      this.reconnectAttempt += 1
      if (this.reconnectAttempt <= this.maxReconnectAttempts) {
        this.handlers.onReconnecting?.(this.reconnectAttempt)
        return
      }
      this.handlers.onError('SSE 连接已中断，请刷新页面重试。')
      this.close()
    })
  }

  close() {
    this.manuallyClosed = true
    this.eventSource?.close()
    this.eventSource = null
  }
}
