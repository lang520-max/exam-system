/*
 * check-paper-stability.js —— 守住「不改动已有试卷题面」这条硬约束。
 *
 * 背景（docs/会话交接-项目当前状态.md §3.7）：题目 ID 是历史答卷的外键目标，
 * 而试卷是按「题型 + ID 升序取第 k 段」切出来的。因此**加题只能追加在末尾**，
 * 一旦有人改动了已有题的 ID、顺序或切分逻辑，前几张卷的题面就会悄悄变掉 ——
 * 学生手里那份卷子和成绩单上的题目对不上，而且不会有任何报错。
 *
 * 用法（在仓库根目录）：
 *   node scripts/check-paper-stability.js              # 与 git HEAD 比
 *   node scripts/check-paper-stability.js <旧文件路径>   # 与指定文件比
 *
 * 判据：
 *   · 12 张模拟卷（id 1-12）的 question_snapshot **必须逐字节相同**；
 *   · 题目正文的差异会被列出来（允许改，但要看得见）；
 *   · 新增的卷/题只做提示，不算失败。
 */
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const CURRENT = path.join(ROOT, 'sql/data-shengben.sql');

function readBaseline() {
  const arg = process.argv[2];
  if (arg) {
    return { label: arg, text: fs.readFileSync(path.resolve(arg), 'utf8') };
  }
  const text = execSync('git show HEAD:sql/data-shengben.sql', { cwd: ROOT, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  return { label: 'git HEAD:sql/data-shengben.sql', text };
}

/**
 * 从 SQL 文本里抽出 { examId: snapshotJson }。
 *
 * 刻意**不按列位置**解析：V4 给 exam 加了 paper_type 列，列数变了，
 * 按位置写死的正则会一条都匹配不上 —— 而「匹配不上」在这里的表现是
 * **检查静默通过**（基线为空 ⇒ 没有差异 ⇒ 报告「通过」）。
 * 第一版就是这么假通过的。所以改成：锁定 exam 插入语句，再取其中那段
 * 唯一以 [{"questionId" 开头的 JSON 数组。
 */
function extractExamSnapshots(sql) {
  const out = {};
  const stmtRe = /INSERT INTO exam[^;]*?VALUES\s*\(([\s\S]*?)\);/g;
  let m;
  while ((m = stmtRe.exec(sql)) !== null) {
    const tuple = m[1];
    const idMatch = tuple.match(/^\s*(\d+)\s*,/);
    const snapMatch = tuple.match(/'(\[\{"questionId".*?\}\])'/s);
    if (idMatch && snapMatch) {
      out[idMatch[1]] = snapMatch[1];
    }
  }
  return out;
}

/**
 * 抽出 { questionId: {content, options, answer, analysis} }（同样不依赖列顺序/列数）。
 * options / analysis 也要比：只比题干会让「改了选项」这类改动隐形。
 */
function extractQuestions(sql) {
  const out = {};
  const re = /INSERT INTO question \([^)]*\) VALUES \(\s*(\d+),\s*\d+,\s*\d+,\s*\d+,\s*'((?:[^']|'')*)',\s*(?:'((?:[^']|'')*)'|NULL),\s*'((?:[^']|'')*)',\s*\d+,\s*(?:'((?:[^']|'')*)'|NULL),/g;
  let m;
  while ((m = re.exec(sql)) !== null) {
    out[m[1]] = { content: m[2], options: m[3] || null, answer: m[4], analysis: m[5] || null };
  }
  return out;
}

const baseline = readBaseline();
const currentText = fs.readFileSync(CURRENT, 'utf8');
const before = extractExamSnapshots(baseline.text);
const after = extractExamSnapshots(currentText);

const problems = [];
const notes = [];

// 0) 自检：两个文件都必须解析出卷子来，否则这个检查毫无意义（会静默"通过"）
if (Object.keys(before).length === 0) {
  console.error('基线里一道试卷都没解析出来（' + baseline.label + '）——');
  console.error('说明解析规则与文件格式不匹配。这种「空基线」会让检查假装通过，因此直接失败。');
  process.exit(2);
}
if (Object.keys(after).length === 0) {
  console.error('新生成的文件里一道试卷都没解析出来 —— 同上，直接失败。');
  process.exit(2);
}
notes.push('解析到试卷：基线 ' + Object.keys(before).length + ' 张，新文件 ' + Object.keys(after).length + ' 张');

// 1) 已有 12 张模拟卷的题面必须一模一样
const MOCK_IDS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'];
let sameCount = 0;
for (const id of MOCK_IDS) {
  if (!before[id]) { notes.push('基线里没有试卷 ' + id + '（新增？）'); continue; }
  if (!after[id]) { problems.push('试卷 ' + id + ' 在新文件里消失了'); continue; }
  if (before[id] === after[id]) { sameCount++; continue; }
  const bIds = (before[id].match(/\d+/g) || []).join(',');
  const aIds = (after[id].match(/\d+/g) || []).join(',');
  problems.push('试卷 ' + id + ' 的题面变了：旧 [' + bIds.slice(0, 90) + '…] 新 [' + aIds.slice(0, 90) + '…]');
}
if (sameCount === MOCK_IDS.length) {
  notes.push('12 张模拟卷的题面与基线逐字节相同 ✔');
}

// 2) 题目正文差异（允许，但要看得见：改了什么、改了哪几个字段）
const qBefore = extractQuestions(baseline.text);
const qAfter = extractQuestions(currentText);
const changed = [];
for (const id of Object.keys(qAfter)) {
  if (!qBefore[id]) { continue; }              // 新题
  const fields = ['content', 'options', 'answer', 'analysis']
    .filter(f => (qBefore[id][f] || '') !== (qAfter[id][f] || ''));
  if (fields.length) { changed.push(id + '(' + fields.join('/') + ')'); }
}
const removed = Object.keys(qBefore).filter(id => !qAfter[id]);

// 3) 新增的卷
const added = Object.keys(after).filter(id => !before[id]);

console.log('基线：' + baseline.label);
console.log('对比：' + path.relative(ROOT, CURRENT).replace(/\\/g, '/'));
console.log('');
notes.forEach(n => console.log('  · ' + n));
console.log('  题目正文有改动：' + (changed.length ? changed.join(', ') : '无'));
console.log('  被删掉的题目：' + (removed.length ? removed.join(', ') : '无'));
console.log('  新增的试卷：' + (added.length ? added.join(', ') : '无'));
console.log('');

if (problems.length) {
  console.error('试卷稳定性检查未通过：');
  problems.forEach(p => console.error('  - ' + p));
  process.exit(1);
}
console.log('试卷稳定性检查通过。');