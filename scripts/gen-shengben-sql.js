/* ============================================================================
 *  生成「四川专升本题库」导入 SQL（高等数学 / 英语 / 计算机基础）
 *
 *  产出两份文件（内容相同，转义不同）：
 *    sql/data-shengben.sql                    —— MySQL 8 语法（手工导入）
 *    src/main/resources/data-h2-shengben.sql  —— H2 (MODE=MySQL)，dev 档自动加载
 *
 *  为什么必须两份：LaTeX 里全是反斜杠，而两个引擎对字符串字面量里的反斜杠
 *  处理完全不同（实测）：
 *      '<\frac>'   在 MySQL 变成 <frac>（反斜杠被当转义符吃掉），在 H2 保持 <\frac>
 *      '<\\frac>'  在 MySQL 变成 <\frac>，在 H2 变成 <\\frac>（多一个）
 *  同一份文件不可能同时喂两个引擎。转义只在一处发生（sqlStr），
 *  内容本身用原始 LaTeX 书写，因此不存在「改了一处忘了改另一处」。
 *
 *  与项目早期那版「高数题库」生成脚本的两点不同（那版已随旧题库一并删除）：
 *
 *  1) 数据不在本脚本里，而是放在 scripts/data/shengben-*.json（每科一份，
 *     各 50 题）。150 道题写进脚本会难以维护，也让「内容」和「生成逻辑」混在一起。
 *  2) **试卷组成由本脚本推导，而不是由数据文件写死**：每科按题型的前一半/后一半
 *     切分成两卷，分值按题型固定（单选 3 / 多选 5 / 判断 3 / 填空 5 / 简答 6），
 *     因此每卷必然 100 分、两卷必然不重不漏地覆盖全部 50 题 —— 这类错误
 *     不该靠人工逐条核对。
 *
 *  内置自检：数据自洽性 + 用 KaTeX 逐条编译每个 $...$ 片段，
 *  任何问题在生成阶段就报错退出，不会等到页面上看见乱码。
 *
 *  用法：node scripts/gen-shengben-sql.js [katex包路径]
 *        （默认用仓库内的 static/vendor/katex/katex.min.js，无需联网）
 * ========================================================================== */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const DATA_DIR = path.join(__dirname, 'data');
const DEFAULT_KATEX = path.join(ROOT, 'src/main/resources/static/vendor/katex/katex.min.js');
// 参数解析：--only-new 是开关，第一个非 -- 开头的参数才是 KaTeX 包路径。
// （曾经的写法直接取 argv[2]，于是 `--only-new` 被当成包路径去 require，报
//  "Cannot find module '--only-new'" —— 加参数时务必走这里。）
const CLI_ARGS = process.argv.slice(2);
const ONLY_NEW = CLI_ARGS.includes('--only-new');
const KATEX_PATH = CLI_ARGS.find(a => !a.startsWith('--')) || DEFAULT_KATEX;

// ---------------------------------------------------------------- 配置

/**
 * 三科题库。
 * 题目 ID 区间必须连续，且试卷组成按「题型 + ID 升序」自动切分：
 *   每科 PAPERS_PER_BANK 张卷，第 k 张取每类题的第 k 段。
 *
 * ★ 加题的约定（2026-09-16 扩题时定下，务必遵守）：
 *   新题一律【追加】在 addedIdFrom 之后，绝不改动已有题的 ID ——
 *   云端已有作答记录通过外键指向题目 ID，改了 ID 会让历史答卷错位。
 *   因为切分是按「题型 + ID 升序」取段，旧题 ID 更小、永远排在前面，
 *   所以前两张卷（examIds 的前两个）的题面构成保持逐字节不变，
 *   新题只进入第三、四张卷。
 */
const BANKS = [
  { file: 'shengben-gaoshu.json',   categoryFile: 'category-gaoshu.json',   bankId: 1, idFrom: 1001, addedIdFrom: 1051, examIds: [1, 2, 7, 8] },
  { file: 'shengben-english.json',  categoryFile: 'category-english.json',  bankId: 2, idFrom: 1101, addedIdFrom: 1151, examIds: [3, 4, 9, 10] },
  { file: 'shengben-computer.json', categoryFile: 'category-computer.json', bankId: 3, idFrom: 1201, addedIdFrom: 1251, examIds: [5, 6, 11, 12] }
];

/**
 * 每科允许的知识点取值（V4）。
 *
 * 为什么要在这里再列一遍：知识点是**人工/半自动打的标签**，最容易出现错别字
 * （「一元函数积分」漏个「学」字）或大小写空格不一致，而这种错误不会让任何东西
 * 报错 —— 只会让「按专题抽题」抽不到题。所以在生成阶段就把白名单卡住。
 */
const CATEGORIES = {
  'shengben-gaoshu.json': ['极限与连续', '导数与微分', '中值定理与导数应用', '一元函数积分学',
    '向量代数与空间解析几何', '多元函数微积分', '微分方程', '级数与广义积分'],
  'shengben-english.json': ['语法', '词汇与短语', '阅读理解', '翻译', '写作'],
  'shengben-computer.json': ['计算机组成与工作原理', '数制与编码', '操作系统', '办公软件基础',
    '计算机网络', '数据库基础', '多媒体与信息安全', '程序设计基础']
};

/** 试卷类型（V4）：模拟卷 / 专项训练 */
const PAPER_TYPE_MOCK = '模拟卷';
const PAPER_TYPE_SPECIAL = '专项训练';

/**
 * 专项训练卷（V4 新增）：按**知识点**取题，而不是按题型分段切。
 *
 * 这是「试卷类型」需求的落点：老师要能给学生一份「一元函数积分学专项训练」。
 * 取题规则刻意是**确定性的**（同题型内按 ID 升序取前 N 道），不是随机：
 *   · 种子数据必须可复现（每次生成得到同一份卷子，diff 才有意义）；
 *   · 校验才能断言「卷内每一道题都属于该专题」。
 * 每类的题数取「该专题在此题型下的实际题量」与「模拟卷该题型题数」的较小值 ——
 * 专题题量不足时得到一份更短的卷子，而不是报错或去别的专题借题
 * （借题会让「专项」名不副实）。
 */
const SPECIAL_PAPERS = [
  {
    bankFile: 'shengben-gaoshu.json', examId: 101, category: '一元函数积分学',
    title: '四川专升本 · 高等数学专项训练（一元函数积分学）',
    description: '按知识点筛选的专项训练卷：只含「一元函数积分学」专题的题目，'
      + '覆盖不定积分、定积分、变限积分与定积分的几何应用。题面固定，不含抽题规则。',
    duration: 90, passScore: null, passRatio: 0.6
  },
  {
    bankFile: 'shengben-english.json', examId: 102, category: '语法',
    title: '四川专升本 · 英语专项训练（语法）',
    description: '按知识点筛选的专项训练卷：只含「语法」专题的题目，'
      + '覆盖时态、语态、从句、非谓语动词、虚拟语气与主谓一致。题面固定，不含抽题规则。',
    duration: 60, passScore: null, passRatio: 0.6
  }
];

/**
 * 专项卷的 id 为什么从 101 起，而不是接着 12 往后排（13、14）：
 *
 * 2026-09-19 在云端踩到的真实事故 —— 演示库上**人工用「智能组卷」创建的卷子也从 13 开始编号**
 * （那天 11:26 有人建了一张 6 题 70 分的卷子正好拿到 id=13）。种子脚本按 id 13/14 插入专项卷时，
 * `ON DUPLICATE KEY UPDATE` 把那张人工卷子的标题与题面**覆盖掉了**；
 * 而且那张卷子带着自己的 start_time/end_time，「到点自动结束」任务随即把新插进去的
 * 专项卷判成「已结束」，学生端根本看不到它。
 *
 * 教训两条，都落在下面的实现里：
 *   1) 种子数据不要占用「人会自然用到」的 id 区间 —— 留出 100 以上的空档；
 *   2) 种子的试卷要显式把 start_time/end_time 写成 NULL（= 不限时段），
 *      否则该 id 上残留的任何时间窗都会让卷子在下一分钟被自动结束掉。
 */

/** 每科的题型数量（必须精确匹配） */
const TYPE_COUNT = { 0: 40, 1: 16, 2: 16, 3: 16, 4: 12 };
/** 每卷中各类题型的题数（每科每卷相同） */
const PAPER_TYPE_COUNT = { 0: 10, 1: 4, 2: 4, 3: 4, 4: 3 };
/** 每科几张卷（每科题数必须正好是「每卷题数 × 张数」） */
const PAPERS_PER_BANK = 4;
/** 每科题目总数（由题型数量推出，避免两处写死不一致） */
const BANK_SIZE = Object.keys(TYPE_COUNT).reduce((a, t) => a + TYPE_COUNT[t], 0);
/** 卷号的中文数字，用于默认标题与日志 */
const CN_NUM = ['一', '二', '三', '四', '五', '六'];
/** 每卷中各类题型的每题分值 */
const PAPER_SCORE = { 0: 5, 1: 5, 2: 3, 3: 5, 4: 16 };   // 150 分制：50+20+12+20+48 = 150
/** 题库默认分值（按题型） */
const BANK_SCORE = { 0: 5, 1: 5, 2: 3, 3: 5, 4: 16 };    // 题目默认分值，与卷面同口径

const TYPE_NAME = { 0: '单选', 1: '多选', 2: '判断', 3: '填空', 4: '简答' };
const DIFF_NAME = { 1: '简单', 2: '中等', 3: '困难' };

/** 创建人：teacher1（见种子数据 sys_user id=2） */
const CREATED_BY = 2;

/**
 * 每卷的目标总分：**由 PAPER_SCORE 推导**，不写死数字。
 *
 * 原先这里写死 100，2026-09-17 把分值改成真实的四川专升本口径（每科 150 分）时
 * 校验立刻报了 12 条「总分应为 100，实际 150」—— 校验拦住改动是对的，
 * 但把常量抄两遍就会每次改分值时都来一轮。推导出来就没有这个隐患。
 */
const PAPER_TOTAL = Object.keys(PAPER_TYPE_COUNT)
  .reduce((sum, t) => sum + PAPER_TYPE_COUNT[t] * PAPER_SCORE[t], 0);

/**
 * 模拟卷直接以「已发布」（1）入库。
 *
 * 这是一批题面固定的演示卷，目的就是让学生一进「我的考试」就能开考；
 * 若留成草稿（0），重置 dev 库后会得到一份空列表，看起来像功能坏了。
 * 「草稿 → 发布」这条流程仍可由「智能组卷」现场体验。
 */
const PAPER_STATUS = 1;

// ---------------------------------------------------------------- 读取

function loadBankData(cfg) {
  const file = path.join(DATA_DIR, cfg.file);
  if (!fs.existsSync(file)) {
    throw new Error('数据文件不存在: ' + file);
  }
  let raw;
  try {
    raw = JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    throw new Error(cfg.file + ' 不是合法 JSON: ' + e.message);
  }
  if (!raw || !Array.isArray(raw.questions)) {
    throw new Error(cfg.file + ' 缺少 questions 数组');
  }

  // 知识点映射（V4）：单独一个文件，因为它是「人工复核过的标签」，
  // 与题目正文分开存更便于 diff（改标签不会把整个题库文件的 diff 弄脏）。
  const catFile = path.join(DATA_DIR, cfg.categoryFile);
  if (!fs.existsSync(catFile)) {
    throw new Error('知识点映射文件不存在: ' + cfg.categoryFile
      + '（每道题都必须有知识点，白名单见 CATEGORIES）');
  }
  let map;
  try {
    map = JSON.parse(fs.readFileSync(catFile, 'utf8'));
  } catch (e) {
    throw new Error(cfg.categoryFile + ' 不是合法 JSON: ' + e.message);
  }
  raw.categoryMap = map;
  // 贴到题目上，后面的校验与 SQL 生成直接用 q.category
  for (const q of raw.questions) {
    q.category = map[String(q.id)] || null;
  }
  return raw;
}

// ---------------------------------------------------------------- 自检

function validateBank(cfg, data, problems) {
  const tag = cfg.file;
  const bank = data.bank || {};
  if (!bank.name || !String(bank.name).trim()) problems.push(tag + ': bank.name 为空');
  if (!bank.subject || !String(bank.subject).trim()) problems.push(tag + ': bank.subject 为空');

  const qs = data.questions;
  if (qs.length !== BANK_SIZE) problems.push(tag + ': 题目数必须是 ' + BANK_SIZE + '，实际 ' + qs.length);

  const seenIds = new Set();
  const idTo = cfg.idFrom + BANK_SIZE - 1;
  const byType = { 0: [], 1: [], 2: [], 3: [], 4: [] };
  const diff = { 1: 0, 2: 0, 3: 0 };
  let analysisMissing = 0;

  for (const q of qs) {
    const where = tag + ' Q' + q.id;
    if (!Number.isInteger(q.id)) { problems.push(where + ': id 必须是整数'); continue; }
    if (seenIds.has(q.id)) problems.push(where + ': id 重复');
    seenIds.add(q.id);
    if (q.id < cfg.idFrom || q.id > idTo) {
      problems.push(where + ': id 超出题目区间 ' + cfg.idFrom + '-' + idTo);
    }
    if (![0, 1, 2, 3, 4].includes(q.type)) { problems.push(where + ': type 非法 ' + q.type); continue; }
    byType[q.type].push(q);
    if (![1, 2, 3].includes(q.difficulty)) problems.push(where + ': difficulty 非法 ' + q.difficulty);
    else diff[q.difficulty]++;
    if (!q.content || !String(q.content).trim()) problems.push(where + ': content 为空');
    if (!q.answer || !String(q.answer).trim()) problems.push(where + ': answer 为空');
    if (!q.analysis || !String(q.analysis).trim()) { analysisMissing++; }
    if (!(q.score > 0)) problems.push(where + ': score 必须大于 0');

    const opts = q.options;
    if (q.type === 0 || q.type === 1) {
      if (!Array.isArray(opts) || opts.length < 2) {
        problems.push(where + ': 选择题至少需要 2 个选项');
      } else {
        const labels = [];
        for (const o of opts) {
          if (!Array.isArray(o) || o.length < 2) { problems.push(where + ': options 元素必须是 [标签, 内容]'); continue; }
          labels.push(String(o[0]).trim());
          if (!String(o[1] == null ? '' : o[1]).trim()) problems.push(where + ': 选项 ' + o[0] + ' 的内容为空');
        }
        const ans = String(q.answer).trim().toUpperCase();
        if (q.type === 0 && !/^[A-Z]$/.test(ans)) problems.push(where + ': 单选答案必须是单个字母，实际 ' + q.answer);
        if (q.type === 1 && !/^[A-Z]{2,}$/.test(ans)) problems.push(where + ': 多选答案必须是 2 个以上字母，实际 ' + q.answer);
        for (const ch of ans) {
          if (!labels.includes(ch)) problems.push(where + ': 答案 ' + ans + ' 含未定义的选项 ' + ch);
        }
        if (new Set(ans.split('')).size !== ans.length) problems.push(where + ': 答案有重复字母 ' + ans);
        if (q.type === 1 && ans !== ans.split('').sort().join('')) {
          problems.push(where + ': 多选答案未按字母升序 ' + ans);
        }
      }
    } else if (q.type === 2) {
      if (opts != null && (!Array.isArray(opts) || opts.length !== 2)) {
        problems.push(where + ': 判断题选项应为 2 个或省略');
      }
      if (!/^[AB]$/.test(String(q.answer).trim().toUpperCase())) {
        problems.push(where + ': 判断答案必须是 A 或 B，实际 ' + q.answer);
      }
    } else if (opts != null) {
      problems.push(where + ': 填空题/简答题不应带选项');
    }
  }

  // 题型数量
  for (const t of [0, 1, 2, 3, 4]) {
    if (byType[t].length !== TYPE_COUNT[t]) {
      problems.push(tag + ': ' + TYPE_NAME[t] + '题数量应为 ' + TYPE_COUNT[t] + '，实际 ' + byType[t].length);
    }
  }

  // 解析是学生的「分是怎么来的」，缺了就只是少一段文字，不会报错，
  // 因此必须在生成阶段拦住（否则会一路静默到页面上）
  if (analysisMissing > 0) {
    problems.push(tag + ': 有 ' + analysisMissing + ' 道题缺少 analysis（每题必写解析）');
  }

  // 试卷能切分的前提：每类题数正好是「每卷题数 × 卷数」
  for (const t of [0, 1, 2, 3, 4]) {
    const need = PAPER_TYPE_COUNT[t] * PAPERS_PER_BANK;
    if (byType[t].length !== need) {
      problems.push(tag + ': ' + TYPE_NAME[t] + '题数量应为 ' + need
        + '（每卷 ' + PAPER_TYPE_COUNT[t] + ' × ' + PAPERS_PER_BANK + ' 卷），实际 ' + byType[t].length);
    }
  }

  // ---- 知识点（V4）----
  // 标签写错（漏字/多空格/用了不在白名单里的词）不会让任何东西报错，
  // 只会让「按专题抽题」静默抽不到题 —— 所以必须在这里拦住。
  const allowed = CATEGORIES[cfg.file] || [];
  const catCount = {};
  const catMissing = [];
  const catInvalid = [];
  for (const q of qs) {
    const c = q.category;
    if (!c) { catMissing.push(q.id); continue; }
    if (!allowed.includes(c)) { catInvalid.push(q.id + '=' + c); continue; }
    catCount[c] = (catCount[c] || 0) + 1;
  }
  if (catMissing.length) {
    problems.push(tag + ': 有 ' + catMissing.length + ' 道题没有知识点（' + catMissing.slice(0, 5).join(', ') + ' 等）');
  }
  if (catInvalid.length) {
    problems.push(tag + ': 知识点不在白名单里：' + catInvalid.slice(0, 5).join(', ')
      + '（白名单见 CATEGORIES）');
  }
  const extraKeys = Object.keys(data.categoryMap || {}).filter(k => !seenIds.has(Number(k)));
  if (extraKeys.length) {
    problems.push(tag + ': 知识点映射里有不存在的题目 id：' + extraKeys.slice(0, 5).join(', '));
  }
  const emptyCats = allowed.filter(c => !catCount[c]);
  if (emptyCats.length) {
    problems.push(tag + ': 这些知识点一道题都没有：' + emptyCats.join('、')
      + '（标签写错，还是该章节确实还没出题？）');
  }

  return { byType, diff, analysisMissing, catCount };
}

/** 按题型 + ID 升序切成 PAPERS_PER_BANK 张卷；分值按题型固定 */
function derivePapers(cfg, data) {
  const sorted = {};
  for (const t of [0, 1, 2, 3, 4]) {
    sorted[t] = data.questions.filter(q => q.type === t).sort((a, b) => a.id - b.id);
  }
  const papers = [];
  for (let k = 0; k < PAPERS_PER_BANK; k++) {
    const snapshot = [];
    for (const t of [0, 1, 2, 3, 4]) {
      const from = k * PAPER_TYPE_COUNT[t];
      const slice = sorted[t].slice(from, from + PAPER_TYPE_COUNT[t]);
      for (const q of slice) snapshot.push([q.id, PAPER_SCORE[t]]);
    }
    papers.push(snapshot);
  }
  return papers;
}

/**
 * 专项训练卷的题面：**按知识点**取题（同题型内按 ID 升序），
 * 每类题不超过模拟卷该题型的题数。
 *
 * 专题题量不足时得到的是一份更短的卷子，而不是报错或跨专题借题 ——
 * 「专项」的价值就在于卷子里只有这一个专题的题。
 */
function deriveSpecialPaper(spec, banks) {
  const b = banks.find(x => x.cfg.file === spec.bankFile);
  if (!b) {
    throw new Error('专项卷 ' + spec.examId + ' 指向了不存在的题库文件：' + spec.bankFile);
  }
  const pool = b.data.questions.filter(q => q.category === spec.category);
  if (!pool.length) {
    throw new Error('专项卷 ' + spec.examId + ' 的知识点「' + spec.category + '」一道题都没有');
  }
  const snapshot = [];
  const picked = {};
  for (const t of [0, 1, 2, 3, 4]) {
    const slice = pool.filter(q => q.type === t).sort((a, c) => a.id - c.id)
      .slice(0, PAPER_TYPE_COUNT[t]);
    if (!slice.length) continue;
    picked[t] = slice.length;
    for (const q of slice) snapshot.push([q.id, PAPER_SCORE[t]]);
  }
  return { bank: b, snapshot, picked, categoryPool: pool.length,
    total: snapshot.reduce((a, [, s]) => a + s, 0) };
}

/** 用 KaTeX 编译文本里所有 $...$ 片段 */
function collectKatexProblems(where, text, problems, katex, counter) {
  if (!text) return;
  const re = /\$([^$]+)\$/g;
  let m;
  while ((m = re.exec(String(text))) !== null) {
    counter.n++;
    try {
      katex.renderToString(m[1], { throwOnError: true, strict: false });
    } catch (e) {
      problems.push(where + ': ' + e.message + '  TeX: ' + m[1]);
    }
  }
}

/**
 * 找出「$...$ 之外的 LaTeX 命令」。
 *
 * 前端 static/js/math.js 的 KaTeX auto-render 只认 $$...$$ / \[...\] / $...$ / \(...\)
 * 四种定界符，所以写在定界符之外的 \underline{...} 之类不会被渲染，
 * **学生会直接看到这串源码**。2026-09-16 实测踩到：高数题库 16 道填空题的答题空位
 * 写成了裸 `\underline{\qquad\qquad}`，答题页上原样显示给学生（填空题第 19 题）。
 * 这类错误 KaTeX 编译检查发现不了 —— 那些片段根本没被当成公式 —— 必须单独查。
 */
function collectBareTexProblems(where, text, problems) {
  if (!text) return;
  const masked = String(text).replace(/\$[^$]*\$/g, (m) => ' '.repeat(m.length));
  const hits = masked.match(/\\[a-zA-Z]+/g);
  if (hits) {
    problems.push(where + ': $...$ 之外的 LaTeX 命令 ' + [...new Set(hits)].join(',')
      + '（前端只渲染 $...$ 内的公式，学生会看到源码）');
  }
}

// ---------------------------------------------------------------- 转义

/**
 * 生成 SQL 字符串字面量。
 * 两个引擎对反斜杠的处理不同（见文件头），因此 mysql 模式要把每个反斜杠翻倍；
 * 单引号统一用 '' 转义，这在 MySQL 与 H2 上都成立。
 */
function sqlStr(value, mysql) {
  if (value === null || value === undefined) return 'NULL';
  let v = String(value).replace(/'/g, "''");
  if (mysql) v = v.replace(/\\/g, '\\\\');
  return "'" + v + "'";
}

function optionsJson(options) {
  if (!options) return null;
  return JSON.stringify(options.map(o => ({ label: String(o[0]), content: String(o[1]) })));
}

function snapshotJson(snapshot) {
  return JSON.stringify(snapshot.map(([questionId, score]) => ({ questionId, score })));
}

// ---------------------------------------------------------------- 产出

function buildSql(mysql, banks, onlyNew) {
  const L = [];
  const engine = mysql ? 'MySQL 8' : 'H2 (MODE=MySQL)';
  const target = onlyNew
    ? 'sql/data-shengben-add.sql'
    : (mysql ? 'sql/data-shengben.sql' : 'src/main/resources/data-h2-shengben.sql');
  const perBank = banks[0] ? banks[0].data.questions.length : BANK_SIZE;
  const totalQ = banks.reduce((a, b) => a + (onlyNew
    ? b.data.questions.filter(q => q.id >= b.cfg.addedIdFrom).length
    : b.data.questions.length), 0);

  L.push('-- ' + '='.repeat(74));
  L.push('--  四川专升本题库导入 —— ' + engine + ' 版' + (onlyNew ? '（只含新增题目）' : ''));
  L.push('--    高等数学 / 英语 / 计算机基础，每科 ' + perBank + ' 题 + ' + PAPERS_PER_BANK + ' 张固定题面模拟卷');
  L.push('--    本次导出 ' + banks.length + ' 科 / ' + totalQ + ' 道题');
  L.push('--');
  L.push('--  ⚠ 本文件由 scripts/gen-shengben-sql.js 生成，请勿手工修改。');
  L.push('--    题目内容改 scripts/data/shengben-*.json，然后重新生成两份 SQL。');
  L.push('--');
  if (onlyNew) {
    L.push('--  用途：往【已经在跑的库】里追加新题（不删任何已有数据）。');
    L.push('--  用法：mysql -uroot -p****** exam_system < sql/data-shengben-add.sql');
    L.push('--  生成：node scripts/gen-shengben-sql.js --only-new');
    L.push('--  与全量文件的区别：只写 addedIdFrom 之后的题目与新增试卷，且【不带 DELETE】。');
  } else if (mysql) {
    L.push('--  用法：mysql -uroot -p123456 exam_system < sql/data-shengben.sql');
  } else {
    L.push('--  用法：dev 档由 spring.sql.init 自动加载（见 application-dev.yml 的 data-locations）。');
    L.push('--  本文件用 MERGE ... KEY(id)，因此每次启动重复执行都是幂等的。');
  }
  L.push('--');
  L.push('--  为什么有两份：题面含 LaTeX，反斜杠在 MySQL 与 H2 的字符串字面量里');
  L.push('--  处理方式不同（实测：\'<\\frac>\' 在 MySQL 变成 <frac>，在 H2 保持 <\\frac>）。');
  L.push('--  同内容另见：' + (mysql ? 'src/main/resources/data-h2-shengben.sql' : 'sql/data-shengben.sql'));
  L.push('--');
  L.push('--  占用 ID：题库 1-3、题目 1001-1250、试卷 1-6');
  L.push('--  依赖：sys_user 需存在 id=2（teacher1）作为创建人');
  L.push('-- ' + '='.repeat(74));
  L.push('');

  if (mysql && !onlyNew) {
    L.push('-- 可重复执行：先清掉本文件导入的数据。');
    L.push('-- 若这些试卷已被学生作答，外键会阻止删除并报错 —— 这是刻意的，');
    L.push('-- 避免静默删掉真实作答数据。');
    const allExamIds = banks.flatMap(b => b.cfg.examIds).concat(SPECIAL_PAPERS.map(p => p.examId));
    L.push('DELETE FROM exam WHERE id IN (' + allExamIds.join(', ') + ');');
    L.push('DELETE FROM question WHERE bank_id IN (' + banks.map(b => b.cfg.bankId).join(', ') + ');');
    L.push('DELETE FROM question_bank WHERE id IN (' + banks.map(b => b.cfg.bankId).join(', ') + ');');
    L.push('');
  }

  for (const b of banks) {
    const { cfg, data, papers } = b;
    const bank = data.bank;
    const verb = mysql ? 'INSERT INTO' : 'MERGE INTO';
    const key = mysql ? '' : ' KEY(id)';
    const questions = onlyNew ? data.questions.filter(q => q.id >= cfg.addedIdFrom) : data.questions;

    L.push('-- ' + '-'.repeat(70));
    L.push('-- 题库 ' + cfg.bankId + '：' + bank.name
      + (onlyNew ? '（追加 ' + questions.length + ' 道新题）' : '（' + questions.length + ' 道题）'));
    L.push('-- ' + '-'.repeat(70));
    if (!onlyNew) {
      L.push(verb + ' question_bank (id, name, subject, description, created_by)' + key + ' VALUES');
      L.push('(' + [cfg.bankId, sqlStr(bank.name, mysql), sqlStr(bank.subject, mysql),
        sqlStr(bank.description, mysql), CREATED_BY].join(', ') + ');');
      L.push('');
    }

    for (const q of questions) {
      L.push('-- [' + TYPE_NAME[q.type] + '/' + DIFF_NAME[q.difficulty] + '/' + (q.category || '未标知识点') + '] '
        + String(q.content).replace(/\$/g, '').replace(/\s+/g, ' ').slice(0, 60));
      if (mysql) {
        L.push('INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (');
        L.push('  ' + [q.id, cfg.bankId, q.type, q.difficulty, sqlStr(q.content, mysql),
          sqlStr(optionsJson(q.options), mysql), sqlStr(q.answer, mysql), BANK_SCORE[q.type],
          sqlStr(q.analysis, mysql), sqlStr(q.category, mysql), 1, CREATED_BY].join(', ') + ');');
      } else {
        L.push('MERGE INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) KEY(id) VALUES (');
        L.push('  ' + [q.id, cfg.bankId, q.type, q.difficulty, sqlStr(q.content, mysql),
          sqlStr(optionsJson(q.options), mysql), sqlStr(q.answer, mysql), BANK_SCORE[q.type],
          sqlStr(q.analysis, mysql), sqlStr(q.category, mysql), 1, CREATED_BY].join(', ') + ');');
      }
    }
    L.push('');

    papers.forEach((snapshot, idx) => {
      // 「是否属于新增卷」由题面推导：整卷题目都在 addedIdFrom 之后 ⇒ 新卷。
      // 这样不需要额外配置，也不会因为配置写错而把旧卷当成新卷重发。
      const isNewPaper = snapshot.every(([id]) => id >= cfg.addedIdFrom);
      if (onlyNew && !isNewPaper) return;
      const examId = cfg.examIds[idx];
      const meta = (data.exams && data.exams[idx]) || {};
      const title = meta.title || (bank.name + ' 模拟卷（' + (CN_NUM[idx] || (idx + 1)) + '）');
      const description = meta.description || null;
      const duration = meta.duration || 120;
      const total = snapshot.reduce((a, [, s]) => a + s, 0);
      const passScore = meta.passScore || Math.ceil(total * 0.6);

      L.push('-- ' + title + '：' + snapshot.length + ' 题，总分 ' + total
        + '，及格 ' + passScore + '，' + duration + ' 分钟（题面固定，无抽题规则）');
      const paperType = meta.paperType || PAPER_TYPE_MOCK;
      // start_time / end_time 显式写 NULL（= 不限时段）。
      // 不写会沿用该行上已有的值：2026-09-19 云端实测，一张被人工结束过的卷子
      // 留下的 end_time 会让「到点自动结束」任务把刚灌进去的种子卷立刻判成已结束。
      const cols = '(id, title, description, bank_id, duration, total_score, pass_score, paper_type,'
        + ' question_snapshot, start_time, end_time, status, created_by)';
      const vals = [examId, sqlStr(title, mysql), sqlStr(description, mysql), cfg.bankId,
        duration, total, passScore, sqlStr(paperType, mysql),
        sqlStr(snapshotJson(snapshot), mysql), 'NULL', 'NULL', PAPER_STATUS, CREATED_BY].join(', ');
      L.push(verb + ' exam ' + cols + key + ' VALUES');
      L.push('  (' + vals + ');');
      L.push('');
    });

    // 专项训练卷（V4）：只在本题库，且只在全量模式下导出。
    // 「追加到已有库」的路径由 sql/data-shengben-v4-update.sql 负责 ——
    // 专项卷用的是**旧题**，--only-new 的「整卷题都在 addedIdFrom 之后」判据不适用。
    if (!onlyNew) {
      for (const spec of SPECIAL_PAPERS.filter(p => p.bankFile === cfg.file)) {
        const sp = deriveSpecialPaper(spec, banks);
        const passScore = spec.passScore || Math.ceil(sp.total * (spec.passRatio || 0.6));
        const byTypeText = [0, 1, 2, 3, 4].filter(t => sp.picked[t])
          .map(t => TYPE_NAME[t] + sp.picked[t]).join(' + ');
        L.push('-- ' + spec.title + '：' + sp.snapshot.length + ' 题，总分 ' + sp.total
          + '，及格 ' + passScore + '，' + spec.duration + ' 分钟');
        L.push('--   知识点「' + spec.category + '」共 ' + sp.categoryPool + ' 道题，本卷取 '
          + sp.snapshot.length + ' 道（' + byTypeText + '）；卷内每题都属于该专题');
        L.push('--   显式清空 start_time/end_time：否则这个 id 上残留的时间窗会让'
          + '「到点自动结束」任务把本卷立刻判成已结束（学生端看不到）');
        const cols = '(id, title, description, bank_id, duration, total_score, pass_score, paper_type,'
          + ' question_snapshot, start_time, end_time, status, created_by)';
        const vals = [spec.examId, sqlStr(spec.title, mysql), sqlStr(spec.description, mysql), cfg.bankId,
          spec.duration, sp.total, passScore, sqlStr(PAPER_TYPE_SPECIAL, mysql),
          sqlStr(snapshotJson(sp.snapshot), mysql), 'NULL', 'NULL', PAPER_STATUS, CREATED_BY].join(', ');
        L.push(verb + ' exam ' + cols + key + ' VALUES');
        L.push('  (' + vals + ');');
        L.push('');
      }
    }

    L.push('-- 演示数据到此为止。注意：这些卷子是「题面固定」的（question_snapshot 已写死），');
    L.push('-- 不经过智能组卷；若要体验随机组卷，请在「智能组卷」页面按规则现场生成。');
    L.push('');
  }

  return L.join('\n');
}

// ---------------------------------------------------------------- V4 补充脚本

/**
 * 给**已经在跑的库**补 V4 的字段与修正（幂等，可重复执行）。
 *
 * 为什么单独一份：全量文件开头是 DELETE（会被外键挡住，而且可能删到真数据），
 * `--only-new` 只导出「新题」，而这一轮变的恰恰是**已有题目**（打知识点标签）
 * 与**旧题里的一处笔误**，两者都落不进那两份文件。云端就是这样：库里有真实作答，
 * 只能用这种"只更新、不重建"的脚本。
 */
function buildV4UpdateSql(banks, specials) {
  const L = [];
  L.push('-- ' + '='.repeat(74));
  L.push('--  V4 补充脚本 —— 给已经在跑的 MySQL 库补三样东西：');
  L.push('--    1) question.category：每道题的知识点标签（题库管理筛选、专项抽题都要用）');
  L.push('--    2) 题目 1253 的选项笔误修正（A 选项原本与题干文字重复）');
  L.push('--    3) 两张专项训练卷（id 13 / 14）');
  L.push('--');
  L.push('--  幂等：可重复执行，不删除任何已有数据。');
  L.push('--  用法：mysql -uroot -p****** exam_system < sql/data-shengben-v4-update.sql');
  L.push('--  前提：Flyway 已跑过 V4（category / paper_type 两列已存在）。');
  L.push('-- ' + '='.repeat(74));
  L.push('');

  for (const b of banks) {
    const qs = b.data.questions;
    L.push('-- 题库 ' + b.cfg.bankId + '：' + b.data.bank.name + '，' + qs.length + ' 道题的知识点');
    // 用一条 CASE 而不是 100 条 UPDATE：好读、好 diff、执行也快
    L.push('UPDATE question SET category = CASE id');
    for (const q of qs) {
      L.push('  WHEN ' + q.id + ' THEN ' + sqlStr(q.category, true));
    }
    L.push('  ELSE category');
    L.push('END');
    L.push('WHERE id BETWEEN ' + b.cfg.idFrom + ' AND ' + (b.cfg.idFrom + qs.length - 1) + ';');
    L.push('');
  }

  const owner = banks.find(b => b.data.questions.some(q => q.id === 1253));
  if (owner) {
    const q = owner.data.questions.find(x => x.id === 1253);
    L.push('-- 题目 1253：选项笔误（A 选项文字与题干完全重复 —— 选择题的选项等于题干是废题）');
    L.push('UPDATE question SET options = ' + sqlStr(optionsJson(q.options), true) + ',');
    L.push('       analysis = ' + sqlStr(q.analysis, true));
    L.push('WHERE id = 1253;');
    L.push('');
  }

  for (const { spec, sp } of specials) {
    const pass = spec.passScore || Math.ceil(sp.total * (spec.passRatio || 0.6));
    L.push('-- ' + spec.title + '：' + sp.snapshot.length + ' 题 / 总分 ' + sp.total + ' / 及格 ' + pass);
    L.push('--   知识点「' + spec.category + '」共 ' + sp.categoryPool + " 道题，本卷取 " + sp.snapshot.length + ' 道');
    L.push('INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type,');
    L.push('                 question_snapshot, start_time, end_time, status, created_by) VALUES');
    L.push('  (' + [spec.examId, sqlStr(spec.title, true), sqlStr(spec.description, true), sp.bank.cfg.bankId,
      spec.duration, sp.total, pass, sqlStr(PAPER_TYPE_SPECIAL, true),
      sqlStr(snapshotJson(sp.snapshot), true), 'NULL', 'NULL', PAPER_STATUS, CREATED_BY].join(', ') + ')');
    L.push('ON DUPLICATE KEY UPDATE');
    L.push('  title = VALUES(title), description = VALUES(description),');
    L.push('  duration = VALUES(duration), total_score = VALUES(total_score), pass_score = VALUES(pass_score),');
    L.push('  paper_type = VALUES(paper_type), question_snapshot = VALUES(question_snapshot),');
    // ★ start_time / end_time 必须一起清成 NULL：
    //   否则这个 id 上残留的时间窗会让「到点自动结束」任务在下一分钟把本卷判成已结束，
    //   学生端看不到 —— 2026-09-19 云端实测踩到过。
    L.push('  start_time = NULL, end_time = NULL,');
    L.push('  status = VALUES(status);');
    L.push('');
  }
  return L.join('\n');
}

// ---------------------------------------------------------------- main

(function main() {
  let katex;
  try {
    katex = require(KATEX_PATH);
    if (typeof katex.renderToString !== 'function') throw new Error('renderToString 不存在');
  } catch (e) {
    console.error('无法加载 KaTeX（用于校验公式）: ' + e.message);
    console.error('请传入 katex 包路径，例如：node scripts/gen-shengben-sql.js /path/to/katex');
    process.exit(1);
  }

  const problems = [];
  const banks = [];
  const idOwner = new Map();

  for (const cfg of BANKS) {
    const data = loadBankData(cfg);
    banks.push({ cfg, data });
  }

  // 逐科自检
  for (const b of banks) {
    const stat = validateBank(b.cfg, b.data, problems);
    b.stat = stat;
    for (const q of b.data.questions) {
      if (idOwner.has(q.id)) {
        problems.push('题目 ID ' + q.id + ' 同时出现在 ' + idOwner.get(q.id) + ' 与 ' + b.cfg.file);
      }
      idOwner.set(q.id, b.cfg.file);
    }
  }

  if (problems.length) {
    console.error('数据检查未通过（' + problems.length + ' 项）：');
    problems.forEach(p => console.error('  - ' + p));
    process.exit(1);
  }

  // 推导试卷 + 校验覆盖
  const coverageProblems = [];
  for (const b of banks) {
    b.papers = derivePapers(b.cfg, b.data);
    b.papers.forEach((snapshot, idx) => {
      const where = b.cfg.file + ' 卷' + (CN_NUM[idx] || (idx + 1));
      const total = snapshot.reduce((a, [, s]) => a + s, 0);
      if (total !== PAPER_TOTAL) coverageProblems.push(where + ': 总分应为 ' + PAPER_TOTAL + '，实际 ' + total);
      const ids = snapshot.map(([id]) => id);
      if (new Set(ids).size !== ids.length) coverageProblems.push(where + ': 卷内题目重复');
      if (snapshot.length !== Object.keys(PAPER_TYPE_COUNT).reduce((a, t) => a + PAPER_TYPE_COUNT[t], 0)) {
        coverageProblems.push(where + ': 题数应为 ' + Object.keys(PAPER_TYPE_COUNT).reduce((a, t) => a + PAPER_TYPE_COUNT[t], 0) + '，实际 ' + snapshot.length);
      }
    });
    const all = b.papers.flat().map(([id]) => id);
    if (new Set(all).size !== b.data.questions.length) {
      coverageProblems.push(b.cfg.file + ': 全部试卷并集没有覆盖所有题目（' + new Set(all).size + '/' + b.data.questions.length + '）');
    }
  }
  if (coverageProblems.length) {
    console.error('试卷切分检查未通过：');
    coverageProblems.forEach(p => console.error('  - ' + p));
    process.exit(1);
  }

  // 专项训练卷：逐项自检（卷内每题都必须属于该专题，这是「专项」二字的全部含义）
  const specialProblems = [];
  const specials = [];
  for (const spec of SPECIAL_PAPERS) {
    let sp;
    try {
      sp = deriveSpecialPaper(spec, banks);
    } catch (e) {
      specialProblems.push(e.message);
      continue;
    }
    const wrong = sp.snapshot.filter(([id]) => {
      const b = sp.bank;
      const q = b.data.questions.find(x => x.id === id);
      return !q || q.category !== spec.category;
    }).map(([id]) => id);
    if (wrong.length) {
      specialProblems.push('专项卷 ' + spec.examId + ' 混入了非本专题的题目：' + wrong.slice(0, 5).join(', '));
    }
    if (sp.snapshot.length !== new Set(sp.snapshot.map(([id]) => id)).size) {
      specialProblems.push('专项卷 ' + spec.examId + ' 卷内题目重复');
    }
    if (!(sp.total > 0)) {
      specialProblems.push('专项卷 ' + spec.examId + ' 总分为 0');
    }
    // 与既有试卷 ID 撞车会导致覆盖，必须拦住
    const clash = banks.some(b => b.cfg.examIds.includes(spec.examId));
    if (clash) {
      specialProblems.push('专项卷 id ' + spec.examId + ' 与模拟卷 id 冲突');
    }
    specials.push({ spec, sp });
  }
  if (specialProblems.length) {
    console.error('专项训练卷检查未通过：');
    specialProblems.forEach(p => console.error('  - ' + p));
    process.exit(1);
  }

  // KaTeX
  const texProblems = [];
  const counter = { n: 0 };
  for (const b of banks) {
    for (const q of b.data.questions) {
      collectKatexProblems(b.cfg.file + ' Q' + q.id + '.content', q.content, texProblems, katex, counter);
      collectKatexProblems(b.cfg.file + ' Q' + q.id + '.answer', q.answer, texProblems, katex, counter);
      collectKatexProblems(b.cfg.file + ' Q' + q.id + '.analysis', q.analysis, texProblems, katex, counter);
      // 同时查「没被 $...$ 包起来」的 LaTeX（编译检查看不见它们）
      collectBareTexProblems(b.cfg.file + ' Q' + q.id + '.content', q.content, texProblems);
      collectBareTexProblems(b.cfg.file + ' Q' + q.id + '.answer', q.answer, texProblems);
      collectBareTexProblems(b.cfg.file + ' Q' + q.id + '.analysis', q.analysis, texProblems);
      if (Array.isArray(q.options)) {
        q.options.forEach(o => {
          collectKatexProblems(b.cfg.file + ' Q' + q.id + '.option' + o[0], o[1], texProblems, katex, counter);
          collectBareTexProblems(b.cfg.file + ' Q' + q.id + '.option' + o[0], o[1], texProblems);
        });
      }
    }
    for (const e of (b.data.exams || [])) {
      collectKatexProblems(b.cfg.file + ' exam.description', e.description, texProblems, katex, counter);
    }
  }
  if (texProblems.length) {
    console.error('LaTeX 校验未通过（' + texProblems.length + ' 处）：');
    texProblems.slice(0, 40).forEach(p => console.error('  - ' + p));
    if (texProblems.length > 40) console.error('  … 另有 ' + (texProblems.length - 40) + ' 处');
    process.exit(1);
  }

  const ONLY_NEW_MODE = ONLY_NEW;
  let mysqlSql = null;
  let h2Sql = null;
  let addSql = null;
  // V4 补充脚本两种模式都产出：它专门服务「已经在跑的库」
  const v4Sql = buildV4UpdateSql(banks, specials);
  fs.writeFileSync(path.join(ROOT, 'sql/data-shengben-v4-update.sql'), v4Sql, 'utf8');
  if (ONLY_NEW_MODE) {
    // 只导出新题：给「已经在跑的库」追加内容用（不带 DELETE，不动任何已有数据）
    addSql = buildSql(true, banks, true);
    fs.writeFileSync(path.join(ROOT, 'sql/data-shengben-add.sql'), addSql, 'utf8');
  } else {
    mysqlSql = buildSql(true, banks, false);
    h2Sql = buildSql(false, banks, false);
    fs.writeFileSync(path.join(ROOT, 'sql/data-shengben.sql'), mysqlSql, 'utf8');
    fs.writeFileSync(path.join(ROOT, 'src/main/resources/data-h2-shengben.sql'), h2Sql, 'utf8');
  }

  console.log('数据检查：通过（' + banks.reduce((a, b) => a + b.data.questions.length, 0) + ' 道题，ID 无重复）');
  console.log('LaTeX 校验：通过（' + counter.n + ' 个公式片段全部被 KaTeX 编译成功）');
  for (const b of banks) {
    const qs = b.data.questions;
    const byType = [0, 1, 2, 3, 4].map(t => TYPE_NAME[t] + ' ' + qs.filter(q => q.type === t).length).join(' / ');
    const byDiff = [1, 2, 3].map(d => DIFF_NAME[d] + ' ' + qs.filter(q => q.difficulty === d).length).join(' / ');
    console.log('');
    console.log('题库 ' + b.cfg.bankId + '：' + b.data.bank.name + '（subject=' + b.data.bank.subject + '）');
    console.log('  题目 ' + qs.length + ' 道：' + byType);
    console.log('  难度：' + byDiff);
    console.log('  知识点：' + Object.entries(b.stat.catCount).sort((x, y) => y[1] - x[1])
      .map(([k, v]) => k + ' ' + v).join(' / '));
    b.papers.forEach((snapshot, idx) => {
      const meta = (b.data.exams && b.data.exams[idx]) || {};
      const total = snapshot.reduce((a, [, s]) => a + s, 0);
      const pass = meta.passScore || Math.ceil(total * 0.6);
      console.log('  试卷 ' + b.cfg.examIds[idx] + '：' + (meta.title || '(未命名)')
        + '  ' + snapshot.length + ' 题 / 总分 ' + total + ' / 及格 ' + pass
        + ' / ' + (meta.duration || 120) + ' 分钟');
    });
    specials.filter(x => x.spec.bankFile === b.cfg.file).forEach(({ spec, sp }) => {
      const pass = spec.passScore || Math.ceil(sp.total * (spec.passRatio || 0.6));
      console.log('  专项卷 ' + spec.examId + '：' + spec.title
        + '  ' + sp.snapshot.length + ' 题 / 总分 ' + sp.total + ' / 及格 ' + pass
        + '（知识点「' + spec.category + '」共 ' + sp.categoryPool + ' 题）');
    });
  }
  console.log('');
  console.log('已写出：');
  if (ONLY_NEW_MODE) {
    console.log('  sql/data-shengben-add.sql                ' + addSql.length + ' 字节（MySQL，追加到已有库，不含 DELETE）');
    console.log('  （--only-new 模式下不重写全量 SQL 与 H2 种子文件，避免把 dev 库的种子截成一半）');
  } else {
    console.log('  sql/data-shengben.sql                    ' + mysqlSql.length + ' 字节（MySQL，手工导入）');
    console.log('  src/main/resources/data-h2-shengben.sql  ' + h2Sql.length + ' 字节（H2，dev 自动加载）');
  }
  console.log('  sql/data-shengben-v4-update.sql          ' + v4Sql.length + ' 字节（MySQL，给已在跑的库补知识点/笔误/专项卷，幂等）');
})();