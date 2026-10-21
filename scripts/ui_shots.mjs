/**
 * 前端页面截图校验（成员 E：UI/兼容性测试辅助工具）
 * 通过 Chrome DevTools Protocol 驱动无头 Chrome，逐页截图，用于人工核验页面渲染与接口联调结果。
 * 运行前提：应用已在 8081 端口启动，Chrome 已开启 --remote-debugging-port=9222。
 */
import fs from 'node:fs';
import path from 'node:path';

const BASE = process.env.JOB_BASE_URL || 'http://127.0.0.1:8080';
const OUT_DIR = process.argv[2] || 'shots';
const CDP_LIST = 'http://127.0.0.1:9222/json/list';

fs.mkdirSync(OUT_DIR, { recursive: true });
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function login(username, password) {
    const response = await fetch(BASE + '/api/job/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
    });
    const json = await response.json();
    if (json.code !== 200) {
        throw new Error('登录失败: ' + json.msg);
    }
    return json.dataMap;
}

const targets = await (await fetch(CDP_LIST)).json();
const page = targets.find((item) => item.type === 'page');
if (!page) {
    throw new Error('未找到可用的页面 target');
}

const ws = new WebSocket(page.webSocketDebuggerUrl);
const pending = new Map();
let seq = 1;
ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
        const { resolve, reject } = pending.get(message.id);
        pending.delete(message.id);
        if (message.error) {
            reject(new Error(JSON.stringify(message.error)));
        } else {
            resolve(message.result);
        }
    }
};
await new Promise((resolve, reject) => {
    ws.onopen = resolve;
    ws.onerror = reject;
});

function send(method, params = {}) {
    const id = seq++;
    return new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject });
        ws.send(JSON.stringify({ id, method, params }));
    });
}

async function evaluate(expression) {
    const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (result.exceptionDetails) {
        throw new Error('页面脚本异常: ' + JSON.stringify(result.exceptionDetails.text || result.exceptionDetails));
    }
    return result.result.value;
}

async function shot(name) {
    const { data } = await send('Page.captureScreenshot', { format: 'png' });
    const file = path.join(OUT_DIR, name + '.png');
    fs.writeFileSync(file, Buffer.from(data, 'base64'));
    const title = await evaluate('document.getElementById("page-title")?.textContent || document.title');
    const text = await evaluate('(document.getElementById("view")?.innerText || "").slice(0, 80).replace(/\\s+/g, " ")');
    console.log(`${name}: title="${title}" text="${text}" -> ${file}`);
}

async function route(hash, waitMs = 1800) {
    await evaluate(`location.hash = ${JSON.stringify(hash)}`);
    await sleep(waitMs);
}

await send('Page.enable');
await send('Runtime.enable');
await send('Emulation.setDeviceMetricsOverride', {
    width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false
});

const userAuth = await login('jobuser', '123456');
const adminAuth = await login('admin', '123456');

// 1. 登录页
await send('Page.navigate', { url: BASE + '/job/index.html' });
await sleep(1500);
await evaluate('localStorage.clear(); location.hash = "#/login"');
await sleep(1200);
await shot('01-login');

// 2. 注入普通用户登录态，进入各业务页
await evaluate(`localStorage.setItem('job_portal_token', ${JSON.stringify(userAuth.token)});
    localStorage.setItem('job_portal_user', ${JSON.stringify(JSON.stringify(userAuth.user))});`);
await send('Page.navigate', { url: BASE + '/job/index.html?bust=' + Date.now() + '#/dashboard' });
await sleep(2500);
await shot('02-dashboard');

await route('#/resumes', 2200);
await shot('03-resumes');

await route('#/positions', 2000);
await shot('04-positions');

await route('#/analyses', 2000);
await shot('05-analyses');

// 打开最新一份分析报告
const analysisId = await evaluate(`(async () => {
    const response = await fetch('/api/job/analyses?page=1&size=1', {
        headers: { 'Authorization': 'Bearer ' + localStorage.getItem('job_portal_token') }
    });
    const json = await response.json();
    return json.dataMap.page.list[0]?.id || 1;
})()`);
await route('#/analyses/' + analysisId, 2000);
await shot('06-analysis-report');

await route('#/interviews', 2000);
await shot('07-interviews');

await route('#/interviews/new', 2000);
await shot('08-interview-new');

const sessionId = await evaluate(`(async () => {
    const response = await fetch('/api/job/interviews?page=1&size=1', {
        headers: { 'Authorization': 'Bearer ' + localStorage.getItem('job_portal_token') }
    });
    const json = await response.json();
    return json.dataMap.page.list[0]?.id || 1;
})()`);
await route('#/interviews/' + sessionId, 2200);
await shot('09-interview-room');

await route('#/interviews/' + sessionId + '/report', 2200);
await shot('10-interview-report');

// 3. 管理员页面
await evaluate(`localStorage.setItem('job_portal_token', ${JSON.stringify(adminAuth.token)});
    localStorage.setItem('job_portal_user', ${JSON.stringify(JSON.stringify(adminAuth.user))});`);
await send('Page.navigate', { url: BASE + '/job/index.html?bust=' + Date.now() + '#/admin' });
await sleep(2800);
await shot('11-admin-ai');

console.log('截图完成，共 11 张，目录：' + path.resolve(OUT_DIR));
ws.close();
