<script setup lang="ts">
/**
 * 工作台 - 生成模式（对应 lingma-workbench.html）
 * ------------------------------------------------------------------
 * 样式原样引自 workbench.css（零视觉改动）。
 * 标记结构 1:1 迁移自原型 body。原型以 id + 大段 <script> 驱动生成模拟，
 * 这里改为 Vue 响应式状态驱动，保留核心交互：
 *   - 实时预览 / 代码预览 切换
 *   - 桌面端 / 手机端 设备切换
 *   - 底部 日志/构建/部署/用量 标签切换
 *   - 对话区继续发送需求（追加气泡）
 *   - 进入页面后的生成进度模拟（清单推进 + 日志揭示 + 状态翻转）
 * 首条用户消息绑定 store.prompt（来自简洁模式的输入），实现两模式的数据贯通。
 */
import { onMounted, ref, nextTick, computed, h } from 'vue'
import { useRouter } from 'vue-router'
import type { TreeOption } from 'naive-ui'
import { NTree } from 'naive-ui'
import { Splitpanes, Pane } from 'splitpanes'
import 'splitpanes/dist/splitpanes.css'
import { IMG } from '@/assets/images'
import { useWorkbenchStore } from '@/stores/workbench'
import TheHeader from '@/components/TheHeader.vue'
import '@/styles/pages/workbench.css'

const router = useRouter()
const store = useWorkbenchStore()

/** 预览标签：app 实时预览 / code 代码预览 */
const previewTab = ref<'app' | 'code'>('app')
/** 设备视角：desktop / mobile */
const device = ref<'desktop' | 'mobile'>('desktop')
/** 底部标签 */
const bottomTab = ref<'logs' | 'build' | 'deploy' | 'usage'>('logs')

/** 首条用户需求：优先取简洁模式输入 */
const firstPrompt =
  store.prompt ||
  '帮我生成一个会员订阅商城。包含三档套餐、支付按钮和订单管理。'

/** 追加的对话消息（初始两条以静态结构渲染，后续发送追加到这里） */
interface Msg {
  role: 'user' | 'ai'
  text: string
  time: string
}
const extraMessages = ref<Msg[]>([])
const chatInput = ref('')
const chatBodyRef = ref<HTMLDivElement | null>(null)

function nowStr() {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function sendChat() {
  const val = chatInput.value.trim()
  if (!val) return
  extraMessages.value.push({ role: 'user', text: val, time: nowStr() })
  chatInput.value = ''
  nextTick(() => {
    chatBodyRef.value?.scrollTo({ top: chatBodyRef.value.scrollHeight, behavior: 'smooth' })
    // 模拟 AI 回复
    setTimeout(() => {
      extraMessages.value.push({
        role: 'ai',
        text: '好的，已收到你的修改需求，正在调整相应组件与样式...',
        time: nowStr(),
      })
      nextTick(() => chatBodyRef.value?.scrollTo({ top: chatBodyRef.value.scrollHeight, behavior: 'smooth' }))
    }, 900)
  })
}

function onChatKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendChat()
  }
}

/* ---------- 生成进度模拟（原型 <script> 的核心行为收敛） ---------- */
const generating = ref(true)
// 清单项：样式优化、预览与验证 的状态
const chkStyle = ref<'running' | 'completed'>('running')
const chkVerify = ref<'pending' | 'running' | 'completed'>('pending')
const showBuildSuccess = ref(false)
const showServerRunning = ref(false)
const toolbarStatus = ref('运行中')
const sidebarStatusText = ref('正在生成中')
const serverStatusText = ref('编译中...')
const resultsStatus = ref('生成中...')

function stopGeneration() {
  generating.value = false
  toolbarStatus.value = '已停止'
  serverStatusText.value = '已停止'
  resultsStatus.value = '已停止'
}

/** 新建对话：回退到简洁模式，清空当前生成状态 */
function newChat() {
  store.reset()
}

/* ---------- NTree 文件树 ---------- */
const mockTreeData: TreeOption[] = [
  { key: 'src', label: 'src', children: [
    { key: 'src/pages', label: 'pages', children: [
      { key: 'src/pages/Home.tsx', label: 'Home.tsx', isLeaf: true },
      { key: 'src/pages/Subscribe.tsx', label: 'Subscribe.tsx', isLeaf: true },
      { key: 'src/pages/Orders.tsx', label: 'Orders.tsx', isLeaf: true },
    ]},
    { key: 'src/components', label: 'components', children: [
      { key: 'src/components/PricingCard.tsx', label: 'PricingCard.tsx', isLeaf: true },
      { key: 'src/components/FeatureItem.tsx', label: 'FeatureItem.tsx', isLeaf: true },
      { key: 'src/components/OrderTable.tsx', label: 'OrderTable.tsx', isLeaf: true },
    ]},
    { key: 'src/api', label: 'api', children: [
      { key: 'src/api/orders.ts', label: 'orders.ts', isLeaf: true },
      { key: 'src/api/payments.ts', label: 'payments.ts', isLeaf: true },
    ]},
    { key: 'src/assets', label: 'assets', children: [
      { key: 'src/assets/mascot.png', label: 'mascot.png', isLeaf: true },
    ]},
  ]},
  { key: 'styles.css', label: 'styles.css', isLeaf: true },
  { key: 'package.json', label: 'package.json', isLeaf: true },
]

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
  const badgeMap: Record<string, string> = { '.tsx': 'TSX', '.ts': 'TS', '.css': 'CSS', '.json': 'JSON', '.vue': 'VUE', '.png': 'IMG' }
  const badge = badgeMap[ext]
  if (!badge) return null
  return h('span', { class: ['tree-node-badge', badge.toLowerCase()], style: { fontSize: '9px', padding: '1px 3px', borderRadius: '3px', fontWeight: 600 } }, badge)
}

onMounted(() => {
  // 1. 样式优化完成
  const t1 = setTimeout(() => {
    if (!generating.value) return
    chkStyle.value = 'completed'
    chkVerify.value = 'running'
  }, 1800)
  // 2. 预览与验证完成 + 构建成功日志
  const t2 = setTimeout(() => {
    if (!generating.value) return
    chkVerify.value = 'completed'
    showBuildSuccess.value = true
  }, 3200)
  // 3. 开发服务器运行
  const t3 = setTimeout(() => {
    if (!generating.value) return
    showServerRunning.value = true
    toolbarStatus.value = '运行中'
    sidebarStatusText.value = '已生成'
    serverStatusText.value = '运行中'
    resultsStatus.value = '生成完成'
  }, 4200)
  // 清理：组件卸载时由 Vue 自动处理 ref；定时器保留无副作用
  void t1
  void t2
  void t3
})
</script>

<template>
  <div class="shot">
    <!-- 顶部导航 -->
    <TheHeader />

    <!-- 工作台 -->
    <div class="workbench-container">
      <!-- 工具条 -->
      <div class="workbench-toolbar">
        <div class="toolbar-left">
          <div class="breadcrumb">
            <span class="breadcrumb-item">工作台</span>
            <span class="breadcrumb-separator"
              ><svg class="icon" style="width: 12px; height: 12px"><use href="#chevron-right" /></svg></span
            >
            <span class="breadcrumb-item breadcrumb-active">订阅商城</span>
          </div>
          <div class="project-title-editor">
            <input type="text" value="订阅商城 Demo" />
            <svg class="icon" style="width: 13px; height: 13px"><use href="#edit" /></svg>
          </div>
          <div class="env-select">
            <span>开发环境</span>
            <svg class="icon" style="width: 12px; height: 12px; color: var(--muted)"
              ><use href="#chevron-down"
            /></svg>
          </div>
          <div class="status-badge running">{{ toolbarStatus }}</div>
          <a href="http://localhost:5173" class="local-url" target="_blank" rel="noopener">
            <span>http://localhost:5173</span>
            <svg class="icon" style="width: 12px; height: 12px"><use href="#external-link" /></svg>
          </a>
        </div>
        <div class="toolbar-right">
          <button class="toolbar-btn new-chat-btn" title="新建对话" @click="newChat">
            <svg class="icon" style="width: 13px; height: 13px"><use href="#plus" /></svg>
            <span>新建对话</span>
          </button>
          <button class="toolbar-btn run-btn">
            <svg class="icon" style="width: 12px; height: 12px; fill: currentcolor"
              ><use href="#play"
            /></svg>
            <span>运行</span>
          </button>
          <button class="toolbar-btn stop-btn">
            <svg class="icon" style="width: 12px; height: 12px; fill: currentcolor"
              ><use href="#stop"
            /></svg>
            <span>停止</span>
          </button>
          <button class="toolbar-btn">
            <svg class="icon" style="width: 14px; height: 14px"><use href="#cloud" /></svg>
            <span>部署</span>
          </button>
          <button class="toolbar-btn">
            <svg class="icon" style="width: 13px; height: 13px"><use href="#share" /></svg>
            <span>分享</span>
          </button>
          <button class="toolbar-btn more-btn">
            <svg class="icon" style="width: 14px; height: 14px"><use href="#more-v" /></svg>
          </button>
        </div>
      </div>

      <!-- 主工作区 -->
      <Splitpanes class="workbench-main">
        <!-- Column 1: 我的项目 -->
        <Pane :size="15" :min-size="10">
        <div class="workspace-panel column-projects">
          <div class="projects-header">
            <h3>我的项目</h3>
            <button class="new-project-btn">
              <svg class="icon" style="width: 10px; height: 10px"><use href="#plus" /></svg>
              <span>新建项目</span>
            </button>
          </div>
          <div class="projects-search">
            <svg class="icon"><use href="#search" /></svg>
            <input type="text" placeholder="搜索项目..." />
          </div>
          <div class="projects-tabs">
            <span class="projects-tab active">最近</span>
            <span class="projects-tab">收藏</span>
          </div>
          <div class="projects-list">
            <div class="project-item active" data-project="subscription">
              <div class="project-icon">
                <svg class="icon" style="width: 16px; height: 16px; stroke-width: 2.5"
                  ><use href="#crown"
                /></svg>
              </div>
              <div class="project-info">
                <div class="project-name">订阅商城</div>
                <div class="project-status building">
                  <span class="status-dot"></span><span>{{ sidebarStatusText }}</span>
                </div>
              </div>
              <div>
                <svg class="icon action-icon loader-spin" style="width: 14px; height: 14px"
                  ><use href="#refresh"
                /></svg>
              </div>
            </div>

            <div class="project-item" data-project="crm">
              <div class="project-icon" style="background: #eef2f6; color: #4a5c6e">
                <svg class="icon" style="width: 16px; height: 16px"><use href="#sun" /></svg>
              </div>
              <div class="project-info">
                <div class="project-name">客户管理系统</div>
                <div class="project-status deployed">
                  <span class="status-dot"></span><span>已部署</span>
                </div>
              </div>
              <svg class="icon action-icon check" style="width: 14px; height: 14px; color: var(--green)"
                ><use href="#check"
              /></svg>
            </div>

            <div class="project-item" data-project="dashboard">
              <div class="project-icon" style="background: #eef2f6; color: #4a5c6e">
                <svg class="icon" style="width: 16px; height: 16px"><use href="#crown" /></svg>
              </div>
              <div class="project-info">
                <div class="project-name">数据看板</div>
                <div class="project-status deployed">
                  <span class="status-dot"></span><span>已部署</span>
                </div>
              </div>
              <svg class="icon action-icon check" style="width: 14px; height: 14px; color: var(--green)"
                ><use href="#check"
              /></svg>
            </div>

            <div class="project-item" data-project="signup">
              <div class="project-icon" style="background: #eef2f6; color: #4a5c6e">
                <svg class="icon" style="width: 16px; height: 16px"><use href="#play" /></svg>
              </div>
              <div class="project-info">
                <div class="project-name">活动报名小程序</div>
                <div class="project-status">
                  <span class="status-dot" style="background: #b0c2d3"></span><span>草稿</span>
                </div>
              </div>
              <svg class="icon action-icon" style="width: 14px; height: 14px; visibility: hidden"
                ><use href="#more-h"
              /></svg>
            </div>

            <div class="project-item" data-project="marketing">
              <div class="project-icon" style="background: #eef2f6; color: #4a5c6e">
                <svg class="icon" style="width: 16px; height: 16px"><use href="#sun" /></svg>
              </div>
              <div class="project-info">
                <div class="project-name">AI 营销落地页</div>
                <div class="project-status deployed">
                  <span class="status-dot"></span><span>已部署</span>
                </div>
              </div>
              <svg class="icon action-icon check" style="width: 14px; height: 14px; color: var(--green)"
                ><use href="#check"
              /></svg>
            </div>
          </div>

          <div class="projects-footer">
            <div class="storage-box">
              <div class="storage-header">
                <span>存储空间</span>
                <a href="#">升级</a>
              </div>
              <div class="progress-bar">
                <div class="progress-fill"></div>
              </div>
              <div class="storage-label">28.6 GB / 100 GB</div>
            </div>
            <div class="member-card">
              <div class="member-crown">
                <svg class="icon" style="width: 16px; height: 16px"><use href="#crown" /></svg>
              </div>
              <div class="member-info">
                <div class="member-title">灵码 VIP 会员</div>
                <div class="member-desc">尊享 AI 深度优化服务</div>
              </div>
              <svg class="icon member-arrow"><use href="#chevron-right" /></svg>
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
          <div ref="chatBodyRef" class="chat-messages" id="chat-messages-container">
            <!-- 用户需求 -->
            <div class="chat-bubble user">
              <div class="chat-content-wrapper">
                <div class="chat-content">{{ firstPrompt }}</div>
                <div class="chat-time">10:30</div>
              </div>
            </div>

            <!-- AI 清单回复 -->
            <div class="chat-bubble ai">
              <div class="chat-avatar"><img :src="IMG.brand.mascotHero" alt="AI Mascot" /></div>
              <div class="chat-content-wrapper" style="flex: 1">
                <div class="chat-content">
                  <div>好的，正在为您生成会员订阅商城...</div>
                  <div class="ai-checklist">
                    <div class="checklist-item completed">
                      <div class="checklist-label">
                        <svg class="icon check"><use href="#check" /></svg>
                        <span>需求分析</span>
                      </div>
                      <span class="checklist-value completed">已完成</span>
                    </div>

                    <div class="checklist-item completed">
                      <div class="checklist-label">
                        <svg class="icon check"><use href="#check" /></svg>
                        <span>执行规划</span>
                      </div>
                      <span class="checklist-value completed">已完成</span>
                    </div>

                    <div class="checklist-item completed">
                      <div class="checklist-label">
                        <svg class="icon check"><use href="#check" /></svg>
                        <span>页面与组件生成</span>
                      </div>
                      <span class="checklist-value">8/8</span>
                    </div>

                    <div class="checklist-item completed">
                      <div class="checklist-label">
                        <svg class="icon check"><use href="#check" /></svg>
                        <span>API 接口生成</span>
                      </div>
                      <span class="checklist-value">3/3</span>
                    </div>

                    <div
                      class="checklist-item"
                      :class="{ completed: chkStyle === 'completed', active: chkStyle === 'running' }"
                    >
                      <div class="checklist-label">
                        <svg class="icon" :class="{ check: chkStyle === 'completed', 'loader-spin': chkStyle === 'running' }"
                          ><use href="#refresh"
                        /></svg>
                        <span>样式优化</span>
                      </div>
                      <span class="checklist-value" :class="{ completed: chkStyle === 'completed', running: chkStyle === 'running' }">
                        {{ chkStyle === 'completed' ? '已完成' : '进行中' }}
                      </span>
                    </div>
                    <div class="checklist-subtext" v-if="chkStyle === 'running'">正在更新 PricingCard 组件...</div>

                    <div
                      class="checklist-item"
                      :class="{ completed: chkVerify === 'completed', active: chkVerify === 'running' }"
                      style="color: var(--muted)"
                    >
                      <div class="checklist-label">
                        <svg class="icon" :class="{ check: chkVerify === 'completed', 'loader-spin': chkVerify === 'running' }"
                          ><use href="#refresh"
                        /></svg>
                        <span>预览与验证</span>
                      </div>
                      <span class="checklist-value" :class="{ completed: chkVerify === 'completed', running: chkVerify === 'running' }">
                        {{ chkVerify === 'completed' ? '已完成' : chkVerify === 'running' ? '进行中' : '等待中' }}
                      </span>
                    </div>
                  </div>

                  <div class="ai-changes-box">
                    <div class="changes-title"><span>生成变更</span></div>
                    <div class="changes-list">
                      <div class="change-item">
                        <div class="change-file">
                          <svg class="icon" style="color: var(--blue)"><use href="#file-code" /></svg>
                          <span>components/PricingCard.tsx</span>
                        </div>
                        <span class="change-tag">
                          <svg class="icon loader-spin" style="width: 11px; height: 11px"
                            ><use href="#refresh"
                          /></svg>
                        </span>
                      </div>
                      <div class="change-item">
                        <div class="change-file">
                          <svg class="icon" style="color: var(--blue)"><use href="#file-code" /></svg>
                          <span>pages/Subscribe.tsx</span>
                        </div>
                        <span class="change-tag m">M</span>
                      </div>
                      <div class="change-item">
                        <div class="change-file">
                          <svg class="icon" style="color: #03a9f4"><use href="#file-code" /></svg>
                          <span>api/payments.ts</span>
                        </div>
                        <span class="change-tag a">+</span>
                      </div>
                      <div class="change-item">
                        <div class="change-file">
                          <svg class="icon" style="color: var(--mint)"><use href="#file-code" /></svg>
                          <span>styles.css</span>
                        </div>
                        <span class="change-tag m">M</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 追加消息 -->
            <template v-for="(m, i) in extraMessages" :key="i">
              <div class="chat-bubble" :class="m.role">
                <div v-if="m.role === 'ai'" class="chat-avatar">
                  <img :src="IMG.brand.mascotHero" alt="AI Mascot" />
                </div>
                <div class="chat-content-wrapper" :style="m.role === 'ai' ? { flex: 1 } : {}">
                  <div class="chat-content">{{ m.text }}</div>
                  <div class="chat-time">{{ m.time }}</div>
                </div>
              </div>
            </template>
          </div>

          <div class="ai-input-area">
            <div class="chat-input-wrapper">
              <textarea
                v-model="chatInput"
                placeholder="继续描述修改需求... (支持 @ 提及文件或功能)"
                @keydown="onChatKeydown"
              ></textarea>
              <div class="chat-input-toolbar">
                <div class="chat-input-icons">
                  <svg class="icon"><use href="#paperclip" /></svg>
                  <svg class="icon"><use href="#at" /></svg>
                  <svg class="icon"><use href="#mic" /></svg>
                </div>
                <button class="chat-send-btn" @click="sendChat">
                  <svg class="icon" style="width: 12px; height: 12px; stroke-width: 2.5"
                    ><use href="#send"
                  /></svg>
                </button>
              </div>
            </div>
            <button class="stop-generate-btn" @click="stopGeneration">停止生成</button>
          </div>
        </div>

        </Pane>

        <!-- Column 3: 文件 & 编辑器 -->
        <Pane :size="35" :min-size="20">
        <div class="workspace-panel column-editor">
          <div class="editor-tabs">
            <div class="editor-tabs-left">
              <span class="editor-tab active" style="cursor: default; pointer-events: none">文件</span>
            </div>
            <svg class="icon" style="width: 14px; height: 14px; color: var(--muted); cursor: pointer"
              ><use href="#more-h"
            /></svg>
          </div>
          <div class="editor-filters">
            <span class="filter-pill active" data-filter="all">18 文件</span>
            <span class="filter-pill" data-filter="pages">4 页面</span>
            <span class="filter-pill" data-filter="components">12 组件</span>
            <span class="filter-pill" data-filter="api">3 API</span>
          </div>
          <div class="file-tree" id="file-tree-container">
            <NTree
              :data="mockTreeData"
              default-expand-all
              :render-prefix="renderTreePrefix"
              :render-suffix="renderTreeSuffix"
              block-line
            />
          </div>
        </div>
        </Pane>

        <!-- Column 4: 实时预览 & 代码预览 -->
        <Pane :size="30" :min-size="18">
        <div class="workspace-panel column-preview">
          <div class="preview-header">
            <div class="preview-tabs">
              <span
                class="preview-tab"
                :class="{ active: previewTab === 'app' }"
                @click="previewTab = 'app'"
                >实时预览</span
              >
              <span
                class="preview-tab"
                :class="{ active: previewTab === 'code' }"
                @click="previewTab = 'code'"
                >代码预览</span
              >
            </div>
            <svg class="icon" style="width: 14px; height: 14px; color: var(--muted); cursor: pointer"
              ><use href="#more-v"
            /></svg>
          </div>

          <div class="preview-address-bar" id="preview-app-address-bar" v-show="previewTab === 'app'">
            <div class="device-switcher">
              <button
                class="device-btn"
                :class="{ active: device === 'desktop' }"
                title="桌面端视角"
                @click="device = 'desktop'"
              >
                <svg class="icon" style="width: 14px; height: 14px"><use href="#desktop" /></svg>
              </button>
              <button
                class="device-btn"
                :class="{ active: device === 'mobile' }"
                title="手机端视角"
                @click="device = 'mobile'"
              >
                <svg class="icon" style="width: 14px; height: 14px"><use href="#mobile" /></svg>
              </button>
            </div>
            <div class="url-input-box">
              <span>http://localhost:5173</span>
              <svg class="icon"><use href="#refresh" /></svg>
              <svg class="icon"><use href="#external-link" /></svg>
            </div>
            <div class="sync-status">
              <span class="sync-dot"></span><span>实时同步</span>
            </div>
          </div>

          <div class="preview-canvas" id="preview-app-canvas" v-show="previewTab === 'app'">
            <div
              class="preview-frame-wrapper"
              id="preview-frame-wrapper-element"
              :class="{ mobile: device === 'mobile' }"
            >
              <div class="simulated-app" id="simulated-app-container">
                <div class="app-header">
                  <div class="app-logo">
                    <img :src="IMG.brand.logo" alt="Logo" />
                    <span>灵码工坊</span>
                  </div>
                  <div class="app-nav">
                    <span>首页</span><span>功能</span><span>解决方案</span><span>定价</span
                    ><span>关于我们</span>
                  </div>
                  <div class="app-actions">
                    <button class="app-btn">登录</button>
                    <button class="app-btn primary">注册</button>
                  </div>
                </div>

                <div class="app-title-section">
                  <h2>选择适合你的订阅方案</h2>
                  <p>灵活的定价策略，满足个人开发者到企业团队的不同需求</p>
                </div>

                <div class="app-pricing-grid">
                  <div class="simulated-card">
                    <h4>基础版</h4>
                    <div class="card-desc">适合个人开发者和轻量使用</div>
                    <div class="card-price">¥ 19 <span>/月</span></div>
                    <ul class="card-features">
                      <li>每月 5 张优惠券</li>
                      <li>专属客服通道</li>
                      <li>积分加成 1.2x</li>
                      <li>基础功能访问</li>
                    </ul>
                    <button
                      class="btn"
                      style="border: 1px solid #00bcd4; color: #00aab3; background: white"
                    >
                      选择基础版
                    </button>
                  </div>

                  <div class="simulated-card popular">
                    <div class="popular-badge">最受欢迎</div>
                    <h4>专业版</h4>
                    <div class="card-desc">适合专业开发者和小型团队</div>
                    <div class="card-price">¥ 49 <span>/月</span></div>
                    <ul class="card-features">
                      <li>每月 15 张优惠券</li>
                      <li>专属高级智能模型</li>
                      <li>积分加成 1.5x</li>
                      <li>多应用模块权限</li>
                      <li>优先体验新功能</li>
                    </ul>
                    <button class="btn">立即订阅</button>
                  </div>

                  <div class="simulated-card">
                    <h4>尊享版</h4>
                    <div class="card-desc">适合团队协作和企业级应用</div>
                    <div class="card-price">¥ 99 <span>/月</span></div>
                    <ul class="card-features">
                      <li>无限优惠券</li>
                      <li>专属定制智能模型</li>
                      <li>积分加成 2x</li>
                      <li>会员专属活动</li>
                      <li>高级团队管理</li>
                      <li>专属技术支持</li>
                    </ul>
                    <button
                      class="btn"
                      style="border: 1px solid #00bcd4; color: #00aab3; background: white"
                    >
                      选择尊享版
                    </button>
                  </div>
                </div>

                <div class="app-footer-badges">
                  <span>
                    <svg class="icon" style="width: 12px; height: 12px; color: var(--green)"
                      ><use href="#check"
                    /></svg>
                    7 天无理由退款
                  </span>
                  <span>
                    <svg class="icon" style="width: 12px; height: 12px; color: var(--green)"
                      ><use href="#check"
                    /></svg>
                    安全支付保障
                  </span>
                  <span>
                    <svg class="icon" style="width: 12px; height: 12px; color: var(--green)"
                      ><use href="#check"
                    /></svg>
                    专属客户服务
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- 代码编辑器面板 -->
          <div
            class="code-editor-panel"
            id="preview-code-editor"
            v-show="previewTab === 'code'"
          >
            <div class="editor-header">
              <div class="editor-title">
                <svg class="icon" style="width: 13px; height: 13px; color: var(--blue)"
                  ><use href="#file-code"
                /></svg>
                <span>PricingCard.tsx</span>
              </div>
              <div class="editor-actions">
                <span class="tree-node-badge tsx" style="font-size: 10px; padding: 1px 4px; font-weight: 700">TSX</span>
                <svg class="icon"><use href="#more-h" /></svg>
              </div>
            </div>
            <div class="editor-code">
              <div class="line-numbers">1
2
3
4
5
6
7
8
9
10
11
12
13
14
15
16
17
18
19
20
21
22
23
24
25
26
27</div>
              <pre class="code-lines"><code><span class="keyword">import</span> React <span class="keyword">from</span> <span class="string">'react'</span>;
<span class="keyword">import</span> { Check } <span class="keyword">from</span> <span class="string">'lucide-react'</span>;

<span class="keyword">interface</span> <span class="type">PricingCardProps</span> {
  title: <span class="type">string</span>;
  price: <span class="type">number</span>;
  period: <span class="type">string</span>;
  features: <span class="type">string</span>[];
  popular?: <span class="type">boolean</span>;
}

<span class="keyword">export default function</span> <span class="function">PricingCard</span>({
  title, price, period, features, popular
}: <span class="type">PricingCardProps</span>) {

  <span class="keyword">return</span> (
    <span class="tag">&lt;</span><span class="keyword">div</span> <span class="type">className</span>=<span class="string">{`pricing-card ${popular ? 'popular' : ''}`}</span><span class="tag">&gt;</span>
      {popular <span class="keyword">&amp;&amp;</span> <span class="tag">&lt;</span><span class="keyword">div</span> <span class="type">className</span>=<span class="string">"popular-badge"</span><span class="tag">&gt;</span>最受欢迎<span class="tag">&lt;/</span><span class="keyword">div</span><span class="tag">&gt;</span>}
      <span class="tag">&lt;</span><span class="keyword">h4</span><span class="tag">&gt;</span>{title}<span class="tag">&lt;/</span><span class="keyword">h4</span><span class="tag">&gt;</span>
      <span class="tag">&lt;</span><span class="keyword">div</span> <span class="type">className</span>=<span class="string">"card-price"</span><span class="tag">&gt;</span>¥ {price} <span class="tag">&lt;</span><span class="keyword">span</span><span class="tag">&gt;</span>/月<span class="tag">&lt;/</span><span class="keyword">span</span><span class="tag">&gt;&lt;/</span><span class="keyword">div</span><span class="tag">&gt;</span>
      <span class="tag">&lt;</span><span class="keyword">ul</span> <span class="type">className</span>=<span class="string">"card-features"</span><span class="tag">&gt;</span>
        {features.map((f, idx) =&gt; <span class="tag">&lt;</span><span class="keyword">li</span> <span class="type">key</span>={idx}<span class="tag">&gt;</span>{f}<span class="tag">&lt;/</span><span class="keyword">li</span><span class="tag">&gt;</span>)}
      <span class="tag">&lt;/</span><span class="keyword">ul</span><span class="tag">&gt;</span>
      <span class="tag">&lt;</span><span class="keyword">button</span> <span class="type">className</span>=<span class="string">"btn"</span><span class="tag">&gt;</span>立即订阅<span class="tag">&lt;/</span><span class="keyword">button</span><span class="tag">&gt;</span>
    <span class="tag">&lt;/</span><span class="keyword">div</span><span class="tag">&gt;</span>
  );
}</code></pre>
            </div>
          </div>
        </div>
        </Pane>
      </Splitpanes>

      <!-- 底部诊断 -->
      <div class="workbench-bottom">
        <div class="bottom-header">
          <div class="bottom-tabs">
            <span class="bottom-tab" :class="{ active: bottomTab === 'logs' }" @click="bottomTab = 'logs'">运行日志</span>
            <span class="bottom-tab" :class="{ active: bottomTab === 'build' }" @click="bottomTab = 'build'">构建</span>
            <span class="bottom-tab" :class="{ active: bottomTab === 'deploy' }" @click="bottomTab = 'deploy'">部署</span>
            <span class="bottom-tab" :class="{ active: bottomTab === 'usage' }" @click="bottomTab = 'usage'">用量</span>
          </div>
          <div class="bottom-collapse-btn">
            <svg class="icon" style="width: 14px; height: 14px"><use href="#chevron-down" /></svg>
          </div>
        </div>

        <div class="bottom-content" v-show="bottomTab === 'logs'">
          <div class="bottom-section logs-panel">
            <div class="log-line">
              <span class="log-time">10:31:22</span>
              <span class="log-text">
                <svg class="icon" style="color: #ffa726"><use href="#folder" /></svg>
                <span>安装依赖包... (npm install)</span>
              </span>
            </div>
            <div class="log-line">
              <span class="log-time">10:31:25</span>
              <span class="log-text">
                <svg class="icon check"><use href="#check" /></svg>
                <span>依赖安装完成 (512 packages loaded)</span>
              </span>
            </div>
            <div class="log-line">
              <span class="log-time">10:31:28</span>
              <span class="log-text">
                <svg class="icon check"><use href="#check" /></svg>
                <span>页面路由生成完成: 4 pages generated</span>
              </span>
            </div>
            <div class="log-line">
              <span class="log-time">10:31:29</span>
              <span class="log-text">
                <svg class="icon check"><use href="#check" /></svg>
                <span>模块组件生成完成: 12 elements rendered</span>
              </span>
            </div>
            <div class="log-line">
              <span class="log-time">10:31:31</span>
              <span class="log-text">
                <svg class="icon check"><use href="#check" /></svg>
                <span>API 绑定注册成功: 3 database paths mapped</span>
              </span>
            </div>
            <div class="log-line" v-show="showBuildSuccess">
              <span class="log-time">10:31:35</span>
              <span class="log-text">
                <svg class="icon check"><use href="#check" /></svg>
                <span style="color: var(--green); font-weight: 700">构建成功 (1.23s)</span>
              </span>
            </div>
            <div class="log-line" v-show="showServerRunning">
              <span class="log-time">10:31:36</span>
              <span class="log-text">
                <svg class="icon play"><use href="#play" /></svg>
                <span
                  >开发服务器运行中
                  <a href="http://localhost:5173" style="color: var(--blue); text-decoration: none" target="_blank" rel="noopener"
                    >http://localhost:5173</a
                  ></span
                >
              </span>
            </div>
          </div>

          <div class="bottom-section usage-panel">
            <div class="usage-header">
              <div class="avatar"><img :src="IMG.brand.mascotHero" alt="Mascot" /></div>
              <div class="usage-header-title">灵码 UI Pro</div>
              <span class="usage-header-badge">当前会话</span>
            </div>
            <div class="usage-item">
              <div class="usage-info">
                <span>本次会话 Tokens</span>
                <strong>12,842 / 200,000</strong>
              </div>
              <div class="usage-progress">
                <div class="usage-progress-fill" style="width: 6.4%"></div>
              </div>
            </div>
            <div class="usage-item">
              <div class="usage-info">
                <span>今日用量</span>
                <strong>128,842 / 1,000,000</strong>
              </div>
              <div class="usage-progress">
                <div class="usage-progress-fill" style="width: 12.8%; background: var(--mint)"></div>
              </div>
            </div>
            <div class="usage-footer">
              <span>预计费用</span>
              <strong style="color: var(--ink); display: flex; align-items: center; gap: 2px">
                ¥ 0.18
                <svg class="icon" style="width: 11px; height: 11px; color: var(--muted)"><use href="#info" /></svg>
              </strong>
            </div>
          </div>

          <div class="bottom-section status-panel">
            <div class="status-header-row">
              <span>运行状态</span>
              <span class="status-header-dot">
                <span class="status-dot"></span><span>{{ serverStatusText }}</span>
              </span>
            </div>
            <div class="status-row"><span>构建耗时</span><strong>-</strong></div>
            <div class="status-row"><span>端口号</span><strong>5173</strong></div>
            <div class="status-row"><span>运行环境</span><strong>Development</strong></div>
            <div class="status-row"><span>启动时间</span><strong>-</strong></div>
            <a href="#" class="status-link">查看运行详情 〉</a>
          </div>

          <div class="bottom-section results-panel">
            <div class="results-header-row">
              <span>本次生成结果</span>
              <span class="results-status-text">{{ resultsStatus }}</span>
            </div>
            <div class="results-grid">
              <div class="result-card"><strong>14</strong><span>文件</span></div>
              <div class="result-card"><strong>3</strong><span>页面</span></div>
              <div class="result-card"><strong>2</strong><span>API</span></div>
              <div class="result-card"><strong>9</strong><span>组件</span></div>
            </div>
            <div class="results-footer">
              <span>代码变更</span>
              <strong style="color: var(--ink); float: right">24 处</strong>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 手机端预览视角：约束模拟应用宽度（对应原型 JS 切换 .mobile 类的视觉效果） */
.preview-frame-wrapper.mobile {
  max-width: 390px;
  margin: 0 auto;
}

/* 「新建对话」按钮：用品牌渐变描边，与运行/停止等操作按钮做视觉区分 */
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
</style>
