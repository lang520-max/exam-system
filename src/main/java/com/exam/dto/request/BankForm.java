package com.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新建题库参数。
 *
 * <p><b>为什么不直接绑定 {@code QuestionBank} 实体</b>：实体的 {@code createdBy}
 * 必须由服务端按当前登录用户写入。如果请求体能直接塞进实体，
 * 客户端只要带上 {@code createdBy} 就能把题库挂到别人名下（或者删掉归属），
 * 而归属正是写操作校验的依据，等于把权限判定的钥匙交给了调用方。</p>
 */
@Data
public class BankForm {

    /** 题库名称 */
    @NotBlank(message = "题库名称不能为空")
    private String name;

    /** 科目；留空时由前端展示为「未分类」 */
    private String subject;

    private String description;
}