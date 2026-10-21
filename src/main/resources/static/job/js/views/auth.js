/**
 * 登录 / 注册页（成员 D）
 * 对接：POST /api/job/auth/login、POST /api/job/auth/register
 */

import { post } from '../api.js';
import { clearAuth, saveAuth } from '../store.js';
import { toast, withLoading } from '../ui.js';
import { navigate } from '../router.js';

let mode = 'login';

export async function render(container) {
    clearAuth();
    container.innerHTML = `
        <div class="auth-wrap">
            <div class="auth-card">
                <h1>智能求职辅助系统</h1>
                <p class="sub">简历管理 · 岗位分析 · 文字模拟面试 · 反馈报告</p>
                <div class="tabs">
                    <button type="button" data-mode="login">登录</button>
                    <button type="button" data-mode="register">注册</button>
                </div>
                <form id="auth-form">
                    <div class="field">
                        <label>用户名</label>
                        <input data-field="username" autocomplete="username" placeholder="3-20 位字母或数字">
                    </div>
                    <div class="field" data-only="register">
                        <label>昵称（可选）</label>
                        <input data-field="nickname" placeholder="展示用昵称">
                    </div>
                    <div class="field">
                        <label>密码</label>
                        <input data-field="password" type="password" autocomplete="current-password" placeholder="至少 6 位">
                    </div>
                    <div class="field" data-only="register">
                        <label>确认密码</label>
                        <input data-field="confirm" type="password" placeholder="再次输入密码">
                    </div>
                    <button class="btn" type="submit" id="auth-submit" style="width:100%">登 录</button>
                </form>
                <div class="auth-foot">
                    演示账号：jobuser / 123456（普通用户）；admin / 123456（管理员，可管理提示词模板与 AI 统计）<br>
                    提示：AI 不可用时会自动降级为本地规则分析，功能仍可完整体验。
                </div>
            </div>
        </div>`;

    const form = container.querySelector('#auth-form');
    const submit = container.querySelector('#auth-submit');

    const switchMode = (next) => {
        mode = next;
        container.querySelectorAll('.tabs button').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.mode === mode);
        });
        container.querySelectorAll('[data-only="register"]').forEach((el) => {
            el.hidden = mode !== 'register';
        });
        submit.textContent = mode === 'login' ? '登 录' : '注 册';
    };
    container.querySelectorAll('.tabs button').forEach((btn) => {
        btn.addEventListener('click', () => switchMode(btn.dataset.mode));
    });
    switchMode('login');

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const username = form.querySelector('[data-field="username"]').value.trim();
        const password = form.querySelector('[data-field="password"]').value;
        const nickname = form.querySelector('[data-field="nickname"]').value.trim();
        const confirm = form.querySelector('[data-field="confirm"]').value;
        if (!username || !password) {
            toast('请输入用户名和密码', 'error');
            return;
        }
        if (mode === 'register' && password !== confirm) {
            toast('两次输入的密码不一致', 'error');
            return;
        }
        await withLoading(submit, mode === 'login' ? '登录中…' : '注册中…', async () => {
            try {
                const path = mode === 'login' ? '/api/job/auth/login' : '/api/job/auth/register';
                const body = mode === 'login'
                    ? { username, password }
                    : { username, password, nickname: nickname || username };
                const data = await post(path, body);
                saveAuth(data.token, data.user);
                toast('欢迎回来，' + (data.user.nickname || data.user.username), 'ok');
                navigate('/dashboard');
            } catch (error) {
                toast(error.message || '操作失败', 'error');
            }
        });
    });

    // 供其他页面跳回登录时显示提示
    container.querySelector('#auth-form')?.setAttribute('data-ready', '1');
}

/** 页面标题/侧边栏在登录页隐藏，由 app.js 统一处理 */
export const authPage = true;
