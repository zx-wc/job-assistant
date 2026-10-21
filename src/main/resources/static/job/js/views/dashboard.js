/**
 * 求职概览页（成员 D：首页数据可视化）
 * 对接：GET /api/job/dashboard
 */

import { get } from '../api.js';
import { degradedTag, emptyState, esc, fmtTime } from '../ui.js';

export async function render(container) {
    const data = await get('/api/job/dashboard');
    const d = data.dashboard || {};
    const latest = d.latestMatchScore;

    container.innerHTML = `
        <div class="grid grid-3">
            <div class="stat">
                <div class="label">简历数量</div>
                <div class="value">${d.resumeCount || 0}</div>
                <div class="extra">支持多版本保存与回溯</div>
            </div>
            <div class="stat">
                <div class="label">关注岗位</div>
                <div class="value">${d.positionCount || 0}</div>
                <div class="extra">岗位 JD 用于分析与出题</div>
            </div>
            <div class="stat">
                <div class="label">分析报告</div>
                <div class="value">${d.analysisCount || 0}</div>
                <div class="extra">最近匹配度：${latest === null || latest === undefined ? '暂无' : latest + ' 分'}</div>
            </div>
            <div class="stat">
                <div class="label">模拟面试</div>
                <div class="value">${d.interviewCount || 0}</div>
                <div class="extra">进行中：${d.ongoingInterviewCount || 0} 场</div>
            </div>
        </div>

        <div class="card">
            <div class="card-title">
                <h3>开始一次求职准备</h3>
            </div>
            <div class="btn-row">
                <button class="btn" data-go="/resumes">① 录入简历</button>
                <button class="btn btn-ghost" data-go="/positions">② 添加目标岗位</button>
                <button class="btn btn-ghost" data-go="/analyses">③ 生成匹配分析</button>
                <button class="btn btn-ghost" data-go="/interviews/new">④ 开始模拟面试</button>
            </div>
            <div class="hint mt-8">推荐顺序：先录入简历与岗位，再做分析明确短板，最后针对短板做模拟面试。</div>
        </div>

        <div class="grid grid-2">
            <div class="card mb-0">
                <div class="card-title"><h3>最近分析报告</h3></div>
                ${renderRecentAnalyses(d.recentAnalyses)}
            </div>
            <div class="card mb-0">
                <div class="card-title"><h3>最近模拟面试</h3></div>
                ${renderRecentInterviews(d.recentInterviews)}
            </div>
        </div>`;

    container.querySelectorAll('[data-go]').forEach((btn) => {
        btn.addEventListener('click', () => {
            location.hash = '#' + btn.dataset.go;
        });
    });
    container.querySelectorAll('[data-analysis]').forEach((row) => {
        row.addEventListener('click', () => {
            location.hash = '#/analyses/' + row.dataset.analysis;
        });
    });
    container.querySelectorAll('[data-interview]').forEach((row) => {
        row.addEventListener('click', () => {
            const id = row.dataset.interview;
            location.hash = row.dataset.status === '1' ? '#/interviews/' + id + '/report' : '#/interviews/' + id;
        });
    });
}

function renderRecentAnalyses(list) {
    if (!list || list.length === 0) {
        return emptyState('还没有分析报告，先录入简历与岗位后生成一份吧', '<button class="btn btn-sm" data-go="/analyses">去分析</button>');
    }
    return '<ul class="list-clean">' + list.map((item) => `
        <li data-analysis="${item.id}" style="cursor:pointer">
            <div style="display:flex;justify-content:space-between;gap:10px;align-items:center">
                <div>
                    <strong>${esc(item.resumeTitle || '简历')}</strong>
                    <span class="hint">→ ${esc(item.positionTitle || '岗位')}${item.company ? ' · ' + esc(item.company) : ''}</span>
                    <div class="hint">${esc(fmtTime(item.createTime))} ${degradedTag(item.degraded)}</div>
                </div>
                <span style="font-weight:700;color:${Number(item.matchScore) >= 60 ? '#4f46e5' : '#d97706'}">${item.matchScore} 分</span>
            </div>
        </li>`).join('') + '</ul>';
}

function renderRecentInterviews(list) {
    if (!list || list.length === 0) {
        return emptyState('还没有模拟面试记录，试试从目标岗位生成一套题', '<button class="btn btn-sm" data-go="/interviews/new">开始面试</button>');
    }
    return '<ul class="list-clean">' + list.map((item) => `
        <li data-interview="${item.id}" data-status="${item.status}" style="cursor:pointer">
            <div style="display:flex;justify-content:space-between;gap:10px;align-items:center">
                <div>
                    <strong>${esc(item.title || '模拟面试')}</strong>
                    <div class="hint">进度 ${item.answeredCount || 0}/${item.totalQuestions || 0}
                        ${item.status === 1 ? '· 已结束' : '· 进行中'}
                        ${degradedTag(item.degraded)}</div>
                </div>
                <span style="font-weight:700;color:${Number(item.overallScore) >= 60 ? '#4f46e5' : '#d97706'}">
                    ${item.overallScore === null || item.overallScore === undefined ? '--' : item.overallScore + ' 分'}
                </span>
            </div>
        </li>`).join('') + '</ul>';
}
