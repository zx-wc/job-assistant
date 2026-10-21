/**
 * 通用 UI 工具（成员 D：交互与可视化）
 * 提供 HTML 转义、Toast、确认框、分数环、维度进度条、轻量 Markdown 渲染，
 * 所有插入到 DOM 的服务端文本都必须先经过 esc()，避免 XSS。
 */

/** HTML 转义 */
export function esc(value) {
    if (value === null || value === undefined) {
        return '';
    }
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/** Toast 提示 */
export function toast(message, type = 'info', timeout = 2600) {
    const box = document.getElementById('toast-box');
    if (!box) {
        return;
    }
    const el = document.createElement('div');
    el.className = 'toast toast-' + (type === 'error' ? 'error' : type === 'ok' ? 'ok' : 'info');
    el.textContent = message;
    box.appendChild(el);
    setTimeout(() => {
        el.style.opacity = '0';
        el.style.transition = 'opacity .2s';
        setTimeout(() => el.remove(), 220);
    }, timeout);
}

/** 确认框（Promise<boolean>） */
export function confirmAction(message, okText = '确认') {
    return new Promise((resolve) => {
        const mask = document.createElement('div');
        mask.className = 'mask';
        mask.innerHTML = `
            <div class="dialog">
                <h4>操作确认</h4>
                <p>${esc(message)}</p>
                <div class="btn-row">
                    <button class="btn btn-ghost" data-act="cancel">取消</button>
                    <button class="btn btn-danger" data-act="ok">${esc(okText)}</button>
                </div>
            </div>`;
        const close = (result) => {
            mask.remove();
            resolve(result);
        };
        mask.addEventListener('click', (event) => {
            if (event.target === mask) {
                close(false);
            }
            const act = event.target.getAttribute && event.target.getAttribute('data-act');
            if (act === 'ok') {
                close(true);
            } else if (act === 'cancel') {
                close(false);
            }
        });
        document.body.appendChild(mask);
    });
}

/** 时间格式化：后端返回 "yyyy-MM-dd HH:mm:ss" 字符串 */
export function fmtTime(text) {
    if (!text) {
        return '-';
    }
    return String(text).replace('T', ' ').slice(0, 19);
}

/** 分数对应颜色 */
export function scoreColor(score) {
    const value = Number(score) || 0;
    if (value >= 80) {
        return 'linear-gradient(135deg, #16a34a, #22c55e)';
    }
    if (value >= 60) {
        return 'linear-gradient(135deg, #4f46e5, #6366f1)';
    }
    if (value >= 40) {
        return 'linear-gradient(135deg, #d97706, #f59e0b)';
    }
    return 'linear-gradient(135deg, #dc2626, #ef4444)';
}

/** 分数环 HTML */
export function scoreRing(score, label = '匹配度') {
    const value = Math.max(0, Math.min(100, Number(score) || 0));
    return `<div class="score-ring" style="background:${scoreColor(value)}">
                <div style="text-align:center;line-height:1.1">${value}<br><small>${esc(label)}</small></div>
            </div>`;
}

/** 维度评分条 HTML */
export function dimensionBars(dimensions) {
    const entries = Object.entries(dimensions || {});
    if (entries.length === 0) {
        return '<div class="hint">暂无维度评分</div>';
    }
    return entries.map(([name, value]) => {
        const score = Math.max(0, Math.min(100, Number(value) || 0));
        return `<div class="dim-item">
                    <div class="dim-head"><span>${esc(name)}</span><span>${score}</span></div>
                    <div class="bar"><i style="width:${score}%"></i></div>
                </div>`;
    }).join('');
}

/** 标签 HTML */
export function tag(text, cls = '') {
    return `<span class="tag ${cls}">${esc(text)}</span>`;
}

/** 降级提示标签 */
export function degradedTag(degraded) {
    return degraded ? tag('本地降级', 'tag-warn') : tag('AI 生成', 'tag-ok');
}

/** 字符串列表 → <ul> */
export function bulletList(items, emptyText = '暂无') {
    const list = (items || []).filter((item) => item !== null && item !== undefined && String(item).trim() !== '');
    if (list.length === 0) {
        return `<div class="hint">${esc(emptyText)}</div>`;
    }
    return '<ul class="list-plain">' + list.map((item) => `<li>${esc(item)}</li>`).join('') + '</ul>';
}

/**
 * 轻量 Markdown 渲染（先转义再替换，保证安全）
 * 支持：代码块、行内代码、加粗、无序列表、换行。
 */
export function renderRich(text) {
    if (!text) {
        return '';
    }
    let html = esc(text);
    html = html.replace(/```([\s\S]*?)```/g, (m, code) => `<pre>${code.trim()}</pre>`);
    html = html.replace(/`([^`\n]+)`/g, '<code>$1</code>');
    html = html.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>');
    const lines = html.split('\n');
    const output = [];
    let inList = false;
    lines.forEach((line) => {
        const trimmed = line.trim();
        if (/^[-*]\s+/.test(trimmed) || /^\d+[.、]\s+/.test(trimmed)) {
            if (!inList) {
                output.push('<ul class="list-plain">');
                inList = true;
            }
            output.push('<li>' + trimmed.replace(/^([-*]|\d+[.、])\s+/, '') + '</li>');
        } else {
            if (inList) {
                output.push('</ul>');
                inList = false;
            }
            if (trimmed === '') {
                output.push('');
            } else {
                output.push(trimmed + '<br>');
            }
        }
    });
    if (inList) {
        output.push('</ul>');
    }
    return output.join('\n');
}

/** 空状态 HTML */
export function emptyState(text, actionHtml = '') {
    return `<div class="empty">${esc(text)}${actionHtml ? '<div class="mt-16">' + actionHtml + '</div>' : ''}</div>`;
}

/** 读取表单字段值 */
export function fieldValue(scope, name) {
    const el = scope.querySelector('[data-field="' + name + '"]');
    return el ? el.value : '';
}

/** 按钮 loading 包装 */
export async function withLoading(button, loadingText, task) {
    if (!button) {
        return task();
    }
    const origin = button.textContent;
    button.disabled = true;
    button.textContent = loadingText;
    try {
        return await task();
    } finally {
        button.disabled = false;
        button.textContent = origin;
    }
}
