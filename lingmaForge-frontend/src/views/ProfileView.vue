<script setup lang="ts">
/**
 * 个人中心（对应 lingma-profile.html）
 * 样式原样引自 profile.css；标记 1:1 迁移。
 * 侧栏菜单锚点平滑滚动 + scrollspy 高亮（对应原型 <script> 的滚动监听逻辑），
 * 适配 SPA：在 .profile-main 滚动容器内监听并切换 active 菜单项。
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { IMG } from '@/assets/images'
import '@/styles/pages/profile.css'

const mainRef = ref<HTMLElement | null>(null)
const activeId = ref('info')

const menus = [
  { href: '#info', icon: 'user', label: '个人资料' },
  { href: '#works', icon: 'folder', label: '我的作品', to: '/works' },
  { href: '#favorites', icon: 'star', label: '收藏' },
  { href: '#orders', icon: 'file-text', label: '订单与发票' },
  { href: '#security', icon: 'shield', label: '账号安全' },
  { href: '#notifications', icon: 'settings', label: '通知设置' },
]

let scrollTimer: number | null = null
function updateActiveOnScroll() {
  const container = mainRef.value
  if (!container) return
  const containerRect = container.getBoundingClientRect()
  let activeHref: string | null = null
  let minDiff = Infinity
  for (const m of menus) {
    if (m.to) continue
    const el = document.querySelector(m.href) as HTMLElement | null
    if (!el) continue
    const r = el.getBoundingClientRect()
    if (r.top < containerRect.bottom - 50 && r.bottom > containerRect.top + 50) {
      const diff = Math.abs(r.top - containerRect.top)
      if (diff < minDiff) {
        minDiff = diff
        activeHref = m.href
      }
    }
  }
  if (activeHref) activeId.value = activeHref.slice(1)
}

function onScroll() {
  if (scrollTimer) window.clearTimeout(scrollTimer)
  scrollTimer = window.setTimeout(updateActiveOnScroll, 60)
}

function scrollTo(href: string) {
  const container = mainRef.value
  const el = document.querySelector(href) as HTMLElement | null
  if (!container || !el) return
  const targetTop = el.getBoundingClientRect().top - container.getBoundingClientRect().top + container.scrollTop
  container.scrollTo({ top: targetTop - 10, behavior: 'smooth' })
  activeId.value = href.slice(1)
}

onMounted(() => {
  mainRef.value?.addEventListener('scroll', onScroll)
  updateActiveOnScroll()
})
onUnmounted(() => {
  mainRef.value?.removeEventListener('scroll', onScroll)
  if (scrollTimer) window.clearTimeout(scrollTimer)
})
</script>

<template>
  <div class="shot">
    <div class="profile-view">
      <div class="profile-layout-container">
        <div class="profile-top-section">
          <!-- 左侧栏 -->
          <aside class="profile-sidebar">
            <div class="sidebar-card">
              <div class="profile-avatar-wrapper">
                <div class="profile-avatar-circle">
                  <img :src="IMG.subscription.mascotAvatar" alt="User Mascot Avatar" />
                </div>
                <div class="profile-avatar-status"></div>
              </div>
              <h3 class="profile-user-name">灵码玩家<span class="profile-badge-pro">Pro</span></h3>
              <span class="profile-user-id">玩家 ID: lmplayer_1024</span>

              <div class="sidebar-menu">
                <a
                  v-for="m in menus"
                  :key="m.href"
                  href="#"
                  class="sidebar-menu-item"
                  :class="{ active: activeId === m.href.slice(1) }"
                  @click.prevent="m.to ? $router.push(m.to) : scrollTo(m.href)"
                >
                  <svg><use :href="`#${m.icon}`" /></svg>
                  {{ m.label }}
                </a>
              </div>

              <div class="sidebar-help-card" style="margin-top: auto !important; width: 100%; box-sizing: border-box">
                <h4 class="sidebar-help-title">需要帮助？</h4>
                <p class="sidebar-help-desc">访问帮助中心或联系客服</p>
                <a href="#" class="sidebar-help-btn">帮助中心 &gt;</a>
              </div>
            </div>
          </aside>

          <!-- 右侧主区 -->
          <main ref="mainRef" class="profile-main">
            <!-- Row 1 -->
            <div class="profile-row-1">
              <section class="profile-card" id="info">
                <div class="card-header-row"><h2 class="card-title">个人资料</h2></div>
                <div class="profile-form-grid">
                  <div class="form-item">
                    <label class="form-label">用户名</label>
                    <div class="form-input-wrapper">
                      <input type="text" class="form-input" value="灵码玩家" />
                      <svg class="form-edit-icon"><use href="#edit-3" /></svg>
                    </div>
                  </div>
                  <div class="form-item">
                    <label class="form-label">邮箱</label>
                    <div class="form-input-wrapper">
                      <input type="email" class="form-input" value="player@lingma.dev" />
                      <span class="form-badge-verified"><svg><use href="#check-circle" /></svg> 已验证</span>
                      <svg class="form-edit-icon"><use href="#edit-3" /></svg>
                    </div>
                  </div>
                  <div class="form-item">
                    <label class="form-label">个人简介</label>
                    <div class="form-input-wrapper">
                      <textarea class="form-textarea">热爱创造与编程，专注于 AI 应用开发与数字产品设计。</textarea>
                      <svg class="form-edit-icon" style="top: 12px"><use href="#edit-3" /></svg>
                      <span class="form-char-count">26/120</span>
                    </div>
                  </div>
                  <div class="form-row-cols">
                    <div class="form-item" style="flex-direction: column; align-items: stretch; gap: 6px">
                      <label class="form-label" style="width: auto">角色</label>
                      <div class="form-select-wrapper">
                        <select class="form-select"><option>开发者</option><option>设计师</option><option>产品经理</option><option>运营专家</option></select>
                        <svg class="form-select-arrow"><use href="#chevron-down" /></svg>
                      </div>
                    </div>
                    <div class="form-item" style="flex-direction: column; align-items: stretch; gap: 6px">
                      <label class="form-label" style="width: auto">所在地区</label>
                      <div class="form-select-wrapper">
                        <select class="form-select"><option>中国 · 北京</option><option>中国 · 上海</option><option>中国 · 深圳</option><option>中国 · 广州</option><option>其他国家/地区</option></select>
                        <svg class="form-select-arrow"><use href="#chevron-down" /></svg>
                      </div>
                    </div>
                  </div>
                  <div class="form-btn-row"><button class="btn-save">保存修改</button></div>
                </div>
              </section>

              <section class="profile-card">
                <div class="card-header-row"><h2 class="card-title">账户概览</h2></div>
                <div class="overview-sub-box">
                  <div class="sub-box-left">
                    <span class="sub-box-title">灵码工坊 Pro 版<span class="profile-badge-pro">Pro</span></span>
                    <span class="sub-box-desc">专业版权益，助力高效创作</span>
                  </div>
                  <button class="btn-manage-sub" @click="$router.push('/subscription')">管理订阅</button>
                </div>
                <div class="sub-meta-grid">
                  <div class="sub-meta-item">
                    <span class="sub-meta-label"><svg><use href="#calendar" /></svg>下次续费日期</span>
                    <span class="sub-meta-value">2025-06-30</span>
                  </div>
                  <div class="sub-meta-item">
                    <span class="sub-meta-label"><svg><use href="#credit-card" /></svg>支付方式</span>
                    <span class="sub-meta-value">
                      <span style="color: var(--profile-text-muted)">**** **** ****</span> 4242
                      <a href="#" class="sub-meta-link" style="margin-left: 8px">更换</a>
                    </span>
                  </div>
                </div>
                <div class="usage-section">
                  <div class="usage-header-row">
                    <span class="usage-title">使用情况（本周期）</span>
                    <a href="#" class="usage-more-link">查看详情 &gt;</a>
                  </div>
                  <div class="usage-item">
                    <div class="usage-item-header">
                      <span class="usage-item-name"><svg><use href="#zap" /></svg>AI 生成次数</span>
                      <div class="usage-item-nums">12,842 <span>/ 20,000</span></div>
                    </div>
                    <div class="usage-progress-wrapper">
                      <div class="usage-progress-bg" style="flex: 1"><div class="usage-progress-fill" style="width: 64.21%"></div></div>
                      <span class="usage-pct">64%</span>
                    </div>
                  </div>
                  <div class="usage-item">
                    <div class="usage-item-header">
                      <span class="usage-item-name"><svg><use href="#database" /></svg>存储空间</span>
                      <div class="usage-item-nums">28.6 GB <span>/ 100 GB</span></div>
                    </div>
                    <div class="usage-progress-wrapper">
                      <div class="usage-progress-bg" style="flex: 1"><div class="usage-progress-fill" style="width: 28.6%"></div></div>
                      <span class="usage-pct">29%</span>
                    </div>
                  </div>
                  <div class="usage-item">
                    <div class="usage-item-header">
                      <span class="usage-item-name"><svg><use href="#activity" /></svg>API 调用次数</span>
                      <div class="usage-item-nums">128,842 <span>/ 1,000,000</span></div>
                    </div>
                    <div class="usage-progress-wrapper">
                      <div class="usage-progress-bg" style="flex: 1"><div class="usage-progress-fill" style="width: 12.88%"></div></div>
                      <span class="usage-pct">12%</span>
                    </div>
                  </div>
                </div>
              </section>
            </div>

            <!-- Row 2 -->
            <div class="profile-row-2">
              <section class="profile-card" id="works">
                <div class="card-header-row">
                  <h2 class="card-title">我的作品</h2>
                  <a href="#" class="card-header-link" @click.prevent="$router.push('/creative')">全部作品 &gt;</a>
                </div>
                <div class="works-grid">
                  <article class="work-card">
                    <div class="work-thumb work-thumb-mall">
                      <div class="work-thumb-mesh"></div>
                      <div class="work-thumb-graphic">
                        <div class="graphic-mall-box">
                          <div class="graphic-mall-line"></div>
                          <div class="graphic-mall-row"><div class="graphic-mall-item"></div><div class="graphic-mall-item"></div></div>
                        </div>
                      </div>
                      <span class="work-type-badge">网站应用</span>
                    </div>
                    <div class="work-card-body">
                      <h4 class="work-item-title" title="AI 会员订阅商城">AI 会员订阅商城</h4>
                      <span class="work-item-time">更新于 2 小时前</span>
                      <div class="work-card-footer">
                        <span class="work-status-badge published">已发布</span>
                        <button class="work-menu-btn"><svg><use href="#more-horizontal" /></svg></button>
                      </div>
                    </div>
                  </article>

                  <article class="work-card">
                    <div class="work-thumb work-thumb-app">
                      <div class="work-thumb-mesh"></div>
                      <div class="work-thumb-graphic">
                        <div class="graphic-app-phone">
                          <div class="graphic-app-circle"></div>
                          <div class="graphic-app-bar"></div>
                          <div class="graphic-app-bar" style="width: 70%"></div>
                        </div>
                      </div>
                      <span class="work-type-badge">移动应用</span>
                    </div>
                    <div class="work-card-body">
                      <h4 class="work-item-title" title="灵码 AI 助手 App">灵码 AI 助手 App</h4>
                      <span class="work-item-time">更新于 1 天前</span>
                      <div class="work-card-footer">
                        <span class="work-status-badge developing">开发中</span>
                        <button class="work-menu-btn"><svg><use href="#more-horizontal" /></svg></button>
                      </div>
                    </div>
                  </article>

                  <article class="work-card">
                    <div class="work-thumb work-thumb-dashboard">
                      <div class="work-thumb-mesh"></div>
                      <div class="work-thumb-graphic">
                        <div class="graphic-dash-chart">
                          <div class="graphic-dash-bar" style="height: 40%"></div>
                          <div class="graphic-dash-bar" style="height: 70%"></div>
                          <div class="graphic-dash-bar" style="height: 50%"></div>
                          <div class="graphic-dash-bar" style="height: 90%"></div>
                        </div>
                      </div>
                      <span class="work-type-badge">数据看板</span>
                    </div>
                    <div class="work-card-body">
                      <h4 class="work-item-title" title="数据分析看板 Pro">数据分析看板 Pro</h4>
                      <span class="work-item-time">更新于 3 天前</span>
                      <div class="work-card-footer">
                        <span class="work-status-badge published">已发布</span>
                        <button class="work-menu-btn"><svg><use href="#more-horizontal" /></svg></button>
                      </div>
                    </div>
                  </article>

                  <article class="work-card">
                    <div class="work-thumb work-thumb-official">
                      <div class="work-thumb-mesh"></div>
                      <div class="work-thumb-graphic"><div class="graphic-off-orbit"><div class="graphic-off-core"></div></div></div>
                      <span class="work-type-badge">网站应用</span>
                    </div>
                    <div class="work-card-body">
                      <h4 class="work-item-title" title="企业官网 3.0">企业官网 3.0</h4>
                      <span class="work-item-time">更新于 5 天前</span>
                      <div class="work-card-footer">
                        <span class="work-status-badge published">已发布</span>
                        <button class="work-menu-btn"><svg><use href="#more-horizontal" /></svg></button>
                      </div>
                    </div>
                  </article>
                </div>
              </section>

              <section class="profile-card">
                <div class="card-header-row">
                  <h2 class="card-title">创作数据</h2>
                  <button class="creation-header-dropdown"><span>近 7 天</span><svg><use href="#chevron-down" /></svg></button>
                </div>
                <div class="metrics-grid">
                  <div class="metric-box"><div class="metric-label">AI 生成</div><div class="metric-val-row"><span class="metric-value">12,842</span><span class="metric-change">↑ 18%</span></div></div>
                  <div class="metric-box"><div class="metric-label">新增作品</div><div class="metric-val-row"><span class="metric-value">126</span><span class="metric-change">↑ 18%</span></div></div>
                  <div class="metric-box"><div class="metric-label">阅读量</div><div class="metric-val-row"><span class="metric-value">8,642</span><span class="metric-change">↑ 24%</span></div></div>
                  <div class="metric-box"><div class="metric-label">收藏量</div><div class="metric-val-row"><span class="metric-value">320</span><span class="metric-change">↑ 16%</span></div></div>
                </div>
                <div class="chart-container">
                  <svg class="profile-chart-svg" viewBox="0 0 500 110" preserveAspectRatio="none">
                    <defs>
                      <linearGradient id="chart-grad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stop-color="rgba(0, 184, 196, 0.24)" />
                        <stop offset="100%" stop-color="rgba(0, 184, 196, 0)" />
                      </linearGradient>
                    </defs>
                    <line x1="0" y1="10" x2="500" y2="10" stroke="#f0f4f8" stroke-width="1.5" stroke-dasharray="4 4" />
                    <line x1="0" y1="40" x2="500" y2="40" stroke="#f0f4f8" stroke-width="1.5" stroke-dasharray="4 4" />
                    <line x1="0" y1="70" x2="500" y2="70" stroke="#f0f4f8" stroke-width="1.5" stroke-dasharray="4 4" />
                    <line x1="0" y1="100" x2="500" y2="100" stroke="#dce6ef" stroke-width="1.5" />
                    <text x="10" y="14" fill="#a0aec0" font-size="9" font-family="sans-serif">3k</text>
                    <text x="10" y="44" fill="#a0aec0" font-size="9" font-family="sans-serif">2k</text>
                    <text x="10" y="74" fill="#a0aec0" font-size="9" font-family="sans-serif">1k</text>
                    <text x="10" y="104" fill="#a0aec0" font-size="9" font-family="sans-serif">0</text>
                    <path d="M 40,80 Q 75,90 110,95 T 180,65 T 250,45 T 320,60 T 390,85 T 460,40" fill="none" stroke="#00b8c4" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round" />
                    <path d="M 40,80 Q 75,90 110,95 T 180,65 T 250,45 T 320,60 T 390,85 T 460,40 L 460,100 L 40,100 Z" fill="url(#chart-grad)" />
                    <circle cx="40" cy="80" r="4.5" fill="#ffffff" stroke="#00b8c4" stroke-width="2.5" />
                    <circle cx="110" cy="95" r="4.5" fill="#ffffff" stroke="#00b8c4" stroke-width="2.5" />
                    <circle cx="180" cy="65" r="4.5" fill="#ffffff" stroke="#00b8c4" stroke-width="2.5" />
                    <circle cx="250" cy="45" r="4.5" fill="#ffffff" stroke="#00b8c4" stroke-width="2.5" />
                    <circle cx="320" cy="60" r="4.5" fill="#ffffff" stroke="#00b8c4" stroke-width="2.5" />
                    <circle cx="390" cy="85" r="4.5" fill="#ffffff" stroke="#00b8c4" stroke-width="2.5" />
                    <circle cx="460" cy="40" r="4.5" fill="#ffffff" stroke="#00b8c4" stroke-width="2.5" />
                    <text x="30" y="116" fill="#718096" font-size="9" font-family="sans-serif">5/16</text>
                    <text x="100" y="116" fill="#718096" font-size="9" font-family="sans-serif">5/17</text>
                    <text x="170" y="116" fill="#718096" font-size="9" font-family="sans-serif">5/18</text>
                    <text x="240" y="116" fill="#718096" font-size="9" font-family="sans-serif">5/19</text>
                    <text x="310" y="116" fill="#718096" font-size="9" font-family="sans-serif">5/20</text>
                    <text x="380" y="116" fill="#718096" font-size="9" font-family="sans-serif">5/21</text>
                    <text x="450" y="116" fill="#718096" font-size="9" font-family="sans-serif">5/22</text>
                  </svg>
                </div>
              </section>
            </div>
          </main>
        </div>

        <!-- Row 3 -->
        <div class="profile-row-3">
          <section class="profile-card row-3-card" id="favorites">
            <div class="card-header-row"><h2 class="card-title">已连接账号</h2></div>
            <div class="card-body">
              <div class="accounts-list">
                <div class="account-row">
                  <div class="account-info">
                    <div class="account-icon" style="background: #f0f3f6; color: #24292e"><svg><use href="#github" /></svg></div>
                    <div class="account-name-box"><span class="account-name">GitHub</span><span class="account-user">player1024</span></div>
                  </div>
                  <span class="account-status connected"><svg class="icon" style="width: 14px; height: 14px"><use href="#check" /></svg> 已连接</span>
                </div>
                <div class="account-row">
                  <div class="account-info">
                    <div class="account-icon" style="background: #eafaf1; color: #07c160"><svg><use href="#wechat" /></svg></div>
                    <div class="account-name-box"><span class="account-name">微信</span><span class="account-user">player_1024</span></div>
                  </div>
                  <span class="account-status connected"><svg class="icon" style="width: 14px; height: 14px"><use href="#check" /></svg> 已连接</span>
                </div>
                <div class="account-row">
                  <div class="account-info">
                    <div class="account-icon" style="background: #fef2f2; color: #ea4335"><svg><use href="#google" /></svg></div>
                    <div class="account-name-box"><span class="account-name">Google</span><span class="account-user">player@gmail.com</span></div>
                  </div>
                  <a href="#" class="account-status unconnected">未连接</a>
                </div>
              </div>
              <div class="card-bottom-action"><a href="#" class="card-bottom-link">管理第三方账号 &gt;</a></div>
            </div>
          </section>

          <section class="profile-card row-3-card" id="security" style="overflow: visible">
            <img class="security-card-mascot" :src="IMG.profile.securityGuardian" alt="Mascot Guardian" />
            <div class="card-header-row"><h2 class="card-title">安全状态</h2></div>
            <div class="card-body">
              <div class="security-alert-box">
                <svg class="security-alert-icon"><use href="#shield" /></svg>
                <div class="security-alert-content">
                  <span class="security-level-title">安全级别：<span>高</span></span>
                  <span class="security-level-desc">你的账号安全状态良好，请继续保持</span>
                </div>
              </div>
              <div class="security-list">
                <div class="security-row">
                  <div class="security-label"><span class="security-dot-mark"></span>登录密码</div>
                  <div class="security-value">已设置 <svg class="security-check-icon"><use href="#check" /></svg></div>
                </div>
                <div class="security-row">
                  <div class="security-label"><span class="security-dot-mark"></span>双重验证 (2FA)</div>
                  <div class="security-value">已启用 <svg class="security-check-icon"><use href="#check" /></svg></div>
                </div>
                <div class="security-row">
                  <div class="security-label"><span class="security-dot-mark"></span>绑定手机</div>
                  <div class="security-value">188****1024 <svg class="security-check-icon"><use href="#check" /></svg></div>
                </div>
                <div class="security-row">
                  <div class="security-label"><span class="security-dot-mark"></span>登录设备管理</div>
                  <a href="#" class="security-row-link">3 台设备 <svg><use href="#chevron-right" /></svg></a>
                </div>
              </div>
              <button class="btn-security-settings">前往账号安全设置</button>
            </div>
          </section>

          <section class="profile-card row-3-card" id="orders">
            <div class="card-header-row">
              <h2 class="card-title">最近登录</h2>
              <a href="#" class="card-header-link">查看所有</a>
            </div>
            <div class="card-body">
              <div class="logins-list">
                <div class="login-row">
                  <div class="login-device-icon"><svg><use href="#chrome" /></svg></div>
                  <div class="login-details">
                    <div class="login-device-row"><span class="login-device-name">Windows · Chrome</span><span class="login-device-current">当前设备</span></div>
                    <div class="login-meta"><span class="login-loc">中国 · 北京</span><span>2025-05-22 10:31:22</span></div>
                  </div>
                </div>
                <div class="login-row">
                  <div class="login-device-icon"><svg><use href="#safari" /></svg></div>
                  <div class="login-details">
                    <div class="login-device-row"><span class="login-device-name">macOS · Safari</span></div>
                    <div class="login-meta"><span class="login-loc">中国 · 北京</span><span>2025-05-21 15:47:10</span></div>
                  </div>
                </div>
                <div class="login-row">
                  <div class="login-device-icon"><svg><use href="#safari" /></svg></div>
                  <div class="login-details">
                    <div class="login-device-row"><span class="login-device-name">iPhone · Safari</span></div>
                    <div class="login-meta"><span class="login-loc">中国 · 北京</span><span>2025-05-20 09:12:33</span></div>
                  </div>
                </div>
              </div>
              <div class="card-bottom-action" style="border-top: 0; padding-top: 8px"><a href="#" class="card-bottom-link">登出所有设备 &gt;</a></div>
            </div>
          </section>

          <section class="profile-card row-3-card" id="notifications">
            <div class="card-header-row"><h2 class="card-title">通知设置</h2></div>
            <div class="card-body">
              <div class="notify-settings-list">
                <div class="notify-row">
                  <div class="notify-info"><span class="notify-title">系统通知</span><span class="notify-desc">平台公告、功能更新等</span></div>
                  <input type="checkbox" id="notify-sys" class="switch-checkbox" checked /><label for="notify-sys" class="switch-label"></label>
                </div>
                <div class="notify-row">
                  <div class="notify-info"><span class="notify-title">作品动态</span><span class="notify-desc">作品评论、点赞、收藏等</span></div>
                  <input type="checkbox" id="notify-works" class="switch-checkbox" checked /><label for="notify-works" class="switch-label"></label>
                </div>
                <div class="notify-row">
                  <div class="notify-info"><span class="notify-title">订单与账单</span><span class="notify-desc">订阅续费、支付成功等</span></div>
                  <input type="checkbox" id="notify-bill" class="switch-checkbox" checked /><label for="notify-bill" class="switch-label"></label>
                </div>
                <div class="notify-row">
                  <div class="notify-info"><span class="notify-title">安全提醒</span><span class="notify-desc">登录提醒、账号安全等</span></div>
                  <input type="checkbox" id="notify-sec" class="switch-checkbox" checked /><label for="notify-sec" class="switch-label"></label>
                </div>
              </div>
              <div class="card-bottom-action"><a href="#" class="card-bottom-link">全部通知设置 &gt;</a></div>
            </div>
          </section>
        </div>
      </div>
    </div>

    <!-- 页脚由 BaseLayout 全局统一承载 -->
  </div>
</template>
