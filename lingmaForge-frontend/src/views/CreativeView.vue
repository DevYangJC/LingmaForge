<script setup lang="ts">
/**
 * 创意中心（对应 lingma-creative.html）
 * 样式原样引自 creative.css；标记 1:1 迁移。
 * hero 轮播：原型为 4 个静态 slide（CSS 控制显隐），这里改为数据驱动 + 可点击切换，
 * 自动轮播每 5s 推进，prev/next 与分页圆点均可控，视觉效果与原型一致。
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { IMG } from '@/assets/images'
import '@/styles/pages/creative.css'

interface HeroSlide {
  title: string
  desc: string
  subs: number
  rating: string
  fav: string
  praise: string
  creator: string
  creatorMeta: string
}

const slides: HeroSlide[] = [
  {
    title: 'AI 会员订阅商城',
    desc: '开箱即用的订阅电商系统，支持多套餐、优惠券、自动续费和营销活动，助力快速变现。',
    subs: 1287, rating: '4.9', fav: '1.2k', praise: '98%',
    creator: '灵感工匠', creatorMeta: 'Pro 创作者｜已发布 12 个创意',
  },
  {
    title: '灵码 AI 助手 App',
    desc: '智能对话、知识库检索与多模态交互平台，支持快速接入多种大模型，打造你的专属 AI 助理。',
    subs: 954, rating: '4.8', fav: '890', praise: '95%',
    creator: '设计喵', creatorMeta: 'Pro 创作者｜已发布 8 个创意',
  },
  {
    title: '数据分析看板 Pro',
    desc: '多维度数据可视化分析平台，拖拽生成报表，实时接入多种数据库源，支持精细化运营分析。',
    subs: 1542, rating: '4.9', fav: '1.5k', praise: '99%',
    creator: '数据工匠', creatorMeta: 'Pro 创作者｜已发布 15 个创意',
  },
  {
    title: '企业官网 3.0',
    desc: '高科技感响应式官网解决方案，支持多语言、SEO 优化和精美动画展示，完美展现企业品牌实力。',
    subs: 760, rating: '4.7', fav: '730', praise: '96%',
    creator: 'WebCraft', creatorMeta: '已发布 9 个创意',
  },
]

const activeSlide = ref(0)
let autoTimer: number | null = null

function go(i: number) {
  activeSlide.value = (i + slides.length) % slides.length
}
function prev() {
  go(activeSlide.value - 1)
}
function next() {
  go(activeSlide.value + 1)
}

onMounted(() => {
  autoTimer = window.setInterval(() => go(activeSlide.value + 1), 5000)
})
onUnmounted(() => {
  if (autoTimer) window.clearInterval(autoTimer)
})

const cards = [
  { accent: 'accent-a', title: 'AI 旅行助手', mock: 'AI 旅行助手', sub: '行程 / 地图 / 推荐', status: '运行中', statusClass: '', tags: ['AI', '旅游', '推荐'], author: '行者无疆', verified: true, rating: '4.8', subs: '853 订阅' },
  { accent: 'accent-b light-text', title: '小红书封面生成器', mock: '封面生成器', sub: '图文 / 海报 / 模板', status: '已发布', statusClass: 'blue', tags: ['设计', '封面', '图文'], author: '设计喵', verified: true, rating: '4.9', subs: '742 订阅' },
  { accent: 'accent-c light-text', title: '客户管理 CRM', mock: '客户管理 CRM', sub: '销售 / 线索 / 跟进', status: '运行中', statusClass: '', tags: ['CRM', '销售', 'SaaS'], author: '数字先锋', verified: true, rating: '4.7', subs: '621 订阅' },
  { accent: 'accent-d', title: '数据分析看板', mock: '数据分析看板', sub: '图表 / 指标 / 报表', status: '运行中', statusClass: '', tags: ['数据', '可视化', '图表'], author: '数据工匠', verified: false, rating: '4.9', subs: '1.1k 订阅' },
  { accent: 'accent-e light-text', title: '课程预约系统', mock: '课程预约系统', sub: '日历 / 课表 / 学员', status: '已发布', statusClass: 'blue', tags: ['教育', '预约', '管理'], author: '教育在线', verified: true, rating: '4.6', subs: '468 订阅' },
  { accent: 'accent-f', title: '作品集官网', mock: '作品集官网', sub: '案例 / 简历 / 联系', status: '运行中', statusClass: '', tags: ['作品集', '官网', '响应式'], author: 'WebCraft', verified: true, rating: '4.8', subs: '589 订阅' },
  { accent: 'accent-g light-text', title: '社群管理平台', mock: '社群管理平台', sub: '成员 / 权限 / 运营', status: '已发布', statusClass: 'blue', tags: ['社群', '运营', '工具'], author: '社群专家', verified: true, rating: '4.7', subs: '355 订阅' },
  { accent: 'accent-h', title: 'API 文档站', mock: 'API 文档站', sub: '接口 / 示例 / 鉴权', status: '运行中', statusClass: '', tags: ['开发者', '文档', '接口'], author: 'API Hub', verified: true, rating: '4.9', subs: '676 订阅' },
]

const creators = [
  { name: '灵感工匠', badge: 'Pro 创作者', meta: '创意 12｜订阅 1.2k', following: false },
  { name: '设计喵', badge: 'Pro 创作者', meta: '创意 8｜订阅 892', following: false },
  { name: '数据工匠', badge: 'Pro 创作者', meta: '创意 15｜订阅 1.5k', following: true },
  { name: 'WebCraft', badge: '', meta: '创意 9｜订阅 734', following: false },
]
function toggleFollow(c: (typeof creators)[number]) {
  c.following = !c.following
}
</script>

<template>
  <div class="shot">
    <section class="creative-center-view" id="creative-center-view">
      <div class="cc-content">
        <!-- 左侧筛选 -->
        <aside class="cc-left">
          <div class="cc-page-title">
            <h1>创意中心</h1>
            <p class="cc-muted">订阅优秀创意，一键克隆成你的应用</p>
          </div>
          <section class="cc-panel cc-filter-panel">
            <div class="cc-filter-group">
              <div class="cc-filter-title">分类</div>
              <button class="cc-filter-item active"><svg class="icon"><use href="#spark" /></svg>全部</button>
              <button class="cc-filter-item"><svg class="icon"><use href="#cube" /></svg>企业官网</button>
              <button class="cc-filter-item"><svg class="icon"><use href="#shield" /></svg>电商订阅</button>
              <button class="cc-filter-item"><svg class="icon"><use href="#zap" /></svg>数据看板</button>
              <button class="cc-filter-item"><svg class="icon"><use href="#message" /></svg>内容工具</button>
              <button class="cc-filter-item"><svg class="icon"><use href="#user" /></svg>个人站点</button>
            </div>
            <div class="cc-filter-group">
              <div class="cc-filter-title">标签</div>
              <div class="cc-tag-cloud">
                <span class="cc-tag">AI</span><span class="cc-tag">SaaS</span><span class="cc-tag">响应式</span><span class="cc-tag">企业级</span>
                <span class="cc-tag">可视化</span><span class="cc-tag">图表</span><span class="cc-tag">支付</span><span class="cc-tag">多语言</span>
                <span class="cc-tag">后台管理</span><span class="cc-tag">PWA</span>
              </div>
            </div>
            <div class="cc-filter-group">
              <div class="cc-filter-title">价格</div>
              <label class="cc-check-row active"><span class="cc-box">✓</span>全部</label>
              <label class="cc-check-row"><span class="cc-box"></span>免费 <span class="cc-badge">FREE</span></label>
              <label class="cc-check-row"><span class="cc-box"></span>付费 <span class="cc-badge gold">Pro</span></label>
            </div>
            <div class="cc-filter-group">
              <div class="cc-filter-title">排序</div>
              <select class="cc-select"><option>综合推荐</option><option>最新发布</option><option>订阅最多</option><option>评分最高</option></select>
            </div>
          </section>
        </aside>

        <!-- 中间主区 -->
        <section class="cc-center">
          <section class="cc-hero">
            <div class="cc-hero-orbit"></div>
            <div class="cc-hero-slides">
              <div
                v-for="(s, i) in slides"
                :key="i"
                class="cc-hero-slide"
                :class="{ active: i === activeSlide }"
              >
                <img class="cc-hero-mascot" :src="IMG.brand.mascotZh" alt="灵码工坊吉祥物" />
                <div class="cc-hero-copy">
                  <span class="cc-badge dark"><svg class="icon" style="width: 15px; height: 15px"><use href="#shield" /></svg>精选推荐</span>
                  <h2>{{ s.title }}</h2>
                  <p>{{ s.desc }}</p>
                  <div class="cc-stat-row">
                    <div class="cc-stat"><strong>{{ s.subs }}</strong><span>订阅量</span></div>
                    <div class="cc-stat"><strong>{{ s.rating }}</strong><span>评分</span></div>
                    <div class="cc-stat"><strong>{{ s.fav }}</strong><span>收藏</span></div>
                    <div class="cc-stat"><strong>{{ s.praise }}</strong><span>好评率</span></div>
                  </div>
                  <div class="cc-mini-creator">
                    <img class="cc-avatar" :src="IMG.brand.mascotHero" alt="" />
                    <div><strong>{{ s.creator }}</strong><br /><span>{{ s.creatorMeta }}</span></div>
                  </div>
                </div>
              </div>
            </div>

            <div class="cc-hero-actions">
              <button class="btn primary">订阅</button>
              <button class="cc-outline-btn">克隆</button>
              <button class="cc-round-nav cc-prev" @click="prev"><svg class="icon" style="transform: rotate(180deg)"><use href="#chevron-right" /></svg></button>
              <button class="cc-round-nav cc-next" @click="next"><svg class="icon"><use href="#chevron-right" /></svg></button>
            </div>
            <div class="cc-dots">
              <i
                v-for="(s, i) in slides"
                :key="i"
                class="cc-dot"
                :class="{ active: i === activeSlide }"
                @click="go(i)"
              ></i>
            </div>
          </section>

          <div class="cc-toolbar">
            <div class="cc-tabs">
              <button class="cc-tab active">全部创意</button>
              <button class="cc-tab">最新发布</button>
              <button class="cc-tab">热门订阅</button>
              <button class="cc-tab">高评分</button>
            </div>
            <div class="cc-view-sort">
              <div class="cc-view-toggle">
                <button class="active"><svg class="icon" style="width: 17px; height: 17px"><use href="#cube" /></svg></button>
                <button><svg class="icon" style="width: 17px; height: 17px"><use href="#message" /></svg></button>
              </div>
              <button class="cc-sort">默认排序 <svg class="icon" style="width: 14px; height: 14px"><use href="#chevron-right" /></svg></button>
            </div>
          </div>

          <section class="cc-card-grid">
            <article v-for="(c, i) in cards" :key="i" class="cc-card" tabindex="0">
              <div class="cc-thumb" :class="c.accent">
                <span class="cc-status" :class="c.statusClass">{{ c.status }}</span>
                <span class="cc-mock-title">{{ c.mock }}<br /><small>{{ c.sub }}</small></span>
                <span class="cc-lines"><i></i><i></i><i></i></span>
              </div>
              <div class="cc-card-body">
                <div class="cc-card-title">{{ c.title }}</div>
                <div class="cc-meta-tags">
                  <span v-for="t in c.tags" :key="t" class="cc-mini-tag">{{ t }}</span>
                </div>
                <div class="cc-card-footer">
                  <span class="cc-author">
                    <img :src="IMG.brand.mascotHero" alt="" />{{ c.author }}
                    <span v-if="c.verified" class="cc-verified">✓</span>
                  </span>
                  <span class="cc-rating">★ {{ c.rating }}</span>
                  <span>{{ c.subs }}</span>
                </div>
              </div>
            </article>
          </section>
        </section>

        <!-- 右侧栏 -->
        <aside class="cc-right">
          <section class="cc-panel cc-panel-section">
            <div class="cc-rail-title"><span>热门创作者</span><button>全部 〉</button></div>
            <div v-for="(cr, i) in creators" :key="i" class="cc-creator-row">
              <img class="cc-avatar" :src="IMG.brand.mascotHero" alt="" />
              <div>
                <div class="cc-creator-name">{{ cr.name }} <span v-if="cr.badge" class="cc-badge gold">{{ cr.badge }}</span></div>
                <small>{{ cr.meta }}</small>
              </div>
              <button class="cc-follow" :class="{ following: cr.following }" @click="toggleFollow(cr)">
                {{ cr.following ? '已关注' : '关注' }}
              </button>
            </div>
          </section>

          <section class="cc-panel cc-panel-section">
            <div class="cc-rail-title"><span>本周上新</span><button>更多 〉</button></div>
            <div class="cc-new-row"><span class="cc-tiny-thumb"></span><div><strong>企业官网模板</strong><br /><small>响应式企业官网解决方案</small></div><span class="cc-price">免费</span></div>
            <div class="cc-new-row"><span class="cc-tiny-thumb" style="background: linear-gradient(135deg, #ffc62e, #20ffa3)"></span><div><strong>电商后台管理</strong><br /><small>高效管理商品与订单</small></div><span class="cc-price">¥39</span></div>
            <div class="cc-new-row"><span class="cc-tiny-thumb" style="background: linear-gradient(135deg, #071018, #00e5ff)"></span><div><strong>可视化报表工具</strong><br /><small>拖拽式数据分析工具</small></div><span class="cc-price">¥29</span></div>
          </section>

          <section class="cc-panel cc-panel-section">
            <div class="cc-rail-title"><span>创意趋势</span><button class="cc-sort" style="height: 30px; padding: 0 9px">近 7 天</button></div>
            <div class="cc-trend-metrics">
              <div class="cc-trend-metric"><span>新增创意</span><strong>126</strong><small>↑ 18%</small></div>
              <div class="cc-trend-metric"><span>订阅总量</span><strong>8,642</strong><small>↑ 24%</small></div>
              <div class="cc-trend-metric"><span>活跃创作者</span><strong>320</strong><small>↑ 16%</small></div>
            </div>
            <div class="cc-chart">
              <svg viewBox="0 0 260 94" preserveAspectRatio="none">
                <path d="M10 76 L42 48 L73 68 L105 52 L136 66 L168 41 L199 55 L230 28 L250 42 L250 94 L10 94Z" fill="rgba(0,229,255,.16)" />
                <path d="M10 76 L42 48 L73 68 L105 52 L136 66 L168 41 L199 55 L230 28 L250 42" fill="none" stroke="#00bcd4" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </div>
          </section>
        </aside>

        <nav class="cc-pagination">
          <button class="cc-page-btn">‹</button>
          <button class="cc-page-btn active">1</button>
          <button class="cc-page-btn">2</button>
          <button class="cc-page-btn">3</button>
          <button class="cc-page-btn">4</button>
          <button class="cc-page-btn">5</button>
          <span>...</span>
          <button class="cc-page-btn">20</button>
          <button class="cc-page-btn">›</button>
        </nav>
      </div>
    </section>
  </div>
</template>
