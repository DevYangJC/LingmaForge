import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import './App.css';

const HomePage = lazy(() => import('./pages/HomePage'));
const AddTodoPage = lazy(() => import('./pages/AddTodoPage'));
const EditTodoPage = lazy(() => import('./pages/EditTodoPage'));

const Loading: React.FC = () => (
  <div className="loading-container">
    <div className="loading-spinner" />
    <span>加载中...</span>
  </div>
);

const NotFoundPage: React.FC = () => (
  <div className="not-found">
    <h1>404</h1>
    <p>页面未找到</p>
    <Link to="/" className="back-link">返回首页</Link>
  </div>
);

const App: React.FC = () => {
  return (
    <BrowserRouter>
      <div className="app">
        <header className="app-header">
          <Link to="/" className="app-logo">
            <h1>📋 Todo 应用</h1>
          </Link>
          <nav className="app-nav">
            <Link to="/" className="nav-link">任务列表</Link>
            <Link to="/add" className="nav-link nav-link--primary">新增任务</Link>
          </nav>
        </header>
        <main className="app-main">
          <Suspense fallback={<Loading />}>
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/add" element={<AddTodoPage />} />
              <Route path="/edit/:id" element={<EditTodoPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </Suspense>
        </main>
        <footer className="app-footer">
          <p>© 2025 Todo 应用 · 简洁高效的任务管理</p>
        </footer>
      </div>
    </BrowserRouter>
  );
};

export default App;
