package com.exam.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 教师批改视图 —— 一场考试下所有考生的逐题作答，供教师复核与改分。
 *
 * <p><b>为什么不复用 {@link ExamResultVO}</b>：那个视图是给学生看自己的卷子的，
 * 服务层会校验「只能查看自己的考试记录」，教师拿不到别人的。
 * 而且批改需要的信息也不同：教师要看到<b>评分来源</b>（客观题自动 / AI / 教师）
 * 与 <b>AI 给出的理由</b>，学生则不需要知道这些（AI 理由除外）。</p>
 */
@Data
public class ExamReviewVO {

    private Long examId;
    private String examTitle;
    private Integer totalScore;
    private Integer passScore;

    /** 全部考生尚未评分的题数合计 —— 教师最关心的一个数 */
    private Integer pendingTotal;

    private List<StudentRecord> records;

    @Data
    public static class StudentRecord {
        private Long recordId;
        private Long userId;
        private String studentName;
        /** 0-考试中 1-待判分 2-已出分 3-超时 */
        private Integer status;
        private String statusText;
        private Integer score;
        /** 该考生尚未评分的题数 */
        private Integer pendingCount;
        private List<AnswerItem> answers;
    }

    @Data
    public static class AnswerItem {
        /** 批改时提交的就是这个 id */
        private Long answerId;
        private Long questionId;
        /** 0单选 1多选 2判断 3填空 4简答 */
        private Integer type;
        private String questionContent;
        private String correctAnswer;
        private String studentAnswer;
        /** 该题在本卷中的满分（取自试卷快照） */
        private Integer fullScore;
        private Integer earnedScore;
        /** null=待复核 / AUTO / AI / TEACHER */
        private String gradedBy;
        private String aiReason;
        private Boolean pendingReview;
    }
}