package com.exam.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 练习模式「对答案」的请求体。
 *
 * <p>只带题目 id 与学生的作答：正确答案与解析只在**提交之后**由服务端返回，
 * 不随题目一起下发（否则一整轮的答案可以一次性拿走）。</p>
 */
@Data
public class PracticeCheckForm {

    @NotNull(message = "缺少题目 id")
    private Long questionId;

    /** 学生的作答；未作答时为空串或 null */
    private String answer;
}