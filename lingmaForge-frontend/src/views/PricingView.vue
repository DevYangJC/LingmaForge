<script setup lang="ts">
/**
 * 价格页（对应 lingma-pricing.html）
 * 样式原样引自 pricing.css；标记 1:1 迁移。共享导航由 BaseLayout 提供，
 * 此处仅渲染 .pricing-view 主体与页脚。faq 项可点击展开/收起（增强交互）。
 */
import { ref } from 'vue'
import { IMG } from '@/assets/images'
import '@/styles/pages/pricing.css'

const faqOpen = ref<Record<number, boolean>>({})
function toggleFaq(i: number) {
  faqOpen.value[i] = !faqOpen.value[i]
}

const faqs = [
  '优惠券如何使用?',
  '积分加成如何计算?',
  '可以随时升级或降级吗?',
  '数据安全如何保障?',
]
</script>

<template>
  <div class="shot">
    <div class="pricing-view">
      <div class="pricing-container">
        <header class="pricing-header">
          <h1>选择适合你的方案</h1>
          <p>灵活的定价方案，满足个人开发者到企业团队的不同需求</p>
        </header>

        <div class="pricing-cards">
          <!-- 基础版 -->
          <div class="pricing-card basic">
            <div>
              <div class="card-top">
                <h2 class="card-title">基础版</h2>
                <p class="card-subtitle">适合个人开发者和轻量使用</p>
                <div class="price-box">
                  <span class="price-currency">¥</span>
                  <span class="price-amount">19</span>
                  <span class="price-period">/ 月</span>
                </div>
                <div class="price-annual-info">¥ 228 / 年<span>¥ 240</span></div>
              </div>
              <ul class="card-features">
                <li><svg class="icon"><use href="#check" /></svg> 每月 5 张优惠券</li>
                <li><svg class="icon"><use href="#check" /></svg> 专属智能模型</li>
                <li><svg class="icon"><use href="#check" /></svg> 积分加成 1.2x</li>
                <li><svg class="icon"><use href="#check" /></svg> 基础功能访问</li>
              </ul>
            </div>
            <div class="card-image-wrapper">
              <img :src="IMG.pricing.basicCubes" alt="Basic Cubes" />
            </div>
            <button class="btn card-btn">选择基础版</button>
          </div>

          <!-- 专业版 -->
          <div class="pricing-card pro">
            <span class="popular-badge">最受欢迎</span>
            <div>
              <div class="card-top">
                <h2 class="card-title">专业版</h2>
                <p class="card-subtitle">适合专业开发者和小型团队</p>
                <div class="price-box">
                  <span class="price-currency">¥</span>
                  <span class="price-amount">49</span>
                  <span class="price-period">/ 月</span>
                </div>
                <div class="price-annual-info">¥ 588 / 年<span>¥ 600</span></div>
              </div>
              <ul class="card-features">
                <li><svg class="icon"><use href="#check" /></svg> 每月 15 张优惠券</li>
                <li><svg class="icon"><use href="#check" /></svg> 专属高级智能模型</li>
                <li><svg class="icon"><use href="#check" /></svg> 积分加成 1.5x</li>
                <li><svg class="icon"><use href="#check" /></svg> 多应用模块权限</li>
                <li><svg class="icon"><use href="#check" /></svg> 优先体验新功能</li>
              </ul>
            </div>
            <div class="card-image-wrapper">
              <img :src="IMG.pricing.proMascot" alt="Pro Mascot" />
            </div>
            <button class="btn card-btn">选择专业版</button>
          </div>

          <!-- 尊享版 -->
          <div class="pricing-card premium">
            <div>
              <div class="card-top">
                <h2 class="card-title">尊享版</h2>
                <p class="card-subtitle">适合团队协作和企业级应用</p>
                <div class="price-box">
                  <span class="price-currency">¥</span>
                  <span class="price-amount">99</span>
                  <span class="price-period">/ 月</span>
                </div>
                <div class="price-annual-info">¥ 1,188 / 年<span>¥ 1,200</span></div>
              </div>
              <ul class="card-features">
                <li><svg class="icon"><use href="#check" /></svg> 无限优惠券</li>
                <li><svg class="icon"><use href="#check" /></svg> 专属定制智能模型</li>
                <li><svg class="icon"><use href="#check" /></svg> 积分加成 2x</li>
                <li><svg class="icon"><use href="#check" /></svg> 会员专属活动</li>
                <li><svg class="icon"><use href="#check" /></svg> 高级团队管理</li>
                <li><svg class="icon"><use href="#check" /></svg> 专属技术支持</li>
              </ul>
            </div>
            <div class="card-image-wrapper">
              <img :src="IMG.pricing.premiumAiCore" alt="Premium Core" />
            </div>
            <button class="btn card-btn">选择尊享版</button>
          </div>
        </div>

        <div class="pricing-footnote">
          所有套餐按年付费可享 8 折优惠 · 价格均为含税价格
          <svg class="icon"><use href="#info" /></svg>
        </div>

        <!-- 功能对比表 -->
        <section class="comparison-section">
          <div class="comparison-table-wrapper">
            <table class="comparison-table">
              <thead>
                <tr>
                  <th colspan="2" class="col-feature-title" style="text-align: left; padding-left: 24px">功能特性</th>
                  <th>
                    <div class="comparison-header-name">基础版</div>
                    <div class="comparison-header-price">¥ 228 / 年</div>
                  </th>
                  <th class="pro-highlight">
                    <div class="pro-badge-wrapper"><span class="pro-badge-table">最受欢迎</span></div>
                    <div class="comparison-header-name">专业版</div>
                    <div class="comparison-header-price">¥ 588 / 年</div>
                  </th>
                  <th>
                    <div class="comparison-header-name">尊享版</div>
                    <div class="comparison-header-price">¥ 1,188 / 年</div>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td rowspan="4" class="category-cell-td">
                    <div class="category-cell">
                      <div class="category-icon-wrapper ai">
                        <svg class="icon" style="stroke-width: 1.8"><use href="#zap" /></svg><span>AI 生成</span>
                      </div>
                    </div>
                  </td>
                  <td class="comparison-subgroup-label">每月优惠券</td>
                  <td>5 张</td>
                  <td class="pro-highlight">15 张</td>
                  <td>无限</td>
                </tr>
                <tr>
                  <td class="comparison-subgroup-label">智能模型</td>
                  <td>专属智能模型</td>
                  <td class="pro-highlight">专属高级智能模型</td>
                  <td>专属定制智能模型</td>
                </tr>
                <tr>
                  <td class="comparison-subgroup-label">积分加成</td>
                  <td>1.2x</td>
                  <td class="pro-highlight">1.5x</td>
                  <td>2x</td>
                </tr>
                <tr>
                  <td class="comparison-subgroup-label">多应用模块访问</td>
                  <td>基础模块</td>
                  <td class="pro-highlight">全部模块</td>
                  <td>全员专属活动</td>
                </tr>

                <tr>
                  <td rowspan="3" class="category-cell-td">
                    <div class="category-cell">
                      <div class="category-icon-wrapper project">
                        <svg class="icon"><use href="#cube" /></svg><span>项目与部署</span>
                      </div>
                    </div>
                  </td>
                  <td class="comparison-subgroup-label">项目数量</td>
                  <td>10 个</td>
                  <td class="pro-highlight">无限</td>
                  <td>无限</td>
                </tr>
                <tr>
                  <td class="comparison-subgroup-label">代码仓库</td>
                  <td>1 个</td>
                  <td class="pro-highlight">10 个</td>
                  <td>无限</td>
                </tr>
                <tr>
                  <td class="comparison-subgroup-label">部署环境</td>
                  <td>1 个</td>
                  <td class="pro-highlight">5 个</td>
                  <td>无限</td>
                </tr>

                <tr>
                  <td rowspan="3" class="category-cell-td">
                    <div class="category-cell">
                      <div class="category-icon-wrapper team">
                        <svg class="icon"><use href="#user" /></svg><span>团队协作</span>
                      </div>
                    </div>
                  </td>
                  <td class="comparison-subgroup-label">团队成员</td>
                  <td>1 人</td>
                  <td class="pro-highlight">10 人</td>
                  <td>无限</td>
                </tr>
                <tr>
                  <td class="comparison-subgroup-label">角色权限</td>
                  <td>基础角色</td>
                  <td class="pro-highlight">多种角色</td>
                  <td>自定义角色</td>
                </tr>
                <tr>
                  <td class="comparison-subgroup-label">协作空间</td>
                  <td>1 个</td>
                  <td class="pro-highlight">10 个</td>
                  <td>无限</td>
                </tr>

                <tr>
                  <td rowspan="3" class="category-cell-td">
                    <div class="category-cell">
                      <div class="category-icon-wrapper support">
                        <svg class="icon"><use href="#shield" /></svg><span>服务支持</span>
                      </div>
                    </div>
                  </td>
                  <td class="comparison-subgroup-label">技术支持</td>
                  <td>工单支持</td>
                  <td class="pro-highlight">优先工单支持</td>
                  <td>专属技术支持</td>
                </tr>
                <tr>
                  <td class="comparison-subgroup-label">SLA 可用性</td>
                  <td>99%</td>
                  <td class="pro-highlight">99.9%</td>
                  <td>99.99%</td>
                </tr>
                <tr>
                  <td class="comparison-subgroup-label">专属支持</td>
                  <td>—</td>
                  <td class="pro-highlight">—</td>
                  <td>专属客户经理</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <!-- FAQ -->
        <section class="faq-section">
          <div class="faq-header">
            <h2>常见问题</h2>
            <a href="#">查看更多 &gt;</a>
          </div>
          <div class="faq-grid">
            <div
              v-for="(q, i) in faqs"
              :key="i"
              class="faq-item"
              :class="{ open: faqOpen[i] }"
              @click="toggleFaq(i)"
            >
              <span class="faq-question">{{ q }}</span>
              <svg class="icon"><use href="#chevron-down" /></svg>
            </div>
          </div>
        </section>

        <!-- 企业方案横幅 -->
        <div class="enterprise-banner">
          <div class="enterprise-left">
            <div class="enterprise-mascot-circle">
              <img :src="IMG.brand.mascotHero" alt="Mascot Avatar" />
            </div>
            <div class="enterprise-title-box">
              <h3>需要企业级解决方案?</h3>
              <p>我们为大中型企业提供定制化方案与专属服务</p>
            </div>
          </div>
          <div class="enterprise-features">
            <div class="enterprise-feature-item"><svg class="icon"><use href="#check" /></svg><span>专属定制功能</span></div>
            <div class="enterprise-feature-item"><svg class="icon"><use href="#check" /></svg><span>私有化部署</span></div>
            <div class="enterprise-feature-item"><svg class="icon"><use href="#check" /></svg><span>专属 SLA 保证</span></div>
            <div class="enterprise-feature-item"><svg class="icon"><use href="#check" /></svg><span>专属客户成功团队</span></div>
          </div>
          <button class="enterprise-btn">
            联系销售团队
            <svg class="icon" style="width: 15px; height: 15px"><use href="#arrow-right" /></svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 页脚由 BaseLayout 全局统一承载 -->
  </div>
</template>
