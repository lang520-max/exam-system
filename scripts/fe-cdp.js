/* ============================================================================
 *  用 Edge + Chrome DevTools Protocol 真跑一遍登录，抓：
 *    - 控制台报错 / 未捕获异常
 *    - 登录前后 location.href
 *    - localStorage 里的 token 与 user
 *    - 最终页面的可见文本
 *  用法：node scripts/fe-cdp.js
 * ========================================================================== */

const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const EDGE = ['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
              'C:/Program Files/Microsoft/Edge/Application/msedge.exe']
             .find(p => fs.existsSync(p));
const PORT = 9222;
const PROFILE = path.join(os.tmpdir(), 'edge-cdp-' + Date.now());

if (!EDGE) { console.error('找不到 msedge.exe'); process.exit(1); }

const edge = spawn(EDGE, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=' + PORT,
  '--user-data-dir=' + PROFILE,
  'about:blank'
], { stdio: 'ignore' });

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function getTarget() {
  for (let i = 0; i < 40; i++) {
    try {
      const res = await fetch('http://127.0.0.1:' + PORT + '/json/list');
      const list = await res.json();
      const page = list.find(t => t.type === 'page');
      if (page && page.webSocketDebuggerUrl) { return page; }
    } catch (e) { /* 还没起来 */ }
    await sleep(400);
  }
  throw new Error('Edge 调试端口没就绪');
}

function cdp(ws) {
  let id = 0;
  const pending = new Map();
  const events = [];
  ws.addEventListener('message', ev => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
    } else if (msg.method) {
      events.push(msg);
    }
  });
  return {
    events,
    send(method, params) {
      const myId = ++id;
      return new Promise((resolve, reject) => {
        pending.set(myId, { resolve, reject });
        ws.send(JSON.stringify({ id: myId, method, params: params || {} }));
      });
    }
  };
}

(async function () {
  const target = await getTarget();
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener('open', res); ws.addEventListener('error', rej); });
  const c = cdp(ws);

  await c.send('Runtime.enable');
  await c.send('Page.enable');
  await c.send('Log.enable');

  const say = (s) => console.log(s);

  say('=== 1) 打开登录页 ===');
  await c.send('Page.navigate', { url: 'http://localhost:8080/login.html' });
  await sleep(2500);

  let r = await c.send('Runtime.evaluate', {
    expression: `JSON.stringify({
      url: location.href,
      title: document.title,
      hasAPI: typeof API,
      hasStore: typeof Store,
      hasAuth: typeof Auth,
      formFound: !!document.getElementById('form-login'),
      btnFound: !!document.getElementById('btn-login'),
      storage: Object.keys(localStorage).length
    })`, returnByValue: true
  });
  say('  ' + r.result.value);

  say('\n=== 2) 填入账号密码并点击登录 ===');
  await c.send('Runtime.evaluate', {
    expression: `
      document.getElementById('lg-username').value = 'teacher1';
      document.getElementById('lg-password').value = '123456';
      document.getElementById('btn-login').click();
      'clicked'`, returnByValue: true
  });

  for (const t of [500, 1500, 3000]) {
    await sleep(t === 500 ? 500 : t - 500);
    r = await c.send('Runtime.evaluate', {
      expression: `JSON.stringify({
        url: location.href,
        tokenLen: (localStorage.getItem('exam.accessToken')||'').length,
        user: localStorage.getItem('exam.user'),
        toast: (document.getElementById('toast-box')||{}).innerText || null,
        bodyHead: document.body.innerText.replace(/\\s+/g,' ').slice(0,160)
      })`, returnByValue: true
    });
    say(`  [+${t}ms] ` + r.result.value);
  }

  say('\n=== 3) 控制台报错 / 未捕获异常 ===');
  const errs = c.events.filter(e =>
    e.method === 'Runtime.exceptionThrown' ||
    (e.method === 'Runtime.consoleAPICalled' && e.params.type === 'error') ||
    e.method === 'Log.entryAdded');
  if (!errs.length) { say('  无'); }
  errs.slice(0, 12).forEach(e => {
    if (e.method === 'Runtime.exceptionThrown') {
      const d = e.params.exceptionDetails;
      say('  [异常] ' + (d.exception && d.exception.description || d.text));
    } else if (e.method === 'Runtime.consoleAPICalled') {
      say('  [console.error] ' + (e.params.args || []).map(a => a.value || a.description).join(' '));
    } else {
      const en = e.params.entry;
      say('  [' + en.level + '] ' + en.text + (en.url ? '  @' + en.url : ''));
    }
  });

  ws.close();
  edge.kill();
  await sleep(600);
  try { fs.rmSync(PROFILE, { recursive: true, force: true }); } catch (e) {}
  process.exit(0);
})().catch(async e => {
  console.error('诊断失败: ' + e.message);
  try { edge.kill(); } catch (x) {}
  process.exit(1);
});