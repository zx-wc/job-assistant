/**
 * AI 网关管理页（成员 D，仅管理员可见）
 * 对接：
 *   GET    /api/job/admin/ai/config            网关配置（密钥脱敏）
 *   GET    /api/job/admin/ai/stats             今日 / 近 7 天统计
 *   GET    /api/job/admin/ai/scenes            场景与模板内容
 *   PUT    /api/job/admin/ai/templates/{scene} 保存模板
 *   DELETE /api/job/admin/ai/templates/{scene} 还原内置模板
 *   GET    /api/job/admin/ai/logs              调用日志
 */

import { del, get, put } from '../api.js';
import { esc, fmtTime, toast, withLoading, confirmAction } from '../ui.js';

const state = {
    config: null,
    stats: null,
    scenes: [],
    logs: [],
    logPage: 1,
    logTotal: 0,
    editing: null
};

export async function render(container) {
    container.innerHTML = '<div class="loading">加载中…</div>';
    try {
        const [config, stats, scenes, logs] = await Promise.all([
            get('/api/job/admin/ai/config'),
            get('/api/job/admin/ai/stats'),
            get('/api/job/admin/ai/scenes'),
            get('/api/job/admin/ai/logs', { page: 1, size: 10 })
        ]);
        state.config = config.config || {};
        state.stats = stats.stats || {};
        state.scenes = scenes.scenes || [];
        state.logs = (logs.page && logs.page.list) || [];
        state.logTotal = (logs.page && logs.page.total) || 0;
    } catch (error) {
        toast(error.message, 'error');
    }
    paint(container);
}

function paint(container) {
    const config = state.config || {};
    const stats = state.stats || {};
    container.innerHTML = `
        <div class="grid grid-3">
            <div class="stat">
                <div class="label">今日调用</div>
                <div class="value">${stats.todayTotal || 0}</div>
                <div class="extra">成功 ${stats.todaySuccess || 0} · 失败 ${stats.todayFail || 0}</div>
            </div>
            <div class="stat">
                <div class="label">成功率</div>
                <div class="value">${stats.successRate || 0}%</div>
                <div class="extra">缓存命中 ${stats.todayCached || 0} · 降级 ${stats.todayDegraded || 0}</div>
            </div>
            <div class="stat">
                <div class="label">平均耗时</div>
                <div class="value">${stats.avgDurationMs || 0}<span style="font-size:13px"> ms</span></div>
                <div class="extra">今日 token：${stats.todayTokens || 0}</div>
            </div>
            <div class="stat">
                <div class="label">网关状态</div>
                <div class="value" style="font-size:20px;color:${config.available ? '#16a34a' : '#dc2626'}">
                    ${config.available ? '可用' : '熔断/关闭'}</div>
                <div class="extra">主模型 ${esc(config.model || '-')}</div>
            </div>
        </div>

        <div class="grid grid-2">
            <div class="card">
                <div class="card-title"><h3>网关运行参数</h3></div>
                <table class="table">
                    <tbody>
                    <tr><td>开关（job.ai.enabled）</td><td>${config.enabled ? '启用' : '关闭（全部本地降级）'}</td></tr>
                    <tr><td>主模型 / 备用模型</td><td>${esc(config.model || '-')} / ${esc((config.fallbackModels || []).join('、') || '-')}</td></tr>
                    <tr><td>单次超时</td><td>${config.timeoutMs || 0} ms</td></tr>
                    <tr><td>重试次数</td><td>${config.maxRetries || 0} 次</td></tr>
                    <tr><td>限流</td><td>${config.rateLimitPerMinute || 0} 次 / ${config.rateLimitWindowSeconds || 0} 秒</td></tr>
                    <tr><td>结果缓存</td><td>${config.cacheTtlSeconds || 0} 秒</td></tr>
                    <tr><td>熔断阈值</td><td>连续失败 ${config.circuitFailureThreshold || 0} 次，熔断 ${config.circuitOpenMs || 0} ms</td></tr>
                    <tr><td>API 密钥</td><td>${config.apiKeyConfigured
                        ? `<code>${esc(config.apiKeyMasked)}</code> <span class="hint">（仅服务端保存，前端只见脱敏值）</span>`
                        : '<span class="tag tag-danger">未配置</span>'}</td></tr>
                    </tbody>
                </table>
            </div>

            <div class="card">
                <div class="card-title"><h3>今日调用分布（按场景）</h3></div>
                ${(stats.byScene || []).length === 0
                    ? '<div class="hint">今日暂无调用记录</div>'
                    : `<table class="table">
                        <thead><tr><th>场景</th><th>调用</th><th>成功</th><th>平均耗时</th></tr></thead>
                        <tbody>
                        ${(stats.byScene || []).map((row) => `
                            <tr><td>${esc(row.scene)}</td><td>${row.total || 0}</td>
                                <td>${row.success_count || 0}</td>
                                <td>${row.avg_duration_ms || 0} ms</td></tr>`).join('')}
                        </tbody></table>`}
                <div class="card-title mt-16"><h3>近 7 天趋势</h3></div>
                ${(stats.daily || []).length === 0
                    ? '<div class="hint">暂无数据</div>'
                    : `<table class="table">
                        <thead><tr><th>日期</th><th>调用</th><th>成功</th><th>降级</th></tr></thead>
                        <tbody>
                        ${(stats.daily || []).map((row) => `
                            <tr><td>${esc(row.day)}</td><td>${row.total || 0}</td>
                                <td>${row.success_count || 0}</td><td>${row.degraded_count || 0}</td></tr>`).join('')}
                        </tbody></table>`}
            </div>
        </div>

        <div class="card">
            <div class="card-title">
                <h3>提示词模板</h3>
                <span class="hint">数据库模板优先于内置模板，保存后立即生效</span>
            </div>
            <table class="table">
                <thead><tr><th>场景</th><th>名称</th><th>来源</th><th>占位符</th><th>操作</th></tr></thead>
                <tbody>
                ${state.scenes.map((scene) => `
                    <tr>
                        <td><code>${esc(scene.code)}</code></td>
                        <td>${esc(scene.title)}</td>
                        <td>${scene.source === 'DB' ? '<span class="tag tag-ok">数据库</span>' : '<span class="tag tag-muted">内置</span>'}</td>
                        <td class="hint">${esc((scene.placeholders || []).join('、'))}</td>
                        <td class="actions">
                            <button class="btn btn-ghost btn-sm" data-edit-scene="${esc(scene.code)}">编辑</button>
                            ${scene.dbConfigured
                                ? `<button class="btn btn-ghost btn-sm" data-reset-scene="${esc(scene.code)}">还原内置</button>` : ''}
                        </td>
                    </tr>`).join('')}
                </tbody>
            </table>
            ${state.editing ? renderEditor() : ''}
        </div>

        <div class="card">
            <div class="card-title">
                <h3>AI 调用日志</h3>
                <span class="hint">共 ${state.logTotal} 条</span>
            </div>
            ${state.logs.length === 0
                ? '<div class="hint">暂无调用日志</div>'
                : `<table class="table">
                    <thead><tr><th>时间</th><th>场景</th><th>模型</th><th>结果</th><th>耗时</th><th>token</th><th>说明</th></tr></thead>
                    <tbody>
                    ${state.logs.map((log) => `
                        <tr>
                            <td>${esc(fmtTime(log.createTime))}</td>
                            <td><code>${esc(log.scene)}</code></td>
                            <td>${esc(log.model || '-')}</td>
                            <td>
                                ${log.success === 1 ? '<span class="tag tag-ok">成功</span>' : '<span class="tag tag-danger">失败</span>'}
                                ${log.cached === 1 ? '<span class="tag tag-muted">缓存</span>' : ''}
                                ${log.degraded === 1 ? '<span class="tag tag-warn">降级</span>' : ''}
                            </td>
                            <td>${log.durationMs || 0} ms</td>
                            <td>${log.totalTokens || 0}</td>
                            <td class="hint">${esc(log.errorMsg || '')}</td>
                        </tr>`).join('')}
                    </tbody></table>`}
            <div class="btn-row mt-8" style="justify-content:space-between">
                <button class="btn btn-ghost btn-sm" id="log-prev" ${state.logPage <= 1 ? 'disabled' : ''}>上一页</button>
                <span class="hint">第 ${state.logPage} 页</span>
                <button class="btn btn-ghost btn-sm" id="log-next"
                    ${state.logPage * 10 >= state.logTotal ? 'disabled' : ''}>下一页</button>
            </div>
        </div>`;

    container.querySelectorAll('[data-edit-scene]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const scene = state.scenes.find((item) => item.code === btn.dataset.editScene);
            state.editing = { code: scene.code, name: scene.title, content: scene.content || '' };
            paint(container);
        });
    });
    container.querySelectorAll('[data-reset-scene]').forEach((btn) => {
        btn.addEventListener('click', async () => {
            const ok = await confirmAction('还原后将删除数据库中的模板，使用代码内置模板。确认？', '还原');
            if (!ok) {
                return;
            }
            try {
                await del('/api/job/admin/ai/templates/' + btn.dataset.resetScene);
                toast('已还原内置模板', 'ok');
                await render(container);
            } catch (error) {
                toast(error.message, 'error');
            }
        });
    });
    container.querySelector('#btn-save-template')?.addEventListener('click', (event) => saveTemplate(container, event.target));
    container.querySelector('#btn-cancel-template')?.addEventListener('click', () => {
        state.editing = null;
        paint(container);
    });
    container.querySelector('#log-prev')?.addEventListener('click', () => loadLogs(container, state.logPage - 1));
    container.querySelector('#log-next')?.addEventListener('click', () => loadLogs(container, state.logPage + 1));
}

function renderEditor() {
    return `
        <div class="mt-16">
            <div class="card-title">
                <h3>编辑模板：<code>${esc(state.editing.code)}</code></h3>
                <span class="hint">使用 {变量名} 占位，保存后立即生效</span>
            </div>
            <div class="field">
                <label>模板名称</label>
                <input id="tpl-name" value="${esc(state.editing.name)}">
            </div>
            <div class="field">
                <label>模板内容</label>
                <textarea id="tpl-content" style="min-height:260px">${esc(state.editing.content)}</textarea>
            </div>
            <div class="btn-row">
                <button class="btn" id="btn-save-template">保存并生效</button>
                <button class="btn btn-ghost" id="btn-cancel-template">取消</button>
            </div>
        </div>`;
}

async function saveTemplate(container, button) {
    const name = container.querySelector('#tpl-name').value.trim();
    const content = container.querySelector('#tpl-content').value;
    if (!content.trim()) {
        toast('模板内容不能为空', 'error');
        return;
    }
    await withLoading(button, '保存中…', async () => {
        try {
            await put('/api/job/admin/ai/templates/' + state.editing.code, {
                name,
                content,
                enabled: true
            });
            toast('模板已保存，下一次调用即生效', 'ok');
            state.editing = null;
            await render(container);
        } catch (error) {
            toast(error.message, 'error');
        }
    });
}

async function loadLogs(container, page) {
    if (page < 1) {
        return;
    }
    try {
        const data = await get('/api/job/admin/ai/logs', { page, size: 10 });
        state.logs = (data.page && data.page.list) || [];
        state.logPage = page;
        state.logTotal = (data.page && data.page.total) || 0;
        paint(container);
    } catch (error) {
        toast(error.message, 'error');
    }
}
