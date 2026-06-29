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
  getLanguageFromPath,
} from '@/core/generationCore.mjs'
import type {
  PipelineNodeName,
  SSECompleteData,
  SSEMessage,
  WorkbenchCoreState,
  WorkbenchMode,
} from '@/core/generationCore.mjs'
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
  const projects = ref<ProjectResponse[]>([])
  const projectId = ref<string | null>(null)
  
  let eventSource: EventSource | null = null

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

  function closeEventSource() {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  function applyMessage(message: SSEMessage) {
    coreState.value = reduceGenerationMessage(coreState.value, message)
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

    eventSource.addEventListener('message', (event) => {
      try {
        const msg = JSON.parse(event.data)
        applyMessage(msg)
      } catch (err) {
        console.error('解析 SSE message 失败:', err)
      }
    })

    eventSource.addEventListener('file', (event) => {
      try {
        const fileData = JSON.parse(event.data)
        const nextFile = {
          id: fileData.path,
          name: fileData.path.split('/').pop() || fileData.path,
          path: fileData.path,
          type: 'file' as const,
          language: getLanguageFromPath(fileData.path),
          status: (fileData.status || 'new') as 'new' | 'modified' | 'unchanged',
          content: (fileData.content || '') as string,
        }
        
        const filesList = [...(coreState.value.files || [])]
        const index = filesList.findIndex((f) => f.path === nextFile.path)
        if (index === -1) {
          filesList.push(nextFile)
        } else {
          filesList[index] = {
            ...filesList[index],
            ...nextFile,
            content: nextFile.content || filesList[index]?.content || '',
          }
        }
        coreState.value.files = filesList
        
        if (!coreState.value.activeFilePath) {
          coreState.value.activeFilePath = nextFile.path
        }
      } catch (err) {
        console.error('解析 SSE file 失败:', err)
      }
    })

    eventSource.addEventListener('log', (event) => {
      try {
        const logData = JSON.parse(event.data)
        const text = logData.text || ''
        let source = 'system'
        let level = 'info'
        if (
          text.includes('失败') ||
          text.includes('报错') ||
          text.includes('error') ||
          text.includes('Error') ||
          text.includes('failed') ||
          text.includes('Failed')
        ) {
          level = 'error'
        } else if (
          text.includes('成功') ||
          text.includes('success') ||
          text.includes('Success') ||
          text.includes('passed') ||
          text.includes('Passed')
        ) {
          level = 'success'
        }
        
        if (
          text.includes('Vite') ||
          text.includes('开发服务器') ||
          text.includes('Server running') ||
          text.includes('localhost:')
        ) {
          source = 'deploy'
        } else if (
          text.includes('构建') ||
          text.includes('npm install') ||
          text.includes('依赖') ||
          text.includes('Build')
        ) {
          source = 'build'
        }
        
        coreState.value.logs.push({
          id: `log-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          timestamp: Date.now(),
          level: level as 'info' | 'success' | 'error',
          source: source as 'system' | 'build' | 'runtime' | 'deploy',
          message: text,
        })
      } catch (err) {
        console.error('解析 SSE log 失败:', err)
      }
    })

    eventSource.addEventListener('complete', (event) => {
      try {
        const data = JSON.parse(event.data)
        applyComplete(data)
        closeEventSource()
        if (projectId.value) {
          syncSandboxStatus(projectId.value)
        }
        loadProjects()
      } catch (err) {
        console.error('解析 SSE complete 失败:', err)
      }
    })

    eventSource.addEventListener('error', (event) => {
      console.error('SSE 发生错误:', event)
      applyError('SSE 连接异常或任务生成失败')
      closeEventSource()
    })
  }

  function startIterationPipeline(taskId: string) {
    closeEventSource()
    const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
    eventSource = new EventSource(`${baseUrl}/stream/iteration/${taskId}`, {
      withCredentials: true,
    })

    eventSource.addEventListener('message', (event) => {
      try {
        const msg = JSON.parse(event.data)
        applyMessage(msg)
      } catch (err) {
        console.error('解析迭代 SSE message 失败:', err)
      }
    })

    eventSource.addEventListener('file', (event) => {
      try {
        const fileData = JSON.parse(event.data)
        const nextFile = {
          id: fileData.path,
          name: fileData.path.split('/').pop() || fileData.path,
          path: fileData.path,
          type: 'file' as const,
          language: getLanguageFromPath(fileData.path),
          status: (fileData.status || 'modified') as 'new' | 'modified' | 'unchanged',
          content: fileData.content || '',
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
          filesList[index] = {
            ...filesList[index],
            ...nextFile,
            content: nextFile.content || filesList[index]?.content || '',
          }
        }
        coreState.value.files = filesList
        coreState.value.activeFilePath = nextFile.path
        
        coreState.value.editorMode = 'diff'
        coreState.value.diffFile = {
          path: nextFile.path,
          original: coreState.value.snapshots[nextFile.path] || '',
          modified: nextFile.content || '',
        }
      } catch (err) {
        console.error('解析迭代 SSE file 失败:', err)
      }
    })

    eventSource.addEventListener('log', (event) => {
      try {
        const logData = JSON.parse(event.data)
        coreState.value.logs.push({
          id: `log-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          timestamp: Date.now(),
          level: 'info',
          source: 'build',
          message: logData.text || '',
        })
      } catch (err) {
        console.error('解析迭代 SSE log 失败:', err)
      }
    })

    eventSource.addEventListener('complete', (event) => {
      try {
        const data = JSON.parse(event.data)
        applyComplete(data)
        closeEventSource()
        if (projectId.value) {
          syncSandboxStatus(projectId.value)
          loadProject(projectId.value)
        }
        loadProjects()
      } catch (err) {
        console.error('解析迭代 SSE complete 失败:', err)
      }
    })

    eventSource.addEventListener('error', (event) => {
      console.error('迭代 SSE 发生错误:', event)
      applyError('迭代修改失败，请重试')
      closeEventSource()
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
      coreState.value.isGenerating = true
      
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

