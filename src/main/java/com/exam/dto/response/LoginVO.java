package com.exam.dto.response;

import lombok.Data;

/**
 * 登录响应
 */
@Data
public class LoginVO {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long userId;
    private String username;
    private Integer role;
    private String realName;
}