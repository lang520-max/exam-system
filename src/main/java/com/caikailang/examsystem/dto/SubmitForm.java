package com.caikailang.examsystem.dto;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * 交卷表单。
 *
 * <p>{@code answers} 的 key 是题目 ID、value 是学生答案（多选形如 {@code "ABD"}，未作答可不传）。
 * 学生身份取自 Session，绝不接受前端传入 studentId。</p>
 *
 * @author 蔡开浪
 */
public class SubmitForm {

    @NotNull(message = "考试记录 ID 不能为空")
    private Long recordId;

    /** 题目 ID → 学生答案 */
    private Map<Long, String> answers = new HashMap<Long, String>();

    /** 是否学生手动点击交卷（false 表示倒计时结束由前端自动提交，两者都走同一套判分逻辑） */
    private boolean autoSubmit;

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Map<Long, String> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<Long, String> answers) {
        this.answers = answers == null ? new HashMap<Long, String>() : answers;
    }

    public boolean isAutoSubmit() {
        return autoSubmit;
    }

    public void setAutoSubmit(boolean autoSubmit) {
        this.autoSubmit = autoSubmit;
    }
}
