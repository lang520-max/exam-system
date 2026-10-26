'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const apiSource = fs.readFileSync(path.join(root, 'src/main/resources/static/js/api.js'), 'utf8');
const practiceHtml = fs.readFileSync(path.join(root, 'src/main/resources/static/student/practice.html'), 'utf8');
const practiceScript = Array.from(practiceHtml.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi))
  .find(function (match) {
    return !/\bsrc\s*=/.test(match[1]) && /function doCheck/.test(match[2]);
  });

assert.ok(practiceScript, 'could not find the free practice inline script');

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise(function (res, rej) {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function flush() {
  return new Promise(function (resolve) {
    setImmediate(resolve);
  }).then(function () {
    return Promise.resolve();
  });
}

function createElement(id) {
  return {
    id,
    value: '',
    textContent: '',
    innerHTML: '',
    className: '',
    disabled: false,
    style: { display: '', width: '' },
    handlers: {},
    addEventListener: function (event, handler) {
      this.handlers[event] = handler;
    },
    scrollIntoView: function () {}
  };
}

function createPracticeHarness(questionSets) {
  const elements = new Map();
  const posts = [];
  const toasts = [];
  let questionSetIndex = 0;

  function element(id) {
    if (!elements.has(id)) { elements.set(id, createElement(id)); }
    return elements.get(id);
  }

  const document = {
    getElementById: element,
    querySelectorAll: function () { return []; },
    querySelector: function () { return null; }
  };
  const API = {
    get: function (url) {
      if (url.indexOf('/api/practice/questions') === 0) {
        return Promise.resolve(questionSets[questionSetIndex++] || []);
      }
      return Promise.resolve([]);
    },
    post: function (url, body, options) {
      const request = deferred();
      posts.push({ url, body, options, request });
      return request.promise;
    }
  };
  const UI = {
    esc: function (value) { return String(value == null ? '' : value); },
    renderNav: function () {},
    toast: function (message, type) { toasts.push({ message, type }); },
    confirm: function () { return Promise.resolve(true); }
  };

  vm.runInNewContext(practiceScript[2], {
    document,
    API,
    UI,
    Auth: { requireRole: function () {} },
    Promise,
    JSON,
    Array,
    Math,
    encodeURIComponent
  });

  return {
    element,
    posts,
    toasts,
    click: function (id) {
      const handler = element(id).handlers.click;
      assert.equal(typeof handler, 'function', 'missing click handler for ' + id);
      handler();
    }
  };
}

function practiceQuestion(id, content) {
  return { id, type: 3, difficulty: 1, content, options: '' };
}

async function testRequestTimeout() {
  let fetchOptions;
  const document = {
    readyState: 'loading',
    addEventListener: function () {},
    querySelector: function () { return null; },
    querySelectorAll: function () { return []; },
    createElement: function () { return {}; },
    body: { appendChild: function () {} },
    head: { appendChild: function () {} }
  };
  const localStorage = { getItem: function () { return null; }, removeItem: function () {}, setItem: function () {} };
  const window = {
    AbortController,
    setTimeout,
    clearTimeout,
    addEventListener: function () {}
  };
  const context = {
    window,
    document,
    localStorage,
    fetch: function (url, options) {
      fetchOptions = options;
      return new Promise(function () {});
    },
    Promise,
    AbortController,
    setTimeout,
    clearTimeout
  };

  vm.runInNewContext(apiSource, context);
  const error = await window.API.post('/api/practice/check', {}, { timeoutMs: 15 })
    .then(function () { throw new Error('expected a timeout'); }, function (err) { return err; });
  assert.equal(error.code, 'TIMEOUT');
  assert.equal(fetchOptions.signal.aborted, true);
}

async function testFinishWaitsForPendingGrade() {
  const harness = createPracticeHarness([[practiceQuestion(1, '延迟判分题')]]);
  await flush();
  harness.click('btn-start');
  await flush();
  harness.element('p-input').value = '答案';
  harness.click('btn-submit');

  assert.equal(harness.posts.length, 1);
  assert.equal(harness.posts[0].options.timeoutMs, 30000);
  assert.equal(harness.element('btn-submit').textContent, '判分中…');

  harness.click('btn-finish');
  await flush();
  assert.equal(harness.element('btn-finish').disabled, true);
  assert.equal(harness.element('summary-card').style.display, 'none');

  harness.posts[0].request.resolve({ judged: true, correct: true, studentAnswer: '答案', correctAnswer: '答案' });
  await flush();
  await flush();
  assert.equal(harness.element('summary-card').style.display, '');
  assert.match(harness.element('summary-body').innerHTML, /1 \/ 1/);
}

async function testTimeoutRestoresSubmitButton() {
  const harness = createPracticeHarness([[practiceQuestion(1, '超时判分题')]]);
  await flush();
  harness.click('btn-start');
  await flush();
  harness.element('p-input').value = '答案';
  harness.click('btn-submit');
  assert.equal(harness.element('btn-submit').disabled, true);

  harness.posts[0].request.reject({ message: '请求超时，请重试' });
  await flush();
  await flush();
  assert.equal(harness.element('btn-submit').disabled, false);
  assert.equal(harness.element('btn-submit').textContent, '提交答案');
}

async function testOldGradeCannotPolluteNextRound() {
  const harness = createPracticeHarness([
    [practiceQuestion(1, '旧练习题')],
    [practiceQuestion(2, '新练习题')]
  ]);
  await flush();
  harness.click('btn-start');
  await flush();
  harness.element('p-input').value = '旧答案';
  harness.click('btn-submit');

  harness.click('btn-start');
  await flush();
  assert.equal(harness.element('q-content').textContent, '新练习题');

  harness.posts[0].request.resolve({ judged: true, correct: true, studentAnswer: '旧答案', correctAnswer: '旧答案' });
  await flush();
  await flush();
  assert.equal(harness.element('feedback').style.display, 'none');
  assert.equal(harness.element('btn-submit').textContent, '提交答案');
  assert.match(harness.element('quiz-score').textContent, /0 \/ 已判 0/);

  harness.click('btn-finish');
  await flush();
  assert.match(harness.element('summary-body').innerHTML, /0 \/ 1/);
}

async function main() {
  await testRequestTimeout();
  console.log('PASS request timeout aborts the pending fetch');
  await testFinishWaitsForPendingGrade();
  console.log('PASS finishing waits for the pending grade before building the summary');
  await testTimeoutRestoresSubmitButton();
  console.log('PASS a timed-out grade restores the submit button');
  await testOldGradeCannotPolluteNextRound();
  console.log('PASS a late grade from an older round cannot update the next round');
}

main().catch(function (err) {
  console.error(err);
  process.exitCode = 1;
});
