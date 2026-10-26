/* ============================================================================
 *  charts.js —— 极简 SVG 图表（零依赖）
 *
 *  为什么自己拼 SVG 而不引 ECharts / Chart.js：
 *  全站刻意保持「零依赖、无构建」（见交接文档 3.5）。为了一个统计弹窗和一张
 *  走势图引入几百 KB 的图表库 + 它的构建链并不划算，而这里只需要三种图。
 *
 *  三条实现约定：
 *
 *  1) 颜色写在**内联 style**（`style="fill:var(--brand)"`）而不是表现属性
 *     （`fill="var(--brand)"`）—— SVG 表现属性不接受 var()，只有 CSS 才解析变量。
 *     这样图表直接沿用全站配色，换主题不用改 JS。
 *  2) 所有外部文本都过 UI.esc()：标签可能来自试卷标题等用户数据。
 *  3) 每张图带 role="img" + <title>，读屏和鼠标悬停都能拿到数值。
 * ========================================================================== */

(function (global) {
  'use strict';

  function esc(s) {
    if (global.UI && UI.esc) { return UI.esc(s); }
    return String(s === null || s === undefined ? '' : s);
  }

  /** 保留 1 位小数，但整数不显示 .0 */
  function fmt(n) {
    if (n === null || n === undefined || isNaN(n)) { return '0'; }
    var v = Math.round(Number(n) * 10) / 10;
    return (v % 1 === 0) ? String(v) : v.toFixed(1);
  }

  function clamp(v, lo, hi) {
    return Math.min(hi, Math.max(lo, v));
  }

  function emptyBox(message) {
    return '<div class="chart-empty">' + esc(message) + '</div>';
  }

  /** 截断过长的标签，避免坐标轴互相压字 */
  function shortLabel(s, max) {
    var t = String(s === null || s === undefined ? '' : s);
    var limit = max || 8;
    return t.length > limit ? t.slice(0, limit) + '…' : t;
  }

  /* ------------------------------------------------------------------ 环形图 */

  /**
   * 环形进度图。
   *
   * @param {{value:number,total:number,label:string,sublabel?:string,tone?:string}} o
   * @returns {string} HTML
   */
  function donut(o) {
    o = o || {};
    var total = Number(o.total) > 0 ? Number(o.total) : 0;
    var value = clamp(Number(o.value) || 0, 0, total || 0);
    var pct = total > 0 ? (value / total * 100) : 0;

    var r = 54;
    var circumference = 2 * Math.PI * r;
    var dash = (clamp(pct, 0, 100) / 100 * circumference).toFixed(2);
    var tone = o.tone || 'var(--brand)';
    var label = o.label || '';

    return '<div class="chart-donut">'
      + '<svg viewBox="0 0 140 140" role="img" aria-label="'
      + esc(label + ' ' + fmt(pct) + '%') + '">'
      + '<title>' + esc(label + '：' + fmt(value) + ' / ' + fmt(total) + '（' + fmt(pct) + '%）') + '</title>'
      + '<circle cx="70" cy="70" r="' + r + '" fill="none" style="stroke:var(--canvas-3)" stroke-width="16"/>'
      + '<circle cx="70" cy="70" r="' + r + '" fill="none" style="stroke:' + tone + '" stroke-width="16"'
      + ' stroke-linecap="round" stroke-dasharray="' + dash + ' ' + circumference.toFixed(2) + '"'
      + ' transform="rotate(-90 70 70)"/>'
      + '<text x="70" y="68" text-anchor="middle" class="donut-value">' + fmt(pct) + '%</text>'
      + '<text x="70" y="90" text-anchor="middle" class="donut-label">' + esc(label) + '</text>'
      + '</svg>'
      + (o.sublabel ? '<div class="chart-sub">' + esc(o.sublabel) + '</div>' : '')
      + '</div>';
  }

  /* ------------------------------------------------------------------ 折线图 */

  /**
   * 得分率折线图（y 轴固定 0-100%）。
   *
   * @param {{points:Array<{label:string,value:number}>, threshold?:number,
   *          thresholdText?:string, maxPoints?:number}} o
   * @returns {string} HTML
   */
  function line(o) {
    o = o || {};
    var pts = (o.points || []).filter(function (p) {
      return p && p.value !== null && p.value !== undefined && !isNaN(p.value);
    });
    if (!pts.length) { return emptyBox('还没有可绘制的成绩'); }

    var W = 600, H = 210;
    var padL = 40, padR = 18, padT = 18, padB = 36;
    var iw = W - padL - padR;
    var ih = H - padT - padB;

    function x(i) {
      return pts.length === 1 ? padL + iw / 2 : padL + iw * i / (pts.length - 1);
    }
    function y(v) {
      return padT + ih * (1 - clamp(Number(v), 0, 100) / 100);
    }

    var svg = '<svg class="chart-svg" viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-label="得分率趋势">';

    // 横向网格 + y 轴刻度（0/25/50/75/100）
    [0, 25, 50, 75, 100].forEach(function (v) {
      var yy = y(v);
      svg += '<line class="grid-line" x1="' + padL + '" y1="' + yy.toFixed(1) + '" x2="' + (W - padR) + '" y2="' + yy.toFixed(1) + '"/>'
        + '<text class="axis-text" x="' + (padL - 8) + '" y="' + (yy + 3.5).toFixed(1) + '" text-anchor="end">' + v + '%</text>';
    });

    // 及格线：虚线 + 右侧标注
    if (o.threshold !== null && o.threshold !== undefined && !isNaN(o.threshold)) {
      var ty = y(o.threshold);
      svg += '<line class="threshold-line" x1="' + padL + '" y1="' + ty.toFixed(1) + '" x2="' + (W - padR) + '" y2="' + ty.toFixed(1) + '"/>'
        + '<text class="axis-text on" x="' + (W - padR) + '" y="' + (ty - 5).toFixed(1) + '" text-anchor="end">'
        + esc(o.thresholdText || ('及格线 ' + fmt(o.threshold) + '%')) + '</text>';
    }

    // 面积 + 折线
    var linePath = pts.map(function (p, i) {
      return (i === 0 ? 'M' : 'L') + x(i).toFixed(1) + ' ' + y(p.value).toFixed(1);
    }).join(' ');
    if (pts.length > 1) {
      svg += '<path class="series-area" d="' + linePath
        + ' L' + x(pts.length - 1).toFixed(1) + ' ' + y(0).toFixed(1)
        + ' L' + x(0).toFixed(1) + ' ' + y(0).toFixed(1) + ' Z"/>';
    }
    svg += '<path class="series-line" d="' + linePath + '"/>';

    // 数据点：不及格用红色描边
    pts.forEach(function (p, i) {
      var fail = o.threshold !== null && o.threshold !== undefined && p.value < o.threshold;
      svg += '<circle class="series-dot' + (fail ? ' fail' : '') + '" cx="' + x(i).toFixed(1) + '" cy="' + y(p.value).toFixed(1) + '" r="4">'
        + '<title>' + esc(p.label + '：' + fmt(p.value) + '%') + '</title>'
        + '</circle>';
    });

    // x 轴标签：最多 4 个，避免压字
    var maxLabels = 4;
    var step = Math.max(1, Math.ceil(pts.length / maxLabels));
    pts.forEach(function (p, i) {
      var show = (i === 0) || (i === pts.length - 1) || (i % step === 0);
      if (!show) { return; }
      svg += '<text class="axis-text" x="' + x(i).toFixed(1) + '" y="' + (H - 14) + '" text-anchor="middle">'
        + esc(shortLabel(p.label, 7)) + '</text>';
    });

    svg += '</svg>';
    return svg;
  }

  /* ------------------------------------------------------------------ 直方图 */

  /**
   * 分段柱状图（分数分布）。
   *
   * @param {{items:Array<{label:string,count:number,passed:boolean}>}} o
   * @returns {string} HTML
   */
  function bars(o) {
    o = o || {};
    var items = (o.items || []).filter(Boolean);
    if (!items.length) { return emptyBox('暂无分布数据'); }

    var W = 600, H = 210;
    var padL = 34, padR = 14, padT = 24, padB = 38;
    var iw = W - padL - padR;
    var ih = H - padT - padB;

    var max = 1;
    items.forEach(function (it) { max = Math.max(max, Number(it.count) || 0); });

    var slot = iw / items.length;
    var barW = Math.min(64, slot * 0.62);
    var baseY = padT + ih;

    var svg = '<svg class="chart-svg" viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-label="分数分布">';

    // 基线
    svg += '<line class="grid-line" x1="' + padL + '" y1="' + baseY + '" x2="' + (W - padR) + '" y2="' + baseY + '"/>';

    items.forEach(function (it, i) {
      var count = Number(it.count) || 0;
      var h = count === 0 ? 0 : Math.max(3, ih * count / max);
      var cx = padL + slot * i + slot / 2;
      var x = cx - barW / 2;
      var y = baseY - h;
      var tone = it.passed ? 'var(--brand)' : 'var(--danger)';
      var failCls = it.passed ? '' : ' fail';

      if (count > 0) {
        svg += '<rect class="bar-rect' + failCls + '" x="' + x.toFixed(1) + '" y="' + y.toFixed(1) + '"'
          + ' width="' + barW.toFixed(1) + '" height="' + h.toFixed(1) + '" rx="5"'
          + ' role="img"><title>' + esc(it.label + '：' + count + ' 人') + '</title></rect>';
      } else {
        // 0 人也画一条极浅的底纹，让「这一档没人」和「没有这一档」看起来不同
        svg += '<rect x="' + x.toFixed(1) + '" y="' + (baseY - 3) + '" width="' + barW.toFixed(1)
          + '" height="3" rx="1.5" style="fill:var(--rule-2)"/>';
      }
      svg += '<text class="bar-count" x="' + cx.toFixed(1) + '" y="' + (y - 7).toFixed(1) + '" text-anchor="middle">'
        + (count > 0 ? count : '') + '</text>';
      svg += '<text class="axis-text" x="' + cx.toFixed(1) + '" y="' + (H - 14) + '" text-anchor="middle">'
        + esc(it.label) + '</text>';
      // tone 变量在此处仅用于可读性；实际着色由 .bar-rect / .fail 承担
      void tone;
    });

    svg += '</svg>';
    return svg;
  }

  global.Charts = {
    donut: donut,
    line: line,
    bars: bars
  };
})(window);