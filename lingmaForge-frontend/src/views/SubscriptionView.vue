<script setup lang="ts">
/**
 * 订阅页（对应 lingma-subscription.html）
 * 样式原样引自 subscription.css；标记 1:1 迁移。
 * 自动续费开关、续费提醒等交互保留为原生 input/button，CSS 已定义视觉态。
 */
import { IMG } from '@/assets/images'
import '@/styles/pages/subscription.css'

const bills = [
  { date: '2025-05-30', no: 'INV20250530012' },
  { date: '2025-04-30', no: 'INV20250430011' },
  { date: '2025-03-30', no: 'INV20250330010' },
  { date: '2025-02-28', no: 'INV20250228009' },
  { date: '2025-01-30', no: 'INV20250130008' },
]
</script>

<template>
  <div class="shot">
    <div class="sub-view">
      <div class="pricing-container">
        <!-- 头部 -->
        <header class="sub-page-header">
          <div class="sub-title-area">
            <h1>订阅与用量</h1>
            <p>管理你的订阅方案、用量与支付信息</p>
          </div>
          <div class="sub-header-info">
            <div class="info-item">
              <span class="info-label">当前方案:</span>
              <span class="info-value pro-text">灵码 Pro</span>
              <span class="badge-live">当前生效</span>
            </div>
            <div style="width: 1px; height: 16px; background: #cbd9e5"></div>
            <div class="info-item">
              <span class="info-label">下次续费:</span>
              <span class="info-value">2025-06-30</span>
              <span class="badge-renew">自动续费</span>
            </div>
          </div>
        </header>

        <!-- 顶部网格：方案详情 + Pro 权益 -->
        <div class="sub-top-grid">
          <div class="sub-card plan-detail-card">
            <div>
              <div class="plan-info-top">
                <div class="plan-title-box">
                  <small style="font-size: 12px; color: var(--sub-text-secondary); font-weight: 700">当前方案</small>
                  <div class="plan-name-row">
                    <span class="plan-name">灵码 Pro</span>
                    <span class="badge-recommend">推荐</span>
                  </div>
                  <div class="plan-type">专业版 · 按月订阅</div>
                  <div class="plan-desc">每月享 15,000 次 AI 生成 · 专属客服 · 高级模型</div>
                </div>
                <div class="plan-price-box">
                  <div class="plan-price">¥ 49<span>/ 月</span></div>
                  <div class="plan-tax">含税</div>
                </div>
              </div>

              <div class="plan-features-ribbon">
                <div class="ribbon-item">
                  <div class="ribbon-avatar"><img :src="IMG.avatar.user1" alt="Avatar" /></div>
                  <span>专属高级模型</span>
                </div>
                <div class="ribbon-item">
                  <svg class="icon ribbon-item-check" style="color: var(--sub-success-color); width: 16px; height: 16px"><use href="#check" /></svg>
                  <span>优先处理通道</span>
                </div>
                <div class="ribbon-item">
                  <svg class="icon ribbon-item-check" style="color: var(--sub-success-color); width: 16px; height: 16px"><use href="#check" /></svg>
                  <span>无限项目数量</span>
                </div>
                <div class="ribbon-item">
                  <svg class="icon ribbon-item-check" style="color: var(--sub-success-color); width: 16px; height: 16px"><use href="#check" /></svg>
                  <span>专属客服支持</span>
                </div>
                <div class="ribbon-item">
                  <svg class="icon ribbon-item-check" style="color: var(--sub-success-color); width: 16px; height: 16px"><use href="#check" /></svg>
                  <span>多设备同步</span>
                </div>
              </div>
            </div>

            <div class="plan-progress-actions">
              <div class="plan-progress-left">
                <div class="progress-text-row">
                  <span>当前计费周期: 2025-05-30 ~ 2025-06-30</span>
                  <span style="font-weight: 700; color: var(--sub-text-primary)">剩余 16 天</span>
                </div>
                <div class="progress-track-bg"><div class="progress-bar-fill" style="width: 53%"></div></div>
              </div>
              <div class="plan-actions-right">
                <button class="plan-btn-outline">管理订阅</button>
                <button class="plan-btn-filled">升级套餐</button>
              </div>
            </div>
          </div>

          <div class="sub-card pro-benefits-card">
            <div class="pro-benefits-title">灵码 Pro 会员</div>
            <div class="pro-benefits-subtitle">尊享更多专业能力</div>
            <ul class="pro-benefits-list">
              <li><div class="pro-benefit-check"><svg class="icon"><use href="#check" /></svg></div><span>每月 15,000 次 AI 生成 (含高级模型)</span></li>
              <li><div class="pro-benefit-check"><svg class="icon"><use href="#check" /></svg></div><span>专属客服 · 优先响应</span></li>
              <li><div class="pro-benefit-check"><svg class="icon"><use href="#check" /></svg></div><span>无限项目与团队协作</span></li>
              <li><div class="pro-benefit-check"><svg class="icon"><use href="#check" /></svg></div><span>数据隐私保护与安全合规</span></li>
              <li><div class="pro-benefit-check"><svg class="icon"><use href="#check" /></svg></div><span>成员管理与权限控制</span></li>
            </ul>
            <div class="pro-mascot-wrapper"><img :src="IMG.subscription.proMascotCubes" alt="Pro Mascot" /></div>
          </div>
        </div>

        <!-- 指标卡 -->
        <div class="sub-metrics-row">
          <div class="metric-card">
            <div class="metric-header">
              <div class="metric-title"><svg class="icon" style="stroke-width: 1.8"><use href="#zap" /></svg><span>AI 生成次数</span></div>
              <svg class="icon metric-info-icon"><use href="#info" /></svg>
            </div>
            <div class="metric-value-row"><span class="metric-curr">8,642</span><span class="metric-max">/ 15,000 次</span></div>
            <div class="metric-progress-wrapper"><div class="metric-progress-bg"><div class="metric-progress-fill" style="width: 57.6%"></div></div></div>
            <div class="metric-footer"><span>57% 已使用</span><span>重置于 2025-06-30</span></div>
          </div>

          <div class="metric-card">
            <div class="metric-header">
              <div class="metric-title"><svg class="icon"><use href="#database" /></svg><span>Token 用量</span></div>
              <svg class="icon metric-info-icon"><use href="#info" /></svg>
            </div>
            <div class="metric-value-row"><span class="metric-curr">128,842</span><span class="metric-max">/ 200,000</span></div>
            <div class="metric-progress-wrapper"><div class="metric-progress-bg"><div class="metric-progress-fill" style="width: 64.4%"></div></div></div>
            <div class="metric-footer"><span>64% 已使用</span><span>重置于 2025-06-30</span></div>
          </div>

          <div class="metric-card">
            <div class="metric-header">
              <div class="metric-title"><svg class="icon"><use href="#clock" /></svg><span>部署时长</span></div>
              <svg class="icon metric-info-icon"><use href="#info" /></svg>
            </div>
            <div class="metric-value-row"><span class="metric-curr">32.6</span><span class="metric-max">/ 100 小时</span></div>
            <div class="metric-progress-wrapper"><div class="metric-progress-bg"><div class="metric-progress-fill" style="width: 32.6%"></div></div></div>
            <div class="metric-footer"><span>33% 已使用</span><span>重置于 2025-06-30</span></div>
          </div>

          <div class="metric-card">
            <div class="metric-header">
              <div class="metric-title"><svg class="icon"><use href="#user" /></svg><span>团队席位</span></div>
              <svg class="icon metric-info-icon"><use href="#info" /></svg>
            </div>
            <div class="metric-value-row"><span class="metric-curr">4</span><span class="metric-max">/ 10 个席位</span></div>
            <div class="metric-progress-wrapper"><div class="metric-progress-bg"><div class="metric-progress-fill" style="width: 40%"></div></div></div>
            <div class="metric-footer"><span>40% 已使用</span><a class="metric-manage-link">管理成员</a></div>
          </div>
        </div>

        <!-- 底部网格：账单 + 支付 + 设置 -->
        <div class="sub-bottom-grid">
          <div class="sub-card bills-card">
            <div class="bottom-card-title">账单与支付记录</div>
            <div class="bills-table-wrapper">
              <table class="bills-table">
                <thead>
                  <tr><th>账单日期</th><th>账单编号</th><th>金额</th><th>支付状态</th><th>操作</th></tr>
                </thead>
                <tbody>
                  <tr v-for="b in bills" :key="b.no">
                    <td>{{ b.date }}</td>
                    <td>{{ b.no }}</td>
                    <td class="bill-amount">¥ 49.00</td>
                    <td><span class="badge-paid">已支付</span></td>
                    <td><a href="#" class="bill-action-link">下载发票</a></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <a class="view-all-bills-link">查看全部账单<svg class="icon" style="width: 13px; height: 13px"><use href="#chevron-right" /></svg></a>
          </div>

          <div class="sub-card payments-card">
            <div class="bottom-card-title">支付方式</div>
            <div class="payment-method-item">
              <div class="payment-method-left">
                <span class="card-brand-logo">VISA</span>
                <div class="payment-method-info">Visa **** 8888</div>
              </div>
              <span class="badge-default">默认</span>
            </div>
            <button class="add-payment-btn"><svg class="icon" style="width: 14px; height: 14px"><use href="#plus" /></svg>添加支付方式</button>
          </div>

          <div class="sub-card settings-card">
            <div>
              <div class="bottom-card-title">订阅设置</div>
              <div class="settings-list">
                <div class="setting-row">
                  <div class="setting-info">
                    <span class="setting-label">自动续费</span>
                    <span class="setting-desc">到期前将自动按当前方案续费</span>
                  </div>
                  <label class="switch"><input type="checkbox" checked /><span class="slider"></span></label>
                </div>
                <div class="setting-row">
                  <div class="setting-info">
                    <span class="setting-label">续费提醒</span>
                    <span class="setting-desc">到期前 3 天通过邮件提醒我</span>
                  </div>
                  <button class="setting-arrow-btn"><svg class="icon"><use href="#chevron-right" /></svg></button>
                </div>
                <div class="setting-row">
                  <div class="setting-info">
                    <span class="setting-label">用量提醒</span>
                    <span class="setting-desc">用量达到 80% 时提醒我</span>
                  </div>
                  <button class="setting-arrow-btn"><svg class="icon"><use href="#chevron-right" /></svg></button>
                </div>
              </div>
            </div>
            <div class="cancel-subscription-row"><a class="cancel-subscription-link">取消订阅</a></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 页脚由 BaseLayout 全局统一承载 -->
  </div>
</template>
