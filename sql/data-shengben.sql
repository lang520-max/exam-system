-- ==========================================================================
--  四川专升本题库导入 —— MySQL 8 版
--    高等数学 / 英语 / 计算机基础，每科 100 题 + 4 张固定题面模拟卷
--    本次导出 3 科 / 300 道题
--
--  ⚠ 本文件由 scripts/gen-shengben-sql.js 生成，请勿手工修改。
--    题目内容改 scripts/data/shengben-*.json，然后重新生成两份 SQL。
--
--  用法：mysql -uroot -p123456 exam_system < sql/data-shengben.sql
--
--  为什么有两份：题面含 LaTeX，反斜杠在 MySQL 与 H2 的字符串字面量里
--  处理方式不同（实测：'<\frac>' 在 MySQL 变成 <frac>，在 H2 保持 <\frac>）。
--  同内容另见：src/main/resources/data-h2-shengben.sql
--
--  占用 ID：题库 1-3、题目 1001-1250、试卷 1-6
--  依赖：sys_user 需存在 id=2（teacher1）作为创建人
-- ==========================================================================

-- 可重复执行：先清掉本文件导入的数据。
-- 若这些试卷已被学生作答，外键会阻止删除并报错 —— 这是刻意的，
-- 避免静默删掉真实作答数据。
DELETE FROM exam WHERE id IN (1, 2, 7, 8, 3, 4, 9, 10, 5, 6, 11, 12, 101, 102);
DELETE FROM question WHERE bank_id IN (1, 2, 3);
DELETE FROM question_bank WHERE id IN (1, 2, 3);

-- ----------------------------------------------------------------------
-- 题库 1：四川专升本 · 高等数学题库（100 道题）
-- ----------------------------------------------------------------------
INSERT INTO question_bank (id, name, subject, description, created_by) VALUES
(1, '四川专升本 · 高等数学题库', '高等数学', '按四川省普通高校专升本《高等数学》考试大纲题型与难度原创命题，覆盖极限与连续、一元微分学与积分学、向量代数、多元微分学、二重积分、常微分方程与无穷级数，题面含 LaTeX。', 2);

-- [单选/简单/极限与连续] 极限 \lim\limits_{x\to 0}\dfrac{\tan 2x}{\sin 3x} 的值为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1001, 1, 0, 1, '极限 $\\lim\\limits_{x\\to 0}\\dfrac{\\tan 2x}{\\sin 3x}$ 的值为（　　）', '[{"label":"A","content":"$\\\\dfrac{3}{2}$"},{"label":"B","content":"$0$"},{"label":"C","content":"$1$"},{"label":"D","content":"$\\\\dfrac{2}{3}$"}]', 'D', 5, '当 $x\\to 0$ 时 $\\tan 2x\\sim 2x$，$\\sin 3x\\sim 3x$，二者都是乘除因子，可直接用等价无穷小替换，得 $\\lim\\limits_{x\\to 0}\\dfrac{2x}{3x}=\\dfrac{2}{3}$。选 A 是把分子、分母的系数写反了；选 C 误以为 $\\tan 2x$ 与 $\\sin 3x$ 是等价无穷小（比值应为 1）；选 B 则误判了分子的阶，把它看成比 $x$ 高阶的无穷小。注意等价无穷小替换只能用于乘除结构，不能对和差中的某一项单独替换。', '极限与连续', 1, 2);
-- [单选/简单/极限与连续] 函数 f(x)=\sqrt{4-x^{2}}+\dfrac{1}{x+1} 的定义域是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1002, 1, 0, 1, '函数 $f(x)=\\sqrt{4-x^{2}}+\\dfrac{1}{x+1}$ 的定义域是（　　）', '[{"label":"A","content":"$[-2,2]$"},{"label":"B","content":"$(-2,2)$"},{"label":"C","content":"$[-2,-1)\\\\cup(-1,2]$"},{"label":"D","content":"$[-2,-1)\\\\cup(-1,2)$"}]', 'C', 5, '根式要求 $4-x^{2}\\geq 0$，即 $-2\\leq x\\leq 2$；分式要求 $x+1\\neq 0$，即 $x\\neq -1$。两者取交集得 $[-2,-1)\\cup(-1,2]$。选 A 漏掉了分母不能为零；选 B 把根式的等号丢掉，而端点 $x=\\pm 2$ 处函数有定义；选 D 在剔除 $x=-1$ 时把 $x=2$ 也一并删掉了。', '极限与连续', 1, 2);
-- [单选/简单/极限与连续] 若函数 f(x)=\begin{cases}\dfrac{\sin x}{x}, & x\neq 0\\ a, & x=
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1003, 1, 0, 1, '若函数 $f(x)=\\begin{cases}\\dfrac{\\sin x}{x}, & x\\neq 0\\\\ a, & x=0\\end{cases}$ 在 $x=0$ 处连续，则常数 $a=$（　　）', '[{"label":"A","content":"$0$"},{"label":"B","content":"$1$"},{"label":"C","content":"$\\\\dfrac{1}{2}$"},{"label":"D","content":"$\\\\infty$"}]', 'B', 5, '在 $x=0$ 处连续等价于 $\\lim\\limits_{x\\to 0}f(x)=f(0)=a$。由第一重要极限 $\\lim\\limits_{x\\to 0}\\dfrac{\\sin x}{x}=1$，故 $a=1$。分段函数在分界点处的连续性必须用「极限值等于函数值」判断，不能把 $x=0$ 直接代入 $\\dfrac{\\sin x}{x}$；选 D 是把该点的极限误判为无穷大。', '极限与连续', 1, 2);
-- [单选/简单/导数与微分] 设 f(x)=x^{2}，则 \lim\limits_{\Delta x\to 0}\dfrac{f(1+\Delta 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1004, 1, 0, 1, '设 $f(x)=x^{2}$，则 $\\lim\\limits_{\\Delta x\\to 0}\\dfrac{f(1+\\Delta x)-f(1)}{\\Delta x}=$（　　）', '[{"label":"A","content":"$1$"},{"label":"B","content":"$2$"},{"label":"C","content":"$0$"},{"label":"D","content":"$2+\\\\Delta x$"}]', 'B', 5, '该极限正是导数定义式 $f''(1)=\\lim\\limits_{\\Delta x\\to 0}\\dfrac{f(1+\\Delta x)-f(1)}{\\Delta x}$。因 $f''(x)=2x$，故其值为 2。选 D 没有对 $\\Delta x$ 取极限，把极限过程当成了普通代数式；选 C 是把 $f(1)=1$ 与分子在 $\\Delta x=0$ 处的值混为一谈。', '导数与微分', 1, 2);
-- [单选/简单/导数与微分] 曲线 y=x^{3} 在点 (1,1) 处的切线斜率为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1005, 1, 0, 1, '曲线 $y=x^{3}$ 在点 $(1,1)$ 处的切线斜率为（　　）', '[{"label":"A","content":"$1$"},{"label":"B","content":"$2$"},{"label":"C","content":"$3$"},{"label":"D","content":"$\\\\dfrac{1}{3}$"}]', 'C', 5, '切线斜率等于导函数在切点处的值。由 $y''=3x^{2}$ 得 $k=y''(1)=3$。选 D 是取了导数的倒数，属于把斜率与倾斜角、反函数混在一起算的常见错误；选 B 是把 $(1,1)$ 的纵坐标 1 与 $x=1$ 相加得来的，没有数学依据。', '导数与微分', 1, 2);
-- [单选/中等/导数与微分] 设 y=\ln(1+x^{2})，则 \dfrac{dy}{dx}=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1006, 1, 0, 2, '设 $y=\\ln(1+x^{2})$，则 $\\dfrac{dy}{dx}=$（　　）', '[{"label":"A","content":"$\\\\dfrac{1}{1+x^{2}}$"},{"label":"B","content":"$\\\\dfrac{2x}{1+x}$"},{"label":"C","content":"$\\\\dfrac{x}{1+x^{2}}$"},{"label":"D","content":"$\\\\dfrac{2x}{1+x^{2}}$"}]', 'D', 5, '这是复合函数求导，外层是对数、内层是 $1+x^{2}$：$y''=\\dfrac{1}{1+x^{2}}\\cdot(1+x^{2})''=\\dfrac{2x}{1+x^{2}}$。选 A 漏乘了内层导数 $(1+x^{2})''=2x$；选 B 把内层的平方丢掉了；选 C 漏掉了内层导数中的因子 2。链式法则「外层先求导、再乘内层导数」是解这类题的关键。', '导数与微分', 1, 2);
-- [单选/中等/导数与微分] 由方程 x^{2}+y^{2}=1 确定的隐函数 y=y(x)，则 \dfrac{dy}{dx}=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1007, 1, 0, 2, '由方程 $x^{2}+y^{2}=1$ 确定的隐函数 $y=y(x)$，则 $\\dfrac{dy}{dx}=$（　　）', '[{"label":"A","content":"$-\\\\dfrac{x}{y}$"},{"label":"B","content":"$\\\\dfrac{x}{y}$"},{"label":"C","content":"$-\\\\dfrac{y}{x}$"},{"label":"D","content":"$\\\\dfrac{y}{x}$"}]', 'A', 5, '方程两端对 $x$ 求导，注意 $y$ 是 $x$ 的函数，$y^{2}$ 要用复合函数求导：$2x+2y\\dfrac{dy}{dx}=0$，解得 $\\dfrac{dy}{dx}=-\\dfrac{x}{y}$。选 C 把 $x$、$y$ 的位置写反；选 B、D 漏掉了移项产生的负号。隐函数求导的结果中出现 $y$ 是正常的，不必强行消去。', '导数与微分', 1, 2);
-- [单选/简单/导数与微分] 函数 y=x^{2}\sin x 的微分 dy=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1008, 1, 0, 1, '函数 $y=x^{2}\\sin x$ 的微分 $dy=$（　　）', '[{"label":"A","content":"$(2x\\\\cos x+x^{2}\\\\sin x)\\\\,dx$"},{"label":"B","content":"$(2x\\\\sin x-x^{2}\\\\cos x)\\\\,dx$"},{"label":"C","content":"$(2x\\\\sin x+x^{2}\\\\cos x)\\\\,dx$"},{"label":"D","content":"$(x^{2}\\\\sin x+2x\\\\cos x)\\\\,dx$"}]', 'C', 5, '由乘积求导法则 $y''=(x^{2})''\\sin x+x^{2}(\\sin x)''=2x\\sin x+x^{2}\\cos x$，故 $dy=y''\\,dx=(2x\\sin x+x^{2}\\cos x)\\,dx$。选 A 把 $\\sin x$ 与 $\\cos x$ 的导数用反了；选 B 把乘积法则中的加号错写成减号；选 D 把两个因子配错了三角函数，成了 $x^{2}\\sin x+2x\\cos x$，与正确结果的对应关系不对。', '导数与微分', 1, 2);
-- [单选/困难/极限与连续] 极限 \lim\limits_{x\to 0}\dfrac{x-\sin x}{x^{3}} 的值为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1009, 1, 0, 3, '极限 $\\lim\\limits_{x\\to 0}\\dfrac{x-\\sin x}{x^{3}}$ 的值为（　　）', '[{"label":"A","content":"$0$"},{"label":"B","content":"$\\\\dfrac{1}{6}$"},{"label":"C","content":"$\\\\dfrac{1}{3}$"},{"label":"D","content":"$\\\\dfrac{1}{2}$"}]', 'B', 5, '分子、分母都趋于 0，属 $\\dfrac{0}{0}$ 型。用泰勒公式 $\\sin x=x-\\dfrac{x^{3}}{6}+o(x^{3})$，得 $x-\\sin x=\\dfrac{x^{3}}{6}+o(x^{3})$，故极限为 $\\dfrac{1}{6}$。若用洛必达法则需连续求导三次，系数容易算错。选 A 是把 $\\sin x\\sim x$ 用在了加减结构上（替换后分子恒为 0），这是等价无穷小替换最典型的误用；选 C、D 对应把三阶项系数记成 $\\dfrac{1}{3}$ 或 $\\dfrac{1}{2}$。', '极限与连续', 1, 2);
-- [单选/中等/中值定理与导数应用] 函数 f(x)=x^{3}-3x^{2}+2 的极小值为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1010, 1, 0, 2, '函数 $f(x)=x^{3}-3x^{2}+2$ 的极小值为（　　）', '[{"label":"A","content":"$-2$"},{"label":"B","content":"$2$"},{"label":"C","content":"$0$"},{"label":"D","content":"$-6$"}]', 'A', 5, '$f''(x)=3x^{2}-6x=3x(x-2)$，驻点为 $x=0$ 与 $x=2$。又 $f''''(x)=6x-6$，$f''''(2)=6>0$，故 $x=2$ 处取极小值 $f(2)=8-12+2=-2$。选 B 是极大值 $f(0)=2$，与极小值混淆；选 C 中 $f(1)=0$ 只是区间内部的一个函数值，$x=1$ 不是驻点；选 D 的 $-6$ 不是函数值，$f''''(2)=6$ 只能用来判断极值类型。', '中值定理与导数应用', 1, 2);
-- [单选/中等/中值定理与导数应用] 曲线 y=x^{3}-6x^{2} 的拐点的横坐标为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1011, 1, 0, 2, '曲线 $y=x^{3}-6x^{2}$ 的拐点的横坐标为（　　）', '[{"label":"A","content":"$0$"},{"label":"B","content":"$4$"},{"label":"C","content":"$6$"},{"label":"D","content":"$2$"}]', 'D', 5, '$y''=3x^{2}-12x$，$y''''=6x-12$。令 $y''''=0$ 得 $x=2$，且 $x<2$ 时 $y''''<0$（凸）、$x>2$ 时 $y''''>0$（凹），凹凸性发生改变，故 $x=2$ 是拐点的横坐标。选 B 的 $x=4$ 是令 $y''=0$ 得到的驻点，驻点与拐点是两个不同的概念；选 A、C 既不是 $y''''=0$ 的根也不是 $y''=0$ 的根。', '中值定理与导数应用', 1, 2);
-- [单选/简单/一元函数积分学] 不定积分 \int\dfrac{1}{1+x^{2}}\,dx=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1012, 1, 0, 1, '不定积分 $\\int\\dfrac{1}{1+x^{2}}\\,dx=$（　　）', '[{"label":"A","content":"$\\\\ln(1+x^{2})+C$"},{"label":"B","content":"$\\\\arcsin x+C$"},{"label":"C","content":"$\\\\arctan x+C$"},{"label":"D","content":"$\\\\dfrac{1}{1+x^{2}}+C$"}]', 'C', 5, '基本积分公式：$\\int\\dfrac{1}{1+x^{2}}\\,dx=\\arctan x+C$。选 A 是把它与 $\\int\\dfrac{2x}{1+x^{2}}\\,dx=\\ln(1+x^{2})+C$ 混淆了；选 B 对应的是 $\\int\\dfrac{1}{\\sqrt{1-x^{2}}}\\,dx$，把根号丢了；选 D 直接拿被积函数当原函数，忘记了积分是求导的逆运算。', '一元函数积分学', 1, 2);
-- [单选/中等/一元函数积分学] 定积分 \int_{0}^{1}xe^{x^{2}}\,dx 的值为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1013, 1, 0, 2, '定积分 $\\int_{0}^{1}xe^{x^{2}}\\,dx$ 的值为（　　）', '[{"label":"A","content":"$\\\\dfrac{e-1}{2}$"},{"label":"B","content":"$e-1$"},{"label":"C","content":"$\\\\dfrac{e}{2}$"},{"label":"D","content":"$1$"}]', 'A', 5, '令 $u=x^{2}$，则 $du=2x\\,dx$，即 $x\\,dx=\\dfrac{1}{2}du$，且 $x:0\\to 1$ 对应 $u:0\\to 1$，故原式 $=\\dfrac{1}{2}\\int_{0}^{1}e^{u}\\,du=\\dfrac{1}{2}(e-1)$。选 B 漏掉了换元时产生的系数 $\\dfrac{1}{2}$；选 C 把 $\\int_{0}^{1}e^{u}\\,du$ 误算成 $e$，漏掉了下限贡献的 $e^{0}=1$；选 D 是只算了 $\\int_{0}^{1}e^{u}\\,du$ 中指数为 0 的特殊值。', '一元函数积分学', 1, 2);
-- [单选/中等/一元函数积分学] 设 F(x)=\int_{0}^{x}\sin(t^{2})\,dt，则 F'(x)=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1014, 1, 0, 2, '设 $F(x)=\\int_{0}^{x}\\sin(t^{2})\\,dt$，则 $F''(x)=$（　　）', '[{"label":"A","content":"$2x\\\\sin(x^{2})$"},{"label":"B","content":"$\\\\cos(x^{2})$"},{"label":"C","content":"$\\\\sin(x^{2})-\\\\sin 0$"},{"label":"D","content":"$\\\\sin(x^{2})$"}]', 'D', 5, '变限积分求导公式：若 $f$ 连续且 $F(x)=\\int_{a}^{x}f(t)\\,dt$，则 $F''(x)=f(x)$。这里把上限 $x$ 直接代入被积函数即得 $F''(x)=\\sin(x^{2})$。选 A 多乘了 $2x$，那是上限为 $x^{2}$ 时链式法则的结果；选 B 把积分与求导弄混；选 C 出现了 $\\sin 0$，说明把变限积分当成了与 $x$ 无关的定值。', '一元函数积分学', 1, 2);
-- [单选/中等/一元函数积分学] 由曲线 y=x^{2} 与直线 y=x 所围成的平面图形的面积为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1015, 1, 0, 2, '由曲线 $y=x^{2}$ 与直线 $y=x$ 所围成的平面图形的面积为（　　）', '[{"label":"A","content":"$\\\\dfrac{1}{3}$"},{"label":"B","content":"$-\\\\dfrac{1}{6}$"},{"label":"C","content":"$\\\\dfrac{1}{6}$"},{"label":"D","content":"$\\\\dfrac{1}{2}$"}]', 'C', 5, '先求交点：由 $x^{2}=x$ 得 $x=0$、$x=1$；在 $(0,1)$ 内直线在曲线上方，故面积 $S=\\int_{0}^{1}(x-x^{2})\\,dx=\\left[\\dfrac{x^{2}}{2}-\\dfrac{x^{3}}{3}\\right]_{0}^{1}=\\dfrac{1}{2}-\\dfrac{1}{3}=\\dfrac{1}{6}$。选 A 只积了 $\\int_{0}^{1}x\\,dx$ 这一项；选 D 只保留了前一项的值；选 B 是把被积函数写成 $x^{2}-x$，上、下曲线颠倒导致差一个负号——面积必须为正，符号一错就能立刻排除。', '一元函数积分学', 1, 2);
-- [单选/简单/向量代数与空间解析几何] 已知向量 \vec{a}=(1,2,-1)，\vec{b}=(2,0,1)，则 \vec{a}\cdot\vec{b}=
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1016, 1, 0, 1, '已知向量 $\\vec{a}=(1,2,-1)$，$\\vec{b}=(2,0,1)$，则 $\\vec{a}\\cdot\\vec{b}=$（　　）', '[{"label":"A","content":"$0$"},{"label":"B","content":"$1$"},{"label":"C","content":"$2$"},{"label":"D","content":"$3$"}]', 'B', 5, '数量积按对应坐标相乘再求和：$\\vec{a}\\cdot\\vec{b}=1\\times 2+2\\times 0+(-1)\\times 1=2-1=1$。选 A 可能是漏算了第三项 $(-1)\\times 1$；选 C 只加了第一项的乘积；选 D 是把各坐标绝对值相加的错误算法。注意数量积的结果是一个数而不是向量，若两非零向量的数量积为 0 才说明它们互相垂直。', '向量代数与空间解析几何', 1, 2);
-- [单选/简单/多元函数微积分] 设 z=x^{2}y+y^{3}，则 \dfrac{\partial z}{\partial y}=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1017, 1, 0, 1, '设 $z=x^{2}y+y^{3}$，则 $\\dfrac{\\partial z}{\\partial y}=$（　　）', '[{"label":"A","content":"$2xy$"},{"label":"B","content":"$x^{2}+y^{2}$"},{"label":"C","content":"$2xy+3y^{2}$"},{"label":"D","content":"$x^{2}+3y^{2}$"}]', 'D', 5, '求 $\\dfrac{\\partial z}{\\partial y}$ 时把 $x$ 看作常数：$x^{2}y$ 对 $y$ 求导得 $x^{2}$，$y^{3}$ 求导得 $3y^{2}$，故结果为 $x^{2}+3y^{2}$。选 A 是 $\\dfrac{\\partial z}{\\partial x}=2xy$，属于对 $x$ 求偏导的结果；选 B 把 $y^{3}$ 的导数算成了 $y^{2}$；选 C 混入了 $2xy$，说明没有把 $x$ 当成常数。', '多元函数微积分', 1, 2);
-- [单选/困难/多元函数微积分] 函数 z=x^{3}+y^{3}-3xy 的极小值为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1018, 1, 0, 3, '函数 $z=x^{3}+y^{3}-3xy$ 的极小值为（　　）', '[{"label":"A","content":"$-1$"},{"label":"B","content":"$0$"},{"label":"C","content":"$2$"},{"label":"D","content":"$-2$"}]', 'A', 5, '令 $z_{x}=3x^{2}-3y=0$、$z_{y}=3y^{2}-3x=0$，消元得 $x^{4}=x$，实驻点为 $(0,0)$ 与 $(1,1)$。记 $A=z_{xx}=6x$、$B=z_{xy}=-3$、$C=z_{yy}=6y$，则 $AC-B^{2}=36xy-9$。在 $(0,0)$ 处 $AC-B^{2}=-9<0$，是鞍点；在 $(1,1)$ 处 $AC-B^{2}=27>0$ 且 $A=6>0$，取极小值 $z(1,1)=1+1-3=-1$。选 B 是鞍点 $(0,0)$ 处的函数值；选 C 只算了 $x^{3}+y^{3}=2$，漏掉非二次项 $-3xy$；选 D 是把 $-3xy$ 的数值与符号都算错的结果。', '多元函数微积分', 1, 2);
-- [单选/中等/多元函数微积分] 二次积分 \int_{0}^{1}dx\int_{0}^{x}f(x,y)\,dy 交换积分次序后为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1019, 1, 0, 2, '二次积分 $\\int_{0}^{1}dx\\int_{0}^{x}f(x,y)\\,dy$ 交换积分次序后为（　　）', '[{"label":"A","content":"$\\\\int_{0}^{1}dy\\\\int_{0}^{y}f(x,y)\\\\,dx$"},{"label":"B","content":"$\\\\int_{0}^{1}dy\\\\int_{1}^{y}f(x,y)\\\\,dx$"},{"label":"C","content":"$\\\\int_{0}^{1}dy\\\\int_{y}^{1}f(x,y)\\\\,dx$"},{"label":"D","content":"$\\\\int_{0}^{1}dy\\\\int_{0}^{1}f(x,y)\\\\,dx$"}]', 'C', 5, '原积分对应的积分区域 $D$ 是由 $y=0$、$y=x$ 与 $x=1$ 围成的三角形，即 $0\\leq x\\leq 1$、$0\\leq y\\leq x$。改为先对 $x$ 积分：$y$ 的范围是 $0\\leq y\\leq 1$，对固定的 $y$，$x$ 从直线 $x=y$ 取到 $x=1$，即 $y\\leq x\\leq 1$，故交换次序后为 $\\int_{0}^{1}dy\\int_{y}^{1}f(x,y)\\,dx$。选 A 把内外层的上下限对应关系弄反；选 B 的上限小于下限，积分无意义；选 D 把三角形区域误当成了正方形。', '多元函数微积分', 1, 2);
-- [单选/中等/微分方程] 微分方程 y'-y=e^{x} 的通解为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1020, 1, 0, 2, '微分方程 $y''-y=e^{x}$ 的通解为（　　）', '[{"label":"A","content":"$y=Ce^{x}$"},{"label":"B","content":"$y=(x+C)e^{x}$"},{"label":"C","content":"$y=xe^{x}+C$"},{"label":"D","content":"$y=Ce^{x}+x$"}]', 'B', 5, '这是一阶线性非齐次方程，用积分因子法最简：方程两端同乘 $e^{-x}$ 得 $(ye^{-x})''=1$，积分得 $ye^{-x}=x+C$，即 $y=(x+C)e^{x}$。选 A 是齐次方程 $y''-y=0$ 的通解，漏掉了非齐次项；选 C、D 把任意常数 $C$ 放在了括号外，代入验证可知它们并不满足原方程（如 $y=xe^{x}+C$ 时 $y''-y=e^{x}-C$）。', '微分方程', 1, 2);
-- [多选/简单/极限与连续] 下列变量在 x\to 0 时是无穷小量的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1021, 1, 1, 1, '下列变量在 $x\\to 0$ 时是无穷小量的有（　　）', '[{"label":"A","content":"$\\\\dfrac{1}{x}$"},{"label":"B","content":"$x^{2}$"},{"label":"C","content":"$\\\\sin x$"},{"label":"D","content":"$1-\\\\cos x$"}]', 'BCD', 5, '无穷小量指极限为 0 的变量。$x^{2}\\to 0$、$\\sin x\\to 0$、$1-\\cos x\\to 0$，三者都是无穷小量；而 $\\dfrac{1}{x}\\to\\infty$ 是无穷大量，故 A 不选。注意「无穷小」只要求极限为 0，并不要求与谁同阶：$1-\\cos x\\sim\\dfrac{x^{2}}{2}$ 与 $x^{2}$ 同阶、$\\sin x\\sim x$，它们都是无穷小量。所以本题应选 BCD。', '极限与连续', 1, 2);
-- [多选/简单/导数与微分] 下列求导结果正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1022, 1, 1, 1, '下列求导结果正确的有（　　）', '[{"label":"A","content":"$(\\\\cos x)''=\\\\sin x$"},{"label":"B","content":"$(\\\\sin x)''=\\\\cos x$"},{"label":"C","content":"$(\\\\ln x)''=\\\\dfrac{1}{x}$"},{"label":"D","content":"$(e^{x})''=e^{x}$"}]', 'BCD', 5, '$(\\sin x)''=\\cos x$、$(\\ln x)''=\\dfrac{1}{x}$、$(e^{x})''=e^{x}$ 都正确。A 错误：$(\\cos x)''=-\\sin x$，余弦求导必须带负号，这是最常记错的一条。换个角度检验：$\\cos x$ 在 $x=0$ 附近单调递减，其导数在 $x=0$ 处应为负，而 $\\sin 0=0$ 恰好掩盖了符号问题。所以本题应选 BCD。', '导数与微分', 1, 2);
-- [多选/简单/一元函数积分学] 下列不定积分计算结果正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1023, 1, 1, 1, '下列不定积分计算结果正确的有（　　）', '[{"label":"A","content":"$\\\\int 0\\\\,dx=C$"},{"label":"B","content":"$\\\\int\\\\cos x\\\\,dx=\\\\sin x+C$"},{"label":"C","content":"$\\\\int\\\\sin x\\\\,dx=\\\\cos x+C$"},{"label":"D","content":"$\\\\int e^{x}\\\\,dx=e^{x}+C$"}]', 'ABD', 5, '常数的积分为 $\\int 0\\,dx=C$；$\\int\\cos x\\,dx=\\sin x+C$；$\\int e^{x}\\,dx=e^{x}+C$，三者都正确。C 错误：$\\int\\sin x\\,dx=-\\cos x+C$，漏掉了负号，把「正弦的原函数是负余弦」记成了余弦。检验积分结果是否正确的通用办法是对右端求导，看它是否等于被积函数，$-\\cos x$ 求导得 $\\sin x$ 正说明 C 的符号写反了。所以本题应选 ABD。', '一元函数积分学', 1, 2);
-- [多选/中等/中值定理与导数应用] 下列函数在给定的闭区间上满足罗尔定理条件的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1024, 1, 1, 2, '下列函数在给定的闭区间上满足罗尔定理条件的有（　　）', '[{"label":"A","content":"$f(x)=|x|$，区间 $[-1,1]$"},{"label":"B","content":"$f(x)=x^{2}-1$，区间 $[-1,1]$"},{"label":"C","content":"$f(x)=\\\\sin x$，区间 $[0,\\\\pi]$"},{"label":"D","content":"$f(x)=\\\\dfrac{1}{x}$，区间 $[-1,1]$"}]', 'BC', 5, '罗尔定理的三个条件是：在闭区间上连续、在开区间内可导、两端点函数值相等。B 中 $f$ 是多项式，处处可导且 $f(-1)=f(1)=0$；C 中 $f$ 处处可导且 $f(0)=f(\\pi)=0$，两者都满足。A 中 $f(x)=|x|$ 在 $x=0$ 处不可导（左右导数分别为 $-1$ 与 $1$）；D 中 $\\dfrac{1}{x}$ 在 $x=0$ 处无定义，从而在 $[-1,1]$ 上不连续，两者都不满足。所以本题应选 BC。', '中值定理与导数应用', 1, 2);
-- [多选/中等/一元函数积分学] 设 f(x) 在 [a,b] 上连续，则下列结论正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1025, 1, 1, 2, '设 $f(x)$ 在 $[a,b]$ 上连续，则下列结论正确的有（　　）', '[{"label":"A","content":"$\\\\int_{a}^{b}f(x)\\\\,dx=-\\\\int_{b}^{a}f(x)\\\\,dx$"},{"label":"B","content":"$\\\\int_{a}^{b}f(x)\\\\,dx=\\\\int_{a}^{b}f(t)\\\\,dt$"},{"label":"C","content":"存在唯一的 $\\\\xi\\\\in[a,b]$，使 $\\\\int_{a}^{b}f(x)\\\\,dx=f(\\\\xi)(b-a)$"},{"label":"D","content":"若 $f(x)\\\\geq 0$，则 $\\\\int_{a}^{b}f(x)\\\\,dx\\\\geq 0$"}]', 'ABD', 5, 'A 是定积分交换上下限变号的基本性质；B 说明定积分的值与积分变量用什么字母无关；D 是定积分的保号性——非负连续函数的定积分不会为负，三者都正确。C 错误：积分中值定理只保证「至少存在一个」这样的 $\\xi$，一般并不唯一，例如 $f(x)=1$ 时区间内任意一点都满足等式，「唯一」二字使结论不成立。所以本题应选 ABD。', '一元函数积分学', 1, 2);
-- [多选/中等/级数与广义积分] 下列级数中收敛的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1026, 1, 1, 2, '下列级数中收敛的有（　　）', '[{"label":"A","content":"$\\\\sum\\\\limits_{n=1}^{\\\\infty}\\\\dfrac{1}{n^{2}}$"},{"label":"B","content":"$\\\\sum\\\\limits_{n=1}^{\\\\infty}\\\\dfrac{1}{\\\\sqrt{n}}$"},{"label":"C","content":"$\\\\sum\\\\limits_{n=1}^{\\\\infty}\\\\dfrac{1}{2^{n}}$"},{"label":"D","content":"$\\\\sum\\\\limits_{n=1}^{\\\\infty}\\\\dfrac{1}{n(n+1)}$"}]', 'ACD', 5, 'A 是 $p=2>1$ 的 $p$-级数，收敛；C 是公比 $\\dfrac{1}{2}$ 的等比级数，收敛；D 的通项可拆为 $\\dfrac{1}{n}-\\dfrac{1}{n+1}$，部分和 $S_{n}=1-\\dfrac{1}{n+1}\\to 1$，收敛。B 是 $p=\\dfrac{1}{2}\\leq 1$ 的 $p$-级数，发散；不能因为 $\\dfrac{1}{\\sqrt{n}}\\to 0$ 就认为它收敛，通项趋于 0 只是收敛的必要条件。所以本题应选 ACD。', '级数与广义积分', 1, 2);
-- [多选/困难/多元函数微积分] 设二元函数 z=f(x,y) 在点 (x_{0},y_{0}) 的某邻域内有定义，则下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1027, 1, 1, 3, '设二元函数 $z=f(x,y)$ 在点 $(x_{0},y_{0})$ 的某邻域内有定义，则下列说法正确的有（　　）', '[{"label":"A","content":"若 $f(x,y)$ 在点 $(x_{0},y_{0})$ 的两个偏导数都存在，则它在该点可微"},{"label":"B","content":"若 $f(x,y)$ 在点 $(x_{0},y_{0})$ 可微，则它在该点连续"},{"label":"C","content":"若 $f(x,y)$ 在点 $(x_{0},y_{0})$ 可微，则它的两个偏导数在该点都存在"},{"label":"D","content":"若 $f(x,y)$ 在点 $(x_{0},y_{0})$ 的两个偏导数都存在，则它在该点连续"}]', 'BC', 5, '可微是比偏导数存在更强的条件：可微能推出偏导数存在，也能推出连续，故 B、C 正确。A 错在把「偏导数存在」当成了可微的充分条件——一元函数中可导与可微等价，但二元函数里两个偏导数都存在并不能保证可微。D 同样错误：偏导数只刻画沿两条坐标轴方向的变化率，这两个方向上的信息不足以控制函数在其他方向上的行为，函数仍可能间断。所以本题应选 BC。', '多元函数微积分', 1, 2);
-- [多选/困难/微分方程] 关于微分方程 y''-3y'+2y=0，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1028, 1, 1, 3, '关于微分方程 $y''''-3y''+2y=0$，下列说法正确的有（　　）', '[{"label":"A","content":"特征方程为 $r^{2}-3r+2=0$"},{"label":"B","content":"通解为 $y=C_{1}e^{-x}+C_{2}e^{-2x}$，其中 $C_{1}$、$C_{2}$ 为任意常数"},{"label":"C","content":"通解为 $y=C_{1}e^{x}+C_{2}e^{2x}$，其中 $C_{1}$、$C_{2}$ 为任意常数"},{"label":"D","content":"$y=e^{x}$ 是该方程的一个解"}]', 'ACD', 5, '二阶常系数齐次线性方程对应的特征方程是 $r^{2}-3r+2=0$，A 正确；解特征方程得两个不相等的实根 $r=1$、$r=2$，故通解为 $y=C_{1}e^{x}+C_{2}e^{2x}$，C 正确而 B 错误——$e^{-x}$、$e^{-2x}$ 对应的是特征方程 $r^{2}+3r+2=0$，即原方程一次项系数符号写反的情形。D 中把 $y=e^{x}$ 代入左端得 $e^{x}-3e^{x}+2e^{x}=0$，成立，它是取 $C_{1}=1$、$C_{2}=0$ 得到的特解。所以本题应选 ACD。', '微分方程', 1, 2);
-- [判断/简单/极限与连续] 两个无穷小量的乘积仍是无穷小量。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1029, 1, 2, 1, '两个无穷小量的乘积仍是无穷小量。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '正确。设 $\\alpha\\to 0$、$\\beta\\to 0$，则 $|\\alpha\\beta|$ 趋于 0 的速度不比 $|\\alpha|$、$|\\beta|$ 慢，乘积仍是无穷小量，即有限个无穷小量之积仍为无穷小量。容易混淆的是「两个无穷小量之商」：其极限并不确定，可能为 0、非零常数，也可能为 $\\infty$，因此不能说两个无穷小量之商一定是无穷小量。', '极限与连续', 1, 2);
-- [判断/简单/导数与微分] 若函数 f(x) 在点 x_{0} 处可导，则 f(x) 在点 x_{0} 处必连续。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1030, 1, 2, 1, '若函数 $f(x)$ 在点 $x_{0}$ 处可导，则 $f(x)$ 在点 $x_{0}$ 处必连续。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '正确。由导数的定义 $\\lim\\limits_{\\Delta x\\to 0}\\dfrac{f(x_{0}+\\Delta x)-f(x_{0})}{\\Delta x}=f''(x_{0})$，把分子写成「差商乘 $\\Delta x$」，即得 $\\lim\\limits_{\\Delta x\\to 0}[f(x_{0}+\\Delta x)-f(x_{0})]=f''(x_{0})\\cdot 0=0$，这正是 $f$ 在 $x_{0}$ 处连续的定义。反之不成立：$f(x)=|x|$ 在 $x=0$ 处连续但不可导，所以「连续」推不出「可导」。', '导数与微分', 1, 2);
-- [判断/简单/一元函数积分学] 定积分 \int_{-1}^{1}x^{3}\,dx=0。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1031, 1, 2, 1, '定积分 $\\int_{-1}^{1}x^{3}\\,dx=0$。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '正确。$x^{3}$ 是奇函数，而积分区间 $[-1,1]$ 关于原点对称，奇函数在对称区间上的定积分为 0。也可直接算出：原函数为 $\\dfrac{x^{4}}{4}$，代入得 $\\dfrac{1}{4}-\\dfrac{1}{4}=0$。要注意这一结论只对奇函数成立：偶函数 $x^{2}$ 在同一区间上的积分为 $\\dfrac{2}{3}$ 而不是 0，判断前必须先认清奇偶性。', '一元函数积分学', 1, 2);
-- [判断/简单/微分方程] 微分方程 y'+2y=0 的通解为 y=Ce^{-2x}（C 为任意常数）。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1032, 1, 2, 1, '微分方程 $y''+2y=0$ 的通解为 $y=Ce^{-2x}$（$C$ 为任意常数）。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '正确。这是可分离变量方程，分离得 $\\dfrac{dy}{y}=-2\\,dx$，两端积分有 $\\ln|y|=-2x+C_{1}$，即 $y=Ce^{-2x}$。也可代入验证：$y''=-2Ce^{-2x}$，于是 $y''+2y=-2Ce^{-2x}+2Ce^{-2x}=0$，恒成立，说明该函数族确为通解。', '微分方程', 1, 2);
-- [判断/简单/级数与广义积分] 若级数 \sum\limits_{n=1}^{\infty}u_{n} 收敛，则 \lim\limits_{n\to\i
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1033, 1, 2, 1, '若级数 $\\sum\\limits_{n=1}^{\\infty}u_{n}$ 收敛，则 $\\lim\\limits_{n\\to\\infty}u_{n}=0$。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '正确。这是级数收敛的必要条件：设部分和 $S_{n}\\to S$，则 $u_{n}=S_{n}-S_{n-1}\\to S-S=0$。但反过来不成立，$\\sum\\limits_{n=1}^{\\infty}\\dfrac{1}{n}$ 的通项趋于 0 而级数发散，所以该条件只能用来判定发散——通项不趋于 0 时级数必发散，不能用它来判定收敛。', '级数与广义积分', 1, 2);
-- [判断/中等/中值定理与导数应用] 函数 f(x) 的驻点一定是它的极值点。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1034, 1, 2, 2, '函数 $f(x)$ 的驻点一定是它的极值点。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '错误。驻点只要求 $f''(x)=0$，未必是极值点。反例：$f(x)=x^{3}$，$f''(x)=3x^{2}$，$x=0$ 是驻点，但 $f$ 在 $(-\\infty,+\\infty)$ 上单调递增，$x=0$ 不是极值点。另一方面极值点也可能出现在导数不存在处（如 $f(x)=|x|$ 的 $x=0$），所以「驻点」与「极值点」互不包含，判断驻点是否为极值点必须看导数是否变号。', '中值定理与导数应用', 1, 2);
-- [判断/中等/一元函数积分学] 若 f(x) 在 [a,b] 上连续，则变限积分 F(x)=\int_{a}^{x}f(t)\,dt 在 [a,b] 上
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1035, 1, 2, 2, '若 $f(x)$ 在 $[a,b]$ 上连续，则变限积分 $F(x)=\\int_{a}^{x}f(t)\\,dt$ 在 $[a,b]$ 上可导，且 $F''(x)=f(x)$。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '正确。这正是微积分基本定理（变限积分求导公式）：被积函数连续时，变限积分就是它的一个原函数。由此可推出牛顿-莱布尼茨公式 $\\int_{a}^{b}f(x)\\,dx=F(b)-F(a)$，打通了微分与积分的联系。需要注意该结论中「$f$ 连续」不可缺少，若 $f$ 只有可积性、存在跳跃间断，$F$ 可能在某些点不可导。', '一元函数积分学', 1, 2);
-- [判断/中等/向量代数与空间解析几何] 对两个非零向量 \vec{a}、\vec{b}，\vec{a}\cdot\vec{b}=0 是 \vec{a}\perp
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1036, 1, 2, 2, '对两个非零向量 $\\vec{a}$、$\\vec{b}$，$\\vec{a}\\cdot\\vec{b}=0$ 是 $\\vec{a}\\perp\\vec{b}$ 的充要条件。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '正确。由 $\\vec{a}\\cdot\\vec{b}=|\\vec{a}||\\vec{b}|\\cos\\theta$（$\\theta$ 为两向量的夹角），当 $\\vec{a}$、$\\vec{b}$ 都非零时 $|\\vec{a}||\\vec{b}|\\neq 0$，故 $\\vec{a}\\cdot\\vec{b}=0$ 与 $\\cos\\theta=0$ 等价，即 $\\theta=\\dfrac{\\pi}{2}$，两者互相垂直。强调「非零」是必要的：零向量与任何向量的数量积都是 0，但通常不讨论零向量与别的向量是否垂直。', '向量代数与空间解析几何', 1, 2);
-- [填空/简单/极限与连续] 极限 \lim\limits_{x\to\infty}\left(1+\dfrac{2}{x}\right)^{x}= 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1037, 1, 3, 1, '极限 $\\lim\\limits_{x\\to\\infty}\\left(1+\\dfrac{2}{x}\\right)^{x}=$ $\\underline{\\qquad\\qquad}$。', NULL, '$e^{2}$', 5, '这是 $1^{\\infty}$ 型极限，属第二重要极限 $\\lim\\limits_{t\\to\\infty}\\left(1+\\dfrac{1}{t}\\right)^{t}=e$ 的形式。令 $\\dfrac{2}{x}=\\dfrac{1}{t}$，则 $x=2t$，原式 $=\\lim\\limits_{t\\to\\infty}\\left(1+\\dfrac{1}{t}\\right)^{2t}=\\left[\\lim\\limits_{t\\to\\infty}\\left(1+\\dfrac{1}{t}\\right)^{t}\\right]^{2}=e^{2}$。常见错误是直接把指数照抄成 $e$，忽略指数上的放大倍数 2。', '极限与连续', 1, 2);
-- [填空/简单/导数与微分] 曲线 y=x^{3}-3x 在点 (0,0) 处的切线方程为 \underline{\qquad\qquad}。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1038, 1, 3, 1, '曲线 $y=x^{3}-3x$ 在点 $(0,0)$ 处的切线方程为 $\\underline{\\qquad\\qquad}$。', NULL, '$y=-3x$', 5, '先求导：$y''=3x^{2}-3$，在 $x=0$ 处 $y''(0)=-3$，即切线斜率为 $-3$。切点已知为 $(0,0)$，由点斜式得 $y-0=-3(x-0)$，即 $y=-3x$。也可把 $y=-3x$ 与曲线联立检验：$x^{3}-3x=-3x$ 得 $x^{3}=0$，只有 $x=0$ 一个二重以上的交点，符合切线特征。', '导数与微分', 1, 2);
-- [填空/中等/中值定理与导数应用] 函数 f(x)=x^{2} 在闭区间 [0,2] 上满足拉格朗日中值定理的条件，则定理结论中的 \xi= \underl
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1039, 1, 3, 2, '函数 $f(x)=x^{2}$ 在闭区间 $[0,2]$ 上满足拉格朗日中值定理的条件，则定理结论中的 $\\xi=$ $\\underline{\\qquad\\qquad}$。', NULL, '$\\xi=1$', 5, '$f$ 在 $[0,2]$ 上连续、在 $(0,2)$ 内可导，满足定理条件，且 $\\dfrac{f(2)-f(0)}{2-0}=\\dfrac{4-0}{2}=2$。令 $f''(\\xi)=2\\xi=2$ 得 $\\xi=1$，而 $1\\in(0,2)$ 符合要求。拉格朗日中值定理只保证这样的 $\\xi$ 存在；本题中它恰好唯一，但一般情况下可以不止一个，所以结论中不应写「唯一」。', '中值定理与导数应用', 1, 2);
-- [填空/中等/一元函数积分学] 不定积分 \int\dfrac{1}{x^{2}+4}\,dx= \underline{\qquad\qquad}。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1040, 1, 3, 2, '不定积分 $\\int\\dfrac{1}{x^{2}+4}\\,dx=$ $\\underline{\\qquad\\qquad}$。', NULL, '$\\dfrac{1}{2}\\arctan\\dfrac{x}{2}+C$', 5, '化为基本公式 $\\int\\dfrac{1}{u^{2}+1}\\,du=\\arctan u+C$。把分母写成 $4\\left[\\left(\\dfrac{x}{2}\\right)^{2}+1\\right]$，令 $u=\\dfrac{x}{2}$，则 $dx=2\\,du$，故原式 $=\\dfrac{1}{4}\\cdot 2\\int\\dfrac{1}{u^{2}+1}\\,du=\\dfrac{1}{2}\\arctan\\dfrac{x}{2}+C$。两个易错点：一是漏掉系数 $\\dfrac{1}{2}$ 而写成 $\\arctan\\dfrac{x}{2}+C$；二是漏写任意常数 $C$。', '一元函数积分学', 1, 2);
-- [填空/中等/一元函数积分学] 由曲线 y=\sqrt{x}、直线 x=1 与 x 轴围成的平面图形绕 x 轴旋转一周所得旋转体的体积为 \underl
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1041, 1, 3, 2, '由曲线 $y=\\sqrt{x}$、直线 $x=1$ 与 $x$ 轴围成的平面图形绕 $x$ 轴旋转一周所得旋转体的体积为 $\\underline{\\qquad\\qquad}$。', NULL, '$\\dfrac{\\pi}{2}$', 5, '绕 $x$ 轴旋转的体积公式为 $V=\\pi\\int_{a}^{b}y^{2}\\,dx$。这里由 $y=\\sqrt{x}$ 得 $y^{2}=x$，积分区间为 $[0,1]$，故 $V=\\pi\\int_{0}^{1}x\\,dx=\\pi\\cdot\\dfrac{x^{2}}{2}\\Big|_{0}^{1}=\\dfrac{\\pi}{2}$。常见错误是漏掉公式中的 $\\pi$，或者把被积函数写成 $y=\\sqrt{x}$ 而不是 $y^{2}=x$。', '一元函数积分学', 1, 2);
-- [填空/中等/多元函数微积分] 函数 z=x^{2}y 在点 (1,1) 处的全微分 dz= \underline{\qquad\qquad}。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1042, 1, 3, 2, '函数 $z=x^{2}y$ 在点 $(1,1)$ 处的全微分 $dz=$ $\\underline{\\qquad\\qquad}$。', NULL, '$2\\,dx+dy$', 5, '$\\dfrac{\\partial z}{\\partial x}=2xy$，$\\dfrac{\\partial z}{\\partial y}=x^{2}$，在点 $(1,1)$ 处两者的值分别为 2 与 1，故 $dz=\\dfrac{\\partial z}{\\partial x}dx+\\dfrac{\\partial z}{\\partial y}dy=2\\,dx+dy$。注意全微分是偏导数与自变量微分的线性组合：必须先把点代入求出偏导数值，结果中保留 $dx$、$dy$；写成 $\\dfrac{\\partial z}{\\partial x}+\\dfrac{\\partial z}{\\partial y}$ 或漏掉 $dx$、$dy$ 都是典型错误。', '多元函数微积分', 1, 2);
-- [填空/中等/级数与广义积分] 幂级数 \sum\limits_{n=0}^{\infty}\dfrac{x^{n}}{2^{n}} 的收敛半径为 \u
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1043, 1, 3, 2, '幂级数 $\\sum\\limits_{n=0}^{\\infty}\\dfrac{x^{n}}{2^{n}}$ 的收敛半径为 $\\underline{\\qquad\\qquad}$。', NULL, '$R=2$', 5, '该级数的系数为 $a_{n}=\\dfrac{1}{2^{n}}$，由比值法 $\\lim\\limits_{n\\to\\infty}\\left|\\dfrac{a_{n+1}}{a_{n}}\\right|=\\lim\\limits_{n\\to\\infty}\\dfrac{1}{2}=\\dfrac{1}{2}$，故收敛半径 $R=\\dfrac{1}{1/2}=2$。也可把它看成公比为 $\\dfrac{x}{2}$ 的等比级数：$\\left|\\dfrac{x}{2}\\right|<1$ 即 $|x|<2$ 时收敛，结论一致。注意这里问的是收敛半径而不是收敛域，端点 $x=\\pm 2$ 的敛散性不影响 $R$。', '级数与广义积分', 1, 2);
-- [填空/困难/一元函数积分学] 极限 \lim\limits_{n\to\infty}\left(\dfrac{1}{n+1}+\dfrac{1}{n+
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1044, 1, 3, 3, '极限 $\\lim\\limits_{n\\to\\infty}\\left(\\dfrac{1}{n+1}+\\dfrac{1}{n+2}+\\cdots+\\dfrac{1}{n+n}\\right)=$ $\\underline{\\qquad\\qquad}$。', NULL, '$\\ln 2$', 5, '把和式提出因子 $\\dfrac{1}{n}$，写成 $\\dfrac{1}{n}\\sum\\limits_{k=1}^{n}\\dfrac{1}{1+\\dfrac{k}{n}}$，这正是函数 $f(x)=\\dfrac{1}{1+x}$ 在 $[0,1]$ 上的黎曼和，故极限等于定积分 $\\int_{0}^{1}\\dfrac{1}{1+x}\\,dx=[\\ln(1+x)]_{0}^{1}=\\ln 2$。关键是把 $n$ 项之和配成 $\\dfrac{1}{n}\\sum f\\left(\\dfrac{k}{n}\\right)$ 的标准形式，从而识别出定积分的定义；若只会用放缩法，只能证明极限存在，求不出具体值。', '一元函数积分学', 1, 2);
-- [简答/简单/极限与连续] 计算极限 \lim\limits_{x\to 0}\dfrac{\sqrt{1+x}-1}{x}。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1045, 1, 4, 1, '计算极限 $\\lim\\limits_{x\\to 0}\\dfrac{\\sqrt{1+x}-1}{x}$。', NULL, '$\\dfrac{1}{2}$。计算要点：分子有理化，$\\dfrac{\\sqrt{1+x}-1}{x}=\\dfrac{(\\sqrt{1+x}-1)(\\sqrt{1+x}+1)}{x(\\sqrt{1+x}+1)}=\\dfrac{x}{x(\\sqrt{1+x}+1)}=\\dfrac{1}{\\sqrt{1+x}+1}$；当 $x\\to 0$ 时 $\\sqrt{1+x}+1\\to 2$，故极限为 $\\dfrac{1}{2}$。', 16, '这是 $\\dfrac{0}{0}$ 型极限。分子有理化是处理根式差的通法，也可直接用等价无穷小 $\\sqrt{1+x}-1\\sim\\dfrac{x}{2}$ 得结果。常见错误有二：一是把 $x=0$ 直接代入得 $\\dfrac{0}{0}$ 就断言极限不存在；二是有理化后约分时漏掉分母中的 $(\\sqrt{1+x}+1)$ 而错算成 1。', '极限与连续', 1, 2);
-- [简答/中等/中值定理与导数应用] 求函数 f(x)=x^{3}-3x^{2}-9x+5 的单调区间与极值。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1046, 1, 4, 2, '求函数 $f(x)=x^{3}-3x^{2}-9x+5$ 的单调区间与极值。', NULL, '定义域为 $(-\\infty,+\\infty)$。$f''(x)=3x^{2}-6x-9=3(x-3)(x+1)$，令 $f''(x)=0$ 得驻点 $x=-1$ 与 $x=3$。讨论导数符号：在 $(-\\infty,-1)$ 上 $f''(x)>0$，函数单调递增；在 $(-1,3)$ 上 $f''(x)<0$，函数单调递减；在 $(3,+\\infty)$ 上 $f''(x)>0$，函数单调递增。因此单调递增区间为 $(-\\infty,-1)$ 与 $(3,+\\infty)$，单调递减区间为 $(-1,3)$；$x=-1$ 为极大值点，极大值 $f(-1)=10$，$x=3$ 为极小值点，极小值 $f(3)=-22$。', 16, '本题考核「求导—求驻点—判号—定单调性与极值」的标准流程。$f''(x)$ 是开口向上的二次函数，其符号由两个零点 $-1$、$3$ 隔出的三个区间决定，依次为正、负、正，对应「增—减—增」，故第一个驻点是极大值点、第二个是极小值点。常见错误是只求出驻点就断定它是极值点而没有判断导数是否变号；另外要区分「极值点」（自变量的值）与「极值」（函数值），以及「单调区间」要用区间而不是点来表示。', '中值定理与导数应用', 1, 2);
-- [简答/中等/一元函数积分学] 计算定积分 \int_{1}^{e}x\ln x\,dx。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1047, 1, 4, 2, '计算定积分 $\\int_{1}^{e}x\\ln x\\,dx$。', NULL, '用分部积分法。取 $u=\\ln x$，$dv=x\\,dx$，则 $du=\\dfrac{1}{x}\\,dx$，$v=\\dfrac{x^{2}}{2}$。于是 $\\int x\\ln x\\,dx=\\dfrac{x^{2}}{2}\\ln x-\\int\\dfrac{x^{2}}{2}\\cdot\\dfrac{1}{x}\\,dx=\\dfrac{x^{2}}{2}\\ln x-\\dfrac{x^{2}}{4}+C$。代入上下限得 $\\int_{1}^{e}x\\ln x\\,dx=\\left[\\dfrac{x^{2}}{2}\\ln x-\\dfrac{x^{2}}{4}\\right]_{1}^{e}=\\left(\\dfrac{e^{2}}{2}-\\dfrac{e^{2}}{4}\\right)-\\left(0-\\dfrac{1}{4}\\right)=\\dfrac{e^{2}}{4}+\\dfrac{1}{4}=\\dfrac{e^{2}+1}{4}$。', 16, '被积函数是幂函数与对数的乘积，属于典型的分部积分题型，口诀「反对幂指三」提示对数应取作 $u$（对数优先当 $u$）。关键细节有两个：一是 $\\int\\dfrac{x}{2}\\,dx=\\dfrac{x^{2}}{4}$ 的系数不要算错；二是代入下限 $x=1$ 时虽然 $\\ln 1=0$，但 $-\\dfrac{x^{2}}{4}$ 这一项要贡献 $+\\dfrac{1}{4}$，漏掉它会把答案错写成 $\\dfrac{e^{2}}{4}$。', '一元函数积分学', 1, 2);
-- [简答/困难/多元函数微积分] 计算二重积分 \iint\limits_{D}(x^{2}+y^{2})\,dx\,dy，其中 D 是由直线 y=x、y
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1048, 1, 4, 3, '计算二重积分 $\\iint\\limits_{D}(x^{2}+y^{2})\\,dx\\,dy$，其中 $D$ 是由直线 $y=x$、$y=0$ 与 $x=1$ 围成的闭区域。', NULL, '区域 $D$ 可表示为 $0\\leq x\\leq 1$、$0\\leq y\\leq x$，化为先对 $y$ 后对 $x$ 的二次积分：$\\iint\\limits_{D}(x^{2}+y^{2})\\,dx\\,dy=\\int_{0}^{1}dx\\int_{0}^{x}(x^{2}+y^{2})\\,dy=\\int_{0}^{1}\\left[x^{2}y+\\dfrac{y^{3}}{3}\\right]_{0}^{x}dx=\\int_{0}^{1}\\left(x^{3}+\\dfrac{x^{3}}{3}\\right)dx=\\dfrac{4}{3}\\int_{0}^{1}x^{3}\\,dx=\\dfrac{4}{3}\\cdot\\dfrac{1}{4}=\\dfrac{1}{3}$。', 16, '关键是先画出 $y=x$、$y=0$、$x=1$ 围成的三角形区域并按 $y$ 型区域写出上下限 $0\\leq y\\leq x$、$0\\leq x\\leq 1$。计算时要注意两点：内层对 $y$ 积分时把 $x$ 视为常数，$\\int_{0}^{x}y^{2}\\,dy=\\dfrac{x^{3}}{3}$ 中的分母 3 不能漏；外层合并 $x^{3}+\\dfrac{x^{3}}{3}=\\dfrac{4}{3}x^{3}$ 后积分得 $\\dfrac{4}{3}\\cdot\\dfrac{1}{4}=\\dfrac{1}{3}$，若在系数上出错就会得到 $\\dfrac{1}{4}$ 之类的答案。', '多元函数微积分', 1, 2);
-- [简答/困难/微分方程] 求微分方程 y''-4y'+4y=0 满足初始条件 y(0)=1、y'(0)=0 的特解。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1049, 1, 4, 3, '求微分方程 $y''''-4y''+4y=0$ 满足初始条件 $y(0)=1$、$y''(0)=0$ 的特解。', NULL, '特征方程为 $r^{2}-4r+4=0$，即 $(r-2)^{2}=0$，得二重实根 $r=2$，故通解为 $y=(C_{1}+C_{2}x)e^{2x}$。由 $y(0)=1$ 得 $C_{1}=1$。对通解求导：$y''=C_{2}e^{2x}+2(C_{1}+C_{2}x)e^{2x}$，由 $y''(0)=0$ 得 $C_{2}+2C_{1}=0$，故 $C_{2}=-2$。所求特解为 $y=(1-2x)e^{2x}$。', 16, '二阶常系数齐次线性方程要按特征根的类型写通解：两个不相等实根对应 $C_{1}e^{r_{1}x}+C_{2}e^{r_{2}x}$，二重实根对应 $(C_{1}+C_{2}x)e^{rx}$，共轭复根对应 $e^{\\alpha x}(C_{1}\\cos\\beta x+C_{2}\\sin\\beta x)$。本题是二重根情形，最易犯的错误是把通解写成 $C_{1}e^{2x}+C_{2}e^{2x}$——这样两个常数实质可以合并，只含一个任意常数，不可能是二阶方程的通解。求导时对 $(C_{1}+C_{2}x)e^{2x}$ 必须用乘积求导法则，得到 $C_{2}e^{2x}+2(C_{1}+C_{2}x)e^{2x}$，才能正确地由 $y''(0)=0$ 解出 $C_{2}$。', '微分方程', 1, 2);
-- [简答/困难/级数与广义积分] 求幂级数 \sum\limits_{n=1}^{\infty}\dfrac{x^{n}}{n} 的收敛域。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1050, 1, 4, 3, '求幂级数 $\\sum\\limits_{n=1}^{\\infty}\\dfrac{x^{n}}{n}$ 的收敛域。', NULL, '先求收敛半径：$a_{n}=\\dfrac{1}{n}$，由 $\\lim\\limits_{n\\to\\infty}\\left|\\dfrac{a_{n+1}}{a_{n}}\\right|=\\lim\\limits_{n\\to\\infty}\\dfrac{n}{n+1}=1$ 得 $R=1$，故级数在 $|x|<1$ 内绝对收敛，在 $|x|>1$ 时发散。再单独讨论两个端点：当 $x=1$ 时级数为调和级数 $\\sum\\limits_{n=1}^{\\infty}\\dfrac{1}{n}$，发散；当 $x=-1$ 时级数为交错级数 $\\sum\\limits_{n=1}^{\\infty}\\dfrac{(-1)^{n}}{n}$，其中 $\\dfrac{1}{n}$ 单调递减且趋于 0，由莱布尼茨判别法知它收敛（条件收敛）。综上，收敛域为 $[-1,1)$。', 16, '求收敛域分两步：先用比值法（或根值法）求收敛半径，得到开区间；再对两个端点分别判断敛散性，这一步不能省略，因为同一个收敛半径下两端点的敛散性可能完全不同。本题在 $x=1$ 处化为发散的调和级数，在 $x=-1$ 处由莱布尼茨判别法知收敛，所以收敛域是左闭右开的 $[-1,1)$。常见错误是只写开区间 $(-1,1)$，或想当然地写成 $[-1,1]$。', '级数与广义积分', 1, 2);
-- [单选/简单/极限与连续] 极限 \lim\limits_{x\to\infty}\dfrac{3x^{2}+2x-1}{2x^{2}-x+5} 的
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1051, 1, 0, 1, '极限 $\\lim\\limits_{x\\to\\infty}\\dfrac{3x^{2}+2x-1}{2x^{2}-x+5}$ 的值为（　　）', '[{"label":"A","content":"$\\\\dfrac{3}{2}$"},{"label":"B","content":"$\\\\dfrac{2}{3}$"},{"label":"C","content":"$1$"},{"label":"D","content":"$\\\\infty$"}]', 'A', 5, '分子、分母都是多项式且最高次幂相同（都是 $x^{2}$），把分子分母同除以 $x^{2}$，得 $\\lim\\limits_{x\\to\\infty}\\dfrac{3+\\dfrac{2}{x}-\\dfrac{1}{x^{2}}}{2-\\dfrac{1}{x}+\\dfrac{5}{x^{2}}}=\\dfrac{3}{2}$。选 B 是把分子、分母最高次项的系数写反了；选 C 误以为同次幂之比的极限总是 1；选 D 只在分母次数低于分子次数时才会出现，本例两个次数相同，极限是有限数，「抓大头」时要连同系数一起抓。', '极限与连续', 1, 2);
-- [单选/简单/极限与连续] 函数 y=\dfrac{\ln(x-1)}{\sqrt{3-x}} 的定义域是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1052, 1, 0, 1, '函数 $y=\\dfrac{\\ln(x-1)}{\\sqrt{3-x}}$ 的定义域是（　　）', '[{"label":"A","content":"$[1,3)$"},{"label":"B","content":"$(1,3)$"},{"label":"C","content":"$(1,3]$"},{"label":"D","content":"$(1,+\\\\infty)$"}]', 'B', 5, '对数要求真数大于零，即 $x-1>0$，得 $x>1$；分母中的偶次根式要求 $3-x>0$，得 $x<3$；两者取交集为 $(1,3)$。选 A 保留了 $x=1$，而该点处 $\\ln 0$ 无意义；选 C 保留了 $x=3$，此时分母为 0；选 D 漏掉了根式给出的上界。求定义域要把「真数为正」「分母非零」「偶次根号下非负」三类限制都列出并取交集。', '极限与连续', 1, 2);
-- [单选/简单/极限与连续] 点 x=1 是函数 f(x)=\dfrac{1}{(x-1)^{2}} 的（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1053, 1, 0, 1, '点 $x=1$ 是函数 $f(x)=\\dfrac{1}{(x-1)^{2}}$ 的（　　）', '[{"label":"A","content":"可去间断点"},{"label":"B","content":"跳跃间断点"},{"label":"C","content":"无穷间断点"},{"label":"D","content":"振荡间断点"}]', 'C', 5, '$f$ 在 $x=1$ 处无定义，且 $\\lim\\limits_{x\\to 1}\\dfrac{1}{(x-1)^{2}}=+\\infty$，极限为无穷大，故 $x=1$ 是无穷间断点。选 A 的可去间断点要求极限为有限值，如 $\\dfrac{\\sin x}{x}$ 在 $x=0$ 处；选 B 要求左右极限都存在但不相等，如符号函数在 $x=0$ 处；选 D 的典型例子是 $\\sin\\dfrac{1}{x}$ 在 $x=0$ 处。判断间断点类型必须先算左右极限，再看它们是否有限、是否相等。', '极限与连续', 1, 2);
-- [单选/简单/向量代数与空间解析几何] 已知向量 \vec{a}=(1,-1,2)，\vec{b}=(2,1,0)，则 \vec{a}\times\vec{b}
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1054, 1, 0, 1, '已知向量 $\\vec{a}=(1,-1,2)$，$\\vec{b}=(2,1,0)$，则 $\\vec{a}\\times\\vec{b}=$（　　）', '[{"label":"A","content":"$(3,4,-2)$"},{"label":"B","content":"$(2,-4,-3)$"},{"label":"C","content":"$(-2,-4,3)$"},{"label":"D","content":"$(-2,4,3)$"}]', 'D', 5, '按三阶行列式展开：$\\vec{a}\\times\\vec{b}=((-1)\\times 0-2\\times 1,\\;2\\times 2-1\\times 0,\\;1\\times 1-(-1)\\times 2)=(0-2,\\,4-0,\\,1+2)=(-2,4,3)$。选 B 三个分量全部反号，那实际是 $\\vec{b}\\times\\vec{a}$；选 C 只把第一分量取了负号，说明中间分量的符号规则记错；选 A 是把三个分量随意重排。要注意向量积的结果仍是向量而非数量，且 $\\vec{b}\\times\\vec{a}=-\\vec{a}\\times\\vec{b}$，交换顺序必须变号。', '向量代数与空间解析几何', 1, 2);
-- [单选/简单/一元函数积分学] 不定积分 \int\sin 2x\,dx=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1055, 1, 0, 1, '不定积分 $\\int\\sin 2x\\,dx=$（　　）', '[{"label":"A","content":"$-\\\\dfrac{1}{2}\\\\cos 2x+C$"},{"label":"B","content":"$\\\\dfrac{1}{2}\\\\cos 2x+C$"},{"label":"C","content":"$-\\\\cos 2x+C$"},{"label":"D","content":"$-2\\\\cos 2x+C$"}]', 'A', 5, '令 $u=2x$，则 $du=2\\,dx$，即 $dx=\\dfrac{1}{2}du$，故 $\\int\\sin 2x\\,dx=\\dfrac{1}{2}\\int\\sin u\\,du=-\\dfrac{1}{2}\\cos 2x+C$。选 C 漏掉了换元产生的系数 $\\dfrac{1}{2}$；选 D 把系数写成了 2，相当于乘内层导数而不是除以它；选 B 漏掉负号，因为基本公式是 $\\int\\sin u\\,du=-\\cos u+C$。检验积分结果最可靠的办法是对它求导，看能否还原被积函数。', '一元函数积分学', 1, 2);
-- [单选/中等/导数与微分] 设 y=x^{x}（x>0），则 y'=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1056, 1, 0, 2, '设 $y=x^{x}$（$x>0$），则 $y''=$（　　）', '[{"label":"A","content":"$x^{x}\\\\ln x$"},{"label":"B","content":"$x^{x}(\\\\ln x+1)$"},{"label":"C","content":"$x\\\\cdot x^{x-1}$"},{"label":"D","content":"$x^{x}(\\\\ln x-1)$"}]', 'B', 5, '幂指函数既不能按幂函数求导，也不能按指数函数求导，应先用对数恒等式变形：$y=e^{x\\ln x}$，故 $y''=e^{x\\ln x}(x\\ln x)''=x^{x}(\\ln x+1)$。选 C 把 $x^{x}$ 误当成幂函数，只对底数用幂函数法则；选 A 漏掉了乘积求导中 $x\\cdot\\dfrac{1}{x}=1$ 这一项；选 D 把乘积求导的加号写成了减号。凡遇「底数与指数都含 $x$」的函数，一律先化为以 $e$ 为底的指数形式。', '导数与微分', 1, 2);
-- [单选/中等/导数与微分] 设参数方程 x=t^{2}、y=t^{3} 确定了函数 y=y(x)，则 \dfrac{dy}{dx}=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1057, 1, 0, 2, '设参数方程 $x=t^{2}$、$y=t^{3}$ 确定了函数 $y=y(x)$，则 $\\dfrac{dy}{dx}=$（　　）', '[{"label":"A","content":"$\\\\dfrac{2t}{3}$"},{"label":"B","content":"$\\\\dfrac{3t^{2}}{2}$"},{"label":"C","content":"$\\\\dfrac{3t}{2}$"},{"label":"D","content":"$3t^{2}$"}]', 'C', 5, '参数方程求导公式为 $\\dfrac{dy}{dx}=\\dfrac{\\dfrac{dy}{dt}}{\\dfrac{dx}{dt}}$。这里 $\\dfrac{dx}{dt}=2t$，$\\dfrac{dy}{dt}=3t^{2}$，故 $\\dfrac{dy}{dx}=\\dfrac{3t^{2}}{2t}=\\dfrac{3t}{2}$（$t\\neq 0$）。选 B 是约分时漏掉了分母中的因子 $t$ 得到的中间结果；选 D 直接用 $\\dfrac{dy}{dt}$ 充当导数，完全没有除以 $\\dfrac{dx}{dt}$；选 A 把分子、分母颠倒了。', '导数与微分', 1, 2);
-- [单选/中等/导数与微分] 由方程 e^{y}+xy=e 确定的隐函数 y=y(x)，则 y'(0)=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1058, 1, 0, 2, '由方程 $e^{y}+xy=e$ 确定的隐函数 $y=y(x)$，则 $y''(0)=$（　　）', '[{"label":"A","content":"$\\\\dfrac{1}{e}$"},{"label":"B","content":"$-1$"},{"label":"C","content":"$-e$"},{"label":"D","content":"$-\\\\dfrac{1}{e}$"}]', 'D', 5, '把 $x=0$ 代入方程得 $e^{y}=e$，故 $y(0)=1$。方程两端对 $x$ 求导，注意 $y$ 是 $x$ 的函数、$xy$ 要用乘积法则：$e^{y}y''+y+xy''=0$，即 $y''(e^{y}+x)=-y$。代入 $x=0$、$y=1$ 得 $y''(0)=-\\dfrac{1}{e}$。选 A 漏掉负号；选 B 忽略了对 $xy$ 使用乘积法则；选 C 是在化简 $e^{y}$ 时把 $\\dfrac{1}{e}$ 错算成了 $e$。隐函数求导后一般要先把已知点代入再解出导数值。', '导数与微分', 1, 2);
-- [单选/中等/中值定理与导数应用] 方程 x^{3}-3x+1=0 在区间 (0,1) 内实根的个数为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1059, 1, 0, 2, '方程 $x^{3}-3x+1=0$ 在区间 $(0,1)$ 内实根的个数为（　　）', '[{"label":"A","content":"$1$"},{"label":"B","content":"$2$"},{"label":"C","content":"$3$"},{"label":"D","content":"$0$"}]', 'A', 5, '令 $f(x)=x^{3}-3x+1$，它在 $[0,1]$ 上连续，且 $f(0)=1>0$、$f(1)=-1<0$，由零点定理知 $(0,1)$ 内至少有一个根；又 $f''(x)=3x^{2}-3<0$ 在 $(0,1)$ 上恒成立，$f$ 严格单调递减，故根至多有一个。两方面结合，实根恰有 1 个。选 C 是把该三次方程在 $(-\\infty,+\\infty)$ 上的三个实根与指定区间内的根数混为一谈；选 D 忽略了零点定理给出的存在性。判定根的个数要「存在性靠零点定理、唯一性靠单调性」。', '中值定理与导数应用', 1, 2);
-- [单选/中等/一元函数积分学] 不定积分 \int\ln x\,dx=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1060, 1, 0, 2, '不定积分 $\\int\\ln x\\,dx=$（　　）', '[{"label":"A","content":"$x\\\\ln x+C$"},{"label":"B","content":"$x\\\\ln x-x+C$"},{"label":"C","content":"$\\\\dfrac{1}{x}+C$"},{"label":"D","content":"$x\\\\ln x+x+C$"}]', 'B', 5, '被积函数只有一个对数因子，用分部积分：取 $u=\\ln x$、$dv=dx$，则 $du=\\dfrac{1}{x}dx$、$v=x$，故 $\\int\\ln x\\,dx=x\\ln x-\\int x\\cdot\\dfrac{1}{x}dx=x\\ln x-x+C$。选 A 漏掉了 $\\int v\\,du$ 这一项，等于默认 $\\int 1\\,dx=0$；选 C 把被积函数当成了原函数；选 D 把分部积分公式中的减号写成加号，对 $x\\ln x+x$ 求导得 $\\ln x+2$，可立刻验伪。', '一元函数积分学', 1, 2);
-- [单选/中等/一元函数积分学] 定积分 \int_{0}^{\frac{\pi}{2}}x\cos x\,dx 的值为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1061, 1, 0, 2, '定积分 $\\int_{0}^{\\frac{\\pi}{2}}x\\cos x\\,dx$ 的值为（　　）', '[{"label":"A","content":"$\\\\dfrac{\\\\pi}{2}$"},{"label":"B","content":"$1$"},{"label":"C","content":"$\\\\dfrac{\\\\pi}{2}-1$"},{"label":"D","content":"$\\\\dfrac{\\\\pi}{2}+1$"}]', 'C', 5, '取 $u=x$、$dv=\\cos x\\,dx$，得原函数 $\\int x\\cos x\\,dx=x\\sin x+\\cos x+C$，故原式 $=\\left[x\\sin x+\\cos x\\right]_{0}^{\\frac{\\pi}{2}}=\\left(\\dfrac{\\pi}{2}+0\\right)-(0+1)=\\dfrac{\\pi}{2}-1$。选 A 只代入了 $x\\sin x$ 这一项，把 $\\cos x$ 整项丢掉；选 B 恰好相反，只算了 $\\cos x$ 项的差；选 D 把下限处 $\\cos 0=1$ 的符号弄反。分部积分求出的原函数有两项，代入上下限时两项都不能漏。', '一元函数积分学', 1, 2);
-- [单选/困难/极限与连续] 极限 \lim\limits_{x\to 0}\left(\dfrac{1}{x^{2}}-\dfrac{1}{x\si
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1062, 1, 0, 3, '极限 $\\lim\\limits_{x\\to 0}\\left(\\dfrac{1}{x^{2}}-\\dfrac{1}{x\\sin x}\\right)$ 的值为（　　）', '[{"label":"A","content":"$-\\\\dfrac{1}{2}$"},{"label":"B","content":"$\\\\dfrac{1}{6}$"},{"label":"C","content":"$0$"},{"label":"D","content":"$-\\\\dfrac{1}{6}$"}]', 'D', 5, '这是「无穷大减无穷大」型，先通分化为 $\\dfrac{\\sin x-x}{x^{2}\\sin x}$。当 $x\\to 0$ 时分子 $\\sin x-x=-\\dfrac{x^{3}}{6}+o(x^{3})$，分母 $x^{2}\\sin x\\sim x^{3}$，故原式 $=-\\dfrac{1}{6}$。选 C 把两个无穷大之差误当成 0，实际上 $\\dfrac{1}{x^{2}}$ 与 $\\dfrac{1}{x\\sin x}$ 虽然同阶，但系数不同，差是有限非零值；选 B 把 $\\sin x-x$ 的符号写反；选 A 对应把三阶展开的系数记成 $\\dfrac{1}{2}$。', '极限与连续', 1, 2);
-- [单选/困难/中值定理与导数应用] 曲线 y=\dfrac{x^{2}}{x-1} 的斜渐近线为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1063, 1, 0, 3, '曲线 $y=\\dfrac{x^{2}}{x-1}$ 的斜渐近线为（　　）', '[{"label":"A","content":"$y=x+1$"},{"label":"B","content":"$y=x-1$"},{"label":"C","content":"$y=x$"},{"label":"D","content":"该曲线没有斜渐近线"}]', 'A', 5, '作多项式除法：$\\dfrac{x^{2}}{x-1}=x+1+\\dfrac{1}{x-1}$，当 $x\\to\\infty$ 时余项 $\\dfrac{1}{x-1}\\to 0$，故斜渐近线为 $y=x+1$。选 C 只取了商的最高次项 $x$，把常数项 1 丢掉；选 B 的 $x-1$ 对应除法中常数项符号写反的情形；选 D 忽略了分子次数恰好比分母次数高一次，而这正是存在斜渐近线的条件。此外该曲线还有铅直渐近线 $x=1$，同一曲线可以同时有铅直与斜渐近线。', '中值定理与导数应用', 1, 2);
-- [单选/中等/一元函数积分学] 设 F(x)=\int_{x}^{1}e^{t^{2}}\,dt，则 F'(x)=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1064, 1, 0, 2, '设 $F(x)=\\int_{x}^{1}e^{t^{2}}\\,dt$，则 $F''(x)=$（　　）', '[{"label":"A","content":"$e^{x^{2}}$"},{"label":"B","content":"$-e^{x^{2}}$"},{"label":"C","content":"$e^{1}-e^{x^{2}}$"},{"label":"D","content":"$e^{1-x^{2}}$"}]', 'B', 5, '先把上限化成变量：$F(x)=-\\int_{1}^{x}e^{t^{2}}dt$，再由变限积分求导公式得 $F''(x)=-e^{x^{2}}$。选 A 漏掉了交换积分上下限产生的负号；选 C 算出的是 $F(x)$ 本身的值而不是 $F''(x)$，把函数与导数混为一谈；选 D 是把指数随意拼凑的结果。变限积分求导时，若上限（或下限）是 $x$ 的函数，还要乘上它的导数。', '一元函数积分学', 1, 2);
-- [单选/中等/多元函数微积分] 设 z=\ln(x^{2}+y^{2})，则 \dfrac{\partial z}{\partial x}=（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1065, 1, 0, 2, '设 $z=\\ln(x^{2}+y^{2})$，则 $\\dfrac{\\partial z}{\\partial x}=$（　　）', '[{"label":"A","content":"$\\\\dfrac{1}{x^{2}+y^{2}}$"},{"label":"B","content":"$\\\\dfrac{2y}{x^{2}+y^{2}}$"},{"label":"C","content":"$\\\\dfrac{2x}{x^{2}+y^{2}}$"},{"label":"D","content":"$\\\\dfrac{2x}{\\\\sqrt{x^{2}+y^{2}}}$"}]', 'C', 5, '求 $\\dfrac{\\partial z}{\\partial x}$ 时把 $y$ 看作常数，由复合函数求导得 $\\dfrac{\\partial z}{\\partial x}=\\dfrac{1}{x^{2}+y^{2}}\\cdot(2x)=\\dfrac{2x}{x^{2}+y^{2}}$。选 A 漏乘了内层导数 $2x$；选 B 得到的是 $\\dfrac{\\partial z}{\\partial y}$，把两个偏导数混淆；选 D 把对数函数的导数与根式函数的导数记混。偏导数计算的核心原则是「对谁求导，另一个变量就当作常数」。', '多元函数微积分', 1, 2);
-- [单选/中等/多元函数微积分] 将二重积分 \iint\limits_{D}\sqrt{x^{2}+y^{2}}\,dx\,dy（D 为圆域 x^{2}
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1066, 1, 0, 2, '将二重积分 $\\iint\\limits_{D}\\sqrt{x^{2}+y^{2}}\\,dx\\,dy$（$D$ 为圆域 $x^{2}+y^{2}\\leq 1$）化为极坐标下的二次积分，结果为（　　）', '[{"label":"A","content":"$\\\\int_{0}^{2\\\\pi}d\\\\theta\\\\int_{0}^{1}r\\\\,dr$"},{"label":"B","content":"$\\\\int_{0}^{\\\\pi}d\\\\theta\\\\int_{0}^{1}r^{2}\\\\,dr$"},{"label":"C","content":"$\\\\int_{0}^{2\\\\pi}d\\\\theta\\\\int_{0}^{1}r^{3}\\\\,dr$"},{"label":"D","content":"$\\\\int_{0}^{2\\\\pi}d\\\\theta\\\\int_{0}^{1}r^{2}\\\\,dr$"}]', 'D', 5, '极坐标下 $x=r\\cos\\theta$、$y=r\\sin\\theta$，面积元素 $dx\\,dy=r\\,dr\\,d\\theta$，被积函数 $\\sqrt{x^{2}+y^{2}}=r$，两者相乘得 $r^{2}\\,dr\\,d\\theta$；整个圆域对应 $0\\leq\\theta\\leq 2\\pi$、$0\\leq r\\leq 1$，故选 D。选 A 漏掉了面积元素中的因子 $r$；选 C 多乘了一个 $r$；选 B 把角度范围误写成 $[0,\\pi]$，那只有半个圆。用极坐标算圆域上的二重积分时，$dx\\,dy=r\\,dr\\,d\\theta$ 这个因子绝不能丢。', '多元函数微积分', 1, 2);
-- [单选/简单/微分方程] 微分方程 y'=2xy 满足初始条件 y(0)=1 的特解为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1067, 1, 0, 1, '微分方程 $y''=2xy$ 满足初始条件 $y(0)=1$ 的特解为（　　）', '[{"label":"A","content":"$y=e^{x^{2}}$"},{"label":"B","content":"$y=e^{2x}$"},{"label":"C","content":"$y=x^{2}+1$"},{"label":"D","content":"$y=e^{x^{2}}+1$"}]', 'A', 5, '这是可分离变量方程，分离得 $\\dfrac{dy}{y}=2x\\,dx$，两端积分有 $\\ln|y|=x^{2}+C_{1}$，即通解 $y=Ce^{x^{2}}$。由 $y(0)=1$ 得 $C=1$，故特解为 $y=e^{x^{2}}$。选 B 把 $2x$ 的积分算成了 $2x$ 而不是 $x^{2}$；选 C 是对右端直接积分的错误写法；选 D 不满足初始条件，它在 $x=0$ 处的值为 2。解可分离变量方程后一定要用初始条件定出常数 $C$。', '微分方程', 1, 2);
-- [单选/困难/级数与广义积分] 级数 \sum\limits_{n=1}^{\infty}\dfrac{n}{2^{n}} 的和为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1068, 1, 0, 3, '级数 $\\sum\\limits_{n=1}^{\\infty}\\dfrac{n}{2^{n}}$ 的和为（　　）', '[{"label":"A","content":"$1$"},{"label":"B","content":"$2$"},{"label":"C","content":"$\\\\dfrac{1}{2}$"},{"label":"D","content":"$4$"}]', 'B', 5, '由幂级数求和公式 $\\sum\\limits_{n=1}^{\\infty}nx^{n}=\\dfrac{x}{(1-x)^{2}}$（$|x|<1$），取 $x=\\dfrac{1}{2}$ 得 $\\sum\\limits_{n=1}^{\\infty}\\dfrac{n}{2^{n}}=\\dfrac{\\frac{1}{2}}{\\left(\\frac{1}{2}\\right)^{2}}=2$。选 C 是把首项与级数的和混淆；选 A 是误把 $n$ 当成常数系数后按等比级数求和 $\\dfrac{\\frac{1}{2}}{1-\\frac{1}{2}}=1$ 的结果；选 D 是漏掉公式分子中的 $x$，直接用 $\\dfrac{1}{(1-x)^{2}}$ 算出的值。求这类级数的和一般借助幂级数逐项求导。', '级数与广义积分', 1, 2);
-- [单选/简单/向量代数与空间解析几何] 在空间直角坐标系中，点 A(1,2,-1) 与点 B(3,0,1) 之间的距离为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1069, 1, 0, 1, '在空间直角坐标系中，点 $A(1,2,-1)$ 与点 $B(3,0,1)$ 之间的距离为（　　）', '[{"label":"A","content":"$2$"},{"label":"B","content":"$3\\\\sqrt{2}$"},{"label":"C","content":"$2\\\\sqrt{3}$"},{"label":"D","content":"$4$"}]', 'C', 5, '$\\vec{AB}=(3-1,\\,0-2,\\,1-(-1))=(2,-2,2)$，由两点间距离公式得 $|AB|=\\sqrt{2^{2}+(-2)^{2}+2^{2}}=\\sqrt{12}=2\\sqrt{3}$。选 A 只取了某一个坐标差的绝对值；选 B 的 $3\\sqrt{2}=\\sqrt{18}$，来自把坐标差算成 $(3,-3,0)$ 之类；选 D 是三个坐标差的绝对值之和，那不是距离。空间两点距离等于各坐标差的平方和再开方。', '向量代数与空间解析几何', 1, 2);
-- [单选/简单/极限与连续] 当 x\to 0 时，\sin(x^{2}) 是 x 的（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1070, 1, 0, 1, '当 $x\\to 0$ 时，$\\sin(x^{2})$ 是 $x$ 的（　　）', '[{"label":"A","content":"等价无穷小"},{"label":"B","content":"同阶但不等价的无穷小"},{"label":"C","content":"低阶无穷小"},{"label":"D","content":"高阶无穷小"}]', 'D', 5, '作商求极限：$\\lim\\limits_{x\\to 0}\\dfrac{\\sin(x^{2})}{x}=\\lim\\limits_{x\\to 0}\\dfrac{x^{2}}{x}=\\lim\\limits_{x\\to 0}x=0$，比值为 0，说明 $\\sin(x^{2})$ 趋于 0 的速度更快，是 $x$ 的高阶无穷小。选 A 把等价无穷小的传递性误用到不同阶的变量上，$\\sin(x^{2})\\sim x^{2}$ 而 $x^{2}$ 与 $x$ 并不等价；选 B 要求比值为非零常数且不等于 1；选 C 对应比值为 $\\infty$ 的情形。判断无穷小的阶只需考察二者之比的极限。', '极限与连续', 1, 2);
-- [多选/简单/极限与连续] 下列极限的值等于 1 的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1071, 1, 1, 1, '下列极限的值等于 $1$ 的有（　　）', '[{"label":"A","content":"$\\\\lim\\\\limits_{x\\\\to 0}\\\\dfrac{\\\\sin x}{x}$"},{"label":"B","content":"$\\\\lim\\\\limits_{x\\\\to\\\\infty}\\\\left(1+\\\\dfrac{1}{x}\\\\right)^{x}$"},{"label":"C","content":"$\\\\lim\\\\limits_{x\\\\to 0}(1+x)^{\\\\frac{1}{x}}$"},{"label":"D","content":"$\\\\lim\\\\limits_{x\\\\to 0}\\\\dfrac{\\\\tan x}{x}$"}]', 'AD', 5, 'A 是第一重要极限，值为 1；D 中当 $x\\to 0$ 时 $\\tan x\\sim x$，比值极限也是 1，两者都正确。B、C 都是第二重要极限的形式，值等于自然常数 $e$ 而不是 1：$\\lim\\limits_{x\\to\\infty}\\left(1+\\dfrac{1}{x}\\right)^{x}=e$，$\\lim\\limits_{x\\to 0}(1+x)^{\\frac{1}{x}}=e$。这两个重要极限极易混淆，区别在于一个是 $\\dfrac{0}{0}$ 型的三角函数之比，另一个是 $1^{\\infty}$ 型的幂指函数。所以本题应选 AD。', '极限与连续', 1, 2);
-- [多选/中等/导数与微分] 下列求导结果正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1072, 1, 1, 2, '下列求导结果正确的有（　　）', '[{"label":"A","content":"$(\\\\arctan x)''=\\\\dfrac{1}{1+x^{2}}$"},{"label":"B","content":"$(\\\\arcsin x)''=\\\\dfrac{1}{\\\\sqrt{1-x^{2}}}$"},{"label":"C","content":"$(\\\\arccos x)''=\\\\dfrac{1}{\\\\sqrt{1-x^{2}}}$"},{"label":"D","content":"$(\\\\cot x)''=-\\\\csc^{2}x$"}]', 'ABD', 5, '$(\\arctan x)''=\\dfrac{1}{1+x^{2}}$、$(\\arcsin x)''=\\dfrac{1}{\\sqrt{1-x^{2}}}$、$(\\cot x)''=-\\csc^{2}x$ 都正确。C 错误：$(\\arccos x)''=-\\dfrac{1}{\\sqrt{1-x^{2}}}$，漏掉了负号——$\\arccos x$ 在定义域内单调递减，其导数必须为负，这个符号不能省。反三角函数的导数公式要么分母带根式、要么分母为 $1+x^{2}$，其中 $\\arccos$ 与 $\\operatorname{arccot}$ 的导数都带负号，记忆时可用单调性核对。所以本题应选 ABD。', '导数与微分', 1, 2);
-- [多选/中等/中值定理与导数应用] 下列函数在给定的区间上单调递增的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1073, 1, 1, 2, '下列函数在给定的区间上单调递增的有（　　）', '[{"label":"A","content":"$f(x)=x^{3}$，$(-\\\\infty,+\\\\infty)$"},{"label":"B","content":"$f(x)=e^{x}-x$，$(0,+\\\\infty)$"},{"label":"C","content":"$f(x)=\\\\ln x$，$(0,+\\\\infty)$"},{"label":"D","content":"$f(x)=\\\\cos x$，$(0,\\\\pi)$"}]', 'ABC', 5, '判断单调性看导数符号：A 中 $f''(x)=3x^{2}\\geq 0$，且仅在 $x=0$ 一点为 0，函数在 $(-\\infty,+\\infty)$ 上单调递增；B 中 $f''(x)=e^{x}-1>0$（$x>0$ 时），递增；C 中 $f''(x)=\\dfrac{1}{x}>0$，递增，三者都正确。D 错误：$f''(x)=-\\sin x<0$ 在 $(0,\\pi)$ 上恒成立，$\\cos x$ 在整个区间上单调递减，不能因为在 $\\left(0,\\dfrac{\\pi}{2}\\right)$ 上「看起来上升」就误判。所以本题应选 ABC。', '中值定理与导数应用', 1, 2);
-- [多选/中等/多元函数微积分] 关于函数 z=x^{2}+y^{2}-2x-4y+5，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1074, 1, 1, 2, '关于函数 $z=x^{2}+y^{2}-2x-4y+5$，下列说法正确的有（　　）', '[{"label":"A","content":"驻点为 $(1,2)$"},{"label":"B","content":"极小值为 $0$"},{"label":"C","content":"极大值为 $5$"},{"label":"D","content":"该函数在驻点处的两个偏导数都等于 $0$"}]', 'ABD', 5, '令 $z_{x}=2x-2=0$、$z_{y}=2y-4=0$，解得唯一驻点 $(1,2)$，A、D 正确。记 $A=z_{xx}=2$、$B=z_{xy}=0$、$C=z_{yy}=2$，则 $AC-B^{2}=4>0$ 且 $A=2>0$，故在该点取极小值 $z(1,2)=1+4-2-8+5=0$，B 正确。C 错误：判别式与 $A$ 的符号已说明这是极小值点而非极大值点，$5$ 只是常数项，不能把它当作极值。所以本题应选 ABD。', '多元函数微积分', 1, 2);
-- [多选/中等/微分方程] 关于微分方程 y''+y=0，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1075, 1, 1, 2, '关于微分方程 $y''''+y=0$，下列说法正确的有（　　）', '[{"label":"A","content":"特征方程为 $r^{2}+1=0$"},{"label":"B","content":"通解为 $y=C_{1}\\\\cos x+C_{2}\\\\sin x$，其中 $C_{1}$、$C_{2}$ 为任意常数"},{"label":"C","content":"$y=\\\\sin x$ 是该方程的一个解"},{"label":"D","content":"通解为 $y=Ce^{x}$，其中 $C$ 为任意常数"}]', 'ABC', 5, '二阶常系数齐次线性方程对应的特征方程是 $r^{2}+1=0$，A 正确；特征根是一对共轭复根 $r=\\pm i$，即 $\\alpha=0$、$\\beta=1$，故通解为 $y=e^{0\\cdot x}(C_{1}\\cos x+C_{2}\\sin x)=C_{1}\\cos x+C_{2}\\sin x$，B 正确；把 $y=\\sin x$ 代入左端得 $-\\sin x+\\sin x=0$，它是取 $C_{1}=0$、$C_{2}=1$ 得到的特解，C 正确。D 错误：$y=Ce^{x}$ 满足的是 $y''''-y=0$，代入 $y''''+y$ 得 $2Ce^{x}$ 并不为 0，而且它只含一个任意常数，不可能是二阶方程的通解。所以本题应选 ABC。', '微分方程', 1, 2);
-- [多选/简单/级数与广义积分] 下列级数中发散的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1076, 1, 1, 1, '下列级数中发散的有（　　）', '[{"label":"A","content":"$\\\\sum\\\\limits_{n=1}^{\\\\infty}\\\\dfrac{1}{n}$"},{"label":"B","content":"$\\\\sum\\\\limits_{n=1}^{\\\\infty}\\\\dfrac{1}{2^{n}}$"},{"label":"C","content":"$\\\\sum\\\\limits_{n=1}^{\\\\infty}\\\\dfrac{2n}{n^{2}+1}$"},{"label":"D","content":"$\\\\sum\\\\limits_{n=1}^{\\\\infty}\\\\dfrac{1}{n^{2}+1}$"}]', 'AC', 5, 'A 是调和级数，即 $p=1$ 的 $p$-级数，发散。C 的通项与 $\\dfrac{1}{n}$ 同阶：$\\lim\\limits_{n\\to\\infty}\\dfrac{2n/(n^{2}+1)}{1/n}=\\lim\\limits_{n\\to\\infty}\\dfrac{2n^{2}}{n^{2}+1}=2$，由比较判别法的极限形式知它也发散。B 是公比为 $\\dfrac{1}{2}$ 的等比级数，收敛；D 的通项小于 $\\dfrac{1}{n^{2}}$，而 $\\sum\\limits_{n=1}^{\\infty}\\dfrac{1}{n^{2}}$ 收敛，由比较判别法知它收敛。判断发散常用的思路是看通项是否与 $\\dfrac{1}{n}$ 同阶或更高。所以本题应选 AC。', '级数与广义积分', 1, 2);
-- [多选/困难/中值定理与导数应用] 关于函数 f(x)=x^{3}-12x 在闭区间 [-3,3] 上，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1077, 1, 1, 3, '关于函数 $f(x)=x^{3}-12x$ 在闭区间 $[-3,3]$ 上，下列说法正确的有（　　）', '[{"label":"A","content":"最大值为 $16$"},{"label":"B","content":"最小值为 $-9$"},{"label":"C","content":"$x=-2$ 是极大值点"},{"label":"D","content":"最小值为 $-16$"}]', 'ACD', 5, '$f''(x)=3x^{2}-12=3(x-2)(x+2)$，驻点为 $x=\\pm 2$，都在区间内部。比较驻点与端点的函数值：$f(-2)=16$、$f(2)=-16$、$f(-3)=9$、$f(3)=-9$，故最大值为 16、最小值为 $-16$，A、D 正确。又 $f''''(x)=6x$，$f''''(-2)=-12<0$，由极值第二判别法知 $x=-2$ 是极大值点，C 正确。B 错误：$-9$ 只是右端点处的函数值 $f(3)$，它比驻点处的 $-16$ 大，不可能是最小值。求闭区间上的最值必须把驻点值与端点值放在一起比较。所以本题应选 ACD。', '中值定理与导数应用', 1, 2);
-- [多选/困难/级数与广义积分] 关于数项级数，下列命题正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1078, 1, 1, 3, '关于数项级数，下列命题正确的有（　　）', '[{"label":"A","content":"若 $\\\\sum\\\\limits_{n=1}^{\\\\infty}u_{n}$ 收敛，则 $\\\\sum\\\\limits_{n=1}^{\\\\infty}|u_{n}|$ 收敛"},{"label":"B","content":"若 $\\\\sum\\\\limits_{n=1}^{\\\\infty}|u_{n}|$ 收敛，则 $\\\\sum\\\\limits_{n=1}^{\\\\infty}u_{n}$ 收敛"},{"label":"C","content":"若 $\\\\lim\\\\limits_{n\\\\to\\\\infty}u_{n}=0$，则 $\\\\sum\\\\limits_{n=1}^{\\\\infty}u_{n}$ 收敛"},{"label":"D","content":"若 $\\\\sum\\\\limits_{n=1}^{\\\\infty}u_{n}$ 收敛，则 $\\\\lim\\\\limits_{n\\\\to\\\\infty}u_{n}=0$"}]', 'BD', 5, 'B 是「绝对收敛必收敛」的定理，正确；D 是级数收敛的必要条件，正确。A 错误：收敛未必绝对收敛，例如 $\\sum\\limits_{n=1}^{\\infty}\\dfrac{(-1)^{n-1}}{n}$ 条件收敛，但各项取绝对值后是调和级数，发散。C 错误：通项趋于 0 只是必要条件，$\\sum\\limits_{n=1}^{\\infty}\\dfrac{1}{n}$ 的通项趋于 0 而级数发散。判断这类命题时要牢记两个强弱关系：「绝对收敛」强于「收敛」，「通项趋于 0」弱于「收敛」。所以本题应选 BD。', '级数与广义积分', 1, 2);
-- [判断/简单/极限与连续] 若极限 \lim\limits_{x\to x_{0}}f(x) 存在，则函数 f(x) 在点 x_{0} 处必有定义。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1079, 1, 2, 1, '若极限 $\\lim\\limits_{x\\to x_{0}}f(x)$ 存在，则函数 $f(x)$ 在点 $x_{0}$ 处必有定义。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '错误。极限刻画的是 $x$ 无限接近 $x_{0}$（但不等于 $x_{0}$）时函数值的变化趋势，与 $f$ 在 $x_{0}$ 处有没有定义无关。反例：$f(x)=\\dfrac{x^{2}-1}{x-1}$ 在 $x=1$ 处无定义，但 $\\lim\\limits_{x\\to 1}f(x)=\\lim\\limits_{x\\to 1}(x+1)=2$ 存在。只有当 $f$ 在 $x_{0}$ 处有定义、极限存在且两者相等时，才能说 $f$ 在该点连续。', '极限与连续', 1, 2);
-- [判断/简单/极限与连续] 若函数 f(x) 在点 x_{0} 处的左极限与右极限都存在且相等，则 f(x) 在点 x_{0} 处连续。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1080, 1, 2, 1, '若函数 $f(x)$ 在点 $x_{0}$ 处的左极限与右极限都存在且相等，则 $f(x)$ 在点 $x_{0}$ 处连续。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '错误。左右极限存在且相等只说明 $\\lim\\limits_{x\\to x_{0}}f(x)$ 存在，而连续还要求 $f$ 在 $x_{0}$ 处有定义，并且这个函数值恰好等于极限值。反例：$f(x)=\\dfrac{\\sin x}{x}$（$x\\neq 0$）在 $x=0$ 处无定义，虽然 $\\lim\\limits_{x\\to 0}f(x)=1$ 存在，函数在该点仍不连续。连续是「有定义、极限存在、二者相等」三件事同时成立。', '极限与连续', 1, 2);
-- [判断/简单/导数与微分] 若可导函数 f(x) 是偶函数，则它的导函数 f'(x) 是奇函数。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1081, 1, 2, 1, '若可导函数 $f(x)$ 是偶函数，则它的导函数 $f''(x)$ 是奇函数。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '正确。由 $f(-x)=f(x)$ 两端对 $x$ 求导，左端用复合函数求导得 $-f''(-x)$，右端得 $f''(x)$，于是 $f''(-x)=-f''(x)$，即 $f''$ 是奇函数。例如 $f(x)=x^{2}$ 的导函数 $2x$ 是奇函数，$f(x)=\\cos x$ 的导函数 $-\\sin x$ 也是奇函数。对偶地，可导奇函数的导函数是偶函数，如 $\\sin x$ 的导函数 $\\cos x$。', '导数与微分', 1, 2);
-- [判断/简单/一元函数积分学] 若函数 f(x) 在 [a,b] 上连续，且 \int_{a}^{b}f(x)\,dx=0，则 f(x) 在 [a,b]
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1082, 1, 2, 1, '若函数 $f(x)$ 在 $[a,b]$ 上连续，且 $\\int_{a}^{b}f(x)\\,dx=0$，则 $f(x)$ 在 $[a,b]$ 上恒等于零。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '错误。定积分是「带符号的面积」，正负部分可以互相抵消，积分为 0 并不意味着函数处处为零。反例：$f(x)=x$ 在 $[-1,1]$ 上连续，由奇函数在对称区间上的积分为 0 可知 $\\int_{-1}^{1}x\\,dx=0$，但 $f$ 显然不恒为零。正确的结论是：若 $f$ 连续且 $f(x)\\geq 0$，则由 $\\int_{a}^{b}f(x)\\,dx=0$ 才能推出 $f\\equiv 0$，「非负」这个条件不能少。', '一元函数积分学', 1, 2);
-- [判断/中等/中值定理与导数应用] 若函数 f(x) 在点 x_{0} 处取得极值，且 f'(x_{0}) 存在，则必有 f'(x_{0})=0。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1083, 1, 2, 2, '若函数 $f(x)$ 在点 $x_{0}$ 处取得极值，且 $f''(x_{0})$ 存在，则必有 $f''(x_{0})=0$。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '正确，这正是费马引理。以极大值为例，在 $x_{0}$ 的某邻域内恒有 $f(x)\\leq f(x_{0})$，于是左导数 $f''_{-}(x_{0})\\geq 0$ 而右导数 $f''_{+}(x_{0})\\leq 0$；又 $f''(x_{0})$ 存在，左右导数必须相等，只能有 $f''(x_{0})=0$。要注意「$f''(x_{0})$ 存在」这一条件不可省：$f(x)=|x|$ 在 $x=0$ 处取极小值，但该点不可导，导数并不存在。', '中值定理与导数应用', 1, 2);
-- [判断/中等/多元函数微积分] 设积分区域 D 关于 x 轴对称，若被积函数满足 f(x,-y)=-f(x,y)，则 \iint\limits_{D}f
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1084, 1, 2, 2, '设积分区域 $D$ 关于 $x$ 轴对称，若被积函数满足 $f(x,-y)=-f(x,y)$，则 $\\iint\\limits_{D}f(x,y)\\,dx\\,dy=0$。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '正确。区域关于 $x$ 轴对称时，$D$ 的上、下两半关于 $x$ 轴互为镜像；$f(x,-y)=-f(x,y)$ 说明对称点处的函数值恰好互为相反数，两部分积分相互抵消，故总积分为 0。这与一元定积分中「奇函数在对称区间上积分为 0」是同一个道理。两个条件缺一不可：区域不对称，或者函数关于 $y$ 是偶函数（此时积分等于上半部分的两倍），结论都不再是 0。', '多元函数微积分', 1, 2);
-- [判断/中等/向量代数与空间解析几何] 直线 \dfrac{x-1}{2}=\dfrac{y}{-1}=\dfrac{z+1}{3} 的一个方向向量为 (2,-
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1085, 1, 2, 2, '直线 $\\dfrac{x-1}{2}=\\dfrac{y}{-1}=\\dfrac{z+1}{3}$ 的一个方向向量为 $(2,-1,3)$。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '正确。直线的对称式方程 $\\dfrac{x-x_{0}}{m}=\\dfrac{y-y_{0}}{n}=\\dfrac{z-z_{0}}{p}$ 中，分母组成的向量 $(m,n,p)$ 就是它的一个方向向量，本题中为 $(2,-1,3)$，同时可读出直线过点 $(1,0,-1)$。要注意方向向量并不唯一，与 $(2,-1,3)$ 平行的任何非零向量（如 $(-2,1,-3)$、$(4,-2,6)$）都可以作方向向量；只有点与方向向量合在一起才能唯一确定一条直线。', '向量代数与空间解析几何', 1, 2);
-- [判断/困难/级数与广义积分] 若级数 \sum\limits_{n=1}^{\infty}u_{n} 收敛，则级数 \sum\limits_{n=1}
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1086, 1, 2, 3, '若级数 $\\sum\\limits_{n=1}^{\\infty}u_{n}$ 收敛，则级数 $\\sum\\limits_{n=1}^{\\infty}u_{n}^{2}$ 必定收敛。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '错误。收敛（可能只是条件收敛）并不能保证各项平方后仍收敛。反例：取 $u_{n}=\\dfrac{(-1)^{n-1}}{\\sqrt{n}}$，由莱布尼茨判别法知 $\\sum\\limits_{n=1}^{\\infty}u_{n}$ 收敛，但 $u_{n}^{2}=\\dfrac{1}{n}$，而 $\\sum\\limits_{n=1}^{\\infty}\\dfrac{1}{n}$ 是调和级数，发散。若把条件加强为 $\\sum\\limits_{n=1}^{\\infty}|u_{n}|$ 收敛，则 $u_{n}\\to 0$，当 $n$ 充分大时有 $u_{n}^{2}\\leq|u_{n}|$，由比较判别法才能断定 $\\sum u_{n}^{2}$ 收敛。', '级数与广义积分', 1, 2);
-- [填空/简单/极限与连续] 极限 \lim\limits_{x\to 0}\dfrac{1-\cos x}{x^{2}}= \underline{\
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1087, 1, 3, 1, '极限 $\\lim\\limits_{x\\to 0}\\dfrac{1-\\cos x}{x^{2}}=$ $\\underline{\\qquad\\qquad}$。', NULL, '$\\dfrac{1}{2}$', 5, '这是 $\\dfrac{0}{0}$ 型极限。利用等价无穷小 $1-\\cos x\\sim\\dfrac{x^{2}}{2}$，立得极限为 $\\dfrac{1}{2}$；也可用 $1-\\cos x=2\\sin^{2}\\dfrac{x}{2}$ 配合 $\\sin\\dfrac{x}{2}\\sim\\dfrac{x}{2}$ 推出同样结果。常见错误是只看到 $1-\\cos x\\to 0$ 就写成 0，或把 $1-\\cos x$ 与 $x$ 当成等价无穷小——实际上它与 $x^{2}$ 同阶而不是与 $x$ 同阶。', '极限与连续', 1, 2);
-- [填空/简单/导数与微分] 曲线 y=x^{2}+e^{x} 在点 (0,1) 处的切线方程为 \underline{\qquad\qquad}。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1088, 1, 3, 1, '曲线 $y=x^{2}+e^{x}$ 在点 $(0,1)$ 处的切线方程为 $\\underline{\\qquad\\qquad}$。', NULL, '$y=x+1$', 5, '先求导得 $y''=2x+e^{x}$，在 $x=0$ 处 $y''(0)=0+1=1$，即切线斜率为 1，切点为 $(0,1)$。由点斜式得 $y-1=1\\cdot(x-0)$，即 $y=x+1$。常见错误有两个：一是漏掉 $e^{x}$ 在 $x=0$ 处取值为 1，误得斜率为 0 而写成 $y=1$；二是把切点的纵坐标当成斜率。切线问题一定要先求导数值，再代点斜式。', '导数与微分', 1, 2);
-- [填空/简单/导数与微分] 设 y=\sin^{2}x，则 dy= \underline{\qquad\qquad}。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1089, 1, 3, 1, '设 $y=\\sin^{2}x$，则 $dy=$ $\\underline{\\qquad\\qquad}$。', NULL, '$\\sin 2x\\,dx$', 5, '先求导：$y''=2\\sin x\\cdot\\cos x=\\sin 2x$（用了二倍角公式），故 $dy=y''\\,dx=\\sin 2x\\,dx$，也可写成 $2\\sin x\\cos x\\,dx$。常见错误一是漏掉 $dx$：微分是函数增量的线性主部，必须带 $dx$；二是把 $\\sin^{2}x$ 的导数算成 $2\\sin x$，忘记对 $\\sin x$ 再用一次链式法则。', '导数与微分', 1, 2);
-- [填空/中等/中值定理与导数应用] 函数 f(x)=x\ln x 的极小值为 \underline{\qquad\qquad}。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1090, 1, 3, 2, '函数 $f(x)=x\\ln x$ 的极小值为 $\\underline{\\qquad\\qquad}$。', NULL, '$-\\dfrac{1}{e}$', 5, '定义域为 $x>0$。$f''(x)=\\ln x+1$，令 $f''(x)=0$ 得唯一驻点 $x=\\dfrac{1}{e}$；又 $f''''(x)=\\dfrac{1}{x}>0$，故 $x=\\dfrac{1}{e}$ 是极小值点，极小值为 $f\\left(\\dfrac{1}{e}\\right)=\\dfrac{1}{e}\\ln\\dfrac{1}{e}=-\\dfrac{1}{e}$。易错点有两个：一是把驻点 $x=\\dfrac{1}{e}$ 当作极小值回答；二是计算函数值时漏掉 $\\ln\\dfrac{1}{e}=-1$ 的符号。', '中值定理与导数应用', 1, 2);
-- [填空/中等/一元函数积分学] 不定积分 \int x\cos x\,dx= \underline{\qquad\qquad}。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1091, 1, 3, 2, '不定积分 $\\int x\\cos x\\,dx=$ $\\underline{\\qquad\\qquad}$。', NULL, '$x\\sin x+\\cos x+C$', 5, '由分部积分公式 $\\int u\\,dv=uv-\\int v\\,du$，取 $u=x$、$dv=\\cos x\\,dx$，则 $du=dx$、$v=\\sin x$，于是 $\\int x\\cos x\\,dx=x\\sin x-\\int\\sin x\\,dx=x\\sin x+\\cos x+C$。常见错误是第二次积分时漏掉负号而写成 $x\\sin x-\\cos x+C$，对它求导得 $x\\cos x+2\\sin x$，与 $x\\cos x$ 不符；另一类错误是忘记写任意常数 $C$。', '一元函数积分学', 1, 2);
-- [填空/中等/一元函数积分学] 定积分 \int_{0}^{2}|x-1|\,dx= \underline{\qquad\qquad}。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1092, 1, 3, 2, '定积分 $\\int_{0}^{2}|x-1|\\,dx=$ $\\underline{\\qquad\\qquad}$。', NULL, '$1$', 5, '被积函数含绝对值，需按 $x=1$ 分段去掉绝对值：当 $0\\leq x\\leq 1$ 时 $|x-1|=1-x$，当 $1\\leq x\\leq 2$ 时 $|x-1|=x-1$。于是原式 $=\\int_{0}^{1}(1-x)dx+\\int_{1}^{2}(x-1)dx=\\dfrac{1}{2}+\\dfrac{1}{2}=1$。常见错误是不加讨论地把 $|x-1|$ 写成 $x-1$，算得 $\\int_{0}^{2}(x-1)dx=0$；从几何意义看，该积分等于两个腰长为 1 的等腰直角三角形面积之和，显然应为 1。', '一元函数积分学', 1, 2);
-- [填空/中等/多元函数微积分] 设 z=\arctan\dfrac{y}{x}（x\neq 0），则 \dfrac{\partial z}{\parti
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1093, 1, 3, 2, '设 $z=\\arctan\\dfrac{y}{x}$（$x\\neq 0$），则 $\\dfrac{\\partial z}{\\partial x}=$ $\\underline{\\qquad\\qquad}$。', NULL, '$-\\dfrac{y}{x^{2}+y^{2}}$', 5, '求 $\\dfrac{\\partial z}{\\partial x}$ 时把 $y$ 视为常数，由复合函数求导得 $\\dfrac{\\partial z}{\\partial x}=\\dfrac{1}{1+\\left(\\dfrac{y}{x}\\right)^{2}}\\cdot\\left(-\\dfrac{y}{x^{2}}\\right)$。把分母化简：$1+\\dfrac{y^{2}}{x^{2}}=\\dfrac{x^{2}+y^{2}}{x^{2}}$，代入得 $\\dfrac{\\partial z}{\\partial x}=\\dfrac{x^{2}}{x^{2}+y^{2}}\\cdot\\left(-\\dfrac{y}{x^{2}}\\right)=-\\dfrac{y}{x^{2}+y^{2}}$。常见错误是漏掉内层 $\\dfrac{y}{x}$ 对 $x$ 求导所得的 $-\\dfrac{y}{x^{2}}$，或者丢掉这个负号。', '多元函数微积分', 1, 2);
-- [填空/困难/微分方程] 微分方程 y''-2y'+5y=0 的通解为 \underline{\qquad\qquad}。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1094, 1, 3, 3, '微分方程 $y''''-2y''+5y=0$ 的通解为 $\\underline{\\qquad\\qquad}$。', NULL, '$y=e^{x}(C_{1}\\cos 2x+C_{2}\\sin 2x)$，其中 $C_{1}$、$C_{2}$ 为任意常数', 5, '特征方程为 $r^{2}-2r+5=0$，判别式 $\\Delta=4-20=-16<0$，得一对共轭复根 $r=1\\pm 2i$，即 $\\alpha=1$、$\\beta=2$。按共轭复根情形写通解：$y=e^{\\alpha x}(C_{1}\\cos\\beta x+C_{2}\\sin\\beta x)=e^{x}(C_{1}\\cos 2x+C_{2}\\sin 2x)$。常见错误有三种：判别式为负就断言方程无解；把通解写成 $e^{x}(C_{1}+C_{2}x)$（那是二重实根的形式）；漏掉指数因子 $e^{x}$ 中的 $\\alpha=1$，只写 $C_{1}\\cos 2x+C_{2}\\sin 2x$。', '微分方程', 1, 2);
-- [简答/简单/极限与连续] 计算极限 \lim\limits_{x\to 0}\dfrac{1-\cos 2x}{x\sin x}。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1095, 1, 4, 1, '计算极限 $\\lim\\limits_{x\\to 0}\\dfrac{1-\\cos 2x}{x\\sin x}$。', NULL, '利用二倍角公式 $1-\\cos 2x=2\\sin^{2}x$，原式 $=\\lim\\limits_{x\\to 0}\\dfrac{2\\sin^{2}x}{x\\sin x}=\\lim\\limits_{x\\to 0}\\dfrac{2\\sin x}{x}=2\\times 1=2$。', 16, '本题是 $\\dfrac{0}{0}$ 型极限。关键一步是用二倍角公式把 $1-\\cos 2x$ 化成 $2\\sin^{2}x$，约去分子分母的公因子 $\\sin x$ 后，剩下的正是第一重要极限 $\\lim\\limits_{x\\to 0}\\dfrac{\\sin x}{x}=1$。也可直接用等价无穷小：$1-\\cos 2x\\sim\\dfrac{(2x)^{2}}{2}=2x^{2}$，而 $x\\sin x\\sim x^{2}$，比值同样是 2。常见错误是把 $1-\\cos 2x$ 的等价无穷小写成 $x^{2}$，忽略了角度倍数带来的系数 2。', '极限与连续', 1, 2);
-- [简答/简单/一元函数积分学] 计算不定积分 \int(3x-1)^{4}\,dx。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1096, 1, 4, 1, '计算不定积分 $\\int(3x-1)^{4}\\,dx$。', NULL, '令 $u=3x-1$，则 $du=3\\,dx$，即 $dx=\\dfrac{1}{3}du$。于是 $\\int(3x-1)^{4}\\,dx=\\dfrac{1}{3}\\int u^{4}\\,du=\\dfrac{1}{3}\\cdot\\dfrac{u^{5}}{5}+C=\\dfrac{(3x-1)^{5}}{15}+C$。', 16, '这是第一类换元法（凑微分）的标准题型：把 $dx$ 凑成 $\\dfrac{1}{3}d(3x-1)$，被积表达式就化为幂函数，可直接套用基本公式。最易犯的错误是漏掉系数 $\\dfrac{1}{3}$ 而写成 $\\dfrac{(3x-1)^{5}}{5}+C$；检验办法是把结果求导，$\\dfrac{1}{15}\\cdot 5(3x-1)^{4}\\cdot 3=(3x-1)^{4}$，恰好还原被积函数。另外不要漏写任意常数 $C$。', '一元函数积分学', 1, 2);
-- [简答/简单/一元函数积分学] 求由曲线 y=x^{2} 与直线 y=2x 所围成的平面图形的面积。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1097, 1, 4, 1, '求由曲线 $y=x^{2}$ 与直线 $y=2x$ 所围成的平面图形的面积。', NULL, '先求交点：由 $x^{2}=2x$ 得 $x=0$ 与 $x=2$，故积分区间为 $[0,2]$。在 $(0,2)$ 内直线 $y=2x$ 位于抛物线 $y=x^{2}$ 上方，所以面积 $S=\\int_{0}^{2}(2x-x^{2})\\,dx=\\left[x^{2}-\\dfrac{x^{3}}{3}\\right]_{0}^{2}=4-\\dfrac{8}{3}=\\dfrac{4}{3}$。', 16, '求平面图形面积的标准步骤是「先求交点定区间，再判上下定被积函数，最后积分」。这里上边界是直线、下边界是抛物线，被积函数必须取「上减下」即 $2x-x^{2}$；若颠倒成 $x^{2}-2x$，结果会相差一个负号，而面积必须为正，可据此迅速自查。计算中 $\\int_{0}^{2}2x\\,dx=4$、$\\int_{0}^{2}x^{2}dx=\\dfrac{8}{3}$ 都要算准，答案 $\\dfrac{4}{3}$ 也可用「三角形的面积 4 减去抛物线下的曲边三角形面积 $\\dfrac{8}{3}$」来验算。', '一元函数积分学', 1, 2);
-- [简答/中等/多元函数微积分] 计算二重积分 \iint\limits_{D}(x^{2}+y)\,dx\,dy，其中 D 是由曲线 y=x^{2}、直
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1098, 1, 4, 2, '计算二重积分 $\\iint\\limits_{D}(x^{2}+y)\\,dx\\,dy$，其中 $D$ 是由曲线 $y=x^{2}$、直线 $x=1$ 与 $y=0$ 围成的闭区域。', NULL, '区域 $D$ 可表示为 $0\\leq x\\leq 1$、$0\\leq y\\leq x^{2}$，化为先对 $y$ 后对 $x$ 的二次积分：$\\iint\\limits_{D}(x^{2}+y)\\,dx\\,dy=\\int_{0}^{1}dx\\int_{0}^{x^{2}}(x^{2}+y)\\,dy=\\int_{0}^{1}\\left[x^{2}y+\\dfrac{y^{2}}{2}\\right]_{0}^{x^{2}}dx=\\int_{0}^{1}\\left(x^{4}+\\dfrac{x^{4}}{2}\\right)dx=\\dfrac{3}{2}\\int_{0}^{1}x^{4}\\,dx=\\dfrac{3}{2}\\cdot\\dfrac{1}{5}=\\dfrac{3}{10}$。', 16, '关键是先画出 $y=x^{2}$、$x=1$、$y=0$ 围成的曲边三角形区域，并按 $y$ 型区域写出上下限：对每个固定的 $x\\in[0,1]$，$y$ 从 0 变到 $x^{2}$，所以内层积分的上限是 $x^{2}$ 而不是 $x$，这是与直线边界情形最容易混淆之处。内层对 $y$ 积分时把 $x$ 视为常数，代入上限分别得 $x^{2}\\cdot x^{2}=x^{4}$ 与 $\\dfrac{(x^{2})^{2}}{2}=\\dfrac{x^{4}}{2}$；漏掉分母 2 或把上限写成 $x$ 都会得到错误结果。', '多元函数微积分', 1, 2);
-- [简答/中等/中值定理与导数应用] 求曲线 y=x^{3}-3x^{2}+1 的凹凸区间与拐点。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1099, 1, 4, 2, '求曲线 $y=x^{3}-3x^{2}+1$ 的凹凸区间与拐点。', NULL, '定义域为 $(-\\infty,+\\infty)$。$y''=3x^{2}-6x$，$y''''=6x-6$。令 $y''''=0$ 得 $x=1$。当 $x<1$ 时 $y''''<0$，曲线是凸的；当 $x>1$ 时 $y''''>0$，曲线是凹的。凹凸性在 $x=1$ 两侧发生改变，故 $x=1$ 是拐点的横坐标，而 $y(1)=1-3+1=-1$，拐点为 $(1,-1)$。所以凸区间为 $(-\\infty,1)$，凹区间为 $(1,+\\infty)$。', 16, '讨论凹凸性只看二阶导数的符号：$y''''<0$ 的区间上曲线为凸，$y''''>0$ 的区间上曲线为凹。拐点必须同时满足「$y''''=0$」与「两侧凹凸性相反」两个条件，只解出 $y''''=0$ 还不够——例如 $y=x^{4}$ 在 $x=0$ 处 $y''''=0$，但两侧 $y''''$ 都为正，该点不是拐点。此外「拐点」是曲线上的点，必须写出完整坐标 $(1,-1)$，只报横坐标是不完整的。', '中值定理与导数应用', 1, 2);
-- [简答/困难/微分方程] 求微分方程 y''-y=e^{x} 的通解。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1100, 1, 4, 3, '求微分方程 $y''''-y=e^{x}$ 的通解。', NULL, '先解对应的齐次方程 $y''''-y=0$：特征方程为 $r^{2}-1=0$，特征根为 $r_{1}=1$、$r_{2}=-1$，故齐次通解为 $Y=C_{1}e^{x}+C_{2}e^{-x}$。由于自由项 $e^{x}$ 中的指数 1 恰是特征单根，应设特解 $y^{*}=Axe^{x}$，则 $(y^{*})''=A(1+x)e^{x}$，$(y^{*})''''=A(2+x)e^{x}$。代入原方程得 $A(2+x)e^{x}-Axe^{x}=2Ae^{x}=e^{x}$，故 $A=\\dfrac{1}{2}$，即 $y^{*}=\\dfrac{1}{2}xe^{x}$。所求通解为 $y=C_{1}e^{x}+C_{2}e^{-x}+\\dfrac{1}{2}xe^{x}$。', 16, '本题是二阶常系数非齐次线性方程，通解等于齐次通解加一个特解。关键在识别「共振」：自由项 $e^{x}$ 的指数 1 恰好是特征方程的单根，此时特解必须设为 $Axe^{x}$，若仍设 $Ae^{x}$ 就会因它是齐次方程的解而使左端恒为 0，永远解不出 $A$。确定系数时要对 $(y^{*})$ 用乘积求导法则算出二阶导数，从而得到 $2A=1$。最后不要漏掉齐次通解中的两个任意常数 $C_{1}$、$C_{2}$，也不要漏掉特解中的因子 $x$。', '微分方程', 1, 2);

-- 四川专升本 · 高等数学模拟卷（一）：25 题，总分 150，及格 90，120 分钟（题面固定，无抽题规则）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (1, '四川专升本 · 高等数学模拟卷（一）', '覆盖函数极限与连续、导数与微分、定积分与变限积分、罗尔定理与积分性质、数项级数审敛及微分方程初步，共 25 题，满分 150 分。', 1, 120, 150, 90, '模拟卷', '[{"questionId":1001,"score":5},{"questionId":1002,"score":5},{"questionId":1003,"score":5},{"questionId":1004,"score":5},{"questionId":1005,"score":5},{"questionId":1006,"score":5},{"questionId":1007,"score":5},{"questionId":1008,"score":5},{"questionId":1009,"score":5},{"questionId":1010,"score":5},{"questionId":1021,"score":5},{"questionId":1022,"score":5},{"questionId":1023,"score":5},{"questionId":1024,"score":5},{"questionId":1029,"score":3},{"questionId":1030,"score":3},{"questionId":1031,"score":3},{"questionId":1032,"score":3},{"questionId":1037,"score":5},{"questionId":1038,"score":5},{"questionId":1039,"score":5},{"questionId":1040,"score":5},{"questionId":1045,"score":16},{"questionId":1046,"score":16},{"questionId":1047,"score":16}]', NULL, NULL, 1, 2);

-- 四川专升本 · 高等数学模拟卷（二）：25 题，总分 150，及格 90，120 分钟（题面固定，无抽题规则）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (2, '四川专升本 · 高等数学模拟卷（二）', '覆盖导数应用与拐点、二重积分与交换积分次序、全微分与多元极值、旋转体体积、幂级数收敛域及二阶常系数微分方程，共 25 题，满分 150 分。', 1, 120, 150, 90, '模拟卷', '[{"questionId":1011,"score":5},{"questionId":1012,"score":5},{"questionId":1013,"score":5},{"questionId":1014,"score":5},{"questionId":1015,"score":5},{"questionId":1016,"score":5},{"questionId":1017,"score":5},{"questionId":1018,"score":5},{"questionId":1019,"score":5},{"questionId":1020,"score":5},{"questionId":1025,"score":5},{"questionId":1026,"score":5},{"questionId":1027,"score":5},{"questionId":1028,"score":5},{"questionId":1033,"score":3},{"questionId":1034,"score":3},{"questionId":1035,"score":3},{"questionId":1036,"score":3},{"questionId":1041,"score":5},{"questionId":1042,"score":5},{"questionId":1043,"score":5},{"questionId":1044,"score":5},{"questionId":1048,"score":16},{"questionId":1049,"score":16},{"questionId":1050,"score":16}]', NULL, NULL, 1, 2);

-- 四川专升本 · 高等数学模拟卷（三）：25 题，总分 150，及格 90，120 分钟（题面固定，无抽题规则）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (7, '四川专升本 · 高等数学模拟卷（三）', '与前三张卷题目完全不重复，由新增题目组成的原创模拟卷，共 25 题、满分 150 分、及格 90 分，建议用时 120 分钟。', 1, 120, 150, 90, '模拟卷', '[{"questionId":1051,"score":5},{"questionId":1052,"score":5},{"questionId":1053,"score":5},{"questionId":1054,"score":5},{"questionId":1055,"score":5},{"questionId":1056,"score":5},{"questionId":1057,"score":5},{"questionId":1058,"score":5},{"questionId":1059,"score":5},{"questionId":1060,"score":5},{"questionId":1071,"score":5},{"questionId":1072,"score":5},{"questionId":1073,"score":5},{"questionId":1074,"score":5},{"questionId":1079,"score":3},{"questionId":1080,"score":3},{"questionId":1081,"score":3},{"questionId":1082,"score":3},{"questionId":1087,"score":5},{"questionId":1088,"score":5},{"questionId":1089,"score":5},{"questionId":1090,"score":5},{"questionId":1095,"score":16},{"questionId":1096,"score":16},{"questionId":1097,"score":16}]', NULL, NULL, 1, 2);

-- 四川专升本 · 高等数学模拟卷（四）：25 题，总分 150，及格 90，120 分钟（题面固定，无抽题规则）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (8, '四川专升本 · 高等数学模拟卷（四）', '与前三张卷题目完全不重复，由新增题目组成的原创模拟卷，共 25 题、满分 150 分、及格 90 分，建议用时 90 分钟。', 1, 120, 150, 90, '模拟卷', '[{"questionId":1061,"score":5},{"questionId":1062,"score":5},{"questionId":1063,"score":5},{"questionId":1064,"score":5},{"questionId":1065,"score":5},{"questionId":1066,"score":5},{"questionId":1067,"score":5},{"questionId":1068,"score":5},{"questionId":1069,"score":5},{"questionId":1070,"score":5},{"questionId":1075,"score":5},{"questionId":1076,"score":5},{"questionId":1077,"score":5},{"questionId":1078,"score":5},{"questionId":1083,"score":3},{"questionId":1084,"score":3},{"questionId":1085,"score":3},{"questionId":1086,"score":3},{"questionId":1091,"score":5},{"questionId":1092,"score":5},{"questionId":1093,"score":5},{"questionId":1094,"score":5},{"questionId":1098,"score":16},{"questionId":1099,"score":16},{"questionId":1100,"score":16}]', NULL, NULL, 1, 2);

-- 四川专升本 · 高等数学专项训练（一元函数积分学）：20 题，总分 127，及格 77，90 分钟
--   知识点「一元函数积分学」共 21 道题，本卷取 20 道（单选8 + 多选2 + 判断3 + 填空4 + 简答3）；卷内每题都属于该专题
--   显式清空 start_time/end_time：否则这个 id 上残留的时间窗会让「到点自动结束」任务把本卷立刻判成已结束（学生端看不到）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (101, '四川专升本 · 高等数学专项训练（一元函数积分学）', '按知识点筛选的专项训练卷：只含「一元函数积分学」专题的题目，覆盖不定积分、定积分、变限积分与定积分的几何应用。题面固定，不含抽题规则。', 1, 90, 127, 77, '专项训练', '[{"questionId":1012,"score":5},{"questionId":1013,"score":5},{"questionId":1014,"score":5},{"questionId":1015,"score":5},{"questionId":1055,"score":5},{"questionId":1060,"score":5},{"questionId":1061,"score":5},{"questionId":1064,"score":5},{"questionId":1023,"score":5},{"questionId":1025,"score":5},{"questionId":1031,"score":3},{"questionId":1035,"score":3},{"questionId":1082,"score":3},{"questionId":1040,"score":5},{"questionId":1041,"score":5},{"questionId":1044,"score":5},{"questionId":1091,"score":5},{"questionId":1047,"score":16},{"questionId":1096,"score":16},{"questionId":1097,"score":16}]', NULL, NULL, 1, 2);

-- 演示数据到此为止。注意：这些卷子是「题面固定」的（question_snapshot 已写死），
-- 不经过智能组卷；若要体验随机组卷，请在「智能组卷」页面按规则现场生成。

-- ----------------------------------------------------------------------
-- 题库 2：四川专升本 · 英语题库（100 道题）
-- ----------------------------------------------------------------------
INSERT INTO question_bank (id, name, subject, description, created_by) VALUES
(2, '四川专升本 · 英语题库', '英语', '按四川专升本《英语》考试大纲题型与难度原创命题的题库，覆盖词汇语法、阅读理解、翻译与写作。', 2);

-- [单选/中等/语法] By the time the ambulance arrived, the injured man ______ un
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1101, 2, 0, 2, 'By the time the ambulance arrived, the injured man ______ unconscious for nearly half an hour.', '[{"label":"A","content":"had been"},{"label":"B","content":"has been"},{"label":"C","content":"was"},{"label":"D","content":"would be"}]', 'A', 5, '「by the time + 一般过去时」引导的从句表示过去某一时刻，主句动作发生在它之前，且与 for nearly half an hour 连用表示持续状态，故用过去完成时 had been。B 项现在完成时不能与表示过去的时间状语连用；C 项一般过去时无法体现「过去的过去」；D 项 would be 是过去将来时，句中缺少相应语境。', '语法', 1, 2);
-- [单选/简单/语法] Neither the students nor the teacher ______ aware of the sud
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1102, 2, 0, 1, 'Neither the students nor the teacher ______ aware of the sudden change in the timetable.', '[{"label":"A","content":"were"},{"label":"B","content":"was"},{"label":"C","content":"are"},{"label":"D","content":"have been"}]', 'B', 5, 'neither...nor... 连接两个主语时谓语动词遵循「就近原则」，与最近的 the teacher 保持一致，用单数；又因整句叙述过去的情况，故用 was。A、C、D 均为复数形式，与就近的单数主语不一致。', '语法', 1, 2);
-- [单选/中等/语法] ______ from the top of the tower, the whole city looks like 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1103, 2, 0, 2, '______ from the top of the tower, the whole city looks like a huge garden.', '[{"label":"A","content":"Seeing"},{"label":"B","content":"Seen"},{"label":"C","content":"To see"},{"label":"D","content":"Having seen"}]', 'B', 5, '句子主语 the whole city 与 see 之间是被动关系（城市被看），故用过去分词 Seen 作状语。A、D 为现在分词，表示主动，其逻辑主语应是人；C 项动词不定式作状语常表示目的，与句意不符。', '语法', 1, 2);
-- [单选/中等/语法] The manager insisted that the meeting ______ put off until n
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1104, 2, 0, 2, 'The manager insisted that the meeting ______ put off until next Monday.', '[{"label":"A","content":"is"},{"label":"B","content":"was"},{"label":"C","content":"be"},{"label":"D","content":"would be"}]', 'C', 5, 'insist 表示「坚持要求」时，其后宾语从句用虚拟语气，谓语为 (should) + 动词原形，故用 be（完整形式是 should be put off，should 被省略）。A、B 为陈述语气；D 项 would be 不用于该虚拟结构。', '语法', 1, 2);
-- [单选/简单/语法] We should never judge a person ______ the clothes he wears.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1105, 2, 0, 1, 'We should never judge a person ______ the clothes he wears.', '[{"label":"A","content":"by"},{"label":"B","content":"on"},{"label":"C","content":"with"},{"label":"D","content":"for"}]', 'A', 5, 'judge a person by the clothes he wears 是固定搭配，by 表示判断所依据的标准。B 项 on 表示「关于、在……上」；C 项 with 表示伴随或使用工具；D 项 for 表示目的或原因，三者都不能与 judge 构成「根据……来判断」之意。', '语法', 1, 2);
-- [单选/困难/语法] Not until he failed the exam ______ how much time he had was
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1106, 2, 0, 3, 'Not until he failed the exam ______ how much time he had wasted.', '[{"label":"A","content":"he realized"},{"label":"B","content":"did he realize"},{"label":"C","content":"he did realize"},{"label":"D","content":"realized he"}]', 'B', 5, 'Not until... 置于句首时主句要用部分倒装，即把助动词提到主语之前，故为 did he realize。A、C 未倒装；D 项 realized he 是全部倒装的形式，且缺少助动词，均不符合部分倒装的要求。', '语法', 1, 2);
-- [单选/简单/阅读理解] Passage: Many people believe that a good memory is something
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1107, 2, 0, 1, 'Passage:
Many people believe that a good memory is something you are born with. Research, however, suggests that memory works more like a muscle: the more you use it, the stronger it becomes. One simple way to train it is to repeat new information aloud. When you speak a word or a sentence, your brain receives the message through both sound and movement, which makes the memory trace deeper than silent reading does.

Sleep also matters. During deep sleep, the brain sorts through what happened during the day and stores the important parts. Students who sleep less than six hours often find that facts they reviewed the night before have slipped away by morning. Perhaps the most useful habit, though, is to connect new facts with things you already know. A date becomes easier to remember when it is linked to a song, a place or a personal story.

According to the passage, what is memory compared to?', '[{"label":"A","content":"a machine"},{"label":"B","content":"a muscle"},{"label":"C","content":"a library"},{"label":"D","content":"a mirror"}]', 'B', 5, '短文第一段明确指出 memory works more like a muscle，即记忆更像肌肉，越用越强。A、C、D 三项在文中均未出现，属于无依据的干扰项。', '阅读理解', 1, 2);
-- [单选/中等/阅读理解] Passage: Many people believe that a good memory is something
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1108, 2, 0, 2, 'Passage:
Many people believe that a good memory is something you are born with. Research, however, suggests that memory works more like a muscle: the more you use it, the stronger it becomes. One simple way to train it is to repeat new information aloud. When you speak a word or a sentence, your brain receives the message through both sound and movement, which makes the memory trace deeper than silent reading does.

Sleep also matters. During deep sleep, the brain sorts through what happened during the day and stores the important parts. Students who sleep less than six hours often find that facts they reviewed the night before have slipped away by morning. Perhaps the most useful habit, though, is to connect new facts with things you already know. A date becomes easier to remember when it is linked to a song, a place or a personal story.

Why is repeating information aloud helpful, according to the passage?', '[{"label":"A","content":"It makes silent reading unnecessary."},{"label":"B","content":"It helps people sleep much longer."},{"label":"C","content":"The brain receives the message through sound and movement."},{"label":"D","content":"It makes new words easier to spell."}]', 'C', 5, '文中说 When you speak a word or a sentence, your brain receives the message through both sound and movement，即朗读使大脑通过声音和动作两条通道接收信息，记忆痕迹更深。A 项「使默读不再必要」文中未提；B 项把睡眠段落的内容张冠李戴；D 项与文意无关。', '阅读理解', 1, 2);
-- [单选/简单/阅读理解] Passage: Many people believe that a good memory is something
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1109, 2, 0, 1, 'Passage:
Many people believe that a good memory is something you are born with. Research, however, suggests that memory works more like a muscle: the more you use it, the stronger it becomes. One simple way to train it is to repeat new information aloud. When you speak a word or a sentence, your brain receives the message through both sound and movement, which makes the memory trace deeper than silent reading does.

Sleep also matters. During deep sleep, the brain sorts through what happened during the day and stores the important parts. Students who sleep less than six hours often find that facts they reviewed the night before have slipped away by morning. Perhaps the most useful habit, though, is to connect new facts with things you already know. A date becomes easier to remember when it is linked to a song, a place or a personal story.

What happens during deep sleep according to the passage?', '[{"label":"A","content":"The brain stores the important parts of the day."},{"label":"B","content":"The brain stops working completely."},{"label":"C","content":"The brain repeats new words aloud."},{"label":"D","content":"The brain forgets the whole day."}]', 'A', 5, '文中提到 During deep sleep, the brain sorts through what happened during the day and stores the important parts，故 A 正确。B、D 与原文相悖；C 项把白天朗读这一方法错误地放到了睡眠过程中。', '阅读理解', 1, 2);
-- [单选/中等/阅读理解] Passage: Many people believe that a good memory is something
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1110, 2, 0, 2, 'Passage:
Many people believe that a good memory is something you are born with. Research, however, suggests that memory works more like a muscle: the more you use it, the stronger it becomes. One simple way to train it is to repeat new information aloud. When you speak a word or a sentence, your brain receives the message through both sound and movement, which makes the memory trace deeper than silent reading does.

Sleep also matters. During deep sleep, the brain sorts through what happened during the day and stores the important parts. Students who sleep less than six hours often find that facts they reviewed the night before have slipped away by morning. Perhaps the most useful habit, though, is to connect new facts with things you already know. A date becomes easier to remember when it is linked to a song, a place or a personal story.

Which habit does the passage consider the most useful for memory?', '[{"label":"A","content":"Sleeping more than ten hours a day."},{"label":"B","content":"Reading silently every night."},{"label":"C","content":"Connecting new facts with things you already know."},{"label":"D","content":"Writing down every word you hear."}]', 'C', 5, '文中用 Perhaps the most useful habit, though, is to connect new facts with things you already know 明确给出答案。A 项睡眠时间在文中只提到少于六小时会影响记忆，并未提倡十小时以上；B 项默读正是文中拿来对比的做法；D 项文中未提及。', '阅读理解', 1, 2);
-- [单选/简单/词汇与短语] Free Wi-Fi is ______ in every reading room of the library.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1111, 2, 0, 1, 'Free Wi-Fi is ______ in every reading room of the library.', '[{"label":"A","content":"available"},{"label":"B","content":"possible"},{"label":"C","content":"probable"},{"label":"D","content":"ready"}]', 'A', 5, 'available 意为「可获得的、可使用的」，be available in/at 常用来表示某处提供某种服务，符合句意。B、C 表示「可能的」，其主语通常不能是 Wi-Fi 这类事物；D 项 ready 意为「准备好的」，多接 for 或 to do，不表示「对外开放、可供使用」。', '词汇与短语', 1, 2);
-- [单选/中等/语法] You ______ have told her the news yesterday; she was already
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1112, 2, 0, 2, 'You ______ have told her the news yesterday; she was already very upset.', '[{"label":"A","content":"mustn''t"},{"label":"B","content":"needn''t"},{"label":"C","content":"shouldn''t"},{"label":"D","content":"couldn''t"}]', 'C', 5, 'shouldn''t have done 表示「本不该做某事（但事实上已经做了）」，与后半句「她当时已经很伤心」吻合。A 项 mustn''t have done 不是规范用法；B 项 needn''t have done 意为「本不必做」，与「不该做」的责备语气不同；D 项 couldn''t have done 表示对过去的否定推测，与句意不符。', '语法', 1, 2);
-- [单选/中等/语法] This is the village ______ we spent our childhood.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1113, 2, 0, 2, 'This is the village ______ we spent our childhood.', '[{"label":"A","content":"which"},{"label":"B","content":"that"},{"label":"C","content":"where"},{"label":"D","content":"when"}]', 'C', 5, '先行词 the village 表示地点，从句 we spent our childhood 成分完整，缺少的是地点状语，故用关系副词 where（相当于 in which）。A、B 是关系代词，在从句中须作主语或宾语；D 项 when 指时间，与先行词不搭配。', '语法', 1, 2);
-- [单选/中等/词汇与短语] The scientist ______ his success to hard work rather than to
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1114, 2, 0, 2, 'The scientist ______ his success to hard work rather than to luck.', '[{"label":"A","content":"contributed"},{"label":"B","content":"attributed"},{"label":"C","content":"distributed"},{"label":"D","content":"devoted"}]', 'B', 5, 'attribute A to B 意为「把 A 归因于 B」，与 rather than to luck 的语境完全吻合。A 项 contribute...to 意为「把……贡献给」，主语一般是人把某物献给某事业；C 项 distribute...to 意为「把……分发给」；D 项 devote...to 意为「把……献给、致力于」，三者都不能表达「归因」。', '词汇与短语', 1, 2);
-- [单选/困难/语法] Hardly ______ the office when the telephone rang.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1115, 2, 0, 3, 'Hardly ______ the office when the telephone rang.', '[{"label":"A","content":"he had entered"},{"label":"B","content":"had he entered"},{"label":"C","content":"he entered"},{"label":"D","content":"did he enter"}]', 'B', 5, 'hardly 置于句首时主句要部分倒装，且 hardly...when... 表示「刚一……就……」，主句用过去完成时、从句用一般过去时，故为 had he entered。A、C 未倒装；D 项 did he enter 时态与句式都不符合 hardly...when... 的固定搭配。', '语法', 1, 2);
-- [单选/困难/词汇与短语] The company had to ______ the plan because of the sudden cut
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1116, 2, 0, 3, 'The company had to ______ the plan because of the sudden cut in funding.', '[{"label":"A","content":"call off"},{"label":"B","content":"call on"},{"label":"C","content":"call for"},{"label":"D","content":"call up"}]', 'A', 5, 'call off 意为「取消」，与 because of the sudden cut in funding（资金突然削减）的语境一致。B 项 call on 意为「拜访、号召」；C 项 call for 意为「需要、要求」；D 项 call up 意为「打电话、征召」，都不能与 the plan 搭配表示取消计划。', '词汇与短语', 1, 2);
-- [单选/简单/阅读理解] Passage: City farms are appearing in many parts of the world
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1117, 2, 0, 1, 'Passage:
City farms are appearing in many parts of the world. They are small pieces of land, often on the roofs of buildings or in empty lots, where vegetables and fruit are grown for the people who live nearby. Supporters say city farms do more than produce food. They cool the air around tall buildings, give insects and birds a place to live, and turn unused space into something green and useful.

Not everyone is happy with the idea. Building a farm on a roof costs a lot, and the soil must be light enough for the building to carry it. Water has to be carried up, which uses energy. Some residents also complain about the smell of fertilizer. Still, in crowded cities where fresh vegetables must travel hundreds of kilometers, a small farm on the roof may be the shortest distance between a seed and a dinner plate.

What is a city farm according to the passage?', '[{"label":"A","content":"A large farm far away from the city."},{"label":"B","content":"A small piece of land in the city where food is grown."},{"label":"C","content":"A shop that sells imported vegetables."},{"label":"D","content":"A park built only for birds and insects."}]', 'B', 5, '短文开头说 They are small pieces of land, often on the roofs of buildings or in empty lots, where vegetables and fruit are grown for the people who live nearby，故 B 正确。A 项与「位于城市之中」相矛盾；C、D 把城市农场曲解为商店或仅供鸟类昆虫的公园，均属过度推断。', '阅读理解', 1, 2);
-- [单选/中等/阅读理解] Passage: City farms are appearing in many parts of the world
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1118, 2, 0, 2, 'Passage:
City farms are appearing in many parts of the world. They are small pieces of land, often on the roofs of buildings or in empty lots, where vegetables and fruit are grown for the people who live nearby. Supporters say city farms do more than produce food. They cool the air around tall buildings, give insects and birds a place to live, and turn unused space into something green and useful.

Not everyone is happy with the idea. Building a farm on a roof costs a lot, and the soil must be light enough for the building to carry it. Water has to be carried up, which uses energy. Some residents also complain about the smell of fertilizer. Still, in crowded cities where fresh vegetables must travel hundreds of kilometers, a small farm on the roof may be the shortest distance between a seed and a dinner plate.

Which of the following is mentioned as a benefit of city farms?', '[{"label":"A","content":"They reduce the cost of building roofs."},{"label":"B","content":"They cool the air around tall buildings."},{"label":"C","content":"They take the place of all traditional farms."},{"label":"D","content":"They provide free fertilizer to residents."}]', 'B', 5, '文中列举的益处包括 cool the air around tall buildings、为昆虫和鸟类提供栖息地、把闲置空间变得绿色有用，故 B 正确。A 项文中只说建屋顶农场花费高，并未说能降低造价；C 项文中未提到取代传统农场；D 项把居民抱怨的「肥料气味」偷换为「免费提供肥料」。', '阅读理解', 1, 2);
-- [单选/中等/阅读理解] Passage: City farms are appearing in many parts of the world
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1119, 2, 0, 2, 'Passage:
City farms are appearing in many parts of the world. They are small pieces of land, often on the roofs of buildings or in empty lots, where vegetables and fruit are grown for the people who live nearby. Supporters say city farms do more than produce food. They cool the air around tall buildings, give insects and birds a place to live, and turn unused space into something green and useful.

Not everyone is happy with the idea. Building a farm on a roof costs a lot, and the soil must be light enough for the building to carry it. Water has to be carried up, which uses energy. Some residents also complain about the smell of fertilizer. Still, in crowded cities where fresh vegetables must travel hundreds of kilometers, a small farm on the roof may be the shortest distance between a seed and a dinner plate.

What problem with city farms does the passage mention?', '[{"label":"A","content":"The soil must be light enough for the roof."},{"label":"B","content":"They produce no vegetables at all."},{"label":"C","content":"They attract too many tourists."},{"label":"D","content":"They are too far from the city centre."}]', 'A', 5, '文中说 the soil must be light enough for the building to carry it，即土壤必须足够轻，这是屋顶农场必须面对的现实困难，故 A 正确。B 与短文内容相反；C 项游客问题文中未提；D 项与「城市农场就在城中」直接矛盾。', '阅读理解', 1, 2);
-- [单选/困难/阅读理解] Passage: City farms are appearing in many parts of the world
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1120, 2, 0, 3, 'Passage:
City farms are appearing in many parts of the world. They are small pieces of land, often on the roofs of buildings or in empty lots, where vegetables and fruit are grown for the people who live nearby. Supporters say city farms do more than produce food. They cool the air around tall buildings, give insects and birds a place to live, and turn unused space into something green and useful.

Not everyone is happy with the idea. Building a farm on a roof costs a lot, and the soil must be light enough for the building to carry it. Water has to be carried up, which uses energy. Some residents also complain about the smell of fertilizer. Still, in crowded cities where fresh vegetables must travel hundreds of kilometers, a small farm on the roof may be the shortest distance between a seed and a dinner plate.

What does the last sentence of the passage suggest?', '[{"label":"A","content":"City farms bring food closer to the people who eat it."},{"label":"B","content":"Seeds cannot grow in crowded cities."},{"label":"C","content":"Dinner plates should be made of soil."},{"label":"D","content":"Fresh vegetables should travel long distances."}]', 'A', 5, '末句用 a small farm on the roof may be the shortest distance between a seed and a dinner plate 作比喻，说明屋顶农场缩短了「种子到餐桌」的距离，即食物离食用者更近，故 A 正确。B、C、D 都是对该比喻的字面误解或与文意相反。', '阅读理解', 1, 2);
-- [多选/简单/词汇与短语] Which of the following words can be used to describe a perso
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1121, 2, 1, 1, 'Which of the following words can be used to describe a person who works hard?', '[{"label":"A","content":"diligent"},{"label":"B","content":"hardworking"},{"label":"C","content":"lazy"},{"label":"D","content":"industrious"}]', 'ABD', 5, 'diligent、hardworking、industrious 都表示「勤奋的、努力的」，可以形容工作努力的人，其中 industrious 较为书面。C 项 lazy 意为「懒惰的」，与 works hard 的意思相反，不能入选。', '词汇与短语', 1, 2);
-- [多选/中等/语法] Which of the following sentences use the present perfect ten
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1122, 2, 1, 2, 'Which of the following sentences use the present perfect tense correctly?', '[{"label":"A","content":"I have finished my homework already."},{"label":"B","content":"She has gone to Beijing last week."},{"label":"C","content":"They have lived here since 2015."},{"label":"D","content":"He has been ill for three days."}]', 'ACD', 5, '现在完成时不能与 last week 这类明确的过去时间状语连用。A 与 already 连用、C 与 since 引导的时间状语连用、D 与 for three days 连用，都符合现在完成时的用法；B 中 last week 指过去的具体时间，应改用一般过去时 went。', '语法', 1, 2);
-- [多选/中等/语法] Which of the following prepositional phrases are correct?
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1123, 2, 1, 2, 'Which of the following prepositional phrases are correct?', '[{"label":"A","content":"be good at"},{"label":"B","content":"be interested on"},{"label":"C","content":"be proud of"},{"label":"D","content":"be afraid of"}]', 'ACD', 5, 'be good at、be proud of、be afraid of 都是固定搭配，介词使用正确。B 项应为 be interested in，interested 不与介词 on 搭配表示「对……感兴趣」，这是介词搭配题中常见的干扰设置。', '语法', 1, 2);
-- [多选/中等/语法] Which of the following can properly follow 「look forward to」
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1124, 2, 1, 2, 'Which of the following can properly follow 「look forward to」?', '[{"label":"A","content":"hearing from you"},{"label":"B","content":"meet you"},{"label":"C","content":"the summer holiday"},{"label":"D","content":"seeing you again"}]', 'ACD', 5, 'look forward to 中的 to 是介词，后面应接名词、代词或动名词。A、D 是动名词短语，C 是名词短语，都可以作介词宾语；B 是动词原形，不能作介词宾语，应改为 meeting you。', '语法', 1, 2);
-- [多选/困难/语法] Which of the following sentences contain the subjunctive moo
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1125, 2, 1, 3, 'Which of the following sentences contain the subjunctive mood?', '[{"label":"A","content":"If I were you, I would accept the offer."},{"label":"B","content":"I wish I had studied harder last year."},{"label":"C","content":"He suggested that we should start early."},{"label":"D","content":"If it rains tomorrow, we will stay at home."}]', 'ABC', 5, 'A 是与现在事实相反的虚拟条件句（从句用 were，主句用 would + 动词原形）；B 是 wish 后表示与过去事实相反的虚拟语气（用 had done）；C 中 suggest 表示「建议」时，宾语从句用 (should) + 动词原形。D 是真实条件句，表示可能发生的情况，不属于虚拟语气。', '语法', 1, 2);
-- [多选/中等/语法] Which of the following sentences are grammatically correct?
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1126, 2, 1, 2, 'Which of the following sentences are grammatically correct?', '[{"label":"A","content":"It took me two hours to finish the report."},{"label":"B","content":"I spent two hours to finish the report."},{"label":"C","content":"I spent two hours finishing the report."},{"label":"D","content":"Finishing the report took me two hours."}]', 'ACD', 5, 'take 表示「花费时间」的常用句型是 It takes/took sb. some time to do sth.，A 符合该句型，D 是动名词作主语，同样正确。spend 表示「花费时间做某事」时要用 spend some time (in) doing sth.，故 C 正确，而 B 中的 to finish 用法错误。', '语法', 1, 2);
-- [多选/中等/语法] Which of the following words are adverbs?
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1127, 2, 1, 2, 'Which of the following words are adverbs?', '[{"label":"A","content":"quickly"},{"label":"B","content":"happy"},{"label":"C","content":"carefully"},{"label":"D","content":"friendly"}]', 'AC', 5, 'quickly 和 carefully 是方式副词，可以修饰动词。B 项 happy 是形容词；D 项 friendly 虽以 -ly 结尾，却是形容词，意为「友好的」，不能作副词修饰动词，是「以 -ly 结尾却非副词」的典型例子。', '语法', 1, 2);
-- [多选/困难/语法] Which of the following sentences are correct in subject-verb
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1128, 2, 1, 3, 'Which of the following sentences are correct in subject-verb agreement?', '[{"label":"A","content":"Each of the students has a dictionary."},{"label":"B","content":"Neither of the answers is correct."},{"label":"C","content":"The number of visitors are increasing."},{"label":"D","content":"A number of students have signed up."}]', 'ABD', 5, 'each of、neither of 后接复数名词时，谓语动词用单数，故 A、B 正确；a number of + 复数名词意为「许多」，谓语用复数，故 D 正确。the number of 意为「……的数量」，中心词是 number，谓语应用单数 is，故 C 错误。', '语法', 1, 2);
-- [判断/简单/语法] 下列句子语法正确：She has been to Shanghai twice.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1129, 2, 2, 1, '下列句子语法正确：She has been to Shanghai twice.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, 'have/has been to 表示「去过某地（现在已经回来）」，与 twice 连用表示经历，句子的时态和用法都正确。若表示「去了某地（尚未回来）」，才用 has gone to。', '语法', 1, 2);
-- [判断/中等/语法] 下列句子语法正确：The news are very exciting.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1130, 2, 2, 2, '下列句子语法正确：The news are very exciting.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, 'news 是以 -s 结尾的不可数名词，作主语时谓语动词用单数，应改为 The news is very exciting。类似情况的词还有 physics、mathematics 等学科名称，不能因为词尾是 -s 就当作复数。', '语法', 1, 2);
-- [判断/简单/语法] 下列句子语法正确：I am looking forward to see you soon.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1131, 2, 2, 1, '下列句子语法正确：I am looking forward to see you soon.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, 'look forward to 中的 to 是介词，后面要接动名词，应改为 looking forward to seeing you soon。本句错在把介词 to 误当成了动词不定式符号。', '语法', 1, 2);
-- [判断/中等/语法] 下列句子语法正确：It is important that everyone (should) be present o
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1132, 2, 2, 2, '下列句子语法正确：It is important that everyone (should) be present on time.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '在 It is important/necessary/essential that... 结构中，主语从句要用虚拟语气，谓语为 (should) + 动词原形，(should) be present 正符合该规则，句子正确。', '语法', 1, 2);
-- [判断/简单/语法] 下列句子用法正确：He is used to getting up early.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1133, 2, 2, 1, '下列句子用法正确：He is used to getting up early.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, 'be used to doing sth. 意为「习惯于做某事」，其中的 to 是介词，后接动名词，故 is used to getting up 正确。注意与 used to do（过去常常做）和 be used to do（被用来做）相区别。', '语法', 1, 2);
-- [判断/中等/语法] 下列句子语法正确：Neither of the two books are interesting.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1134, 2, 2, 2, '下列句子语法正确：Neither of the two books are interesting.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, 'neither of + 复数名词作主语时，谓语动词通常用单数，应改为 Neither of the two books is interesting。are 与主语的单数意义不一致。', '语法', 1, 2);
-- [判断/简单/语法] 下列句子语法正确：The boy is enough old to go to school alone.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1135, 2, 2, 1, '下列句子语法正确：The boy is enough old to go to school alone.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, 'enough 修饰形容词或副词时应放在其后，正确说法是 The boy is old enough to go to school alone。只有当 enough 修饰名词时才前置，例如 enough time。', '语法', 1, 2);
-- [判断/困难/语法] 下列句子语法正确：Hardly had I got home when it began to rain heavily
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1136, 2, 2, 3, '下列句子语法正确：Hardly had I got home when it began to rain heavily.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, 'hardly 置于句首时主句用部分倒装，且 hardly...when... 表示「刚一……就……」，主句用过去完成时、when 从句用一般过去时。本句 had I got home 与 began 的时态与语序完全符合该结构，故正确。', '语法', 1, 2);
-- [填空/简单/语法] 用括号内单词的适当形式填空（要求使用现在完成时）：He ______ (study) English since he 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1137, 2, 3, 1, '用括号内单词的适当形式填空（要求使用现在完成时）：He ______ (study) English since he was ten years old.', NULL, 'has studied', 5, 'since 引导的时间状语从句用一般过去时，主句要用现在完成时；主语 He 是第三人称单数，故填 has studied。若强调动作一直持续，也可用 has been studying，但本题明确要求使用现在完成时，故答案为 has studied。', '语法', 1, 2);
-- [填空/简单/语法] 在下列句子的横线处填入恰当的介词：We arrived ______ Beijing at six o'clock ye
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1138, 2, 3, 1, '在下列句子的横线处填入恰当的介词：We arrived ______ Beijing at six o''clock yesterday morning.', NULL, 'in', 5, 'arrive 后接大地点（城市、国家等）用介词 in，接小地点（车站、机场、村庄等）用 at。Beijing 是城市，故用 in。英语中不说 arrive to a place。', '语法', 1, 2);
-- [填空/简单/语法] 用括号内单词的适当形式填空：The little girl sang the song ______ (beautifu
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1139, 2, 3, 1, '用括号内单词的适当形式填空：The little girl sang the song ______ (beautiful).', NULL, 'beautifully', 5, '此处需要修饰动词 sang，应使用副词，故把形容词 beautiful 变为副词 beautifully。形容词 beautiful 只能修饰名词或作表语，不能直接修饰动词。', '语法', 1, 2);
-- [填空/中等/语法] 用括号内单词的适当形式填空：If I ______ (be) you, I would take the job.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1140, 2, 3, 2, '用括号内单词的适当形式填空：If I ______ (be) you, I would take the job.', NULL, 'were', 5, '这是与现在事实相反的虚拟条件句，从句谓语用过去式，be 动词一律用 were；主句用 would/could/might + 动词原形。故填 were，不能填 was 或 am。', '语法', 1, 2);
-- [填空/中等/语法] 用括号内单词的适当形式填空：The bridge ______ (build) in 1998 and is still
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1141, 2, 3, 2, '用括号内单词的适当形式填空：The bridge ______ (build) in 1998 and is still in use today.', NULL, 'was built', 5, '主语 The bridge 与 build 之间是被动关系，且时间状语 in 1998 表示过去，故用一般过去时的被动语态 was built。后半句 is still in use 用一般现在时，与前半句的过去时间并不冲突。', '语法', 1, 2);
-- [填空/简单/语法] 在下列句子的横线处填入恰当的介词：You should take advantage ______ the chance
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1142, 2, 3, 1, '在下列句子的横线处填入恰当的介词：You should take advantage ______ the chance to practise your spoken English.', NULL, 'of', 5, 'take advantage of 是固定短语，意为「利用」，介词只能用 of，故填 of。该短语不能换成其他介词，也不要说成 take advantage over。', '语法', 1, 2);
-- [填空/中等/语法] 用括号内单词的适当形式填空：______ (compare) with his brother, he is more 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1143, 2, 3, 2, '用括号内单词的适当形式填空：______ (compare) with his brother, he is more patient.', NULL, 'Compared', 5, '句中主语 he 与 compare 之间是被动关系（他被人拿来与弟弟比较），故用过去分词 Compared 作状语，构成 Compared with... 这一固定表达。若用 Comparing，则逻辑主语应是主动去比较别的事物的人，与句意不符。', '语法', 1, 2);
-- [填空/简单/语法] 用括号内单词的适当形式填空：The children are looking forward to ______ (vi
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1144, 2, 3, 1, '用括号内单词的适当形式填空：The children are looking forward to ______ (visit) the science museum.', NULL, 'visiting', 5, 'look forward to 中的 to 是介词，后面要接动名词，故填 visiting。学生常误填 visit，原因就是把介词 to 误认为动词不定式符号。', '语法', 1, 2);
-- [简答/简单/翻译] 把下列句子译成汉语：Nothing is more important than health.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1145, 2, 4, 1, '把下列句子译成汉语：Nothing is more important than health.', NULL, '参考译文：没有什么比健康更重要。（也可译为「健康最重要」。）', 16, '否定词 nothing 与比较级连用表示最高级含义，即「健康最重要」。翻译时应保留「没有什么比……更……」这一汉语表达，不必逐字译为「没有东西是更重要的」。', '翻译', 1, 2);
-- [简答/简单/翻译] 把下列句子译成英语：他太累了，以至于不能继续工作了。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1146, 2, 4, 1, '把下列句子译成英语：他太累了，以至于不能继续工作了。', NULL, '参考译文：He was so tired that he could not go on working.（同义表达：He was too tired to go on working.）', 16, '「太……以至于不能……」可用 so + 形容词 + that + 否定句，也可用 too...to... 结构。「继续工作」用 go on working 或 continue to work。句子叙述过去的情况，故用一般过去时 was。', '翻译', 1, 2);
-- [简答/简单/翻译] 把下列句子译成汉语：It was not until midnight that he finished his hom
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1147, 2, 4, 1, '把下列句子译成汉语：It was not until midnight that he finished his homework.', NULL, '参考译文：直到半夜他才完成作业。', 16, '本句是 not until 的强调句型 It was not until... that...，翻译时应处理为「直到……才……」。若不用强调结构，该句相当于 He did not finish his homework until midnight，两句意思相同。', '翻译', 1, 2);
-- [简答/中等/写作] 请根据以下要点，用英语写一封约 100 词的申请信（开头和结尾已给出，不计入总词数）。要点：1. 你从学校网站得知学生会
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1148, 2, 4, 2, '请根据以下要点，用英语写一封约 100 词的申请信（开头和结尾已给出，不计入总词数）。要点：1. 你从学校网站得知学生会正在招募志愿者；2. 你愿意参加并为新生提供帮助；3. 你的优势是英语口语好、有耐心、周末有空；4. 希望得到面试机会。', NULL, '写作要点与常用句式：第一段说明信息来源与写信目的，如 I learned from the school website that the Students'' Union is recruiting volunteers, so I am writing to apply for the position.；第二段陈述自身优势，如 I am good at spoken English and I am patient with others. Besides, I am free at weekends.；第三段表达期待，如 I would appreciate it if you could give me an interview. I am looking forward to your early reply.。', 16, '本题为应用文写作，评分关注三方面：要点是否齐全（信息来源、申请意愿、个人优势、期待面试）、格式是否得体（书信的称呼与落款）、语言是否准确。叙述能力与意愿用一般现在时，提出请求用 would like、would appreciate 等委婉表达，切忌逐字直译汉语提纲而写出中式英语。', '写作', 1, 2);
-- [简答/困难/写作] 请根据以下提纲，用英语写一篇约 120 词的短文，谈谈你对「中学生使用手机」的看法。提纲：1. 手机给学习与生活带来的便
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1149, 2, 4, 3, '请根据以下提纲，用英语写一篇约 120 词的短文，谈谈你对「中学生使用手机」的看法。提纲：1. 手机给学习与生活带来的便利；2. 过度使用手机带来的问题；3. 你的建议。', NULL, '写作要点与常用句式：第一段引出话题并说明便利，如 With the development of technology, mobile phones have become part of our daily life. They make it easier to search for information and keep in touch with others.；第二段指出问题，如 However, spending too much time on phones does harm to our eyes and our study. Some students even play games in class.；第三段提出建议，如 In my opinion, we should use phones properly. For example, we can set a time limit and keep phones away while studying.。常用句式：play an important part in, do harm to, in my opinion, it is advisable to...。', 16, '本题为提纲式议论文，需按「便利—问题—建议」三段展开，与三个提纲要点一一对应。评分要点包括：内容是否覆盖全部提纲、是否使用 however、besides、in my opinion 等连接词使逻辑清晰、时态是否以一般现在时为主，以及建议是否具体可行而非空泛表态。', '写作', 1, 2);
-- [简答/简单/翻译] 把下列句子译成英语：这本书值得再读一遍。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1150, 2, 4, 1, '把下列句子译成英语：这本书值得再读一遍。', NULL, '参考译文：The book is worth reading again.', 16, '「值得做某事」用 be worth doing，其中动名词用主动形式表达被动意义，故为 is worth reading。不能写成 be worth to read；若改用 worthy，则须说 be worthy of being read 或 be worthy to be read。', '翻译', 1, 2);
-- [单选/简单/词汇与短语] 单项选择：The heavy rain seriously （ ） the traffic in the city ce
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1151, 2, 0, 1, '单项选择：The heavy rain seriously （　　） the traffic in the city centre.', '[{"label":"A","content":"affected"},{"label":"B","content":"effected"},{"label":"C","content":"afforded"},{"label":"D","content":"offered"}]', 'A', 5, 'affect 是及物动词，意为「影响」，符合「大雨严重影响市中心交通」的句意。B 项 effect 通常作名词，意为「效果、影响」，作动词时意为「促成、实现」（如 effect a change），不能表示「对……产生不良影响」；C 项 afforded 意为「负担得起」，D 项 offered 意为「提供」，都只是与答案拼写相近的干扰项。', '词汇与短语', 1, 2);
-- [单选/简单/语法] 单项选择：The new teacher is very patient （ ） the children who ar
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1152, 2, 0, 1, '单项选择：The new teacher is very patient （　　） the children who are slow in reading.', '[{"label":"A","content":"with"},{"label":"B","content":"to"},{"label":"C","content":"for"},{"label":"D","content":"at"}]', 'A', 5, 'be patient with sb. 是固定搭配，意为「对某人有耐心」，介词只能用 with。B 项 to 用于 be kind to sb.、be polite to sb. 等搭配；C 项 for 多表示目的或对象；D 项 at 用于 be good at、be slow at 等搭配，三者都不能与 patient 连用表示「对某人有耐心」。', '语法', 1, 2);
-- [单选/中等/语法] 单项选择：My hands are dirty because I （ ） the flowers in the gar
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1153, 2, 0, 2, '单项选择：My hands are dirty because I （　　） the flowers in the garden all morning.', '[{"label":"A","content":"have been watering"},{"label":"B","content":"watered"},{"label":"C","content":"have watered"},{"label":"D","content":"was watering"}]', 'A', 5, '现在完成进行时 have been watering 表示从过去一直持续到现在、并可能继续下去的动作，与 all morning 以及「现在手还是脏的」这一结果完全吻合。B、D 两项是一般过去时和过去进行时，只说明过去的情况，无法解释现在的结果；C 项现在完成时强调动作已经完成，与「整个上午一直在浇花」的持续性不符。', '语法', 1, 2);
-- [单选/简单/语法] 单项选择：All the exercise books （ ） before Friday.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1154, 2, 0, 1, '单项选择：All the exercise books （　　） before Friday.', '[{"label":"A","content":"must hand in"},{"label":"B","content":"must be handed in"},{"label":"C","content":"must handed in"},{"label":"D","content":"must be handing in"}]', 'B', 5, '主语 All the exercise books 与 hand in 之间是被动关系（作业本被交上去），含情态动词的被动语态结构为 must be + 过去分词，故用 must be handed in。A 项是主动语态，作业本不可能自己去交；C 项漏掉了 be，情态动词后不能直接跟过去分词；D 项是进行时，与句意不符。', '语法', 1, 2);
-- [单选/中等/语法] 单项选择：Please remember （ ） the windows when you leave the read
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1155, 2, 0, 2, '单项选择：Please remember （　　） the windows when you leave the reading room.', '[{"label":"A","content":"to lock"},{"label":"B","content":"locking"},{"label":"C","content":"lock"},{"label":"D","content":"locked"}]', 'A', 5, 'remember to do sth. 表示「记得去做某事」（事情还没有做），remember doing sth. 表示「记得做过某事」（事情已经做过）。本句是在对方离开前作出提醒，故用 to lock。B 项 locking 意为「记得锁过窗户」，与提醒的语境矛盾；C、D 两项的形式都不能作 remember 的宾语。', '语法', 1, 2);
-- [单选/简单/语法] 单项选择：The girl （ ） by the window is my cousin.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1156, 2, 0, 1, '单项选择：The girl （　　） by the window is my cousin.', '[{"label":"A","content":"sitting"},{"label":"B","content":"sat"},{"label":"C","content":"to sit"},{"label":"D","content":"sits"}]', 'A', 5, '句中已有谓语 is，空格处只能填非谓语动词；the girl 与 sit 是主动关系，故用现在分词 sitting 作后置定语，相当于 who is sitting by the window。B 项 sat 是过去式，不能作定语；C 项不定式作定语通常表示将来的动作；D 项 sits 会使句子出现两个谓语。', '语法', 1, 2);
-- [单选/简单/语法] 单项选择：The teacher made the boy （ ） the sentence three times.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1157, 2, 0, 1, '单项选择：The teacher made the boy （　　） the sentence three times.', '[{"label":"A","content":"to read"},{"label":"B","content":"read"},{"label":"C","content":"reading"},{"label":"D","content":"reads"}]', 'B', 5, 'make sb. do sth. 是使役动词的固定用法，宾语补足语用不带 to 的动词原形，故填 read。A 项多加了 to，是受「不定式作宾补」思维定式影响而误选的典型干扰项；C 项现在分词表示动作正在进行，与「读三遍」这一完整动作不符；D 项第三人称单数形式不能作宾语补足语。', '语法', 1, 2);
-- [单选/中等/语法] 单项选择：This is the young writer （ ） novels are popular among c
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1158, 2, 0, 2, '单项选择：This is the young writer （　　） novels are popular among college students.', '[{"label":"A","content":"who"},{"label":"B","content":"whose"},{"label":"C","content":"which"},{"label":"D","content":"that"}]', 'B', 5, '空格后的 novels 是名词，与先行词 the young writer 构成所属关系，故用关系代词 whose 作定语，whose novels 相当于 the writer''s novels。A 项 who 在从句中只能作主语或宾语；C、D 两项指代事物，都不能放在名词前表示所属关系。', '语法', 1, 2);
-- [单选/困难/语法] 单项选择：The tool （ ） the worker repaired the machine is a speci
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1159, 2, 0, 3, '单项选择：The tool （　　） the worker repaired the machine is a special spanner.', '[{"label":"A","content":"with which"},{"label":"B","content":"which"},{"label":"C","content":"that"},{"label":"D","content":"by which"}]', 'A', 5, '表示「用某件工具做某事」要用介词 with，故定语从句由 with which 引导，with which 在从句中作方式状语。B、C 两项在从句中只能作主语或宾语，而从句中已有主语 the worker 和宾语 the machine；D 项 by 表示手段时通常接动名词或抽象名词（如 by working hard），不能指具体的工具。', '语法', 1, 2);
-- [单选/中等/语法] 单项选择：（ ） she said at the meeting made all of us think deeply
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1160, 2, 0, 2, '单项选择：（　　） she said at the meeting made all of us think deeply.', '[{"label":"A","content":"What"},{"label":"B","content":"That"},{"label":"C","content":"Which"},{"label":"D","content":"It"}]', 'A', 5, '从句中 said 缺少宾语，需要一个既起引导作用、又在从句中充当宾语的连接代词，故用 What（相当于 the thing that），引导主语从句。B 项 That 引导主语从句时从句结构必须完整，而这里 said 没有宾语；C 项 Which 表示「哪一个」，与句意不符；D 项 It 不能引导从句。', '语法', 1, 2);
-- [单选/简单/语法] 单项选择：Excuse me, could you tell me （ ）?
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1161, 2, 0, 1, '单项选择：Excuse me, could you tell me （　　）?', '[{"label":"A","content":"when does the train leave"},{"label":"B","content":"when the train leaves"},{"label":"C","content":"when the train leave"},{"label":"D","content":"when will the train leave"}]', 'B', 5, '宾语从句必须用陈述语序，即「连接词 + 主语 + 谓语」，故为 when the train leaves。A、D 两项保留了疑问句的倒装语序，是最容易误选的干扰项；C 项主语 the train 是第三人称单数，谓语却用了原形 leave，主谓不一致。', '语法', 1, 2);
-- [单选/中等/语法] 单项选择：（ ） tired he was, he went on working till midnight.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1162, 2, 0, 2, '单项选择：（　　） tired he was, he went on working till midnight.', '[{"label":"A","content":"However"},{"label":"B","content":"Whatever"},{"label":"C","content":"No matter"},{"label":"D","content":"Although"}]', 'A', 5, 'however 引导让步状语从句时可以直接修饰形容词或副词，语序为 However + adj./adv. + 主语 + 系动词，故 However tired he was 正确。B 项 Whatever 只能修饰名词或在从句中作主语、宾语；C 项 No matter 必须与 how 连用，即 No matter how tired he was；D 项 Although 后面要接完整的句子，不能说 Although tired he was。', '语法', 1, 2);
-- [单选/困难/语法] 单项选择：If you had set off half an hour earlier, you （ ） the tr
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1163, 2, 0, 3, '单项选择：If you had set off half an hour earlier, you （　　） the train.', '[{"label":"A","content":"would not have missed"},{"label":"B","content":"would not miss"},{"label":"C","content":"did not miss"},{"label":"D","content":"had not missed"}]', 'A', 5, '本题是与过去事实相反的虚拟条件句：从句用 had + 过去分词，主句用 would/could/might + have + 过去分词，故为 would not have missed。B 项 would not miss 是与现在或将来事实相反的主句形式；C 项是陈述语气；D 项把虚拟形式误用到了主句之中。', '语法', 1, 2);
-- [单选/中等/语法] 单项选择：You （ ） the tickets — I had already bought two.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1164, 2, 0, 2, '单项选择：You （　　） the tickets — I had already bought two.', '[{"label":"A","content":"needn''t have bought"},{"label":"B","content":"mustn''t have bought"},{"label":"C","content":"can''t have bought"},{"label":"D","content":"didn''t need to buy"}]', 'A', 5, 'needn''t have done 表示「本来不必做某事，却已经做了」，与后半句「我已经买了两张」相呼应，说明买票是多余的。B、C 两项都表示对过去情况的推测（「一定没买」「不可能买了」），与事实正好相反；D 项 didn''t need to buy 只说明当时不必买，无法体现「票已经买了」这一结果。', '语法', 1, 2);
-- [单选/简单/语法] 单项选择：You've been working in the sun all day. You （ ） be very
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1165, 2, 0, 1, '单项选择：You''ve been working in the sun all day. You （　　） be very thirsty.', '[{"label":"A","content":"must"},{"label":"B","content":"can"},{"label":"C","content":"need"},{"label":"D","content":"would"}]', 'A', 5, 'must 可以表示有根据的肯定推测，意为「一定」，此处依据是「在太阳下干了一整天活」，故 must be very thirsty 最贴切。B 项 can 表示推测时一般只用于疑问句和否定句，很少用于肯定句；C 项 need 作情态动词只用于疑问句和否定句；D 项 would 表示意愿或过去将来，与推测无关。', '语法', 1, 2);
-- [单选/简单/语法] 单项选择：Reading English aloud every morning （ ） a good habit.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1166, 2, 0, 1, '单项选择：Reading English aloud every morning （　　） a good habit.', '[{"label":"A","content":"is"},{"label":"B","content":"are"},{"label":"C","content":"were"},{"label":"D","content":"have been"}]', 'A', 5, '动名词短语 Reading English aloud every morning 作主语时被视为一个整体，谓语动词用单数，且本句陈述的是一般事实，故用 is。B、C、D 三项都是复数形式，是把动名词短语误当成复数名词所致；同理，To learn a language well takes time 中的谓语也用单数。', '语法', 1, 2);
-- [单选/困难/语法] 单项选择：Only in this way （ ） improve your spoken English.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1167, 2, 0, 3, '单项选择：Only in this way （　　） improve your spoken English.', '[{"label":"A","content":"can you"},{"label":"B","content":"you can"},{"label":"C","content":"you could"},{"label":"D","content":"do you can"}]', 'A', 5, 'only + 状语（in this way）置于句首时，主句要用部分倒装，即把情态动词或助动词提到主语之前，故为 can you improve。B、C 两项没有倒装；D 项把助动词 do 与情态动词 can 混用，一个谓语中不能同时出现两个助动词。', '语法', 1, 2);
-- [单选/中等/语法] 单项选择：It was in the school library （ ） I first met my best fr
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1168, 2, 0, 2, '单项选择：It was in the school library （　　） I first met my best friend.', '[{"label":"A","content":"that"},{"label":"B","content":"which"},{"label":"C","content":"where"},{"label":"D","content":"when"}]', 'A', 5, '本句是强调句型 It is/was + 被强调部分 + that + 其余部分，被强调的是地点状语 in the school library，连接词仍用 that。B 项 which 不能用于强调句；C、D 两项最容易误选，因为被强调的部分表示地点和时间，但强调句的连接词不随被强调部分的性质变化，只有强调人时才可以用 who。', '语法', 1, 2);
-- [单选/中等/语法] 单项选择：The more you practise, （ ） you will speak English.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1169, 2, 0, 2, '单项选择：The more you practise, （　　） you will speak English.', '[{"label":"A","content":"the more fluently"},{"label":"B","content":"more fluently"},{"label":"C","content":"the most fluently"},{"label":"D","content":"the more fluent"}]', 'A', 5, '「the + 比较级..., the + 比较级...」表示「越……就越……」，后半句需要一个修饰 speak 的副词比较级，故用 the more fluently。B 项缺少定冠词 the，比较级句式不完整；C 项是最高级，只用于三者或三者以上的比较；D 项 fluent 是形容词，不能修饰动词 speak。', '语法', 1, 2);
-- [单选/中等/阅读理解] 阅读下面短文，选择最佳答案： Passage: Many students believe that the best 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1170, 2, 0, 2, '阅读下面短文，选择最佳答案：
Passage:
Many students believe that the best way to remember a new word is to look it up in a dictionary again and again. A study of 200 college students suggests something different. The students who wrote each new word into a sentence of their own remembered nearly twice as many words as those who simply copied the definitions. The reason is simple: a word becomes part of your memory when it is connected with a real situation. The researchers also found that reviewing the sentences a week later helped more than reviewing them the same evening. So the next time you meet a new word, do not just copy it — use it.

According to the passage, what helped the students remember more new words?（　　）', '[{"label":"A","content":"Copying the definitions many times."},{"label":"B","content":"Writing each new word into a sentence of their own."},{"label":"C","content":"Looking up the words in a dictionary again and again."},{"label":"D","content":"Reviewing the words on the same evening."}]', 'B', 5, '文中说 The students who wrote each new word into a sentence of their own remembered nearly twice as many words，即把生词写进自己造的句子里效果最好，故 B 正确。A、C 两项正是文中拿来对比的「反复抄定义、反复查词典」的做法；D 项与原文相反，文中说一周后复习比当天晚上复习更有效。', '阅读理解', 1, 2);
-- [多选/简单/词汇与短语] 多项选择：Which of the following words have a meaning similar to 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1171, 2, 1, 1, '多项选择：Which of the following words have a meaning similar to 「important」?（　　）', '[{"label":"A","content":"significant"},{"label":"B","content":"vital"},{"label":"C","content":"trivial"},{"label":"D","content":"essential"}]', 'ABD', 5, 'significant、vital、essential 都表示「重要的、至关重要的」，其中 vital 和 essential 语气更强，意为「必不可少的」。C 项 trivial 意为「琐碎的、不重要的」，与 important 意思相反，不能入选，它是本题唯一表示否定意义的干扰项。', '词汇与短语', 1, 2);
-- [多选/中等/词汇与短语] 多项选择：Which of the following sentences use the phrasal verbs 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1172, 2, 1, 2, '多项选择：Which of the following sentences use the phrasal verbs correctly?（　　）', '[{"label":"A","content":"Please turn off the lights before you leave the office."},{"label":"B","content":"The sports meeting was put off till next Friday."},{"label":"C","content":"He gave up to smoke three years ago."},{"label":"D","content":"I ran into an old classmate in the street yesterday."}]', 'ABD', 5, 'turn off（关掉）、put off（推迟）、run into（偶然遇见）在 A、B、D 三句中都用得正确，其中 put off 用于被动语态时仍保留介词 off。C 项 give up 后面要接动名词，应改为 He gave up smoking three years ago，不能说 give up to smoke，这是动名词与不定式混用的常见错误。', '词汇与短语', 1, 2);
-- [多选/中等/语法] 多项选择：Which of the following sentences are in the passive voi
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1173, 2, 1, 2, '多项选择：Which of the following sentences are in the passive voice?（　　）', '[{"label":"A","content":"The song was written by a student of our class."},{"label":"B","content":"The problem has been solved at last."},{"label":"C","content":"They are building a new bridge over the river."},{"label":"D","content":"The meeting will be held in the main hall."}]', 'ABD', 5, 'A 项是一般过去时的被动语态（was written），B 项是现在完成时的被动语态（has been solved），D 项是一般将来时的被动语态（will be held），三者都是「be + 过去分词」结构。C 项 are building 是主动语态，改为被动应说 A new bridge is being built over the river。', '语法', 1, 2);
-- [多选/中等/语法] 多项选择：Which of the following sentences contain an attributive
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1174, 2, 1, 2, '多项选择：Which of the following sentences contain an attributive clause?（　　）', '[{"label":"A","content":"The man who is standing under the tree is my uncle."},{"label":"B","content":"This is the village where we spent our holiday."},{"label":"C","content":"Please tell me when the train will arrive."},{"label":"D","content":"I have lost the book that you lent me last week."}]', 'ABD', 5, 'A 项 who 引导的从句修饰 the man，B 项 where 引导的从句修饰 the village，D 项 that 引导的从句修饰 the book，三者都是定语从句。C 项 when 引导的是宾语从句，作 tell 的宾语，表示时间，并不是修饰某个名词的定语从句，这是最容易误选的一项。', '语法', 1, 2);
-- [多选/中等/语法] 多项选择：Which of the following sentences use modal verbs correc
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1175, 2, 1, 2, '多项选择：Which of the following sentences use modal verbs correctly?（　　）', '[{"label":"A","content":"You mustn''t smoke here — it''s a gas station."},{"label":"B","content":"She can be able to swim across the river."},{"label":"C","content":"You needn''t come so early tomorrow."},{"label":"D","content":"He may have missed the last bus."}]', 'ACD', 5, 'A 项 mustn''t 表示「禁止」，C 项 needn''t 表示「不必」，D 项 may have done 表示对过去情况的推测（「可能已经错过了末班车」），三者用法都正确。B 项 can 与 be able to 都表示能力，不能叠用，应改为 She can swim across the river 或 She is able to swim across the river。', '语法', 1, 2);
-- [多选/困难/语法] 多项选择：Which of the following sentences use non-finite verbs c
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1176, 2, 1, 3, '多项选择：Which of the following sentences use non-finite verbs correctly?（　　）', '[{"label":"A","content":"Seeing the teacher coming, the students stopped talking."},{"label":"B","content":"Having finished his homework, he went out to play basketball."},{"label":"C","content":"To be honest, I don''t like the idea at all."},{"label":"D","content":"Looked out of the window, I saw a beautiful rainbow."}]', 'ABC', 5, 'A 项现在分词作时间状语，其逻辑主语 the students 与 see 是主动关系；B 项分词的完成式表示该动作先于主句动作发生；C 项不定式作独立成分，意为「说实话」，三者都正确。D 项的逻辑主语 I 与 look 是主动关系，应改为 Looking out of the window，用过去分词就变成了「我被看向窗外」，逻辑不通。', '语法', 1, 2);
-- [多选/困难/语法] 多项选择：Which of the following sentences are in inverted order?
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1177, 2, 1, 3, '多项选择：Which of the following sentences are in inverted order?（　　）', '[{"label":"A","content":"Never have I seen such a beautiful sunset."},{"label":"B","content":"Here comes the bus we have been waiting for."},{"label":"C","content":"Only then did he realize how serious the problem was."},{"label":"D","content":"He not only wrote the song but also sang it himself."}]', 'ABC', 5, 'A 项否定词 Never 置于句首，引起部分倒装；B 项 Here 置于句首且主语是名词，用全部倒装；C 项 only + 状语置于句首，同样引起部分倒装。D 项用的是 not only...but also... 的常规语序，并未倒装，若把 Not only 提到句首，则前半句需改为 Not only did he write the song。', '语法', 1, 2);
-- [多选/简单/词汇与短语] 多项选择：Which of the following words can be used to describe so
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1178, 2, 1, 1, '多项选择：Which of the following words can be used to describe something difficult?（　　）', '[{"label":"A","content":"hard"},{"label":"B","content":"tough"},{"label":"C","content":"simple"},{"label":"D","content":"challenging"}]', 'ABD', 5, 'hard、tough、challenging 都可以形容「困难的、有挑战性的」，其中 tough 多用于口语，challenging 强调「有挑战性但值得一试」。C 项 simple 意为「简单的」，与 difficult 意思相反，不能入选。', '词汇与短语', 1, 2);
-- [判断/简单/语法] 下列句子语法正确：I have seen this film two years ago.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1179, 2, 2, 1, '下列句子语法正确：I have seen this film two years ago.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '现在完成时不能与 two years ago 这类表示明确过去时间的状语连用，本句应改为 I saw this film two years ago。如果去掉具体时间，则可以说 I have seen this film twice，用现在完成时表示经历。', '语法', 1, 2);
-- [判断/简单/词汇与短语] 下列句子语法正确：He works hardly every day.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1180, 2, 2, 1, '下列句子语法正确：He works hardly every day.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, 'hard 作副词时意为「努力地」，hardly 虽然也是副词，但意思是「几乎不」，用在本句中意思完全相反，应改为 He works hard every day。判断这类句子不能只看词尾有没有 -ly，还要看副词的词义。', '词汇与短语', 1, 2);
-- [判断/简单/语法] 下列句子语法正确：Either of the two answers is acceptable.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1181, 2, 2, 1, '下列句子语法正确：Either of the two answers is acceptable.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, 'either of + 复数名词作主语时，谓语动词用单数，本句用 is 是正确的，句意为「两个答案中的任何一个都可以接受」。学生常因为看到 two answers 是复数而误用 are，实际上 either 才是句子的中心词。', '语法', 1, 2);
-- [判断/简单/阅读理解] 阅读下面短文，判断下列表述是否符合短文内容： Passage: Paper is one of the most com
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1182, 2, 2, 1, '阅读下面短文，判断下列表述是否符合短文内容：
Passage:
Paper is one of the most common things in our daily life, and it is also one of the easiest to waste. Every year, millions of tons of paper are thrown away, yet most of it could be used again. Recycling one ton of paper saves about seventeen trees and a large amount of water and electricity. Schools can help in simple ways. Teachers can print on both sides of a sheet, students can use the back of old exercise books for draft work, and notice boards can be replaced by online messages. Recycling paper does not solve every environmental problem, but it is a habit that almost everyone can start today.

Recycling paper can solve all of our environmental problems.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '短文最后一句明确指出 Recycling paper does not solve every environmental problem，即回收纸张并不能解决所有环境问题，故该表述错误，应选 B。文中只说回收一吨纸可以节约约十七棵树以及大量的水和电，并未夸大它的作用。', '阅读理解', 1, 2);
-- [判断/中等/语法] 下列句子语法正确：The boy stood there, not knowing what to say.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1183, 2, 2, 2, '下列句子语法正确：The boy stood there, not knowing what to say.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, 'not knowing what to say 是现在分词短语作伴随状语，其逻辑主语 the boy 与 know 是主动关系，用现在分词正确；what to say 是「疑问词 + 不定式」结构，作 knowing 的宾语。若改成 not known，就变成了被动关系，与句意不符。', '语法', 1, 2);
-- [判断/中等/语法] 下列句子语法正确：I wish I can speak English as fluently as you.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1184, 2, 2, 2, '下列句子语法正确：I wish I can speak English as fluently as you.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, 'wish 后的宾语从句要用虚拟语气，表示与现在事实相反的愿望时，从句谓语用一般过去时，故 can 应改为 could，即 I wish I could speak English as fluently as you。句中 as fluently as 是同级比较，本身没有错误，问题只出在 can 的形式上。', '语法', 1, 2);
-- [判断/中等/语法] 下列句子语法正确：I don't know that he will come or not.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1185, 2, 2, 2, '下列句子语法正确：I don''t know that he will come or not.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '表示「是否」的宾语从句要用 whether 或 if 引导，且与 or not 直接连用时只能用 whether，故应改为 I don''t know whether he will come or not。that 引导宾语从句时只起连接作用，不表示「是否」，用在这里与句意矛盾。', '语法', 1, 2);
-- [判断/困难/语法] 下列句子语法正确：It is the third time that he has visited the Great 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1186, 2, 2, 3, '下列句子语法正确：It is the third time that he has visited the Great Wall.', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '在 It is/was the first (second, third...) time that... 句型中，主句用 is 时从句要用现在完成时，主句用 was 时从句则用过去完成时。本句主句是 It is，从句用 has visited，完全符合该规则，故句子正确。', '语法', 1, 2);
-- [填空/简单/语法] 用括号内单词的适当形式填空：Look! The children （ ） (play) football on the 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1187, 2, 3, 1, '用括号内单词的适当形式填空：Look! The children （　　） (play) football on the playground.', NULL, 'are playing', 5, 'Look! 提示动作正在发生，应使用现在进行时；主语 The children 是复数，be 动词用 are，故填 are playing。现在进行时的结构是 be + 现在分词，只填 play 或 played 都无法表示「正在踢球」。', '语法', 1, 2);
-- [填空/简单/语法] 在下列句子的空白处填入恰当的介词：The accident happened （ ） a rainy night.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1188, 2, 3, 1, '在下列句子的空白处填入恰当的介词：The accident happened （　　） a rainy night.', NULL, 'on', 5, '表示具体的某个早晨、下午或夜晚（尤其是带有形容词修饰时）要用介词 on，故 a rainy night 前用 on。in the morning/afternoon/evening 是泛指一天中的某一段时间，at night 表示「在夜里」这一笼统概念，都不能与 a rainy night 这样具体修饰的夜晚搭配。', '语法', 1, 2);
-- [填空/简单/语法] 用括号内单词的适当形式填空：Of the two plans, this one seems （ ） (good).
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1189, 2, 3, 1, '用括号内单词的适当形式填空：Of the two plans, this one seems （　　） (good).', NULL, 'better', 5, '句首的 Of the two plans 表明是在两者之间进行比较，故用比较级 better。不能填 best，因为最高级用于三者或三者以上的比较；也不能填原级 good，那样就没有比较的意思，与 of the two 的提示不符。', '语法', 1, 2);
-- [填空/简单/语法] 用括号内单词的适当形式填空：This dictionary is very （ ） (use) to English b
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1190, 2, 3, 1, '用括号内单词的适当形式填空：This dictionary is very （　　） (use) to English beginners.', NULL, 'useful', 5, 'be 动词后需要形容词作表语，故把 use 变为形容词 useful，构成 be useful to sb.（对某人有帮助）。若填 used，意思是「被使用的」，与句意不符；若填 using，则不能作表语。', '语法', 1, 2);
-- [填空/中等/语法] 用括号内单词的适当形式填空：A new hospital （ ） (build) in our town at the 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1191, 2, 3, 2, '用括号内单词的适当形式填空：A new hospital （　　） (build) in our town at the moment.', NULL, 'is being built', 5, '主语 A new hospital 与 build 之间是被动关系，at the moment 表示动作正在进行，故用现在进行时的被动语态 is being built。现在进行时被动的结构是 be being + 过去分词，学生常漏掉 being 而误写成 is built，那样就成了一般现在时的被动语态，无法体现「正在建造」。', '语法', 1, 2);
-- [填空/中等/语法] 用括号内单词的适当形式填空：It is no use （ ） (argue) with a person who nev
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1192, 2, 3, 2, '用括号内单词的适当形式填空：It is no use （　　） (argue) with a person who never listens.', NULL, 'arguing', 5, 'It is no use doing sth. 是固定句型，意为「做某事没有用」，其中 It 是形式主语，真正的主语是动名词短语，故填 arguing。此处不能用动词原形 argue，因为动名词短语才是句子的真正主语。', '语法', 1, 2);
-- [填空/中等/语法] 用括号内单词的适当形式填空：By the time you get this letter, I （ ） (leave)
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1193, 2, 3, 2, '用括号内单词的适当形式填空：By the time you get this letter, I （　　） (leave) for London.', NULL, 'will have left', 5, 'by the time 引导的时间状语从句中用一般现在时表示将来（you get），主句动作在这一将来时刻之前就已经完成，故用将来完成时 will have left。误填 will leave 无法体现「在你收到信之前就已离开」，误填 have left 则与将来的时间不符。', '语法', 1, 2);
-- [填空/困难/语法] 用括号内单词的适当形式填空：He talked about the city as if he （ ） (be) the
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1194, 2, 3, 3, '用括号内单词的适当形式填空：He talked about the city as if he （　　） (be) there many times.', NULL, 'had been', 5, 'as if 引导的从句表示与过去事实相反的虚拟情况时，谓语用过去完成时，故填 had been；句中的 talked 是过去时，说明「去过很多次」是与过去事实相反的假设。如果表示与现在事实相反，则用 were，如 He talks about the city as if he were there。', '语法', 1, 2);
-- [简答/简单/翻译] 把下列句子译成英语：他每天花两个小时练习说英语。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1195, 2, 4, 1, '把下列句子译成英语：他每天花两个小时练习说英语。', NULL, '参考译文：He spends two hours practising speaking English every day.（同义表达：It takes him two hours to practise speaking English every day.）', 16, '「花时间做某事」可用 spend some time (in) doing sth.，也可用 It takes sb. some time to do sth.。「练习做某事」用 practise doing sth.，故为 practising speaking English。注意 spend 后面不能接不定式，不能说 spend two hours to practise。', '翻译', 1, 2);
-- [简答/简单/翻译] 把下列句子译成汉语：He is said to have written three novels about vill
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1196, 2, 4, 1, '把下列句子译成汉语：He is said to have written three novels about village life.', NULL, '参考译文：据说他写了三部关于乡村生活的小说。', 16, 'sb. is said to have done sth. 是「据说某人做过某事」的固定结构，不定式的完成式 to have written 表示该动作发生在 is said 之前。翻译时可处理为「据说他写了……」，不必逐字译成「他被说已经写了……」，那样就成了中式英语。', '翻译', 1, 2);
-- [简答/中等/翻译] 把下列句子译成英语：无论发生什么，你都不应该放弃自己的梦想。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1197, 2, 4, 2, '把下列句子译成英语：无论发生什么，你都不应该放弃自己的梦想。', NULL, '参考译文：No matter what happens, you should never give up your dream.（同义表达：Whatever happens, you should never give up your dream.）', 16, '「无论……」可用 no matter what 或 whatever 引导让步状语从句，从句中用一般现在时表示将来，故为 No matter what happens。主句「不应该放弃」用 should never give up；注意 give up 后接名词或动名词，不能接不定式。', '翻译', 1, 2);
-- [简答/中等/语法] 把下列句子改为被动语态：They have already finished the new bridge.
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1198, 2, 4, 2, '把下列句子改为被动语态：They have already finished the new bridge.', NULL, '参考答案：The new bridge has already been finished (by them).', 16, '改为被动语态时，原句宾语 the new bridge 变为主语，谓语用「have/has been + 过去分词」；新主语是单数，故用 has been finished。原句中的 already 要放在 has 之后，by them 在不强调执行者时可以省略，省略后句子更自然。', '语法', 1, 2);
-- [简答/中等/语法] 请从语法角度简要说明下面两个句子在意思上的差别：① He must be at home. ② He can't be 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1199, 2, 4, 2, '请从语法角度简要说明下面两个句子在意思上的差别：① He must be at home. ② He can''t be at home.', NULL, '参考答案：① must be 表示肯定的推测，意为「他一定在家」；② can''t be 表示否定的推测，意为「他不可能在家」。两者都是对现在情况的推测，语气都很强。表示推测的 must 不能用于否定句，其否定形式用 can''t（或 couldn''t）。', 16, 'must 与 can 都可以表示推测，但分工不同：must 只用于肯定句，can 主要用于否定句和疑问句，因此「一定不在家」不能说 mustn''t be at home。如果是对过去情况的推测，则分别用 must have done 和 can''t have done，如 He must have gone home。答题时既要说明两句各自的意思，也要点出 must 不能用于否定推测这一规则。', '语法', 1, 2);
-- [简答/困难/写作] 请根据以下要点，用英语写一段约 80 词的短文，介绍你的英语学习方法。要点：1. 每天早晨朗读课文约二十分钟；2. 用单
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1200, 2, 4, 3, '请根据以下要点，用英语写一段约 80 词的短文，介绍你的英语学习方法。要点：1. 每天早晨朗读课文约二十分钟；2. 用单词卡片记单词，随时复习；3. 坚持用英语写日记；4. 每周看一部英文电影。', NULL, '写作要点与常用句式：开头点题，如 Everyone has his own way of learning English. Here is mine.；主体按四个要点展开，如 Every morning I read my English texts aloud for about twenty minutes. I also make word cards and review them in my spare time. Besides, I keep a diary in English, and I watch an English film every week.；结尾总结，如 These habits have helped me a lot, and I will keep them up.。常用表达：read aloud, make word cards, keep a diary, be helpful to, stick to。', 16, '本题为提纲式写作，篇幅约 80 词，四个要点必须齐全，缺一点就会被扣分。行文应以一般现在时为主，用 every morning、also、besides 等词语把四个要点连接成连贯的段落，避免把要点逐条罗列成四句互不相关的话。评分还关注基本句式与拼写是否正确，如 for about twenty minutes 中的 for 不能省略。', '写作', 1, 2);

-- 四川专升本 · 英语模拟卷（一）：25 题，总分 150，及格 90，120 分钟（题面固定，无抽题规则）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (3, '四川专升本 · 英语模拟卷（一）', '覆盖词汇与语法、阅读理解、翻译与写作五个题型，共 22 题，满分 150 分。', 2, 120, 150, 90, '模拟卷', '[{"questionId":1101,"score":5},{"questionId":1102,"score":5},{"questionId":1103,"score":5},{"questionId":1104,"score":5},{"questionId":1105,"score":5},{"questionId":1106,"score":5},{"questionId":1107,"score":5},{"questionId":1108,"score":5},{"questionId":1109,"score":5},{"questionId":1110,"score":5},{"questionId":1121,"score":5},{"questionId":1122,"score":5},{"questionId":1123,"score":5},{"questionId":1124,"score":5},{"questionId":1129,"score":3},{"questionId":1130,"score":3},{"questionId":1131,"score":3},{"questionId":1132,"score":3},{"questionId":1137,"score":5},{"questionId":1138,"score":5},{"questionId":1139,"score":5},{"questionId":1140,"score":5},{"questionId":1145,"score":16},{"questionId":1146,"score":16},{"questionId":1147,"score":16}]', NULL, NULL, 1, 2);

-- 四川专升本 · 英语模拟卷（二）：25 题，总分 150，及格 90，120 分钟（题面固定，无抽题规则）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (4, '四川专升本 · 英语模拟卷（二）', '与模拟卷（一）题目完全不重复，覆盖另一篇阅读与其余词汇语法、翻译写作题，满分 150 分。', 2, 120, 150, 90, '模拟卷', '[{"questionId":1111,"score":5},{"questionId":1112,"score":5},{"questionId":1113,"score":5},{"questionId":1114,"score":5},{"questionId":1115,"score":5},{"questionId":1116,"score":5},{"questionId":1117,"score":5},{"questionId":1118,"score":5},{"questionId":1119,"score":5},{"questionId":1120,"score":5},{"questionId":1125,"score":5},{"questionId":1126,"score":5},{"questionId":1127,"score":5},{"questionId":1128,"score":5},{"questionId":1133,"score":3},{"questionId":1134,"score":3},{"questionId":1135,"score":3},{"questionId":1136,"score":3},{"questionId":1141,"score":5},{"questionId":1142,"score":5},{"questionId":1143,"score":5},{"questionId":1144,"score":5},{"questionId":1148,"score":16},{"questionId":1149,"score":16},{"questionId":1150,"score":16}]', NULL, NULL, 1, 2);

-- 四川专升本 · 英语模拟卷（三）：25 题，总分 150，及格 90，120 分钟（题面固定，无抽题规则）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (9, '四川专升本 · 英语模拟卷（三）', '与前三张卷题目完全不重复，由新增题目组成的原创模拟卷，共 25 题、满分 150 分、及格 90 分，建议用时 120 分钟。', 2, 120, 150, 90, '模拟卷', '[{"questionId":1151,"score":5},{"questionId":1152,"score":5},{"questionId":1153,"score":5},{"questionId":1154,"score":5},{"questionId":1155,"score":5},{"questionId":1156,"score":5},{"questionId":1157,"score":5},{"questionId":1158,"score":5},{"questionId":1159,"score":5},{"questionId":1160,"score":5},{"questionId":1171,"score":5},{"questionId":1172,"score":5},{"questionId":1173,"score":5},{"questionId":1174,"score":5},{"questionId":1179,"score":3},{"questionId":1180,"score":3},{"questionId":1181,"score":3},{"questionId":1182,"score":3},{"questionId":1187,"score":5},{"questionId":1188,"score":5},{"questionId":1189,"score":5},{"questionId":1190,"score":5},{"questionId":1195,"score":16},{"questionId":1196,"score":16},{"questionId":1197,"score":16}]', NULL, NULL, 1, 2);

-- 四川专升本 · 英语模拟卷（四）：25 题，总分 150，及格 90，120 分钟（题面固定，无抽题规则）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (10, '四川专升本 · 英语模拟卷（四）', '与前三张卷题目完全不重复，由新增题目组成的原创模拟卷，共 25 题、满分 150 分、及格 90 分，建议用时 90 分钟。', 2, 120, 150, 90, '模拟卷', '[{"questionId":1161,"score":5},{"questionId":1162,"score":5},{"questionId":1163,"score":5},{"questionId":1164,"score":5},{"questionId":1165,"score":5},{"questionId":1166,"score":5},{"questionId":1167,"score":5},{"questionId":1168,"score":5},{"questionId":1169,"score":5},{"questionId":1170,"score":5},{"questionId":1175,"score":5},{"questionId":1176,"score":5},{"questionId":1177,"score":5},{"questionId":1178,"score":5},{"questionId":1183,"score":3},{"questionId":1184,"score":3},{"questionId":1185,"score":3},{"questionId":1186,"score":3},{"questionId":1191,"score":5},{"questionId":1192,"score":5},{"questionId":1193,"score":5},{"questionId":1194,"score":5},{"questionId":1198,"score":16},{"questionId":1199,"score":16},{"questionId":1200,"score":16}]', NULL, NULL, 1, 2);

-- 四川专升本 · 英语专项训练（语法）：24 题，总分 134，及格 81，60 分钟
--   知识点「语法」共 71 道题，本卷取 24 道（单选10 + 多选4 + 判断4 + 填空4 + 简答2）；卷内每题都属于该专题
--   显式清空 start_time/end_time：否则这个 id 上残留的时间窗会让「到点自动结束」任务把本卷立刻判成已结束（学生端看不到）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (102, '四川专升本 · 英语专项训练（语法）', '按知识点筛选的专项训练卷：只含「语法」专题的题目，覆盖时态、语态、从句、非谓语动词、虚拟语气与主谓一致。题面固定，不含抽题规则。', 2, 60, 134, 81, '专项训练', '[{"questionId":1101,"score":5},{"questionId":1102,"score":5},{"questionId":1103,"score":5},{"questionId":1104,"score":5},{"questionId":1105,"score":5},{"questionId":1106,"score":5},{"questionId":1112,"score":5},{"questionId":1113,"score":5},{"questionId":1115,"score":5},{"questionId":1152,"score":5},{"questionId":1122,"score":5},{"questionId":1123,"score":5},{"questionId":1124,"score":5},{"questionId":1125,"score":5},{"questionId":1129,"score":3},{"questionId":1130,"score":3},{"questionId":1131,"score":3},{"questionId":1132,"score":3},{"questionId":1137,"score":5},{"questionId":1138,"score":5},{"questionId":1139,"score":5},{"questionId":1140,"score":5},{"questionId":1198,"score":16},{"questionId":1199,"score":16}]', NULL, NULL, 1, 2);

-- 演示数据到此为止。注意：这些卷子是「题面固定」的（question_snapshot 已写死），
-- 不经过智能组卷；若要体验随机组卷，请在「智能组卷」页面按规则现场生成。

-- ----------------------------------------------------------------------
-- 题库 3：四川专升本 · 计算机基础题库（100 道题）
-- ----------------------------------------------------------------------
INSERT INTO question_bank (id, name, subject, description, created_by) VALUES
(3, '四川专升本 · 计算机基础题库', '计算机基础', '依据四川省普通高校专升本《计算机基础》考试大纲原创命题的题库，覆盖计算机组成、数制与编码、操作系统、办公软件、计算机网络、数据库、多媒体与信息安全、程序设计基础，共 100 题，可组成四张满分 150 分的模拟卷。', 2);

-- [单选/简单/计算机组成与工作原理] 冯·诺依曼体系结构计算机的核心设计思想是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1201, 3, 0, 1, '冯·诺依曼体系结构计算机的核心设计思想是（　　）', '[{"label":"A","content":"存储程序与程序控制"},{"label":"B","content":"并行处理"},{"label":"C","content":"指令流水线"},{"label":"D","content":"虚拟存储"}]', 'A', 5, '冯·诺依曼于 1945 年提出存储程序原理：程序和数据一样以二进制形式存放在存储器中，计算机自动按顺序取出指令并执行，这就是“存储程序与程序控制”。并行处理、指令流水线和虚拟存储都是后来发展起来的技术，并非该体系结构最初的核心思想，故 B、C、D 不选。', '计算机组成与工作原理', 1, 2);
-- [单选/简单/计算机组成与工作原理] 计算机的中央处理器（CPU）主要由（ ）组成。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1202, 3, 0, 1, '计算机的中央处理器（CPU）主要由（　　）组成。', '[{"label":"A","content":"运算器和控制器"},{"label":"B","content":"运算器和存储器"},{"label":"C","content":"控制器和内存"},{"label":"D","content":"寄存器和硬盘"}]', 'A', 5, 'CPU 由运算器（ALU）和控制器（CU）两大部分构成，寄存器组作为其内部支撑部件服务于这两部分。存储器（内存）和硬盘都是 CPU 之外的部件，故 B、C、D 均错。', '计算机组成与工作原理', 1, 2);
-- [单选/简单/数制与编码] 二进制数 1101B 转换为十进制数是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1203, 3, 0, 1, '二进制数 1101B 转换为十进制数是（　　）', '[{"label":"A","content":"11D"},{"label":"B","content":"13D"},{"label":"C","content":"15D"},{"label":"D","content":"26D"}]', 'B', 5, '按权展开：1×2^3+1×2^2+0×2^1+1×2^0=8+4+0+1=13。干扰项 A 的 11=8+2+1，漏掉了 2^2 位的 1；C 的 15=8+4+2+1，误把末位 1 当成权值 2；D 的 26=13×2，结果又多乘了 2。', '数制与编码', 1, 2);
-- [单选/简单/数制与编码] 在计算机中，1GB 等于（ ）MB。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1204, 3, 0, 1, '在计算机中，1GB 等于（　　）MB。', '[{"label":"A","content":"1000"},{"label":"B","content":"1024"},{"label":"C","content":"512"},{"label":"D","content":"2048"}]', 'B', 5, '计算机内部采用二进制，存储容量单位以 2 的幂为进率，1GB=$2^{10}$MB=1024MB。干扰项 A 的 1000 是十进制进率，不符合计算机的度量习惯；C 与 D 分别与正确值相差一倍和一倍，数值不符。', '数制与编码', 1, 2);
-- [单选/简单/操作系统] 关于进程和线程，下列说法正确的是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1205, 3, 0, 1, '关于进程和线程，下列说法正确的是（　　）', '[{"label":"A","content":"一个进程只能包含一个线程"},{"label":"B","content":"线程是系统进行资源分配的基本单位"},{"label":"C","content":"同一进程内的多个线程共享该进程的地址空间"},{"label":"D","content":"线程之间切换的开销比进程之间切换的开销更大"}]', 'C', 5, '线程是处理机调度（CPU 分配）的基本单位，进程才是资源分配的基本单位，故 B 错；同一进程内的线程共享该进程的地址空间和已打开文件等资源，故 C 对。一个进程可以包含多个线程，A 错；同一进程内线程切换不涉及地址空间切换，开销小于进程切换，D 说反了。', '操作系统', 1, 2);
-- [单选/简单/办公软件基础] 在 Word 中，要使文档中所有一级标题的字体、字号统一改变，最规范高效的做法是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1206, 3, 0, 1, '在 Word 中，要使文档中所有一级标题的字体、字号统一改变，最规范高效的做法是（　　）', '[{"label":"A","content":"逐个选中标题手工修改格式"},{"label":"B","content":"用格式刷逐个刷过每个标题"},{"label":"C","content":"用“查找和替换”替换字体"},{"label":"D","content":"修改“标题 1”样式定义"}]', 'D', 5, '样式是若干格式的集合，修改样式定义后，所有应用该样式的段落会自动更新，一次修改即可全篇生效，这正是样式的意义。A、B 效率低且容易漏改；“查找和替换”只能改变字符格式，无法整体统一段落格式，C 也不规范。', '办公软件基础', 1, 2);
-- [单选/简单/计算机网络] 下列 IP 地址中，属于私有地址的是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1207, 3, 0, 1, '下列 IP 地址中，属于私有地址的是（　　）', '[{"label":"A","content":"8.8.8.8"},{"label":"B","content":"192.168.1.10"},{"label":"C","content":"202.115.1.1"},{"label":"D","content":"114.114.114.114"}]', 'B', 5, '私有地址段为 10.0.0.0/8、172.16.0.0/12 和 192.168.0.0/16，192.168.1.10 属于第三段，只能在内部网络使用、不能直接在 Internet 上路由。A 是公共 DNS 服务地址，C、D 均为公网可路由地址。', '计算机网络', 1, 2);
-- [单选/简单/数据库基础] 在关系数据库中，主键的主要作用是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1208, 3, 0, 1, '在关系数据库中，主键的主要作用是（　　）', '[{"label":"A","content":"唯一标识表中的每一条记录"},{"label":"B","content":"加快所有类型的查询速度"},{"label":"C","content":"存储表与表之间的联系"},{"label":"D","content":"限定字段的取值范围"}]', 'A', 5, '主键的值在表中唯一且不允许为空，用于唯一标识一条记录，并保证实体完整性。加快查询是索引的作用，主键虽常自动建索引但不是其定义目的，B 错；表间联系由外键建立，C 错；取值范围由字段的数据类型和域约束描述，D 错。', '数据库基础', 1, 2);
-- [单选/简单/计算机组成与工作原理] 在程序流程图中，用于表示“判断条件”的图形符号是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1209, 3, 0, 1, '在程序流程图中，用于表示“判断条件”的图形符号是（　　）', '[{"label":"A","content":"矩形"},{"label":"B","content":"菱形"},{"label":"C","content":"平行四边形"},{"label":"D","content":"圆形"}]', 'B', 5, '流程图的约定符号为：矩形表示处理（赋值、计算等），菱形表示判断（分支），平行四边形表示输入/输出，圆形或圆角框表示起止。故表示判断条件的是菱形，A、C、D 分别对应处理、输入输出和起止。', '计算机组成与工作原理', 1, 2);
-- [单选/简单/多媒体与信息安全] 下列不属于计算机病毒基本特征的是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1210, 3, 0, 1, '下列不属于计算机病毒基本特征的是（　　）', '[{"label":"A","content":"传染性"},{"label":"B","content":"潜伏性"},{"label":"C","content":"破坏性"},{"label":"D","content":"可读性"}]', 'D', 5, '计算机病毒具有传染性、潜伏性、破坏性、隐蔽性和可触发性。可读性不是病毒的特征，恰恰相反，病毒常采用加密、变形、依附宿主程序等手段隐藏自身，故 D 符合题意。', '多媒体与信息安全', 1, 2);
-- [单选/中等/计算机组成与工作原理] 计算机中地址总线的宽度决定了（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1211, 3, 0, 2, '计算机中地址总线的宽度决定了（　　）', '[{"label":"A","content":"CPU 可直接寻址的内存空间大小"},{"label":"B","content":"一次能够传输的数据位数"},{"label":"C","content":"总线的数据传输速率"},{"label":"D","content":"外存储器的容量"}]', 'A', 5, '地址总线用于传送地址信息，n 位地址总线可以区分 $2^{n}$ 个存储单元，因此其宽度决定了 CPU 能直接寻址的最大内存空间。一次传输的数据位数由数据总线宽度决定，B 错；传输速率还取决于时钟频率和总线宽度等多种因素，C 不准确；外存容量由外存设备本身决定，与地址总线无关，D 错。', '计算机组成与工作原理', 1, 2);
-- [单选/中等/数制与编码] 8 位二进制补码 11111111B 所表示的十进制真值是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1212, 3, 0, 2, '8 位二进制补码 11111111B 所表示的十进制真值是（　　）', '[{"label":"A","content":"255"},{"label":"B","content":"-1"},{"label":"C","content":"-127"},{"label":"D","content":"128"}]', 'B', 5, '最高位为 1 表示负数，按补码各位的权值求和：-128+64+32+16+8+4+2+1=-1。也可先“取反加一”得到绝对值为 1，真值即 -1。干扰项 A 的 255 是把它当作无符号数的结果；C 的 -127 对应原码 11111111B 的数值；D 的 128 已超出 8 位补码的正数范围。', '数制与编码', 1, 2);
-- [单选/中等/数制与编码] 已知大写英文字母 A 的 ASCII 码值为 65，则字母 D 的 ASCII 码值是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1213, 3, 0, 2, '已知大写英文字母 A 的 ASCII 码值为 65，则字母 D 的 ASCII 码值是（　　）', '[{"label":"A","content":"66"},{"label":"B","content":"67"},{"label":"C","content":"68"},{"label":"D","content":"69"}]', 'C', 5, 'ASCII 码表中大写字母 A 到 Z 的编码是连续递增的，A 为 65，则 D=A+3=68。干扰项 66、67、69 分别是 B、C、E 的码值，属于常见的“数错字母序号”错误。', '数制与编码', 1, 2);
-- [单选/中等/操作系统] 产生死锁的四个必要条件中，不包括（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1214, 3, 0, 2, '产生死锁的四个必要条件中，不包括（　　）', '[{"label":"A","content":"互斥条件"},{"label":"B","content":"请求并保持条件"},{"label":"C","content":"不可剥夺条件"},{"label":"D","content":"优先级反转条件"}]', 'D', 5, '死锁的四个必要条件是互斥条件、请求并保持条件、不可剥夺条件和循环等待条件，四者同时成立才可能发生死锁。优先级反转是实时系统中高优先级进程被低优先级进程间接阻塞的现象，属于调度问题，不是死锁的必要条件。', '操作系统', 1, 2);
-- [单选/中等/办公软件基础] 在 Excel 中，把公式中的单元格引用设置为绝对引用后，将该公式复制或填充到其他单元格，则（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1215, 3, 0, 2, '在 Excel 中，把公式中的单元格引用设置为绝对引用后，将该公式复制或填充到其他单元格，则（　　）', '[{"label":"A","content":"该引用随公式所在位置作相对偏移"},{"label":"B","content":"该引用始终指向原来指定的那个单元格"},{"label":"C","content":"该单元格中的数值被固定为常量，不再随源单元格变化"},{"label":"D","content":"该引用会自动转换为同一列下一行的单元格"}]', 'B', 5, '绝对引用通过锁定行号和列标（书写时在列标和行号前各加一个美元符号）使引用不随公式位置发生偏移，复制或填充到任何单元格后仍指向原来指定的那个单元格，故 B 正确。A 描述的是相对引用的行为；C 混淆了“引用位置固定”和“数值变成常量”，绝对引用得到的结果仍会随源单元格数值的改变而更新；D 描述的是相对引用向下填充时的偏移效果。', '办公软件基础', 1, 2);
-- [单选/中等/计算机网络] 在 OSI 参考模型中，负责端到端可靠传输并进行流量控制的层次是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1216, 3, 0, 2, '在 OSI 参考模型中，负责端到端可靠传输并进行流量控制的层次是（　　）', '[{"label":"A","content":"网络层"},{"label":"B","content":"数据链路层"},{"label":"C","content":"传输层"},{"label":"D","content":"会话层"}]', 'C', 5, '传输层为端到端（进程到进程）提供数据传输服务，TCP 就工作在这一层，通过确认、重传、滑动窗口等机制实现可靠传输与流量控制。网络层负责分组的寻址与路由转发，A 错；数据链路层只负责相邻结点之间的成帧与差错检测，B 错；会话层负责会话的建立、管理与同步，D 错。', '计算机网络', 1, 2);
-- [单选/中等/数据库基础] 要从学生表 student 中查询所有年龄大于 18 的学生的姓名和年龄，正确的 SQL 语句是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1217, 3, 0, 2, '要从学生表 student 中查询所有年龄大于 18 的学生的姓名和年龄，正确的 SQL 语句是（　　）', '[{"label":"A","content":"SELECT 姓名, 年龄 FROM student WHERE 年龄 > 18"},{"label":"B","content":"SELECT * FROM student WHEN 年龄 > 18"},{"label":"C","content":"SELECT 姓名, 年龄 FROM student HAVING 年龄 > 18"},{"label":"D","content":"SELECT 姓名, 年龄 WHERE 年龄 > 18 FROM student"}]', 'A', 5, 'SQL 查询的基本结构是 SELECT 指定列、FROM 指定表、WHERE 对行进行条件筛选，语序固定，故 A 正确。WHEN 不是 SQL 的查询子句，B 错；HAVING 用于对分组统计后的结果进行筛选，通常与 GROUP BY 配合，直接代替 WHERE 使用不规范，C 错；D 把 WHERE 写在 FROM 之前，语序错误。', '数据库基础', 1, 2);
-- [单选/中等/多媒体与信息安全] 关于数字签名，下列说法正确的是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1218, 3, 0, 2, '关于数字签名，下列说法正确的是（　　）', '[{"label":"A","content":"数字签名使用接收方的公钥对消息加密生成"},{"label":"B","content":"数字签名可以验证消息的完整性和发送方的身份"},{"label":"C","content":"数字签名对消息内容加密，可以保证消息的机密性"},{"label":"D","content":"数字签名不需要哈希算法参与"}]', 'B', 5, '数字签名的典型做法是发送方先用哈希算法对消息生成摘要，再用自己的私钥对摘要加密，接收方用发送方的公钥解密验证，从而确认来源真实并检测内容是否被篡改，故 B 对。A 把收、发双方公钥的用途弄反了；签名不隐藏明文，因此不提供机密性，C 错；摘要由哈希算法产生，签名离不开哈希，D 错。', '多媒体与信息安全', 1, 2);
-- [单选/困难/数制与编码] 用 8 位二进制补码表示整数，能够表示的数值范围是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1219, 3, 0, 3, '用 8 位二进制补码表示整数，能够表示的数值范围是（　　）', '[{"label":"A","content":"-127 ~ +127"},{"label":"B","content":"-128 ~ +127"},{"label":"C","content":"-128 ~ +128"},{"label":"D","content":"0 ~ 255"}]', 'B', 5, '8 位补码中 0 只有一种表示（全 0），因此可多表示一个负数：-128 的补码为 10000000B，最大正数为 01111111B 即 +127，范围是 -128 ~ +127。A 是 8 位原码和反码的对称范围，它们有两个零；C 的 -128 ~ +128 共 257 个数，超出 8 位能表示的 256 种状态；D 是无符号数的范围。', '数制与编码', 1, 2);
-- [单选/困难/计算机网络] 某主机 IP 地址为 192.168.10.100，子网掩码为 255.255.255.192，则该子网中可分配给主机的
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1220, 3, 0, 3, '某主机 IP 地址为 192.168.10.100，子网掩码为 255.255.255.192，则该子网中可分配给主机的 IP 地址数为（　　）', '[{"label":"A","content":"62"},{"label":"B","content":"64"},{"label":"C","content":"126"},{"label":"D","content":"30"}]', 'A', 5, '掩码最后一字节 192 写成二进制是 11111100B，说明网络号占 26 位、主机号占 6 位，共 $2^{6}=64$ 个地址。其中全 0 的地址表示本子网、全 1 的地址是广播地址，都不能分配给主机，故可用地址为 64-2=62 个。B 没有扣除两个特殊地址；C 的 126 对应 /25 掩码；D 的 30 对应 /27 掩码。', '计算机网络', 1, 2);
-- [多选/简单/计算机组成与工作原理] 下列设备中，属于输入设备的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1221, 3, 1, 1, '下列设备中，属于输入设备的有（　　）', '[{"label":"A","content":"键盘"},{"label":"B","content":"鼠标"},{"label":"C","content":"打印机"},{"label":"D","content":"扫描仪"}]', 'ABD', 5, '键盘、鼠标和扫描仪都把外界信息送入计算机内部，属于输入设备。打印机把计算机的处理结果输出到纸张，属于输出设备，故 C 不选。判断输入还是输出，关键看数据流动的方向。', '计算机组成与工作原理', 1, 2);
-- [多选/简单/数制与编码] 下列各数与十六进制数 2FH 数值相等的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1222, 3, 1, 1, '下列各数与十六进制数 2FH 数值相等的有（　　）', '[{"label":"A","content":"101111B"},{"label":"B","content":"47D"},{"label":"C","content":"57O"},{"label":"D","content":"01011111B"}]', 'ABC', 5, '2FH=2×16+15=47。101111B=32+8+4+2+1=47，A 对；47D 本身即 47，B 对；57O=5×8+7=47，C 对；01011111B=64+16+8+4+2+1=95，与 47 不等，D 错。二进制与十六进制可按“四位一组”快速互化，2FH 即 0010 1111B。', '数制与编码', 1, 2);
-- [多选/简单/办公软件基础] 在 Excel 中，关于常用函数与区域引用，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1223, 3, 1, 1, '在 Excel 中，关于常用函数与区域引用，下列说法正确的有（　　）', '[{"label":"A","content":"SUM(A1:A10) 表示对 A1 到 A10 区域求和"},{"label":"B","content":"AVERAGE(A1:A10) 计算该区域数据的平均值"},{"label":"C","content":"COUNT(A1:A10) 统计该区域中数值型数据的个数"},{"label":"D","content":"MAX(A1:A10) 返回该区域中的最小值"}]', 'ABC', 5, 'SUM 求和、AVERAGE 求算术平均值、COUNT 统计区域中数值型单元格的个数，三项都正确；冒号是区域运算符，A1:A10 表示连续的矩形区域。MAX 返回区域中的最大值，求最小值应使用 MIN，故 D 错。', '办公软件基础', 1, 2);
-- [多选/中等/计算机组成与工作原理] 关于计算机的存储器，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1224, 3, 1, 2, '关于计算机的存储器，下列说法正确的有（　　）', '[{"label":"A","content":"内存（RAM）断电后数据丢失，属于易失性存储器"},{"label":"B","content":"外存（如硬盘、U 盘）断电后数据仍能保存"},{"label":"C","content":"Cache 的存取速度比内存快，容量通常比内存小"},{"label":"D","content":"内存容量一般比硬盘大，所以程序都应保存在内存中"}]', 'ABC', 5, 'RAM 依靠电容或触发器保存信息，断电即失，属于易失性存储器，A 对；硬盘、U 盘等外存采用磁或闪存介质，断电后数据不丢失，B 对；Cache 位于 CPU 与内存之间，速度最快而容量最小，C 对。内存容量远小于硬盘，且断电丢数据，不能作为长期存储，D 错。', '计算机组成与工作原理', 1, 2);
-- [多选/中等/操作系统] 关于操作系统的进程调度，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1225, 3, 1, 2, '关于操作系统的进程调度，下列说法正确的有（　　）', '[{"label":"A","content":"时间片轮转调度适合分时系统，能保证较好的响应时间"},{"label":"B","content":"先来先服务调度对短作业不利，平均周转时间可能较长"},{"label":"C","content":"优先级调度中若高优先级进程不断到来，低优先级进程可能长期得不到运行"},{"label":"D","content":"进程调度只能在进程运行结束后才可能发生"}]', 'ABC', 5, '时间片轮转让就绪进程轮流获得 CPU，适合分时交互场景，A 对；先来先服务（FCFS）对长作业有利、对短作业不利，可能出现“护航效应”使平均周转时间变长，B 对；静态优先级调度可能出现低优先级进程长期得不到服务的饥饿现象，C 对。进程可在时间片用完、被更高优先级进程抢占、等待 I/O 等时机被调度，并非只在运行结束时，D 错。', '操作系统', 1, 2);
-- [多选/中等/计算机网络] 关于常见网络协议与其默认端口的对应关系，下列正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1226, 3, 1, 2, '关于常见网络协议与其默认端口的对应关系，下列正确的有（　　）', '[{"label":"A","content":"HTTP 使用 80 端口"},{"label":"B","content":"HTTPS 使用 443 端口"},{"label":"C","content":"FTP 使用 21 端口"},{"label":"D","content":"SMTP 使用 110 端口"}]', 'ABC', 5, 'HTTP 默认端口为 80，HTTPS 默认端口为 443，FTP 的控制连接默认使用 21 端口，三项正确。SMTP 用于发送邮件，默认端口是 25；110 是 POP3 收取邮件的端口，故 D 错。记忆时注意“发件 25、收件 110”这一对易混端口。', '计算机网络', 1, 2);
-- [多选/中等/数据库基础] 关于 SQL 中的数据操作语句，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1227, 3, 1, 2, '关于 SQL 中的数据操作语句，下列说法正确的有（　　）', '[{"label":"A","content":"INSERT 语句用于向表中添加新的记录"},{"label":"B","content":"UPDATE 语句用于修改表中已存在的记录"},{"label":"C","content":"DELETE 语句用于删除表中的记录"},{"label":"D","content":"DROP TABLE 与 DELETE 的作用完全相同"}]', 'ABC', 5, 'INSERT 新增记录、UPDATE 修改记录、DELETE 删除记录，分别对应数据的增、改、删操作，A、B、C 均对。DROP TABLE 删除的是整张表，包括表结构和全部数据；DELETE 只删除表中的若干行，表结构仍然保留，两者作用不同，D 错。', '数据库基础', 1, 2);
-- [多选/困难/多媒体与信息安全] 关于计算机安全防护措施，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1228, 3, 1, 3, '关于计算机安全防护措施，下列说法正确的有（　　）', '[{"label":"A","content":"防火墙可依据预设规则对进出网络的数据包进行过滤"},{"label":"B","content":"对重要数据定期备份并异地存放，可降低数据丢失风险"},{"label":"C","content":"使用足够复杂的密码并定期更换，有助于防止口令被暴力破解"},{"label":"D","content":"只要安装了杀毒软件，就不必再安装系统补丁，也不必备份数据"}]', 'ABC', 5, '防火墙部署在网络边界，按规则允许或拒绝数据包，A 对；备份是应对误删、硬件故障和勒索软件的可靠手段，异地存放还能抵御区域性灾害，B 对；增大口令空间并缩短使用周期可显著提高暴力破解成本，C 对。杀毒软件依赖病毒特征库，无法覆盖系统漏洞和所有新型攻击，补丁更新与数据备份仍然必不可少，D 错。', '多媒体与信息安全', 1, 2);
-- [判断/简单/数制与编码] 在 ASCII 码表中，大写英文字母的编码值小于对应小写英文字母的编码值。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1229, 3, 2, 1, '在 ASCII 码表中，大写英文字母的编码值小于对应小写英文字母的编码值。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, 'ASCII 码表中大写字母 A 到 Z 的码值为 65 到 90，小写字母 a 到 z 的码值为 97 到 122，同一个字母的小写编码比大写大 32，故命题正确。这一规律也解释了按 ASCII 码值比较字符串时大写字母总是排在前面。', '数制与编码', 1, 2);
-- [判断/简单/计算机组成与工作原理] 计算机的运算器和存储器合称为中央处理器（CPU）。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1230, 3, 2, 1, '计算机的运算器和存储器合称为中央处理器（CPU）。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '中央处理器由运算器（负责算术运算和逻辑运算）和控制器（负责取指、译码并发出控制信号）两部分构成，存储器是 CPU 之外的独立部件，把运算器与存储器合称 CPU 是错误的。若改为“运算器和控制器合称为中央处理器”，命题才成立。', '计算机组成与工作原理', 1, 2);
-- [判断/简单/操作系统] 在采用虚拟存储技术的操作系统中，程序运行时并不需要把它的全部代码和数据一次装入内存。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1231, 3, 2, 1, '在采用虚拟存储技术的操作系统中，程序运行时并不需要把它的全部代码和数据一次装入内存。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '虚拟存储技术以程序执行的局部性原理为基础，只把当前要用的页或段调入内存，其余部分留在外存；运行中按需调入，访问到不在内存的页时再通过缺页中断换入，因此不必一次全部装入，命题正确。这也使得小内存可以运行大程序。', '操作系统', 1, 2);
-- [判断/简单/计算机网络] IP 地址 255.255.255.255 可以分配给网络中的某一台主机使用。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1232, 3, 2, 1, 'IP 地址 255.255.255.255 可以分配给网络中的某一台主机使用。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '255.255.255.255 是受限广播地址，用于向本网段内的所有主机发送数据，不能分配给任何一台主机；能够分配给主机的是普通的单播地址，例如 192.168.1.10。另外，主机号全 0 的地址表示本网络，同样不能分配给主机使用，故命题错误。', '计算机网络', 1, 2);
-- [判断/中等/数制与编码] 计算机中的 1KB 等于 1000 个字节。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1233, 3, 2, 2, '计算机中的 1KB 等于 1000 个字节。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '计算机以二进制为计数基础，存储容量单位按 2 的幂进位，1KB=$2^{10}$B=1024B，而不是 1000B。1000 是十进制近似说法，在硬盘厂商标注容量时可能见到，但严格意义上 1KB 等于 1024 字节，故命题错误。同理 1MB=1024KB、1GB=1024MB。', '数制与编码', 1, 2);
-- [判断/中等/办公软件基础] 在 Word 中，页眉和页脚的内容只能在屏幕上看到，打印时不会输出。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1234, 3, 2, 2, '在 Word 中，页眉和页脚的内容只能在屏幕上看到，打印时不会输出。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '页眉和页脚在页面视图和打印预览中都会显示，并且默认随文档一起打印输出，正因如此才常用于放置页码、单位名称、文档标题等各页重复的信息。命题说打印时不输出，与事实相反，故错误。', '办公软件基础', 1, 2);
-- [判断/中等/程序设计基础] 结构化程序设计的三种基本控制结构是顺序结构、选择结构和循环结构。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1235, 3, 2, 2, '结构化程序设计的三种基本控制结构是顺序结构、选择结构和循环结构。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '顺序结构按语句先后依次执行，选择结构根据条件决定执行哪一分支，循环结构在条件成立时重复执行一段语句。理论上任何算法都可以只用这三种结构表达，因此它们是结构化程序设计的基本控制结构，命题正确。', '程序设计基础', 1, 2);
-- [判断/困难/操作系统] 同一进程内的多个线程共享该进程的地址空间，因此某个线程的非法内存访问不会影响同进程的其他线程。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1236, 3, 2, 3, '同一进程内的多个线程共享该进程的地址空间，因此某个线程的非法内存访问不会影响同进程的其他线程。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '线程与进程的重要区别之一就是线程之间没有独立的地址空间：同一进程的线程共享代码段、数据段和堆区。因此某个线程出现越界访问、野指针等非法操作时，会引发整个进程异常终止，同进程的其他线程一并受影响。线程切换开销小的代价正是隔离性差，故命题错误。', '操作系统', 1, 2);
-- [填空/简单/数制与编码] 二进制数 11010B 转换为十进制数的结果是 ______。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1237, 3, 3, 1, '二进制数 11010B 转换为十进制数的结果是 ______。', NULL, '26', 5, '按权展开：1×2^4+1×2^3+0×2^2+1×2^1+0×2^0=16+8+0+2+0=26，故结果为 26（也可写作 26D）。注意 0 所在的位对结果没有贡献，但位权仍要占位，不能把 11010 少算一位。', '数制与编码', 1, 2);
-- [填空/简单/计算机组成与工作原理] 冯·诺依曼结构计算机的硬件系统由运算器、控制器、存储器、输入设备和 ______ 五个基本部分组成。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1238, 3, 3, 1, '冯·诺依曼结构计算机的硬件系统由运算器、控制器、存储器、输入设备和 ______ 五个基本部分组成。', NULL, '输出设备', 5, '冯·诺依曼结构计算机硬件由运算器、控制器、存储器、输入设备和输出设备五大部件构成。输入设备负责把程序和数据送入计算机，输出设备负责把处理结果以人们需要的形式送出，二者方向相反，缺一不可。', '计算机组成与工作原理', 1, 2);
-- [填空/中等/数制与编码] 已知大写英文字母 A 的 ASCII 码值为 65，则数字字符 0 的 ASCII 码值为 ______。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1239, 3, 3, 2, '已知大写英文字母 A 的 ASCII 码值为 65，则数字字符 0 的 ASCII 码值为 ______。', NULL, '48', 5, 'ASCII 码表中数字字符 0 到 9 的编码是 48 到 57，连续递增且整体小于大写字母的 65，故字符 0 的 ASCII 码值为 48。常见错误是误记为 0 或 32。', '数制与编码', 1, 2);
-- [填空/中等/计算机网络] 在 Internet 中，负责把域名解析为对应 IP 地址的服务（系统）称为 ______。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1240, 3, 3, 2, '在 Internet 中，负责把域名解析为对应 IP 地址的服务（系统）称为 ______。', NULL, 'DNS（域名系统）', 5, 'DNS 是 Domain Name System 的缩写，即域名系统。它把便于人记忆的域名转换为机器通信所需的 IP 地址：用户或应用程序把域名交给 DNS 服务器，DNS 通过递归或迭代查询返回对应的 IP 地址，之后通信仍使用 IP 地址进行。', '计算机网络', 1, 2);
-- [填空/中等/操作系统] 在操作系统的存储管理中，把内存空间划分为大小相等的物理块、同时把进程的逻辑地址空间划分为同样大小的页，这种管理方式称为 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1241, 3, 3, 2, '在操作系统的存储管理中，把内存空间划分为大小相等的物理块、同时把进程的逻辑地址空间划分为同样大小的页，这种管理方式称为 ______ 存储管理。', NULL, '分页', 5, '分页存储管理把内存划分为等长的物理块（又称页框），把进程的逻辑地址空间划分为等长的页，页与块大小相同，通过页表建立“页号到块号”的映射。由于分配单位大小一致，分页能有效减少外部碎片。若改成按逻辑意义划分可变大小的段，则是分段存储管理。', '操作系统', 1, 2);
-- [填空/中等/办公软件基础] 在 Excel 中，若要计算 A1 到 A10 这十个单元格中数值的总和，应在单元格中输入的公式是 ______。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1242, 3, 3, 2, '在 Excel 中，若要计算 A1 到 A10 这十个单元格中数值的总和，应在单元格中输入的公式是 ______。', NULL, '=SUM(A1:A10)', 5, 'Excel 公式必须以等号开头；SUM 是求和函数；A1:A10 中用冒号连接起止单元格，表示一个连续的矩形区域。因此正确公式为 =SUM(A1:A10)。写成 SUM(A1+A2+…+A10) 虽然结果可能相同，但不合规范且易错。', '办公软件基础', 1, 2);
-- [填空/中等/数据库基础] 在 SQL 中，查询 student 表中全部学生的姓名（列名为 name）应使用的语句是 ______。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1243, 3, 3, 2, '在 SQL 中，查询 student 表中全部学生的姓名（列名为 name）应使用的语句是 ______。', NULL, 'SELECT name FROM student', 5, 'SELECT 子句指明要查询的列 name，FROM 子句指明数据来源表 student；由于没有任何筛选条件，查询会返回表中的全部行。语句写成 SELECT name FROM student; 也可以，末尾的分号在多数数据库中是可选的分隔符。若要求姓名不重复，可加 DISTINCT。', '数据库基础', 1, 2);
-- [填空/困难/程序设计基础] 在一个长度为 n 的有序数组中采用二分查找查找某个元素，其平均时间复杂度用大 O 记号表示为 ______。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1244, 3, 3, 3, '在一个长度为 n 的有序数组中采用二分查找查找某个元素，其平均时间复杂度用大 O 记号表示为 ______。', NULL, 'O(log n)', 5, '二分查找每比较一次就把查找区间缩小一半，最多比较的次数与以 2 为底的 n 的对数同阶，因此时间复杂度为 O(log n)，也可写作 O(log2 n)。它比顺序查找的 O(n) 高效得多，但前提是数据必须有序且能随机访问（顺序存储）。', '程序设计基础', 1, 2);
-- [简答/简单/数制与编码] 简述把十进制整数转换为二进制整数的基本方法，并写出十进制数 45 转换后的二进制结果。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1245, 3, 4, 1, '简述把十进制整数转换为二进制整数的基本方法，并写出十进制数 45 转换后的二进制结果。', NULL, '方法是“除 2 取余，逆序排列”：用 2 反复去除该十进制数，每次记录余数，直到商为 0 为止，最后把各次得到的余数从后往前（逆序）依次排列，即得对应的二进制数。以 45 为例：45÷2=22 余 1，22÷2=11 余 0，11÷2=5 余 1，5÷2=2 余 1，2÷2=1 余 0，1÷2=0 余 1；余数依次为 1、0、1、1、0、1，逆序排列得 101101，所以 45D=101101B。', 16, '本题的评分要点有两处：一是说明“除 2 取余、逆序排列”这一方法，二是给出正确的转换结果 101101B。验算：101101B=32+8+4+1=45，结果正确。常见错误有二：一是把余数按求出的先后顺序正着写而忘记逆序（本题余数序列 1、0、1、1、0、1 恰好左右对称，逆序后不变，属于巧合，换一个数就会出错）；二是漏记最后一次商为 0 时得到的余数 1，从而少一位。', '数制与编码', 1, 2);
-- [简答/中等/操作系统] 简述进程与线程的主要区别。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1246, 3, 4, 2, '简述进程与线程的主要区别。', NULL, '①从地位看：进程是程序的一次执行过程，是系统进行资源分配的基本单位；线程是进程内的一个执行单元，是处理机调度（CPU 分配）的基本单位。②从包含关系看：一个进程至少包含一个线程，线程不能脱离进程独立存在。③从资源与地址空间看：同一进程内的多个线程共享该进程的地址空间以及已打开的文件等资源，而各个进程拥有各自独立的地址空间。④从开销看：线程的创建、撤销和切换开销都小于进程，并发效率更高；但由于线程之间缺乏地址空间隔离，一个线程出错可能导致整个进程崩溃。⑤从通信看：进程之间通信需要借助操作系统提供的机制（如管道、共享内存、消息传递等），而同进程内的线程可以直接通过共享变量通信。', 16, '答题主线是“进程是资源分配的基本单位、线程是处理机调度的基本单位”，再从包含关系、地址空间是否共享、切换开销大小、通信方式与隔离性几个方面展开即可得满分。只写“线程比进程小、比进程快”过于笼统，缺少要点支撑；把两者地位说反（如称进程是调度单位）属于原则性错误。', '操作系统', 1, 2);
-- [简答/中等/计算机网络] 写出 OSI 参考模型自下而上的七个层次名称，并说明 TCP/IP 协议中的 IP 与 TCP 分别工作在哪一层。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1247, 3, 4, 2, '写出 OSI 参考模型自下而上的七个层次名称，并说明 TCP/IP 协议中的 IP 与 TCP 分别工作在哪一层。', NULL, 'OSI 参考模型自下而上依次为：物理层、数据链路层、网络层、传输层、会话层、表示层、应用层。IP 工作在网络层，负责为分组寻址并选择合适的路径进行转发；TCP 工作在传输层，为应用进程提供面向连接的可靠传输服务。', 16, '七层的记忆口诀为“物、链、网、传、会、表、应”，顺序不能颠倒，尤其是“会话层在传输层之上、表示层在会话层之上”容易被写反。第二问要抓住分工：IP 只提供无连接、尽最大努力交付的服务，可靠性（确认、重传、流量控制、拥塞控制）由传输层的 TCP 承担，把 IP 说成可靠协议属于概念性错误。', '计算机网络', 1, 2);
-- [简答/困难/数据库基础] 简述关系数据库中主键与外键的含义和作用，并说明两者的区别。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1248, 3, 4, 3, '简述关系数据库中主键与外键的含义和作用，并说明两者的区别。', NULL, '主键是表中能够唯一标识一条记录的属性或属性组，它的值必须唯一且不允许为空；一个表最多只能有一个主键。主键用于保证实体的完整性，并常被其他表引用来建立表间联系。外键是本表中引用另一个表（或本表）主键的字段，它的取值必须是被引用表中已经存在的主键值，或者取空值；一个表可以有多个外键。外键用于保证参照完整性，维护并体现表与表之间的关联。两者的区别主要有三点：一是作用不同，主键唯一标识本表内的记录，外键建立表与表之间的联系；二是取值约束不同，主键不允许为空且必须唯一，外键允许为空、且其值来自被引用表的主键；三是数量不同，一个表只能有一个主键，但可以有多个外键。', 16, '答题要点在于准确说出两个定义、两种完整性（实体完整性与参照完整性）以及三条区别（作用、是否允许为空、数量）。只笼统写“主键唯一、外键不唯一”得分有限；把主键与外键的作用对调，或声称外键必须唯一、不能为空，都是常见错误。此外应明确指出外键可以取空值，表示暂时尚未确定与哪条记录关联。', '数据库基础', 1, 2);
-- [简答/困难/多媒体与信息安全] 简述计算机病毒的主要特征，并列出至少三种防范计算机病毒的措施。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1249, 3, 4, 3, '简述计算机病毒的主要特征，并列出至少三种防范计算机病毒的措施。', NULL, '主要特征：①传染性，能够自我复制并把自身嵌入其他程序或存储介质中传播；②潜伏性，可长期潜伏在系统中，在特定日期、特定操作等条件满足时才发作；③破坏性，发作时可能删除文件、篡改数据、占用系统资源，甚至破坏系统引导区；④隐蔽性，常通过加密、变形、依附宿主程序等方式隐藏，不易被发现；⑤可触发性，由预先设定的触发条件激活。防范措施：①安装杀毒软件并保持病毒库更新，定期进行全盘查杀；②及时安装操作系统和应用软件的安全补丁，修补已知漏洞；③不打开来历不明的邮件附件和链接，不下载安装来源不明的软件，使用移动存储设备前先查毒；④对重要数据定期备份，并采用离线或异地存放的方式；⑤启用防火墙，关闭不必要的端口和共享服务；⑥不浏览不安全网站、不使用盗版软件和来历不明的激活工具。', 16, '特征部分应写全传染性、潜伏性、破坏性、隐蔽性和可触发性五项，漏写隐蔽性或可触发性会失分。措施部分答出三条以上具体可操作的做法即可，但仅写“装杀毒软件”一条通常不够；补丁更新、数据备份、慎重对待移动存储和不明附件都是必不可少的要点，且应体现“备份是最后一道防线”这一认识。', '多媒体与信息安全', 1, 2);
-- [简答/困难/程序设计基础] 请用自然语言描述“求 1 到 100 之间所有偶数之和”的算法步骤，并说明该算法主要使用了哪种基本控制结构。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1250, 3, 4, 3, '请用自然语言描述“求 1 到 100 之间所有偶数之和”的算法步骤，并说明该算法主要使用了哪种基本控制结构。', NULL, '算法步骤：①令累加变量 sum 的初值为 0，令循环变量 i 的初值为 2；②判断 i 是否小于等于 100；③若条件成立，执行 sum=sum+i，再令 i=i+2，然后返回第②步继续判断；④若条件不成立，结束循环，输出 sum 的值。该算法主要使用了循环结构，并在循环入口处配合选择（判断）结构来决定是否继续执行循环体。最终输出结果为 2550。', 16, '答题要点有四：设置累加变量的初值、设置循环变量的初值、给出循环条件与循环体（累加并更新循环变量）、最后输出结果。缺少循环变量的更新会造成死循环，是常见错误。也可写成把 i 从 1 递增到 100、在循环体内判断 i 是否为偶数再累加，同样正确，但步长取 2 更简洁。结果验算：2+4+…+100=2×(1+2+…+50)=2×1275=2550。', '程序设计基础', 1, 2);
-- [单选/简单/计算机组成与工作原理] 下列各项中，属于衡量 CPU 运算速度的性能指标是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1251, 3, 0, 1, '下列各项中，属于衡量 CPU 运算速度的性能指标是（　　）', '[{"label":"A","content":"屏幕分辨率"},{"label":"B","content":"主频"},{"label":"C","content":"硬盘容量"},{"label":"D","content":"显示器尺寸"}]', 'B', 5, '主频即 CPU 时钟频率，表示 CPU 内部时钟信号的振荡速度，是衡量其运算速度快慢的基本指标；此外字长、核心数、Cache 容量等也影响 CPU 性能。屏幕分辨率、显示器尺寸属于显示设备的指标，硬盘容量属于外存容量指标，都与 CPU 的运算速度没有直接关系，故 A、C、D 不选。', '计算机组成与工作原理', 1, 2);
-- [单选/简单/计算机组成与工作原理] 冯·诺依曼体系结构计算机中，指令和数据（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1252, 3, 0, 1, '冯·诺依曼体系结构计算机中，指令和数据（　　）', '[{"label":"A","content":"都以二进制形式存放在存储器中"},{"label":"B","content":"分别以二进制和十进制形式存放"},{"label":"C","content":"都以十进制形式存放"},{"label":"D","content":"以人们书写的自然语言形式存放"}]', 'A', 5, '冯·诺依曼结构的基本思想是“存储程序”，即程序（指令序列）与数据一同以二进制形式存放在存储器中，计算机按地址顺序取出指令并自动执行，因此二者在存储器中的表示形式都是二进制代码。十进制是给人阅读的书写形式，计算机内部必须转换为二进制才能处理，B、C 错；自然语言不能被机器直接执行，D 错。', '计算机组成与工作原理', 1, 2);
-- [单选/简单/计算机网络] 在计算机的分类中，按覆盖范围划分，覆盖一个房间、一栋楼或一个校园范围的网络通常称为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1253, 3, 0, 1, '在计算机的分类中，按覆盖范围划分，覆盖一个房间、一栋楼或一个校园范围的网络通常称为（　　）', '[{"label":"A","content":"局域网（LAN）"},{"label":"B","content":"城域网（MAN）"},{"label":"C","content":"广域网（WAN）"},{"label":"D","content":"个人区域网（PAN）"}]', 'A', 5, '按覆盖的地理范围，计算机网络可分为局域网（LAN）、城域网（MAN）和广域网（WAN）。局域网覆盖一栋楼、一个校园或一个企业园区，范围通常在几公里以内，传输速率高、误码率低，故 A 正确。B 的城域网覆盖一个城市，C 的广域网覆盖国家乃至全球，二者范围都大于局域网；D 的个人区域网（PAN）覆盖范围更小（10 米左右），也不是本题所描述的范围。', '计算机网络', 1, 2);
-- [单选/简单/数制与编码] 在计算机中，1KB 等于（ ）个字节。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1254, 3, 0, 1, '在计算机中，1KB 等于（　　）个字节。', '[{"label":"A","content":"1000"},{"label":"B","content":"512"},{"label":"C","content":"1024"},{"label":"D","content":"2048"}]', 'C', 5, '计算机采用二进制计数，存储容量单位以 2 的幂为进率，1KB=$2^{10}$B=1024B，故 B 正确。1000 是十进制的近似说法，常见于硬盘厂商标注容量时，不是严格意义上的换算关系，A 错；512 与 2048 分别与正确值相差一倍，数值不符，C、D 错。', '数制与编码', 1, 2);
-- [单选/简单/办公软件基础] 在 Excel 中，若要把工作表 A1 到 D1 连续四个单元格中的数值相加，下列公式正确的是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1255, 3, 0, 1, '在 Excel 中，若要把工作表 A1 到 D1 连续四个单元格中的数值相加，下列公式正确的是（　　）', '[{"label":"A","content":"=SUM(A1+D1)"},{"label":"B","content":"=SUM(A1:D1)"},{"label":"C","content":"=COUNT(A1:D1)"},{"label":"D","content":"=AVERAGE(A1:D1)"}]', 'B', 5, '求和应使用 SUM 函数，A1:D1 中用冒号连接区域左上角和右下角单元格，表示连续的矩形区域，故 B 正确。A 中只写了首尾两个单元格，漏掉 B1、C1，结果不等于四个数之和；COUNT 统计区域中数值型单元格的个数，AVERAGE 计算算术平均值，二者都不是求和函数，C、D 错。', '办公软件基础', 1, 2);
-- [单选/简单/多媒体与信息安全] 下列文件格式中，属于图像文件格式的是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1256, 3, 0, 1, '下列文件格式中，属于图像文件格式的是（　　）', '[{"label":"A","content":"MP3"},{"label":"B","content":"AVI"},{"label":"C","content":"TXT"},{"label":"D","content":"JPEG"}]', 'D', 5, 'JPEG 是常见的有损压缩静态图像格式，扩展名为 .jpg 或 .jpeg，广泛用于照片存储，故 D 正确。MP3 是音频格式，AVI 是视频格式，TXT 是纯文本格式，三者都不是图像格式，A、B、C 不选。常见的图像格式还有 BMP、GIF、PNG 等。', '多媒体与信息安全', 1, 2);
-- [单选/简单/计算机网络] 在计算机网络的拓扑结构中，所有结点都通过单独的线路连接到一个中心结点、由中心结点转发数据的结构是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1257, 3, 0, 1, '在计算机网络的拓扑结构中，所有结点都通过单独的线路连接到一个中心结点、由中心结点转发数据的结构是（　　）', '[{"label":"A","content":"总线型"},{"label":"B","content":"环型"},{"label":"C","content":"树型"},{"label":"D","content":"星型"}]', 'D', 5, '星型拓扑以中心结点（如交换机）为核心，每个结点各自用一条链路与中心结点相连，结点之间的通信必须经过中心结点转发，故 D 正确。总线型拓扑中所有结点共享一条公共传输介质，环型拓扑中结点首尾相连构成闭合环路，树型拓扑是星型结构的分层扩展，三者都不符合题干描述，A、B、C 不选。', '计算机网络', 1, 2);
-- [单选/简单/多媒体与信息安全] 在数据加密技术中，加密密钥与解密密钥相同（或可由其中一个方便地推出另一个）的加密方式称为（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1258, 3, 0, 1, '在数据加密技术中，加密密钥与解密密钥相同（或可由其中一个方便地推出另一个）的加密方式称为（　　）', '[{"label":"A","content":"消息摘要"},{"label":"B","content":"非对称加密"},{"label":"C","content":"对称加密"},{"label":"D","content":"数字签名"}]', 'C', 5, '对称加密又称单密钥加密，通信双方使用同一把密钥进行加密和解密，典型算法有 DES、AES 等，故 C 正确。非对称加密使用公钥和私钥配对，两把密钥不同且不能由公钥推出私钥，B 错；数字签名利用私钥对摘要签名、公钥验证，属于非对称密码技术的应用，D 错；消息摘要（哈希）是单向不可逆的定长摘要算法，本身不涉及密钥，A 错。', '多媒体与信息安全', 1, 2);
-- [单选/简单/数制与编码] 下列关于计算机中数据单位的说法，正确的是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1259, 3, 0, 1, '下列关于计算机中数据单位的说法，正确的是（　　）', '[{"label":"A","content":"1 个字节等于 4 个二进制位"},{"label":"B","content":"1 个二进制位可以表示 0 到 9 十个数字"},{"label":"C","content":"1 个字节等于 8 个二进制位"},{"label":"D","content":"1 个字节最多能表示 10 种不同的状态"}]', 'C', 5, '字节（Byte）是计算机中最基本的存储单位，1B=8bit（位），故 C 正确、A 错。二进制位只有 0 和 1 两种状态，1 位最多表示 2 种状态，B 说它能表示十个数字与二进制的定义矛盾；1 个字节共 8 位，可表示 $2^{8}=256$ 种不同状态，D 错。', '数制与编码', 1, 2);
-- [单选/简单/操作系统] 对计算机用户而言，操作系统属于（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1260, 3, 0, 1, '对计算机用户而言，操作系统属于（　　）', '[{"label":"A","content":"应用软件"},{"label":"B","content":"硬件"},{"label":"C","content":"病毒防护软件"},{"label":"D","content":"系统软件"}]', 'D', 5, '软件分为系统软件和应用软件两大类。操作系统管理和调度计算机的软硬件资源、为用户和应用程序提供运行环境，是最基本的系统软件，故 D 正确。应用软件是为解决某一具体问题而开发的程序，如文字处理软件、浏览器，A 错；操作系统是程序而非硬件，B 错；病毒防护软件只是运行在操作系统之上的应用软件，C 错。', '操作系统', 1, 2);
-- [单选/中等/计算机组成与工作原理] 计算机的地址总线的位数决定了（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1261, 3, 0, 2, '计算机的地址总线的位数决定了（　　）', '[{"label":"A","content":"CPU 一次能够并行传输的数据位数"},{"label":"B","content":"CPU 的运算精度"},{"label":"C","content":"CPU 能够直接访问的最大内存容量"},{"label":"D","content":"外存储器的读写速度"}]', 'C', 5, '地址总线传送的是存储单元的地址信息，n 位地址总线可区分 $2^{n}$ 个不同地址，因此其位数决定了 CPU 能直接寻址（访问）的内存空间上限，故 C 正确。一次并行传输的数据位数由数据总线的位数决定，A 错；运算精度主要由字长（尤其是运算器位数）决定，B 错；外存的读写速度取决于外存设备本身的机械或电子特性，与地址总线无关，D 错。', '计算机组成与工作原理', 1, 2);
-- [单选/中等/数制与编码] 二进制数 10101010B 转换为十六进制数是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1262, 3, 0, 2, '二进制数 10101010B 转换为十六进制数是（　　）', '[{"label":"A","content":"AAH"},{"label":"B","content":"A5H"},{"label":"C","content":"55H"},{"label":"D","content":"F0H"}]', 'A', 5, '二进制与十六进制之间可按“四位一组”快速互化：10101010B 从右向左四位一组为 1010 和 1010，分别等于十进制的 10 和 10，即十六进制的 A 和 A，合起来是 AAH。干扰项 B 的 A5H 对应 10100101B，是分组时把位序写乱的结果；C 的 55H 对应 01010101B，恰是本数按位取反；D 的 F0H 对应 11110000B，与本数不符。', '数制与编码', 1, 2);
-- [单选/中等/计算机网络] 在 TCP/IP 体系结构中，为用户提供网页浏览服务的 HTTP 协议工作于（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1263, 3, 0, 2, '在 TCP/IP 体系结构中，为用户提供网页浏览服务的 HTTP 协议工作于（　　）', '[{"label":"A","content":"网络接口层"},{"label":"B","content":"网际层"},{"label":"C","content":"传输层"},{"label":"D","content":"应用层"}]', 'D', 5, 'HTTP 是超文本传输协议，直接面向用户的网页浏览、文件下载等应用，位于 TCP/IP 体系结构的最高层——应用层（它也对应 OSI 参考模型的应用层），故 D 正确。网络接口层处理与物理网络的接口，网际层由 IP 负责寻址和路由，传输层由 TCP、UDP 提供端到端的传输服务，三者都不直接提供网页浏览这一应用功能，A、B、C 错。', '计算机网络', 1, 2);
-- [单选/中等/程序设计基础] 在程序设计中，下列关于变量和常量的说法正确的是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1264, 3, 0, 2, '在程序设计中，下列关于变量和常量的说法正确的是（　　）', '[{"label":"A","content":"变量和常量的值在程序运行过程中都不允许改变"},{"label":"B","content":"变量的值在程序运行过程中允许改变，常量的值一经定义便不允许改变"},{"label":"C","content":"变量的值在程序运行过程中不允许改变，常量的值可以随时修改"},{"label":"D","content":"变量和常量的值在程序运行过程中都可以任意改变"}]', 'B', 5, '变量是内存中一块可命名的存储单元，其值在程序运行中可以重新赋值；常量则是在程序运行期间保持不变的量，一经定义便不能被修改，故 B 正确。C 把两者特性完全说反；A 否定了变量最基本的“可变”性质；D 否定了常量的“不可变”性质。此外，变量在使用前应先定义其数据类型，以便系统分配存储空间并确定取值范围。', '程序设计基础', 1, 2);
-- [单选/中等/办公软件基础] 在 Excel 中，若在单元格 B1 中输入公式 =A1，然后把它复制到单元格 C3，则 C3 中公式的引用是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1265, 3, 0, 2, '在 Excel 中，若在单元格 B1 中输入公式 =$A$1，然后把它复制到单元格 C3，则 C3 中公式的引用是（　　）', '[{"label":"A","content":"变为 $B$1"},{"label":"B","content":"仍为 $A$1"},{"label":"C","content":"变为 $A$3"},{"label":"D","content":"变为 $B$3"}]', 'B', 5, '行号和列标前都带有美元符号的是绝对引用，绝对引用在公式被复制或填充时不发生任何偏移，因此 C3 中的公式仍为 =$A$1，故 B 正确。A、C、D 都是引用随位置发生了行或列偏移，这正是相对引用（如 A1）或混合引用（如 A$1、$A1）才会出现的现象，与绝对引用的定义不符。', '办公软件基础', 1, 2);
-- [单选/中等/数据库基础] 在 SQL 中，用于从数据表中查询数据的关键字是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1266, 3, 0, 2, '在 SQL 中，用于从数据表中查询数据的关键字是（　　）', '[{"label":"A","content":"INSERT"},{"label":"B","content":"SELECT"},{"label":"C","content":"UPDATE"},{"label":"D","content":"DELETE"}]', 'B', 5, 'SELECT 是 SQL 的查询关键字，通常与 FROM、WHERE 等子句配合使用，用于从表中检索满足条件的行和列，故 B 正确。INSERT 用于向表中插入新记录，UPDATE 用于修改表中已有记录，DELETE 用于删除表中的记录，三者属于数据更新操作而不是查询，A、C、D 错。', '数据库基础', 1, 2);
-- [单选/中等/办公软件基础] 在 PowerPoint 中，用于统一设置演示文稿中所有幻灯片标题和正文文字格式的是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1267, 3, 0, 2, '在 PowerPoint 中，用于统一设置演示文稿中所有幻灯片标题和正文文字格式的是（　　）', '[{"label":"A","content":"幻灯片切换"},{"label":"B","content":"动画效果"},{"label":"C","content":"排练计时"},{"label":"D","content":"幻灯片母版"}]', 'D', 5, '幻灯片母版保存了幻灯片的版式、占位符位置、字体、配色等全局格式信息，修改母版后，所有基于该母版的幻灯片会同步更新，是统一风格的规范做法，故 D 正确。幻灯片切换设置的是幻灯片之间的过渡效果，动画效果针对对象进入、强调、退出时的动态表现，排练计时用于记录放映每张幻灯片所需的时间，三者都不改变文字的字体字号等格式，A、B、C 错。', '办公软件基础', 1, 2);
-- [单选/困难/计算机组成与工作原理] 某计算机的字长为 8 位，地址总线为 16 位，按字节编址，则该机能够直接访问的最大内存容量是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1268, 3, 0, 3, '某计算机的字长为 8 位，地址总线为 16 位，按字节编址，则该机能够直接访问的最大内存容量是（　　）', '[{"label":"A","content":"16KB"},{"label":"B","content":"32KB"},{"label":"C","content":"128KB"},{"label":"D","content":"64KB"}]', 'D', 5, '16 位地址总线可表示 $2^{16}=65536$ 个不同地址；由于按字节编址，每个地址对应 1 个字节，故最大可访问容量为 65536B=65536÷1024KB=64KB，D 正确。A、B 分别对应 14 位和 15 位地址总线，可寻址单元数不足；C 的 128KB 对应 17 位地址总线。本题中的字长 8 位只影响一次处理的数据宽度，不改变可寻址的单元个数。', '计算机组成与工作原理', 1, 2);
-- [单选/困难/数制与编码] 十进制数 -65 用 8 位二进制补码表示，结果是（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1269, 3, 0, 3, '十进制数 -65 用 8 位二进制补码表示，结果是（　　）', '[{"label":"A","content":"10111111B"},{"label":"B","content":"11000001B"},{"label":"C","content":"10111110B"},{"label":"D","content":"01000001B"}]', 'A', 5, '求负数的补码：先写出其绝对值的二进制 65=01000001B，再按位取反得 10111110B，末位加 1 得 10111111B，最高位为 1 表示负数，故 A 正确。干扰项 C 的 10111110B 只做了取反而忘记加 1，得到的是反码；B 的 11000001B 是取反时把高两位一并处理错误的结果；D 的 01000001B 是 +65 本身，符号位为 0，是正数。', '数制与编码', 1, 2);
-- [单选/困难/操作系统] 在采用分页存储管理的系统中，页的大小为 4KB，某进程的页表中记录：页号 0 对应物理块号 8，页号 1 对应物理块号 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1270, 3, 0, 3, '在采用分页存储管理的系统中，页的大小为 4KB，某进程的页表中记录：页号 0 对应物理块号 8，页号 1 对应物理块号 12。若该进程要访问的逻辑地址为 1000，则对应的物理地址是（　　）', '[{"label":"A","content":"8×4096+1000=33768"},{"label":"B","content":"12×4096+1000=50152"},{"label":"C","content":"1000"},{"label":"D","content":"1000×4096=4096000"}]', 'A', 5, '逻辑地址 1000 小于页大小 4096，说明它落在第 0 页内，页内偏移量为 1000；查页表知页号 0 对应的物理块号是 8，故物理地址=块号×块大小+页内偏移量=8×4096+1000=32768+1000=33768，A 正确。干扰项 B 用的是页号 1 对应的块号 12，属于查错页表项；C 直接把逻辑地址当作物理地址，忽略了地址变换；D 误把偏移量当作页号去乘块大小，变换公式用错。', '操作系统', 1, 2);
-- [多选/简单/程序设计基础] 下列各项中，属于计算机程序设计语言的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1271, 3, 1, 1, '下列各项中，属于计算机程序设计语言的有（　　）', '[{"label":"A","content":"机器语言"},{"label":"B","content":"汇编语言"},{"label":"C","content":"自然语言"},{"label":"D","content":"高级语言"}]', 'ABD', 5, '计算机程序设计语言按与硬件的接近程度分为机器语言、汇编语言和高级语言三类：机器语言是二进制指令代码，能被机器直接执行；汇编语言用助记符代替机器指令；高级语言接近自然语言和数学表达式，需经编译或解释后执行，A、B、D 均正确。自然语言是人类日常交流使用的语言（如汉语、英语），存在大量歧义，不能被计算机直接执行，不属于程序设计语言，C 不选。', '程序设计基础', 1, 2);
-- [多选/简单/计算机组成与工作原理] 关于计算机的内存储器，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1272, 3, 1, 1, '关于计算机的内存储器，下列说法正确的有（　　）', '[{"label":"A","content":"RAM 中的信息在断电后会丢失"},{"label":"B","content":"Cache 的存取速度比主存快，容量通常比主存小"},{"label":"C","content":"ROM 中的信息在断电后仍能保存"},{"label":"D","content":"RAM 的容量通常比硬盘大，程序和数据都应长期保存在 RAM 中"}]', 'ABC', 5, 'RAM 依靠电容或触发器保存信息，断电即失，属于易失性存储器，A 对；Cache 位于 CPU 与主存之间，用于缓解速度差异，速度最快而容量最小，B 对；ROM 采用只读存储介质，断电后内容不丢失，常用于存放 BIOS 等固化程序，C 对。主存容量远小于硬盘，且断电后数据全部丢失，不能作为长期存储介质，D 错。', '计算机组成与工作原理', 1, 2);
-- [多选/简单/计算机网络] 下列设备中，属于网络互联设备的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1273, 3, 1, 1, '下列设备中，属于网络互联设备的有（　　）', '[{"label":"A","content":"网卡"},{"label":"B","content":"交换机"},{"label":"C","content":"打印机"},{"label":"D","content":"路由器"}]', 'ABD', 5, '网卡是主机接入网络的接口设备，负责数据的发送与接收；交换机工作在数据链路层，用于局域网内部各结点的连接与数据帧转发；路由器工作在网络层，用于连接不同网络并选择转发路径，三者都属于网络互联设备，A、B、D 均正确。打印机是输出设备，用于把计算机的处理结果输出到纸张，与网络互联无关，C 不选（网络打印机只是同时具备网络接口的输出设备，其本质仍是输出设备）。', '计算机网络', 1, 2);
-- [多选/中等/多媒体与信息安全] 关于计算机网络安全，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1274, 3, 1, 2, '关于计算机网络安全，下列说法正确的有（　　）', '[{"label":"A","content":"只要安装了杀毒软件，就不必再安装系统补丁，也不必对数据进行备份"},{"label":"B","content":"对重要数据定期备份并异地存放，可降低数据丢失的风险"},{"label":"C","content":"使用足够复杂并在必要时更换的密码，有助于抵御口令猜测和暴力破解"},{"label":"D","content":"加密技术可以把明文变换为密文，使未授权者即使截获数据也难以读懂内容"}]', 'BCD', 5, '备份是应对误删、硬件故障和勒索软件最可靠的手段，异地存放还能抵御区域性灾害，B 对；增大口令空间并定期更换可显著提高暴力破解的成本，C 对；加密通过密钥和算法把明文变换为密文，保证数据的机密性，D 对。杀毒软件依赖已知病毒特征库，无法覆盖系统漏洞和所有新型攻击，及时安装补丁、坚持数据备份仍然必不可少，A 错。', '多媒体与信息安全', 1, 2);
-- [多选/中等/操作系统] 关于操作系统的基本功能，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1275, 3, 1, 2, '关于操作系统的基本功能，下列说法正确的有（　　）', '[{"label":"A","content":"存储管理负责内存的分配、回收与保护，并支持虚拟存储"},{"label":"B","content":"文件管理负责文件的组织、存取、共享与保护"},{"label":"C","content":"处理机管理负责进程的创建、调度与撤销"},{"label":"D","content":"设备管理只负责设备的物理安装，不涉及任何软件驱动"}]', 'ABC', 5, '操作系统的五大管理功能中，处理机管理以进程为单位进行创建、调度和撤销，C 对；存储管理负责内存的分配与回收、地址变换、存储保护和虚拟存储的实现，A 对；文件管理负责文件的组织、目录结构、存取控制与共享保护，B 对。设备管理除硬件外还必须包括设备驱动程序、缓冲技术、设备分配和中断处理等大量软件工作，D 说“不涉及任何软件驱动”明显错误。', '操作系统', 1, 2);
-- [多选/中等/办公软件基础] 在 Excel 中，关于数据表的排序与筛选，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1276, 3, 1, 2, '在 Excel 中，关于数据表的排序与筛选，下列说法正确的有（　　）', '[{"label":"A","content":"自动筛选可以直接删除表中不满足条件的记录"},{"label":"B","content":"排序可以按一个或多个关键字进行"},{"label":"C","content":"自动筛选可以按指定条件只显示满足条件的记录"},{"label":"D","content":"排序和筛选都不会改变原始数据本身的内容，只是改变显示或排列方式"}]', 'BCD', 5, 'Excel 支持多关键字排序，可依次指定主要关键字、次要关键字，B 对；自动筛选通过字段下拉列表设定条件，仅显示满足条件的行，C 对；排序只是重新排列行的先后次序，筛选只是隐藏不符合条件的行，二者都不改动单元格中的数据内容，D 对。筛选的效果是“隐藏”而不是“删除”，被筛掉的数据仍然保留在表中，取消筛选即可重新显示，A 错。', '办公软件基础', 1, 2);
-- [多选/中等/多媒体与信息安全] 关于多媒体技术，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1277, 3, 1, 2, '关于多媒体技术，下列说法正确的有（　　）', '[{"label":"A","content":"图像分辨率越高，图像越清晰，占用的存储空间通常也越大"},{"label":"B","content":"JPEG 采用的是有损压缩，适用于照片等色彩丰富的图像"},{"label":"C","content":"数据压缩与还原的过程分别称为解压缩和压缩"},{"label":"D","content":"多媒体数据压缩可分为有损压缩和无损压缩两类"}]', 'ABD', 5, '分辨率越高表示图像包含的像素越多，细节越清晰，同时数据量也随之增大，A 对；JPEG 通过舍弃人眼不敏感的高频信息实现有损压缩，压缩比高，适合色彩层次丰富的照片，B 对；按还原后能否完全恢复原始数据，压缩分为无损压缩（如 ZIP、PNG）和有损压缩（如 JPEG、MP3），D 对。压缩是把数据体积变小的过程，还原（解压）才是把压缩数据恢复为原始数据的过程，C 把两者的名称说反了。', '多媒体与信息安全', 1, 2);
-- [多选/困难/数据库基础] 在 SQL 中，关于查询语句 SELECT 及其子句，下列说法正确的有（ ）
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1278, 3, 1, 3, '在 SQL 中，关于查询语句 SELECT 及其子句，下列说法正确的有（　　）', '[{"label":"A","content":"在 SELECT 语句中必须使用 ORDER BY 子句，否则语句不能执行"},{"label":"B","content":"SELECT 子句用于指定查询结果中要显示的列"},{"label":"C","content":"FROM 子句用于指定数据来源的表"},{"label":"D","content":"WHERE 子句用于对行进行条件筛选"}]', 'BCD', 5, 'SELECT 指明结果集包含哪些列，FROM 指明从哪张表（或哪些表）取数，WHERE 对元组（行）施加条件筛选，三者构成查询语句的基本骨架，B、C、D 均对。ORDER BY 是可选子句，只用于对查询结果按指定列排序，缺少它语句照样可以正常执行，A 错。若需要对分组统计结果再筛选，应使用 HAVING 而不是 WHERE。', '数据库基础', 1, 2);
-- [判断/简单/操作系统] 操作系统是最基本的系统软件，是计算机硬件与用户之间的接口。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1279, 3, 2, 1, '操作系统是最基本的系统软件，是计算机硬件与用户之间的接口。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '操作系统直接管理计算机的处理器、存储器、设备和文件等软硬件资源，为上层应用程序提供运行环境，并给用户提供命令界面或图形界面，因此它是最基本的系统软件，也是用户与硬件之间的桥梁，命题正确。没有操作系统的计算机被称为裸机，用户几乎无法使用。', '操作系统', 1, 2);
-- [判断/简单/操作系统] 在计算机中，文件名相同的两个文件不能存放在同一个文件夹中。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1280, 3, 2, 1, '在计算机中，文件名相同的两个文件不能存放在同一个文件夹中。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '文件夹（目录）的作用之一就是通过命名空间来区分文件，在同一个文件夹内不允许出现两个完全同名的文件，否则系统无法唯一定位。若需要保留同名文件，可把它们放入不同的文件夹，或改用不同的文件名，故命题正确。', '操作系统', 1, 2);
-- [判断/简单/办公软件基础] 在 PowerPoint 中，修改幻灯片母版后，凡是基于该母版的幻灯片都会随之改变。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1281, 3, 2, 1, '在 PowerPoint 中，修改幻灯片母版后，凡是基于该母版的幻灯片都会随之改变。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '母版是幻灯片版式、占位符、字体、配色等全局格式的载体，普通幻灯片都继承母版的格式设置。因此修改母版中的字体、配色或占位符样式后，凡基于该母版的幻灯片会同步更新，这正是母版用于统一演示文稿风格的原因，命题正确。', '办公软件基础', 1, 2);
-- [判断/中等/计算机组成与工作原理] 微型计算机中的字长是指 CPU 一次能够并行处理的二进制位数，字长越长，其运算精度通常越高。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1282, 3, 2, 2, '微型计算机中的字长是指 CPU 一次能够并行处理的二进制位数，字长越长，其运算精度通常越高。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '字长是 CPU 一次能同时处理的二进制数据位数，也是通用寄存器和数据总线的常见宽度，它直接决定了机器一次运算所能表示的数值范围与精度，字长越长，可表示的数值范围越大、精度越高，命题正确。需要注意字长的单位是“位”而不是“字节”，常见的 32 位、64 位指的就是字长。', '计算机组成与工作原理', 1, 2);
-- [判断/中等/计算机网络] 在计算机网络中，IP 地址 192.168.1.1 属于 A 类地址。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1283, 3, 2, 2, '在计算机网络中，IP 地址 192.168.1.1 属于 A 类地址。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, 'IPv4 地址按第一字节的取值分类：A 类为 1~126，B 类为 128~191，C 类为 192~223。192.168.1.1 的第一字节是 192，落在 192~223 之间，属于 C 类地址（且是常用的私有地址段），故命题错误。B 类地址的例子有 172.16.0.1。', '计算机网络', 1, 2);
-- [判断/中等/多媒体与信息安全] 防火墙部署在网络边界，能够阻止病毒通过移动存储设备传入内部计算机。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1284, 3, 2, 2, '防火墙部署在网络边界，能够阻止病毒通过移动存储设备传入内部计算机。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '防火墙位于内部网络与外部网络之间，通过对进出网络的数据包进行检查和过滤来控制网络访问，因此它对来自网络的攻击有明显的防护作用。但通过 U 盘等移动存储设备传入的病毒根本不经过网络边界，防火墙无从检查，防范这类病毒应依靠杀毒软件扫描、关闭自动播放等措施，故命题错误。', '多媒体与信息安全', 1, 2);
-- [判断/中等/计算机组成与工作原理] 计算机中的存储器分为内存储器和外存储器，硬盘属于外存储器。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1285, 3, 2, 2, '计算机中的存储器分为内存储器和外存储器，硬盘属于外存储器。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'A', 3, '按与 CPU 的联系和数据能否被 CPU 直接访问，存储器分为内存（主存）和外存（辅存）。内存如 RAM、ROM，可被 CPU 直接访问但断电易失；外存如硬盘、光盘、U 盘，容量大、断电后数据仍能保存，CPU 需通过内存间接访问，硬盘正属于外存，命题正确。', '计算机组成与工作原理', 1, 2);
-- [判断/困难/程序设计基础] 结构化程序设计要求任何算法都只能用顺序结构和循环结构两种基本控制结构来表示。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1286, 3, 2, 3, '结构化程序设计要求任何算法都只能用顺序结构和循环结构两种基本控制结构来表示。', '[{"label":"A","content":"正确"},{"label":"B","content":"错误"}]', 'B', 3, '结构化程序设计的三种基本控制结构是顺序结构、选择结构和循环结构，缺一不可：没有选择结构就无法实现分支判断，绝大多数实用算法都要根据条件决定执行路径。命题把三种结构说成只有两种，漏掉了选择结构，故错误。', '程序设计基础', 1, 2);
-- [填空/简单/办公软件基础] 在 Excel 中，把公式复制到其他单元格时，引用会随公式所在位置自动发生偏移的引用方式称为 ______ 引用。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1287, 3, 3, 1, '在 Excel 中，把公式复制到其他单元格时，引用会随公式所在位置自动发生偏移的引用方式称为 ______ 引用。', NULL, '相对', 5, '相对引用的书写形式为列标加行号（如 A1），不含美元符号。把它复制或填充到其他单元格时，行号和列标会按照位移量自动调整，从而指向相对当前位置同样偏移的单元格，这正是相对引用的含义。若在列标和行号前都加美元符号（如 $A$1），则成为绝对引用，复制后引用位置保持不变；只锁定行或只锁定列则是混合引用。', '办公软件基础', 1, 2);
-- [填空/简单/计算机组成与工作原理] 计算机执行一条指令通常需要经过取指令、分析指令和执行指令三个阶段，其中“分析指令”又称指令的 ______。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1288, 3, 3, 1, '计算机执行一条指令通常需要经过取指令、分析指令和执行指令三个阶段，其中“分析指令”又称指令的 ______。', NULL, '译码', 5, '一条指令的执行过程一般划分为三个阶段：取指令阶段由程序计数器（PC）给出指令地址，从内存取出指令送入指令寄存器；分析指令阶段由指令译码器对指令的操作码进行译码，判断这是一条什么操作、需要哪些操作数；执行指令阶段由控制器发出相应的控制信号，完成该指令规定的操作。因此分析指令又称指令译码。从宏观上看，计算机的工作过程就是“取指令—分析指令—执行指令”周而复始地自动重复的过程。', '计算机组成与工作原理', 1, 2);
-- [填空/中等/数制与编码] 计算机中存储容量的基本单位是字节，1 个字节等于 ______ 个二进制位。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1289, 3, 3, 2, '计算机中存储容量的基本单位是字节，1 个字节等于 ______ 个二进制位。', NULL, '8', 5, '位（bit）是计算机中表示信息的最小单位，只能取 0 或 1；字节（Byte）是存储容量的基本单位，1B=8bit。因此 1 个字节可以表示 $2^{8}=256$ 种不同的状态，正好足以对 128 个常用字符（标准 ASCII 码为 7 位，扩展 ASCII 码为 8 位）进行编码。', '数制与编码', 1, 2);
-- [填空/中等/计算机网络] 在计算机网络中，用于连接不同网络并在它们之间选择路径转发数据分组的设备是 ______（填设备名称）。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1290, 3, 3, 2, '在计算机网络中，用于连接不同网络并在它们之间选择路径转发数据分组的设备是 ______（填设备名称）。', NULL, '路由器', 5, '路由器工作在网络层，能够识别 IP 地址，根据路由表为数据分组选择下一跳，从而把分组从一个网络转发到另一个网络，是实现网络互联和 Internet 的核心设备。集线器工作在物理层、交换机工作在数据链路层，它们主要用于同一局域网内部各结点的连接，不能完成不同网络之间的路径选择。', '计算机网络', 1, 2);
-- [填空/中等/计算机网络] 在电子邮件系统中，负责把邮件从发件人一方发送（投递）到邮件服务器所使用的协议是 ______。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1291, 3, 3, 2, '在电子邮件系统中，负责把邮件从发件人一方发送（投递）到邮件服务器所使用的协议是 ______。', NULL, 'SMTP', 5, 'SMTP 即简单邮件传输协议，负责邮件在客户端与服务器之间以及服务器与服务器之间的发送与投递，默认使用 25 端口。接收邮件则使用 POP3（默认端口 110）或 IMAP（默认端口 143）协议，把 SMTP 与收件协议混为一谈是常见错误。', '计算机网络', 1, 2);
-- [填空/中等/数制与编码] 按照数的进位规则分类，十进制、二进制、八进制和十六进制统称为 ______ 计数制。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1292, 3, 3, 2, '按照数的进位规则分类，十进制、二进制、八进制和十六进制统称为 ______ 计数制。', NULL, '进位', 5, '进位计数制的共同特点是按固定的基数进位：逢基数进一，同时采用位权表示法，各位的权值是基数的整数次幂，数值等于各位数字与其位权乘积之和。十进制的基数是 10，二进制的基数是 2，八进制和十六进制的基数分别是 8 和 16，它们都属于进位计数制。', '数制与编码', 1, 2);
-- [填空/困难/操作系统] 在操作系统的存储管理中，当访问的页面不在内存时，硬件会产生 ______ 中断，由操作系统负责把所需页面调入内存。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1293, 3, 3, 3, '在操作系统的存储管理中，当访问的页面不在内存时，硬件会产生 ______ 中断，由操作系统负责把所需页面调入内存。', NULL, '缺页', 5, '在请求分页存储管理中，页表项设有状态位以标记该页是否已在内存。当 CPU 访问的页面不在内存时，硬件检测到状态位为“不在内存”，便产生缺页中断（又称缺页异常），转入操作系统处理；操作系统按置换算法选择淘汰页，把所需页面从外存调入内存，再重新执行被中断的指令。若内存中已无空闲块且需淘汰页面，被淘汰页若被修改过还需写回外存。', '操作系统', 1, 2);
-- [填空/困难/操作系统] 在操作系统的页面置换算法中，选择以后最长时间内不再被访问（或最长时间不使用）的页面予以淘汰的算法是 ______ 置换算
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1294, 3, 3, 3, '在操作系统的页面置换算法中，选择以后最长时间内不再被访问（或最长时间不使用）的页面予以淘汰的算法是 ______ 置换算法。', NULL, '最佳（OPT，最优）', 5, '最佳置换算法（OPT）在需要淘汰页面时，选择以后最长时间内不再被访问的页面淘汰，可以保证获得最低的缺页率，故又称最优置换算法。但它需要预先知道程序未来的访问序列，实际系统中无法实现，因此只用作衡量其他算法优劣的理论标准。常见的可实现算法有先进先出（FIFO）和最近最久未使用（LRU）等。', '操作系统', 1, 2);
-- [简答/简单/计算机组成与工作原理] 简述计算机硬件系统的五个基本组成部分，并说明其中运算器和控制器各自的主要功能。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1295, 3, 4, 1, '简述计算机硬件系统的五个基本组成部分，并说明其中运算器和控制器各自的主要功能。', NULL, '计算机硬件系统由运算器、控制器、存储器、输入设备和输出设备五个基本部分组成。运算器又称算术逻辑单元（ALU），主要完成算术运算（加、减、乘、除等）和逻辑运算（与、或、非、比较等），是数据加工处理的部件。控制器是整个计算机的指挥中心，负责从存储器中取出指令、分析（译码）指令，并按指令的要求向各个部件发出控制信号，使各部件协调工作。运算器和控制器合称中央处理器（CPU）；存储器用于存放程序和数据；输入设备把外界信息送入计算机；输出设备把处理结果送出。', 16, '答题要点有三：一是五个组成部分写全（运算器、控制器、存储器、输入设备、输出设备）；二是说明运算器负责算术运算和逻辑运算；三是说明控制器负责取指、译码并发出控制信号、统一指挥各部件。常见错误是把“存储器”写成 CPU 的组成部分，或把控制器说成执行运算的部件。最后点明运算器与控制器合称 CPU，可以体现知识的整体性。', '计算机组成与工作原理', 1, 2);
-- [简答/简单/多媒体与信息安全] 简述多媒体数据压缩的必要性，并说明无损压缩与有损压缩的区别，每类各举一种常见的数据格式。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1296, 3, 4, 1, '简述多媒体数据压缩的必要性，并说明无损压缩与有损压缩的区别，每类各举一种常见的数据格式。', NULL, '压缩的必要性：多媒体信息（尤其是图像、音频和视频）的数据量极为庞大，例如一幅 1024×768 像素、24 位真彩色的图像未压缩时就要占用约 2.25MB，一段未经压缩的视频更是每秒需要数十兆字节。这样大的数据量既难以在网络上传输，也难以在存储介质中保存，因此必须在不明显影响使用效果的前提下对数据进行压缩。无损压缩是指压缩后的数据经解压还原能够完全恢复原始数据、不丢失任何信息，其压缩比相对较低，适用于程序文件、文本和需要精确还原的数据，常见格式有 ZIP、RAR、PNG 等。有损压缩是指压缩过程中舍弃了部分人眼或人耳不敏感的信息，解压后无法完全恢复原始数据，但压缩比可以做得很高，适用于图像、音频和视频等允许一定失真的场合，常见格式有 JPEG、MP3、MPEG 等。', 16, '答题要点有三：一是说明压缩的必要性，即多媒体数据量大，传输和存储都受到限制，这一点应结合具体例子说明而不是空泛地说“数据太大”；二是准确区分两类压缩的判别标准——解压后能否完全恢复原始数据，这是本题的核心；三是各举出正确的格式名称，注意不能把 MP3、JPEG 说成无损格式，也不能把 PNG 说成有损格式。常见错误是仅按“压缩比高低”来区分两者，而忽略了“信息是否丢失”这一本质标准。', '多媒体与信息安全', 1, 2);
-- [简答/中等/操作系统] 简述进程的三种基本状态及其相互转换关系，并说明引起这些转换的典型原因。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1297, 3, 4, 2, '简述进程的三种基本状态及其相互转换关系，并说明引起这些转换的典型原因。', NULL, '进程有三种基本状态：①就绪状态，进程已获得除 CPU 之外的全部所需资源，只要再获得 CPU 便可立即执行，多个就绪进程按一定策略排成就绪队列；②运行状态，进程已获得 CPU，正在处理机上执行，单 CPU 系统中任一时刻最多只有一个进程处于运行状态；③阻塞（等待）状态，进程因等待某一事件（如等待 I/O 完成、等待其他进程发来的消息）而暂停执行，即使给它 CPU 也无法运行。转换关系与原因：就绪→运行，是进程调度程序把 CPU 分配给了它；运行→就绪，是时间片用完或被更高优先级的进程抢占；运行→阻塞，是进程主动请求 I/O 或等待某事件而无法继续；阻塞→就绪，是它所等待的事件已经完成（如 I/O 结束）。需要特别指出，阻塞状态不能直接转为运行状态，就绪状态也不能直接转为阻塞状态。', 16, '答题要点是三种状态的名称与含义、四条合法转换路径及其原因，并强调“阻塞→运行”和“就绪→阻塞”这两种转换不存在。常见错误是把阻塞到就绪写成阻塞到运行，忽略了必须重新排队等待调度这一步；也有的只写状态名称而不说明原因，得分不完整。记忆时可抓住一条主线：状态的变化要么由进程自身主动申请资源引起（运行→阻塞），要么由外部事件完成或调度策略引起（阻塞→就绪、运行→就绪、就绪→运行）。', '操作系统', 1, 2);
-- [简答/中等/计算机网络] 写出 TCP/IP 体系结构自下而上的四个层次名称，并简要说明各层的主要功能。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1298, 3, 4, 2, '写出 TCP/IP 体系结构自下而上的四个层次名称，并简要说明各层的主要功能。', NULL, 'TCP/IP 体系结构自下而上分为四层：①网络接口层（网络访问层），负责与物理网络的接口，把上层交来的 IP 数据报封装成帧并在物理介质上发送与接收，对应 OSI 参考模型的物理层和数据链路层；②网际层（互联网层），由 IP 协议为核心，负责为数据分组寻址、选择路由并转发分组，提供无连接、尽最大努力交付的服务，对应 OSI 的网络层；③传输层，为源主机与目的主机的应用进程之间提供端到端的数据传输服务，主要协议有面向连接、可靠传输的 TCP 和无连接、开销小的 UDP，对应 OSI 的传输层；④应用层，直接面向用户的各类网络应用，包含 HTTP、FTP、SMTP、DNS 等具体协议，对应 OSI 的会话层、表示层和应用层。', 16, '答题要点是四层的名称与顺序，以及每层的核心功能与代表协议，最好同时指出与 OSI 七层的对应关系。常见错误有三：一是顺序颠倒，把网际层写在传输层之上；二是把 IP 说成可靠传输协议（可靠性由传输层的 TCP 承担，IP 只提供尽最大努力交付）；三是漏写应用层的代表协议。四层记忆口诀可记为“接口、网际、传输、应用”。', '计算机网络', 1, 2);
-- [简答/中等/数据库基础] 简述关系数据库中候选键、主键与外键的含义，并说明设置主键的意义。
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1299, 3, 4, 2, '简述关系数据库中候选键、主键与外键的含义，并说明设置主键的意义。', NULL, '候选键（候选码）是能唯一标识关系中每一条记录、且不含多余属性的属性或属性组，一个关系可以有多个候选键。主键（主码）是从多个候选键中选定一个作为实际使用的键，它的值必须唯一且不允许为空，一个表最多只能有一个主键。外键（外码）是本关系中的一个属性或属性组，它引用另一个关系（或本关系）的主键，其取值必须是被引用关系中已存在的主键值，或者取空值，一个表可以有多个外键。设置主键的意义主要有三点：一是唯一标识表中的每一条记录，避免出现完全重复、无法区分的元组；二是保证实体完整性，即主键非空且唯一；三是主键常被自动建立索引，能够加快按主键进行的查找；同时它也是其他表建立外键、从而表达表与表之间联系的依据。', 16, '答题要点是三个概念的定义与区别，以及主键的作用。候选键与主键的关系是“多选一”，把两者混为一谈、认为候选键就是主键是常见错误；另一常见错误是声称外键必须唯一或不允许为空，实际上外键允许重复也允许取空值（表示暂时未确定与哪条记录关联）。说明主键意义时应落到实体完整性、唯一标识记录、支撑表间联系三个方面，只说“唯一”略显单薄。', '数据库基础', 1, 2);
-- [简答/困难/多媒体与信息安全] 某图像的分辨率为 1024×768 像素，每个像素用 24 位二进制数表示颜色，请计算该图像未压缩时所占的存储空间（用 
INSERT INTO question (id, bank_id, type, difficulty, content, options, answer, score, analysis, category, status, created_by) VALUES (
  1300, 3, 4, 3, '某图像的分辨率为 1024×768 像素，每个像素用 24 位二进制数表示颜色，请计算该图像未压缩时所占的存储空间（用 MB 表示，结果保留整数），并说明图像文件采用有损压缩后占用空间减小的原因以及常见的图像文件格式。', NULL, '计算过程：像素总数=1024×768=786432 个；每个像素占 24 位=24÷8=3 字节；因此数据量=786432×3=2359296B；换算成 MB：2359296÷1024=2304KB，2304÷1024=2.25MB，保留整数约为 2MB。有损压缩后文件变小的原因：图像数据中存在大量冗余（如相邻像素颜色接近造成的空间冗余、人眼对亮度敏感而对色度不敏感造成的视觉冗余），有损压缩利用人眼的视觉特性，舍弃部分人眼不易察觉的高频细节和色度信息，只保留主要信息，因此压缩比可以做得很高，代价是还原后无法完全恢复原始图像。常见的图像文件格式有 BMP、JPEG、GIF、PNG、TIFF 等，其中 BMP 一般不压缩或只做简单无损压缩，文件较大；JPEG 为有损压缩，适合照片；PNG 支持无损压缩和透明背景；GIF 支持简单的动画和 256 色。', 16, '本题的评分要点有三：一是计算过程与结果。必须先算像素总数，再乘每个像素的字节数，最后按 1MB=1024KB、1KB=1024B 换算，结果为 2359296B=2.25MB≈2MB。常见错误是把 24 位当成 24 字节（结果会放大 8 倍，得到约 18MB），或按 1MB=1000KB 换算导致结果偏差。二是说明有损压缩以牺牲部分细节换取高压缩比、还原后不能完全恢复原始数据。三是至少列举 BMP、JPEG、GIF、PNG 中的三种格式并说明其特点，只写格式名称而不作区分会失分。', '多媒体与信息安全', 1, 2);

-- 四川专升本 · 计算机基础模拟卷（一）：25 题，总分 150，及格 90，120 分钟（题面固定，无抽题规则）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (5, '四川专升本 · 计算机基础模拟卷（一）', '覆盖计算机组成与工作原理、数制与编码、操作系统、办公软件基础、计算机网络、数据库基础、多媒体与信息安全、程序设计基础的原创模拟卷，共 25 题、满分 150 分、及格 90 分，建议用时 90 分钟。', 3, 120, 150, 90, '模拟卷', '[{"questionId":1201,"score":5},{"questionId":1202,"score":5},{"questionId":1203,"score":5},{"questionId":1204,"score":5},{"questionId":1205,"score":5},{"questionId":1206,"score":5},{"questionId":1207,"score":5},{"questionId":1208,"score":5},{"questionId":1209,"score":5},{"questionId":1210,"score":5},{"questionId":1221,"score":5},{"questionId":1222,"score":5},{"questionId":1223,"score":5},{"questionId":1224,"score":5},{"questionId":1229,"score":3},{"questionId":1230,"score":3},{"questionId":1231,"score":3},{"questionId":1232,"score":3},{"questionId":1237,"score":5},{"questionId":1238,"score":5},{"questionId":1239,"score":5},{"questionId":1240,"score":5},{"questionId":1245,"score":16},{"questionId":1246,"score":16},{"questionId":1247,"score":16}]', NULL, NULL, 1, 2);

-- 四川专升本 · 计算机基础模拟卷（二）：25 题，总分 150，及格 90，120 分钟（题面固定，无抽题规则）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (6, '四川专升本 · 计算机基础模拟卷（二）', '与模拟卷（一）题目完全不重复，覆盖数制与编码、操作系统、办公软件基础、计算机网络、数据库基础、信息安全与程序设计基础的原创模拟卷，共 25 题、满分 150 分、及格 90 分，建议用时 90 分钟。', 3, 120, 150, 90, '模拟卷', '[{"questionId":1211,"score":5},{"questionId":1212,"score":5},{"questionId":1213,"score":5},{"questionId":1214,"score":5},{"questionId":1215,"score":5},{"questionId":1216,"score":5},{"questionId":1217,"score":5},{"questionId":1218,"score":5},{"questionId":1219,"score":5},{"questionId":1220,"score":5},{"questionId":1225,"score":5},{"questionId":1226,"score":5},{"questionId":1227,"score":5},{"questionId":1228,"score":5},{"questionId":1233,"score":3},{"questionId":1234,"score":3},{"questionId":1235,"score":3},{"questionId":1236,"score":3},{"questionId":1241,"score":5},{"questionId":1242,"score":5},{"questionId":1243,"score":5},{"questionId":1244,"score":5},{"questionId":1248,"score":16},{"questionId":1249,"score":16},{"questionId":1250,"score":16}]', NULL, NULL, 1, 2);

-- 四川专升本 · 计算机基础模拟卷（三）：25 题，总分 150，及格 90，120 分钟（题面固定，无抽题规则）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (11, '四川专升本 · 计算机基础模拟卷（三）', '与前三张卷题目完全不重复，由新增题目组成的原创模拟卷，共 25 题、满分 150 分、及格 90 分，建议用时 90 分钟。', 3, 120, 150, 90, '模拟卷', '[{"questionId":1251,"score":5},{"questionId":1252,"score":5},{"questionId":1253,"score":5},{"questionId":1254,"score":5},{"questionId":1255,"score":5},{"questionId":1256,"score":5},{"questionId":1257,"score":5},{"questionId":1258,"score":5},{"questionId":1259,"score":5},{"questionId":1260,"score":5},{"questionId":1271,"score":5},{"questionId":1272,"score":5},{"questionId":1273,"score":5},{"questionId":1274,"score":5},{"questionId":1279,"score":3},{"questionId":1280,"score":3},{"questionId":1281,"score":3},{"questionId":1282,"score":3},{"questionId":1287,"score":5},{"questionId":1288,"score":5},{"questionId":1289,"score":5},{"questionId":1290,"score":5},{"questionId":1295,"score":16},{"questionId":1296,"score":16},{"questionId":1297,"score":16}]', NULL, NULL, 1, 2);

-- 四川专升本 · 计算机基础模拟卷（四）：25 题，总分 150，及格 90，120 分钟（题面固定，无抽题规则）
INSERT INTO exam (id, title, description, bank_id, duration, total_score, pass_score, paper_type, question_snapshot, start_time, end_time, status, created_by) VALUES
  (12, '四川专升本 · 计算机基础模拟卷（四）', '与前三张卷题目完全不重复，由新增题目组成的原创模拟卷，共 25 题、满分 150 分、及格 90 分，建议用时 90 分钟。', 3, 120, 150, 90, '模拟卷', '[{"questionId":1261,"score":5},{"questionId":1262,"score":5},{"questionId":1263,"score":5},{"questionId":1264,"score":5},{"questionId":1265,"score":5},{"questionId":1266,"score":5},{"questionId":1267,"score":5},{"questionId":1268,"score":5},{"questionId":1269,"score":5},{"questionId":1270,"score":5},{"questionId":1275,"score":5},{"questionId":1276,"score":5},{"questionId":1277,"score":5},{"questionId":1278,"score":5},{"questionId":1283,"score":3},{"questionId":1284,"score":3},{"questionId":1285,"score":3},{"questionId":1286,"score":3},{"questionId":1291,"score":5},{"questionId":1292,"score":5},{"questionId":1293,"score":5},{"questionId":1294,"score":5},{"questionId":1298,"score":16},{"questionId":1299,"score":16},{"questionId":1300,"score":16}]', NULL, NULL, 1, 2);

-- 演示数据到此为止。注意：这些卷子是「题面固定」的（question_snapshot 已写死），
-- 不经过智能组卷；若要体验随机组卷，请在「智能组卷」页面按规则现场生成。
