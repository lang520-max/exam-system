-- ==========================================================================
--  内容修正：把裸的 \underline{...} 包进 $...$（前端只渲染 $...$ 内的公式）
--  由 .deploy-tools/qadd/fix-bare-tex.js 生成；只改 content，不动 ID 与试卷快照
--  用法：mysql -uroot -p****** exam_system < sql/fix-content-updates.sql
-- ==========================================================================

USE `exam_system`;

-- Q1037 content
UPDATE question SET content = '极限 $\\lim\\limits_{x\\to\\infty}\\left(1+\\dfrac{2}{x}\\right)^{x}=$ $\\underline{\\qquad\\qquad}$。' WHERE id = 1037;
-- Q1038 content
UPDATE question SET content = '曲线 $y=x^{3}-3x$ 在点 $(0,0)$ 处的切线方程为 $\\underline{\\qquad\\qquad}$。' WHERE id = 1038;
-- Q1039 content
UPDATE question SET content = '函数 $f(x)=x^{2}$ 在闭区间 $[0,2]$ 上满足拉格朗日中值定理的条件，则定理结论中的 $\\xi=$ $\\underline{\\qquad\\qquad}$。' WHERE id = 1039;
-- Q1040 content
UPDATE question SET content = '不定积分 $\\int\\dfrac{1}{x^{2}+4}\\,dx=$ $\\underline{\\qquad\\qquad}$。' WHERE id = 1040;
-- Q1041 content
UPDATE question SET content = '由曲线 $y=\\sqrt{x}$、直线 $x=1$ 与 $x$ 轴围成的平面图形绕 $x$ 轴旋转一周所得旋转体的体积为 $\\underline{\\qquad\\qquad}$。' WHERE id = 1041;
-- Q1042 content
UPDATE question SET content = '函数 $z=x^{2}y$ 在点 $(1,1)$ 处的全微分 $dz=$ $\\underline{\\qquad\\qquad}$。' WHERE id = 1042;
-- Q1043 content
UPDATE question SET content = '幂级数 $\\sum\\limits_{n=0}^{\\infty}\\dfrac{x^{n}}{2^{n}}$ 的收敛半径为 $\\underline{\\qquad\\qquad}$。' WHERE id = 1043;
-- Q1044 content
UPDATE question SET content = '极限 $\\lim\\limits_{n\\to\\infty}\\left(\\dfrac{1}{n+1}+\\dfrac{1}{n+2}+\\cdots+\\dfrac{1}{n+n}\\right)=$ $\\underline{\\qquad\\qquad}$。' WHERE id = 1044;
-- Q1087 content
UPDATE question SET content = '极限 $\\lim\\limits_{x\\to 0}\\dfrac{1-\\cos x}{x^{2}}=$ $\\underline{\\qquad\\qquad}$。' WHERE id = 1087;
-- Q1088 content
UPDATE question SET content = '曲线 $y=x^{2}+e^{x}$ 在点 $(0,1)$ 处的切线方程为 $\\underline{\\qquad\\qquad}$。' WHERE id = 1088;
-- Q1089 content
UPDATE question SET content = '设 $y=\\sin^{2}x$，则 $dy=$ $\\underline{\\qquad\\qquad}$。' WHERE id = 1089;
-- Q1090 content
UPDATE question SET content = '函数 $f(x)=x\\ln x$ 的极小值为 $\\underline{\\qquad\\qquad}$。' WHERE id = 1090;
-- Q1091 content
UPDATE question SET content = '不定积分 $\\int x\\cos x\\,dx=$ $\\underline{\\qquad\\qquad}$。' WHERE id = 1091;
-- Q1092 content
UPDATE question SET content = '定积分 $\\int_{0}^{2}|x-1|\\,dx=$ $\\underline{\\qquad\\qquad}$。' WHERE id = 1092;
-- Q1093 content
UPDATE question SET content = '设 $z=\\arctan\\dfrac{y}{x}$（$x\\neq 0$），则 $\\dfrac{\\partial z}{\\partial x}=$ $\\underline{\\qquad\\qquad}$。' WHERE id = 1093;
-- Q1094 content
UPDATE question SET content = '微分方程 $y''''-2y''+5y=0$ 的通解为 $\\underline{\\qquad\\qquad}$。' WHERE id = 1094;
