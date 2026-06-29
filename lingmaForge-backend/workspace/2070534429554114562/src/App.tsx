import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, NavLink, useLocation, Outlet } from 'react-router-dom';

/* ========== CSS 变量 (全局注入) ========== */
const ROOT_CSS_VARS = `
  :root {
    --color-primary: #4f46e5;
    --color-primary-hover: #4338ca;
    --color-bg: #f9fafb;
    --color-surface: #ffffff;
    --color-text: #1f2937;
    --color-text-secondary: #6b7280;
    --color-border: #e5e7eb;
    --color-success: #10b981;
    --color-warning: #f59e0b;
    --color-error: #ef4444;
    --shadow-sm: 0 1px 2px rgba(0,0,0,.05);
    --shadow-md: 0 4px 6px rgba(0,0,0,.07);
    --radius-md: 8px;
    --radius-lg: 12px;
    --max-width: 1200px;
    --transition: 200ms ease;
  }
`;

function injectCSS(css: string) {
  if (typeof document !== 'undefined') {
    const style = document.createElement('style');
    style.textContent = css;
    document.head.appendChild(style);
  }
}

/* ========== 通用占位页面 ========== */
interface PageShellProps {
  title: string;
  description?: string;
  children?: React.ReactNode;
}

const PageShell: React.FC<PageShellProps> = ({ title, description, children }) => (
  <div style={{ animation: 'fadeIn .3s ease' }}>
    <h1 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: '.5rem' }}>{title}</h1>
    {description && (
      <p style={{ color: 'var(--color-text-secondary)', marginBottom: '1.5rem', lineHeight: 1.6 }}>
        {description}
      </p>
    )}
    {children}
  </div>
);

/* ---- 各页面 ---- */
const HomePage: React.FC = () => (
  <PageShell title="🏠 首页" description="欢迎来到本应用。使用顶部导航浏览不同页面。">
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
        gap: '1rem',
        marginTop: '1rem',
      }}
    >
      {[
        { label: '快速开始', icon: '🚀' },
        { label: '组件示例', icon: '🧩' },
        { label: 'API 文档', icon: '📘' },
        { label: '关于我们', icon: 'ℹ️' },
      ].map((card) => (
        <div
          key={card.label}
          style={{
            background: 'var(--color-surface)',
            borderRadius: 'var(--radius-md)',
            padding: '1.5rem',
            boxShadow: 'var(--shadow-sm)',
            border: '1px solid var(--color-border)',
            textAlign: 'center',
            cursor: 'default',
            transition: 'var(--transition)',
          }}
        >
          <div style={{ fontSize: '2rem', marginBottom: '.5rem' }}>{card.icon}</div>
          <div style={{ fontWeight: 600 }}>{card.label}</div>
        </div>
      ))}
    </div>
  </PageShell>
);

const AboutPage: React.FC = () => (
  <PageShell title="ℹ️ 关于" description="React 18 + TypeScript + Vite 构建的现代化前端项目。">
    <ul style={{ lineHeight: 2, paddingLeft: '1.2rem', color: 'var(--color-text-secondary)' }}>
      <li>React 18 并发特性</li>
      <li>TypeScript 严格类型检查</li>
      <li>Vite 极速 HMR</li>
      <li>CSS 变量主题体系</li>
      <li>React Router v6 路由</li>
    </ul>
  </PageShell>
);

const NotFoundPage: React.FC = () => (
  <PageShell title="404" description="抱歉，您访问的页面不存在。">
    <NavLink
      to="/"
      style={{
        display: 'inline-block',
        marginTop: '1rem',
        color: 'var(--color-primary)',
        fontWeight: 600,
        textDecoration: 'none',
      }}
    >
      ← 返回首页
    </NavLink>
  </PageShell>
);

/* ========== Layout 组件 ========== */
const navLinkStyle = ({ isActive }: { isActive: boolean }): React.CSSProperties => ({
  textDecoration: 'none',
  padding: '.4rem .8rem',
  borderRadius: 'var(--radius-md)',
  fontWeight: 500,
  transition: 'var(--transition)',
  color: isActive ? 'var(--color-primary)' : 'var(--color-text-secondary)',
  background: isActive ? 'rgba(79,70,229,.08)' : 'transparent',
});

const Layout: React.FC = () => {
  const location = useLocation();

  React.useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [location.pathname]);

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--color-bg)',
        color: 'var(--color-text)',
      }}
    >
      {/* ---- 全局 CSS 变量注入 ---- */}
      <style>{`
        ${ROOT_CSS_VARS}
        *,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
        body{font-family:system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;-webkit-font-smoothing:antialiased}
        @keyframes fadeIn{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}
      `}</style>

      {/* ---- 顶部导航 ---- */}
      <header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 100,
          background: 'var(--color-surface)',
          borderBottom: '1px solid var(--color-border)',
          boxShadow: 'var(--shadow-sm)',
        }}
      >
        <nav
          style={{
            maxWidth: 'var(--max-width)',
            margin: '0 auto',
            padding: '0 1.5rem',
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <NavLink
            to="/"
            style={{ textDecoration: 'none', fontWeight: 700, fontSize: '1.125rem', color: 'var(--color-text)' }}
          >
            ⚡ Vite React App
          </NavLink>

          <div style={{ display: 'flex', gap: '.25rem' }}>
            {[
              { to: '/', label: '首页' },
              { to: '/about', label: '关于' },
            ].map((link) => (
              <NavLink key={link.to} to={link.to} end={link.to === '/'} style={navLinkStyle}>
                {link.label}
              </NavLink>
            ))}
          </div>
        </nav>
      </header>

      {/* ---- 主内容 ---- */}
      <main
        style={{
          flex: 1,
          maxWidth: 'var(--max-width)',
          width: '100%',
          margin: '2rem auto',
          padding: '0 1.5rem',
        }}
      >
        <Suspense
          fallback={
            <div style={{ textAlign: 'center', padding: '3rem 0', color: 'var(--color-text-secondary)' }}>
              ⏳ 加载中...
            </div>
          }
        >
          <Outlet />
        </Suspense>
      </main>

      {/* ---- 底部 ---- */}
      <footer
        style={{
          borderTop: '1px solid var(--color-border)',
          padding: '1.5rem',
          textAlign: 'center',
          fontSize: '.85rem',
          color: 'var(--color-text-secondary)',
        }}
      >
        <span>© {new Date().getFullYear()} Vite React App — 使用 React 18 + TypeScript 构建</span>
      </footer>
    </div>
  );
};

/* ========== App 根组件 ========== */
const App: React.FC = () => {
  React.useEffect(() => {
    injectCSS(ROOT_CSS_VARS);
  }, []);

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="about" element={<AboutPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
};

export default App;
