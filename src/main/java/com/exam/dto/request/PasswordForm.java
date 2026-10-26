package com.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自助修改密码表单。
 *
 * <p>长度规则与注册保持一致（6-50 位），免得出现「注册能设、改密码却不认」的怪事。</p>
 */
@Data
public class PasswordForm {

    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 50, message = "新密码长度6-50位")
    private String newPassword;
}