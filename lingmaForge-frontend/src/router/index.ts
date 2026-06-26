import { createRouter, createWebHistory } from 'vue-router'
import BaseLayout from '@/components/BaseLayout.vue'

/**
 * 路由设计
 * ------------------------------------------------------------------
 * - 共享顶部导航的页面（首页/创意中心/订阅/价格/文档/个人中心/我的作品）
 *   挂在 BaseLayout 父路由下，复用 .site-nav 导航与 SPA 路由过渡动画。
 * - 工作台 /workbench 为全屏页，自带独立导航头与双模式切换逻辑，
 *   不进入 BaseLayout，避免与简洁模式的居中布局冲突。
 * - 认证 /auth 为独立全屏页（钥匙门概念），无导航。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior() {
    return { top: 0 }
  },
  routes: [
    {
      path: '/',
      component: BaseLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
        },
        {
          path: 'creative',
          name: 'creative',
          component: () => import('@/views/CreativeView.vue'),
        },
        {
          path: 'subscription',
          name: 'subscription',
          component: () => import('@/views/SubscriptionView.vue'),
        },
        {
          path: 'pricing',
          name: 'pricing',
          component: () => import('@/views/PricingView.vue'),
        },
        {
          path: 'doc',
          name: 'doc',
          component: () => import('@/views/DocView.vue'),
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
        },
        {
          path: 'works',
          name: 'works',
          component: () => import('@/views/WorksView.vue'),
        },
      ],
    },
    {
      path: '/workbench',
      name: 'workbench',
      component: () => import('@/views/WorkbenchView.vue'),
    },
    {
      path: '/auth',
      name: 'auth',
      component: () => import('@/views/AuthView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

export default router
