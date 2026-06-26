<script setup lang="ts">
/**
 * 顶部导航栏 —— 对应原型 .site-nav（shared.css）
 * ------------------------------------------------------------------
 * 6 个主菜单项使用 <router-link>，高亮态由 isActive() 精确判断当前路由。
 * 不再使用 active-class="active"：其底层是 inclusive 匹配，目标路径是当前
 * 路径前缀即命中，而「/」是所有路径前缀，会导致「首页」在任何页面都高亮。
 * 登录/注册跳 /auth，头像跳 /profile。工作台菜单项指向 /workbench（全屏页）。
 */
import { IMG } from '@/assets/images'
import AppIcon from '@/components/AppIcon.vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const menus = [
  { to: '/', label: '首页' },
  { to: '/creative', label: '创意中心' },
  { to: '/workbench', label: '工作台' },
  { to: '/subscription', label: '订阅' },
  { to: '/pricing', label: '价格' },
  { to: '/doc', label: '文档' },
]

/**
 * 精确高亮判定：
 * - 首页「/」仅在根路径精确命中，避免 inclusive 匹配在所有页面都高亮；
 * - 其余菜单按前缀匹配，兼容未来可能新增的子路由（如 /doc/xxx）。
 */
function isActive(to: string): boolean {
  if (to === '/') return route.path === '/'
  return route.path === to || route.path.startsWith(to + '/')
}
</script>

<template>
  <nav class="site-nav">
    <router-link to="/" class="brand">
      <div class="brand-symbol"><img :src="IMG.brand.logo" alt="灵码工坊 Logo" /></div>
      <div class="brand-text">灵码工坊</div>
    </router-link>

    <div class="menu">
      <router-link
        v-for="m in menus"
        :key="m.to"
        :to="m.to"
        :class="{ active: isActive(m.to) }"
      >
        {{ m.label }}
      </router-link>
    </div>

    <div class="nav-actions">
      <span class="sun"><AppIcon name="sun" /></span>
      <button class="btn" @click="$router.push('/auth')">登录</button>
      <button class="btn primary" @click="$router.push('/auth#register')">免费开始</button>
      <router-link
        to="/profile"
        class="nav-avatar"
        title="个人中心"
        :class="{ active: route.path === '/profile' }"
      >
        <img :src="IMG.brand.mascotHero" alt="User Avatar" />
      </router-link>
    </div>
  </nav>
</template>
