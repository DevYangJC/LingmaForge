export const pipelineNodeNames = [
  'requirement_analysis',
  'execution_planning',
  'code_generation',
  'style_optimization',
  'build_verification',
  'preview_deploy',
  'iteration_intent',
  'code_locating',
  'modification_generation',
]

const stageLabels = {
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

let idSeed = 0

function nextId(prefix) {
  idSeed += 1
  return `${prefix}-${Date.now().toString(36)}-${idSeed}`
}

function now() {
  return Date.now()
}

function createChecklist() {
  return Object.fromEntries(pipelineNodeNames.map((nodeName) => [nodeName, 'idle']))
}

function cloneState(state) {
  return {
    ...state,
    checklist: { ...state.checklist },
    chatMessages: [...state.chatMessages],
    files: state.files.map((file) => ({ ...file })),
    logs: [...state.logs],
    snapshots: { ...state.snapshots },
  }
}

function toFileName(path) {
  return path.split('/').pop() || path
}

export function getLanguageFromPath(path) {
  const ext = path.split('.').pop()?.toLowerCase()
  const map = {
    vue: 'vue',
    ts: 'typescript',
    tsx: 'typescriptreact',
    js: 'javascript',
    jsx: 'javascriptreact',
    css: 'css',
    scss: 'scss',
    html: 'html',
    json: 'json',
    md: 'markdown',
  }
  return map[ext] || 'plaintext'
}

export function createInitialWorkbenchState(taskId = '', prompt = '') {
  const checklist = createChecklist()
  checklist.requirement_analysis = prompt ? 'running' : 'idle'

  return {
    taskId,
    prompt,
    mode: 'generation',
    isGenerating: Boolean(prompt),
    sandboxStatus: prompt ? 'starting' : 'stopped',
    previewUrl: '',
    previewPort: null,
    buildTime: null,
    activeFilePath: null,
    editorMode: 'code',
    diffFile: null,
    checklist,
    chatMessages: prompt
      ? [
          {
            id: nextId('msg'),
            role: 'user',
            content: prompt,
            contentType: 'TEXT',
            timestamp: now(),
          },
        ]
      : [],
    files: [],
    logs: [
      {
        id: nextId('log'),
        timestamp: now(),
        level: 'info',
        source: 'system',
        message: prompt ? '生成任务已创建，等待流水线事件。' : '工作台已就绪。',
      },
    ],
    snapshots: {},
  }
}

function safeJsonParse(text) {
  if (!text || typeof text !== 'string') return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function normalizeFile(raw) {
  if (!raw || typeof raw !== 'object') return null
  const path = raw.path || raw.filePath || raw.name
  if (!path || typeof path !== 'string') return null
  return {
    id: path,
    name: toFileName(path),
    path,
    type: 'file',
    language: getLanguageFromPath(path),
    status: raw.status || 'new',
    content: typeof raw.content === 'string' ? raw.content : '',
  }
}

function extractFilesFromMessage(msg) {
  const payload = safeJsonParse(msg.text)
  if (!payload) return []

  const candidates = Array.isArray(payload)
    ? payload
    : payload.files || payload.generatedFiles || payload.generationOrder || []

  return candidates.map(normalizeFile).filter(Boolean)
}

function upsertFile(files, nextFile) {
  const index = files.findIndex((file) => file.path === nextFile.path)
  if (index === -1) return [...files, nextFile]

  const current = files[index]
  const merged = {
    ...current,
    ...nextFile,
    content: nextFile.content || current.content || '',
  }
  return files.map((file, i) => (i === index ? merged : file))
}

function appendAssistantMessage(state, msg) {
  state.chatMessages.push({
    id: nextId('msg'),
    role: 'assistant',
    content: msg.text,
    contentType: msg.textType === 'MARK_DOWN' ? 'MARK_DOWN' : msg.textType || 'TEXT',
    timestamp: now(),
    nodeName: msg.nodeName,
  })
}

function appendLog(state, source, message, level = 'info') {
  state.logs.push({
    id: nextId('log'),
    timestamp: now(),
    level,
    source,
    message,
  })
}

function applyModificationMessage(state, msg) {
  const payload = safeJsonParse(msg.text) || msg
  const modifications = Array.isArray(payload.modifications) ? payload.modifications : []

  for (const modification of modifications) {
    const path = modification.path
    if (!path) continue
    const file = state.files.find((item) => item.path === path)
    if (file && !state.snapshots[path]) state.snapshots[path] = file.content || ''
    state.files = state.files.map((item) =>
      item.path === path ? { ...item, status: 'modified' } : item,
    )
  }

  if (modifications[0]?.path) {
    const path = modifications[0].path
    const file = state.files.find((item) => item.path === path)
    state.activeFilePath = path
    state.editorMode = 'diff'
    state.diffFile = {
      path,
      original: state.snapshots[path] || '',
      modified: file?.content || '',
    }
  }
}

export function reduceGenerationMessage(currentState, msg) {
  if (msg.error) return reduceGenerationError(currentState, msg.text || '生成过程发生错误')

  const state = cloneState(currentState)
  state.isGenerating = true
  state.mode = 'generation'
  state.checklist[msg.nodeName] = 'done'

  if (msg.nodeName !== 'build_verification') appendAssistantMessage(state, msg)

  if (msg.nodeName === 'execution_planning' || msg.nodeName === 'code_generation') {
    const nextFiles = extractFilesFromMessage(msg)
    for (const file of nextFiles) state.files = upsertFile(state.files, file)
    if (!state.activeFilePath && state.files[0]) state.activeFilePath = state.files[0].path
  }

  if (msg.nodeName === 'style_optimization') {
    appendLog(state, 'system', '样式优化已完成。', 'success')
  }

  if (msg.nodeName === 'build_verification') {
    appendLog(state, 'build', msg.text, 'success')
  }

  if (msg.nodeName === 'preview_deploy') {
    const payload = safeJsonParse(msg.text)
    const url = payload?.url || (typeof msg.text === 'string' && msg.text.startsWith('http') ? msg.text : '')
    if (url) state.previewUrl = url
    state.sandboxStatus = url ? 'running' : state.sandboxStatus
  }

  if (msg.nodeName === 'modification_generation') {
    applyModificationMessage(state, msg)
  }

  return state
}

export function reduceGenerationComplete(currentState, data) {
  const state = cloneState(currentState)
  state.mode = 'complete'
  state.isGenerating = false
  state.sandboxStatus = 'running'
  state.previewUrl = data.url || state.previewUrl
  state.previewPort = data.port ?? state.previewPort
  state.buildTime = data.buildTime ?? state.buildTime
  state.checklist.preview_deploy = 'done'
  appendLog(state, 'deploy', `预览已就绪${data.buildTime ? `，构建耗时 ${data.buildTime}s` : ''}。`, 'success')
  return state
}

export function reduceGenerationError(currentState, error) {
  const state = cloneState(currentState)
  state.isGenerating = false
  state.sandboxStatus = 'error'
  state.chatMessages.push({
    id: nextId('msg'),
    role: 'system',
    content: error,
    contentType: 'TEXT',
    timestamp: now(),
    level: 'error',
  })
  appendLog(state, 'system', error, 'error')
  return state
}

export function selectActiveFile(state) {
  return state.files.find((file) => file.path === state.activeFilePath) || state.files[0] || null
}

export function openFile(currentState, path) {
  const state = cloneState(currentState)
  const file = state.files.find((item) => item.path === path)
  if (!file) return state
  state.activeFilePath = path
  state.editorMode = 'code'
  state.diffFile = null
  return state
}

export function updateActiveFileContent(currentState, content) {
  const state = cloneState(currentState)
  if (!state.activeFilePath) return state
  state.files = state.files.map((file) =>
    file.path === state.activeFilePath ? { ...file, content, status: 'modified' } : file,
  )
  return state
}

export function showFileDiff(currentState, path) {
  const state = cloneState(currentState)
  const file = state.files.find((item) => item.path === path)
  if (!file) return state
  state.activeFilePath = path
  state.editorMode = 'diff'
  state.diffFile = {
    path,
    original: state.snapshots[path] || '',
    modified: file.content || '',
  }
  return state
}
