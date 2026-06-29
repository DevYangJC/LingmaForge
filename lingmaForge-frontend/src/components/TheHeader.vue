<script setup lang="ts">
/**
 * 顶部导航栏 —— 对应原型 .site-nav（shared.css）
 * ------------------------------------------------------------------
 * 6 个主菜单项使用 <router-link>，高亮态由 isActive() 精确判断当前路由。
 * 支持 variant 属性以适配常规页面、全屏工作台和极简认证页。
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { IMG } from '@/assets/images'
import AppIcon from '@/components/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    variant?: 'default' | 'workbench' | 'minimal'
  }>(),
  {
    variant: 'default',
  },
)

const route = useRoute()
const router = useRouter()

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

const dropdownOpen = ref(false)

function toggleDropdown(e: Event) {
  e.stopPropagation()
  dropdownOpen.value = !dropdownOpen.value
}

function closeDropdown() {
  dropdownOpen.value = false
}

onMounted(() => {
  window.addEventListener('click', closeDropdown)
})

onUnmounted(() => {
  window.removeEventListener('click', closeDropdown)
})
</script>

<template>
  <nav
    class="site-nav"
    :class="{
      'lws-nav': variant === 'workbench',
      'site-nav--minimal': variant === 'minimal',
      'site-nav--workbench': variant === 'workbench',
    }"
  >
    <div class="site-nav-blur-bg"></div>
    <router-link to="/" class="brand">
      <div class="brand-symbol"><img :src="IMG.brand.logo" alt="灵码工坊 Logo" /></div>
      <div class="brand-text">灵码工坊</div>
    </router-link>

    <div v-if="variant !== 'minimal'" class="menu">
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
      <template v-if="variant === 'minimal'">
        <router-link to="/" class="header-link">返回首页</router-link>
        <span class="header-sep">|</span>
        <router-link to="/doc" class="header-link">帮助中心</router-link>
      </template>

      <template v-else>
        <span class="sun"><AppIcon name="sun" /></span>

        <template v-if="variant !== 'workbench'">
          <button class="btn" @click="router.push('/auth')">登录</button>
          <button class="btn primary" @click="router.push('/auth#register')">免费开始</button>
        </template>

        <!-- 针对 workbench 渲染胶囊型下拉组件 -->
        <div v-if="variant === 'workbench'" class="lws-profile-dropdown" @click="toggleDropdown">
          <div class="lws-profile-avatar-box">
            <img class="lws-profile-avatar-img" :src="IMG.brand.mascotHero" alt="Avatar" />
          </div>
          <span class="lws-profile-name">灵码玩家</span>
          <svg class="lws-profile-arrow-icon" viewBox="0 0 24 24">
            <path
              d="m6 9 6 6 6-6"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>

          <div class="lws-dropdown-menu-list" :class="{ 'lws-active': dropdownOpen }">
            <router-link to="/profile" class="lws-dropdown-item-link">个人中心</router-link>
            <router-link to="/works" class="lws-dropdown-item-link">我的作品</router-link>
            <router-link to="/subscription" class="lws-dropdown-item-link">订阅详情</router-link>
            <router-link to="/auth" class="lws-dropdown-item-link" style="color: #ff5f57"
              >退出登录</router-link
            >
          </div>
        </div>

        <!-- 针对默认常规页面渲染圆形头像下拉组件 -->
        <div v-else class="nav-avatar-dropdown-container">
          <button
            class="nav-avatar"
            :class="{ active: route.path === '/profile' || dropdownOpen }"
            @click="toggleDropdown"
            title="个人中心"
          >
            <img :src="IMG.brand.mascotHero" alt="User Avatar" />
          </button>

          <div class="dropdown-menu-list" v-show="dropdownOpen" @click.stop>
            <div class="dropdown-user-info">
              <span class="username">灵码玩家</span>
              <span class="role">免费版</span>
            </div>
            <div class="dropdown-divider"></div>
            <router-link to="/profile" class="dropdown-item-link" @click="closeDropdown"
              >个人中心</router-link
            >
            <router-link to="/works" class="dropdown-item-link" @click="closeDropdown"
              >我的作品</router-link
            >
            <router-link to="/subscription" class="dropdown-item-link" @click="closeDropdown"
              >订阅详情</router-link
            >
            <div class="dropdown-divider"></div>
            <router-link
              to="/auth"
              class="dropdown-item-link logout-link"
              @click="closeDropdown"
              >退出登录</router-link
            >
          </div>
        </div>
      </template>
    </div>
  </nav>
</template>

<style scoped>
/* Locked styles to ensure consistency and fix dropdown visibility issues */
.site-nav,
.lws-nav {
  position: relative !important;
  z-index: auto !important;
  background: transparent !important;
  backdrop-filter: none !important;
  border-bottom: none !important;
  height: 76px !important;
}

/* Auth minimal header and Workbench header need specific z-index to overlay above background/layout */
.site-nav.site-nav--minimal,
.site-nav.site-nav--workbench,
.lws-nav {
  z-index: 100 !important;
}

/* Delegate background and blur to a leaf element so z-index works globally */
.site-nav-blur-bg {
  position: absolute !important;
  inset: 0 !important;
  z-index: -1 !important;
  background: rgba(255, 255, 255, 0.84) !important;
  backdrop-filter: blur(18px) !important;
  border-bottom: 1px solid rgba(215, 226, 236, 0.86) !important;
  pointer-events: none !important;
}

.brand {
  display: inline-flex !important;
  align-items: center !important;
  gap: 12px !important;
  text-decoration: none !important;
  color: inherit !important;
  cursor: pointer !important;
}

.brand-symbol {
  width: 48px !important;
  height: 48px !important;
  position: relative !important;
  border-radius: 12px !important;
  overflow: hidden !important;
  background: white !important;
}

.brand-symbol img {
  position: absolute !important;
  width: 70px !important;
  height: 65px !important;
  left: -11px !important;
  top: -7px !important;
  object-fit: cover !important;
}

.brand-text {
  font: 900 28px/1 var(--font-display) !important;
  letter-spacing: .02em !important;
  color: #111821 !important;
  white-space: nowrap !important;
}

.nav-avatar-dropdown-container {
  position: relative;
  display: flex;
  align-items: center;
}

.dropdown-menu-list {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 160px;
  background: #ffffff;
  border: 1px solid rgba(215, 226, 236, 0.8);
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(28, 42, 58, 0.08);
  padding: 8px 0;
  display: flex;
  flex-direction: column;
  z-index: 2000 !important;
  animation: slideDownIn 0.22s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

.dropdown-user-info {
  padding: 6px 16px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  user-select: none;
}

.dropdown-user-info .username {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.dropdown-user-info .role {
  font-size: 11px;
  color: #00aab3;
  font-weight: 500;
}

.dropdown-divider {
  height: 1px;
  background: rgba(215, 226, 236, 0.5);
  margin: 4px 0;
}

.dropdown-item-link {
  padding: 8px 16px;
  font-size: 13px;
  color: #475569;
  text-decoration: none;
  transition: background 0.16s ease, color 0.16s ease;
  font-weight: 500;
}

.dropdown-item-link:hover {
  background: #edf6f8;
  color: #00aab3;
}

.dropdown-item-link.logout-link {
  color: #ff5f57;
}

.dropdown-item-link.logout-link:hover {
  background: #fff5f5;
  color: #ff5f57;
}

@keyframes slideDownIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Minimal Mode Header Links styles */
.header-link {
  font-size: 14px;
  color: #64748b;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.16s ease;
}

.header-link:hover {
  color: #00a2b0;
}

.header-sep {
  color: #cbd5e1;
  font-size: 12px;
  user-select: none;
}

/* Ensure buttons never wrap and keep consistent height */
.btn {
  white-space: nowrap !important;
  flex-shrink: 0 !important;
}
</style>

