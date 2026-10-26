package com.exam.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 教师/管理员「人脸核验放行」。
 *
 * <p>用在两种情况：学生连续失败已达上限、或人脸服务不可用（超时/欠费/限流）。
 * 放行会写一条 OVERRIDE_PASS 留痕（含操作人），开考门禁随后认可它。</p>
 */
@Data
public class FaceOverrideForm {

    @NotNull(message = "考生ID不能为空")
    private Long userId;

    @NotNull(message = "考试ID不能为空")
    private Long examId;

    /** 放行理由（可选，但建议写：审计时最有用的就是这一栏） */
    private String reason;
}