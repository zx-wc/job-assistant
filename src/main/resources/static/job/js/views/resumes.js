/**
 * 简历管理页（成员 D）
 * 对接接口：
 *   GET    /api/job/resumes?keyword&page&size
 *   POST   /api/job/resumes                       新建
 *   PUT    /api/job/resumes/{id}                  编辑（正文变化自动留版本）
 *   GET    /api/job/resumes/{id}                  详情
 *   DELETE /api/job/resumes/{id}                  删除
 *   GET    /api/job/resumes/{id}/versions         版本列表
 *   GET    /api/job/resumes/{id}/versions/{no}    版本详情
 *   POST   /api/job/resumes/{id}/versions         手动存版本
 *   POST   /api/job/resumes/{id}/versions/{no}/restore  回滚
 *   POST   /api/job/resumes/upload-text           上传 txt/md
 */

import { del, get, post, put, upload } from '../api.js';
import { esc, fmtTime, toast, confirmAction, withLoading } from '../ui.js';

const state = {
    page: 1,
    size: 8,
    keyword: '',
    total: 0,
    list: [],
    draft: null,
    versions: [],
    versionPreview: null
};

export async function render(container) {
    state.draft = emptyDraft();
    state.versions = [];
    state.versionPreview = null;
    state.keyword = '';
    state.page = 1;
    container.innerHTML = '<div class="loading">加载中…</div>';
    await loadList();
    // 默认打开第一份简历，便于直接看到正文与版本历史
    if (state.list.length > 0) {
        try {
            const data = await get('/api/job/resumes/' + state.list[0].id);
            state.draft = data.resume;
            await loadVersions(state.draft.id);
        } catch (error) {
            // 打不开时保持新建状态，不阻断页面
        }
    }
    paint(container);
}

function emptyDraft() {
    return {
        id: null,
        title: '',
        candidateName: '',
        targetPosition: '',
        summary: '',
        content: '',
        status: 1
    };
}

async function loadList() {
    try {
        const data = await get('/api/job/resumes', {
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
        <div class="split">
            <div class="card">
                <div class="card-title">
                    <h3>我的简历</h3>
                    <button class="btn btn-sm" id="btn-new">新建</button>
                </div>
                <div class="btn-row" style="margin-bottom:10px">
                    <input id="keyword" placeholder="搜索标题 / 岗位 / 姓名" value="${esc(state.keyword)}"
                           style="flex:1;padding:8px 10px;border:1px solid var(--border);border-radius:8px">
                    <button class="btn btn-ghost btn-sm" id="btn-search">搜索</button>
                </div>
                <div class="pick-list" id="pick-list">
                    ${state.list.length === 0
                        ? '<div class="empty">还没有简历，点击右上角「新建」开始</div>'
                        : state.list.map((item) => `
                            <div class="pick-item ${state.draft && state.draft.id === item.id ? 'active' : ''}" data-id="${item.id}">
                                <strong>${esc(item.title)}</strong>
                                <small>${esc(item.targetPosition || '未填写目标岗位')} · v${item.currentVersion || 1} ·
                                更新于 ${esc(fmtTime(item.updateTime))}</small>
                            </div>`).join('')}
                </div>
                <div class="btn-row mt-8" style="justify-content:space-between">
                    <button class="btn btn-ghost btn-sm" id="btn-prev" ${state.page <= 1 ? 'disabled' : ''}>上一页</button>
                    <span class="hint">第 ${state.page} 页 / 共 ${state.total} 条</span>
                    <button class="btn btn-ghost btn-sm" id="btn-next"
                        ${state.page * state.size >= state.total ? 'disabled' : ''}>下一页</button>
                </div>
            </div>

            <div>
                <div class="card">
                    <div class="card-title">
                        <h3>${state.draft.id ? '编辑简历 #' + state.draft.id : '新建简历'}</h3>
                        <span class="hint">正文入库前会自动脱敏手机号 / 邮箱 / 身份证</span>
                    </div>
                    <div class="field-row">
                        <div class="field">
                            <label>简历标题</label>
                            <input data-edit="title" value="${esc(state.draft.title)}" placeholder="如：Java 后端-秋招版">
                        </div>
                        <div class="field">
                            <label>姓名</label>
                            <input data-edit="candidateName" value="${esc(state.draft.candidateName)}">
                        </div>
                        <div class="field">
                            <label>目标岗位</label>
                            <input data-edit="targetPosition" value="${esc(state.draft.targetPosition)}">
                        </div>
                    </div>
                    <div class="field">
                        <label>一句话概述</label>
                        <input data-edit="summary" value="${esc(state.draft.summary)}">
                    </div>
                    <div class="field">
                        <label>简历正文（纯文本）</label>
                        <textarea data-edit="content" style="min-height:280px"
                            placeholder="教育背景、专业技能、项目经历、实习经历、荣誉奖项…">${esc(state.draft.content)}</textarea>
                    </div>
                    <div class="btn-row">
                        <button class="btn" id="btn-save">保存</button>
                        ${state.draft.id ? `<button class="btn btn-ghost" id="btn-save-version">保存为新版本</button>
                        <button class="btn btn-danger" id="btn-delete">删除</button>` : ''}
                        <label class="btn btn-ghost" style="cursor:pointer">
                            导入 txt/md
                            <input type="file" id="file-input" accept=".txt,.md" hidden>
                        </label>
                        ${state.draft.id ? `<span class="hint">当前版本 v${state.draft.currentVersion || 1}</span>` : ''}
                    </div>
                </div>

                ${state.draft.id ? renderVersions() : ''}
            </div>
        </div>`;

    bind(container);
}

function renderVersions() {
    return `
        <div class="card">
            <div class="card-title">
                <h3>版本历史</h3>
                <span class="hint">历史版本只增不改，可随时回滚</span>
            </div>
            <div class="btn-row" style="margin-bottom:10px">
                <input id="version-note" placeholder="版本说明，如：补充项目量化数据"
                    style="flex:1;padding:8px 10px;border:1px solid var(--border);border-radius:8px">
                <button class="btn btn-sm" id="btn-add-version">存档当前内容</button>
            </div>
            ${state.versions.length === 0
                ? '<div class="hint">暂无版本记录</div>'
                : `<table class="table">
                    <thead><tr><th>版本</th><th>说明</th><th>时间</th><th>操作</th></tr></thead>
                    <tbody>
                    ${state.versions.map((v) => `
                        <tr>
                            <td>v${v.versionNo}${state.draft.currentVersion === v.versionNo ? ' <span class="tag tag-ok">当前</span>' : ''}</td>
                            <td>${esc(v.changeNote || '-')}</td>
                            <td>${esc(fmtTime(v.createTime))}</td>
                            <td class="actions">
                                <button class="btn btn-ghost btn-sm" data-view-version="${v.versionNo}">查看</button>
                                <button class="btn btn-ghost btn-sm" data-restore="${v.versionNo}">回滚</button>
                            </td>
                        </tr>`).join('')}
                    </tbody></table>`}
            ${state.versionPreview ? `
                <div class="mt-16">
                    <div class="card-title"><h3>版本 v${state.versionPreview.versionNo} 内容</h3></div>
                    <div class="mono">${esc(state.versionPreview.content || '（空）')}</div>
                </div>` : ''}
        </div>`;
}

function bind(container) {
    container.querySelectorAll('[data-edit]').forEach((el) => {
        el.addEventListener('input', () => {
            state.draft[el.dataset.edit] = el.value;
        });
    });
    container.querySelectorAll('[data-id]').forEach((el) => {
        el.addEventListener('click', () => select(container, Number(el.dataset.id)));
    });

    container.querySelector('#btn-new')?.addEventListener('click', () => {
        state.draft = emptyDraft();
        state.versions = [];
        state.versionPreview = null;
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
    container.querySelector('#btn-delete')?.addEventListener('click', () => remove(container));
    container.querySelector('#btn-save-version')?.addEventListener('click', (event) => addVersion(container, event.target));
    container.querySelector('#btn-add-version')?.addEventListener('click', (event) => addVersion(container, event.target));
    container.querySelector('#file-input')?.addEventListener('change', (event) => importFile(container, event.target));

    container.querySelectorAll('[data-view-version]').forEach((btn) => {
        btn.addEventListener('click', () => previewVersion(container, Number(btn.dataset.viewVersion)));
    });
    container.querySelectorAll('[data-restore]').forEach((btn) => {
        btn.addEventListener('click', () => restoreVersion(container, Number(btn.dataset.restore)));
    });
}

async function select(container, id) {
    try {
        const data = await get('/api/job/resumes/' + id);
        state.draft = data.resume;
        state.versionPreview = null;
        await loadVersions(id);
        paint(container);
    } catch (error) {
        toast(error.message, 'error');
    }
}

async function loadVersions(id) {
    try {
        const data = await get('/api/job/resumes/' + id + '/versions');
        state.versions = data.versions || [];
    } catch (error) {
        state.versions = [];
    }
}

async function save(container, button) {
    if (!state.draft.content || !state.draft.content.trim()) {
        toast('简历正文不能为空', 'error');
        return;
    }
    await withLoading(button, '保存中…', async () => {
        try {
            const body = {
                title: state.draft.title,
                candidateName: state.draft.candidateName,
                targetPosition: state.draft.targetPosition,
                summary: state.draft.summary,
                content: state.draft.content,
                status: state.draft.status === 0 ? 0 : 1
            };
            let saved;
            if (state.draft.id) {
                saved = (await put('/api/job/resumes/' + state.draft.id, body)).resume;
                toast('已保存（正文有变化会自动留存版本）', 'ok');
            } else {
                saved = (await post('/api/job/resumes', body)).resume;
                toast('简历已创建', 'ok');
            }
            state.draft = saved;
            await loadList();
            await loadVersions(saved.id);
            paint(container);
        } catch (error) {
            toast(error.message, 'error');
        }
    });
}

async function remove(container) {
    const ok = await confirmAction('删除后该简历不再出现在列表中（历史版本保留在数据库），确认删除？', '删除');
    if (!ok) {
        return;
    }
    try {
        await del('/api/job/resumes/' + state.draft.id);
        toast('已删除', 'ok');
        state.draft = emptyDraft();
        state.versions = [];
        state.versionPreview = null;
        await loadList();
        paint(container);
    } catch (error) {
        toast(error.message, 'error');
    }
}

async function addVersion(container, button) {
    if (!state.draft.id) {
        toast('请先保存简历', 'error');
        return;
    }
    const note = container.querySelector('#version-note')?.value.trim() || '手动保存版本';
    await withLoading(button, '存档中…', async () => {
        try {
            await post('/api/job/resumes/' + state.draft.id + '/versions', { changeNote: note });
            toast('版本已保存', 'ok');
            await loadList();
            await loadVersions(state.draft.id);
            const fresh = await get('/api/job/resumes/' + state.draft.id);
            state.draft = fresh.resume;
            paint(container);
        } catch (error) {
            toast(error.message, 'error');
        }
    });
}

async function previewVersion(container, versionNo) {
    try {
        const data = await get('/api/job/resumes/' + state.draft.id + '/versions/' + versionNo);
        state.versionPreview = data.version;
        paint(container);
    } catch (error) {
        toast(error.message, 'error');
    }
}

async function restoreVersion(container, versionNo) {
    const ok = await confirmAction('回滚会把当前正文替换为 v' + versionNo + ' 的内容，并生成一个新版本记录。继续？', '回滚');
    if (!ok) {
        return;
    }
    try {
        const data = await post('/api/job/resumes/' + state.draft.id + '/versions/' + versionNo + '/restore');
        state.draft = data.resume;
        state.versionPreview = null;
        await loadList();
        await loadVersions(state.draft.id);
        toast('已回滚到 v' + versionNo, 'ok');
        paint(container);
    } catch (error) {
        toast(error.message, 'error');
    }
}

async function importFile(container, input) {
    const file = input.files && input.files[0];
    if (!file) {
        return;
    }
    const formData = new FormData();
    formData.append('file', file);
    try {
        const data = await upload('/api/job/resumes/upload-text', formData);
        state.draft.content = data.content || '';
        const textarea = container.querySelector('[data-edit="content"]');
        if (textarea) {
            textarea.value = state.draft.content;
        }
        toast('已导入 ' + (data.length || 0) + ' 个字符（敏感信息已脱敏）', 'ok');
    } catch (error) {
        toast(error.message, 'error');
    } finally {
        input.value = '';
    }
}
