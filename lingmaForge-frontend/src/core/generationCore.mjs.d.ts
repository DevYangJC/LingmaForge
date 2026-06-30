export type PipelineNodeName =
  | 'requirement_analysis'
  | 'execution_planning'
  | 'code_generation'
  | 'style_optimization'
  | 'build_verification'
  | 'preview_deploy'
  | 'iteration_intent'
  | 'code_locating'
  | 'modification_generation'

export type SSETextType = 'TEXT' | 'JSON' | 'MARK_DOWN' | 'SQL' | 'HTML'
export type ChecklistStage = 'idle' | 'running' | 'done' | 'error'
export type WorkbenchMode = 'simple' | 'generation' | 'complete'
export type SandboxStatus = 'stopped' | 'starting' | 'running' | 'error'
export type LogLevel = 'info' | 'success' | 'warn' | 'error' | 'debug'

export interface SSEMessage {
  threadId: string
  nodeName: PipelineNodeName
  text: string
  textType: SSETextType
  error: boolean
  modifications?: FileModification[]
}

export interface SSECompleteData {
  threadId: string
  url: string
  port?: number
  buildTime?: number
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  contentType: 'TEXT' | 'JSON' | 'MARK_DOWN' | 'HTML' | 'SQL'
  timestamp: number
  nodeName?: PipelineNodeName
  level?: 'info' | 'error'
}

export interface FileNode {
  id: string
  name: string
  path: string
  type: 'file'
  language: string
  status: 'new' | 'modified' | 'unchanged'
  content: string
}

export interface LogEntry {
  id: string
  timestamp: number
  level: LogLevel
  source: 'build' | 'runtime' | 'deploy' | 'system'
  message: string
}

export interface FileModification {
  path: string
  type?: 'patch' | 'replace'
  patches?: Array<{ line: number; old: string; new: string }>
}

export interface DiffFile {
  path: string
  original: string
  modified: string
}

export interface WorkbenchCoreState {
  taskId: string
  prompt: string
  mode: WorkbenchMode
  isGenerating: boolean
  sandboxStatus: SandboxStatus
  previewUrl: string
  previewPort: number | null
  buildTime: number | null
  activeFilePath: string | null
  editorMode: 'code' | 'diff'
  diffFile: DiffFile | null
  checklist: Record<PipelineNodeName, ChecklistStage>
  chatMessages: ChatMessage[]
  files: FileNode[]
  logs: LogEntry[]
  snapshots: Record<string, string>
  nodeThinkings: Record<string, string>
}

export const pipelineNodeNames: PipelineNodeName[]
export function getLanguageFromPath(path: string): string
export function createInitialWorkbenchState(taskId?: string, prompt?: string): WorkbenchCoreState
export function reduceGenerationMessage(state: WorkbenchCoreState, msg: SSEMessage): WorkbenchCoreState
export function reduceGenerationComplete(state: WorkbenchCoreState, data: SSECompleteData): WorkbenchCoreState
export function reduceGenerationError(state: WorkbenchCoreState, error: string): WorkbenchCoreState
export function selectActiveFile(state: WorkbenchCoreState): FileNode | null
export function openFile(state: WorkbenchCoreState, path: string): WorkbenchCoreState
export function updateActiveFileContent(state: WorkbenchCoreState, content: string): WorkbenchCoreState
export function showFileDiff(state: WorkbenchCoreState, path: string): WorkbenchCoreState
