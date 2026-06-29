import { type GlobalThemeOverrides, darkTheme } from 'naive-ui'

/**
 * Naive UI 主题覆盖 — 将项目品牌设计令牌映射到 Naive UI 组件变量
 *
 * 设计令牌来源：src/styles/global.css :root
 *   --cyan: #00e5ff  (主强调色)
 *   --mint: #14d9bd  (次要强调色)
 *   --ink:  #111821  (主文字色)
 *   --muted: #697686 (次要文字色)
 *   --line: #dce6ef  (边框色)
 *   --radius: 12px   (圆角)
 */
export const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#00e5ff',
    primaryColorHover: '#14d9bd',
    primaryColorPressed: '#009be5',
    primaryColorSuppl: '#14d9bd',
    infoColor: '#1689ff',
    successColor: '#19c990',
    warningColor: '#ff7a00',
    errorColor: '#ff5370',
    textColorBase: '#111821',
    textColor1: '#111821',
    textColor2: '#1d2732',
    textColor3: '#697686',
    borderColor: '#dce6ef',
    borderRadius: '12px',
    fontSize: '14px',
    fontSizeSmall: '12px',
    fontSizeMedium: '14px',
    fontSizeLarge: '16px',
    fontFamily:
      '"Microsoft YaHei UI", "PingFang SC", "Noto Sans CJK SC", sans-serif',
    borderRadiusSmall: '6px',
  },
  Tree: {
    nodeHeight: '28px',
    nodeWrapperPadding: '0 6px',
    nodeBorderRadius: '4px',
    fontSize: '13px',
    nodeTextColor: '#111821',
    nodeTextColorDisabled: '#cbd9e5',
    arrowColor: '#697686',
    nodeColorHover: '#f5f9fc',
    nodeColorActive: '#f0f7fc',
  },
  Collapse: {
    titleFontSize: '13px',
    titleTextColor: '#111821',
    titleTextColorDisabled: '#cbd9e5',
    arrowColor: '#697686',
    headerColorHover: '#f5f9fc',
    contentTextColor: '#1d2732',
    dividerColor: '#eef2f6',
  },
}
