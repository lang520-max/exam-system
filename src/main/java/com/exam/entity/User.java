package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户
 */
@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    /** 真实姓名 */
    private String realName;

    private String email;

    private String phone;

    /** 0-管理员 1-教师 2-学生 */
    private Integer role;

    private String avatar;

    /** 0-禁用 1-启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 是否为管理员 */
    public boolean isAdmin() {
        return role != null && role == 0;
    }

    /** 是否为教师 */
    public boolean isTeacher() {
        return role != null && role <= 1;
    }

    /** 是否为管理员或教师 */
    public boolean isStaff() {
        return role != null && role <= 1;
    }

    /** 是否为管理员或教师 */
    public boolean isStudent() {
        return role != null && role == 2;
    }
}