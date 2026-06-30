<script setup lang="ts">
/**
 * 工作台 - 生成模式（接入 Pinia Store 真实数据）
 * ------------------------------------------------------------------
 * 从 GenerationMode mock 改造为数据驱动组件，消费 store 响应式数据：
 *   - 文件树    → store.fileTree → NTree
 *   - 对话面板  → store.chatMessages
 *   - 流水线清单 → store.checklistItems
 *   - 构建日志  → store.logs
 *   - 项目列表  → store.projects
 *   - 代码编辑器 → store.activeFile
 *   - 预览      → store.previewUrl (iframe)
 *   - 工具栏    → store.projectId / isGenerating / sandboxStatus
 */
import { computed, h, nextTick, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import type { TreeOption } from 'naive-ui'
import { NTree } from 'naive-ui'
import { Splitpanes, Pane } from 'splitpanes'
import 'splitpanes/dist/splitpanes.css'
import { IMG } from '@/assets/images'
import { useWorkbenchStore } from '@/stores/workbench'
import TheHeader from '@/components/TheHeader.vue'
import '@/styles/pages/workbench.css'

const store = useWorkbenchStore()
const {
  mode,
  prompt,
  projectId,
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
  fileTree,
  projects,
} = storeToRefs(store)

/* ---------- 本地 UI 状态 ---------- */
const previewTab = ref<'app' | 'code'>('app')
const device = ref<'desktop' | 'mobile'>('desktop')
const bottomTab = ref<'logs' | 'build' | 'deploy' | 'usage'>('logs')
const chatInput = ref('')
const chatBodyRef = ref<HTMLDivElement | null>(null)
const selectedFileKey = ref<string | null>(null)

/* ---------- 计算属性 ---------- */

/** 当前项目名称 */
const currentProjectName = computed(() => {
  const found = projects.value.find((p) => String(p.id) === projectId.value)
  return found?.name || '未命名项目'
})

/** 工具栏状态文本 */
const toolbarStatusText = computed(() => {
  if (isGenerating.value) return '生成中'
  if (sandboxStatus.value === 'running') return '运行中'
  if (sandboxStatus.value === 'starting') return '启动中'
  if (mode.value === 'complete') return '已完成'
  return '就绪'
})

/** 预览 URL（优先 SSE complete 返回的，其次沙箱启动的） */
const displayUrl = computed(() => {
  if (previewUrl.value) return previewUrl.value
  return 'http://localhost:5173'
})

/** 流水线清单项（只显示 6 个可见阶段） */
const visibleChecklist = computed(() => checklistItems.value || [])

/** 文件统计 */
const fileStats = computed(() => {
  const list = files.value || []
  const pages = list.filter((f) => f.path.includes('/pages/') || f.path.includes('/views/')).length
  const components = list.filter((f) => f.path.includes('/components/')).length
  const apis = list.filter((f) => f.path.includes('/api/') || f.path.includes('/services/')).length
  return { total: list.length, pages, components, apis }
})

/** 首条用户消息（来自 store.prompt，同 SimpleMode 输入） */
const firstPrompt = computed(() => prompt.value || '正在处理你的需求...')

/** 聊天消息（真实数据，跳过第一条 user 消息因为已单独展示） */
const displayMessages = computed(() => {
  // 第一条是 user prompt，在模板中单独展示
  return chatMessages.value.slice(1)
})

/* ---------- NTree 文件树转换 ---------- */
const ntreeData = computed<TreeOption[]>(() => {
  function convert(nodes: any[]): TreeOption[] {
    if (!nodes) return []
    return nodes.map((node: any) => ({
      key: node.path,
      label: node.name,
      isLeaf: node.type === 'file',
      children: node.children ? convert(node.children) : undefined,
    }))
  }
  return convert(fileTree.value || [])
})

function renderTreePrefix({ option }: { option: TreeOption }) {
  const iconName = option.isLeaf ? 'file-code' : 'folder-open'
  const iconColor = option.isLeaf ? 'var(--blue)' : '#ffa726'
  return h('svg', { class: 'icon', style: { width: '14px', height: '14px', color: iconColor, flexShrink: '0' } }, [
    h('use', { href: `#${iconName}` }),
  ])
}

function renderTreeSuffix({ option }: { option: TreeOption }) {
  if (!option.isLeaf) return null
  const name = String(option.label ?? option.key)
  const ext = name.includes('.') ? name.slice(name.lastIndexOf('.')) : ''
  const badgeMap: Record<string, string> = {
    '.tsx': 'TSX', '.ts': 'TS', '.css': 'CSS', '.json': 'JSON', '.vue': 'VUE', '.png': 'IMG',
    '.js': 'JS', '.jsx': 'JSX', '.scss': 'SCSS', '.html': 'HTML', '.md': 'MD',
  }
  const badge = badgeMap[ext]
  if (!badge) return null
  return h('span', {
    class: ['tree-node-badge', badge.toLowerCase()],
    style: { fontSize: '9px', padding: '1px 3px', borderRadius: '3px', fontWeight: 600 },
  }, badge)
}

function onFileSelect(keys: string[]) {
  if (keys.length > 0) {
    const path = keys[0]!
    selectedFileKey.value = path
    store.openFileByPath(path)
  }
}

/* ---------- 操作 ---------- */

async function sendChat() {
  const val = chatInput.value.trim()
  if (!val || !projectId.value) return
  chatInput.value = ''
  await store.continueGeneration(val)
  nextTick(() => {
    chatBodyRef.value?.scrollTo({ top: chatBodyRef.value.scrollHeight, behavior: 'smooth' })
  })
}

function onChatKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendChat()
  }
}

function handleStop() {
  if (isGenerating.value) {
    store.stopGeneration()
  } else {
    store.stopSandbox()
  }
}

function handleRun() {
  store.startSandbox()
}

function newChat() {
  store.reset()
}

function selectProject(proj: any) {
  const id = String(proj.id)
  if (id !== projectId.value) {
    store.loadProject(id)
  }
}

function formatTime(ts: number) {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/* ---------- 生命周期 ---------- */
onMounted(() => {
  store.loadProjects()
})
</script>

<template>
  <div class="shot">
    <TheHeader />

    <div class="workbench-container">
      <!-- ========== 工具条 ========== -->
      <div class="workbench-toolbar">
        <div class="toolbar-left">
          <div class="breadcrumb">
            <span class="breadcrumb-item">工作台</span>
            <span class="breadcrumb-separator">
              <svg class="icon" style="width: 12px; height: 12px"><use href="#chevron-right" /></svg>
            </span>
            <span class="breadcrumb-item breadcrumb-active">{{ currentProjectName }}</span>
          </div>
          <div class="project-title-editor">
            <input type="text" :value="currentProjectName" readonly />
            <svg class="icon" style="width: 13px; height: 13px"><use href="#edit" /></svg>
          </div>
          <div class="status-badge" :class="{ running: isGenerating || sandboxStatus === 'running' }">
            {{ toolbarStatusText }}
          </div>
          <a
            v-if="previewUrl"
            :href="previewUrl"
            class="local-url"
            target="_blank"
            rel="noopener"
          >
            <span>{{ displayUrl }}</span>
            <svg class="icon" style="width: 12px; height: 12px"><use href="#external-link" /></svg>
          </a>
        </div>
        <div class="toolbar-right">
          <button class="toolbar-btn new-chat-btn" title="新建对话" @click="newChat">
            <svg class="icon" style="width: 13px; height: 13px"><use href="#plus" /></svg>
            <span>新建对话</span>
          </button>
          <button class="toolbar-btn run-btn" :disabled="sandboxStatus === 'running'" @click="handleRun">
            <svg class="icon" style="width: 12px; height: 12px; fill: currentcolor"><use href="#play" /></svg>
            <span>运行</span>
          </button>
          <button class="toolbar-btn stop-btn" @click="handleStop">
            <svg class="icon" style="width: 12px; height: 12px; fill: currentcolor"><use href="#stop" /></svg>
            <span>{{ isGenerating ? '停止' : '停止' }}</span>
          </button>
          <button class="toolbar-btn">
            <svg class="icon" style="width: 14px; height: 14px"><use href="#cloud" /></svg>
            <span>部署</span>
          </button>
          <button class="toolbar-btn">
            <svg class="icon" style="width: 13px; height: 13px"><use href="#share" /></svg>
            <span>分享</span>
          </button>
        </div>
      </div>

      <!-- ========== 主工作区 ========== -->
      <Splitpanes class="workbench-main">
        <!-- Column 1: 我的项目 -->
        <Pane :size="15" :min-size="10">
          <div class="workspace-panel column-projects">
            <div class="projects-header">
              <h3>我的项目</h3>
              <button class="new-project-btn" @click="newChat">
                <svg class="icon" style="width: 10px; height: 10px"><use href="#plus" /></svg>
                <span>新建项目</span>
              </button>
            </div>

            <div class="projects-list">
              <div
                v-for="proj in projects"
                :key="proj.id"
                class="project-item"
                :class="{ active: String(proj.id) === projectId }"
                @click="selectProject(proj)"
              >
                <div class="project-icon" :style="{ background: String(proj.id) === projectId ? 'var(--mint)' : '#eef2f6', color: String(proj.id) === projectId ? '#fff' : '#4a5c6e' }">
                  <svg class="icon" style="width: 16px; height: 16px"><use href="#crown" /></svg>
                </div>
                <div class="project-info">
                  <div class="project-name">{{ proj.name }}</div>
                  <div class="project-status" :class="{ building: proj.status === 'generating', deployed: proj.status === 'ready' }">
                    <span class="status-dot"></span>
                    <span>{{ proj.status === 'generating' ? '生成中' : proj.status === 'ready' ? '已就绪' : proj.status === 'draft' ? '草稿' : proj.status === 'error' ? '错误' : proj.status }}</span>
                  </div>
                </div>
                <svg
                  v-if="proj.status === 'ready'"
                  class="icon action-icon check"
                  style="width: 14px; height: 14px; color: var(--green)"
                ><use href="#check" /></svg>
                <svg
                  v-else-if="proj.status === 'generating'"
                  class="icon action-icon loader-spin"
                  style="width: 14px; height: 14px"
                ><use href="#refresh" /></svg>
              </div>

              <div v-if="projects.length === 0" class="project-item" style="justify-content: center; color: var(--muted); font-size: 12px; padding: 24px 12px">
                暂无项目，在简洁模式下创建
              </div>
            </div>
          </div>
        </Pane>

        <!-- Column 2: AI 助手 -->
        <Pane :size="20" :min-size="15">
          <div class="workspace-panel column-ai">
            <div class="ai-header">
              <div class="chat-avatar" style="width: 24px; height: 24px">
                <img :src="IMG.brand.mascotHero" alt="Mascot" />
              </div>
              <h3>AI 助手</h3>
            </div>

            <div ref="chatBodyRef" class="chat-messages">
              <!-- 首条用户消息 -->
              <div class="chat-bubble user">
                <div class="chat-content-wrapper">
                  <div class="chat-content">{{ firstPrompt }}</div>
                  <div class="chat-time">{{ formatTime(Date.now()) }}</div>
                </div>
              </div>

              <!-- AI 进度清单 -->
              <div v-if="visibleChecklist.length > 0 || isGenerating" class="chat-bubble ai">
                <div class="chat-avatar">
                  <img :src="IMG.brand.mascotHero" alt="AI Mascot" />
                </div>
                <div class="chat-content-wrapper" style="flex: 1">
                  <div class="chat-content">
                    <div v-if="isGenerating">
                      {{ mode === 'generation' ? '正在为您生成应用...' : '正在处理修改请求...' }}
                    </div>
                    <div v-else>生成完成！</div>

                    <div v-if="visibleChecklist.length > 0" class="ai-checklist">
                      <div
                        v-for="item in visibleChecklist"
                        :key="item.nodeName"
                        class="checklist-item"
                        :class="{
                          completed: item.status === 'done',
                          active: item.status === 'running',
                        }"
                      >
                        <div class="checklist-label">
                          <svg
                            v-if="item.status === 'done'"
                            class="icon check"
                          ><use href="#check" /></svg>
                          <svg
                            v-else-if="item.status === 'running'"
                            class="icon loader-spin"
                          ><use href="#refresh" /></svg>
                          <svg
                            v-else
                            class="icon"
                            style="opacity: 0.3"
                          ><use href="#circle" /></svg>
                          <span>{{ item.label }}</span>
                        </div>
                        <span
                          class="checklist-value"
                          :class="{
                            completed: item.status === 'done',
                            running: item.status === 'running',
                          }"
                        >
                          {{ item.status === 'done' ? '已完成' : item.status === 'running' ? '进行中' : '等待中' }}
                        </span>
                      </div>
                    </div>

                    <!-- 生成完成后展示文件统计 -->
                    <div v-if="mode === 'complete' && files.length > 0" class="ai-changes-box">
                      <div class="changes-title"><span>生成结果</span></div>
                      <div class="changes-list">
                        <div class="change-item" v-for="f in files.slice(0, 8)" :key="f.path">
                          <div class="change-file">
                            <svg class="icon" style="color: var(--blue)"><use href="#file-code" /></svg>
                            <span>{{ f.path }}</span>
                          </div>
                          <span class="change-tag" :class="{ m: f.status === 'modified', a: f.status === 'new' }">
                            {{ f.status === 'modified' ? 'M' : f.status === 'new' ? '+' : '' }}
                          </span>
                        </div>
                        <div v-if="files.length > 8" class="change-item" style="color: var(--muted); font-size: 11px; justify-content: center">
                          ...还有 {{ files.length - 8 }} 个文件
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 无清单提示 -->
              <div
                v-if="visibleChecklist.length === 0 && !isGenerating && displayMessages.length === 0"
                class="chat-bubble ai"
              >
                <div class="chat-avatar">
                  <img :src="IMG.brand.mascotHero" alt="AI Mascot" />
                </div>
                <div class="chat-content-wrapper" style="flex: 1">
                  <div class="chat-content">已加载项目，可以在下方发送修改指令。</div>
                </div>
              </div>

              <!-- SSE 推送的 AI 消息 -->
              <template v-for="m in displayMessages" :key="m.id">
                <div class="chat-bubble ai" v-if="m.role === 'assistant'">
                  <div class="chat-avatar">
                    <img :src="IMG.brand.mascotHero" alt="AI Mascot" />
                  </div>
                  <div class="chat-content-wrapper" style="flex: 1">
                    <div class="chat-content">{{ m.content }}</div>
                    <div class="chat-time">{{ formatTime(m.timestamp) }}</div>
                  </div>
                </div>
                <div class="chat-bubble user" v-else>
                  <div class="chat-content-wrapper">
                    <div class="chat-content">{{ m.content }}</div>
                    <div class="chat-time">{{ formatTime(m.timestamp) }}</div>
                  </div>
                </div>
              </template>
            </div>

            <div class="ai-input-area">
              <div class="chat-input-wrapper">
                <textarea
                  v-model="chatInput"
                  :disabled="isGenerating"
                  :placeholder="isGenerating ? '生成中，请稍候...' : '继续描述修改需求... (支持 @ 提及文件或功能)'"
                  @keydown="onChatKeydown"
                ></textarea>
                <div class="chat-input-toolbar">
                  <div class="chat-input-icons">
                    <svg class="icon"><use href="#paperclip" /></svg>
                    <svg class="icon"><use href="#at" /></svg>
                    <svg class="icon"><use href="#mic" /></svg>
                  </div>
                  <button class="chat-send-btn" @click="sendChat" :disabled="isGenerating">
                    <svg class="icon" style="width: 12px; height: 12px; stroke-width: 2.5"><use href="#send" /></svg>
                  </button>
                </div>
              </div>
              <button v-if="isGenerating" class="stop-generate-btn" @click="store.stopGeneration()">停止生成</button>
            </div>
          </div>
        </Pane>

        <!-- Column 3: 文件编辑器 -->
        <Pane :size="35" :min-size="20">
          <div class="workspace-panel column-editor">
            <div class="editor-tabs">
              <div class="editor-tabs-left">
                <span class="editor-tab active">文件</span>
              </div>
              <span style="font-size: 11px; color: var(--muted)">{{ fileStats.total }} 文件</span>
            </div>
            <div class="editor-filters">
              <span class="filter-pill active">{{ fileStats.total }} 文件</span>
              <span class="filter-pill">{{ fileStats.pages }} 页面</span>
              <span class="filter-pill">{{ fileStats.components }} 组件</span>
              <span class="filter-pill">{{ fileStats.apis }} API</span>
            </div>
            <div class="file-tree">
              <NTree
                v-if="ntreeData.length > 0"
                :data="ntreeData"
                default-expand-all
                :render-prefix="renderTreePrefix"
                :render-suffix="renderTreeSuffix"
                :selected-keys="selectedFileKey ? [selectedFileKey] : []"
                block-line
                @update:selected-keys="onFileSelect"
              />
              <div v-else style="padding: 24px; color: var(--muted); font-size: 12px; text-align: center">
                <template v-if="isGenerating">等待文件生成...</template>
                <template v-else>暂无文件</template>
              </div>
            </div>
          </div>
        </Pane>

        <!-- Column 4: 预览 -->
        <Pane :size="30" :min-size="18">
          <div class="workspace-panel column-preview">
            <div class="preview-header">
              <div class="preview-tabs">
                <span class="preview-tab" :class="{ active: previewTab === 'app' }" @click="previewTab = 'app'">实时预览</span>
                <span class="preview-tab" :class="{ active: previewTab === 'code' }" @click="previewTab = 'code'">代码预览</span>
              </div>
            </div>

            <!-- App 预览 -->
            <div v-show="previewTab === 'app'">
              <div class="preview-address-bar">
                <div class="device-switcher">
                  <button class="device-btn" :class="{ active: device === 'desktop' }" title="桌面端视角" @click="device = 'desktop'">
                    <svg class="icon" style="width: 14px; height: 14px"><use href="#desktop" /></svg>
                  </button>
                  <button class="device-btn" :class="{ active: device === 'mobile' }" title="手机端视角" @click="device = 'mobile'">
                    <svg class="icon" style="width: 14px; height: 14px"><use href="#mobile" /></svg>
                  </button>
                </div>
                <div class="url-input-box">
                  <span>{{ displayUrl }}</span>
                  <svg class="icon"><use href="#refresh" /></svg>
                  <svg class="icon"><use href="#external-link" /></svg>
                </div>
                <div class="sync-status">
                  <span class="sync-dot"></span><span>实时同步</span>
                </div>
              </div>

              <div class="preview-canvas">
                <div
                  class="preview-frame-wrapper"
                  :class="{ mobile: device === 'mobile' }"
                >
                  <!-- 有预览 URL 时显示 iframe -->
                  <iframe
                    v-if="previewUrl"
                    :src="previewUrl"
                    class="preview-iframe"
                    style="width: 100%; height: 100%; border: none; border-radius: 8px"
                    sandbox="allow-scripts allow-same-origin"
                  />
                  <!-- 无预览 URL 时显示占位 -->
                  <div v-else class="preview-placeholder" style="display: flex; align-items: center; justify-content: center; height: 100%; color: var(--muted); font-size: 13px; flex-direction: column; gap: 12px">
                    <svg class="icon loader-spin" style="width: 32px; height: 32px; opacity: 0.5"><use href="#refresh" /></svg>
                    <span>{{ isGenerating ? '生成中，完成后可预览...' : sandboxStatus === 'starting' ? '沙箱启动中...' : '点击「运行」启动预览' }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 代码编辑器面板 -->
            <div v-show="previewTab === 'code'" class="code-editor-panel">
              <div class="editor-header">
                <div class="editor-title">
                  <svg class="icon" style="width: 13px; height: 13px; color: var(--blue)"><use href="#file-code" /></svg>
                  <span>{{ activeFile?.name || '未选择文件' }}</span>
                </div>
                <div class="editor-actions">
                  <span v-if="activeFile?.language" class="tree-node-badge" :class="(activeFile.language || '').toLowerCase()" style="font-size: 10px; padding: 1px 4px; font-weight: 700">
                    {{ activeFile.language?.toUpperCase() }}
                  </span>
                </div>
              </div>
              <div v-if="activeFile?.content" class="editor-code">
                <div class="line-numbers">{{ activeFile.content.split('\n').map((_, i) => i + 1).join('\n') }}</div>
                <pre class="code-lines"><code>{{ activeFile.content }}</code></pre>
              </div>
              <div v-else style="padding: 32px; color: var(--muted); font-size: 12px; text-align: center">
                选择左侧文件树中的文件以查看代码
              </div>
            </div>
          </div>
        </Pane>
      </Splitpanes>

      <!-- ========== 底部诊断面板 ========== -->
      <div class="workbench-bottom">
        <div class="bottom-header">
          <div class="bottom-tabs">
            <span class="bottom-tab" :class="{ active: bottomTab === 'logs' }" @click="bottomTab = 'logs'">运行日志</span>
            <span class="bottom-tab" :class="{ active: bottomTab === 'build' }" @click="bottomTab = 'build'">构建</span>
            <span class="bottom-tab" :class="{ active: bottomTab === 'deploy' }" @click="bottomTab = 'deploy'">部署</span>
            <span class="bottom-tab" :class="{ active: bottomTab === 'usage' }" @click="bottomTab = 'usage'">用量</span>
          </div>
        </div>

        <!-- 日志面板 -->
        <div class="bottom-content" v-show="bottomTab === 'logs'">
          <div class="bottom-section logs-panel">
            <div v-if="logs.length === 0" style="color: var(--muted); font-size: 12px; padding: 12px 0">暂无日志</div>
            <div v-for="log in logs" :key="log.id" class="log-line">
              <span class="log-time">{{ formatTime(log.timestamp) }}</span>
              <span class="log-text">
                <svg v-if="log.level === 'error'" class="icon" style="color: #ff5252"><use href="#alert" /></svg>
                <svg v-else-if="log.level === 'success'" class="icon check"><use href="#check" /></svg>
                <svg v-else class="icon" style="color: #ffa726"><use href="#folder" /></svg>
                <span :style="{ color: log.level === 'error' ? '#ff5252' : log.level === 'success' ? 'var(--green)' : 'inherit', fontWeight: log.level === 'success' ? 700 : 400 }">
                  {{ log.message }}
                </span>
              </span>
            </div>
          </div>
        </div>

        <!-- 构建面板 -->
        <div class="bottom-content" v-show="bottomTab === 'build'">
          <div class="bottom-section status-panel">
            <div class="status-header-row">
              <span>构建状态</span>
              <span class="status-header-dot">
                <span class="status-dot"></span>
                <span>{{ sandboxStatus === 'running' ? '运行中' : isGenerating ? '生成中' : '空闲' }}</span>
              </span>
            </div>
            <div class="status-row"><span>构建耗时</span><strong>{{ buildTime ? buildTime + 's' : '-' }}</strong></div>
            <div class="status-row"><span>端口号</span><strong>{{ previewUrl ? previewUrl.split(':').pop() : '-' }}</strong></div>
            <div class="status-row"><span>运行环境</span><strong>Development</strong></div>
            <div class="status-row"><span>项目 ID</span><strong>{{ projectId || '-' }}</strong></div>
          </div>
        </div>

        <!-- 部署面板 -->
        <div class="bottom-content" v-show="bottomTab === 'deploy'">
          <div class="bottom-section results-panel">
            <div class="results-header-row">
              <span>本次生成结果</span>
              <span class="results-status-text">{{ mode === 'complete' ? '已完成' : '进行中' }}</span>
            </div>
            <div class="results-grid">
              <div class="result-card"><strong>{{ fileStats.total }}</strong><span>文件</span></div>
              <div class="result-card"><strong>{{ fileStats.pages }}</strong><span>页面</span></div>
              <div class="result-card"><strong>{{ fileStats.apis }}</strong><span>API</span></div>
              <div class="result-card"><strong>{{ fileStats.components }}</strong><span>组件</span></div>
            </div>
          </div>
        </div>

        <!-- 用量面板 -->
        <div class="bottom-content" v-show="bottomTab === 'usage'">
          <div class="bottom-section usage-panel">
            <div class="usage-header">
              <div class="avatar"><img :src="IMG.brand.mascotHero" alt="Mascot" /></div>
              <div class="usage-header-title">灵码 UI Pro</div>
              <span class="usage-header-badge">当前会话</span>
            </div>
            <div style="padding: 16px; color: var(--muted); font-size: 12px; text-align: center">
              用量统计将在后续版本中提供
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 手机端预览视角 */
.preview-frame-wrapper.mobile {
  max-width: 390px;
  margin: 0 auto;
}

/* 「新建对话」按钮 */
.new-chat-btn {
  border-color: rgba(0, 201, 209, 0.55);
  color: #00aab3;
  background: linear-gradient(135deg, rgba(4, 210, 220, 0.08), rgba(22, 201, 157, 0.08));
}
.new-chat-btn:hover {
  border-color: var(--mint);
  color: #008c93;
  box-shadow: 0 6px 16px rgba(0, 196, 186, 0.18);
}

/* Iframe 占位 */
.preview-iframe {
  background: #fff;
}
</style>
