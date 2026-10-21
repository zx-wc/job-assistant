/**
 * 简历与岗位分析页（成员 D）
 * 对接：
 *   POST   /api/job/analyses            生成分析
 *   GET    /api/job/analyses            报告列表
 *   GET    /api/job/analyses/{id}       报告详情
 *   DELETE /api/job/analyses/{id}       删除报告
 *   GET    /api/job/resumes、/api/job/positions  下拉数据
 */

import { del, get, post } from '../api.js';
import {
    bulletList, degradedTag, dimensionBars, emptyState, esc, fmtTime,
    renderRich, scoreRing, toast, withLoading, confirmAction
} from '../ui.js';

const state = { page: 1, size: 10, total: 0, list: [], resumes: [], positions: [] };

export async function renderList(container) {
    state.page = 1;
    container.innerHTML = '<div class="loading">加载中…</div>';
    await Promise.all([loadList(), loadOptions()]);
    paintList(container);
}

async function loadList() {
    try {
        const data = await get('/api/job/analyses', { page: state.page, size: state.size });
        state.list = (data.page && data.page.list) || [];
        state.total = (data.page && data.page.total) || 0;
    } catch (error) {
        toast(error.message, 'error');
    }
}

async function loadOptions() {
    try {
        const [resumes, positions] = await Promise.all([
            get('/api/job/resumes', { page: 1, size: 50 }),
            get('/api/job/positions', { page: 1, size: 50 })
        ]);
        state.resumes = (resumes.page && resumes.page.list) || [];
        state.positions = (positions.page && positions.page.list) || [];
    } catch (error) {
        state.resumes = [];
        state.positions = [];
    }
}

function paintList(container) {
    container.innerHTML = `
        <div class="card">
            <div class="card-title">
                <h3>生成新的匹配分析</h3>
                <span class="hint">AI 会输出匹配度、优势/短板、改进建议与面试重点</span>
            </div>
            ${state.resumes.length === 0 || state.positions.length === 0
                ? `<div class="tip tip-warn">需要先准备至少一份简历和一个岗位才能生成分析。
                   <a href="#/resumes">去添加简历</a> · <a href="#/positions">去添加岗位</a></div>`
                : `<div class="field-row">
                    <div class="field">
                        <label>选择简历</label>
                        <select id="sel-resume">
                            ${state.resumes.map((item) => `<option value="${item.id}">${esc(item.title)}（${esc(item.targetPosition || '未填岗位')}）</option>`).join('')}
                        </select>
                    </div>
                    <div class="field">
                        <label>选择岗位</label>
                        <select id="sel-position">
                            ${state.positions.map((item) => `<option value="${item.id}">${esc(item.position)}${item.company ? ' · ' + esc(item.company) : ''}</option>`).join('')}
                        </select>
                    </div>
                    <div class="field" style="display:flex;align-items:flex-end">
                        <button class="btn" id="btn-analyze" style="width:100%">开始分析</button>
                    </div>
                   </div>`}
        </div>

        <div class="card">
            <div class="card-title">
                <h3>历史分析报告</h3>
                <span class="hint">共 ${state.total} 份</span>
            </div>
            ${state.list.length === 0
                ? emptyState('还没有分析报告')
                : `<table class="table">
                    <thead><tr><th>简历</th><th>岗位</th><th>匹配度</th><th>来源</th><th>时间</th><th>操作</th></tr></thead>
                    <tbody>
                    ${state.list.map((item) => `
                        <tr>
                            <td>${esc(item.resumeTitle || '-')}</td>
                            <td>${esc(item.positionTitle || '-')}<div class="hint">${esc(item.company || '')}</div></td>
                            <td><strong style="color:${item.matchScore >= 60 ? '#4f46e5' : '#d97706'}">${item.matchScore}</strong> 分</td>
                            <td>${degradedTag(item.degraded)}</td>
                            <td>${esc(fmtTime(item.createTime))}</td>
                            <td class="actions">
                                <button class="btn btn-ghost btn-sm" data-open="${item.id}">查看</button>
                                <button class="btn btn-ghost btn-sm" data-del="${item.id}">删除</button>
                                <button class="btn btn-ghost btn-sm" data-interview="${item.positionId}">去面试</button>
                            </td>
                        </tr>`).join('')}
                    </tbody></table>`}
            <div class="btn-row mt-8" style="justify-content:space-between">
                <button class="btn btn-ghost btn-sm" id="btn-prev" ${state.page <= 1 ? 'disabled' : ''}>上一页</button>
                <span class="hint">第 ${state.page} 页</span>
                <button class="btn btn-ghost btn-sm" id="btn-next"
                    ${state.page * state.size >= state.total ? 'disabled' : ''}>下一页</button>
            </div>
        </div>`;

    container.querySelectorAll('[data-open]').forEach((btn) => {
        btn.addEventListener('click', () => {
            location.hash = '#/analyses/' + btn.dataset.open;
        });
    });
    container.querySelectorAll('[data-del]').forEach((btn) => {
        btn.addEventListener('click', async () => {
            const ok = await confirmAction('确认删除该分析报告？', '删除');
            if (!ok) {
                return;
            }
            try {
                await del('/api/job/analyses/' + btn.dataset.del);
                toast('已删除', 'ok');
                await loadList();
                paintList(container);
            } catch (error) {
                toast(error.message, 'error');
            }
        });
    });
    container.querySelectorAll('[data-interview]').forEach((btn) => {
        btn.addEventListener('click', () => {
            location.hash = '#/interviews/new?positionId=' + btn.dataset.interview;
        });
    });
    container.querySelector('#btn-analyze')?.addEventListener('click', (event) => analyze(container, event.target));
    container.querySelector('#btn-prev')?.addEventListener('click', async () => {
        if (state.page > 1) {
            state.page -= 1;
            await loadList();
            paintList(container);
        }
    });
    container.querySelector('#btn-next')?.addEventListener('click', async () => {
        if (state.page * state.size < state.total) {
            state.page += 1;
            await loadList();
            paintList(container);
        }
    });
}

async function analyze(container, button) {
    const resumeId = container.querySelector('#sel-resume')?.value;
    const positionId = container.querySelector('#sel-position')?.value;
    if (!resumeId || !positionId) {
        toast('请选择简历与岗位', 'error');
        return;
    }
    await withLoading(button, '分析中，请稍候…', async () => {
        try {
            const data = await post('/api/job/analyses', {
                resumeId: Number(resumeId),
                positionId: Number(positionId)
            });
            const vo = data.analysis;
            toast(vo.degraded ? 'AI 暂不可用，已生成本地规则分析' : '分析完成', vo.degraded ? 'info' : 'ok');
            location.hash = '#/analyses/' + vo.id;
        } catch (error) {
            toast(error.message, 'error');
        }
    });
}

export async function renderDetail(container, params) {
    container.innerHTML = '<div class="loading">加载中…</div>';
    let vo;
    try {
        vo = (await get('/api/job/analyses/' + params.id)).analysis;
    } catch (error) {
        container.innerHTML = `<div class="card">${emptyState('报告不存在或已删除')}</div>`;
        return;
    }
    container.innerHTML = `
        <div class="card">
            <div class="card-title">
                <h3>${esc(vo.resumeTitle || '简历')} → ${esc(vo.positionTitle || '岗位')}</h3>
                <div class="btn-row">
                    <button class="btn btn-ghost btn-sm" id="btn-back">返回列表</button>
                    <button class="btn btn-sm" id="btn-interview">按此岗位开始模拟面试</button>
                </div>
            </div>
            <div class="score-line">
                ${scoreRing(vo.matchScore, '匹配度')}
                <div style="flex:1">
                    <div class="hint">公司：${esc(vo.company || '未填写')} ·
                        生成时间：${esc(fmtTime(vo.createTime))} · 来源：${degradedTag(vo.degraded)}</div>
                    <div class="rich mt-8">${renderRich(vo.summary)}</div>
                </div>
            </div>
        </div>

        <div class="grid grid-2">
            <div class="card">
                <div class="card-title"><h3>维度评分</h3></div>
                ${dimensionBars(vo.dimensionScores)}
            </div>
            <div class="card">
                <div class="card-title"><h3>岗位要求但简历未体现</h3></div>
                ${bulletList(vo.missingSkills, '岗位关键词已基本覆盖')}
            </div>
            <div class="card">
                <div class="card-title"><h3>优势</h3></div>
                ${bulletList(vo.strengths)}
            </div>
            <div class="card">
                <div class="card-title"><h3>短板</h3></div>
                ${bulletList(vo.weaknesses)}
            </div>
            <div class="card">
                <div class="card-title"><h3>改进建议</h3></div>
                ${bulletList(vo.suggestions)}
            </div>
            <div class="card">
                <div class="card-title"><h3>面试准备重点</h3></div>
                ${bulletList(vo.interviewFocus)}
            </div>
        </div>`;

    container.querySelector('#btn-back')?.addEventListener('click', () => {
        location.hash = '#/analyses';
    });
    container.querySelector('#btn-interview')?.addEventListener('click', () => {
        location.hash = '#/interviews/new?positionId=' + vo.positionId;
    });
}
