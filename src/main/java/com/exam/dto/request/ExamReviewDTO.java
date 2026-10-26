package com.exam.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 教师批改提交。
 *
 * <p>批量提交而不是一题一个请求：教师批完一份卷子要保存十几处改动，
 * 逐题请求既慢又可能保存到一半失败，留下半份批改结果。
 * 批量还能只对受影响的记录重算一次总分。</p>
 */
@Data
public class ExamReviewDTO {

    @NotEmpty(message = "没有需要保存的批改")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {

        @NotNull(message = "答案ID不能为空")
        private Long answerId;

        /**
         * 教师给的分数。
         *
         * <p>这里只校验非负——上界是「本题卷面满分」，它存在试卷快照里，
         * 要查库才知道，因此由服务层裁剪而不是注解校验。</p>
         */
        @NotNull(message = "分数不能为空")
        @Min(value = 0, message = "分数不能为负")
        private Integer score;
    }
}