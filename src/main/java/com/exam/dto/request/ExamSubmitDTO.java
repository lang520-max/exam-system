package com.exam.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 提交答案
 */
@Data
public class ExamSubmitDTO {

    @NotNull(message = "考试记录ID不能为空")
    private Long recordId;

    /** 题目ID -> 学生答案 */
    private Map<Long, String> answers;
}