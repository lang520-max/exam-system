/* ============================================================================
 *  前端链路诊断：在 Node 里加载真实的 static/js/api.js，模拟浏览器的调用过程
 *  验证三件事：
 *    1. API.post 能否正确解出 {code,message,data} 信封
 *    2. Store.save 写入的 token / user 是否可用于后续请求
 *    3. 登录后 Auth.homeFor(role) 指向的目标页面，其接口能否用这个 token 调通
 * ========================================================================== */

const BASE = 'http://localhost:8080';

/* ---- 最小的浏览器环境桩 ---- */
const store = {};
global.localStorage = {
  getItem: function (k) { return Object.prototype.hasOwnProperty.call(store, k) ? store[k] : null; },
  setItem: function (k, v) { store[k] = String(v); },
  removeItem: function (k) { delete store[k]; }
};
let navigated = null;
global.location = {
  href: BASE + '/login.html', pathname: '/login.html', search: '',
  protocol: 'http:', host: 'localhost:8080',
  replace: function (u) { navigated = u; },
  set hrefTo(v) { navigated = v; }
};
// api.js 里用 location.href = target 做跳转，用 setter 捕获
Object.defineProperty(global.location, 'href', {
  get: function () { return BASE + '/login.html'; },
  set: function (v) { navigated = v; }
});
global.document = {
  getElementById: function () { return null; },
  createElement: function () {
    return { style: {}, classList: { add: function () {}, remove: function () {} },
             appendChild: function () {}, setAttribute: function () {}, addEventListener: function () {} };
  },
  body: { appendChild: function () {} },
  addEventListener: function () {}
};
global.window = global;

/* ---- Node 的 fetch 不支持相对路径，这里补上 base ---- */
const realFetch = global.fetch;
global.fetch = function (url, opts) { return realFetch(BASE + url, opts); };

// 加载真实的前端代码
require('D:/deepseek-workspace/exam-system/src/main/resources/static/js/api.js');

let pass = 0, fail = 0;
function check(name, cond, extra) {
  if (cond) { pass++; console.log('  [OK]   ' + name); }
  else { fail++; console.log('  [FAIL] ' + name + (extra ? '  <- ' + extra : '')); }
}

(async function () {
  console.log('=== A. 教师登录链路 ===');
  const vo = await API.post('/api/auth/login', { username: 'teacher1', password: '123456' });
  check('API.post 正确解出 data（拿到 accessToken）', !!(vo && vo.accessToken));
  check('LoginVO 含 role/username', vo.role !== undefined && !!vo.username,
        JSON.stringify({ role: vo.role, username: vo.username, realName: vo.realName }));

  Store.save(vo);
  check('Store.save 后 getToken() 有值', !!Store.getToken());
  const u = Store.getUser();
  check('Store.getUser() 含 role 与 userId', !!(u && u.role !== undefined && u.userId),
        JSON.stringify(u));
  check('Auth.homeFor(1) === /teacher/exams.html', Auth.homeFor(vo.role) === '/teacher/exams.html',
        'actual=' + Auth.homeFor(vo.role));

  // 模拟跳到目标页面后，该页面自己的请求
  const exams = await API.get('/api/exams');
  check('目标页请求 GET /api/exams 成功（' + (exams ? exams.length : 0) + ' 场）', Array.isArray(exams));
  const banks = await API.get('/api/banks');
  check('目标页请求 GET /api/banks 成功（' + (banks ? banks.length : 0) + ' 个）', Array.isArray(banks));

  console.log('\n=== B. 学生登录链路 ===');
  const vo2 = await API.post('/api/auth/login', { username: 'student01', password: '123456' });
  Store.save(vo2);
  check('Auth.homeFor(2) === /student/exams.html', Auth.homeFor(vo2.role) === '/student/exams.html',
        'actual=' + Auth.homeFor(vo2.role));
  const pub = await API.get('/api/exams?status=1');
  check('目标页请求 GET /api/exams?status=1 成功（' + (pub ? pub.length : 0) + ' 场）', Array.isArray(pub));
  const my = await API.get('/api/exams/my');
  check('目标页请求 GET /api/exams/my 成功', Array.isArray(my));
  const stats = await API.get('/api/dashboard/my-stats');
  check('目标页请求 GET /api/dashboard/my-stats 成功', !!stats && stats.totalExams !== undefined);

  console.log('\n=== C. 角色守卫与错误处理 ===');
  // 学生试图访问教师页 → requireRole('teacher') 应拦截
  let blocked = false;
  try { Auth.requireRole('teacher'); } catch (e) { blocked = true; }
  check('学生调用 requireRole(\'teacher\') 被拦截', blocked);
  check('被拦截后已安排跳转到学生主页', navigated === '/student/exams.html', 'navigated=' + navigated);

  // 错误密码 → 应抛出 ApiError 而不是静默成功
  let errCode = null;
  try { await API.post('/api/auth/login', { username: 'teacher1', password: 'wrongpass' }); }
  catch (e) { errCode = e.code; }
  check('错误密码抛出 ApiError(401)', errCode === 401, 'code=' + errCode);

  console.log('\n===== 前端链路: 通过 ' + pass + ' / 失败 ' + fail + ' =====');
  process.exit(fail === 0 ? 0 : 1);
})().catch(function (e) {
  console.error('\n未捕获异常: ' + e.name + ' code=' + e.code + ' msg=' + e.message);
  console.error(e.stack);
  process.exit(1);
});