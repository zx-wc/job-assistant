/**
 * 模拟面试页（成员 D）
 * 对接：
 *   POST /api/job/interviews                       开始面试
 *   GET  /api/job/interviews                       记录列表
 *   GET  /api/job/interviews/{id}                  会话详情（题目 + 作答 + 报告）
 *   POST /api/job/interviews/{id}/answers          提交单题作答
 *   POST /api/job/interviews/{id}/follow-up        追问
 *   POST /api/job/interviews/{id}/finish           结束并生成报告
 *   GET  /api/job/interviews/{id}/report           查询报告
 */

import { get, post } from '../api.js';
import {
    bulletList, degradedTag, dimensionBars, emptyState, esc, fmtTime,
    renderRich, scoreRing, tag, toast, withLoading
} from '../ui.js';

const state = {
    page: 1,
    size: 10,
    total: 0,
    list: [],
    positions: [],
    resumes: [],
    session: null,
    lastResult: null
};

// ==================== 记录列表 ====================

export async function renderList(container) {
    state.page = 1;
    container.innerHTML = '<div class="loading">加载中…</div>';
    try {
        const data = await get('/api/job/interviews', { page: state.page, size: state.size });
        state.list = (data.page && data.page.list) || [];
        state.total = (data.page && data.page.total) || 0;
    } catch (error) {
        toast(error.message, 'error');
    }
    container.innerHTML = `
        <div class="card">
            <div class="card-title">
                <h3>训练记录</h3>
                <button class="btn" id="btn-new">开始新面试</button>
            </div>
            ${state.list.length === 0
                ? emptyState('还没有模拟面试记录', '<button class="btn btn-sm" id="btn-new2">开始第一次模拟面试</button>')
                : `<table class="table">
                    <thead><tr><th>面试</th><th>难度</th><th>进度</th><th>得分</th><th>状态</th><th>时间</th><th>操作</th></tr></thead>
                    <tbody>
                    ${state.list.map((item) => `
                        <tr>
                            <td><strong>${esc(item.title || '模拟面试')}</strong>
                                <div class="hint">${esc(item.positionTitle || '')} ${item.company ? '· ' + esc(item.company) : ''}</div></td>
                            <td>${esc(item.difficulty || 'NORMAL')}</td>
                            <td>${item.answeredCount || 0}/${item.totalQuestions || 0}</td>
                            <td>${item.overallScore === null || item.overallScore === undefined ? '-' : item.overallScore + ' 分'}</td>
                            <td>${item.status === 1 ? tag('已结束', 'tag-muted') : tag('进行中', 'tag-ok')}
                                ${degradedTag(item.degraded)}</td>
                            <td>${esc(fmtTime(item.createTime))}</td>
                            <td class="actions">
                                ${item.status === 1
                                    ? `<button class="btn btn-ghost btn-sm" data-report="${item.id}">查看报告</button>`
                                    : `<button class="btn btn-sm" data-continue="${item.id}">继续练习</button>
                                       <button class="btn btn-ghost btn-sm" data-report="${item.id}">报告</button>`}
                            </td>
                        </tr>`).join('')}
                    </tbody></table>`}
            <div class="btn-row mt-8" style="justify-content:space-between">
                <button class="btn btn-ghost btn-sm" id="btn-prev" ${state.page <= 1 ? 'disabled' : ''}>上一页</button>
                <span class="hint">第 ${state.page} 页 / 共 ${state.total} 条</span>
                <button class="btn btn-ghost btn-sm" id="btn-next"
                    ${state.page * state.size >= state.total ? 'disabled' : ''}>下一页</button>
            </div>
        </div>`;

    const goNew = () => {
        location.hash = '#/interviews/new';
    };
    container.querySelector('#btn-new')?.addEventListener('click', goNew);
    container.querySelector('#btn-new2')?.addEventListener('click', goNew);
    container.querySelectorAll('[data-continue]').forEach((btn) => {
        btn.addEventListener('click', () => {
            location.hash = '#/interviews/' + btn.dataset.continue;
        });
    });
    container.querySelectorAll('[data-report]').forEach((btn) => {
        btn.addEventListener('click', () => {
            location.hash = '#/interviews/' + btn.dataset.report + '/report';
        });
    });
    container.querySelector('#btn-prev')?.addEventListener('click', async () => {
        if (state.page > 1) {
            state.page -= 1;
            await renderList(container);
        }
    });
    container.querySelector('#btn-next')?.addEventListener('click', async () => {
        if (state.page * state.size < state.total) {
            state.page += 1;
            await renderList(container);
        }
    });
}

// ==================== 开始面试 ====================

export async function renderCreate(container, params) {
    container.innerHTML = '<div class="loading">加载中…</div>';
    try {
        const [positions, resumes] = await Promise.all([
            get('/api/job/positions', { page: 1, size: 50 }),
            get('/api/job/resumes', { page: 1, size: 50 })
        ]);
        state.positions = (positions.page && positions.page.list) || [];
        state.resumes = (resumes.page && resumes.page.list) || [];
    } catch (error) {
        toast(error.message, 'error');
    }
    const presetPosition = params && params.positionId ? String(params.positionId) : '';

    if (state.positions.length === 0) {
        container.innerHTML = `<div class="card">${emptyState('需要先添加一个目标岗位，才能围绕岗位出题',
            '<a class="btn btn-sm" href="#/positions">去添加岗位</a>')}</div>`;
        return;
    }

    container.innerHTML = `
        <div class="card" style="max-width:720px">
            <div class="card-title">
                <h3>开始一场文字模拟面试</h3>
                <span class="hint">AI 将围绕岗位与简历出题，逐题评分，最后生成反馈报告</span>
            </div>
            <div class="field">
                <label>目标岗位 *</label>
                <select id="sel-position">
                    ${state.positions.map((item) => `<option value="${item.id}" ${String(item.id) === presetPosition ? 'selected' : ''}>
                        ${esc(item.position)}${item.company ? ' · ' + esc(item.company) : ''}</option>`).join('')}
                </select>
            </div>
            <div class="field">
                <label>关联简历（可选，选了会针对项目经历追问）</label>
                <select id="sel-resume">
                    <option value="">不关联简历</option>
                    ${state.resumes.map((item) => `<option value="${item.id}">${esc(item.title)}</option>`).join('')}
                </select>
            </div>
            <div class="field-row">
                <div class="field">
                    <label>难度</label>
                    <select id="sel-difficulty">
                        <option value="EASY">EASY · 基础概念</option>
                        <option value="NORMAL" selected>NORMAL · 常规校招</option>
                        <option value="HARD">HARD · 深挖原理</option>
                    </select>
                </div>
                <div class="field">
                    <label>题目数量（1-12）</label>
                    <input id="inp-count" type="number" min="1" max="12" value="5">
                </div>
            </div>
            <div class="field">
                <label>面试标题（可选）</label>
                <input id="inp-title" placeholder="如：某公司-Java 后端-模拟面试">
            </div>
            <div class="btn-row">
                <button class="btn" id="btn-start">开始面试</button>
                <button class="btn btn-ghost" id="btn-back">返回记录</button>
            </div>
            <div class="tip mt-16">提示：面试内容仅用于练习，AI 评价不构成真实面试结论。</div>
        </div>`;

    container.querySelector('#btn-back')?.addEventListener('click', () => {
        location.hash = '#/interviews';
    });
    container.querySelector('#btn-start')?.addEventListener('click', (event) => start(container, event.target));
}

async function start(container, button) {
    const positionId = container.querySelector('#sel-position').value;
    const resumeId = container.querySelector('#sel-resume').value;
    const difficulty = container.querySelector('#sel-difficulty').value;
    const questionCount = Number(container.querySelector('#inp-count').value) || 5;
    const title = container.querySelector('#inp-title').value.trim();
    await withLoading(button, '出题中，请稍候…', async () => {
        try {
            const data = await post('/api/job/interviews', {
                positionId: Number(positionId),
                resumeId: resumeId ? Number(resumeId) : null,
                difficulty,
                questionCount,
                title: title || null
            });
            const session = data.session;
            toast(session.degraded ? 'AI 暂不可用，已用本地题库出题' : '题目已生成，开始作答吧',
                session.degraded ? 'info' : 'ok');
            location.hash = '#/interviews/' + session.id;
        } catch (error) {
            toast(error.message, 'error');
        }
    });
}

// ==================== 面试进行中 ====================

export async function renderRoom(container, params) {
    state.lastResult = null;
    container.innerHTML = '<div class="loading">加载中…</div>';
    try {
        state.session = (await get('/api/job/interviews/' + params.id)).session;
    } catch (error) {
        container.innerHTML = `<div class="card">${emptyState('面试会话不存在或无权访问')}</div>`;
        return;
    }
    paintRoom(container, params.id);
}

function paintRoom(container, sessionId) {
    const session = state.session;
    const questions = session.questions || [];
    const pending = questions.find((q) => q.status === 0);
    const answered = questions.filter((q) => q.status === 1).length;
    const total = questions.length || session.totalQuestions || 0;
    const percent = total === 0 ? 0 : Math.round(answered * 100 / total);

    container.innerHTML = `
        <div class="card">
            <div class="card-title">
                <h3>${esc(session.title || '模拟面试')}</h3>
                <div class="btn-row">
                    ${tag('难度 ' + (session.difficulty || 'NORMAL'), 'tag-muted')}
                    ${degradedTag(session.degraded)}
                    <button class="btn btn-ghost btn-sm" id="btn-exit">返回记录</button>
                </div>
            </div>
            <div class="progress">
                <span>进度 ${answered}/${total}</span>
                <div class="bar"><i style="width:${percent}%"></i></div>
                <span>${percent}%</span>
            </div>
            <div class="hint mt-8">岗位：${esc(session.positionTitle || '-')}
                ${session.company ? ' · ' + esc(session.company) : ''}
                · 简历：${esc(session.resumeTitle || '未关联')}</div>
        </div>

        ${state.lastResult ? renderAnswerResult(state.lastResult) : ''}

        ${session.status === 1
            ? `<div class="card">${emptyState('本场面试已结束', '<button class="btn btn-sm" id="btn-report">查看反馈报告</button>')}</div>`
            : pending
                ? renderAnswerForm(pending)
                : `<div class="card">
                     <div class="card-title"><h3>所有题目已作答</h3></div>
                     <p class="hint">点击下方按钮生成整场反馈报告：包含综合得分、亮点、短板与改进建议。</p>
                     <button class="btn" id="btn-finish">生成反馈报告</button>
                   </div>`}

        <div class="card">
            <div class="card-title"><h3>本场题目与作答</h3></div>
            ${questions.length === 0 ? '<div class="hint">暂无题目</div>' : questions.map((q) => renderQaBlock(q)).join('')}
        </div>`;

    container.querySelector('#btn-exit')?.addEventListener('click', () => {
        location.hash = '#/interviews';
    });
    container.querySelector('#btn-report')?.addEventListener('click', () => {
        location.hash = '#/interviews/' + sessionId + '/report';
    });
    container.querySelector('#btn-submit-answer')?.addEventListener('click', (event) => submitAnswer(container, sessionId, event.target));
    container.querySelector('#btn-finish')?.addEventListener('click', (event) => finish(container, sessionId, event.target));
    container.querySelectorAll('[data-follow-up]').forEach((btn) => {
        btn.addEventListener('click', () => followUp(container, sessionId, Number(btn.dataset.followUp)));
    });
    container.querySelectorAll('[data-goto-question]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const block = container.querySelector('#q-' + btn.dataset.gotoQuestion);
            block?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        });
    });
}

function renderAnswerForm(question) {
    return `
        <div class="card answer-box">
            <div class="card-title">
                <h3>第 ${question.questionNo} 题 ${tag(question.type || '技能')}</h3>
                <span class="hint">建议 150-300 字：结论 → 做法 → 结果（带数据）</span>
            </div>
            <div class="qa-block">
                <div class="q">${renderRich(question.question)}</div>
                ${(question.expectedPoints || []).length > 0
                    ? `<div class="hint">考察要点：${esc((question.expectedPoints || []).join('；'))}</div>` : ''}
            </div>
            <div class="field">
                <textarea id="answer-input" placeholder="请输入你的回答…"></textarea>
            </div>
            <div class="btn-row">
                <button class="btn" id="btn-submit-answer">提交作答并评分</button>
                <span class="hint">提交后不可修改，但可以对该题追加一次追问</span>
            </div>
        </div>`;
}

function renderAnswerResult(result) {
    return `
        <div class="card" style="border-color:#bbf7d0;background:#f7fef9">
            <div class="card-title">
                <h3>第 ${result.questionNo} 题评分</h3>
                <span>${degradedTag(result.degraded)}</span>
            </div>
            <div class="score-line">
                ${scoreRing(result.score, '单题得分')}
                <div style="flex:1">
                    <div class="rich">${renderRich(result.comment)}</div>
                    ${result.allAnswered
                        ? '<div class="tip tip-warn mt-8">所有题目已作答，可以生成整场反馈报告了。</div>' : ''}
                </div>
            </div>
            <div class="grid grid-2 mt-16">
                <div>
                    <div class="card-title"><h3>亮点</h3></div>
                    ${bulletList(result.highlights, '本次回答暂无记录到亮点')}
                </div>
                <div>
                    <div class="card-title"><h3>改进建议</h3></div>
                    ${bulletList(result.improvements, '暂无')}
                </div>
            </div>
            ${result.nextQuestion
                ? `<div class="tip mt-8">下一题：第 ${result.nextQuestion.questionNo} 题 · ${esc(result.nextQuestion.type || '技能')}</div>`
                : ''}
        </div>`;
}

function renderQaBlock(question, allowFollowUp = true) {
    return `
        <div class="qa-block" id="q-${question.questionNo}">
            <div class="q">第 ${question.questionNo} 题 ${tag(question.type || '技能')}
                ${question.score === null || question.score === undefined ? '' : `<span class="tag tag-ok">${question.score} 分</span>`}
                ${question.status === 0 ? '<span class="tag tag-warn">待作答</span>' : ''}
            </div>
            <div class="rich">${renderRich(question.question)}</div>
            ${question.answer ? `<div class="a">${esc(question.answer)}</div>` : ''}
            ${question.feedback ? `<div class="fb">${renderRich(question.feedback)}</div>` : ''}
            ${allowFollowUp && question.status === 1
                ? `<div class="btn-row mt-8">
                     <button class="btn btn-ghost btn-sm" data-follow-up="${question.questionNo}">就这题追问一次</button>
                   </div>` : ''}
        </div>`;
}

async function submitAnswer(container, sessionId, button) {
    const input = container.querySelector('#answer-input');
    const answer = input ? input.value.trim() : '';
    if (!answer) {
        toast('请先输入回答内容', 'error');
        return;
    }
    const pending = (state.session.questions || []).find((q) => q.status === 0);
    await withLoading(button, '评分中…', async () => {
        try {
            const data = await post('/api/job/interviews/' + sessionId + '/answers', {
                questionNo: pending.questionNo,
                answer
            });
            state.lastResult = data.result;
            state.session = (await get('/api/job/interviews/' + sessionId)).session;
            toast(data.result.degraded ? 'AI 不可用，已用本地规则评分' : '评分完成',
                data.result.degraded ? 'info' : 'ok');
            paintRoom(container, sessionId);
        } catch (error) {
            toast(error.message, 'error');
        }
    });
}

async function followUp(container, sessionId, questionNo) {
    try {
        const data = await post('/api/job/interviews/' + sessionId + '/follow-up', { questionNo });
        state.session = data.session;
        toast('已生成一道追问，请继续作答', 'ok');
        paintRoom(container, sessionId);
    } catch (error) {
        toast(error.message, 'error');
    }
}

async function finish(container, sessionId, button) {
    await withLoading(button, '生成报告中…', async () => {
        try {
            const data = await post('/api/job/interviews/' + sessionId + '/finish');
            toast(data.report.degraded ? 'AI 不可用，已生成规则版报告' : '反馈报告已生成',
                data.report.degraded ? 'info' : 'ok');
            location.hash = '#/interviews/' + sessionId + '/report';
        } catch (error) {
            toast(error.message, 'error');
        }
    });
}

// ==================== 反馈报告 ====================

export async function renderReport(container, params) {
    container.innerHTML = '<div class="loading">加载中…</div>';
    let session;
    let report;
    try {
        const detail = await get('/api/job/interviews/' + params.id);
        session = detail.session;
    } catch (error) {
        container.innerHTML = `<div class="card">${emptyState('面试记录不存在')}</div>`;
        return;
    }
    try {
        report = (await get('/api/job/interviews/' + params.id + '/report')).report;
    } catch (error) {
        report = null;
    }

    if (!report) {
        container.innerHTML = `
            <div class="card">
                <div class="card-title"><h3>${esc(session.title || '模拟面试')}</h3></div>
                ${emptyState('本场面试还没有生成反馈报告', '<button class="btn btn-sm" id="btn-finish">立即生成报告</button>')}
            </div>`;
        container.querySelector('#btn-finish')?.addEventListener('click', async (event) => {
            await withLoading(event.target, '生成中…', async () => {
                try {
                    await post('/api/job/interviews/' + params.id + '/finish');
                    toast('报告已生成', 'ok');
                    renderReport(container, params);
                } catch (error) {
                    toast(error.message, 'error');
                }
            });
        });
        return;
    }

    const questions = session.questions || [];
    container.innerHTML = `
        <div class="card">
            <div class="card-title">
                <h3>${esc(session.title || '模拟面试')} · 反馈报告</h3>
                <div class="btn-row">
                    <span>${degradedTag(report.degraded)}</span>
                    <button class="btn btn-ghost btn-sm" id="btn-back">返回记录</button>
                    <button class="btn btn-sm" id="btn-again">再练一次</button>
                </div>
            </div>
            <div class="score-line">
                ${scoreRing(report.overallScore, '综合得分')}
                <div style="flex:1">
                    <div class="hint">岗位：${esc(session.positionTitle || '-')}
                        ${session.company ? ' · ' + esc(session.company) : ''}
                        · 题数：${questions.length} · 报告生成：${esc(fmtTime(report.createTime))}</div>
                    <div class="rich mt-8">${renderRich(report.summary)}</div>
                </div>
            </div>
        </div>

        <div class="grid grid-2">
            <div class="card">
                <div class="card-title"><h3>维度评分</h3></div>
                ${dimensionBars(report.dimensionScores)}
            </div>
            <div class="card">
                <div class="card-title"><h3>亮点</h3></div>
                ${bulletList(report.strengths)}
            </div>
            <div class="card">
                <div class="card-title"><h3>不足</h3></div>
                ${bulletList(report.weaknesses)}
            </div>
            <div class="card">
                <div class="card-title"><h3>下一步行动建议</h3></div>
                ${bulletList(report.suggestions)}
            </div>
        </div>

        <div class="card">
            <div class="card-title"><h3>逐题回顾</h3></div>
            ${questions.map((q) => renderQaBlock(q, false)).join('')}
        </div>`;

    container.querySelector('#btn-back')?.addEventListener('click', () => {
        location.hash = '#/interviews';
    });
    container.querySelector('#btn-again')?.addEventListener('click', () => {
        location.hash = '#/interviews/new' + (session.positionId ? '?positionId=' + session.positionId : '');
    });
}
