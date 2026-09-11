/* ============================================================================
   在线考试 / 题库系统 —— 页面脚本（原生 JS，无构建步骤）
   职责划分：页面内容由 Thymeleaf 服务端渲染，这里只负责「动作」——
   调 REST 接口、倒计时、收集答题卡、交卷。
   ========================================================================== */

/* ------------------------------ 基础工具 ------------------------------ */

/** 提示条 */
function showTip(el, message, ok) {
  if (!el) { return; }
  el.textContent = message || '';
  el.className = 'tip ' + (ok ? 'ok' : 'err');
}

/**
 * 统一调用 REST 接口。
 * 后端所有接口都返回 { code, message, data } 结构，code === 0 才算成功。
 */
async function api(method, url, data) {
  var options = { method: method, headers: { 'Accept': 'application/json' } };
  if (data !== undefined && data !== null) {
    options.headers['Content-Type'] = 'application/json;charset=UTF-8';
    options.body = JSON.stringify(data);
  }
  var resp;
  try {
    resp = await fetch(url, options);
  } catch (e) {
    return { code: -1, message: '网络异常，请检查服务是否还在运行' };
  }
  // 未登录：拦下的响应也是统一结构，这里顺手把用户送回登录页
  if (resp.status === 401) {
    window.location.href = '/login';
    return { code: 401, message: '请先登录后再操作' };
  }
  var text = await resp.text();
  try {
    return JSON.parse(text);
  } catch (e) {
    return { code: resp.status, message: '服务返回了非预期内容（HTTP ' + resp.status + '）' };
  }
}

function escapeHtml(value) {
  if (value === null || value === undefined) { return ''; }
  return String(value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function pad2(n) { return (n < 10 ? '0' : '') + n; }

/* ------------------------------ 登录 / 注册 ------------------------------ */

function logout() {
  api('POST', '/api/auth/logout').then(function () {
    window.location.href = '/login';
  });
}

function bindLoginForm() {
  var form = document.getElementById('login-form');
  if (!form) { return; }
  var tip = document.getElementById('tip');
  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    var username = form.username.value.trim();
    var password = form.password.value;
    var res = await api('POST', '/api/auth/login', { username: username, password: password });
    if (res.code === 0) {
      showTip(tip, '登录成功，正在进入系统…', true);
      window.location.href = res.data.role === 'TEACHER' ? '/teacher/questions' : '/student/exams';
    } else {
      showTip(tip, res.message, false);
    }
  });
}

function bindRegisterForm() {
  var form = document.getElementById('register-form');
  if (!form) { return; }
  var tip = document.getElementById('tip');
  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    var res = await api('POST', '/api/auth/register', {
      username: form.username.value.trim(),
      password: form.password.value,
      nickname: form.nickname.value.trim()
    });
    if (res.code === 0) {
      showTip(tip, '注册成功，正在前往登录页…', true);
      setTimeout(function () { window.location.href = '/login'; }, 800);
    } else {
      showTip(tip, res.message, false);
    }
  });
}

/* ------------------------------ 教师：题库管理 ------------------------------ */

function bindQuestionAdmin() {
  var form = document.getElementById('question-form');
  if (!form) { return; }
  var tip = document.getElementById('question-tip');
  var typeSelect = document.getElementById('q-type');

  // 判断题没有选项，切换题型时把选项区收起来，避免填了也用不上
  function toggleOptions() {
    var isJudge = typeSelect.value === 'JUDGE';
    document.getElementById('option-block').style.display = isJudge ? 'none' : '';
    document.getElementById('answer-hint').textContent = isJudge
      ? '判断题答案填「对」或「错」（系统会归一化为 A / B）'
      : (typeSelect.value === 'MULTIPLE' ? '多选题答案形如：ABD' : '单选题答案形如：A');
  }
  typeSelect.addEventListener('change', toggleOptions);
  toggleOptions();

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    var payload = {
      type: typeSelect.value,
      subject: form.subject.value.trim(),
      knowledgePoint: form.knowledgePoint.value.trim(),
      content: form.content.value.trim(),
      optionA: form.optionA.value.trim(),
      optionB: form.optionB.value.trim(),
      optionC: form.optionC.value.trim(),
      optionD: form.optionD.value.trim(),
      answer: form.answer.value.trim(),
      score: form.score.value,
      difficulty: form.difficulty.value
    };
    var id = form.id.value;
    var res = id
      ? await api('PUT', '/api/questions/' + id, payload)
      : await api('POST', '/api/questions', payload);

    if (res.code === 0) {
      showTip(tip, id ? '修改成功，页面即将刷新…' : '新增成功，页面即将刷新…', true);
      setTimeout(function () { window.location.reload(); }, 700);
    } else {
      showTip(tip, res.message, false);
    }
  });
}

/** 点「编辑」把当前行数据填进表单 */
function editQuestion(id) {
  api('GET', '/api/questions/' + id).then(function (res) {
    if (res.code !== 0) { alert(res.message); return; }
    var q = res.data;
    var form = document.getElementById('question-form');
    form.id.value = q.id;
    form.type.value = q.type;
    form.subject.value = q.subject || '';
    form.knowledgePoint.value = q.knowledgePoint || '';
    form.content.value = q.content || '';
    form.optionA.value = q.optionA || '';
    form.optionB.value = q.optionB || '';
    form.optionC.value = q.optionC || '';
    form.optionD.value = q.optionD || '';
    form.answer.value = q.answer || '';
    form.score.value = q.score;
    form.difficulty.value = q.difficulty;
    document.getElementById('q-type').dispatchEvent(new Event('change'));
    document.getElementById('form-title').textContent = '编辑题目 #' + q.id;
    document.getElementById('question-tip').textContent = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  });
}

function resetQuestionForm() {
  var form = document.getElementById('question-form');
  form.reset();
  form.id.value = '';
  document.getElementById('form-title').textContent = '新增题目';
  document.getElementById('question-tip').textContent = '';
}

function deleteQuestion(id) {
  if (!window.confirm('确定删除题目 #' + id + ' 吗？已被试卷引用的题目无法删除。')) { return; }
  api('DELETE', '/api/questions/' + id).then(function (res) {
    if (res.code === 0) {
      window.location.reload();
    } else {
      alert(res.message);
    }
  });
}

/* ------------------------------ 教师：试卷管理 ------------------------------ */

function publishPaper(id) {
  api('POST', '/api/papers/' + id + '/publish').then(function (res) {
    alert(res.code === 0 ? '已发布，学生端现在可以看到这场考试' : res.message);
    if (res.code === 0) { window.location.reload(); }
  });
}

function closePaper(id) {
  if (!window.confirm('结束这场考试？学生将无法再开考，历史成绩会保留。')) { return; }
  api('POST', '/api/papers/' + id + '/close').then(function (res) {
    alert(res.code === 0 ? '已结束' : res.message);
    if (res.code === 0) { window.location.reload(); }
  });
}

function deletePaper(id) {
  if (!window.confirm('确定删除这份试卷吗？（已有学生考过的试卷不能删除）')) { return; }
  api('DELETE', '/api/papers/' + id).then(function (res) {
    alert(res.code === 0 ? '删除成功' : res.message);
    if (res.code === 0) { window.location.reload(); }
  });
}

/* ------------------------------ 教师：组卷页 ------------------------------ */

/** 手动组卷：按条件检索题库，勾选后生成试卷 */
function bindPaperNewPage() {
  var searchForm = document.getElementById('bank-search');
  if (!searchForm) { return; }

  async function loadBank() {
    var params = new URLSearchParams();
    params.set('pageSize', '50');
    if (searchForm.keyword.value.trim()) { params.set('keyword', searchForm.keyword.value.trim()); }
    if (searchForm.subject.value.trim()) { params.set('subject', searchForm.subject.value.trim()); }
    if (searchForm.type.value) { params.set('type', searchForm.type.value); }

    var res = await api('GET', '/api/questions?' + params.toString());
    var body = document.getElementById('bank-body');
    if (res.code !== 0) {
      body.innerHTML = '<tr><td colspan="6" class="muted">' + escapeHtml(res.message) + '</td></tr>';
      return;
    }
    if (!res.data.list.length) {
      body.innerHTML = '<tr><td colspan="6" class="muted">没有匹配的题目</td></tr>';
      return;
    }
    var html = '';
    res.data.list.forEach(function (q) {
      html += '<tr>'
        + '<td><input type="checkbox" class="pick" value="' + q.id + '"'
        + ' data-type="' + escapeHtml(q.type) + '" data-score="' + q.score + '"></td>'
        + '<td>' + q.id + '</td>'
        + '<td class="nowrap">' + escapeHtml(q.typeText) + '</td>'
        + '<td>' + escapeHtml(q.content) + '</td>'
        + '<td class="nowrap">' + escapeHtml(q.answer) + '</td>'
        + '<td class="nowrap">' + q.score + '</td>'
        + '</tr>';
    });
    body.innerHTML = html;
    updatePicked();
  }

  function picked() {
    return Array.prototype.slice.call(document.querySelectorAll('.pick:checked'));
  }

  function updatePicked() {
    var boxes = picked();
    var total = boxes.reduce(function (sum, box) {
      return sum + parseFloat(box.getAttribute('data-score'));
    }, 0);
    document.getElementById('picked-count').textContent = boxes.length;
    document.getElementById('picked-score').textContent = total.toFixed(1);
  }

  document.getElementById('bank-search-btn').addEventListener('click', function (event) {
    event.preventDefault();
    loadBank();
  });
  document.getElementById('pick-all').addEventListener('change', function (event) {
    document.querySelectorAll('.pick').forEach(function (box) { box.checked = event.target.checked; });
    updatePicked();
  });
  document.getElementById('bank-body').addEventListener('change', function (event) {
    if (event.target.classList.contains('pick')) { updatePicked(); }
  });

  document.getElementById('manual-form').addEventListener('submit', async function (event) {
    event.preventDefault();
    var form = event.target;
    var tip = document.getElementById('manual-tip');
    var ids = picked().map(function (box) { return Number(box.value); });
    if (!ids.length) { showTip(tip, '请先在左侧勾选至少一道题', false); return; }

    var res = await api('POST', '/api/papers', {
      title: form.title.value.trim(),
      subject: form.subject.value.trim(),
      durationMinutes: Number(form.durationMinutes.value),
      passScore: form.passScore.value,
      questionIds: ids
    });
    if (res.code === 0) {
      showTip(tip, '组卷成功，试卷 #' + res.data.id + ' 已生成草稿，请到「试卷管理」发布', true);
    } else {
      showTip(tip, res.message, false);
    }
  });

  document.getElementById('random-form').addEventListener('submit', async function (event) {
    event.preventDefault();
    var form = event.target;
    var tip = document.getElementById('random-tip');
    var rules = [];
    document.querySelectorAll('.rule-row').forEach(function (row) {
      var count = Number(row.querySelector('.rule-count').value);
      if (count > 0) {
        rules.push({
          type: row.querySelector('.rule-type').value,
          count: count,
          score: row.querySelector('.rule-score').value
        });
      }
    });
    if (!rules.length) { showTip(tip, '请至少填写一条抽题规则', false); return; }

    var res = await api('POST', '/api/papers/random', {
      title: form.title.value.trim(),
      subject: form.subject.value.trim(),
      durationMinutes: Number(form.durationMinutes.value),
      passScore: form.passScore.value,
      rules: rules
    });
    if (res.code === 0) {
      showTip(tip, '随机组卷成功，试卷 #' + res.data.id + ' 总分 ' + res.data.totalScore
        + ' 分、共 ' + res.data.questionCount + ' 题，请到「试卷管理」发布', true);
    } else {
      showTip(tip, res.message, false);
    }
  });

  loadBank();
}

/* ------------------------------ 学生：考试列表 ------------------------------ */

function startExam(paperId) {
  api('POST', '/api/exams/' + paperId + '/start').then(function (res) {
    if (res.code === 0) {
      window.location.href = '/student/exam?recordId=' + res.data.id;
    } else {
      alert(res.message);
    }
  });
}

/* ------------------------------ 学生：答题页 ------------------------------ */

/** 收集答题卡：多选题把勾选的选项排序后拼成 "ABD" */
function collectAnswers() {
  var answers = {};
  document.querySelectorAll('[data-question-id]').forEach(function (box) {
    var questionId = box.getAttribute('data-question-id');
    var type = box.getAttribute('data-type');
    if (type === 'MULTIPLE') {
      var picked = [];
      box.querySelectorAll('input[type=checkbox]:checked').forEach(function (input) {
        picked.push(input.value);
      });
      picked.sort();
      if (picked.length > 0) { answers[questionId] = picked.join(''); }
    } else {
      var checked = box.querySelector('input[type=radio]:checked');
      if (checked) { answers[questionId] = checked.value; }
    }
  });
  return answers;
}

function bindExamPage() {
  var app = document.getElementById('exam-app');
  if (!app) { return; }

  var recordId = app.getAttribute('data-record-id');
  var deadline = parseInt(app.getAttribute('data-deadline'), 10);
  var submitting = false;
  var tip = document.getElementById('tip');
  var answeredEl = document.getElementById('answered-count');

  function refreshAnswered() {
    var boxes = document.querySelectorAll('[data-question-id]');
    var answered = 0;
    boxes.forEach(function (box) {
      if (box.querySelector('input[type=checkbox]:checked, input[type=radio]:checked')) { answered++; }
    });
    answeredEl.textContent = answered;
  }
  document.getElementById('question-list').addEventListener('change', refreshAnswered);
  refreshAnswered();

  async function doSubmit(auto) {
    if (submitting) { return; }
    submitting = true;
    var button = document.getElementById('submit-btn');
    button.disabled = true;
    button.textContent = '正在判分…';

    var res = await api('POST', '/api/exams/submit', {
      recordId: Number(recordId),
      answers: collectAnswers(),
      autoSubmit: !!auto
    });
    if (res.code === 0) {
      showTip(tip, '交卷成功，得分 ' + res.data.score + ' 分，正在打开成绩单…', true);
      window.location.href = '/student/review?recordId=' + recordId;
      return;
    }
    // 已经交过卷（例如开了两个标签页）：直接去成绩单，不要卡在这一页
    if (res.code === 400 && res.message && res.message.indexOf('重复提交') >= 0) {
      window.location.href = '/student/review?recordId=' + recordId;
      return;
    }
    submitting = false;
    button.disabled = false;
    button.textContent = '交卷';
    showTip(tip, res.message, false);
  }

  document.getElementById('submit-btn').addEventListener('click', function () {
    var total = document.querySelectorAll('[data-question-id]').length;
    var answered = Number(answeredEl.textContent);
    var left = total - answered;
    var message = left > 0
      ? '还有 ' + left + ' 道题没作答，未作答按 0 分计。确定交卷吗？'
      : '确定交卷吗？交卷后不能再修改答案。';
    if (window.confirm(message)) { doSubmit(false); }
  });

  // 倒计时：到点自动交卷（服务端会把记录标记为超时）
  if (!isNaN(deadline)) {
    var bar = document.getElementById('countdown-bar');
    var clock = document.getElementById('countdown');
    var timer = setInterval(function () {
      var left = Math.max(0, Math.floor((deadline - Date.now()) / 1000));
      clock.textContent = pad2(Math.floor(left / 3600)) + ':' + pad2(Math.floor(left % 3600 / 60)) + ':' + pad2(left % 60);
      if (left <= 300) { bar.classList.add('urgent'); }
      if (left <= 0) {
        clearInterval(timer);
        clock.textContent = '00:00:00';
        doSubmit(true);
      }
    }, 1000);
  }
}

/* ------------------------------ 启动 ------------------------------ */

document.addEventListener('DOMContentLoaded', function () {
  bindLoginForm();
  bindRegisterForm();
  bindQuestionAdmin();
  bindPaperNewPage();
  bindExamPage();
});
