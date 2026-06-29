<script setup lang="ts">
/**
 * 工作台 - 简洁模式（对应 lingma-workbench-standalone.html）
 * ------------------------------------------------------------------
 * 样式原样引自 workbench-standalone.css（lws- 前缀隔离，零视觉改动）。
 * 交互逻辑由原型的 <script> 迁移为 Vue 响应式：
 *   - 模板卡片点击 → 回填输入框
 *   - 回车 / 提交按钮 → 调用 store.submit() 触发模式切换到「生成模式」
 *   - 头像下拉、模型循环切换、需求优化、导入项目等微交互保留
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { IMG } from '@/assets/images'
import { useWorkbenchStore } from '@/stores/workbench'
import TheHeader from '@/components/TheHeader.vue'
import '@/styles/pages/workbench-standalone.css'

const router = useRouter()
const store = useWorkbenchStore()

const promptText = ref('')

const templates = [
  {
    key: 'corp',
    name: '企业官网',
    intro: '品牌展示与获客',
    prompt:
      '帮我设计并开发一个精美的企业官网，要求包含现代化的品牌展示板块、核心服务介绍、用户案例轮播以及用于收集潜在客户信息的联系表单，整体风格需具有强烈的科技感 and 信任感。',
  },
  {
    key: 'admin-theme',
    name: '后台管理',
    intro: '数据与业务管理',
    prompt:
      '帮我构建一个功能完善的后台管理系统，具备数据分析大盘、多级权限菜单管理、用户列表表格（支持分页、筛选和编辑）、以及日志记录功能，要求采用简洁专业的浅色系设计。',
  },
  {
    key: 'shop',
    name: '电商订阅',
    intro: '套餐、支付与订单',
    prompt:
      '帮我做一个支持电商订阅机制的商城，提供多档位会员套餐选择、接入支付模拟、带有账单明细列表的个人订单管理后台，界面风格需现代且利于付费引导。',
  },
  {
    key: 'chart',
    name: '数据看板',
    intro: '指标、图表与分析',
    prompt:
      '帮我生成一个交互式的数据可视化看板，要求包含营业额折线趋势图、渠道来源饼图、关键指标卡（如 UV/PV/转化率）以及支持导出为 PDF 的数据概览表格。',
  },
]

function pickTemplate(t: (typeof templates)[number]) {
  promptText.value = t.prompt
  // 微动效：输入框轻微缩放（对应原型 transform: scale(1.01)）
  pulseTarget.value = true
  setTimeout(() => (pulseTarget.value = false), 150)
  focusTextarea()
}

const pulseTarget = ref(false)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
function focusTextarea() {
  textareaRef.value?.focus()
}

async function submit() {
  if (await store.submit(promptText.value)) {
    // 切换到生成模式由 store.mode 驱动；输入文本已存入 store.prompt
    promptText.value = ''
  } else {
    focusTextarea()
  }
}

function onTextareaKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    submit()
  }
}



function optimizePrompt() {
  const val = promptText.value.trim()
  if (!val) {
    promptText.value =
      '帮我开发一个用于个人摄影作品展示的精美响应式网页，有暗黑模式，并包含大图展示弹窗与社交账号链接。'
  } else {
    promptText.value =
      '【优化版】' +
      val +
      '。请重点在视觉设计、动画动效、用户交互和响应式适配这几个维度上进行精细化实现，让页面看起来极具高级感和专业感。'
  }
  focusTextarea()
}

function onImport() {
  alert('导入项目功能已就绪，请选择您的本地工坊项目配置文件。')
}
</script>

<template>
  <div class="lws-page-root">
    <!-- 色键（绿幕抠像）SVG 滤镜：吉祥物去绿底，原样保留。
         放在单根 div 内，保证 <Transition> 可正常对根节点做过渡动画。 -->
    <svg width="0" height="0" style="position: absolute; pointer-events: none">
      <filter id="lws-chroma-key-green-filter" color-interpolation-filters="sRGB">
        <feColorMatrix
          type="matrix"
          values="1 0 0 0 0
                  0 1 0 0 0
                  0 0 1 0 0
                  1.5 -3.5 3 0 1.5"
          result="keyed"
        />
        <feComposite operator="in" in="SourceGraphic" in2="keyed" />
      </filter>
    </svg>
    <div class="lws-shot">
      <!-- 顶部导航 -->
      <TheHeader variant="workbench" />

      <!-- 主体 -->
      <main class="lws-layout">
        <div class="lws-background-grid"></div>

        <div class="lws-core-container">
          <!-- 1. 顶部胶囊 -->
          <div class="lws-top-pill-wrapper">
            <div class="lws-top-pill">
              <span class="lws-pill-title"
                >工作台 <span class="lws-pill-separator">·</span>
                <strong>简洁模式</strong></span
              >
              <span class="lws-pill-divider"></span>
              <router-link to="/works" class="lws-pill-link">
                查看我的项目
                <svg
                  viewBox="0 0 24 24"
                  width="14"
                  height="14"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M9 18l6-6-6-6" />
                </svg>
              </router-link>
            </div>
          </div>

          <!-- 2. 欢迎区 -->
          <div class="lws-welcome-block">
            <div class="lws-ready-badge">
              <span class="lws-ready-dot"></span>
              灵码 AI 已准备就绪
            </div>
            <h1 class="lws-welcome-heading">你好，<span class="lws-gradient-text">灵码玩家</span></h1>
            <p class="lws-welcome-desc">
              今天想创造什么？告诉我你的想法，我会帮你把它变成可运行的应用。
            </p>

            <div class="lws-mascot-overlay">
              <img
                class="lws-mascot-sprite"
                :src="IMG.mascot.chromaKey"
                alt="灵码工坊吉祥物"
              />
            </div>
          </div>

          <!-- 3. 输入卡片 -->
          <form class="lws-input-card" @submit.prevent="submit">
            <div class="lws-textarea-wrapper">
              <textarea
                ref="textareaRef"
                v-model="promptText"
                class="lws-prompt-textarea"
                :style="{
                  transform: pulseTarget ? 'scale(1.01)' : 'scale(1)',
                  transition: 'transform 0.15s ease',
                }"
                placeholder="描述你想创建的应用，例如：帮我做一个会员订阅商城，包含套餐、支付和订单管理..."
                @keydown="onTextareaKeydown"
              ></textarea>
            </div>

            <div class="lws-input-toolbar">
              <div class="lws-toolbar-left">
                <button type="button" class="lws-action-btn" title="上传文件">
                  <svg viewBox="0 0 24 24" width="16" height="16">
                    <path
                      d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"
                    />
                  </svg>
                  <span>上传文件</span>
                </button>
                <div class="lws-action-separator"></div>

                <button type="button" class="lws-action-btn lws-icon-only" title="插入图片">
                  <svg viewBox="0 0 24 24" width="16" height="16">
                    <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                    <circle cx="8.5" cy="8.5" r="1.5" />
                    <polyline points="21 15 16 10 5 21" />
                  </svg>
                </button>
                <div class="lws-action-separator"></div>

                <button type="button" class="lws-action-btn" title="引用模板">
                  <svg viewBox="0 0 24 24" width="16" height="16">
                    <circle cx="12" cy="12" r="4" />
                    <path d="M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-3.92 7.94" />
                  </svg>
                  <span>引用模板</span>
                </button>
                <div class="lws-action-separator"></div>

                <button type="button" class="lws-action-btn lws-icon-only" title="语音输入">
                  <svg viewBox="0 0 24 24" width="16" height="16">
                    <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
                    <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
                    <line x1="12" y1="19" x2="12" y2="23" />
                    <line x1="8" y1="23" x2="16" y2="23" />
                  </svg>
                </button>
              </div>

              <div class="lws-toolbar-right">
                <div class="lws-model-selector" @click.stop="store.cycleModel()">
                  <span>{{ store.model }}</span>
                  <svg viewBox="0 0 24 24" width="14" height="14">
                    <path d="m6 9 6 6 6-6" />
                  </svg>
                </div>

                <div class="lws-cost-info">
                  <span>预计消耗将在分析后显示</span>
                  <svg viewBox="0 0 24 24">
                    <circle cx="12" cy="12" r="10" />
                    <path d="M12 16v-4" />
                    <path d="M12 8h.01" />
                  </svg>
                </div>

                <button type="submit" class="lws-send-circle-btn" title="发送">
                  <svg viewBox="0 0 24 24">
                    <line x1="22" y1="2" x2="11" y2="13" />
                    <polygon points="22 2 15 22 11 13 2 9 22 2" />
                  </svg>
                </button>
              </div>
            </div>
          </form>

          <div class="lws-input-hint-row">Enter 发送 · Shift + Enter 换行</div>

          <!-- 4. 模板推荐 -->
          <div class="lws-cards-row">
            <a
              v-for="t in templates"
              :key="t.key"
              href="#"
              class="lws-template-item"
              :class="`lws-${t.key}`"
              @click.prevent="pickTemplate(t)"
            >
              <div class="lws-template-item-content">
                <div class="lws-template-icon-box">
                  <svg
                    v-if="t.key === 'corp'"
                    viewBox="0 0 24 24"
                    width="18"
                    height="18"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <circle cx="12" cy="12" r="10" />
                    <line x1="2" y1="12" x2="22" y2="12" />
                    <path
                      d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"
                    />
                  </svg>
                  <svg
                    v-else-if="t.key === 'admin-theme'"
                    viewBox="0 0 24 24"
                    width="18"
                    height="18"
                    fill="currentColor"
                  >
                    <rect x="3" y="3" width="8" height="8" rx="1.5" />
                    <rect x="13" y="3" width="8" height="8" rx="1.5" />
                    <rect x="3" y="13" width="8" height="8" rx="1.5" />
                    <rect x="13" y="13" width="8" height="8" rx="1.5" />
                  </svg>
                  <svg
                    v-else-if="t.key === 'shop'"
                    viewBox="0 0 24 24"
                    width="18"
                    height="18"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <circle cx="9" cy="21" r="1" />
                    <circle cx="20" cy="21" r="1" />
                    <path
                      d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"
                    />
                  </svg>
                  <svg
                    v-else
                    viewBox="0 0 24 24"
                    width="18"
                    height="18"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <line x1="18" y1="20" x2="18" y2="10" />
                    <line x1="12" y1="20" x2="12" y2="4" />
                    <line x1="6" y1="20" x2="6" y2="14" />
                  </svg>
                </div>
                <div class="lws-template-label-group">
                  <span class="lws-template-name">{{ t.name }}</span>
                  <span class="lws-template-intro">{{ t.intro }}</span>
                </div>
              </div>
              <svg class="lws-template-chevron" viewBox="0 0 24 24" width="14" height="14">
                <path d="M9 18l6-6-6-6" />
              </svg>
            </a>
          </div>

          <!-- 5. 需求优化链接 -->
          <div class="lws-optimizer-link-wrap">
            <a href="#" class="lws-optimizer-link" @click.prevent="optimizePrompt">
              <svg viewBox="0 0 24 24" width="16" height="16">
                <path
                  d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"
                />
              </svg>
              不知道怎么描述？让 AI 帮我完善需求 &gt;
            </a>
          </div>
        </div>

        <!-- 6. 底部操作 -->
        <footer class="lws-footer-zone">
          <p class="lws-footer-desc">从这里开始，你的项目会自动保存到「我的作品」</p>
          <div class="lws-footer-nav-links">
            <router-link to="/creative" class="lws-footer-nav-btn">
              <svg viewBox="0 0 24 24" width="16" height="16">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
              </svg>
              使用模板创建
            </router-link>
            <span class="lws-footer-vertical-split"></span>
            <a href="#" class="lws-footer-nav-btn" @click.prevent="onImport">
              <svg viewBox="0 0 24 24" width="16" height="16">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="17 8 12 3 7 8" />
                <line x1="12" y1="3" x2="12" y2="15" />
              </svg>
              导入已有项目
            </a>
          </div>
        </footer>
      </main>
    </div>
  </div>
</template>
