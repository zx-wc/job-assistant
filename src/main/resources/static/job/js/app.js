/**
 * 应用入口（成员 D：路由与页面挂载）
 *
 * 职责：
 *  1. 恢复登录态、渲染顶部用户信息与退出登录；
 *  2. 注册路由表并做登录守卫 / 管理员守卫；
 *  3. 把对应页面模块渲染到 #view，统一处理加载态与异常。
 */

import { get, post } from './api.js';
import { clearAuth, getUser, isAdmin, loadAuth, saveAuth, state, subscribe } from './store.js';
import { navigate, startRouter } from './router.js';
import { esc, toast } from './ui.js';

import * as authView from './views/auth.js';
import * as dashboardView from './views/dashboard.js';
import * as resumesView from './views/resumes.js';
import * as positionsView from './views/positions.js';
import * as analysesView from './views/analyses.js';
import * as interviewsView from './views/interviews.js';
import * as adminView from './views/admin.js';

const routes = [
    { path: '/login', view: authView, publicPage: true, title: '登录' },
    { path: '/dashboard', view: dashboardView, title: '求职概览' },
    { path: '/resumes', view: resumesView, title: '简历管理' },
    { path: '/positions', view: positionsView, title: '岗位管理' },
    { path: '/analyses', view: { render: analysesView.renderList }, title: '简历分析' },
    { path: '/analyses/:id', view: { render: analysesView.renderDetail }, title: '分析报告' },
    { path: '/interviews', view: { render: interviewsView.renderList }, title: '模拟面试' },
    { path: '/interviews/new', view: { render: interviewsView.renderCreate }, title: '开始面试' },
    { path: '/interviews/:id', view: { render: interviewsView.renderRoom }, title: '面试进行中' },
    { path: '/interviews/:id/report', view: { render: interviewsView.renderReport }, title: '面试反馈报告' },
    { path: '/admin', view: adminView, title: 'AI 网关管理', adminOnly: true }
];

const navRoutes = {
    '/dashboard': '/dashboard',
    '/resumes': '/resumes',
    '/positions': '/positions',
    '/analyses': '/analyses',
    '/interviews': '/interviews',
    '/admin': '/admin'
};

let activeNav = '/dashboard';

function hasToken() {
    return !!state.token;
}

function renderUserArea() {
    const badge = document.getElementById('user-badge');
    const logoutBtn = document.getElementById('btn-logout');
    const adminLink = document.getElementById('nav-admin');
    const user = getUser();
    if (user && hasToken()) {
        badge.hidden = false;
        badge.textContent = (user.nickname || user.username) + ' · ' + (user.role || 'USER');
        logoutBtn.hidden = false;
    } else {
        badge.hidden = true;
        logoutBtn.hidden = true;
    }
    adminLink.hidden = !(hasToken() && isAdmin());
}

function highlightNav(path) {
    Object.values(navRoutes).forEach((route) => {
        const link = document.querySelector(`#nav a[data-route="${route}"]`);
        if (link) {
            link.classList.toggle('active', route === activeNav);
        }
    });
}

function resolveNav(path) {
    if (path.startsWith('/analyses')) {
        return '/analyses';
    }
    if (path.startsWith('/interviews')) {
        return '/interviews';
    }
    return path;
}

async function handleRoute(matched, locationInfo) {
    const viewEl = document.getElementById('view');
    const body = document.body;

    if (!matched) {
        activeNav = '';
        highlightNav('');
        document.getElementById('page-title').textContent = '页面不存在';
        body.classList.remove('auth-page');
        viewEl.innerHTML = `<div class="card"><div class="empty">页面不存在，<a href="#/dashboard">回到求职概览</a></div></div>`;
        return;
    }

    const { route, params } = matched;

    // 登录页与登录态互斥
    if (route.publicPage) {
        body.classList.add('auth-page');
        document.getElementById('page-title').textContent = '';
        if (hasToken()) {
            navigate('/dashboard');
            return;
        }
        await mount(viewEl, route, { ...params, ...locationInfo.query });
        return;
    }

    body.classList.remove('auth-page');

    if (!hasToken()) {
        navigate('/login');
        return;
    }

    if (route.adminOnly && !isAdmin()) {
        toast('该页面仅管理员可访问', 'error');
        navigate('/dashboard');
        return;
    }

    activeNav = resolveNav(locationInfo.path);
    highlightNav(activeNav);
    document.getElementById('page-title').textContent = route.title || '智能求职助手';
    await mount(viewEl, route, { ...params, ...locationInfo.query });
}

async function mount(container, route, params) {
    container.innerHTML = '<div class="loading">加载中…</div>';
    try {
        await route.view.render(container, params);
    } catch (error) {
        console.error(error);
        container.innerHTML = `
            <div class="card">
                <div class="card-title"><h3>页面加载失败</h3></div>
                <p class="hint">${esc(error.message || '未知错误')}</p>
                <div class="btn-row">
                    <button class="btn btn-sm" id="btn-retry">重试</button>
                    <a class="btn btn-ghost btn-sm" href="#/dashboard">回到概览</a>
                </div>
            </div>`;
        container.querySelector('#btn-retry')?.addEventListener('click', () => {
            mount(container, route, params);
        });
    }
}

function bindLogout() {
    document.getElementById('btn-logout').addEventListener('click', async () => {
        try {
            await post('/api/job/auth/logout');
        } catch (error) {
            // 令牌可能已过期，忽略失败继续本地登出
        }
        clearAuth();
        toast('已退出登录', 'ok');
        navigate('/login');
    });
}

function init() {
    loadAuth();
    // 以服务端为准刷新一次用户信息（令牌失效时会自动跳转登录页）
    if (hasToken()) {
        get('/api/job/auth/me')
            .then((data) => {
                if (data.user) {
                    const merged = { ...data.user, nickname: (getUser() || {}).nickname || data.user.nickname };
                    saveAuth(state.token, merged);
                }
            })
            .catch(() => {
                // 已由 api.js 统一处理跳转
            });
    }
    renderUserArea();
    subscribe(renderUserArea);
    bindLogout();
    startRouter(routes, handleRoute);
}

init();
