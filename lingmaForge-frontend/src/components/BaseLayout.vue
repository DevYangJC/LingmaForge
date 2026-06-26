<script setup lang="ts">
/**
 * 基础布局：共享顶部导航 + 路由出口 + 统一页脚
 * ------------------------------------------------------------------
 * 顶部导航 TheHeader 与页脚 TheFooter 全局复用，保证所有挂在本布局下的
 * 页面（首页/创意中心/订阅/价格/文档/个人中心/我的作品）头部与底部一致。
 *
 * 页面切换动画：原型使用跨文档 @view-transition（仅 Chromium 支持），SPA 中
 * 改用 Vue <Transition> 包裹 <router-view>，复用 global.css 中的 fade / slide
 * 关键帧，做到与原型一致的「左侧淡出 + 右侧滑入」过渡效果。
 */
import TheHeader from '@/components/TheHeader.vue'
import TheFooter from '@/components/TheFooter.vue'
</script>

<template>
  <div class="layout-shell">
    <TheHeader />
    <main class="layout-main">
      <router-view v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </router-view>
    </main>
    <TheFooter />
  </div>
</template>

<style scoped>
.layout-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 内容区弹性填充：短内容时把页脚顶到视口底部，长内容时页脚自然下移 */
.layout-main {
  flex: 1 0 auto;
  display: flex;
  flex-direction: column;
}

/* 各页面根节点 .shot 原本带 min-height:100vh，会让页脚永远低于首屏；
   在布局内改为弹性填充，由 layout-main 撑高，页脚得以贴底 */
:deep(.shot) {
  min-height: auto;
  flex: 1 0 auto;
}

/* 与原型 shared.css 的 view-transition 动画对齐：
   旧视图左滑淡出，新视图从右滑入淡入 */
.page-enter-active {
  transition: opacity 0.42s cubic-bezier(0, 0, 0.2, 1),
    transform 0.55s cubic-bezier(0.16, 1, 0.3, 1);
  transition-delay: 0.12s;
}
.page-leave-active {
  transition: opacity 0.22s cubic-bezier(0.4, 0, 1, 1),
    transform 0.55s cubic-bezier(0.16, 1, 0.3, 1);
}
.page-enter-from {
  opacity: 0;
  transform: translateX(24px);
}
.page-leave-to {
  opacity: 0;
  transform: translateX(-24px);
}
</style>
