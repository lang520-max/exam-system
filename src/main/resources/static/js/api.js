/* ============================================================================
 *  api.js —— 全站共享的前端基础层
 *
 *  做四件事：
 *    1. 统一 fetch 封装：自动带 JWT、自动解 {code,message,data} 信封、自动处理 401
 *    2. Token 与当前用户的存取（localStorage）
 *    3. 页面级守卫：未登录跳登录页、角色不符跳自己的主页
 *    4. 一些到处都要用的小工具：HTML 转义、时间格式化、轻提示
 *
 *  为什么 token 放 localStorage 而不是 Cookie：
 *  后端是 JWT 无状态鉴权，不依赖 Cookie；用 localStorage 可以避免 CSRF
 *  （浏览器不会自动携带），也便于将来把前端部署到别的域名。
 * ========================================================================== */

(function (global) {
  'use strict';

  var TOKEN_KEY = 'exam.accessToken';
  var REFRESH_KEY = 'exam.refreshToken';
  var USER_KEY = 'exam.user';

  /* ---------------------------------------------------------------- 存储 */

  var Store = {
    getToken: function () { return localStorage.getItem(TOKEN_KEY); },
    getRefresh: function () { return localStorage.getItem(REFRESH_KEY); },
    getUser: function () {
      try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null'); }
      catch (e) { return null; }
    },
    save: function (loginVo) {
      localStorage.setItem(TOKEN_KEY, loginVo.accessToken);
      if (loginVo.refreshToken) { localStorage.setItem(REFRESH_KEY, loginVo.refreshToken); }
      localStorage.setItem(USER_KEY, JSON.stringify({
        userId: loginVo.userId,
        username: loginVo.username,
        role: loginVo.role,
        realName: loginVo.realName
      }));
    },
    clear: function () {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(REFRESH_KEY);
      localStorage.removeItem(USER_KEY);
    }
  };

  /* ------------------------------------------------------------ 错误类型 */

  /** 业务/HTTP 失败。带 code 与 message，页面可据此给不同提示。 */
  function ApiError(code, message) {
    this.name = 'ApiError';
    this.code = code;
    this.message = message || '请求失败';
  }
  ApiError.prototype = Object.create(Error.prototype);

  /* ---------------------------------------------------------------- 请求 */

  var BASE = '';   // 与页面同源，留空即可

  function buildUrl(path) {
    if (/^https?:\/\//.test(path)) { return path; }
    return BASE + path;
  }

  /**
   * 核心请求方法。
   *
   * @param {string} method  HTTP 方法
   * @param {string} path    形如 /api/exams
   * @param {object} [body]  请求体（自动 JSON 序列化）
   * @param {object} [opts]  { headers:{}, raw:true, redirectOn401:false, timeoutMs:number }
   *   `redirectOn401:false`：401 按业务结果抛给调用方，不清会话、不跳登录页
   *   （登录/注册接口专用 —— 那里的 401 就是「口令不对」）。
   */
  function request(method, path, body, opts) {
    opts = opts || {};
    var headers = Object.assign({ 'Accept': 'application/json' }, opts.headers || {});
    if (body !== undefined && body !== null) {
      headers['Content-Type'] = 'application/json;charset=UTF-8';
    }
    var token = Store.getToken();
    if (token) { headers['Authorization'] = 'Bearer ' + token; }

    var timeoutMs = Number(opts.timeoutMs);
    var controller = (timeoutMs > 0 && global.AbortController)
      ? new global.AbortController() : null;
    var fetchOptions = {
      method: method,
      headers: headers,
      body: (body === undefined || body === null) ? undefined : JSON.stringify(body)
    };
    if (controller) { fetchOptions.signal = controller.signal; }

    var responsePromise = fetch(buildUrl(path), fetchOptions).then(function (res) {
      // 401：token 缺失/过期 → 清掉本地状态并回登录页
      if (res.status === 401) {
        // `redirectOn401:false` 的意思是「这个 401 是**业务结果**，不是登录态失效」：
        // 登录接口对错误口令返回的正是 401，此时既不该清本地会话
        // （另一个标签页可能还登录着），也不该跳转（会跳向登录页自己，
        // 把刚要显示的错误提示一起抹掉），交给调用方自己提示。
        if (opts.redirectOn401 !== false) {
          Store.clear();
          goLogin();
        }
        return res.text().then(function (t) {
          throw new ApiError(401, extractMessage(t) || '登录已过期，请重新登录');
        });
      }

      var contentType = res.headers.get('Content-Type') || '';
      if (contentType.indexOf('application/json') === -1) {
        // 非 JSON（例如被网关拦下的 HTML 错误页）
        return res.text().then(function (t) {
          if (!res.ok) { throw new ApiError(res.status, 'HTTP ' + res.status); }
          return opts.raw ? t : null;
        });
      }

      return res.json().then(function (json) {
        if (!res.ok) {
          throw new ApiError(json && json.code ? json.code : res.status,
            (json && json.message) || ('HTTP ' + res.status));
        }
        // 后端统一信封 { code, message, data }
        if (json && typeof json.code !== 'undefined') {
          if (json.code !== 200) { throw new ApiError(json.code, json.message); }
          return json.data;
        }
        return json;
      });
    });

    var timeoutId = null;
    if (timeoutMs > 0) {
      var timeoutPromise = new Promise(function (resolve, reject) {
        timeoutId = global.setTimeout(function () {
          reject(new ApiError('TIMEOUT', '请求超时，请重试'));
          if (controller) { controller.abort(); }
        }, timeoutMs);
      });
      responsePromise = Promise.race([responsePromise, timeoutPromise]);
    }

    return responsePromise.then(function (result) {
      if (timeoutId !== null) { global.clearTimeout(timeoutId); }
      return result;
    }, function (err) {
      if (timeoutId !== null) { global.clearTimeout(timeoutId); }
      throw err;
    });
  }

  function extractMessage(text) {
    try {
      var o = JSON.parse(text);
      return o && o.message;
    } catch (e) { return null; }
  }

  /* ---------------------------------------------------------------- 工具 */

  /** HTML 转义：所有来自后端的文本渲染进 innerHTML 前都要过一遍，防 XSS */
  function esc(s) {
    if (s === null || s === undefined) { return ''; }
    return String(s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  /** 秒 → mm:ss 或 hh:mm:ss */
  function fmtDuration(sec) {
    sec = Math.max(0, Math.floor(sec || 0));
    var h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
    var pad = function (n) { return n < 10 ? '0' + n : '' + n; };
    return (h > 0 ? pad(h) + ':' : '') + pad(m) + ':' + pad(s);
  }

  /** 时间戳/字符串 → yyyy-MM-dd HH:mm */
  function fmtTime(v) {
    if (!v) { return '-'; }
    var d = (typeof v === 'number') ? new Date(v) : new Date(String(v).replace(/-/g, '/').replace('T', ' '));
    if (isNaN(d.getTime())) { return String(v); }
    var pad = function (n) { return n < 10 ? '0' + n : '' + n; };
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
      + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  /** 生成幂等键（交卷接口要求 X-Request-Id） */
  function uuid() {
    if (global.crypto && crypto.randomUUID) { return crypto.randomUUID(); }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      var r = Math.random() * 16 | 0;
      var v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  }

  /** 轻提示（右下角浮层，2.6 秒自动消失） */
  function toast(message, type) {
    var box = document.getElementById('toast-box');
    if (!box) {
      box = document.createElement('div');
      box.id = 'toast-box';
      document.body.appendChild(box);
    }
    var el = document.createElement('div');
    el.className = 'toast toast-' + (type || 'info');
    el.textContent = message;
    box.appendChild(el);
    setTimeout(function () { el.classList.add('toast-out'); }, 2200);
    setTimeout(function () { if (el.parentNode) { el.parentNode.removeChild(el); } }, 2700);
  }

  /* ------------------------------------------------------------ 页面守卫 */

  function goLogin() {
    // ★ 已经在登录页就不要再跳。
    //   401 一律走这里（见 request()），而登录页自己也会收到 401 ——
    //   登录接口对错误口令返回的就是 401。于是「密码错一次」会把登录页
    //   跳向 `login.html?redirect=%2Flogin.html`：表单被清空、错误提示被这次
    //   导航抹掉，用户看到的是「点了登录没有任何反应」（Bug 2），
    //   而且这个自指的 redirect 会让下一次**成功**的登录也跳回登录页（Bug 1）。
    //   两个 bug 同一个根因，都从这一行堵住。
    if (/\/login\.html$/i.test(location.pathname)) { return; }
    var here = location.pathname + location.search;
    location.href = '/login.html?redirect=' + encodeURIComponent(here);
  }

  function homeFor(role) {
    // role: 0 管理员 1 教师 2 学生
    return (role === 0 || role === 1) ? '/teacher/exams.html' : '/student/exams.html';
  }

  /** 需要登录；返回当前用户。未登录则跳转并抛错阻断后续代码。 */
  function requireLogin() {
    var user = Store.getUser();
    if (!user || !Store.getToken()) {
      goLogin();
      throw new ApiError(401, '未登录');
    }
    return user;
  }

  /**
   * 需要指定角色（'teacher' | 'student' | 'admin'）。
   * 角色不符时跳到自己该去的首页，而不是干瞪眼。
   */
  function requireRole(role) {
    var user = requireLogin();
    var ok = false;
    if (role === 'admin') { ok = user.role === 0; }
    else if (role === 'teacher') { ok = user.role === 0 || user.role === 1; }
    else if (role === 'student') { ok = user.role === 2; }
    if (!ok) {
      toast('你的角色无权访问该页面', 'warn');
      setTimeout(function () { location.href = homeFor(user.role); }, 900);
      throw new ApiError(403, '角色不符');
    }
    return user;
  }

  /* ---------------------------------------------------- 预期内的守卫异常 */

  /**
   * 页面守卫抛出的 ApiError 属于**控制流**，不是故障。
   *
   * <p>{@link requireLogin} / {@link requireRole} 用「抛异常」让调用它的页面脚本
   * 立刻停止执行：未登录时已经跳登录页了，角色不符时也已经提示 + 安排跳转了。
   * 但页面是在顶层直接调用它们的（`Auth.requireRole('teacher');`），
   * 没有人 catch，浏览器就会记一条 `Uncaught ApiError` ——
   * 学生误入教师页、未登录直接打开受保护页时控制台都会出现，
   * 看着像未处理缺陷，实际是**已经处理过的跳转**（Bug 7 / Bug 8）。
   *
   * <p>这里把这两个 code 标记为已处理：`preventDefault()` 阻止浏览器默认的
   * 未捕获上报（等价于 `window.onerror` 返回 true）。只吞 401/403 这一类
   * 「守卫码」，其它异常照旧完整暴露。
   */
  function isExpectedGuardError(e) {
    var err = e && (e.error || e.reason);
    return err instanceof ApiError && (err.code === 401 || err.code === 403);
  }

  window.addEventListener('error', function (e) {
    if (isExpectedGuardError(e)) { e.preventDefault(); }
  });
  window.addEventListener('unhandledrejection', function (e) {
    if (isExpectedGuardError(e)) { e.preventDefault(); }
  });

  /**
   * 退出登录。
   *
   * JWT 是无状态的，服务端没有「会话」可销毁，能做的是把 refresh token
   * 拉黑（`POST /api/auth/logout`），让它无法再换出新的 access token。
   * 已经签发的 access token 在剩余有效期内仍可用 —— 这是无状态 JWT 的固有代价。
   *
   * **不等待这个请求**：登出必须立刻生效，不能因为网络抖动让用户
   * 「点了没反应」。即使请求失败，本地 Token 也照清不误。
   */
  function logout() {
    var refresh = Store.getRefresh();
    if (refresh) {
      try {
        fetch(BASE + '/api/auth/logout', {
          method: 'POST',
          headers: { 'X-Refresh-Token': refresh },
          // keepalive：下面紧接着就 location.href 跳走，普通 fetch 会被这次导航取消。
          // ⚠ 别把它当成「登出请求永远 inflight」的解药：本地做过 A/B
          //   （.deploy-tools/logout-ab.js，用服务端日志数送达次数），
          //   带不带 keepalive 都是 **3/3 送达**，服务端早就处理完了。
          //   那个「inflight」是**已经导航走的那个页面在 devtools 里的显示**，
          //   不是请求没到。保留它是**防御性**的：慢网、HTTPS 或浏览器策略变化时，
          //   只有它保证请求在页面卸载后还能跑完。
          keepalive: true
        }).catch(function () { /* 服务端没收到也无妨，本地清理照做 */ });
      } catch (e) { /* 忽略：不能让登出被异常卡住 */ }
    }
    Store.clear();
    location.href = '/login.html';
  }

  /* -------------------------------------------------------- 工作台侧边栏渲染 */

  /**
   * 取名字的第一个字形，用作头像里的字符。
   * 用 Array.from 逐个取「码点」而不是 charAt，避免把代理对（emoji 等）切成半个字符。
   */
  function firstGlyph(name) {
    var s = String(name || '').trim();
    if (!s) { return '·'; }
    try { return Array.from(s)[0]; } catch (e) { return s.charAt(0); }
  }

  function roleTextOf(role) {
    return role === 0 ? '管理员' : role === 1 ? '教师' : '学生';
  }

  /**
   * 渲染工作台左侧边栏。
   *
   * 外观取向是「学术纸本」：菜单用 **01/02 编号 + 文字**，不用图标；
   * 选中态是左侧一条赭石竖线，不做浅底胶囊。编号与脚注本身就是装饰
   * （「去 AI 味」要求装饰要有意义，而不是空洞的几何图形）。
   *
   * 从「顶部导航条」改成「左侧边栏」时保留了全部既有 class 名
   * （.nav-inner / .nav-brand / .nav-links / .nav-user …），
   * 因此各页面的骨架与调用方式都不用动，只是外观重排。
   *
   * @param {'teacher'|'student'} role
   * @param {string} active 当前高亮的菜单 key
   */
  function renderNav(role, active) {
    // ★ 允许传数字角色（0 管理员 / 1 教师 / 2 学生）。
    //   `/face-enroll.html` 传的就是 `Store.getUser().role` 这个数字，
    //   而旧代码只认字符串 'teacher' / 'student'：数字 1 不等于 'teacher'，
    //   于是掉进 else 分支 —— **教师打开人脸录入页看到的是学生菜单**
    //   （顶栏面包屑也跟着变成「学习中心」），见 Bug 9。
    //   两种写法都接受，调用方不用改。
    if (role === 0 || role === 1) { role = 'teacher'; }
    else if (role === 2) { role = 'student'; }

    var user = Store.getUser() || {};
    var groups = role === 'teacher'
      ? [['教学管理', [
          ['exams', '考试管理', '/teacher/exams.html'],
          ['paper-new', '智能组卷', '/teacher/paper-new.html'],
          ['import', '导入试卷', '/teacher/import.html'],
          ['questions', '题库管理', '/teacher/questions.html']]]]
      : [['学习中心', [
          ['exams', '我的考试', '/student/exams.html'],
          ['scores', '我的成绩', '/student/scores.html'],
          ['practice', '自由练习', '/student/practice.html']]]];

    // 有页面但不在侧栏菜单里的入口（目前只有顶栏用户菜单里的「人脸录入」）：
    // 只用来给面包屑补一个当前页名。
    var OFF_MENU_PAGES = { 'face-enroll': '人脸录入' };

    var display = user.realName || user.username || '';
    var initial = firstGlyph(display);
    var roleText = roleTextOf(user.role);

    // ---------- 侧栏 ----------
    var navHtml = '<div class="nav-inner">'
      + '<div class="nav-brand">在线考试系统</div>'
      + '<nav class="nav-links">';
    var no = 0;
    var activeLabel = '';
    var activeGroup = groups[0][0];
    groups.forEach(function (group) {
      navHtml += '<div class="nav-group-title">' + esc(group[0]) + '</div>';
      group[1].forEach(function (m) {
        no++;
        var isActive = (m[0] === active);
        if (isActive) {
          activeLabel = m[1];
          activeGroup = group[0];
        }
        navHtml += '<a href="' + m[2] + '"'
          + (isActive ? ' class="active" aria-current="page"' : '')
          + '><span class="nav-no">' + (no < 10 ? '0' + no : no) + '</span>'
          + '<span>' + esc(m[1]) + '</span></a>';
      });
    });
    // 不在侧栏菜单里的页面（「人脸录入」是顶栏用户菜单里的入口）也要有正确的
    // 当前页名，否则面包屑会退化成「教学管理 / 教学管理」。
    if (!activeLabel) { activeLabel = OFF_MENU_PAGES[active] || activeGroup; }
    navHtml += '</nav>'
      + '<div class="nav-foot">v2.0.0 · 教学演示环境<br>四川专升本题库（本地 H2）</div>'
      + '</div>';

    var nav = document.getElementById('nav');
    if (nav) {
      nav.innerHTML = navHtml;
    }

    // ---------- 顶栏（面包屑 + 用户入口）----------
    //
    // 由 JS 注入而不是写进每个页面：6 个页面的骨架与 <script> 顺序都不用动，
    // 面包屑也能从「当前菜单」自动推导出来（各页面只是传一个 active key）。
    var topbar = document.getElementById('topbar');
    if (!topbar) {
      topbar = document.createElement('header');
      topbar.id = 'topbar';
      if (nav && nav.parentNode) {
        nav.parentNode.insertBefore(topbar, nav.nextSibling);   // 紧跟 #nav，位于 .container 之前
      } else {
        document.body.insertBefore(topbar, document.body.firstChild);
      }
    }
    topbar.innerHTML = '<div class="topbar-inner">'
      + '<nav class="breadcrumb" aria-label="面包屑">'
      + '<span>工作台</span><span class="sep">/</span>'
      + '<span>' + esc(activeGroup) + '</span><span class="sep">/</span>'
      + '<b>' + esc(activeLabel || activeGroup) + '</b>'
      + '</nav>'
      + '<div class="topbar-user">'
      + '<span class="topbar-role">' + esc(roleText) + '</span>'
      + '<button type="button" class="user-btn" id="user-btn" aria-haspopup="true" aria-expanded="false">'
      + '<span class="nav-avatar">' + esc(initial) + '</span>'
      + '<span class="nav-name">' + esc(display) + '</span>'
      + '<svg class="user-caret" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9l6 6 6-6"/></svg>'
      + '</button>'
      + '<div class="user-menu" id="user-menu" hidden>'
      + '<div class="user-menu-head"><b>' + esc(display) + '</b>'
      + '<span>' + esc(user.username || '') + '</span></div>'
      + '<button type="button" class="user-menu-item" id="nav-profile">账号信息</button>'
      + '<button type="button" class="user-menu-item" id="nav-password">修改密码</button>'
      + '<button type="button" class="user-menu-item" id="nav-face">人脸录入</button>'
      + '<button type="button" class="user-menu-item danger" id="nav-logout">退出登录</button>'
      + '</div>'
      + '</div></div>';

    var userBtn = document.getElementById('user-btn');
    var menu = document.getElementById('user-menu');

    function closeMenu() {
      if (menu) { menu.hidden = true; }
      if (userBtn) { userBtn.setAttribute('aria-expanded', 'false'); }
    }

    if (userBtn && menu) {
      userBtn.addEventListener('click', function (ev) {
        ev.stopPropagation();
        var willOpen = menu.hidden;
        menu.hidden = !willOpen;
        userBtn.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
      });
      // 点空白处收起；点菜单内部不收起，交给按钮自己处理
      document.addEventListener('click', function (ev) {
        if (menu.hidden) { return; }
        if (menu.contains(ev.target) || userBtn.contains(ev.target)) { return; }
        closeMenu();
      });
    }

    var logoutBtn = document.getElementById('nav-logout');
    // 人脸录入：老账号（含 4 个演示账号）靠它补上底片，否则新门禁会让它们考不了试
    var faceBtn = document.getElementById('nav-face');
    if (faceBtn) {
      faceBtn.addEventListener('click', function () {
        closeMenu();
        location.href = '/face-enroll.html';
      });
    }
    if (logoutBtn) {
      logoutBtn.addEventListener('click', function () { closeMenu(); confirmLogout(); });
    }
    var profileBtn = document.getElementById('nav-profile');
    if (profileBtn) {
      profileBtn.addEventListener('click', function () { closeMenu(); showProfile(); });
    }
    var passwordBtn = document.getElementById('nav-password');
    if (passwordBtn) {
      passwordBtn.addEventListener('click', function () { closeMenu(); showPasswordDialog(); });
    }
  }

  /* ------------------------------------------------------------ 通用弹窗 */

  function closeModal() {
    var mask = document.getElementById('app-modal-mask');
    if (mask && mask.parentNode) { mask.parentNode.removeChild(mask); }
    document.removeEventListener('keydown', onModalKey);
  }

  function onModalKey(ev) {
    if (ev.key === 'Escape') { closeModal(); }
  }

  /** 打开一个小弹窗（右上角关闭、点遮罩关闭、Esc 关闭） */
  function openModal(innerHtml) {
    closeModal();
    var mask = document.createElement('div');
    mask.className = 'modal-mask';
    mask.id = 'app-modal-mask';
    mask.innerHTML = '<div class="modal modal-sm">' + innerHtml + '</div>';
    document.body.appendChild(mask);
    mask.addEventListener('click', function (ev) { if (ev.target === mask) { closeModal(); } });
    document.addEventListener('keydown', onModalKey);
    return mask;
  }

  /**
   * 应用内的确认弹窗，返回 Promise&lt;boolean&gt;。
   *
   * <p><b>为什么要有它</b>：以前「删除题目 / 发布 / 结束考试 / 未答完就交卷」
   * 用的是浏览器原生的 `confirm()` —— 它是**浏览器画的**，样式与本系统无关、
   * 会阻塞整个 JS 线程，而且**不在 DOM 里**：只看 DOM 的自动化探测根本看不到它，
   * 于是「有确认」会被误判成「无确认、直接删除」（第二轮报告 Bug 16/17 就是这么来的）。
   * 「退出登录」早就改成了这套 `.modal-sm` 弹窗，这里把它抽成通用能力。</p>
   *
   * <p>点遮罩、按 Esc、点「取消」都算取消；返回 false。</p>
   */
  function confirmDialog(opts) {
    opts = opts || {};
    return new Promise(function (resolve) {
      var mask = openModal(
        '<h3 class="modal-title">' + esc(opts.title || '请确认') + '</h3>'
        + '<p class="modal-text">' + esc(opts.text || '') + '</p>'
        + '<div class="modal-footer">'
        + '<button type="button" class="btn btn-ghost" data-act="cancel">'
        + esc(opts.cancelText || '取消') + '</button>'
        + '<button type="button" class="btn' + (opts.danger ? ' btn-danger' : '') + '" data-act="ok">'
        + esc(opts.okText || '确定') + '</button>'
        + '</div>');

      var done = false;
      function finish(value) {
        if (done) { return; }
        done = true;
        document.removeEventListener('keydown', onKey);
        closeModal();
        resolve(value);
      }
      function onKey(ev) { if (ev.key === 'Escape') { finish(false); } }

      mask.querySelector('[data-act="cancel"]').addEventListener('click', function () { finish(false); });
      mask.querySelector('[data-act="ok"]').addEventListener('click', function () { finish(true); });
      // openModal 自己会处理「点遮罩 / Esc → closeModal」，但那两条路径不会回调到这里；
      // 补上监听，保证 Promise 一定被兑现（否则调用方的 .then 永远不执行）
      mask.addEventListener('click', function (ev) { if (ev.target === mask) { finish(false); } });
      document.addEventListener('keydown', onKey);
    });
  }

  /**
   * 退出登录前的二次确认。
   * 之前是一个点下去就立刻跳走的文字链接——误点等于丢失当前页面状态。
   */
  function confirmLogout() {
    var mask = openModal(
      '<h3 class="modal-title">退出登录</h3>'
      + '<p class="modal-text">退出后本机不再保留登录状态，下次访问需要重新输入用户名与密码。</p>'
      + '<div class="modal-footer">'
      + '<button type="button" class="btn btn-ghost" data-act="cancel">取消</button>'
      + '<button type="button" class="btn btn-danger" data-act="ok">退出登录</button>'
      + '</div>');
    mask.querySelector('[data-act="cancel"]').addEventListener('click', closeModal);
    mask.querySelector('[data-act="ok"]').addEventListener('click', function () {
      closeModal();
      logout();
    });
  }

  /** 账号信息：只展示本地已有的身份信息，不发请求 */
  function showProfile() {
    var user = Store.getUser() || {};
    var mask = openModal(
      '<h3 class="modal-title">账号信息</h3>'
      + '<div class="info-grid">'
      + '<div class="info-cell"><span>用户名</span><b>' + esc(user.username || '—') + '</b></div>'
      + '<div class="info-cell"><span>姓名</span><b>' + esc(user.realName || '—') + '</b></div>'
      + '<div class="info-cell"><span>角色</span><b>' + esc(roleTextOf(user.role)) + '</b></div>'
      + '<div class="info-cell"><span>登录方式</span><b>JWT 无状态</b></div>'
      + '</div>'
      + '<div class="modal-footer">'
      + '<button type="button" class="btn btn-ghost" data-act="close">关闭</button>'
      + '<button type="button" class="btn btn-danger" data-act="logout">退出登录</button>'
      + '</div>');
    mask.querySelector('[data-act="close"]').addEventListener('click', closeModal);
    mask.querySelector('[data-act="logout"]').addEventListener('click', function () {
      closeModal();
      confirmLogout();
    });
  }

  /**
   * 自助修改密码。
   *
   * <p>以前登录页写着「忘记密码请联系教务」——泄露了也只能等人工。
   * 现在用户菜单里可以直接改：填原密码 + 新密码 + 确认。</p>
   *
   * <p>服务端改完会**拉黑当前 refresh token**，所以这里成功后主动清本地会话并回登录页；
   * 不这么做的话页面还留着一个马上要被拒的登录态，用户会看到一堆莫名其妙的重定向。</p>
   */
  function showPasswordDialog() {
    var mask = openModal(
      '<h3 class="modal-title">修改密码</h3>'
      + '<p class="modal-text">修改成功后需要重新登录（当前登录凭证会立即失效）。</p>'
      + '<div class="form-row" id="row-pw-old"><label>原密码</label>'
      + '<input type="password" id="pw-old" autocomplete="current-password" maxlength="50" placeholder="请输入当前密码">'
      + '<div class="field-error" id="err-pw-old"></div></div>'
      + '<div class="form-row" id="row-pw-new"><label>新密码</label>'
      + '<input type="password" id="pw-new" autocomplete="new-password" maxlength="50" placeholder="6-50 位">'
      + '<div class="field-error" id="err-pw-new"></div></div>'
      + '<div class="form-row" id="row-pw-rep"><label>确认新密码</label>'
      + '<input type="password" id="pw-rep" autocomplete="new-password" maxlength="50" placeholder="再输一次">'
      + '<div class="field-error" id="err-pw-rep"></div></div>'
      + '<div class="modal-footer">'
      + '<button type="button" class="btn btn-ghost" data-act="cancel">取消</button>'
      + '<button type="button" class="btn" data-act="ok">确认修改</button>'
      + '</div>');

    function fieldError(rowId, errId, message) {
      var row = document.getElementById(rowId);
      var err = document.getElementById(errId);
      if (row) { row.classList.add('invalid'); }
      if (err) { err.textContent = message; }
    }
    function clearErrors() {
      [['row-pw-old', 'err-pw-old'], ['row-pw-new', 'err-pw-new'], ['row-pw-rep', 'err-pw-rep']]
        .forEach(function (pair) {
          var row = document.getElementById(pair[0]);
          var err = document.getElementById(pair[1]);
          if (row) { row.classList.remove('invalid'); }
          if (err) { err.textContent = ''; }
        });
    }

    mask.querySelector('[data-act="cancel"]').addEventListener('click', closeModal);
    mask.querySelector('[data-act="ok"]').addEventListener('click', function () {
      clearErrors();
      var oldPwd = document.getElementById('pw-old').value;
      var newPwd = document.getElementById('pw-new').value;
      var repPwd = document.getElementById('pw-rep').value;

      // 字段级校验：与注册同一套规则（6-50 位），并且要求两次输入一致
      var bad = false;
      if (!oldPwd) { fieldError('row-pw-old', 'err-pw-old', '请输入原密码'); bad = true; }
      if (!newPwd) { fieldError('row-pw-new', 'err-pw-new', '请输入新密码'); bad = true; }
      else if (newPwd.length < 6 || newPwd.length > 50) {
        fieldError('row-pw-new', 'err-pw-new', '新密码需要 6-50 位'); bad = true;
      } else if (newPwd === oldPwd) {
        fieldError('row-pw-new', 'err-pw-new', '新密码不能与原密码相同'); bad = true;
      }
      if (!repPwd) { fieldError('row-pw-rep', 'err-pw-rep', '请再输入一次新密码'); bad = true; }
      else if (repPwd !== newPwd) { fieldError('row-pw-rep', 'err-pw-rep', '两次输入的新密码不一致'); bad = true; }
      if (bad) {
        var firstInvalid = mask.querySelector('.form-row.invalid input');
        if (firstInvalid) { firstInvalid.focus(); }
        return;
      }

      var btn = mask.querySelector('[data-act="ok"]');
      btn.disabled = true;
      request('PUT', '/api/auth/password',
        { oldPassword: oldPwd, newPassword: newPwd },
        { headers: { 'X-Refresh-Token': Store.getRefresh() || '' } })
        .then(function () {
          closeModal();
          toast('密码已修改，请用新密码重新登录', 'success');
          Store.clear();
          setTimeout(function () { location.href = '/login.html'; }, 1200);
        })
        .catch(function (err) {
          btn.disabled = false;
          fieldError('row-pw-old', 'err-pw-old', err.message);
          toast(err.message, 'error');
        });
    });

    var first = document.getElementById('pw-old');
    if (first) { first.focus(); }
  }

  /* ------------------------------------------------- 手机端：表格 → 竖排卡片 */

  /**
   * 把表头写进每个单元格的 data-label，供 CSS 在窄屏把表格显示成竖排卡片。
   *
   * <p>为什么需要它：CSS 没法知道「这一格是哪个字段」，卡片布局要靠
   * {@code td::before { content: attr(data-label) } } 才能显示字段名。
   * 页面全都是用 innerHTML 重画表格的，所以这里零页面改动：扫描现有
   * {@code .table-wrap} 里的表格，并用 MutationObserver 跟上后续重渲染。</p>
   *
   * <p>注意两点：① 跨列的「暂无数据」行不做卡片化（加 .row-plain）；
   * ② 观察器只监听 childList（不监听 attributes），而本函数只改属性、不改节点，
   * 所以不会自激循环。</p>
   */
  function labelTableCells() {
    var wraps = document.querySelectorAll('.table-wrap');
    Array.prototype.forEach.call(wraps, function (wrap) {
      var table = wrap.querySelector('table');
      if (!table) { return; }
      var heads = table.querySelectorAll('thead th');
      if (!heads.length) { return; }
      var labels = Array.prototype.map.call(heads, function (th) {
        return (th.textContent || '').trim();
      });
      Array.prototype.forEach.call(table.querySelectorAll('tbody tr'), function (tr) {
        var cells = tr.children;
        var plain = cells.length !== labels.length;
        for (var i = 0; i < cells.length; i++) {
          if (cells[i].colSpan > 1) { plain = true; }
        }
        if (plain !== tr.classList.contains('row-plain')) {
          tr.classList.toggle('row-plain', plain);
        }
        for (var j = 0; j < cells.length; j++) {
          if (plain) {
            if (cells[j].hasAttribute('data-label')) { cells[j].removeAttribute('data-label'); }
          } else if (cells[j].getAttribute('data-label') !== labels[j]) {
            cells[j].setAttribute('data-label', labels[j]);
          }
          // 超长文本单元格（题干/答案/解析常有几百字）标记出来：
          // 手机端 CSS 会把它截到 6 行，点一下展开（桌面端这个类没有任何样式）
          var long = !plain && (cells[j].textContent || '').trim().length > 140;
          if (long !== cells[j].classList.contains('cell-long')) {
            cells[j].classList.toggle('cell-long', long);
          }
        }
      });
    });
  }

  var labelPending = false;
  function scheduleLabelTableCells() {
    if (labelPending) { return; }
    labelPending = true;
    setTimeout(function () { labelPending = false; labelTableCells(); }, 30);
  }

  /* 手机端：点了被截断的长文本单元格就展开/收起（桌面端没有截断，这个开关无副作用） */
  document.addEventListener('click', function (e) {
    if (!e.target || !e.target.closest) { return; }
    if (e.target.closest('button, a, input, select, textarea, label')) { return; }
    var td = e.target.closest('.table-wrap td.cell-long');
    if (td) { td.classList.toggle('cell-expanded'); }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', labelTableCells);
  } else {
    labelTableCells();
  }
  if (window.MutationObserver && document.body) {
    new MutationObserver(scheduleLabelTableCells).observe(document.body, { childList: true, subtree: true });
  }

  /* ------------------------------------------------------------ 站点图标 */

  /**
   * 没有 `<link rel="icon">` 时，浏览器会自己去请求 `/favicon.ico`：
   * 这个项目没有这个文件 → 404 / ERR_ABORTED，Network 面板与控制台都是噪音，
   * 而且每次导航都可能重试（Bug 12）。
   *
   * 这里用**内联 data URI** 声明一个图标：政务蓝底 + 白色「考」字，
   * 零额外请求、零额外文件、也不违反「无构建、无依赖」的约定。
   * SVG 里的中文要按 UTF-8 百分号编码（考 = %E8%80%83），否则某些浏览器不认。
   */
  function ensureFavicon() {
    try {
      if (document.querySelector('link[rel~="icon"]')) { return; }
      var link = document.createElement('link');
      link.rel = 'icon';
      link.type = 'image/svg+xml';
      link.href = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E"
        + "%3Crect width='32' height='32' rx='6' fill='%231b3a6b'/%3E"
        + "%3Ctext x='16' y='23' font-size='19' text-anchor='middle' fill='%23ffffff'"
        + " font-family='sans-serif'%3E%E8%80%83%3C/text%3E%3C/svg%3E";
      (document.head || document.documentElement).appendChild(link);
    } catch (e) { /* 图标是纯装饰，失败不影响任何功能 */ }
  }
  ensureFavicon();

  /* ---------------------------------------------------------------- 导出 */

  global.API = {
    get: function (p, o) { return request('GET', p, null, o); },
    post: function (p, b, o) { return request('POST', p, b, o); },
    put: function (p, b, o) { return request('PUT', p, b, o); },
    del: function (p, o) { return request('DELETE', p, null, o); },
    request: request
  };

  global.Store = Store;
  global.ApiError = ApiError;
  global.Auth = {
    requireLogin: requireLogin,
    requireRole: requireRole,
    logout: logout,
    homeFor: homeFor,
    goLogin: goLogin
  };
  global.UI = {
    esc: esc,
    fmtDuration: fmtDuration,
    fmtTime: fmtTime,
    uuid: uuid,
    toast: toast,
    renderNav: renderNav,
    confirm: confirmDialog,
    labelTableCells: labelTableCells
  };
})(window);
