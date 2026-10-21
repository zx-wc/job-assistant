/**
 * 岗位管理页（成员 D）
 * 对接：GET/POST/PUT/DELETE /api/job/positions
 */

import { del, get, post, put } from '../api.js';
import { esc, fmtTime, toast, confirmAction, withLoading } from '../ui.js';

const state = {
    page: 1,
    size: 10,
    keyword: '',
    total: 0,
    list: [],
    draft: null
};

export async function render(container) {
    state.page = 1;
    state.keyword = '';
    state.draft = emptyDraft();
    container.innerHTML = '<div class="loading">加载中…</div>';
    await loadList();
    paint(container);
}

function emptyDraft() {
    return {
        id: null,
        company: '',
        position: '',
        city: '',
        salaryRange: '',
        jobDesc: '',
        requirements: '',
        sourceUrl: '',
        status: 1
    };
}

async function loadList() {
    try {
        const data = await get('/api/job/positions', {
            keyword: state.keyword,
            page: state.page,
            size: state.size
        });
        const page = data.page || {};
        state.list = page.list || [];
        state.total = page.total || 0;
    } catch (error) {
        toast(error.message, 'error');
        state.list = [];
        state.total = 0;
    }
}

function paint(container) {
    container.innerHTML = `
        <div class="grid grid-2">
            <div class="card mb-0">
                <div class="card-title">
                    <h3>目标岗位</h3>
                    <button class="btn btn-sm" id="btn-new">新建</button>
                </div>
                <div class="btn-row" style="margin-bottom:10px">
                    <input id="keyword" placeholder="搜索岗位 / 公司 / 城市" value="${esc(state.keyword)}"
                        style="flex:1;padding:8px 10px;border:1px solid var(--border);border-radius:8px">
                    <button class="btn btn-ghost btn-sm" id="btn-search">搜索</button>
                </div>
                ${state.list.length === 0
                    ? '<div class="empty">还没有岗位，先录入一个目标岗位</div>'
                    : `<table class="table">
                        <thead><tr><th>岗位</th><th>公司 / 城市</th><th>薪资</th><th>操作</th></tr></thead>
                        <tbody>
                        ${state.list.map((item) => `
                            <tr>
                                <td><strong>${esc(item.position)}</strong>
                                    <div class="hint">更新于 ${esc(fmtTime(item.updateTime))}</div></td>
                                <td>${esc(item.company || '-')}<div class="hint">${esc(item.city || '-')}</div></td>
                                <td>${esc(item.salaryRange || '-')}</td>
                                <td class="actions">
                                    <button class="btn btn-ghost btn-sm" data-edit-id="${item.id}">编辑</button>
                                    <button class="btn btn-ghost btn-sm" data-del-id="${item.id}">删除</button>
                                </td>
                            </tr>`).join('')}
                        </tbody></table>`}
                <div class="btn-row mt-8" style="justify-content:space-between">
                    <button class="btn btn-ghost btn-sm" id="btn-prev" ${state.page <= 1 ? 'disabled' : ''}>上一页</button>
                    <span class="hint">第 ${state.page} 页 / 共 ${state.total} 条</span>
                    <button class="btn btn-ghost btn-sm" id="btn-next"
                        ${state.page * state.size >= state.total ? 'disabled' : ''}>下一页</button>
                </div>
            </div>

            <div class="card mb-0">
                <div class="card-title">
                    <h3>${state.draft.id ? '编辑岗位 #' + state.draft.id : '新建岗位'}</h3>
                    <span class="hint">JD 越具体，分析与出题越准</span>
                </div>
                <div class="field-row">
                    <div class="field">
                        <label>岗位名称 *</label>
                        <input data-edit="position" value="${esc(state.draft.position)}" placeholder="如：Java 后端开发工程师">
                    </div>
                    <div class="field">
                        <label>公司</label>
                        <input data-edit="company" value="${esc(state.draft.company)}">
                    </div>
                </div>
                <div class="field-row">
                    <div class="field">
                        <label>城市</label>
                        <input data-edit="city" value="${esc(state.draft.city)}">
                    </div>
                    <div class="field">
                        <label>薪资范围</label>
                        <input data-edit="salaryRange" value="${esc(state.draft.salaryRange)}" placeholder="如 15k-25k">
                    </div>
                </div>
                <div class="field">
                    <label>岗位职责</label>
                    <textarea data-edit="jobDesc" style="min-height:110px">${esc(state.draft.jobDesc)}</textarea>
                </div>
                <div class="field">
                    <label>任职要求</label>
                    <textarea data-edit="requirements" style="min-height:110px">${esc(state.draft.requirements)}</textarea>
                </div>
                <div class="field">
                    <label>来源链接（可选）</label>
                    <input data-edit="sourceUrl" value="${esc(state.draft.sourceUrl)}">
                </div>
                <div class="btn-row">
                    <button class="btn" id="btn-save">保存</button>
                    ${state.draft.id ? '<button class="btn btn-danger" id="btn-delete">删除</button>' : ''}
                </div>
            </div>
        </div>`;

    bind(container);
}

function bind(container) {
    container.querySelectorAll('[data-edit]').forEach((el) => {
        el.addEventListener('input', () => {
            state.draft[el.dataset.edit] = el.value;
        });
    });
    container.querySelectorAll('[data-edit-id]').forEach((btn) => {
        btn.addEventListener('click', () => edit(container, Number(btn.dataset.editId)));
    });
    container.querySelectorAll('[data-del-id]').forEach((btn) => {
        btn.addEventListener('click', () => remove(container, Number(btn.dataset.delId)));
    });
    container.querySelector('#btn-new')?.addEventListener('click', () => {
        state.draft = emptyDraft();
        paint(container);
    });
    container.querySelector('#btn-search')?.addEventListener('click', async () => {
        state.keyword = container.querySelector('#keyword').value.trim();
        state.page = 1;
        await loadList();
        paint(container);
    });
    container.querySelector('#keyword')?.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            container.querySelector('#btn-search').click();
        }
    });
    container.querySelector('#btn-prev')?.addEventListener('click', async () => {
        if (state.page > 1) {
            state.page -= 1;
            await loadList();
            paint(container);
        }
    });
    container.querySelector('#btn-next')?.addEventListener('click', async () => {
        if (state.page * state.size < state.total) {
            state.page += 1;
            await loadList();
            paint(container);
        }
    });
    container.querySelector('#btn-save')?.addEventListener('click', (event) => save(container, event.target));
    container.querySelector('#btn-delete')?.addEventListener('click', () => remove(container, state.draft.id));
}

async function edit(container, id) {
    try {
        const data = await get('/api/job/positions/' + id);
        state.draft = data.position;
        paint(container);
    } catch (error) {
        toast(error.message, 'error');
    }
}

async function save(container, button) {
    if (!state.draft.position || !state.draft.position.trim()) {
        toast('岗位名称不能为空', 'error');
        return;
    }
    await withLoading(button, '保存中…', async () => {
        try {
            const body = {
                company: state.draft.company,
                position: state.draft.position,
                city: state.draft.city,
                salaryRange: state.draft.salaryRange,
                jobDesc: state.draft.jobDesc,
                requirements: state.draft.requirements,
                sourceUrl: state.draft.sourceUrl,
                status: state.draft.status === 0 ? 0 : 1
            };
            if (state.draft.id) {
                state.draft = (await put('/api/job/positions/' + state.draft.id, body)).position;
                toast('岗位已更新', 'ok');
            } else {
                state.draft = (await post('/api/job/positions', body)).position;
                toast('岗位已创建', 'ok');
            }
            await loadList();
            paint(container);
        } catch (error) {
            toast(error.message, 'error');
        }
    });
}

async function remove(container, id) {
    if (!id) {
        return;
    }
    const ok = await confirmAction('确认删除该岗位？关联的分析与面试记录不会被删除。', '删除');
    if (!ok) {
        return;
    }
    try {
        await del('/api/job/positions/' + id);
        toast('已删除', 'ok');
        if (state.draft.id === id) {
            state.draft = emptyDraft();
        }
        await loadList();
        paint(container);
    } catch (error) {
        toast(error.message, 'error');
    }
}
