package com.caikailang.examsystem.service;

import com.caikailang.examsystem.domain.Question;
import com.caikailang.examsystem.dto.GradeItem;
import com.caikailang.examsystem.dto.GradeResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 自动判分器 —— 整个系统最核心、也最需要被测试钉死的一段逻辑。
 *
 * <p><b>设计要点：完全不依赖数据库、Spring 上下文与任何外部状态。</b>
 * {@code grade} 是纯函数：同样的题目和答案，永远得到同样的分数。
 * 因此它可以被单元测试逐条覆盖（单选、判断、多选全对/少选/多选/乱序、未作答），
 * 而不需要启动 Spring 或连数据库。</p>
 *
 * <p><b>判分规则</b></p>
 * <ul>
 *   <li>单选题：学生答案与标准答案精确匹配（忽略大小写与空白）得满分，否则 0 分；</li>
 *   <li>判断题：同上，答案统一归一化为 {@code A}=正确 / {@code B}=错误；</li>
 *   <li>多选题：<b>全对才得分</b>。少选、多选、错选一律 0 分（不给半分），
 *       选项顺序不影响结果（{@code "ACB"} 与 {@code "ABC"} 等价）；</li>
 *   <li>未作答：0 分；</li>
 *   <li>整卷得分 = 各题得分之和，按题目在试卷上的分值快照累加。</li>
 * </ul>
 *
 * @author 蔡开浪
 */
@Component
public class GradeCalculator {

    /** 判断题「正确」的各种写法，统一归一化为 A */
    private static final Set<String> JUDGE_TRUE =
        Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "A", "T", "TRUE", "Y", "YES", "对", "正确", "是", "√")));

    /** 判断题「错误」的各种写法，统一归一化为 B */
    private static final Set<String> JUDGE_FALSE =
        Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "B", "F", "FALSE", "N", "NO", "错", "错误", "否", "×", "X")));

    /**
     * 答案规范化：把「同一个意思的不同写法」收敛成同一个字符串，再做比较。
     *
     * <p>这样学生用 {@code "a"}、{@code " A "}、{@code "A."} 作答都能被正确识别；
     * 多选题的 {@code "C,A"} 会被排序去重成 {@code "AC"}，避免因为书写顺序被判错。</p>
     *
     * @param type 题型，判断题与多选题有额外归一化规则
     * @param rawAnswer 原始答案，可为 null
     * @return 规范化后的答案；空白或无法识别时返回 {@code null}（视为未作答）
     */
    public static String normalize(String type, String rawAnswer) {
        if (rawAnswer == null) {
            return null;
        }
        String value = rawAnswer.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (Question.TYPE_JUDGE.equals(type)) {
            if (JUDGE_TRUE.contains(value)) {
                return "A";
            }
            if (JUDGE_FALSE.contains(value)) {
                return "B";
            }
        }
        if (Question.TYPE_MULTIPLE.equals(type)) {
            // 只保留字母，排序 + 去重，使 "C,A" / "a c" / "AC" 三种写法等价
            char[] chars = value.replaceAll("[^A-Z]", "").toCharArray();
            Arrays.sort(chars);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) {
                if (sb.length() == 0 || sb.charAt(sb.length() - 1) != c) {
                    sb.append(c);
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        // 单选 / 判断：剥掉 "A." "A、" 之类的修饰，只留字母
        String cleaned = value.replaceAll("[^A-Z]", "");
        return cleaned.isEmpty() ? value : cleaned;
    }

    /**
     * 单题判分。
     *
     * @param question         题目（其 {@code score} 应为该题在本卷中的分值快照）
     * @param rawStudentAnswer 学生原始作答，可为 null（未作答）
     * @return 判分明细，含是否答对、满分与实际得分
     */
    public GradeItem grade(Question question, String rawStudentAnswer) {
        String type = question.getType();
        String correctAnswer = normalize(type, question.getAnswer());
        String studentAnswer = normalize(type, rawStudentAnswer);
        boolean correct = correctAnswer != null && correctAnswer.equals(studentAnswer);

        BigDecimal fullScore = question.getScore() == null ? BigDecimal.ZERO : question.getScore();
        BigDecimal earnedScore = correct ? fullScore : BigDecimal.ZERO;

        return new GradeItem(question.getId(), type, studentAnswer, correctAnswer,
            fullScore, earnedScore, correct);
    }

    /**
     * 整卷判分：按题目顺序逐题判分并累加总分。
     *
     * <p>未在 {@code answers} 中出现的题目按未作答处理（0 分），
     * 保证「试卷有几道题就有几条判分明细」，交卷后成绩单不会缺题。</p>
     *
     * @param questions 试卷题目（分值已替换为卷面分值快照）
     * @param answers   题目 ID → 学生原始答案
     */
    public GradeResult grade(List<Question> questions, Map<Long, String> answers) {
        GradeResult result = new GradeResult();
        if (questions == null || questions.isEmpty()) {
            return result;
        }
        Map<Long, String> safeAnswers = answers == null
            ? Collections.<Long, String>emptyMap() : answers;
        for (Question question : questions) {
            result.addItem(grade(question, safeAnswers.get(question.getId())));
        }
        return result;
    }
}
