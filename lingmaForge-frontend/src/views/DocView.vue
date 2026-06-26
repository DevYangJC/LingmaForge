<script setup lang="ts">
/**
 * 文档页（对应 lingma-doc.html）
 * 样式原样引自 doc.css；标记 1:1 迁移。
 * 左侧目录分组可折叠，TOC 项点击切换 active（对应原型 <script> 的导航高亮）。
 */
import { ref } from 'vue'
import { IMG } from '@/assets/images'
import '@/styles/pages/doc.css'

interface SidebarGroup {
  title: string
  links: { icon: string; label: string }[]
}
const groups: SidebarGroup[] = [
  {
    title: '快速开始',
    links: [
      { icon: 'rocket', label: '快速开始' },
      { icon: 'sliders', label: '安装与配置' },
      { icon: 'cube', label: '第一个应用' },
      { icon: 'info', label: '常见问题' },
    ],
  },
  {
    title: '核心概念',
    links: [
      { icon: 'user', label: '灵感模型' },
      { icon: 'adjustments', label: '智能节点' },
      { icon: 'file-code', label: '代码符号' },
      { icon: 'spark', label: '创意火花' },
      { icon: 'cube', label: '工坊基座' },
    ],
  },
  {
    title: 'AI 生成',
    links: [
      { icon: 'zap', label: '对话生成' },
      { icon: 'file-code', label: '模板与提示词' },
      { icon: 'sliders', label: '样式优化' },
      { icon: 'spark', label: '多模态生成' },
    ],
  },
  {
    title: '项目部署',
    links: [
      { icon: 'info', label: '环境要求' },
      { icon: 'cloud', label: '部署到云端' },
      { icon: 'zap', label: 'CI/CD 集成' },
      { icon: 'clock', label: '监控与日志' },
    ],
  },
  { title: 'API 参考', links: [{ icon: 'file-code', label: 'API 概览' }] },
  {
    title: 'SDK',
    links: [
      { icon: 'database', label: 'JavaScript SDK' },
      { icon: 'database', label: 'Python SDK' },
    ],
  },
]

const collapsed = ref<Record<number, boolean>>({})
function toggleGroup(i: number) {
  collapsed.value[i] = !collapsed.value[i]
}

const tocActive = ref('#sec-1')
const tocItems = [
  { href: '#sec-1', label: '1. 安装灵码工坊 CLI' },
  { href: '#sec-2', label: '2. 创建并启动项目' },
  { href: '#sec-3', label: '3. 在浏览器中预览' },
  { href: '#sec-next', label: '下一步' },
  { href: '#', label: '常见问题' },
]
function setToc(href: string) {
  tocActive.value = href
}

const codeTab = ref<'npm' | 'pnpm' | 'yarn'>('npm')
</script>

<template>
  <div class="shot">
    <div class="doc-view">
      <div class="doc-layout-container">
        <!-- 子头部：标题 + 搜索/操作 -->
        <header class="doc-sub-header">
          <div class="doc-center-title-box">
            <div class="doc-center-icon"><svg class="icon"><use href="#document" /></svg></div>
            <div class="doc-title-text">
              <h2>文档中心</h2>
              <p>开发者文档与 API 参考</p>
            </div>
          </div>
          <div class="doc-search-actions">
            <div class="doc-search-bar-wrapper">
              <svg class="icon search-icon"><use href="#search" /></svg>
              <input type="text" class="doc-search-input" placeholder="搜索文档、API 或教程..." />
              <span class="doc-shortcut-badge">⌘ K</span>
            </div>
            <button class="doc-select-btn">
              <span>v2.4</span>
              <svg class="icon" style="width: 12px; height: 12px; color: var(--doc-text-secondary)"><use href="#chevron-down" /></svg>
            </button>
            <a href="https://github.com" target="_blank" rel="noopener" class="doc-github-btn">
              <svg class="icon" style="width: 16px; height: 16px"><use href="#file-code" /></svg>
              <span>在 GitHub 上查看</span>
              <svg class="icon" style="width: 12px; height: 12px; color: #bdc8d3"><use href="#external-link" /></svg>
            </a>
            <button class="doc-select-btn">
              <svg class="icon" style="width: 16px; height: 16px"><use href="#globe" /></svg>
              <span>简体中文</span>
              <svg class="icon" style="width: 12px; height: 12px; color: var(--doc-text-secondary)"><use href="#chevron-down" /></svg>
            </button>
          </div>
        </header>

        <!-- 三栏布局 -->
        <div class="doc-main-layout">
          <!-- 左侧目录 -->
          <aside class="doc-left-sidebar">
            <div v-for="(g, gi) in groups" :key="gi" class="sidebar-group">
              <div class="sidebar-group-header" @click="toggleGroup(gi)">
                <span>{{ g.title }}</span>
                <svg class="icon" :style="{ transform: collapsed[gi] ? 'rotate(-90deg)' : '' }"><use href="#chevron-down" /></svg>
              </div>
              <div v-show="!collapsed[gi]" class="sidebar-links">
                <a
                  v-for="(l, li) in g.links"
                  :key="li"
                  href="#"
                  class="sidebar-link-item"
                  :class="{ active: gi === 0 && li === 0 }"
                >
                  <svg class="icon"><use :href="`#${l.icon}`" /></svg>
                  <span>{{ l.label }}</span>
                </a>
              </div>
            </div>
          </aside>

          <!-- 中间正文 -->
          <main class="doc-content-area">
            <div class="doc-breadcrumb">
              <span>首页</span>
              <span class="doc-breadcrumb-sep">&gt;</span>
              <span>快速开始</span>
              <span class="doc-breadcrumb-sep">&gt;</span>
              <span style="color: var(--doc-text-primary); font-weight: 700">快速开始</span>
            </div>

            <h1 class="doc-main-title">快速开始</h1>
            <p class="doc-intro-text">
              在 5 分钟内完成 灵码工坊 的安装并创建你的第一个 AI 应用。灵感一触即发，编码从未如此简单。
            </p>

            <h3 class="doc-section-title" id="sec-1">1. 安装灵码工坊 CLI</h3>
            <p class="doc-body-p">灵码工坊 提供功能强大的命令行工具，帮助你快速创建、开发和部署项目。</p>

            <div class="doc-code-tabs">
              <button
                v-for="t in (['npm','pnpm','yarn'] as const)"
                :key="t"
                class="doc-code-tab-btn"
                :class="{ active: codeTab === t }"
                @click="codeTab = t"
              >{{ t }}</button>
            </div>
            <div class="doc-code-container">
              <div class="doc-code-content">
                <span class="code-line-nums">1</span>
                <span class="code-lines-text">
                  <span class="code-keyword">{{ codeTab }}</span>
                  <span class="code-command">install</span>
                  <span class="code-param">-g @lingma/forge-cli</span>
                </span>
              </div>
              <button class="doc-code-copy-btn"><svg class="icon"><use href="#copy" /></svg><span>复制</span></button>
            </div>

            <div class="doc-tip-box">
              <div class="doc-tip-avatar"><img :src="IMG.brand.mascotHero" alt="Mascot Avatar" /></div>
              <div class="doc-tip-text">
                <strong>提示:</strong> 安装完成后，可运行 <code>forge --version</code> 验证是否成功安装。
              </div>
            </div>

            <h3 class="doc-section-title" id="sec-2">2. 创建并启动你的第一个项目</h3>
            <p class="doc-body-p">使用以下命令创建一个新项目，并启动本地开发服务器。</p>
            <div class="doc-code-container">
              <div class="doc-code-content">
                <span class="code-line-nums">1<br />2</span>
                <span class="code-lines-text">
                  <span class="code-keyword">forge</span> <span class="code-command">create</span> <span class="code-param">my-first-app</span><br />
                  <span class="code-keyword">cd</span> <span class="code-param">my-first-app</span> <span class="code-keyword">&amp;&amp;</span> <span class="code-keyword">forge</span> <span class="code-command">dev</span>
                </span>
              </div>
              <button class="doc-code-copy-btn"><svg class="icon"><use href="#copy" /></svg><span>复制</span></button>
            </div>

            <h3 class="doc-section-title" id="sec-3">3. 在浏览器中预览</h3>
            <p class="doc-body-p">
              打开浏览器访问
              <code style="background: #f4f8fb; padding: 2px 6px; border-radius: 4px; color: var(--doc-primary-color); font-weight: 700">http://localhost:5173</code>，即可看到默认的示例应用。
            </p>

            <h3 class="doc-section-title" id="sec-next" style="border-bottom: none; margin-bottom: 8px">下一步</h3>
            <div class="doc-next-grid">
              <a v-for="(n, i) in [
                { icon: 'cube', t: '了解核心概念', p: '理解灵码工坊的核心组件与工作原理' },
                { icon: 'spark', t: '探索 AI 生成', p: '学习如何使用对话与模板生成应用' },
                { icon: 'file-code', t: '查看 API 参考', p: '查询完整的 API 文档与示例' },
                { icon: 'cloud', t: '部署你的应用', p: '将应用部署到云端对外提供服务' },
              ]" :key="i" href="#" class="doc-next-card">
                <div class="doc-next-left">
                  <div class="doc-next-icon-box"><svg class="icon"><use :href="`#${n.icon}`" /></svg></div>
                  <div class="doc-next-info"><h4>{{ n.t }}</h4><p>{{ n.p }}</p></div>
                </div>
                <svg class="icon chevron-next"><use href="#chevron-right" /></svg>
              </a>
            </div>
          </main>

          <!-- 右侧 TOC -->
          <aside class="doc-right-sidebar">
            <div class="right-sidebar-card">
              <div class="right-card-title">本页导航</div>
              <nav class="toc-list">
                <a
                  v-for="(t, i) in tocItems"
                  :key="i"
                  :href="t.href"
                  class="toc-item"
                  :class="{ active: tocActive === t.href }"
                  @click.prevent="setToc(t.href)"
                >
                  <svg class="icon"><use href="#link" /></svg>
                  <span>{{ t.label }}</span>
                </a>
              </nav>
            </div>

            <div class="right-sidebar-card">
              <div class="right-card-title">帮助与反馈</div>
              <div class="help-list">
                <div class="help-item">
                  <svg class="icon"><use href="#feedback" /></svg>
                  <div class="help-item-info"><h5>文档反馈</h5><p>帮助我们改进文档</p></div>
                </div>
                <div class="help-item">
                  <svg class="icon"><use href="#users" /></svg>
                  <div class="help-item-info"><h5>加入开发者社区</h5><p>与开发者交流与提问</p></div>
                </div>
              </div>
            </div>

            <div class="right-sidebar-card ai-assistant-card">
              <div class="ai-assistant-header">
                <div class="ai-assistant-title">
                  <svg class="icon" style="stroke-width: 2.5"><use href="#spark" /></svg>
                  <span>灵码助手</span>
                </div>
                <span class="ai-status-online"><span class="ai-status-dot"></span>在线</span>
              </div>
              <div class="ai-chat-bubble">👋 你好！我是灵码助手，可以帮你快速找到文档、解答问题或生成代码示例。</div>
              <div class="ai-quick-actions">
                <button v-for="(q, i) in ['如何集成到 Next.js 项目？','API 调用失败如何排查？','生成一个用户登录页面示例']" :key="i" class="ai-action-btn">
                  <span>{{ q }}</span><svg class="icon"><use href="#chevron-right" /></svg>
                </button>
              </div>
              <div class="ai-chat-input-wrapper">
                <input type="text" class="ai-chat-input" placeholder="向我提问..." />
                <button class="ai-send-btn"><svg class="icon"><use href="#send" /></svg></button>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </div>

    <!-- 页脚由 BaseLayout 全局统一承载 -->
  </div>
</template>
