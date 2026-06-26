/**
 * 静态资源统一导入映射
 * ------------------------------------------------------------------
 * 设计原则：项目零外部依赖，所有图片均从本地 src/assets 加载。
 * 原型中的外部资源引用（brand/...、../assets/lingma-product-ui/...、../output/...）
 * 全部本地化并重命名为 ASCII 文件名，避免 Vite 对中文/特殊字符路径的构建问题。
 *
 * 替换规则（原型 src → 本地 key）：
 *   brand/lingma-forge-logo.png                      → brand.logo
 *   brand/hero/lingma-forge-mascot-v2.png            → brand.mascotHero
 *   ../output/mascot-chroma-key-green.png            → mascot.chromaKey
 *   ../assets/.../key-portal-background-...4K高清.png → auth.keyPortalBg
 *   ../assets/.../key-portal-mascot-exact-v2抠像.png  → auth.keyPortalMascot
 *   ../assets/.../pricing-basic-cubes抠像.png         → pricing.basicCubes
 *   ../assets/.../pricing-premium-ai-core抠像.png     → pricing.premiumAiCore
 *   ../assets/.../pricing-pro-mascot-...抠像.png      → pricing.proMascot
 *   ../assets/.../mascot-account-security-guardian抠图.png → profile.securityGuardian
 *   ../assets/.../subscription-gold-module-cube抠像.png    → subscription.goldCube
 *   ../assets/.../subscription-pro-mascot-cubes抠图.png    → subscription.proMascotCubes
 *   ../assets/.../头像/subscription-mascot-avatar抠像.png  → subscription.mascotAvatar
 *   ../assets/.../头像/头像1.jpg                            → avatar.user1
 */
import logo from '@/assets/brand/lingma-forge-logo.png'
import mascotHero from '@/assets/brand/hero/lingma-forge-mascot-v2.png'
import mascotAlt from '@/assets/brand/lingma-forge-mascot-v2-alt.png'
import mascotZh from '@/assets/brand/lingma-forge-zh.png'
import chromaKey from '@/assets/mascot/mascot-chroma-key-green.png'

import keyPortalBg from '@/assets/auth/key-portal-bg.png'
import keyPortalMascot from '@/assets/auth/key-portal-mascot.png'

import pricingBasicCubes from '@/assets/pricing/pricing-basic-cubes.png'
import pricingPremiumAiCore from '@/assets/pricing/pricing-premium-ai-core.png'
import pricingProMascot from '@/assets/pricing/pricing-pro-mascot.png'

import securityGuardian from '@/assets/profile/security-guardian.png'

import goldCube from '@/assets/subscription/gold-module-cube.png'
import proMascotCubes from '@/assets/subscription/pro-mascot-cubes.png'
import subscriptionMascotAvatar from '@/assets/subscription/mascot-avatar.png'

import avatarUser1 from '@/assets/avatar/avatar1.jpg'

export const IMG = {
  brand: {
    logo,
    mascotHero,
    mascotAlt,
    mascotZh,
  },
  mascot: {
    chromaKey,
  },
  auth: {
    keyPortalBg,
    keyPortalMascot,
  },
  pricing: {
    basicCubes: pricingBasicCubes,
    premiumAiCore: pricingPremiumAiCore,
    proMascot: pricingProMascot,
  },
  profile: {
    securityGuardian,
  },
  subscription: {
    goldCube,
    proMascotCubes,
    mascotAvatar: subscriptionMascotAvatar,
  },
  avatar: {
    user1: avatarUser1,
  },
} as const

export type ImgKey = keyof typeof IMG
