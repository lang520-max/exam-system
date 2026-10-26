package com.exam.dto.response;

import com.exam.entity.Question;
import lombok.Data;

import java.util.List;

/**
 * 考试题目视图（不含正确答案）
 */
@Data
public class QuestionVO {

    private Long id;
    private Integer type;
    private String content;
    private String options;
    private Integer score;

    /**
     * 知识点/专题（V4）。
     *
     * <p>练习模式会把它显示成一个小标签（「这道题属于语法」），
     * 同时也是「按专题抽题确实生效了」的可验证证据。
     * 它是标签、不是答案，放进考试取题视图也没有泄题风险。</p>
     */
    private String category;

    public static QuestionVO from(Question q) {
        QuestionVO vo = new QuestionVO();
        vo.setId(q.getId());
        vo.setType(q.getType());
        vo.setContent(q.getContent());
        vo.setOptions(q.getOptions());
        vo.setScore(q.getScore());
        vo.setCategory(q.getCategory());
        return vo;
    }

    public static List<QuestionVO> from(List<Question> questions) {
        return questions.stream().map(QuestionVO::from).toList();
    }
}