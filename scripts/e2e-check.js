'use strict';
/*
 * 在线考试与题库系统 —— 真实运行端到端验收
 * 前置：应用已启动（dev 档 H2 文件库）
 * 覆盖：鉴权 → 题库录入 → 随机组卷 → 发布 → 学生考试 → 自动判分（含多选规则）
 *      → 防重复交卷 → 成绩统计 → 越权与答案不外泄 → 页面渲染
 */
const BASE = process.argv[2] || 'http://127.0.0.1:18081';

let pass = 0, fail = 0;
const failures = [];
function check(name, cond, detail) {
  if (cond) { pass++; console.log('  [PASS] ' + name); }
  else { fail++; failures.push(name); console.log('  [FAIL] ' + name + '  -> ' + (detail === undefined ? '' : JSON.stringify(detail))); }
}

function newClient() {
  let cookie = '';
  async function call(method, path, body) {
    const headers = {};
    if (body !== undefined) { headers['Content-Type'] = 'application/json; charset=utf-8'; }
    if (cookie) { headers['Cookie'] = cookie; }
    const res = await fetch(BASE + path, {
      method: method, headers: headers,
      body: body === undefined ? undefined : Buffer.from(JSON.stringify(body), 'utf8'),
      redirect: 'manual'
    });
    const sc = res.headers.getSetCookie ? res.headers.getSetCookie() : [];
    if (sc.length) { cookie = sc.map(c => c.split(';')[0]).join('; '); }
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch (e) { /* HTML 页面 */ }
    return { status: res.status, json: json, text: text };
  }
  return {
    get: (p) => call('GET', p),
    post: (p, b) => call('POST', p, b === undefined ? {} : b),
    put: (p, b) => call('PUT', p, b),
    del: (p) => call('DELETE', p)
  };
}

(async () => {
  const suffix = String(Math.floor(Math.random() * 90000) + 10000);
  // 两个独立科目：
  //   authSubject 只放权限校验用的题，不参与组卷，避免随机抽题结果不确定
  //   subject     放组卷用的 4 道题，题目与答案在脚本里完全可控
  const subject = 'E2E试卷科目' + suffix;
  const authSubject = 'E2E权限科目' + suffix;
  const teacher = newClient(), student = newClient(), other = newClient(), anon = newClient();

  console.log('\n=== 1. 鉴权 ===');
  let r = await anon.get('/api/questions?pageSize=5');
  check('未登录访问题库被 401 拒绝', r.json && r.json.code === 401, r.json);
  r = await anon.get('/api/papers');
  check('未登录访问试卷列表被 401 拒绝', r.json && r.json.code === 401, r.json);
  r = await teacher.post('/api/auth/login', { username: 'teacher', password: '123456' });
  check('教师登录成功（种子哈希与 BCrypt 一致）', r.json && r.json.code === 0 && r.json.data.role === 'TEACHER', r.json);
  r = await student.post('/api/auth/login', { username: 'student01', password: '123456' });
  check('学生登录成功', r.json && r.json.code === 0 && r.json.data.role === 'STUDENT', r.json);
  r = await anon.post('/api/auth/login', { username: 'teacher', password: 'wrong' });
  check('密码错误登录失败', r.json && r.json.code !== 0, r.json);

  console.log('\n=== 2. 角色隔离 ===');
  const qBody = {
    type: 'SINGLE', subject: authSubject, knowledgePoint: '基础', content: '1+1=?',
    optionA: '1', optionB: '2', optionC: '3', optionD: '4', answer: 'B', score: 10, difficulty: 'EASY'
  };
  r = await student.post('/api/questions', qBody);
  check('学生新增题目被 403 拒绝（不能建题）', r.json && r.json.code === 403, r.json);
  r = await student.get('/api/questions?pageSize=5');
  check('学生访问题库列表被 403 拒绝', r.json && r.json.code === 403, r.json);
  r = await teacher.post('/api/questions', qBody);
  check('教师可新增题目', r.json && r.json.code === 0 && r.json.data.id > 0, r.json);

  console.log('\n=== 3. 题库录入（含参数校验） ===');
  const design = [
    { type: 'SINGLE', answer: 'A', score: 10, content: '单选题1：以下哪个是 Java 关键字？', optionA: 'class', optionB: 'Class', optionC: 'CLASS', optionD: 'clazz' },
    { type: 'SINGLE', answer: 'B', score: 10, content: '单选题2：Java 中用于继承的关键字是？', optionA: 'implements', optionB: 'extends', optionC: 'instanceof', optionD: 'super' },
    { type: 'MULTIPLE', answer: 'ABC', score: 20, content: '多选题1：属于面向对象特性的是？', optionA: '封装', optionB: '继承', optionC: '多态', optionD: '编译' },
    { type: 'JUDGE', answer: 'A', score: 10, content: '判断题1：Java 支持单继承。', optionA: '对', optionB: '错', optionC: null, optionD: null }
  ];
  const created = [];
  for (const d of design) {
    r = await teacher.post('/api/questions', Object.assign({
      subject: subject, knowledgePoint: 'E2E', difficulty: 'EASY'
    }, d));
    check('录入题目：' + d.type + ' - ' + d.content.slice(0, 10), r.json && r.json.code === 0, r.json);
    if (r.json && r.json.code === 0) { created.push({ id: r.json.data.id, answer: d.answer, score: d.score, type: d.type }); }
  }
  r = await teacher.post('/api/questions', Object.assign({}, qBody, { type: 'BAD_TYPE' }));
  check('非法题型被参数校验拦截', r.json && r.json.code !== 0 && /题型/.test(r.json.message), r.json);
  r = await teacher.post('/api/questions', Object.assign({}, qBody, { score: 0.1 }));
  check('分值越界被参数校验拦截', r.json && r.json.code !== 0 && /分值/.test(r.json.message), r.json);

  console.log('\n=== 4. 随机组卷与发布 ===');
  r = await teacher.post('/api/papers/random', {
    title: '《E2E自动化测试卷》' + suffix, subject: subject, durationMinutes: 30, passScore: 30,
    rules: [
      { type: 'SINGLE', count: 2, score: 10 },
      { type: 'MULTIPLE', count: 1, score: 20 },
      { type: 'JUDGE', count: 1, score: 10 }
    ]
  });
  check('按规则随机抽题组卷成功', r.json && r.json.code === 0 && r.json.data.id > 0, r.json);
  const paperId = r.json.data.id;
  check('试卷总分按规则累加为 50', r.json && Number(r.json.data.totalScore) === 50, r.json && r.json.data.totalScore);

  r = await teacher.get('/api/papers/' + paperId + '/questions');
  check('组卷结果题目数正确（2+1+1=4）', r.json && r.json.data.length === 4, r.json && r.json.data && r.json.data.length);

  r = await student.get('/api/exams');
  const beforePublish = (r.json.data.list || []).filter(p => p.id === paperId).length;
  check('未发布的卷子不出现在学生可考列表', beforePublish === 0, beforePublish);

  r = await teacher.post('/api/papers/' + paperId + '/publish');
  check('教师发布试卷成功', r.json && r.json.code === 0, r.json);
  r = await student.get('/api/exams');
  check('发布后学生可见该考试', (r.json.data.list || []).some(p => p.id === paperId), r.json && r.json.data);

  r = await student.post('/api/papers/' + paperId + '/publish');
  check('学生发布试卷被 403 拒绝', r.json && r.json.code === 403, r.json);

  console.log('\n=== 5. 考试与答案不外泄 ===');
  r = await student.post('/api/exams/' + paperId + '/start');
  check('学生开始考试成功', r.json && r.json.code === 0 && r.json.data.id > 0 && r.json.data.status === 'ONGOING', r.json);
  const recordId = r.json.data.id;

  r = await student.post('/api/exams/' + paperId + '/start');
  check('重复开考返回同一记录（不重置计时、不报错）', r.json && r.json.data.id === recordId, r.json && r.json.data);

  r = await student.get('/api/exams/records/' + recordId + '/questions');
  const paperQuestions = r.json.data;
  check('取题成功且题数为 4', Array.isArray(paperQuestions) && paperQuestions.length === 4, paperQuestions && paperQuestions.length);
  check('考试中下发的题目不含答案字段（防作弊）',
    JSON.stringify(r.text).indexOf('"answer"') < 0 && JSON.stringify(r.text).indexOf('correctAnswer') < 0,
    '响应里出现了 answer/correctAnswer');

  console.log('\n=== 6. 自动判分规则（核心） ===');
  // 说明：取题接口返回的是 ExamQuestionView（结构上没有答案字段），题目主键字段名为 questionId。
  // 判分预期：单选答对 1 题(10) + 单选答错 1 题(0) + 多选少选(0) + 判断题答对(10) = 20 分
  const answerMap = {};
  let expected = 0;
  let wrongSinglePlanned = false;
  for (const q of paperQuestions) {
    const d = created.find(c => c.id === q.questionId);
    if (q.type === 'MULTIPLE') {
      answerMap[q.questionId] = (d ? d.answer : 'ABC').slice(0, 2);   // 故意少选 → 应 0 分
      continue;
    }
    if (q.type === 'SINGLE' && !wrongSinglePlanned) {
      // 第一道单选故意选一个错的选项 → 应 0 分
      wrongSinglePlanned = true;
      answerMap[q.questionId] = (d && d.answer === 'A') ? 'D' : 'A';
      continue;
    }
    // 其余题目按正确答案作答 → 拿满分
    answerMap[q.questionId] = d ? d.answer : 'A';
    expected += Number(q.score);
  }

  r = await student.post('/api/exams/submit', { recordId: recordId, answers: answerMap });
  check('交卷成功并返回判分结果', r.json && r.json.code === 0, r.json);
  const rec = r.json && r.json.data;
  check('判分总分 = 答对单选10 + 多选少选0 + 判断10 = 20', rec && Number(rec.score) === expected,
    { got: rec && rec.score, expected: expected, answers: answerMap });
  check('多选题少选不得分（全对才得分规则生效：总分不等于满分 50）', rec && Number(rec.score) !== 50, rec && rec.score);
  check('答对题数统计正确（2 题）', rec && rec.correctCount === 2, rec && rec.correctCount);
  check('总题数统计正确（4 题）', rec && rec.totalCount === 4, rec && rec.totalCount);
  check('交卷后状态变为 SUBMITTED', rec && rec.status === 'SUBMITTED', rec && rec.status);
  check('未超时标记正确（刚开考即交卷，字段名为 timeout）', rec && rec.timeout === false, rec && rec.timeout);

  console.log('\n=== 7. 防重复交卷 ===');
  const tampered = {};
  for (const q of paperQuestions) { tampered[q.questionId] = 'A'; }
  r = await student.post('/api/exams/submit', { recordId: recordId, answers: tampered });
  check('重复交卷被拒绝', r.json && r.json.code !== 0, r.json);
  r = await student.get('/api/exams/records/' + recordId);
  check('重复交卷未改写原分数', r.json && Number(r.json.data.score) === expected, r.json && r.json.data.score);
  r = await student.get('/api/exams/records/' + recordId + '/review');
  check('答题明细未被重复写入（仍为 4 条）', r.json && r.json.data.length === 4, r.json && r.json.data && r.json.data.length);

  console.log('\n=== 8. 越权与成绩统计 ===');
  // 另注册一名学生，验证"同一份试卷可以被多名学生独立考试"。
  // 注意：该系统的注册接口是刻意"不自动登录"的（前端注册成功后会跳到登录页），
  // 因此这里注册完必须再登录一次，才能拿到有效 Session。
  const otherName = 'e2e_stu_' + suffix;
  r = await other.post('/api/auth/register', { username: otherName, password: '123456', nickname: 'E2E学生2' });
  check('第二名学生注册成功', r.json && r.json.code === 0, r.json);
  r = await other.get('/api/auth/me');
  check('注册接口不自动登录（符合设计：需再登录一次）', r.json && r.json.data === null, r.json);
  r = await other.post('/api/auth/login', { username: otherName, password: '123456' });
  check('第二名学生登录成功', r.json && r.json.code === 0, r.json);
  r = await other.post('/api/exams/' + paperId + '/start');
  const otherRecord = r.json && r.json.data && r.json.data.id;
  check('另一学生可独立开考（同一试卷多人可考）', !!otherRecord && otherRecord !== recordId, r.json);
  r = await other.get('/api/exams/records/' + recordId + '/questions');
  check('学生不能查看他人考试题目', r.json && r.json.code !== 0, r.json);
  r = await other.get('/api/exams/records/' + recordId + '/review');
  check('学生不能查看他人答卷回顾', r.json && r.json.code !== 0, r.json);

  r = await teacher.get('/api/exams/records/' + recordId + '/review');
  check('教师可查看学生答卷回顾', r.json && r.json.code === 0 && r.json.data.length === 4, r.json);

  r = await student.get('/api/scores/my');
  check('学生可查自己成绩（含刚才的 20 分）', r.json && r.json.code === 0 && (r.json.data.list || []).some(x => x.id === recordId), r.json);
  r = await student.get('/api/scores/paper/' + paperId);
  check('学生查看某场成绩列表被 403 拒绝', r.json && r.json.code === 403, r.json);

  r = await teacher.get('/api/scores/paper/' + paperId + '/stats');
  const st = r.json && r.json.data;
  check('教师可查看成绩统计', r.json && r.json.code === 0, r.json);
  check('统计平均分按实际交卷人数计算', st && Number(st.avgScore) === expected, st);
  check('统计最高/最低分正确', st && Number(st.maxScore) === expected && Number(st.minScore) === expected, st);
  check('统计及格率正确（20 分 < 及格分 30，及格数 0）', st && st.passCount === 0 && st.totalCount === 1, st);

  console.log('\n=== 9. 页面渲染（Thymeleaf） ===');
  r = await anon.get('/');
  check('首页可渲染', r.status === 200 && r.text.includes('考试'), r.status + ':' + r.text.slice(0, 60));
  r = await anon.get('/login');
  check('登录页可渲染', r.status === 200, r.status);
  r = await teacher.get('/teacher/questions');
  check('教师题库页可渲染', r.status === 200, r.status);
  r = await teacher.get('/teacher/papers');
  check('教师试卷页可渲染', r.status === 200, r.status);
  r = await teacher.get('/teacher/scores');
  check('教师成绩页可渲染', r.status === 200, r.status);
  r = await student.get('/student/exams');
  check('学生考试列表页可渲染', r.status === 200, r.status);
  r = await student.get('/student/scores');
  check('学生成绩页可渲染', r.status === 200, r.status);
  // 已交卷的记录打开答题页会被 302 送回成绩回顾页，这是刻意设计（防止考完再回来改答案）
  r = await student.get('/student/exam?recordId=' + recordId);
  check('已交卷记录的答题页自动跳转到成绩回顾（防考后改答案）', r.status === 302, r.status);
  r = await other.get('/student/exam?recordId=' + otherRecord);
  check('未交卷记录可正常打开答题页', r.status === 200 && r.text.includes('交卷'), r.status);
  r = await student.get('/student/review?recordId=' + recordId);
  check('学生答卷回顾页可渲染', r.status === 200, r.status);
  r = await student.get('/teacher/questions');
  check('学生访问教师页面被拦截（重定向或 403）', r.status === 302 || r.status === 403, r.status);

  console.log('\n================================');
  console.log('端到端验收结果: 通过 ' + pass + ' 项, 失败 ' + fail + ' 项');
  if (fail) { console.log('失败项: ' + failures.join(' | ')); }
  console.log('================================');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error('验收脚本异常: ' + e.stack); process.exit(2); });
