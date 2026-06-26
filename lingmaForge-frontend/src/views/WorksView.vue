<script setup lang="ts">
/**
 * 我的作品（对应 lingma-works.html）
 * 原型同时引用 creative.css + profile.css + works.css（复用 .cc-card / .profile-sidebar 等样式），
 * 故三者均导入。标记 1:1 迁移；侧栏菜单项路由跳转回 /profile 对应锚点。
 */
import { IMG } from '@/assets/images'
import '@/styles/pages/creative.css'
import '@/styles/pages/profile.css'
import '@/styles/pages/works.css'

const projects = [
  { accent: 'accent-a', status: '已部署', statusClass: '', title: 'AI 会员订阅商城', mock: 'AI 会员订阅商城', sub: '多套餐 / 自动续费 / 营销', tag: '电商订阅', time: '2 小时前', pages: 4, api: 3 },
  { accent: 'accent-b', status: '已部署', statusClass: '', title: '客户管理 CRM', mock: '客户管理 CRM', sub: '客户 / 线索 / 报表', tag: '企业应用', time: '1 天前', pages: 6, api: 4 },
  { accent: 'accent-c', status: '已部署', statusClass: '', title: '数据分析看板 Pro', mock: '数据看板 Pro', sub: '可视化 / 图表 / 监控', tag: '数据分析', time: '3 天前', pages: 5, api: 2 },
  { accent: 'accent-d', status: '已部署', statusClass: '', title: '企业官网 3.0', mock: '企业官网 3.0', sub: '响应式 / 品牌 / 宣传', tag: '企业官网', time: '5 天前', pages: 8, api: 2 },
  { accent: 'accent-e', status: '生成中', statusClass: 'orange', title: '活动报名小程序', mock: '活动小程序', sub: '报名 / 签到 / 票务', tag: '小程序', time: '6 天前', pages: 3, api: 2 },
  { accent: 'accent-f', status: '已部署', statusClass: '', title: 'AI 营销落地页', mock: '营销落地页', sub: '引流 / 表单 / 转化', tag: '营销推广', time: '7 天前', pages: 2, api: 1 },
  { accent: 'accent-g', status: '开发中', statusClass: 'blue', title: '智能客服中心', mock: '智能客服中心', sub: '对话 / 知识库 / 工单', tag: 'AI 应用', time: '1 周前', pages: 5, api: 3 },
  { accent: 'accent-h', status: '草稿', statusClass: 'gray', title: '课程预约系统', mock: '课程预约系统', sub: '排课 / 预约 / 班级', tag: '教育培训', time: '1 周前', pages: 4, api: 2 },
]

const stats = [
  { bg: 'bg-all', label: '全部项目', value: 24 },
  { bg: 'bg-deployed', label: '已部署', value: 12 },
  { bg: 'bg-developing', label: '开发中', value: 7 },
  { bg: 'bg-draft', label: '草稿', value: 5 },
]
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
                <a href="#" class="sidebar-menu-item" @click.prevent="$router.push('/profile#info')"><svg><use href="#user" /></svg>个人资料</a>
                <a href="#" class="sidebar-menu-item active"><svg><use href="#folder" /></svg>我的作品</a>
                <a href="#" class="sidebar-menu-item" @click.prevent="$router.push('/profile#favorites')"><svg><use href="#star" /></svg>收藏</a>
                <a href="#" class="sidebar-menu-item" @click.prevent="$router.push('/profile#orders')"><svg><use href="#file-text" /></svg>订单与发票</a>
                <a href="#" class="sidebar-menu-item" @click.prevent="$router.push('/profile#security')"><svg><use href="#shield" /></svg>账号安全</a>
                <a href="#" class="sidebar-menu-item" @click.prevent="$router.push('/profile#notifications')"><svg><use href="#settings" /></svg>通知设置</a>
              </div>

              <div class="sidebar-help-card" style="margin-top: auto !important; width: 100%; box-sizing: border-box">
                <h4 class="sidebar-help-title">需要帮助？</h4>
                <p class="sidebar-help-desc">访问帮助中心或联系客服</p>
                <a href="#" class="sidebar-help-btn">帮助中心 &gt;</a>
              </div>
            </div>
          </aside>

          <!-- 右侧主区 -->
          <main class="profile-main">
            <div class="works-header-row">
              <div>
                <h1 class="works-page-title">我的项目与作品</h1>
                <p class="works-page-subtitle">管理你创建的项目，查看进度与状态</p>
              </div>
              <div class="works-header-right">
                <button class="btn-create-project">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 5v14M5 12h14" /></svg>
                  新建项目
                </button>
                <div class="works-search-wrapper">
                  <input type="text" class="works-search-input" placeholder="搜索项目名称、类型或关键词..." />
                  <svg class="works-search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8" /><path d="M21 21l-4.35-4.35" /></svg>
                </div>
                <div class="works-filter-tabs">
                  <button class="filter-tab active">全部</button>
                  <button class="filter-tab">最近</button>
                  <button class="filter-tab">收藏</button>
                  <button class="filter-tab">回收站</button>
                </div>
                <button class="sort-btn">
                  最近更新
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9" /></svg>
                </button>
              </div>
            </div>

            <div class="works-stats-row">
              <div v-for="(s, i) in stats" :key="i" class="stat-widget-card">
                <div class="widget-icon-bg" :class="s.bg">
                  <svg v-if="i === 0" viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" /></svg>
                  <svg v-else-if="i === 1" viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" /></svg>
                  <svg v-else-if="i === 2" viewBox="0 0 24 24" fill="none" stroke="currentColor"><polyline points="16 18 22 12 16 6" /><polyline points="8 6 2 12 8 18" /></svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /></svg>
                </div>
                <div class="widget-info">
                  <span class="widget-label">{{ s.label }}</span>
                  <span class="widget-value">{{ s.value }}</span>
                </div>
              </div>
            </div>

            <div class="works-grid-scroll-wrapper">
              <div class="works-projects-grid">
                <article v-for="(p, i) in projects" :key="i" class="cc-card" tabindex="0">
                  <div class="cc-thumb" :class="p.accent">
                    <span class="cc-status" :class="p.statusClass">{{ p.status }}</span>
                    <span class="cc-mock-title">{{ p.mock }}<br /><small>{{ p.sub }}</small></span>
                    <span class="cc-lines"><i></i><i></i><i></i></span>
                  </div>
                  <div class="cc-card-body">
                    <div class="cc-card-title-row">
                      <div class="cc-card-title">{{ p.title }}</div>
                      <span class="cc-card-category-tag">{{ p.tag }}</span>
                    </div>
                    <div class="cc-card-update-time">更新于 {{ p.time }}</div>
                    <div class="cc-card-footer-stats">
                      <span class="stat-item"><svg class="icon-small"><use href="#file-text" /></svg> {{ p.pages }} 页面</span>
                      <span class="stat-item"><svg class="icon-small"><use href="#activity" /></svg> {{ p.api }} API</span>
                      <button class="btn-card-more"><svg class="icon-small"><use href="#more-horizontal" /></svg></button>
                    </div>
                  </div>
                </article>
              </div>
            </div>

            <div class="recent-activities-panel">
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px">
                <h3 class="panel-section-title">最近动态</h3>
                <div class="works-pagination">
                  <button class="page-arrow"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="15 18 9 12 15 6" /></svg></button>
                  <button class="page-num active">1</button>
                  <button class="page-num">2</button>
                  <button class="page-num">3</button>
                  <button class="page-arrow"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6" /></svg></button>
                </div>
              </div>
              <div class="activities-wrapper">
                <div class="activity-row">
                  <div class="activity-left">
                    <div class="activity-icon-wrapper"><svg><use href="#file-text" /></svg></div>
                    <span class="activity-text"><strong>AI 会员订阅商城</strong> 更新了 3 个组件</span>
                  </div>
                  <span class="activity-time">2 小时前</span>
                </div>
                <div class="activity-row">
                  <div class="activity-left">
                    <div class="activity-icon-wrapper text-deployed"><svg><use href="#check-circle" /></svg></div>
                    <span class="activity-text"><strong>客户管理 CRM</strong> 成功部署 to 生产环境</span>
                  </div>
                  <span class="activity-time">1 天前</span>
                </div>
                <div class="activity-row">
                  <div class="activity-left">
                    <div class="activity-icon-wrapper text-developing"><svg><use href="#activity" /></svg></div>
                    <span class="activity-text"><strong>智能客服中心</strong> 创建了新接口 api/chat</span>
                  </div>
                  <span class="activity-time">2 天前</span>
                </div>
                <div class="activity-row">
                  <div class="activity-left">
                    <div class="activity-icon-wrapper"><svg><use href="#file-text" /></svg></div>
                    <span class="activity-text"><strong>课程预约系统</strong> 创建了项目</span>
                  </div>
                  <span class="activity-time">1 周前</span>
                </div>
              </div>
            </div>
          </main>
        </div>
      </div>
    </div>

    <!-- 页脚由 BaseLayout 全局统一承载 -->
  </div>
</template>
