/**
 * 全局状态管理（成员 D：状态管理）
 * 职责：登录态（令牌 + 用户信息）的读写、路由状态的保存、状态变更通知。
 * 令牌与用户信息存放在 localStorage，页面刷新后可恢复登录态。
 */

const TOKEN_KEY = 'job_portal_token';
const USER_KEY = 'job_portal_user';

/** 应用状态（只读引用，修改请走 setState） */
export const state = {
    user: null,
    token: '',
    route: '/dashboard'
};

const listeners = new Set();

/** 订阅状态变化，返回取消订阅函数 */
export function subscribe(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
}

/** 合并状态并通知订阅者 */
export function setState(patch) {
    Object.assign(state, patch);
    listeners.forEach((listener) => {
        try {
            listener(state);
        } catch (e) {
            console.warn('状态订阅回调异常', e);
        }
    });
}

/** 从 localStorage 恢复登录态 */
export function loadAuth() {
    const token = localStorage.getItem(TOKEN_KEY) || '';
    let user = null;
    try {
        user = JSON.parse(localStorage.getItem(USER_KEY) || 'null');
    } catch (e) {
        user = null;
    }
    setState({ token, user });
    return { token, user };
}

/** 保存登录态 */
export function saveAuth(token, user) {
    localStorage.setItem(TOKEN_KEY, token || '');
    localStorage.setItem(USER_KEY, JSON.stringify(user || null));
    setState({ token: token || '', user: user || null });
}

/** 清理登录态 */
export function clearAuth() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setState({ token: '', user: null });
}

export function getToken() {
    return localStorage.getItem(TOKEN_KEY) || '';
}

export function getUser() {
    try {
        return JSON.parse(localStorage.getItem(USER_KEY) || 'null');
    } catch (e) {
        return null;
    }
}

export function isAdmin() {
    const user = getUser();
    return !!user && String(user.role || '').toUpperCase() === 'ADMIN';
}
