package com.caikailang.examsystem.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 系统用户：教师（TEACHER，即管理员）与学生（STUDENT）共用一张表，靠 {@code role} 区分。
 *
 * <p>{@code password} 存 BCrypt 哈希；对外返回前必须调用 {@link #clearPassword()}，
 * 保证 REST 响应里永远不出现密码字段。</p>
 *
 * @author lang520-max
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 教师 / 管理员：可维护题库、组卷、查看全场成绩 */
    public static final String ROLE_TEACHER = "TEACHER";
    /** 学生：可参加考试、查看自己的成绩与错题 */
    public static final String ROLE_STUDENT = "STUDENT";

    private Long id;
    private String username;
    /** BCrypt 哈希值，仅持久层使用 */
    private String password;
    private String nickname;
    private String role;
    private Date createdAt;
    private Date updatedAt;

    public boolean isTeacher() {
        return ROLE_TEACHER.equals(role);
    }

    public boolean isStudent() {
        return ROLE_STUDENT.equals(role);
    }

    /** 擦除密码，用于对外返回（Session 中保存的也是擦除后的对象） */
    public void clearPassword() {
        this.password = null;
    }

    /** 角色中文名，供页面展示 */
    public String getRoleText() {
        return isTeacher() ? "教师" : "学生";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', role='" + role + "'}";
    }
}
