import { defineStore } from 'pinia'
import { computed, ref, shallowRef } from 'vue'
import {
  createInitialWorkbenchState,
  openFile,
  pipelineNodeNames,
  reduceGenerationComplete,
  reduceGenerationError,
  reduceGenerationMessage,
  reducePipelineNodeStart,
  selectActiveFile,
  showFileDiff,
  updateActiveFileContent,
  getLanguageFromPath,
} from '@/core/generationCore'
import type {
  PipelineNodeName,
  SSECompleteData,
  SSEMessage,
  WorkbenchCoreState,
  WorkbenchMode,
} from '@/core/generationCore'
import { projectApi } from '@/api/project'
import type { ProjectResponse } from '@/api/project'
import { generationApi } from '@/api/generation'
import { sandboxApi } from '@/api/sandbox'

const stageLabels: Record<PipelineNodeName, string> = {
  requirement_analysis: '需求分析',
  execution_planning: '执行规划',
  code_generation: '代码生成',
  style_optimization: '样式优化',
  build_verification: '构建验证',
  preview_deploy: '预览部署',
  iteration_intent_analysis: '意图分析',
  project_context_load: '上下文读取',
  modification_planning: '修改规划',
  code_patch: '代码应用',
  build_error_analysis: '错误分析',
  iteration_intent: '迭代理解',
  code_locating: '代码定位',
  modification_generation: '修改生成',
}

const generationPipelineNodes: PipelineNodeName[] = [
  'requirement_analysis',
  'execution_planning',
  'code_generation',
  'style_optimization',
  'build_verification',
  'preview_deploy',
]

const iterationPipelineNodes: PipelineNodeName[] = [
  'code_patch',
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

/**
 * 把后端统一封装的 SSE 事件注册到 EventSource 上。
 * 后端事件类型收敛为五类：token / thinking / tool_call / done / error。
 * - `token` 事件用 `data.kind` 区分二级语义（node_start / node_end / node_text /
 *   file_token / file_complete / file / log / mod），分别复用既有 UI 渲染分支。
 * - `thinking` → 流式推理过程缓冲。
 * - `tool_call` → 模型工具调用可视化（调用中 result=null / 完成阶段 result 非空）。
 * - `done` → 流水线完成（含预览地址、端口、构建耗时）。
 * - `error` → 任意阶段失败。
 *
 * 这样既适配新协议，又完全复用现有 reducer 与缓冲机制，不打断打字机动画。
 */
function attachUnifiedSseListeners(
  eventSource: EventSource,
  ctx: {
    onData: (data: { nodeName: PipelineNodeName; text: string }) => void
    onNodeStart: (data: { nodeName: PipelineNodeName; title?: string }) => void
    onNodeEnd: (data: { nodeName: PipelineNodeName }) => void
    onThinkingToken: (data: { nodeName: PipelineNodeName; token: string }) => void
    onFileToken: (data: { path: string; token: string }) => void
    onFileComplete: (data: { path: string }) => void
    onFile: (data: { path: string; content: string; status: string }) => void
    onLog: (data: { text: string }) => void
    onModification?: (data: any) => void
    onToolCall?: (data: { id: string; name: string; arguments: string; result: string | null }) => void
    onDone: (data: { url: string; port?: number; buildTime?: number }) => void
    onError: (message: string) => void
  },
) {
  eventSource.addEventListener('token', (event) => {
    try {
      const d = JSON.parse(event.data) as { kind?: string; [k: string]: any }
      const kind = d.kind || 'node_text'
      switch (kind) {
        case 'node_start':
          ctx.onNodeStart({ nodeName: d.nodeName, title: d.text })
          break
        case 'node_end':
          ctx.onNodeEnd({ nodeName: d.nodeName })
          break
        case 'node_text':
          ctx.onData({ nodeName: d.nodeName, text: d.text || '' })
          break
        case 'file_token':
          ctx.onFileToken({ path: d.path, token: d.token || '' })
          break
        case 'file_complete':
          ctx.onFileComplete({ path: d.path })
          break
        case 'file':
          ctx.onFile({ path: d.path, content: d.content || '', status: d.status || 'new' })
          break
        case 'log':
          ctx.onLog({ text: d.text || '' })
          break
        case 'mod':
          ctx.onModification?.(d)
          break
        default:
          console.warn('未知的 token kind:', kind, d)
      }
    } catch (err) {
      console.error('解析 token SSE 失败:', err)
    }
  })
  eventSource.addEventListener('thinking', (event) => {
    try {
      const d = JSON.parse(event.data) as { nodeName: PipelineNodeName; token: string }
      ctx.onThinkingToken({ nodeName: d.nodeName, token: d.token || '' })
    } catch (err) {
      console.error('解析 thinking SSE 失败:', err)
    }
  })
  eventSource.addEventListener('tool_call', (event) => {
    try {
      const d = JSON.parse(event.data) as { id: string; name: string; arguments: string; result: string | null }
      ctx.onToolCall?.(d)
    } catch (err) {
      console.error('解析 tool_call SSE 失败:', err)
    }
  })
  eventSource.addEventListener('done', (event) => {
    try {
      const d = JSON.parse(event.data) as { url: string; port?: number; buildTime?: number }
      ctx.onDone({ url: d.url, port: d.port, buildTime: d.buildTime })
    } catch (err) {
      console.error('解析 done SSE 失败:', err)
    }
  })
  eventSource.addEventListener('error', (event) => {
    try {
      const msg = (event as any)?.data ? JSON.parse((event as any).data)?.message : null
      ctx.onError(msg || 'SSE 连接异常或任务生成失败')
    } catch {
      ctx.onError('SSE 连接异常或任务生成失败')
    }
  })
}

export const useWorkbenchStore = defineStore('workbench', () => {
  const coreState = ref<WorkbenchCoreState>(createSimpleState())
  const model = ref('灵码 UI Pro')
  const models = ['灵码 UI Pro', '灵码 UI Standard', '灵码 Speed Fast']
  const projects = ref<ProjectResponse[]>([])
  const projectId = ref<string | null>(null)
  
  let eventSource: EventSource | null = null
  let typewriterTimers: number[] = []

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
  const activePipelineNodes = computed(() => {
    const visibleNodeNames = ((coreState.value as any).visibleNodeNames || []) as PipelineNodeName[]
    const hasIterationNode = visibleNodeNames.some((nodeName) => iterationPipelineNodes.includes(nodeName))
    if (coreState.value.mode === 'iteration' || hasIterationNode) {
      return iterationPipelineNodes
    }
    return generationPipelineNodes
  })
  const checklistItems = computed(() => {
    const visibleNodeNames = ((coreState.value as any).visibleNodeNames || []) as PipelineNodeName[]
    const allowedNodeNames = new Set(activePipelineNodes.value)
    return visibleNodeNames
      .filter((nodeName) => allowedNodeNames.has(nodeName))
      .map((nodeName) => ({
        nodeName,
        label: stageLabels[nodeName],
        status: coreState.value.checklist[nodeName],
        thinking: (coreState.value as any).nodeThinkings?.[nodeName] || '',
      }))
  })

  const fileTree = computed(() => {
    const list = coreState.value.files || []
    const root: any[] = []
    
    for (const f of list) {
      const parts = f.path.split('/')
      let current = root
      let currentPath = ''
      
      for (let i = 0; i < parts.length; i++) {
        const part = parts[i] || ''
        currentPath = currentPath ? `${currentPath}/${part}` : part
        const isLast = i === parts.length - 1
        
        let found = current.find((node) => node.name === part)
        if (!found) {
          found = {
            name: part,
            path: currentPath,
            type: isLast ? 'file' : 'dir',
            language: isLast ? (f.language || 'plaintext') : undefined,
            status: isLast ? (f.status as any) : undefined,
            children: isLast ? undefined : [],
          }
          current.push(found)
        }
        if (!isLast) {
          current = found.children
        }
      }
    }
    return root
  })

  // Token 流性能优化：批量节流缓冲区，防止高并发下主线程 DOM 渲染卡死与连接重置
  let fileTokenBuffer: Record<string, string> = {}
  let thinkingBuffer: Record<string, string> = {}
  let flushTimer: any = null

  function flushBuffer() {
    if (flushTimer) {
      clearTimeout(flushTimer)
      flushTimer = null
    }

    const filesKeys = Object.keys(fileTokenBuffer)
    const thinkingKeys = Object.keys(thinkingBuffer)
    if (filesKeys.length === 0 && thinkingKeys.length === 0) return

    // 1. 批量刷新文件内容
    if (filesKeys.length > 0) {
      const filesList = [...(coreState.value.files || [])]
      for (const path of filesKeys) {
        const token = fileTokenBuffer[path] || ''
        const index = filesList.findIndex((f) => f.path === path)
        if (index === -1) {
          filesList.push({
            id: path,
            name: path.split('/').pop() || path,
            path: path,
            type: 'file',
            language: getLanguageFromPath(path),
            status: coreState.value.mode === 'generation' ? 'new' : 'modified',
            content: token,
          })
        } else {
          filesList[index] = {
            ...filesList[index]!,
            content: (filesList[index]?.content || '') + token,
          }
        }
      }
      coreState.value.files = filesList
      fileTokenBuffer = {}
    }

    // 2. 批量刷新思考文本
    if (thinkingKeys.length > 0) {
      const thinkings = { ...(coreState.value as any).nodeThinkings }
      for (const nodeName of thinkingKeys) {
        thinkings[nodeName] = (thinkings[nodeName] || '') + (thinkingBuffer[nodeName] || '')
      }
      ;(coreState.value as any).nodeThinkings = thinkings
      thinkingBuffer = {}
    }
  }

  function scheduleBufferFlush() {
    if (flushTimer !== null) return
    flushTimer = setTimeout(flushBuffer, 100) // 100ms 节流频率（10次 DOM 渲染/秒，兼顾实时性与极佳性能）
  }

  function currentRunningNode(): PipelineNodeName | null {
    const visible = ((coreState.value as any).visibleNodeNames || []) as PipelineNodeName[]
    for (let i = visible.length - 1; i >= 0; i--) {
      const nodeName = visible[i]
      if (nodeName && coreState.value.checklist[nodeName] === 'running') return nodeName
    }
    return visible.at(-1) || null
  }

  function appendNodeActivity(nodeName: PipelineNodeName | null, text: string) {
    if (!nodeName || !text) return
    const thinkings = { ...(coreState.value as any).nodeThinkings }
    const prefix = thinkings[nodeName] ? '\n' : ''
    thinkings[nodeName] = `${thinkings[nodeName] || ''}${prefix}${text}`
    ;(coreState.value as any).nodeThinkings = thinkings
  }

  function clearTypewriterTimers() {
    for (const timer of typewriterTimers) {
      window.clearInterval(timer)
    }
    typewriterTimers = []
  }

  function applyMessageWithTypewriter(message: SSEMessage) {
    const beforeLength = coreState.value.chatMessages.length
    const originalText = message.text || ''
    coreState.value = reduceGenerationMessage(coreState.value, message)

    if (message.error || message.textType !== 'TEXT' || !originalText || message.nodeName === 'build_verification') {
      return
    }

    const nextMessages = [...coreState.value.chatMessages]
    const index = nextMessages.findIndex(
      (item, itemIndex) => itemIndex >= beforeLength && item.role === 'assistant' && item.nodeName === message.nodeName,
    )
    if (index === -1) return

    nextMessages[index] = { ...nextMessages[index]!, content: '' }
    coreState.value.chatMessages = nextMessages

    let cursor = 0
    const timer = window.setInterval(() => {
      cursor += Math.max(1, Math.ceil(originalText.length / 36))
      const messages = [...coreState.value.chatMessages]
      const current = messages[index]
      if (!current) {
        window.clearInterval(timer)
        typewriterTimers = typewriterTimers.filter((item) => item !== timer)
        return
      }
      messages[index] = { ...current, content: originalText.slice(0, cursor) }
      coreState.value.chatMessages = messages
      if (cursor >= originalText.length) {
        window.clearInterval(timer)
        typewriterTimers = typewriterTimers.filter((item) => item !== timer)
      }
    }, 24)
    typewriterTimers.push(timer)
  }
  function closeEventSource() {
    clearTypewriterTimers()
    if (flushTimer) {
      clearTimeout(flushTimer)
      flushTimer = null
    }
    flushBuffer()
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  function applyMessage(message: SSEMessage) {
    applyMessageWithTypewriter(message)
  }

  function applyComplete(data: SSECompleteData) {
    coreState.value = reduceGenerationComplete(coreState.value, data)
  }

  function applyError(error: string) {
    coreState.value = reduceGenerationError(coreState.value, error)
  }

  function startRealPipeline(taskId: string) {
    closeEventSource()
    const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
    eventSource = new EventSource(`${baseUrl}/stream/generation/${taskId}`, {
      withCredentials: true,
    })

    attachUnifiedSseListeners(eventSource, {
      onData: (d) => applyMessage(asSseMessage(taskId, d.nodeName, d.text, 'TEXT')),
      onNodeStart: (d) => {
        coreState.value = reducePipelineNodeStart(coreState.value, { nodeName: d.nodeName, title: d.title })
        coreState.value.logs.push({
          id: 'log-' + Date.now(),
          timestamp: Date.now(),
          level: 'info' as const,
          source: 'system' as const,
          message: '步骤 [' + (stageLabels[d.nodeName] || d.nodeName) + '] 开始执行...',
        })
      },
      onNodeEnd: (d) => {
        const updatedChecklist = { ...coreState.value.checklist }
        updatedChecklist[d.nodeName] = 'done'
        coreState.value.checklist = updatedChecklist
        flushBuffer()
        coreState.value.logs.push({
          id: 'log-' + Date.now(),
          timestamp: Date.now(),
          level: 'success' as const,
          source: 'system' as const,
          message: 'Step [' + (stageLabels[d.nodeName] || d.nodeName) + '] done.',
        })
      },
      onThinkingToken: (d) => {
        thinkingBuffer[d.nodeName] = (thinkingBuffer[d.nodeName] || '') + d.token
        scheduleBufferFlush()
      },
      onFileToken: (d) => {
        fileTokenBuffer[d.path] = (fileTokenBuffer[d.path] || '') + d.token
        if (coreState.value.activeFilePath !== d.path) {
          coreState.value.activeFilePath = d.path
        }
        scheduleBufferFlush()
      },
      onFileComplete: (d) => {
        appendNodeActivity(currentRunningNode(), '工具调用：文件生成完成 ' + d.path)
        coreState.value.logs.push({
          id: 'log-' + Date.now(),
          timestamp: Date.now(),
          level: 'success' as const,
          source: 'build' as const,
          message: '文件 [' + (d.path.split('/').pop() || d.path) + '] 生成落盘成功。',
        })
      },
      onFile: (d) => {
        appendNodeActivity(currentRunningNode(), '工具调用：写入文件 ' + d.path)
        const nextFile = {
          id: d.path,
          name: d.path.split('/').pop() || d.path,
          path: d.path,
          type: 'file' as const,
          language: getLanguageFromPath(d.path),
          status: (d.status || 'new') as 'new' | 'modified' | 'unchanged',
          content: d.content || '',
        }
        const filesList = [...(coreState.value.files || [])]
        const index = filesList.findIndex((f) => f.path === nextFile.path)
        if (index === -1) {
          filesList.push(nextFile)
        } else {
          filesList[index] = { ...filesList[index]!, ...nextFile, content: nextFile.content || filesList[index]?.content || '' }
        }
        coreState.value.files = filesList
        if (!coreState.value.activeFilePath) {
          coreState.value.activeFilePath = nextFile.path
        }
      },
      onLog: (d) => {
        const text = d.text || ''
        appendNodeActivity(currentRunningNode(), '工具调用：' + text)
        let source: 'system' | 'build' | 'runtime' | 'deploy' = 'system'
        let level: 'info' | 'success' | 'error' = 'info'
        if (text.includes('失败') || text.includes('报错') || text.includes('error') || text.includes('Error') || text.includes('failed') || text.includes('Failed')) {
          level = 'error'
        } else if (text.includes('成功') || text.includes('success') || text.includes('Success') || text.includes('passed') || text.includes('Passed')) {
          level = 'success'
        }
        if (text.includes('Vite') || text.includes('开发服务器') || text.includes('Server running') || text.includes('localhost:')) {
          source = 'deploy'
        } else if (text.includes('构建') || text.includes('npm install') || text.includes('依赖') || text.includes('Build')) {
          source = 'build'
        }
        coreState.value.logs.push({
          id: 'log-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
          timestamp: Date.now(),
          level,
          source,
          message: text,
        })
      },
      onToolCall: (d) => {
        const label = d.result == null
          ? '调用工具 ' + d.name + '…'
          : '工具 ' + d.name + ' 完成'
        appendNodeActivity(currentRunningNode(), label)
        coreState.value.logs.push({
          id: 'log-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
          timestamp: Date.now(),
          level: 'info' as const,
          source: 'build' as const,
          message: label + (d.result ? '：' + d.result.slice(0, 120) : ''),
        })
      },
      onDone: (data) => {
        applyComplete({ threadId: taskId, url: data.url, port: data.port, buildTime: data.buildTime })
        closeEventSource()
        if (projectId.value) {
          syncSandboxStatus(projectId.value)
        }
        loadProjects()
      },
      onError: (message) => {
        console.error(message)
        applyError(message)
        closeEventSource()
      },
    })
  }

  function startIterationPipeline(taskId: string) {
    closeEventSource()
    const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
    eventSource = new EventSource(`${baseUrl}/stream/iteration/${taskId}`, {
      withCredentials: true,
    })

    attachUnifiedSseListeners(eventSource, {
      onData: (d) => applyMessage(asSseMessage(taskId, d.nodeName, d.text, 'TEXT')),
      onNodeStart: (d) => {
        coreState.value = reducePipelineNodeStart(coreState.value, { nodeName: d.nodeName, title: d.title })
        coreState.value.logs.push({
          id: 'log-' + Date.now(),
          timestamp: Date.now(),
          level: 'info' as const,
          source: 'system' as const,
          message: '步骤 [' + (stageLabels[d.nodeName] || d.nodeName) + '] 开始执行...',
        })
      },
      onNodeEnd: (d) => {
        const updatedChecklist = { ...coreState.value.checklist }
        updatedChecklist[d.nodeName] = 'done'
        coreState.value.checklist = updatedChecklist
        flushBuffer()
      },
      onThinkingToken: (d) => {
        thinkingBuffer[d.nodeName] = (thinkingBuffer[d.nodeName] || '') + d.token
        scheduleBufferFlush()
      },
      onFileToken: (d) => {
        fileTokenBuffer[d.path] = (fileTokenBuffer[d.path] || '') + d.token
        if (coreState.value.activeFilePath !== d.path) {
          coreState.value.activeFilePath = d.path
        }
        scheduleBufferFlush()
      },
      onFileComplete: (d) => {
        const file = coreState.value.files.find((f) => f.path === d.path)
        if (file) {
          coreState.value.editorMode = 'diff'
          coreState.value.diffFile = {
            path: d.path,
            original: coreState.value.snapshots[d.path] || '',
            modified: file.content || '',
          }
        }
      },
      onFile: (d) => {
        appendNodeActivity(currentRunningNode(), '工具调用：写入文件 ' + d.path)
        const nextFile = {
          id: d.path,
          name: d.path.split('/').pop() || d.path,
          path: d.path,
          type: 'file' as const,
          language: getLanguageFromPath(d.path),
          status: (d.status || 'modified') as 'new' | 'modified' | 'unchanged',
          content: d.content || '',
        }
        const filesList = [...(coreState.value.files || [])]
        const index = filesList.findIndex((f) => f.path === nextFile.path)
        const oldContent = index !== -1 ? (filesList[index]?.content || '') : ''
        if (oldContent && !coreState.value.snapshots[nextFile.path]) {
          coreState.value.snapshots[nextFile.path] = oldContent
        }
        if (index === -1) {
          filesList.push(nextFile)
        } else {
          filesList[index] = { ...filesList[index]!, ...nextFile, content: nextFile.content || filesList[index]?.content || '' }
        }
        coreState.value.files = filesList
        coreState.value.activeFilePath = nextFile.path
        coreState.value.editorMode = 'diff'
        coreState.value.diffFile = {
          path: nextFile.path,
          original: coreState.value.snapshots[nextFile.path] || '',
          modified: nextFile.content || '',
        }
      },
      onLog: (d) => {
        appendNodeActivity(currentRunningNode(), '工具调用：' + (d.text || ''))
        coreState.value.logs.push({
          id: 'log-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
          timestamp: Date.now(),
          level: 'info' as const,
          source: 'build' as const,
          message: d.text || '',
        })
      },
      onToolCall: (d) => {
        const label = d.result == null ? '调用工具 ' + d.name + '…' : '工具 ' + d.name + ' 完成'
        appendNodeActivity(currentRunningNode(), label)
        coreState.value.logs.push({
          id: 'log-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
          timestamp: Date.now(),
          level: 'info' as const,
          source: 'build' as const,
          message: label + (d.result ? '：' + d.result.slice(0, 120) : ''),
        })
      },
      onDone: (data) => {
        applyComplete({ threadId: taskId, url: data.url, port: data.port, buildTime: data.buildTime })
        closeEventSource()
        if (projectId.value) {
          syncSandboxStatus(projectId.value)
          loadProject(projectId.value)
        }
        loadProjects()
      },
      onError: (message) => {
        console.error(message)
        applyError(message)
        closeEventSource()
      },
    })
  }

  async function loadProjects() {
    try {
      const list = await projectApi.list()
      projects.value = list || []
    } catch (e) {
      console.error('加载项目列表失败:', e)
    }
  }

  async function loadProject(paramProjectId: string | number) {
    try {
      const idStr = String(paramProjectId)
      projectId.value = idStr
      
      const rawTree = await projectApi.getFileTree(idStr)
      const flatFiles: any[] = []
      
      function traverse(nodes: any[]) {
        for (const n of nodes) {
          if (n.type === 'file') {
            flatFiles.push({
              id: n.path,
              name: n.name,
              path: n.path,
              type: 'file' as const,
              language: n.language || getLanguageFromPath(n.path),
              status: n.status || 'unchanged',
              content: '',
            })
          } else if (n.children && n.children.length > 0) {
            traverse(n.children)
          }
        }
      }
      if (rawTree) traverse(rawTree)
      
      coreState.value.files = flatFiles
      coreState.value.mode = 'complete'
      coreState.value.isGenerating = false
      
      if (flatFiles.length > 0) {
        await openFileByPath(flatFiles[0].path)
      } else {
        coreState.value.activeFilePath = null
      }
      
      await syncSandboxStatus(idStr)
    } catch (e) {
      console.error('加载项目详情失败:', e)
    }
  }

  async function syncSandboxStatus(paramProjectId: string | number) {
    try {
      const statusInfo = await sandboxApi.getStatus(String(paramProjectId))
      if (statusInfo) {
        coreState.value.sandboxStatus = statusInfo.status || 'stopped'
        coreState.value.previewUrl = statusInfo.url || ''
        coreState.value.previewPort = statusInfo.port || null
      }
    } catch (e) {
      console.error('同步沙箱状态失败:', e)
    }
  }

  async function startSandbox() {
    if (!projectId.value) return
    coreState.value.sandboxStatus = 'starting'
    try {
      const res = await sandboxApi.start(projectId.value)
      if (res) {
        coreState.value.sandboxStatus = res.status || 'running'
        coreState.value.previewUrl = res.url || ''
        coreState.value.previewPort = res.port || null
        
        coreState.value.logs.push({
          id: `log-${Date.now()}`,
          timestamp: Date.now(),
          level: 'success',
          source: 'deploy',
          message: `沙箱服务启动成功，端口: ${res.port}。`,
        })
      }
    } catch (e) {
      coreState.value.sandboxStatus = 'stopped'
      console.error('启动沙箱失败:', e)
      coreState.value.logs.push({
        id: `log-${Date.now()}`,
        timestamp: Date.now(),
        level: 'error',
        source: 'deploy',
        message: '沙箱服务启动失败，请检查构建日志。',
      })
    }
  }

  async function stopSandbox() {
    if (!projectId.value) return
    try {
      await sandboxApi.stop(projectId.value)
      coreState.value.sandboxStatus = 'stopped'
      coreState.value.previewUrl = ''
      coreState.value.previewPort = null
      coreState.value.logs.push({
        id: `log-${Date.now()}`,
        timestamp: Date.now(),
        level: 'info',
        source: 'deploy',
        message: '沙箱服务已关闭。',
      })
    } catch (e) {
      console.error('停止沙箱失败:', e)
    }
  }

  async function submit(value: string) {
    const trimmed = value.trim()
    if (!trimmed) return false
    closeEventSource()
    
    try {
      const projName = '应用-' + Date.now().toString(36)
      const project = await projectApi.create({
        name: projName,
        description: trimmed,
        framework: 'vue-vite-ts',
      })
      
      const projectIdStr = String(project.id)
      const res = await generationApi.create({
        projectId: projectIdStr,
        prompt: trimmed,
      })
      
      const taskId = res.taskId
      coreState.value = createInitialWorkbenchState(taskId, trimmed)
      projectId.value = projectIdStr
      
      startRealPipeline(taskId)
      loadProjects()
      return true
    } catch (e: any) {
      console.error('提交生成任务失败:', e)
      alert('任务创建失败: ' + (e.message || e))
      return false
    }
  }

  async function continueGeneration(value: string) {
    const trimmed = value.trim()
    if (!trimmed) return false
    if (!projectId.value) return false
    
    closeEventSource()
    
    try {
      const res = await generationApi.iterate(projectId.value, trimmed)
      const taskId = res.taskId
      
      coreState.value.chatMessages.push({
        id: `msg-${Date.now().toString(36)}`,
        role: 'user',
        content: trimmed,
        contentType: 'TEXT',
        timestamp: Date.now(),
      })
      
      coreState.value.taskId = taskId
      coreState.value.prompt = trimmed
      coreState.value.mode = 'iteration'
      coreState.value.isGenerating = true
      ;(coreState.value as any).visibleNodeNames = []
      for (const nodeName of iterationPipelineNodes) {
        coreState.value.checklist[nodeName] = 'pending'
      }
      
      startIterationPipeline(taskId)
      return true
    } catch (e: any) {
      console.error('迭代修改失败:', e)
      alert('迭代修改提交失败: ' + (e.message || e))
      return false
    }
  }

  function reset() {
    closeEventSource()
    coreState.value = createSimpleState()
  }

  async function stopGeneration() {
    if (coreState.value.taskId) {
      try {
        await generationApi.stop(coreState.value.taskId)
      } catch (e) {
        console.error('停止生成请求失败:', e)
      }
    }
    closeEventSource()
    applyError('用户已停止本次生成。')
  }

  function cycleModel() {
    const idx = models.indexOf(model.value)
    model.value = models[(idx + 1) % models.length] ?? models[0]!
  }

  async function openFileByPath(path: string) {
    const file = coreState.value.files.find((f) => f.path === path)
    if (!file) return
    
    if (!file.content && projectId.value) {
      try {
        const content = await projectApi.getFileContent(projectId.value, path)
        file.content = content || ''
      } catch (e) {
        console.error(`读取文件内容失败: ${path}`, e)
      }
    }
    
    coreState.value = openFile(coreState.value, path)
  }

  async function updateActiveContent(content: string) {
    coreState.value = updateActiveFileContent(coreState.value, content)
    
    if (projectId.value && coreState.value.activeFilePath) {
      try {
        await projectApi.saveFile(projectId.value, coreState.value.activeFilePath, content)
      } catch (e) {
        console.error('手动保存文件失败:', e)
      }
    }
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
    projects,
    isGenerating,
    sandboxStatus,
    previewUrl,
    projectId,
    files,
    logs,
    chatMessages,
    activeFile,
    editorMode,
    diffFile,
    buildTime,
    checklistItems,
    fileTree,
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
    loadProjects,
    loadProject,
    startSandbox,
    stopSandbox,
  }
})

