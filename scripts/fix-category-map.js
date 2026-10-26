/*
 * fix-category-map.js —— 对子代理产出的知识点映射做人工复核后的修正。
 *
 * 三处改动，都是复核时发现的问题（不是脚本 bug）：
 *  1) 高数缺一个章节：七个允许值里没有「向量代数与空间解析几何」，
 *     子代理只好把 5 道向量题塞进「多元函数微积分」。四川专升本高数考纲里
 *     向量代数与空间解析几何是独立一章，补上并迁移这 5 题。
 *  2) 1044（lim Σ1/(n+k)，黎曼和）：归「极限与连续」是按"含 lim 就归极限"的机械规则，
 *     而它的解析明说关键是识别**定积分定义** → 迁到「一元函数积分学」（专项卷要用它）。
 *  3) 顺带修一处**题目数据瑕疵**（计算机 1253）：A 选项文字与题干完全重复，
 *     选择题的选项等于题干是废题 → 四个选项改成规范名称（局域网/城域网/广域网/个人区域网），
 *     解析里的 D 描述同步改。
 *
 * 幂等：重复执行结果一致。
 */
const fs = require('fs');
const path = require('path');

const DIR = 'D:\\deepseek-workspace\\exam-system\\scripts\\data';
const readJson = (f) => JSON.parse(fs.readFileSync(path.join(DIR, f), 'utf8'));
const writeJson = (f, obj) => fs.writeFileSync(path.join(DIR, f), JSON.stringify(obj, null, 2) + '\n', 'utf8');

// ---- 1) 高数：补章节 + 迁移 ----
const gaoshuMap = readJson('category-gaoshu.json');
const VECTOR = '向量代数与空间解析几何';
const vectorIds = ['1016', '1036', '1054', '1069', '1085'];
let movedVector = 0;
for (const id of vectorIds) {
  if (gaoshuMap[id] !== VECTOR) { gaoshuMap[id] = VECTOR; movedVector++; }
}
if (gaoshuMap['1044'] !== '一元函数积分学') { gaoshuMap['1044'] = '一元函数积分学'; }
writeJson('category-gaoshu.json', gaoshuMap);
console.log('高数：迁移到「' + VECTOR + '」' + movedVector + ' 题（目标 ' + vectorIds.length + '），'
  + '1044 归「一元函数积分学」');

// ---- 2) 计算机：修 1253 的废选项 ----
const computer = readJson('shengben-computer.json');
const q1253 = computer.questions.find((q) => q.id === 1253);
if (!q1253) { throw new Error('找不到题目 1253'); }
const before = JSON.stringify(q1253.options);
q1253.options = [
  ['A', '局域网（LAN）'],
  ['B', '城域网（MAN）'],
  ['C', '广域网（WAN）'],
  ['D', '个人区域网（PAN）'],
];
q1253.analysis = '按覆盖的地理范围，计算机网络可分为局域网（LAN）、城域网（MAN）和广域网（WAN）。'
  + '局域网覆盖一栋楼、一个校园或一个企业园区，范围通常在几公里以内，传输速率高、误码率低，故 A 正确。'
  + 'B 的城域网覆盖一个城市，C 的广域网覆盖国家乃至全球，二者范围都大于局域网；'
  + 'D 的个人区域网（PAN）覆盖范围更小（10 米左右），也不是本题所描述的范围。';
writeJson('shengben-computer.json', computer);
console.log('计算机 1253：选项 ' + (before === JSON.stringify(q1253.options) ? '未变' : '已改为规范名称'));

// ---- 3) 汇总 ----
for (const [bank, file] of [['高数', 'category-gaoshu.json'], ['英语', 'category-english.json'], ['计算机', 'category-computer.json']]) {
  const p = path.join(DIR, file);
  if (!fs.existsSync(p)) { console.log(bank + '：' + file + ' 还不存在（跳过）'); continue; }
  const map = JSON.parse(fs.readFileSync(p, 'utf8'));
  const counts = {};
  for (const v of Object.values(map)) { counts[v] = (counts[v] || 0) + 1; }
  const ids = Object.keys(map).length;
  console.log(bank + '（' + ids + ' 题）：' + Object.entries(counts).sort((a, b) => b[1] - a[1])
    .map(([k, v]) => k + ' ' + v).join(' | '));
}