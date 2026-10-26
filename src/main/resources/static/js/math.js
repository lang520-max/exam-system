/* ============================================================================
 *  math.js —— KaTeX 公式自动渲染
 *
 *  依赖（必须在本文件之前加载）：
 *      /vendor/katex/katex.min.js
 *      /vendor/katex/contrib/auto-render.min.js
 *      /vendor/katex/katex.min.css
 *
 *  为什么用 MutationObserver 而不是在每个注入点手动调用：
 *      各页面的内容是分散在不同 render()/innerHTML 赋值里注入的
 *      （题目列表、成绩单、弹窗、答题页…），逐个补一行 typeset() 既容易漏，
 *      以后新增页面也容易忘记。这里统一接管，页面代码一行都不用改。
 *
 *  为什么重复渲染是安全的：
 *      KaTeX 渲染后会把原文的文本节点替换成 <span class="katex">，
 *      页面上不再残留 $...$ 字面量；而 MathML 的 <annotation> 里存的是
 *      去掉定界符后的 TeX，不带 $，因此不会被再次匹配。重复扫描是幂等的。
 * ========================================================================== */

(function () {
  'use strict';

  if (typeof renderMathInElement !== 'function') {
    // KaTeX 没加载（或该页面不需要公式）。静默返回，不影响页面其它功能。
    return;
  }

  var OPTIONS = {
    delimiters: [
      { left: '$$', right: '$$', display: true },
      { left: '\\[', right: '\\]', display: true },
      { left: '$', right: '$', display: false },
      { left: '\\(', right: '\\)', display: false }
    ],
    // 单条公式写错时只在原地标红，不让整页渲染中断
    throwOnError: false,
    errorColor: '#9c3a2b',
    ignoredTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code', 'option'],
    ignoredClasses: ['katex', 'no-math']
  };

  /** 渲染 root 子树内的所有公式 */
  function typeset(root) {
    if (!root) { return; }
    try {
      renderMathInElement(root, OPTIONS);
    } catch (e) {
      // 渲染失败绝不能影响页面主体功能
      if (window.console && console.warn) { console.warn('公式渲染失败：', e); }
    }
  }

  var timer = null;

  function scheduleTypeset() {
    if (timer) { return; }          // 同一批 DOM 变更只渲染一次
    timer = setTimeout(function () {
      timer = null;
      typeset(document.body);
    }, 50);
  }

  function boot() {
    typeset(document.body);

    var observer = new MutationObserver(scheduleTypeset);
    observer.observe(document.body, { childList: true, subtree: true });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }

  // 提供给需要精确控制时机的调用方（例如渲染完成后再截图）
  window.MathTypeset = typeset;
})();