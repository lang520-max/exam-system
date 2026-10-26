package com.exam.dto.response;

import com.exam.common.constant.Correctness;
import com.exam.common.constant.QuestionType;
import com.exam.entity.Question;
import com.exam.entity.StudentAnswer;
import lombok.Data;

import java.util.List;

/**
 * 成绩视图
 */
@Data
public class ExamResultVO {

    private Long recordId;
    private Long examId;
    private String examTitle;
    private Integer totalScore;
    private Integer score;
    private Integer correctCount;
    private Integer totalCount;
    /** 尚未评分的题数（主观题且 AI 未启用或调用失败） */
    private Integer pendingReviewCount;
    private Boolean passed;
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {
        private Long questionId;
        private String questionContent;
        /**
         * 选项（题库里的原始 JSON 串，形如 {@code [{"label":"A","content":"…"}]}）。
         *
         * <p><b>为什么必须带上它</b>：选择题只看题干 + 字母是看不懂的 ——
         * 学生在成绩单上看到「我的作答 B / 正确答案 D」，却无从知道 B 和 D
         * 分别写了什么，等于看不到原题。它与 {@code correctAnswer} 同级：
         * 都在交卷之后才返回（本接口仅限本人、且记录已交卷）。</p>
         */
        private String options;
        private Integer type;
        private String studentAnswer;
        private String correctAnswer;
        /** 该题在本卷中的满分（取自试卷快照） */
        private Integer score;
        /** 该题实际得分 */
        private Integer earnedScore;
        /** 是否完全正确（拿到满分）。部分正确时为 false，但 earnedScore > 0 */
        private Boolean isCorrect;
        /** 是否尚未评分。true 时前端应显示「待复核」而不是「答错」 */
        private Boolean pendingReview;
        /** 评分来源：AUTO-客观题自动 / AI-模型评分 / TEACHER-教师复核；null 表示待复核 */
        private String gradedBy;
        /** AI 给出的评分理由（由 AI 评分时才有） */
        private String aiReason;
        /** 答案解析（错题复习最需要的一段；题库里没有则为 null） */
        private String analysis;

        public static AnswerItem from(StudentAnswer sa, Question q, Integer fullScore) {
            AnswerItem item = new AnswerItem();
            item.setQuestionId(sa.getQuestionId());
            item.setQuestionContent(q == null ? null : q.getContent());
            item.setOptions(q == null ? null : q.getOptions());
            item.setType(q == null ? null : q.getType());
            item.setCorrectAnswer(q == null ? null : q.getAnswer());
            item.setAnalysis(q == null ? null : q.getAnalysis());

            int full = fullScore == null ? 0 : fullScore;
            int earned = sa.getScore() == null ? 0 : sa.getScore();
            item.setScore(full);
            item.setEarnedScore(earned);

            // 关键：待复核（gradedBy == null）必须与「答错」区分开。
            // 原先这里只看 isCorrect == 1，而主观题被判为「待复核」时 isCorrect 不是 1，
            // 于是成绩单把待复核的题显示成「答错、红底、0 分」。
            boolean graded = sa.getGradedBy() != null;
            // ★ 客观题的「对错」以 is_correct 为准，不用「得分是否够满分」反推：
            //   判分时 AUTO 已经给出结论，而历史数据里存在「is_correct=1 但 score
            //   小于快照满分」的情况（旧的 100 分制记录被按比例折算过），
            //   用分数反推会把**答对的题显示成部分正确**，连带让「只看错题」
            //   把它们当错题留下来（第二轮报告 Bug 13 的现象）。
            boolean objective = q != null && q.getType() != null
                    && QuestionType.isObjective(q.getType());
            item.setIsCorrect(graded && (objective
                    ? Integer.valueOf(Correctness.CORRECT).equals(sa.getIsCorrect())
                    : full > 0 && earned >= full));
            item.setGradedBy(sa.getGradedBy());
            item.setPendingReview(!graded);
            item.setAiReason(sa.getAiReason());
            return item;
        }
    }
}