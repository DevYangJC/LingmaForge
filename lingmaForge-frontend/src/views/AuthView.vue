<script setup lang="ts">
/**
 * 认证页（对应 lingma-auth.html，钥匙门概念）—— 全屏页，不进入 BaseLayout。
 * 样式原样引自 auth.css。标记结构 1:1 迁移自原型 body。
 * 交互：登录/注册 主 tab 切换、验证码/密码 方式 tab 切换（对应原型 <script>）。
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { IMG } from '@/assets/images'
import '@/styles/pages/auth.css'

const router = useRouter()
const mainTab = ref<'login' | 'register'>('login')
const methodTab = ref<'code' | 'password'>('code')

function goHome() {
  router.push('/')
}
function goDoc() {
  router.push('/doc')
}
</script>

<template>
  <div class="auth-container">
    <!-- 背景层 -->
    <div class="auth-background-layer">
      <div class="aspect-ratio-cover">
        <img class="bg-cover-image" :src="IMG.auth.keyPortalBg" alt="background" />
        <div class="mascot-wrapper">
          <img class="floating-mascot" :src="IMG.auth.keyPortalMascot" alt="灵码工坊 吉祥物" />
        </div>
      </div>
    </div>

    <!-- Header -->
    <header class="auth-header">
      <div class="brand" style="cursor: pointer" @click="goHome">
        <div class="brand-symbol"><img :src="IMG.brand.logo" alt="灵码工坊 Logo" /></div>
        <div class="brand-text">灵码工坊</div>
      </div>
      <div class="header-menu">
        <a href="#" class="header-link" @click.prevent="goHome">返回首页</a>
        <span class="header-sep">|</span>
        <a href="#" class="header-link" @click.prevent="goDoc">帮助中心</a>
      </div>
    </header>

    <main class="auth-main">
      <!-- 左侧展示 -->
      <section class="auth-left-showcase">
        <div class="showcase-content">
          <h2 class="showcase-title">打开你的<span class="gradient-text">创意入口</span></h2>
          <p class="showcase-sub">连接你的项目、模型与创作世界</p>
          <div class="showcase-features">
            <div class="feature-col">
              <div class="feature-icon-wrapper"><svg class="icon feature-icon"><use href="#cube" /></svg></div>
              <div class="feature-info"><h3>项目云同步</h3><p>多端实时同步</p></div>
            </div>
            <div class="feature-col">
              <div class="feature-icon-wrapper"><svg class="icon feature-icon"><use href="#spark" /></svg></div>
              <div class="feature-info"><h3>AI 实时生成</h3><p>对话驱动快速构建</p></div>
            </div>
            <div class="feature-col">
              <div class="feature-icon-wrapper"><svg class="icon feature-icon"><use href="#rocket" /></svg></div>
              <div class="feature-info"><h3>一键运行部署</h3><p>从开发到上线更简单</p></div>
            </div>
          </div>
        </div>
      </section>

      <!-- 右侧登录卡片 -->
      <section class="auth-right-card">
        <div class="auth-card">
          <div class="auth-tabs">
            <button
              class="auth-tab-btn"
              :class="{ active: mainTab === 'login' }"
              @click="mainTab = 'login'"
            >
              登录
            </button>
            <button
              class="auth-tab-btn"
              :class="{ active: mainTab === 'register' }"
              @click="mainTab = 'register'"
            >
              注册
            </button>
          </div>

          <div class="auth-form-wrapper">
            <div class="form-header">
              <div class="form-header-title-row">
                <div class="shield-badge"><svg class="icon badge-icon"><use href="#shield-check" /></svg></div>
                <h1>{{ mainTab === 'login' ? '继续你的创作' : '开启你的创作之旅' }}</h1>
              </div>
              <p>安全可靠的云端环境，守护你的每一次创意</p>
            </div>

            <form class="auth-form" @submit.prevent>
              <div class="input-group">
                <span class="input-icon"><svg class="icon"><use href="#envelope" /></svg></span>
                <input type="text" :placeholder="mainTab === 'login' ? '邮箱 / 手机号' : '请输入邮箱 / 手机号'" required />
              </div>

              <div v-if="mainTab === 'login'" class="login-method-tabs">
                <button
                  type="button"
                  class="method-tab"
                  :class="{ active: methodTab === 'code' }"
                  @click="methodTab = 'code'"
                >
                  验证码登录
                </button>
                <button
                  type="button"
                  class="method-tab"
                  :class="{ active: methodTab === 'password' }"
                  @click="methodTab = 'password'"
                >
                  密码登录
                </button>
              </div>

              <div class="input-group">
                <span class="input-icon"><svg class="icon"><use href="#shield-key" /></svg></span>
                <input
                  type="text"
                  :placeholder="methodTab === 'password' ? '请输入密码' : '输入 6 位验证码'"
                  required
                />
                <button v-if="methodTab === 'code'" type="button" class="btn-get-code">获取验证码</button>
              </div>

              <div class="form-options">
                <label class="remember-me">
                  <input type="checkbox" checked />
                  <span class="custom-checkbox"></span>
                  记住我
                </label>
                <a href="#" class="forgot-pass-link">忘记密码</a>
              </div>

              <button type="submit" class="btn-login-submit">
                <span>{{ mainTab === 'login' ? '登录' : '注册' }}</span>
                <svg class="submit-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="5" y1="12" x2="19" y2="12"></line>
                  <polyline points="12 5 19 12 12 19"></polyline>
                </svg>
              </button>
            </form>

            <div class="auth-divider"><span>或使用以下方式继续</span></div>

            <div class="oauth-options">
              <button class="btn-oauth"><svg class="oauth-icon" style="color: #000"><use href="#github" /></svg>GitHub</button>
              <button class="btn-oauth"><svg class="oauth-icon" style="color: #09bb07"><use href="#wechat" /></svg>微信</button>
              <button class="btn-oauth"><svg class="oauth-icon"><use href="#google" /></svg>Google</button>
            </div>

            <div class="register-prompt">
              {{ mainTab === 'login' ? '还没有账号？' : '已有账号？' }}
              <a href="#" class="switch-to-register" @click.prevent="mainTab = mainTab === 'login' ? 'register' : 'login'">
                {{ mainTab === 'login' ? '立即注册' : '去登录' }}
              </a>
            </div>
          </div>

          <footer class="ssl-security-tip">
            <svg class="icon security-icon"><use href="#lock" /></svg>
            <span>安全连接已建立 SSL 加密保护中</span>
            <span class="pulse-dot"></span>
          </footer>
        </div>
      </section>
    </main>

    <!-- Footer -->
    <footer class="auth-footer">
      <div class="footer-left"><span class="copyright">© 2025 灵码工坊 · 让创造更简单</span></div>
      <div class="footer-right">
        <span class="status-indicator"><span class="status-dot"></span>在线状态：正常运行</span>
        <span class="footer-sep">|</span>
        <a href="#" class="footer-link">隐私政策</a>
        <span class="footer-sep">|</span>
        <a href="#" class="footer-link">用户协议</a>
      </div>
    </footer>
  </div>
</template>
