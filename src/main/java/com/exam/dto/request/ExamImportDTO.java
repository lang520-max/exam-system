package com.exam.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 试卷导入参数（JSON 导入）。
 *
 * <p>支持两种写法：</p>
 * <ol>
 *   <li><b>单卷导入</b>：只给 {@code title} + {@code questions}，导入一份试卷；</li>
 *   <li><b>多卷导入</b>：给 {@code exams} 数组，每个元素自带 title 与 questions。</li>
 * </ol>
 *
 * <p>目标题库二选一：{@code bankId}（用已有题库）或 {@code bank}
 * （按 name 找，找不到就新建）。题目也支持两种写法：{@code questionId}
 * 引用题库里已有的题，或者直接给出题干/选项/答案让系统新建。</p>
 *
 * <p>导入出来的试卷一律是<b>草稿</b>（status=0），学生看不见，
 * 需要教师在「考试管理」里手动发布 —— 与「智能组卷」的行为保持一致。</p>
 */
@Data
public class ExamImportDTO {

    /** 单卷导入时的试卷标题 */
    private String title;

    /** 单卷导入时的试卷说明 */
    private String description;

    /** 目标题库 ID（与 bank 二选一） */
    private Long bankId;

    /** 目标题库定义（与 bankId 二选一）：按 name 匹配，不存在则新建 */
    private BankSpec bank;

    /** 单卷导入时的考试时长（分钟） */
    private Integer duration;

    /** 单卷导入时的及格分；留空按总分 60% 自动计算 */
    private Integer passScore;

    /** 单卷导入时的试卷类型（V4）：模拟卷 / 专项训练；留空按「模拟卷」 */
    private String paperType;

    /** 单卷导入时的题目列表 */
    private List<ImportQuestion> questions;

    /** 多卷导入：一次导入多份试卷 */
    private List<ImportExam> exams;

    /** 题库定义 */
    @Data
    public static class BankSpec {
        private String name;
        private String subject;
        private String description;
    }

    /** 多卷导入时的单份试卷 */
    @Data
    public static class ImportExam {
        private String title;
        private String description;
        private Integer duration;
        private Integer passScore;
        /** 试卷类型（V4）：模拟卷 / 专项训练；留空按「模拟卷」 */
        private String paperType;
        private List<ImportQuestion> questions;
    }

    /**
     * 导入的题目。
     *
     * <p>{@code questionId} 非空时表示引用题库里已有的题目，此时其余字段可省略
     * （{@code score} 仍可覆盖该题在本卷的卷面分值）。</p>
     */
    @Data
    public static class ImportQuestion {

        /** 引用已有题目时使用 */
        private Long questionId;

        /** 0-单选 1-多选 2-判断 3-填空 4-简答 */
        private Integer type;

        /** 1-简单 2-中等 3-困难，留空默认为 2 */
        private Integer difficulty;

        /** 本卷卷面分值；留空时新建题按题型默认分值、引用题按题库分值 */
        private Integer score;

        private String content;

        /**
         * 选项。为方便手写，三种写法都接受：
         * <pre>
         *   {"A":"选项一","B":"选项二"}
         *   [{"label":"A","content":"选项一"}, ...]
         *   [["A","选项一"], ["B","选项二"]]
         * </pre>
         * 判断题（type=2）可省略，系统自动补「正确 / 错误」。
         */
        private Object options;

        private String answer;

        private String analysis;

        /**
         * 知识点/专题（V4），如「一元函数积分学」「语法」。
         *
         * <p>引用已有题目（{@code questionId}）时忽略 —— 此时题目已在库里有自己的标签，
         * 导入一份卷子不该顺手改掉题库里的标签。</p>
         */
        private String category;
    }
}