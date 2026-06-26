<script setup lang="ts">
/**
 * 站点统一页脚 —— 由 BaseLayout 全局承载，保证各页面 footer 完全一致。
 * ------------------------------------------------------------------
 * 沿用登录页（auth.css 的 .auth-footer）的简洁单行页脚风格：
 * 左侧版权 · 右侧「在线状态 | 隐私政策 | 用户协议」。
 * 色值与结构 1:1 对齐 auth.css，仅在 scoped 内联对应 hex（auth 的
 * --auth-* 变量作用域仅限认证页，全局 footer 取不到，故内联）。
 */
import { useRouter } from 'vue-router'

const router = useRouter()

function goDoc() {
  router.push('/doc')
}
</script>

<template>
  <footer class="sf">
    <div class="sf-left">
      <span class="sf-copyright">© 2025 灵码工坊 · 让创造更简单</span>
    </div>
    <div class="sf-right">
      <span class="sf-status"><span class="sf-dot"></span>在线状态：正常运行</span>
      <span class="sf-sep">|</span>
      <a class="sf-link" @click="goDoc">隐私政策</a>
      <span class="sf-sep">|</span>
      <a class="sf-link" @click="goDoc">用户协议</a>
    </div>
  </footer>
</template>

<style scoped>
/* 与 auth.css .auth-footer 一致的单行页脚 */
.sf {
  flex: 0 0 auto;
  height: 50px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 clamp(24px, 3vw, 48px);
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid rgba(226, 232, 240, 0.8);
  position: relative;
  z-index: 100;
}

.sf-copyright {
  font-size: 12px;
  color: #64748b; /* = --auth-text-muted */
}

.sf-right {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: #475569;
  font-weight: 700;
}

.sf-status {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sf-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.4);
  flex: 0 0 auto;
}

.sf-link {
  color: inherit;
  text-decoration: none;
  cursor: pointer;
  transition: color 0.16s ease;
}

.sf-link:hover {
  color: #00a2b0; /* = --auth-primary */
}

.sf-sep {
  color: #cbd5e1;
  font-size: 11px;
}

/* 与导航栏窄屏断点对齐：窄屏下左右两块换行居中 */
@media (max-width: 640px) {
  .sf {
    height: auto;
    flex-direction: column;
    gap: 10px;
    padding-block: 12px;
    text-align: center;
  }
}
</style>
