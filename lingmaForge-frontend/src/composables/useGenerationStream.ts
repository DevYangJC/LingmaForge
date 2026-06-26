import { onBeforeUnmount, shallowRef } from 'vue'
import { generationApi } from '@/api/generation'
import { useWorkbenchStore } from '@/stores/workbench'
import { GenerationSSEClient } from '@/utils/sseClient'

export function useGenerationStream() {
  const store = useWorkbenchStore()
  const client = shallowRef<GenerationSSEClient | null>(null)

  async function start(prompt: string) {
    const { taskId } = await generationApi.create({ prompt })
    client.value?.close()
    client.value = new GenerationSSEClient(taskId, {
      onMessage: store.applyMessage,
      onComplete: store.applyComplete,
      onError: store.applyError,
      onReconnecting: (attempt) => store.applyError(`SSE 正在重连，第 ${attempt} 次尝试。`),
    })
    client.value.connect()
    return taskId
  }

  async function stop() {
    const taskId = store.taskId
    client.value?.close()
    client.value = null
    if (taskId) await generationApi.stop(taskId)
    store.stopGeneration()
  }

  onBeforeUnmount(() => client.value?.close())

  return { start, stop }
}
