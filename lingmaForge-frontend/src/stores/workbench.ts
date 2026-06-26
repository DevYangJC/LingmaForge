import { defineStore } from 'pinia'
import { computed, ref, shallowRef } from 'vue'
import {
  createInitialWorkbenchState,
  openFile,
  pipelineNodeNames,
  reduceGenerationComplete,
  reduceGenerationError,
  reduceGenerationMessage,
  selectActiveFile,
  showFileDiff,
  updateActiveFileContent,
} from '@/core/generationCore.mjs'
import type {
  PipelineNodeName,
  SSECompleteData,
  SSEMessage,
  WorkbenchCoreState,
  WorkbenchMode,
} from '@/core/generationCore.mjs'

const stageLabels: Record<PipelineNodeName, string> = {
  requirement_analysis: '需求分析',
  execution_planning: '执行规划',
  code_generation: '代码生成',
  style_optimization: '样式优化',
  build_verification: '构建验证',
  preview_deploy: '预览部署',
  iteration_intent: '迭代理解',
  code_locating: '代码定位',
  modification_generation: '修改生成',
}

const visiblePipelineNodes: PipelineNodeName[] = [
  'requirement_analysis',
  'execution_planning',
  'code_generation',
  'style_optimization',
  'build_verification',
  'preview_deploy',
]

function createSimpleState(): WorkbenchCoreState {
  return {
    ...createInitialWorkbenchState('', ''),
    mode: 'simple',
    isGenerating: false,
    sandboxStatus: 'stopped',
  }
}

function createTaskId() {
  return `task-${Date.now().toString(36)}`
}

function asSseMessage(
  taskId: string,
  nodeName: PipelineNodeName,
  text: string,
  textType: SSEMessage['textType'] = 'TEXT',
): SSEMessage {
  return { threadId: taskId, nodeName, text, textType, error: false }
}

export const useWorkbenchStore = defineStore('workbench', () => {
  const coreState = ref<WorkbenchCoreState>(createSimpleState())
  const model = ref('灵码 UI Pro')
  const models = ['灵码 UI Pro', '灵码 UI Standard', '灵码 Speed Fast']
  const timers = shallowRef<number[]>([])

  const mode = computed<WorkbenchMode>(() => coreState.value.mode)
  const prompt = computed(() => coreState.value.prompt)
  const taskId = computed(() => coreState.value.taskId)
  const isGenerating = computed(() => coreState.value.isGenerating)
  const sandboxStatus = computed(() => coreState.value.sandboxStatus)
  const previewUrl = computed(() => coreState.value.previewUrl)
  const files = computed(() => coreState.value.files)
  const logs = computed(() => coreState.value.logs)
  const chatMessages = computed(() => coreState.value.chatMessages)
  const activeFile = computed(() => selectActiveFile(coreState.value))
  const editorMode = computed(() => coreState.value.editorMode)
  const diffFile = computed(() => coreState.value.diffFile)
  const buildTime = computed(() => coreState.value.buildTime)
  const checklistItems = computed(() =>
    visiblePipelineNodes.map((nodeName) => ({
      nodeName,
      label: stageLabels[nodeName],
      status: coreState.value.checklist[nodeName],
    })),
  )

  function clearMockTimers() {
    for (const timer of timers.value) window.clearTimeout(timer)
    timers.value = []
  }

  function applyMessage(message: SSEMessage) {
    coreState.value = reduceGenerationMessage(coreState.value, message)
  }

  function applyComplete(data: SSECompleteData) {
    clearMockTimers()
    coreState.value = reduceGenerationComplete(coreState.value, data)
  }

  function applyError(error: string) {
    clearMockTimers()
    coreState.value = reduceGenerationError(coreState.value, error)
  }

  function scheduleMock(delay: number, callback: () => void) {
    const timer = window.setTimeout(callback, delay)
    timers.value = [...timers.value, timer]
  }

  function startMockPipeline(currentTaskId: string, userPrompt: string) {
    scheduleMock(450, () => {
      applyMessage(
        asSseMessage(
          currentTaskId,
          'requirement_analysis',
          JSON.stringify({
            summary: '已识别应用目标、核心页面、交互状态与视觉基调。',
            prompt: userPrompt,
          }),
          'JSON',
        ),
      )
    })

    scheduleMock(950, () => {
      applyMessage(
        asSseMessage(
          currentTaskId,
          'execution_planning',
          JSON.stringify({
            files: [
              {
                path: 'src/App.vue',
                content:
                  '<template>\n  <main class="app-shell">\n    <HeroSection />\n    <PricingGrid />\n  </main>\n</template>\n',
              },
              {
                path: 'src/components/PricingGrid.vue',
                content:
                  '<template>\n  <section class="pricing-grid">\n    <article v-for="plan in plans" :key="plan.name">{{ plan.name }}</article>\n  </section>\n</template>\n',
              },
              {
                path: 'src/styles/theme.css',
                content:
                  ':root {\n  --accent: #14d9bd;\n  --paper: #ffffff;\n}\n.pricing-grid { display: grid; gap: 18px; }\n',
              },
            ],
          }),
          'JSON',
        ),
      )
    })

    scheduleMock(1550, () => {
      applyMessage(
        asSseMessage(
          currentTaskId,
          'code_generation',
          JSON.stringify({
            files: [
              {
                path: 'src/components/PricingGrid.vue',
                content:
                  '<script setup lang="ts">\nconst plans = [\n  { name: \'基础版\', price: 19 },\n  { name: \'专业版\', price: 49 },\n  { name: \'尊享版\', price: 99 },\n]\n</script>\n\n<template>\n  <section class="pricing-grid">\n    <article v-for="plan in plans" :key="plan.name" class="plan-card">\n      <h3>{{ plan.name }}</h3>\n      <strong>¥{{ plan.price }}/月</strong>\n      <button>立即订阅</button>\n    </article>\n  </section>\n</template>\n',
              },
            ],
          }),
          'JSON',
        ),
      )
    })

    scheduleMock(2250, () => {
      applyMessage(asSseMessage(currentTaskId, 'style_optimization', '已完成卡片间距、按钮状态和移动端断点优化。'))
    })

    scheduleMock(2900, () => {
      applyMessage(asSseMessage(currentTaskId, 'build_verification', 'Build passed. 14 modules transformed in 1.24s.'))
    })

    scheduleMock(3400, () => {
      applyMessage(asSseMessage(currentTaskId, 'preview_deploy', 'https://sandbox.lingmaforge.local/preview'))
    })

    scheduleMock(3900, () => {
      applyComplete({
        threadId: currentTaskId,
        url: 'https://sandbox.lingmaforge.local/preview',
        port: 5173,
        buildTime: 1.24,
      })
    })
  }

  function submit(value: string) {
    const trimmed = value.trim()
    if (!trimmed) return false
    clearMockTimers()
    const nextTaskId = createTaskId()
    coreState.value = createInitialWorkbenchState(nextTaskId, trimmed)
    startMockPipeline(nextTaskId, trimmed)
    return true
  }

  function continueGeneration(value: string) {
    const trimmed = value.trim()
    if (!trimmed) return false
    clearMockTimers()
    const nextTaskId = createTaskId()
    coreState.value = {
      ...coreState.value,
      taskId: nextTaskId,
      prompt: trimmed,
      mode: 'generation',
      isGenerating: true,
      sandboxStatus: 'starting',
      chatMessages: [
        ...coreState.value.chatMessages,
        {
          id: `msg-${Date.now().toString(36)}`,
          role: 'user',
          content: trimmed,
          contentType: 'TEXT',
          timestamp: Date.now(),
        },
      ],
    }
    scheduleMock(500, () => applyMessage(asSseMessage(nextTaskId, 'iteration_intent', '正在理解你的修改意图。')))
    scheduleMock(1000, () => applyMessage(asSseMessage(nextTaskId, 'code_locating', '已定位到 PricingGrid.vue 与 theme.css。')))
    scheduleMock(1550, () => {
      applyMessage({
        threadId: nextTaskId,
        nodeName: 'modification_generation',
        text: JSON.stringify({
          modifications: [{ path: 'src/components/PricingGrid.vue', type: 'patch' }],
        }),
        textType: 'JSON',
        error: false,
      })
    })
    scheduleMock(2200, () => applyMessage(asSseMessage(nextTaskId, 'build_verification', 'Iteration build passed in 0.82s.')))
    scheduleMock(2800, () => applyComplete({ threadId: nextTaskId, url: 'https://sandbox.lingmaforge.local/preview', port: 5173, buildTime: 0.82 }))
    return true
  }

  function reset() {
    clearMockTimers()
    coreState.value = createSimpleState()
  }

  function stopGeneration() {
    applyError('用户已停止本次生成。')
  }

  function cycleModel() {
    const idx = models.indexOf(model.value)
    model.value = models[(idx + 1) % models.length] ?? models[0]!
  }

  function openFileByPath(path: string) {
    coreState.value = openFile(coreState.value, path)
  }

  function updateActiveContent(content: string) {
    coreState.value = updateActiveFileContent(coreState.value, content)
  }

  function showDiff(path: string) {
    coreState.value = showFileDiff(coreState.value, path)
  }

  return {
    coreState,
    mode,
    prompt,
    taskId,
    model,
    models,
    isGenerating,
    sandboxStatus,
    previewUrl,
    files,
    logs,
    chatMessages,
    activeFile,
    editorMode,
    diffFile,
    buildTime,
    checklistItems,
    pipelineNodeNames,
    submit,
    continueGeneration,
    reset,
    stopGeneration,
    cycleModel,
    applyMessage,
    applyComplete,
    applyError,
    openFileByPath,
    updateActiveContent,
    showDiff,
  }
})

