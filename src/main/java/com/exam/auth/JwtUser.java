package com.exam.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * JWT 用户信息
 */
@Data
@AllArgsConstructor
public class JwtUser {

    private Long id;
    private String username;
    private Integer role;

    public boolean isAdmin() {
        return role != null && role == 0;
    }

    public boolean isTeacher() {
        return role != null && role <= 1;
    }

    public boolean isStaff() {
        return role != null && role <= 1;
    }

    public boolean isStudent() {
        return role != null && role == 2;
    }
}