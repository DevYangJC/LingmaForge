<script setup lang="ts">
/**
 * 首页（对应 lingma-homepage.html）
 * ------------------------------------------------------------------
 * 样式原样引自 homepage.css（共享 global.css 的设计令牌）。
 * 原型 <script> 中的「AI 生成中」打字机 + 进度条循环动画，迁移为 Vue 响应式 +
 * setInterval 驱动，逻辑与原型完全一致（70ms 步进、阶段文案、100% 后 4s 重启）。
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { IMG } from '@/assets/images'
import '@/styles/pages/homepage.css'

const router = useRouter()

const codeText = `import { Button } from '@/ui'

export default function Home() {
  return (
    <div className="page">
      <AIWorkshop />
    </div>
  )
}`

const codeRender = ref('')
const progress = ref(0)
const progressStatus = ref('AI 正在分析需求...')
const progressBarStyle = ref<Record<string, string>>({ width: '0%' })

let timer: number | null = null
let restartTimer: number | null = null

function startGeneration() {
  progress.value = 0
  codeRender.value = ''
  progressBarStyle.value = { width: '0%' }

  if (timer) window.clearInterval(timer)

  timer = window.setInterval(() => {
    progress.value += 1
    const p = progress.value

    if (p > 100) {
      progress.value = 100
      if (timer) window.clearInterval(timer)
      restartTimer = window.setTimeout(startGeneration, 4000)
    }

    progressBarStyle.value = {
      width: `${Math.min(p, 100)}%`,
      ...(p >= 100 ? { background: 'linear-gradient(90deg, #14d9bd, #20ffa3)' } : {}),
    }

    if (p <= 15) {
      progressStatus.value = 'AI 正在分析需求...'
    } else if (p <= 75) {
      progressStatus.value = 'AI 正在生成代码...'
      const typing = (p - 15) / 60
      codeRender.value = codeText.substring(0, Math.floor(typing * codeText.length))
    } else if (p <= 90) {
      progressStatus.value = '正在编译构建应用...'
      codeRender.value = codeText
    } else if (p < 100) {
      progressStatus.value = '正在部署至云端...'
    } else {
      progressStatus.value = '✓ 部署成功!'
    }
  }, 70)
}

onMounted(startGeneration)
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  if (restartTimer) window.clearTimeout(restartTimer)
})

function generate() {
  // 首页「立即生成」→ SPA 跳工作台简洁模式（避免整页刷新）
  router.push('/workbench')
}
</script>

<template>
  <div class="shot">
    <section class="hero">
      <div class="orbital"></div>
      <div class="hero-inner">
        <div class="hero-aura"></div>
        <div class="hero-ring"></div>
        <div class="hero-copy">
          <span class="pill"
            ><svg class="icon" style="width: 15px; height: 15px; color: #00aee9"><use href="#spark" /></svg>
            企业级 AI 应用生成平台</span
          >
          <h1>灵感<span class="gradient-word">一触</span>即发</h1>
          <p class="subtitle">对话生成应用，预览、改代码、部署一气呵成</p>
          <label class="prompt">
            <svg class="icon" style="color: #667789"><use href="#message" /></svg>
            <input placeholder="描述你想创建的应用，例如：做一个会员订阅页..." @keydown.enter="generate" />
            <svg class="icon" style="color: #8aa0b3"><use href="#spark" /></svg>
          </label>
          <div class="hero-buttons">
            <button class="btn primary" @click="generate">
              <svg class="icon"><use href="#spark" /></svg> 立即生成
            </button>
            <button class="btn"><svg class="icon"><use href="#play" /></svg> 查看案例</button>
          </div>
          <div class="metrics">
            <div class="metric-card">
              <div class="metric-icon">
                <svg class="icon" style="width: 36px; height: 36px; stroke-width: 1.8"><use href="#zap" /></svg>
              </div>
              <div><strong>10x</strong><span>开发效率</span></div>
            </div>
            <div class="metric-card">
              <div class="metric-icon">
                <svg class="icon" style="width: 34px; height: 34px"><use href="#shield" /></svg>
              </div>
              <div><strong>99.9%</strong><span>项目可用率</span></div>
            </div>
            <div class="metric-card">
              <div class="metric-icon">
                <svg class="icon" style="width: 35px; height: 35px"><use href="#cube" /></svg>
              </div>
              <div><strong>50万+</strong><span>创意组件</span></div>
            </div>
          </div>
        </div>

        <div class="code-float">
          <pre><code>{{ codeRender }}</code><span class="code-cursor">|</span></pre>
        </div>
        <div class="progress-float">
          <strong>
            <span>{{ progressStatus }}</span>
            <span style="float: right; font-weight: 500">{{ Math.min(progress, 100) }}%</span>
          </strong>
          <div class="bar"><i :style="progressBarStyle"></i></div>
        </div>
        <img class="mascot" :src="IMG.brand.mascotHero" alt="灵码工坊吉祥物" />
      </div>
    </section>

    <section class="workshop">
      <div class="content">
        <div class="section-head">
          <div class="section-title">
            <h2>创意工坊 <span class="slash"></span></h2>
            <div class="tabs">
              <button class="active">精选推荐</button>
              <button>网站应用</button>
              <button>管理系统</button>
              <button>小程序</button>
              <button>数据应用</button>
              <button>全部模板 〉</button>
            </div>
          </div>
          <div class="filters">
            <label class="search"
              ><svg class="icon"><use href="#search" /></svg><input placeholder="搜索模板、组件或功能..."
            /></label>
            <button class="select">
              最新 <svg class="icon" style="width: 14px; height: 14px"><use href="#chevron-right" /></svg>
            </button>
          </div>
        </div>

        <div class="cards">
          <article class="app-card">
            <div class="thumb graph">
              <div class="thumb-title"><strong>SaaS 官网</strong><span>现代极简风格的产品官网</span></div>
              <div class="mock-window"></div>
            </div>
            <div class="card-body">
              <div class="card-title">SaaS 官网 <span class="time">2分钟前</span></div>
              <div class="card-desc">现代极简风格的产品官网</div>
              <div class="chips"><span class="chip">SaaS</span><span class="chip">官网</span><span class="chip">响应式</span></div>
              <div class="card-foot">
                <span class="creator"
                  ><img class="avatar" :src="IMG.brand.mascotHero" alt="" />WebCraft
                  <span class="verified">✓</span></span
                ><span class="success">✓ 部署成功</span
                ><span><svg class="icon" style="width: 14px; height: 14px"><use href="#eye" /></svg> 1.2k 订阅</span>
              </div>
            </div>
          </article>

          <article class="app-card">
            <div class="thumb light">
              <div class="price-ui">
                <div class="price-card"><small>基础版</small><br /><strong>¥19</strong><br /><small>/月</small></div>
                <div class="price-card"><small>专业版</small><br /><strong>¥49</strong><br /><small>/月</small></div>
                <div class="price-card"><small>尊享版</small><br /><strong>¥99</strong><br /><small>/月</small></div>
              </div>
            </div>
            <div class="card-body">
              <div class="card-title">订阅商城 <span class="time">7分钟前</span></div>
              <div class="card-desc">会员订阅与支付系统模板</div>
              <div class="chips"><span class="chip">订阅</span><span class="chip">支付</span><span class="chip">电商</span></div>
              <div class="card-foot">
                <span class="creator"
                  ><img class="avatar" :src="IMG.brand.mascotHero" alt="" />设计喵
                  <span class="verified">✓</span></span
                ><span class="success">✓ 部署成功</span><span>892 订阅</span>
              </div>
            </div>
          </article>

          <article class="app-card">
            <div class="thumb graph">
              <div class="thumb-title"><strong>12,842</strong><span>数据增长率 32.6%</span></div>
              <div class="chart">
                <span class="bar-col" style="height: 32%"></span><span class="bar-col" style="height: 44%"></span
                ><span class="bar-col" style="height: 38%"></span><span class="bar-col" style="height: 52%"></span
                ><span class="bar-col" style="height: 62%"></span><span class="bar-col" style="height: 76%"></span
                ><span class="bar-col" style="height: 88%"></span>
              </div>
            </div>
            <div class="card-body">
              <div class="card-title">数据看板 <span class="time">9分钟前</span></div>
              <div class="card-desc">可视化数据分析仪表盘</div>
              <div class="chips"><span class="chip">数据</span><span class="chip">可视化</span><span class="chip">看板</span></div>
              <div class="card-foot">
                <span class="creator"
                  ><img class="avatar" :src="IMG.brand.mascotHero" alt="" />数据工匠
                  <span class="verified">✓</span></span
                ><span class="success">✓ 部署成功</span><span>1.5k 订阅</span>
              </div>
            </div>
          </article>

          <article class="app-card">
            <div class="thumb person">
              <div class="thumb-title"><strong>Hello, I'm<br />Developer</strong><span>个人开发者作品展示</span></div>
              <div class="person-img"></div>
            </div>
            <div class="card-body">
              <div class="card-title">个人作品集 <span class="time">19分钟前</span></div>
              <div class="card-desc">创意开发者作品展示模板</div>
              <div class="chips"><span class="chip">作品集</span><span class="chip">简历</span><span class="chip">响应式</span></div>
              <div class="card-foot">
                <span class="creator"
                  ><img class="avatar" :src="IMG.brand.mascotHero" alt="" />CodeArtisan
                  <span class="verified">✓</span></span
                ><span class="success">✓ 部署成功</span><span>734 订阅</span>
              </div>
            </div>
          </article>
          <button class="next-card">
            <svg class="icon" style="width: 17px; height: 17px"><use href="#chevron-right" /></svg>
          </button>
        </div>

        <div class="activity">
          <div class="activity-title"><span class="live-dot"></span>实时动态</div>
          <div class="feed">
            <div class="feed-thumb"><img :src="IMG.brand.mascotHero" alt="" /></div>
            <div><strong>用户 <span style="color: #00aab3">灵感玩家</span> 生成了会员订阅页</strong><small>2 分钟前　<span class="success">部署成功</span></small></div>
          </div>
          <div class="feed">
            <div class="feed-thumb"><img :src="IMG.brand.mascotHero" alt="" /></div>
            <div><strong>用户 <span style="color: #00aab3">设计喵</span> 更新了模板</strong><small>5 分钟前　<span style="color: #1689ff">已发布</span></small></div>
          </div>
          <div class="feed">
            <div class="feed-thumb"><img :src="IMG.brand.mascotHero" alt="" /></div>
            <div><strong>用户 <span style="color: #00aab3">WebCraft</span> 生成了 SaaS 官网模板</strong><small>8 分钟前　<span class="success">部署成功</span></small></div>
          </div>
          <div class="feed">
            <div class="feed-thumb" style="background: #102631"></div>
            <div><strong>用户 <span style="color: #00aab3">数据工匠</span> 生成了销售数据看板</strong><small>12 分钟前　<span style="color: #ff9f1c">运行中</span></small></div>
          </div>
          <button class="btn">
            查看全部动态 <svg class="icon" style="width: 16px; height: 16px"><use href="#arrow-right" /></svg>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>
