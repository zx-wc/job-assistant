/**
 * 统一接口调用层（成员 D：与后端联调）
 *
 * 约定（与后端 R 结构一致）：
 *   { code: 200, msg: "成功", dataMap: { ... } }
 * - code === 401：登录失效，清理登录态并跳转登录页
 * - 其他非 200：抛出 ApiError，由页面统一 toast 提示
 */

import { clearAuth, getToken } from './store.js';

export class ApiError extends Error {
    constructor(code, message) {
        super(message);
        this.code = code;
    }
}

function buildQuery(query) {
    if (!query) {
        return '';
    }
    const params = new URLSearchParams();
    Object.entries(query).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
            params.append(key, value);
        }
    });
    const text = params.toString();
    return text ? '?' + text : '';
}

/**
 * 通用请求方法
 * @param {string} path 接口路径
 * @param {{method?:string, body?:any, query?:object, formData?:FormData}} options
 * @returns {Promise<object>} dataMap
 */
export async function api(path, options = {}) {
    const { method = 'GET', body, query, formData } = options;
    const headers = {};
    const token = getToken();
    if (token) {
        headers['Authorization'] = 'Bearer ' + token;
    }
    let payload;
    if (formData) {
        payload = formData;
    } else if (body !== undefined && body !== null) {
        headers['Content-Type'] = 'application/json';
        payload = JSON.stringify(body);
    }
    let response;
    try {
        response = await fetch(path + buildQuery(query), { method, headers, body: payload });
    } catch (e) {
        throw new ApiError(-1, '网络异常，请检查服务是否启动：' + e.message);
    }
    if (!response.ok) {
        throw new ApiError(response.status, '请求失败（HTTP ' + response.status + '）');
    }
    let json;
    try {
        json = await response.json();
    } catch (e) {
        throw new ApiError(-2, '响应不是合法 JSON');
    }
    if (json.code === 401) {
        clearAuth();
        if (!location.hash.startsWith('#/login')) {
            location.hash = '#/login';
        }
        throw new ApiError(401, json.msg || '登录已过期，请重新登录');
    }
    if (json.code !== 200) {
        throw new ApiError(json.code, json.msg || '请求失败');
    }
    return json.dataMap || {};
}

export const get = (path, query) => api(path, { query });
export const post = (path, body) => api(path, { method: 'POST', body });
export const put = (path, body) => api(path, { method: 'PUT', body });
export const del = (path) => api(path, { method: 'DELETE' });
export const upload = (path, formData) => api(path, { method: 'POST', formData });
